const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
const token = fs.readFileSync(SECRETS, "utf8").match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/)[1].trim();
const headers = { Authorization: token, "User-Agent": "ESS/1.0" };

async function main() {
  const slug = process.argv[2] || "dead-air";
  const p = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers }).then((r) => r.json());
  console.log("project:", {
    status: p.status,
    requested_status: p.requested_status,
    queued: p.queued,
    thread_id: p.thread_id,
    environment: p.environment,
    monetization_status: p.monetization_status,
  });

  if (p.thread_id) {
    const thread = await fetch(`https://api.modrinth.com/v2/thread/${p.thread_id}`, { headers }).then(async (r) => ({
      status: r.status,
      body: r.ok ? await r.json() : await r.text(),
    }));
    console.log("\nthread:", JSON.stringify(thread, null, 2).slice(0, 2000));
  }

  const v3 = await fetch(`https://api.modrinth.com/v3/project/${slug}`, { headers }).then((r) => r.json());
  console.log("\nv3:", {
    status: v3.status,
    requested_status: v3.requested_status,
    queued: v3.queued,
    side_types_migration_review_status: v3.side_types_migration_review_status,
    environment: v3.environment,
  });
}

main().catch(console.error);
