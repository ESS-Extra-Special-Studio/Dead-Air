/**
 * Create Dead Air + expansion Modrinth projects (drafts) and upload local JARs.
 * Does not touch Radio Towers (upload requires owner permission on that project).
 *
 *   node scripts/create-dead-air-modrinth.cjs --dry-run
 *   node scripts/create-dead-air-modrinth.cjs
 *   node scripts/create-dead-air-modrinth.cjs --only=dead-air-frontline-fm
 *   node scripts/create-dead-air-modrinth.cjs --titles-only
 */
const fs = require("fs");
const { openAsBlob } = require("fs");
const path = require("path");
const { assertModrinthUploadsNotPaused } = require("./modrinth-pause-guard.cjs");

assertModrinthUploadsNotPaused();

const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
const MAP = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\modrinth-import\\mods-map.json";
const ICON_DIR = "C:\\Users\\Ksivi\\IdeaProjects\\ess-website\\public\\images\\mods";
const OUT = path.join(__dirname, "..", "modrinth-create-results.json");
const DRY = process.argv.includes("--dry-run");
const TITLES_ONLY = process.argv.includes("--titles-only");
const ONLY_ARG = process.argv.find((a) => a.startsWith("--only="));
const ONLY_SLUGS = ONLY_ARG
  ? new Set(
      ONLY_ARG.slice("--only=".length)
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    )
  : null;

const RETRY_DELAYS_MS = [5000, 15000, 30000];
const PAUSE_BETWEEN_MODS_MS = 10000;

const DEAD_AIR_IDS = new Set([
  "dead-air",
  "pip-boy-radio",
  "after-hours-fm",
  "beat-blocks",
  "broken-youth-radio",
  "frontline-fm",
  "iron-rain-fm",
  "wayfarer-radio",
  "zero-gravity",
]);

const CATEGORIES = {
  "dead-air": ["adventure", "utility"],
  "pip-boy-radio": ["equipment", "utility"],
  "after-hours-fm": ["adventure"],
  "beat-blocks": ["adventure"],
  "broken-youth-radio": ["adventure"],
  "frontline-fm": ["adventure"],
  "iron-rain-fm": ["adventure"],
  "wayfarer-radio": ["adventure"],
  "zero-gravity": ["adventure"],
};

const JAR_MATCH = {
  "dead-air": [/^dead_air-\d/i, /^dead-air-\d/i],
  "pip-boy-radio": [/pip.?boy/i],
  "after-hours-fm": [/after.?hours/i],
  "beat-blocks": [/block.?beats|beat.?blocks/i],
  "broken-youth-radio": [/broken.?youth/i],
  "frontline-fm": [/frontline/i],
  "iron-rain-fm": [/iron.?rain/i],
  "wayfarer-radio": [/wayfarer/i],
  "zero-gravity": [/zero.?gravity/i],
};

const JAR_DIRS = [
  "C:\\Users\\Ksivi\\curseforge\\minecraft\\Instances\\C.Ideas\\mods",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Frontline FM\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Wayfarer Radio\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air After Hours FM\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Iron Rain FM\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Broken Youth Radio\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Beat Blocks\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air Zero Gravity\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Pip-Boy Radio Conversion\\build\\libs",
];

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

function modTitle(mod) {
  return mod.mrTitle || mod.name;
}

function modDescription(mod) {
  return (mod.short || mod.name).slice(0, 250);
}

function modBody(mod) {
  return `${mod.short || mod.name}\n\nSee [CurseForge](${mod.cfUrl}) for full description.`;
}

function listJars() {
  const jars = [];
  for (const dir of JAR_DIRS) {
    if (!fs.existsSync(dir)) continue;
    for (const name of fs.readdirSync(dir)) {
      if (!name.endsWith(".jar") || /sources|javadoc/i.test(name)) continue;
      jars.push({ name, path: path.join(dir, name), mtime: fs.statSync(path.join(dir, name)).mtimeMs });
    }
  }
  return jars;
}

function findJar(id, jars) {
  const patterns = JAR_MATCH[id];
  if (!patterns) return null;
  let hits = jars.filter((j) => patterns.some((re) => re.test(j.name)));
  if (id === "dead-air") {
    hits = hits.filter((j) => !/after|block|broken|frontline|iron|wayfarer|zero|pip/i.test(j.name));
  }
  if (!hits.length) return null;
  return hits.sort((a, b) => b.mtime - a.mtime)[0];
}

