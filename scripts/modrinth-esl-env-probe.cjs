/**
 * Probe ESL (and optionally more) for environment / side migration state.
 *   node scripts/modrinth-esl-env-probe.cjs [slug...]
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

const DEFAULT = [
  "esl-extra-special-lib",
  "esc-extra-special-core",
  "esh-extra-special-hub",
  "dead-air",
];

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function api(token, apiPath) {
  const res = await fetch(`https://api.modrinth.com${apiPath}`, {
    headers: { Authorization: token, "User-Agent": "ESS-Modrinth-EslProbe/1.0" },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : null;
}

async function main() {
  const token = readToken();
  const slugs = process.argv.slice(2).length ? process.argv.slice(2) : DEFAULT;

  for (const slug of slugs) {
    const v2 = await api(token, `/v2/project/${slug}`);
    const v3 = await api(token, `/v3/project/${slug}`);
    const vers = await api(token, `/v2/project/${v2.id}/version`);
    // also try v3 versions if different
    let v3vers = null;
    try {
      v3vers = await api(token, `/v3/project/${v2.id}/version`);
    } catch (_) {}

    console.log("\n###", slug);
    console.log(
      JSON.stringify(
        {
          status: v2.status,
          thread_id: v2.thread_id,
          v2_environment: v2.environment,
          v2_client_side: v2.client_side,
          v2_server_side: v2.server_side,
          v3_environment: v3.environment,
          side_types_migration_review_status: v3.side_types_migration_review_status,
          versions: (vers || []).map((v) => ({
            id: v.id,
            version_number: v.version_number,
            loaders: v.loaders,
            game_versions: v.game_versions,
            environment: v.environment,
            client_side: v.client_side,
            server_side: v.server_side,
          })),
          v3_version_envs: Array.isArray(v3vers)
            ? v3vers.map((v) => ({
                id: v.id,
                version_number: v.version_number,
                environment: v.environment,
              }))
            : typeof v3vers,
        },
        null,
        2
      )
    );

    if (v2.thread_id) {
      const th = await api(token, `/v2/thread/${v2.thread_id}`);
      const texts = (th.messages || [])
        .filter((m) => m.body?.type === "text")
        .map((m) => (m.body.body || "").replace(/\s+/g, " ").slice(0, 350));
      console.log("moderation:", texts.length ? texts : "(no text messages)");
    }
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
