const fs = require("fs");
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}
const SLUGS = [
  "apocalypse-structures-radio-towers-and-airdrops",
  "dead-air",
  "dead-air-pip-boy-radio-conversion",
  "dead-air-after-hours-fm",
  "dead-air-block-beats",
  "dead-air-broken-youth-radio",
  "dead-air-frontline-fm",
  "dead-air-iron-rain-fm",
  "dead-air-wayfarer-radio",
  "dead-air-zero-gravity",
];
async function main() {
  const token = readToken();
  const headers = { Authorization: token, "User-Agent": "ESS-Modrinth-Status/1.0" };
  for (const slug of SLUGS) {
    try {
      const p = await fetch(`https://api.modrinth.com/v2/project/${slug}`, { headers }).then(async (r) => {
        if (!r.ok) return { missing: true, status: r.status };
        return r.json();
      });
      if (p.missing) {
        console.log(slug, "— NOT FOUND");
        continue;
      }
      const vers = await fetch(`https://api.modrinth.com/v2/project/${p.id}/version`, { headers }).then((r) => r.json());
      const listed = (vers || []).filter((v) => v.status !== "draft" && v.status !== "archived");
      const latest = listed[0];
      console.log(
        slug,
        "—",
        p.status || "?",
        p.queued ? `(queued ${p.queued.slice(0, 10)})` : "",
        "—",
        listed.length ? `${listed.length} version(s), latest: ${latest.version_number}` : "NO VERSIONS"
      );
    } catch (e) {
      console.log(slug, "— ERROR:", e.message);
    }
  }
}
main().catch(console.error);
