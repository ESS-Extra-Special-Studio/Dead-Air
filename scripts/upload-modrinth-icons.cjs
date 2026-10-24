/**
 * Upload resized icons to Modrinth projects under review.
 *   node scripts/upload-modrinth-icons.cjs
 *   node scripts/upload-modrinth-icons.cjs --dry-run
 */
const fs = require("fs");
const path = require("path");

const SECRETS = path.join(
  "C:",
  "Users",
  "Ksivi",
  "Desktop",
  "Extra Special Studio",
  "Where we're at now",
  "SECRETS.local.md"
);
const ICON_DIR = path.join("C:", "Users", "Ksivi", "IdeaProjects", "ess-website", "public", "images", "mods");
const DRY = process.argv.includes("--dry-run");

const ICONS = [
  { modId: "after-hours-fm", slug: "dead-air-after-hours-fm" },
  { modId: "beat-blocks", slug: "dead-air-block-beats" },
  { modId: "broken-youth-radio", slug: "dead-air-broken-youth-radio" },
  { modId: "iron-rain-fm", slug: "dead-air-iron-rain-fm" },
];

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

async function main() {
  const token = readToken();
  for (const { modId, slug } of ICONS) {
    const iconPath = path.join(ICON_DIR, `${modId}.png`);
    if (!fs.existsSync(iconPath)) {
      console.log("SKIP", slug, "— no icon");
      continue;
    }
    const size = fs.statSync(iconPath).size;
    if (size > 256 * 1024) {
      console.log("SKIP", slug, `— still too large (${Math.round(size / 1024)}KiB)`);
      continue;
    }
    if (DRY) {
      console.log("DRY", slug, `— would upload ${Math.round(size / 1024)}KiB icon`);
      continue;
    }
    const project = await fetch(`https://api.modrinth.com/v2/project/${slug}`, {
      headers: { Authorization: token, "User-Agent": "ESS-Modrinth-Icons/1.0" },
    }).then((r) => r.json());

    const res = await fetch(`https://api.modrinth.com/v3/project/${project.id}/icon?ext=png`, {
      method: "PATCH",
      headers: {
        Authorization: token,
        "Content-Type": "image/png",
        "User-Agent": "ESS-Modrinth-Icons/1.0",
      },
      body: fs.readFileSync(iconPath),
    });
    console.log(res.ok ? "OK" : "ERR", slug, "->", res.status, `(${Math.round(size / 1024)}KiB)`);
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
