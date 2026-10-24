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

const hosts = [
  "https://legacy.curseforge.com",
  "https://minecraft.curseforge.com",
];

const jobs = [
  {
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - 1.21.1 NeoForge\\build\\libs\\dead_air-2.1.3+1.21.1-neoforge.jar",
    displayName: "2.1.3 (NeoForge 1.21.1)",
    gameVersions: [11779, 10150, 9638, 9639, 11135],
  },
  {
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air\\build\\libs\\dead_air-2.1.3+1.20.1-forge.jar",
    displayName: "2.1.3 (Forge 1.20.1)",
    gameVersions: [9990, 7498, 9638, 9639, 8326],
  },
];

const changelog =
  "Companion release for RadioTowers 1.2.2 (Forge + NeoForge). Use that build for airdrop descent, tower-density mixin, and ESL wave delivery fixes.";

for (const job of jobs) {
  for (const host of hosts) {
    console.log("\n==", job.displayName, "via", host);
    const metaPath = path.join(__dirname, "_cf-meta-tmp.json");
    fs.writeFileSync(
      metaPath,
      JSON.stringify({
        changelog,
        changelogType: "markdown",
        displayName: job.displayName,
        gameVersions: job.gameVersions,
        releaseType: "release",
      })
    );
    const args = [
      "-sS",
      "-X",
      "POST",
      `${host}/api/projects/${projectId}/upload-file`,
      "-H",
      `X-Api-Token: ${token}`,
      "-H",
      "Expect:",
      "--http1.1",
      "-F",
      `metadata=<${metaPath};type=application/json`,
      "-F",
      `file=@${job.jar};type=application/java-archive`,
      "--connect-timeout",
      "60",
      "--max-time",
      "1200",
      "-w",
      "\\nHTTP %{http_code} time %{time_total}\\n",
    ];
    const r = spawnSync("curl.exe", args, { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 });
    const out = (r.stdout || "").trim();
    console.log(out.slice(0, 400));
    if (r.stderr) console.log("stderr:", r.stderr.slice(0, 200));
    try {
      fs.unlinkSync(metaPath);
    } catch {}
    if (out.includes('"id"') || /HTTP 200/.test(out)) {
      console.log("SUCCESS — skipping other hosts for this jar");
      break;
    }
  }
}
