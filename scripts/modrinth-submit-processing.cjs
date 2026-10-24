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
  const slug = process.argv[2] || "dead-air";

  const p = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers: { Authorization: token } }).then((r) => r.json());
  console.log("before:", { status: p.status, requested: p.requested_status, queued: p.queued });

  const res = await fetch(`https://api.modrinth.com/v2/project/${p.id}`, {
    method: "PATCH",
    headers,
    body: JSON.stringify({ status: "processing", requested_status: "approved" }),
  });
  console.log("PATCH ->", res.status, (await res.text()).slice(0, 300));

  const after = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers: { Authorization: token } }).then((r) => r.json());
  console.log("after:", { status: after.status, requested: after.requested_status, queued: after.queued });
}

main().catch(console.error);
