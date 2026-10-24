/**
 * Upload only Radio Towers to the existing Modrinth project.
 */
const fs = require("fs");
const path = require("path");
const { assertModrinthUploadsNotPaused } = require("./modrinth-pause-guard.cjs");

assertModrinthUploadsNotPaused();

const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
const PROJECT_SLUG = "apocalypse-structures-radio-towers-and-airdrops";
const JAR_DIRS = [
  "C:\\Users\\Ksivi\\MCreatorWorkspaces\\radioos\\build\\libs",
  "C:\\Users\\Ksivi\\curseforge\\minecraft\\Instances\\C.Ideas\\mods",
];

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found");
  return m[1].trim();
}

function findJar() {
  const jars = [];
  for (const dir of JAR_DIRS) {
    if (!fs.existsSync(dir)) continue;
    for (const name of fs.readdirSync(dir)) {
      if (/^radiotowers-.*\.jar$/i.test(name)) {
        jars.push({ name, path: path.join(dir, name), mtime: fs.statSync(path.join(dir, name)).mtimeMs });
      }
    }
  }
  if (!jars.length) throw new Error("No radiotowers JAR found");
  return jars.sort((a, b) => b.mtime - a.mtime)[0];
}

async function main() {
  const token = readToken();
  const jar = findJar();
  const project = await fetch(`https://api.modrinth.com/v2/project/${PROJECT_SLUG}`, {
    headers: { Authorization: token },
  }).then(async (r) => {
    const t = await r.text();
    if (!r.ok) throw new Error(t);
    return JSON.parse(t);
  });
  const versionNumber = jar.name.match(/(\d+\.\d+\.\d+(?:\+[^\s]+)?)/)[1];
  const data = {
    name: versionNumber,
    version_number: versionNumber,
    changelog:
      "Maintenance build with Dead Air integration (Extra Special Studio collaborator).\n\nOriginal mod by [that1lilguy](https://www.curseforge.com/members/that1lilguy/projects).",
    dependencies: [],
    game_versions: ["1.20.1"],
    version_type: "release",
    loaders: ["forge"],
    featured: true,
    status: "listed",
    project_id: project.id,
    file_parts: ["file"],
    primary_file: "file",
  };
  const form = new FormData();
  form.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }));
  form.append("file", new Blob([fs.readFileSync(jar.path)]), jar.name);
  const ver = await fetch("https://api.modrinth.com/v2/version", {
    method: "POST",
    headers: { Authorization: token, "User-Agent": "ESS-Modrinth-Batch/1.0" },
    body: form,
  }).then(async (r) => {
    const t = await r.text();
    if (!r.ok) throw new Error(t);
    return JSON.parse(t);
  });
  console.log("Uploaded", ver.version_number, ver.id, "from", jar.name);
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
