/** One-off: toggle requested_status to re-enter moderation queue */
const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function mrFetch(token, method, apiPath, { json } = {}) {
  const res = await fetch(`https://api.modrinth.com/v2${apiPath}`, {
    method,
    headers: {
      Authorization: token,
      "User-Agent": "ESS-Modrinth-Retrigger/1.0",
      ...(json ? { "Content-Type": "application/json" } : {}),
    },
    body: json ? JSON.stringify(json) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : null;
}

async function show(token, slug) {
  const p = await mrFetch(token, "GET", `/project/${slug}`);
  console.log(slug, { status: p.status, requested: p.requested_status, queued: p.queued, approved: p.approved });
  return p;
}

async function main() {
  const token = readToken();
  const slug = process.argv[2] || "dead-air";
  const p = await show(token, slug);

  console.log("\n1) PATCH requested_status -> draft");
  await mrFetch(token, "PATCH", `/project/${p.id}`, { json: { requested_status: "draft" } });
  await show(token, slug);

  console.log("\n2) PATCH requested_status -> approved");
  await mrFetch(token, "PATCH", `/project/${p.id}`, { json: { requested_status: "approved" } });
  await show(token, slug);
}

main().catch(console.error);
