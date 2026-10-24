const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
const token = fs.readFileSync(SECRETS, "utf8").match(/### MODRINTH_TOKEN\s*\r?\n([^\r\n`]+)/)[1].trim();
const headers = { Authorization: token, "Content-Type": "application/json", "User-Agent": "ESS/1.0" };

async function show(label) {
  const p = await fetch("https://api.modrinth.com/v2/project/dead-air", { headers: { Authorization: token } }).then((r) => r.json());
  console.log(label, { status: p.status, requested: p.requested_status, queued: p.queued });
}

async function patch(body) {
  const p = await fetch("https://api.modrinth.com/v2/project/dead-air", { headers: { Authorization: token } }).then((r) => r.json());
  const res = await fetch(`https://api.modrinth.com/v2/project/${p.id}`, { method: "PATCH", headers, body: JSON.stringify(body) });
  console.log("PATCH", JSON.stringify(body), "->", res.status, (await res.text()).slice(0, 200));
}

async function main() {
  await show("before");
  await patch({ requested_status: null });
  await show("after null");
  await patch({ requested_status: "approved" });
  await show("after approved");
  await patch({ status: "approved" });
  await show("after status approved");
}

main();
