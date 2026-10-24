const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

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

async function main() {
  const token = readToken();
  const headers = { Authorization: token, "User-Agent": "ESS-Modrinth-Moderation/1.0" };

  for (const slug of SLUGS) {
    const p = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers }).then((r) => r.json());
    const vers = await fetch(`https://api.modrinth.com/v2/project/${p.id}/version`, { headers }).then((r) => r.json());

    console.log("\n===", slug, "===");
    console.log({
      id: p.id,
      status: p.status,
      requested_status: p.requested_status,
      queued: p.queued,
      approved: p.approved,
      published: p.published,
      moderation_message: p.moderation_message,
      moderation_message_body: p.moderation_message_body,
      has_icon: Boolean(p.icon_url),
      has_description: Boolean(p.body),
      license: p.license,
      categories: p.categories,
      client_side: p.client_side,
      server_side: p.server_side,
      versions: (vers || []).map((v) => ({
        version_number: v.version_number,
        status: v.status,
        version_type: v.version_type,
        loaders: v.loaders,
        game_versions: v.game_versions,
      })),
    });
  }
}

main().catch(console.error);
