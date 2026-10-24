/**
 * Set Modrinth version+project environment where still "unknown".
 *
 *   node scripts/fix-modrinth-unknown-environments.cjs --dry-run
 *   node scripts/fix-modrinth-unknown-environments.cjs
 *   node scripts/fix-modrinth-unknown-environments.cjs --only esl-extra-special-lib
 *   node scripts/fix-modrinth-unknown-environments.cjs --resubmit
 *
 * Environment defaults to client_and_server; override with --env client_only etc.
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
const OUT = path.join(__dirname, "..", "modrinth-unknown-env-fix.json");

const DRY = process.argv.includes("--dry-run");
const RESUBMIT = process.argv.includes("--resubmit");
const ENV = (() => {
  const i = process.argv.indexOf("--env");
  return i === -1 ? "client_and_server" : process.argv[i + 1];
})();
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

/** Known ESS / Dead Air Modrinth slugs (stack + DA family). */
const SLUGS = [
  "esl-extra-special-lib",
  "esc-extra-special-core",
  "esh-extra-special-hub",
  "esg-extra-special-gui",
  "whatlib",
  "pantheonapi",
  "aegis-accord",
  "hermes",
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
  "calm-the-leaks",
  "lootr-liaison",
  "dead-letters",
  "nag",
];

/** Soundtrack packs / client-ish overrides if needed later. */
const ENV_BY_SLUG = {
  // leave empty — client_and_server is correct for ESL/ESC/ESH and DA stack
};

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function api(token, method, apiPath, body) {
  const res = await fetch(`https://api.modrinth.com${apiPath}`, {
    method,
    headers: {
      Authorization: token,
      "User-Agent": "ESS-Modrinth-FixUnknownEnv/1.0",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : null;
}

function needsEnvFix(env) {
  if (env == null) return true;
  if (env === "unknown") return true;
  if (Array.isArray(env) && (env.length === 0 || env.includes("unknown"))) return true;
  return false;
}

async function fixSlug(token, slug) {
  const env = ENV_BY_SLUG[slug] || ENV;
  const row = { slug, targetEnv: env };

  let v2;
  try {
    v2 = await api(token, "GET", `/v2/project/${slug}`);
  } catch (e) {
    row.skipped = e.message;
    return row;
  }

  const v3 = await api(token, "GET", `/v3/project/${slug}`);
  const vers = await api(token, "GET", `/v2/project/${v2.id}/version`);

  row.status = v2.status;
  row.projectEnvBefore = v3.environment;
  row.sideReview = v3.side_types_migration_review_status;
  row.versions = (vers || []).map((v) => ({
    id: v.id,
    version_number: v.version_number,
    environment: v.environment,
    loaders: v.loaders,
  }));

  const versionFixIds = (vers || []).filter((v) => needsEnvFix(v.environment)).map((v) => v.id);
  const projectNeeds = needsEnvFix(v3.environment);

  row.projectNeedsFix = projectNeeds;
  row.versionsNeedingFix = versionFixIds.length;

  if (!projectNeeds && versionFixIds.length === 0) {
    row.action = "already set";
    return row;
  }

  if (DRY) {
    row.action = "would fix";
    return row;
  }

  for (const id of versionFixIds) {
    await api(token, "PATCH", `/v3/version/${id}`, { environment: env });
  }
  row.versionsPatched = versionFixIds.length;

  // Project environment is an array in v3
  await api(token, "PATCH", `/v3/project/${v2.id}`, {
    environment: [env],
    side_types_migration_review_status: "reviewed",
  });
  row.projectPatched = true;

  if (RESUBMIT && (v2.status === "rejected" || v2.status === "draft")) {
    await api(token, "PATCH", `/v2/project/${v2.id}`, {
      requested_status: "approved",
      status: "processing",
    });
    row.resubmitted = true;
  }

  const after = await api(token, "GET", `/v3/project/${slug}`);
  const afterVers = await api(token, "GET", `/v2/project/${v2.id}/version`);
  row.projectEnvAfter = after.environment;
  row.versionEnvsAfter = [...new Set((afterVers || []).map((v) => v.environment))];
  row.action = RESUBMIT && row.resubmitted ? "fixed + resubmitted" : "fixed";
  return row;
}

async function main() {
  const token = readToken();
  const slugs = ONLY ? [...ONLY] : SLUGS;
  const results = [];

  for (const slug of slugs) {
    try {
      const row = await fixSlug(token, slug);
      results.push(row);
      const flag =
        row.skipped || row.error
          ? "ERR"
          : DRY
            ? "DRY"
            : row.action === "already set"
              ? "OK "
              : "FIX";
      console.log(
        `${flag}  ${slug} — ${row.skipped || row.action}` +
          (row.versionsNeedingFix != null ? ` (versions needing fix: ${row.versionsNeedingFix})` : "") +
          (row.projectEnvAfter ? ` → project ${JSON.stringify(row.projectEnvAfter)}` : "")
      );
    } catch (e) {
      results.push({ slug, error: e.message });
      console.log(`ERR  ${slug} — ${e.message}`);
    }
  }

  fs.writeFileSync(
    OUT,
    JSON.stringify({ at: new Date().toISOString(), dryRun: DRY, env: ENV, resubmit: RESUBMIT, results }, null, 2)
  );
  console.log("\nWrote", OUT);
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
