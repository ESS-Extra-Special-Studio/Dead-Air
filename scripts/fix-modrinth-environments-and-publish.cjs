/**
 * Fix Modrinth environment metadata (Aug 2025 migration blocker) and submit drafts for review.
 *
 *   node scripts/fix-modrinth-environments-and-publish.cjs
 *   node scripts/fix-modrinth-environments-and-publish.cjs --dry-run
 *   node scripts/fix-modrinth-environments-and-publish.cjs --only dead-air
 */
const fs = require("fs");
const path = require("path");

const SECRETS = path.join(
  "C:",
  "Users",
  "Ksivi",
  "Desktop",
  "Extra Special Studio",
  "Where we're at now",
  "SECRETS.local.md"
);
const MAP = path.join(
  "C:",
  "Users",
  "Ksivi",
  "Desktop",
  "Extra Special Studio",
  "Where we're at now",
  "modrinth-import",
  "mods-map.json"
);
const ICON_DIR = path.join("C:", "Users", "Ksivi", "IdeaProjects", "ess-website", "public", "images", "mods");
const OUT = path.join(__dirname, "..", "modrinth-fix-env-results.json");

const DRY = process.argv.includes("--dry-run");
const ONLY = (() => {
  const i = process.argv.indexOf("--only");
  if (i === -1) return null;
  return new Set(
    process.argv[i + 1]
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)
  );
})();

const SLUGS = [
  "apocalypse-structures-radio-towers-and-airdrops",
  "dead-air",
  "dead-air-pip-boy-radio-conversion",
  "dead-air-after-hours-fm",
  "dead-air-block-beats",
  "dead-air-broken-youth-radio",
  "dead-air-frontline-fm",
  "dead-air-iron-rain-fm",
  "dead-air-wayfarer-radio",
  "dead-air-zero-gravity",
];

const CATEGORIES = {
  "dead-air": ["adventure", "utility"],
  "radio-towers": ["adventure", "worldgen"],
  "pip-boy-radio": ["equipment", "utility"],
  "after-hours-fm": ["adventure"],
  "beat-blocks": ["adventure"],
  "broken-youth-radio": ["adventure"],
  "frontline-fm": ["adventure"],
  "iron-rain-fm": ["adventure"],
  "wayfarer-radio": ["adventure"],
  "zero-gravity": ["adventure"],
};

const ENVIRONMENT = "client_and_server";

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found in SECRETS.local.md");
  return m[1].trim();
}

function loadModsMap() {
  let text = fs.readFileSync(MAP, "utf8");
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
  const mods = JSON.parse(text);
  const bySlug = new Map();
  for (const mod of mods) {
    if (mod.cfSlug) bySlug.set(mod.cfSlug, mod);
    if (mod.mrSlug) bySlug.set(mod.mrSlug, mod);
  }
  return bySlug;
}

async function api(token, base, method, apiPath, body) {
  const res = await fetch(`${base}${apiPath}`, {
    method,
    headers: {
      Authorization: token,
      "User-Agent": "ESS-Modrinth-FixEnv/1.0",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${base}${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
  }
  return text ? JSON.parse(text) : null;
}

function modMeta(mod) {
  if (!mod) return null;
  return {
    title: mod.mrTitle || mod.name,
    description: (mod.short || mod.name).slice(0, 250),
    body: `${mod.short || mod.name}\n\nSee [CurseForge](${mod.cfUrl}) for full description.`,
    source_url: mod.github || undefined,
    issues_url: mod.github ? `${mod.github}/issues` : undefined,
    discord_url: "https://discord.gg/dhnAuhNMQH",
    license_id: "LicenseRef-All-Rights-Reserved",
    categories: CATEGORIES[mod.id] || ["adventure"],
  };
}

async function uploadIcon(token, projectId, modId) {
  const iconPath = path.join(ICON_DIR, `${modId}.png`);
  if (!fs.existsSync(iconPath)) return { skipped: "no icon file" };
  const stat = fs.statSync(iconPath);
  if (stat.size > 256 * 1024) return { skipped: `icon too large (${Math.round(stat.size / 1024)}KiB)` };

  const res = await fetch(`https://api.modrinth.com/v3/project/${projectId}/icon?ext=png`, {
    method: "PATCH",
    headers: {
      Authorization: token,
      "Content-Type": "image/png",
      "User-Agent": "ESS-Modrinth-FixEnv/1.0",
    },
    body: fs.readFileSync(iconPath),
  });
  if (!res.ok) {
    const t = await res.text();
    return { error: `${res.status}: ${t.slice(0, 200)}` };
  }
  return { ok: true };
}

async function fixProject(token, slug, modBySlug) {
  const row = { slug };
  const mod = modBySlug.get(slug);
  const meta = modMeta(mod);

  const project = await api(token, "https://api.modrinth.com/v2", "GET", `/project/${slug}`);
  row.statusBefore = project.status;
  row.environmentBefore = project.environment;
  row.projectId = project.id;

  if (project.status === "approved" || project.status === "listed") {
    row.action = "already public";
    return row;
  }

  const versions = await api(token, "https://api.modrinth.com/v2", "GET", `/project/${project.id}/version`);
  row.versionCount = (versions || []).length;
  if (!row.versionCount) {
    row.action = "skipped — no versions";
    return row;
  }

  if (DRY) {
    row.action = "would fix env + submit";
    row.versionsUnknown = (versions || []).filter((v) => v.environment === "unknown").length;
    return row;
  }

  if (meta) {
    await api(token, "https://api.modrinth.com/v2", "PATCH", `/project/${project.id}`, meta);
    row.metadataPatched = true;
  }

  if (mod?.id) {
    row.icon = await uploadIcon(token, project.id, mod.id);
  }

  await api(token, "https://api.modrinth.com/v3", "PATCH", `/project/${project.id}`, {
    side_types_migration_review_status: "reviewed",
  });
  row.reviewStatusSet = true;

  let fixedVersions = 0;
  for (const v of versions || []) {
    if (v.environment === ENVIRONMENT) continue;
    await api(token, "https://api.modrinth.com/v3", "PATCH", `/version/${v.id}`, {
      environment: ENVIRONMENT,
    });
    fixedVersions++;
  }
  row.versionsFixed = fixedVersions;

  await api(token, "https://api.modrinth.com/v2", "PATCH", `/project/${project.id}`, {
    requested_status: "approved",
    status: "processing",
  });
  row.submitted = true;

  const after = await api(token, "https://api.modrinth.com/v2", "GET", `/project/${slug}`);
  row.statusAfter = after.status;
  row.requestedAfter = after.requested_status;
  row.queuedAfter = after.queued;
  row.environmentAfter = after.environment;
  row.action = after.queued ? "queued for review" : after.status === "processing" ? "processing" : "submitted";
  return row;
}

async function main() {
  const token = readToken();
  const modBySlug = loadModsMap();
  const slugs = ONLY ? [...ONLY] : SLUGS;
  const results = [];

  for (const slug of slugs) {
    try {
      const row = await fixProject(token, slug, modBySlug);
      results.push(row);
      const msg = row.error
        ? `ERR  ${slug} — ${row.error}`
        : `${DRY ? "DRY" : "OK"}  ${slug} — ${row.action}${row.queuedAfter ? ` (queued ${row.queuedAfter})` : ""}`;
      console.log(msg);
    } catch (e) {
      results.push({ slug, error: e.message });
      console.log(`ERR  ${slug} — ${e.message}`);
    }
  }

  fs.writeFileSync(OUT, JSON.stringify({ at: new Date().toISOString(), dryRun: DRY, results }, null, 2));
  console.log("\nWrote", OUT);
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
