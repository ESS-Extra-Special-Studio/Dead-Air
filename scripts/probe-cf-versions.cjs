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
const core = text.match(/### CURSEFORGE_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/)[1].trim();

async function get(url) {
  const r = await fetch(url, { headers: { "x-api-key": core, Accept: "application/json" } });
  const t = await r.text();
  if (!r.ok) throw new Error(url + " " + r.status + " " + t.slice(0, 200));
  return JSON.parse(t);
}

(async () => {
  const j = await get("https://api.curseforge.com/v1/minecraft/version");
  console.log("mc/version count", (j.data || []).length);
  console.log("mc/version sample", JSON.stringify((j.data || []).slice(0, 3)));
  const g = await get("https://api.curseforge.com/v1/games/432/versions");
  for (const block of g.data || []) {
    const versions = block.versions || [];
    const hits = ["1.20.1", "1.21.1", "Forge", "NeoForge", "Client", "Server", "Java 17", "Java 21"].filter((x) =>
      versions.includes(x)
    );
    if (hits.length) console.log("block type", block.type, "hits", hits.join(","));
  }
  const vt = await get("https://api.curseforge.com/v1/games/432/version-types");
  console.log(
    "interesting types",
    JSON.stringify((vt.data || []).filter((t) => /Minecraft|Modloader|Java|Environment|Forge/i.test(t.name || "")))
  );
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
