const fs = require("fs");
const path = require("path");

const studio = path.join(process.env.USERPROFILE, "Desktop", "Extra Special Studio");
const ess = fs.readdirSync(studio).find((n) => n.startsWith("Where we"));
const text = fs.readFileSync(path.join(studio, ess, "SECRETS.local.md"), "utf8");

function secret(h) {
  const m = text.match(new RegExp(`### ${h}\\s*\\r?\\n\`\`\`\\s*\\r?\\n([^\\r\\n\`]+)`));
  if (!m) throw new Error(h);
  return m[1].trim();
}

const key = secret("CURSEFORGE_TOKEN");
const author = secret("CURSEFORGE_AUTHOR_TOKEN");

async function search(slug) {
  const url = `https://api.curseforge.com/v1/mods/search?gameId=432&slug=${encodeURIComponent(slug)}`;
  const r = await fetch(url, { headers: { "x-api-key": key, Accept: "application/json" } });
  const j = await r.json();
  const d = (j.data || [])[0];
  console.log(slug, d ? { id: d.id, name: d.name, slug: d.slug } : "NOT FOUND", "status", r.status);
  return d?.id;
}

async function tryAuthor(url) {
  const r = await fetch(url, { headers: { "X-Api-Token": author, Accept: "application/json" } });
  console.log("author", url, r.status, (await r.text()).slice(0, 240));
}

(async () => {
  await search("dead-air");
  await search("apocalypse-structures-radio-towers-and-airdrops");
  for (const u of [
    "https://minecraft.curseforge.com/api/projects",
    "https://minecraft.curseforge.com/api/project",
    "https://authors.curseforge.com/_api/projects",
  ]) {
    await tryAuthor(u);
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
