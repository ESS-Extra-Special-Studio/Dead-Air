const fs = require("fs");
const path = require("path");
const SECRETS = path.join(
  process.env.USERPROFILE,
  "Desktop",
  "Extra Special Studio",
  "Where we're at now",
  "SECRETS.local.md"
);
const text = fs.readFileSync(SECRETS, "utf8");
const author = text.match(/### CURSEFORGE_AUTHOR_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/)[1].trim();
const core = text.match(/### CURSEFORGE_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/)[1].trim();

(async () => {
  for (const [label, url, headers] of [
    ["author game/versions", "https://minecraft.curseforge.com/api/game/versions", { "X-Api-Token": author }],
    ["author game/version-types", "https://minecraft.curseforge.com/api/game/version-types", { "X-Api-Token": author }],
  ]) {
    const r = await fetch(url, { headers: { Accept: "application/json", ...headers } });
    const t = await r.text();
    console.log(label, r.status, t.slice(0, 300));
    if (r.ok) {
      const j = JSON.parse(t);
      const arr = Array.isArray(j) ? j : j.data || [];
      for (const n of ["1.20.1", "1.21.1", "Forge", "NeoForge", "Client", "Server", "Java 17", "Java 21"]) {
        const hit = arr.find((v) => v.name === n || v.Name === n);
        if (hit) console.log(" ", n, "->", hit.id || hit.ID, JSON.stringify(hit).slice(0, 120));
      }
    }
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
