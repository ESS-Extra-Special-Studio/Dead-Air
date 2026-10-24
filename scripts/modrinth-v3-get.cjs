const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
const token = fs.readFileSync(SECRETS, "utf8").match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/)[1].trim();
const headers = { Authorization: token };

async function main() {
  const v3 = await fetch("https://api.modrinth.com/v3/project/dead-air", { headers }).then((r) => r.json());
  console.log("v3 project:", {
    environment: v3.environment,
    side_types_migration_review_status: v3.side_types_migration_review_status,
    status: v3.status,
    queued: v3.queued,
  });

  const vers = await fetch(`https://api.modrinth.com/v2/project/${v3.id}/version`, { headers }).then((r) => r.json());
  for (const v of vers) {
    console.log("version", v.version_number, { id: v.id, environment: v.environment, status: v.status });
  }
}

main();
