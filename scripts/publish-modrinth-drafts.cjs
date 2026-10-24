/**
 * Submit draft Modrinth projects for review (public listing).
 *
 *   node scripts/publish-modrinth-drafts.cjs
 *   node scripts/publish-modrinth-drafts.cjs --dry-run
 *   node scripts/publish-modrinth-drafts.cjs --only dead-air,dead-air-after-hours-fm
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

const DEFAULT_SLUGS = [
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

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found in SECRETS.local.md");
  return m[1].trim();
}

async function mrFetch(token, method, apiPath, { json } = {}) {
  const res = await fetch(`https://api.modrinth.com/v2${apiPath}`, {
    method,
    headers: {
      Authorization: token,
      "User-Agent": "ESS-Modrinth-Publish/1.0",
      ...(json ? { "Content-Type": "application/json" } : {}),
    },
    body: json ? JSON.stringify(json) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
  }
  return text ? JSON.parse(text) : null;
}

async function main() {
  const token = readToken();
  const slugs = ONLY ? [...ONLY] : DEFAULT_SLUGS;
  const results = [];

  for (const slug of slugs) {
    const row = { slug };
    try {
      const project = await mrFetch(token, "GET", `/project/${slug}`);
      row.title = project.title;
      row.statusBefore = project.status;
      row.requestedBefore = project.requested_status;
      row.queued = project.queued;

      const versions = await mrFetch(token, "GET", `/project/${project.id}/version`);
      row.versionCount = (versions || []).length;

      if (project.status === "approved" || project.status === "listed") {
        row.action = "already public";
        console.log(`OK  ${slug} — already ${project.status}`);
        results.push(row);
        continue;
      }

      if (project.status === "processing") {
        row.action = "already in review";
        console.log(`…  ${slug} — already processing (submitted ${project.queued || "?"})`);
        results.push(row);
        continue;
      }

      if (!row.versionCount) {
        row.action = "skipped — no versions";
        console.log(`SKIP ${slug} — no versions uploaded`);
        results.push(row);
        continue;
      }

      if (DRY) {
        row.action = "would submit for review";
        console.log(`DRY ${slug} — would PATCH requested_status=approved (${row.versionCount} version(s))`);
        results.push(row);
        continue;
      }

      await mrFetch(token, "PATCH", `/project/${project.id}`, {
        json: { requested_status: "approved", status: "processing" },
      });

      const after = await mrFetch(token, "GET", `/project/${slug}`);
      row.statusAfter = after.status;
      row.requestedAfter = after.requested_status;
      row.queuedAfter = after.queued;
      row.action = "submitted";
      console.log(`SUBMIT ${slug} — ${row.statusBefore} -> ${after.status} (queued ${after.queued || "now"})`);
      results.push(row);
    } catch (e) {
      row.error = e.message;
      console.log(`ERR ${slug} — ${e.message}`);
      results.push(row);
    }
  }

  const out = path.join(__dirname, "..", "modrinth-publish-results.json");
  fs.writeFileSync(out, JSON.stringify({ at: new Date().toISOString(), dryRun: DRY, results }, null, 2));
  console.log("\nWrote", out);
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
