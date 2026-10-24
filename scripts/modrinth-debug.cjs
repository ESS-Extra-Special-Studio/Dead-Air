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
  const headers = { Authorization: token, "User-Agent": "ESS-Modrinth-Debug/1.0" };
  const user = await fetch("https://api.modrinth.com/v2/user", { headers }).then((r) => r.json());
  console.log("User:", user.username, user.id);
  const projects = await fetch("https://api.modrinth.com/v2/user/projects", { headers }).then(async (r) => {
    const t = await r.text();
    if (!r.ok) { console.log("user/projects failed:", r.status, t); return []; }
    return JSON.parse(t);
  });
  console.log("\nUser projects (" + projects.length + "):");
  for (const p of projects) {
    console.log(" -", p.slug, p.id, p.title);
  }
  const slug = "apocalypse-structures-radio-towers-and-airdrops";
  const proj = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers }).then(async (r) => ({
    ok: r.ok,
    body: await r.json(),
  }));
  console.log("\nRadio Towers project:", proj.ok ? "found" : "missing");
  if (proj.ok) {
    console.log("  id:", proj.body.id);
    console.log("  team:", proj.body.team);
    console.log("  organization:", proj.body.organization);
    const members = await fetch(`https://api.modrinth.com/v2/team/${proj.body.team}/members`, { headers }).then(async (r) => {
      const t = await r.text();
      if (!r.ok) return { error: t };
      return JSON.parse(t);
    });
    console.log("  team members:", JSON.stringify(members, null, 2));
  }
}
main().catch(console.error);
