const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function api(token, base, method, path, body) {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: {
      Authorization: token,
      "User-Agent": "ESS-Modrinth-V3/1.0",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  console.log(`${method} ${path} -> ${res.status}`);
  if (text) console.log(text.slice(0, 1200));
  return { status: res.status, text };
}

async function main() {
  const token = readToken();
  const slug = process.argv[2] || "dead-air";

  console.log("=== v2 GET ===");
  await api(token, "https://api.modrinth.com/v2", "GET", `/project/${slug}`);

  console.log("\n=== v3 GET ===");
  await api(token, "https://api.modrinth.com/v3", "GET", `/project/${slug}`);

  const v2 = await fetch(`https://api.modrinth.com/v2/project/${slug}`, {
    headers: { Authorization: token },
  }).then((r) => r.json());

  console.log("\n=== v3 PATCH environment + review ===");
  await api(token, "https://api.modrinth.com/v3", "PATCH", `/project/${v2.id}`, {
    environment: ["client_and_server"],
    side_types_migration_review_status: "reviewed",
  });

  console.log("\n=== v2 PATCH submit ===");
  await api(token, "https://api.modrinth.com/v2", "PATCH", `/project/${v2.id}`, {
    requested_status: "draft",
  });
  await api(token, "https://api.modrinth.com/v2", "PATCH", `/project/${v2.id}`, {
    requested_status: "approved",
  });

  console.log("\n=== v2 GET after ===");
  const after = await fetch(`https://api.modrinth.com/v2/project/${slug}`, {
    headers: { Authorization: token },
  }).then((r) => r.json());
  console.log({
    status: after.status,
    requested_status: after.requested_status,
    queued: after.queued,
    environment: after.environment,
    client_side: after.client_side,
    server_side: after.server_side,
  });
}

main().catch(console.error);
