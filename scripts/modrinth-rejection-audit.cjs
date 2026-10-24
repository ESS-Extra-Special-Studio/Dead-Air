/**
 * Audit Modrinth rejection reasons + environment/loader state for ESS Dead Air projects.
 *   node scripts/modrinth-rejection-audit.cjs
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
const OUT = path.join(__dirname, "..", "modrinth-rejection-audit.json");

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

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function api(token, apiPath) {
  const res = await fetch(`https://api.modrinth.com${apiPath}`, {
    headers: { Authorization: token, "User-Agent": "ESS-Modrinth-RejectionAudit/1.0" },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${apiPath} -> ${res.status}: ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
}

function summarizeModeration(messages) {
  return (messages || [])
    .filter((m) => m.body && m.body.type === "text")
    .map((m) => {
      const body = (m.body.body || "").trim();
      const headings = [...body.matchAll(/^##\s+(.+)$/gm)].map((x) => x[1].trim());
      return {
        created: m.created,
        headings,
        preview: body.replace(/\s+/g, " ").slice(0, 400),
      };
    });
}

async function main() {
  const token = readToken();
  const results = [];

  for (const slug of SLUGS) {
    const p = await api(token, `/v2/project/${slug}`);
    const v3 = await api(token, `/v3/project/${slug}`);
    const vers = await api(token, `/v2/project/${p.id}/version`);
    let moderation = [];
    if (p.thread_id) {
      const th = await api(token, `/v2/thread/${p.thread_id}`);
      moderation = summarizeModeration(th.messages);
    }

    const row = {
      slug,
      id: p.id,
      status: p.status,
      environment: v3.environment,
      side_types_migration_review_status: v3.side_types_migration_review_status,
      loaders: [...new Set((vers || []).flatMap((v) => v.loaders || []))],
      game_versions: [...new Set((vers || []).flatMap((v) => v.game_versions || []))],
      version_environments: [...new Set((vers || []).map((v) => v.environment))],
      body_length: (p.body || "").length,
      body_preview: (p.body || "").replace(/\s+/g, " ").trim().slice(0, 160),
      moderation_headings: [...new Set(moderation.flatMap((m) => m.headings))],
      moderation,
    };
    results.push(row);
    console.log(
      `${slug}: ${row.status} | env=${JSON.stringify(row.environment)} | loaders=${row.loaders.join(",")} | body=${row.body_length} | reasons=${row.moderation_headings.join("; ") || "(none)"}`
    );
  }

  fs.writeFileSync(OUT, JSON.stringify({ at: new Date().toISOString(), results }, null, 2));
  console.log("\nWrote", OUT);
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
