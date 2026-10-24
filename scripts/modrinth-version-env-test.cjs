const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function main() {
  const token = readToken();
  const headers = { Authorization: token, "Content-Type": "application/json", "User-Agent": "ESS/1.0" };
  const versionId = process.argv[2] || "9zuo44Cm";

  const res = await fetch(`https://api.modrinth.com/v3/version/${versionId}`, {
    method: "PATCH",
    headers,
    body: JSON.stringify({ environment: "client_and_server" }),
  });
  console.log("PATCH version ->", res.status, await res.text());

  const v = await fetch(`https://api.modrinth.com/v2/version/${versionId}`, {
    headers: { Authorization: token },
  }).then((r) => r.json());
  console.log("after:", { environment: v.environment, client_side: v.client_side, server_side: v.server_side });

  const p = await fetch(`https://api.modrinth.com/v2/project/dead-air`, {
    headers: { Authorization: token },
  }).then((r) => r.json());
  console.log("project:", { environment: p.environment, side_review: p.side_types_migration_review_status });
}

main().catch(console.error);
