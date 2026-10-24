const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const studio = path.join(process.env.USERPROFILE, "Desktop", "Extra Special Studio");
const ess = fs.readdirSync(studio).find((n) => n.startsWith("Where we"));
const text = fs.readFileSync(path.join(studio, ess, "SECRETS.local.md"), "utf8");
function secret(h) {
  const m = text.match(new RegExp(`### ${h}\\s*\\r?\\n\`\`\`\\s*\\r?\\n([^\\r\\n\`]+)`));
  if (!m) throw new Error(h);
  return m[1].trim();
}

const token = secret("CURSEFORGE_AUTHOR_TOKEN");
const projectId = 1453495;

const jobs = [
  {
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air\\build\\libs\\dead_air-2.1.3+1.20.1-forge.jar",
    displayName: "2.1.3 (Forge 1.20.1)",
    gameVersions: [9990, 7498, 9638, 9639, 8326],
    changelog:
      "Companion release for RadioTowers 1.2.2 (Forge + NeoForge). Use that build for airdrop descent, tower-density mixin, and ESL wave delivery fixes.",
  },
  {
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - 1.21.1 NeoForge\\build\\libs\\dead_air-2.1.3+1.21.1-neoforge.jar",
    displayName: "2.1.3 (NeoForge 1.21.1)",
    gameVersions: [11779, 10150, 9638, 9639, 11135],
    changelog:
      "Companion release for RadioTowers 1.2.2 (Forge + NeoForge). Use that build for airdrop descent, tower-density mixin, and ESL wave delivery fixes.",
  },
];

for (const job of jobs) {
  console.log("\n==", job.displayName, "size", (fs.statSync(job.jar).size / 1e6).toFixed(1), "MB");
  const metaPath = path.join(__dirname, "_cf-meta-tmp.json");
  const metadata = {
    changelog: job.changelog,
    changelogType: "markdown",
    displayName: job.displayName,
    gameVersions: job.gameVersions,
    releaseType: "release",
  };
  fs.writeFileSync(metaPath, JSON.stringify(metadata));

  // curl: longer timeouts; @file for multipart
  const args = [
    "-sS",
    "-X",
    "POST",
    `https://minecraft.curseforge.com/api/projects/${projectId}/upload-file`,
    "-H",
    `X-Api-Token: ${token}`,
    "-F",
    `metadata=<${metaPath};type=application/json`,
    "-F",
    `file=@${job.jar};type=application/java-archive`,
    "--connect-timeout",
    "60",
    "--max-time",
    "900",
    "-w",
    "\\nHTTP %{http_code}\\n",
  ];
  const r = spawnSync("curl.exe", args, { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 });
  console.log("stdout:", (r.stdout || "").slice(0, 500));
  if (r.stderr) console.log("stderr:", r.stderr.slice(0, 300));
  console.log("exit", r.status);
  try {
    fs.unlinkSync(metaPath);
  } catch {}
}