function parseVersion(name) {
  const m = name.match(/(\d+\.\d+\.\d+(?:\+[^\s]+)?)/);
  return m ? m[1] : "1.0.0";
}

function uploadTimeoutMs(filePath) {
  const mb = fs.statSync(filePath).size / (1024 * 1024);
  // 2 minutes per MB, minimum 15 minutes, maximum 2 hours
  return Math.min(7200000, Math.max(900000, Math.ceil(mb * 120000)));
}

function isRetryableError(err) {
  if (!err) return false;
  const msg = String(err.message || err);
  if (/fetch failed|timeout|ECONNRESET|ETIMEDOUT|ENOTFOUND|socket hang up/i.test(msg)) return true;
  if (err.status >= 500) return true;
  if (err.status === 429) return true;
  return false;
}

async function withRetry(label, fn) {
  let lastErr;
  for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastErr = err;
      if (attempt >= RETRY_DELAYS_MS.length || !isRetryableError(err)) throw err;
      const wait = RETRY_DELAYS_MS[attempt];
      console.log(`  retry ${label} in ${wait / 1000}s (${attempt + 1}/${RETRY_DELAYS_MS.length}): ${err.message}`);
      await sleep(wait);
    }
  }
  throw lastErr;
}

async function mrFetch(token, method, apiPath, { json, form, timeoutMs = 600000 } = {}) {
  const headers = { Authorization: token, "User-Agent": "ESS-Dead-Air-Modrinth/1.0" };
  let body;
  if (form) body = form;
  else if (json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(json);
  }
  const res = await fetch(`https://api.modrinth.com/v2${apiPath}`, {
    method,
    headers,
    body,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    const err = new Error(`${method} ${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

async function getProject(token, slug) {
  try {
    return await withRetry(`GET /project/${slug}`, () => mrFetch(token, "GET", `/project/${slug}`));
  } catch (e) {
    if (e.status === 404) return null;
    throw e;
  }
}

async function listProjectVersions(token, projectId) {
  return withRetry(`GET /project/${projectId}/version`, () =>
    mrFetch(token, "GET", `/project/${projectId}/version`)
  );
}

async function patchProject(token, projectId, mod) {
  const payload = {
    title: modTitle(mod),
    description: modDescription(mod),
    body: modBody(mod),
  };
  return withRetry(`PATCH /project/${projectId}`, () =>
    mrFetch(token, "PATCH", `/project/${projectId}`, { json: payload })
  );
}

async function ensureProject(token, mod) {
  const slug = mod.cfSlug;
  const existing = await getProject(token, slug);
  if (existing) return { id: existing.id, slug: existing.slug, created: false };

  const payload = {
    slug,
    title: modTitle(mod),
    description: modDescription(mod),
    body: modBody(mod),
    categories: CATEGORIES[mod.id] || ["utility"],
    client_side: "optional",
    server_side: "optional",
    license_id: "LicenseRef-All-Rights-Reserved",
    project_type: "mod",
    is_draft: true,
    initial_versions: [],
    source_url: mod.github,
    issues_url: mod.github ? `${mod.github}/issues` : undefined,
    discord_url: "https://discord.gg/dhnAuhNMQH",
  };

  const form = new FormData();
  form.append("data", new Blob([JSON.stringify(payload)], { type: "application/json" }));
  const iconPath = path.join(ICON_DIR, `${mod.id}.png`);
  if (fs.existsSync(iconPath)) {
    const stat = fs.statSync(iconPath);
    if (stat.size <= 256 * 1024) {
      form.append("icon", new Blob([fs.readFileSync(iconPath)], { type: "image/png" }), `${mod.id}.png`);
    } else {
      console.log(`  skip icon ${mod.id}.png (${Math.round(stat.size / 1024)}KiB > 256KiB)`);
    }
  }
  const created = await withRetry(`POST /project ${slug}`, () => mrFetch(token, "POST", "/project", { form }));
  return { id: created.id, slug: created.slug, created: true };
}

async function versionExists(token, projectId, versionNumber) {
  const versions = await listProjectVersions(token, projectId);
  return (versions || []).some((v) => v.version_number === versionNumber);
}

async function uploadVersion(token, projectId, jar, changelog) {
  const versionNumber = parseVersion(jar.name);
  const sizeMb = Math.round((fs.statSync(jar.path).size / (1024 * 1024)) * 10) / 10;
  const timeoutMs = uploadTimeoutMs(jar.path);
  console.log(`  uploading ${jar.name} (${sizeMb}MB, timeout ${Math.round(timeoutMs / 60000)}m)`);
  const data = {
    name: versionNumber,
    version_number: versionNumber,
    changelog: changelog || `Forge 1.20.1 release ${versionNumber}`,
    dependencies: [],
    game_versions: [/1\.21/.test(jar.name) ? "1.21.1" : "1.20.1"],
    version_type: "release",
    loaders: [/neoforge/i.test(jar.name) ? "neoforge" : "forge"],
    featured: true,
    status: "listed",
    project_id: projectId,
    file_parts: ["file"],
    primary_file: "file",
  };
  const form = new FormData();
  form.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }));
  form.append("file", await openAsBlob(jar.path), jar.name);
  return withRetry(`POST /version ${versionNumber}`, () =>
    mrFetch(token, "POST", "/version", { form, timeoutMs })
  );
}

function changelogFor(mod) {
  if (mod.id === "dead-air") return "Forge 1.20.1 — Dead Air with Radio Towers integration.";
  return `Dead Air expansion — ${modTitle(mod)}.`;
}

async function main() {
  const token = readToken();
  let mods = JSON.parse(fs.readFileSync(MAP, "utf8").replace(/^\uFEFF/, "")).filter((m) => DEAD_AIR_IDS.has(m.id));
  if (ONLY_SLUGS) {
    mods = mods.filter((m) => ONLY_SLUGS.has(m.cfSlug));
    if (!mods.length) {
      throw new Error(`No mods matched --only=${[...ONLY_SLUGS].join(",")}`);
    }
  }
  const jars = listJars();
  const results = [];

  for (let i = 0; i < mods.length; i++) {
    const mod = mods[i];
    const row = { id: mod.id, slug: mod.cfSlug, name: modTitle(mod) };
    try {
      const jar = findJar(mod.id, jars);
      row.jar = jar?.name || null;

      if (DRY) {
        const existing = await getProject(token, mod.cfSlug);
        row.projectId = existing?.id || null;
        row.exists = Boolean(existing);
        console.log(
          `DRY ${modTitle(mod)} ${existing ? `exists (${existing.id})` : "would create"} jar=${jar?.name || "none"}`
        );
        row.ok = true;
      } else if (TITLES_ONLY) {
        const existing = await getProject(token, mod.cfSlug);
        if (!existing) {
          row.skipped = "project not found";
          console.log(`SKIP ${modTitle(mod)} — project not found`);
        } else {
          await patchProject(token, existing.id, mod);
          row.projectId = existing.id;
          row.patched = true;
          console.log(`PATCHED ${modTitle(mod)} -> ${existing.slug}`);
        }
        row.ok = true;
      } else {
        const proj = await ensureProject(token, mod);
        row.projectId = proj.id;
        row.modrinthSlug = proj.slug;
        row.created = proj.created;
        row.modrinthUrl = `https://modrinth.com/mod/${proj.slug}`;
        console.log(`${proj.created ? "CREATED" : "FOUND"} ${modTitle(mod)} -> ${proj.slug}`);

        await patchProject(token, proj.id, mod);
        row.patched = true;
        console.log(`  title -> ${modTitle(mod)}`);

        if (jar) {
          const versionNumber = parseVersion(jar.name);
          if (await versionExists(token, proj.id, versionNumber)) {
            row.skippedVersion = versionNumber;
            console.log(`  skip upload — version ${versionNumber} already exists`);
          } else {
            const ver = await uploadVersion(token, proj.id, jar, changelogFor(mod));
            row.versionId = ver.id;
            console.log(`  uploaded ${ver.version_number}`);
          }
        } else {
          console.log("  no JAR — project only");
        }
        row.ok = true;
      }
    } catch (e) {
      row.ok = false;
      row.error = String(e.message || e);
      console.log(`FAIL ${modTitle(mod)}: ${row.error}`);
    }
    results.push(row);

    if (!DRY && i < mods.length - 1) {
      await sleep(PAUSE_BETWEEN_MODS_MS);
    }
  }

  fs.writeFileSync(OUT, JSON.stringify(results, null, 2));
  console.log("\nWrote", OUT);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
