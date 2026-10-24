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
  const headers = { Authorization: token, "User-Agent": "ESS-Modrinth-Team/1.0" };
  const slug = process.argv[2] || "dead-air";
  const p = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers }).then((r) => r.json());
  console.log("Project:", slug, p.id, "status:", p.status, "team:", p.team);
  const members = await fetch(`https://api.modrinth.com/v2/team/${p.team}/members`, { headers }).then((r) => r.json());
  for (const m of members) {
    console.log(" ", m.role, m.user.username, "perms:", m.permissions);
  }
}

main().catch(console.error);
