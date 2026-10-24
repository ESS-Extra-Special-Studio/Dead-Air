const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function main() {
  const slug = process.argv[2] || "dead-air";
  const headers = { Authorization: readToken(), "User-Agent": "ESS/1.0" };
  const p = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers }).then((r) => r.json());
  const pick = [
    "id", "status", "requested_status", "queued", "approved", "thread_id",
    "environment", "client_side", "server_side", "icon_url", "description", "body",
    "license", "categories", "game_versions", "loaders", "moderator_message",
  ];
  const out = {};
  for (const k of pick) out[k] = p[k];
  console.log(JSON.stringify(out, null, 2));

  // compare to a known public project
  const pub = await fetch("https://api.modrinth.com/v2/project/sodium").then((r) => r.json());
  console.log("\nPublic ref (sodium):", {
    status: pub.status,
    environment: pub.environment,
    client_side: pub.client_side,
    server_side: pub.server_side,
    thread_id: pub.thread_id,
  });
}

main().catch(console.error);
