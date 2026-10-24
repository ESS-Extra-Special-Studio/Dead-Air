/**
 * Retry remaining DA 2.1.3 / RT 1.2.2 uploads (skip already-live versions).
 */
const fs = require("fs");
const path = require("path");

const studio = path.join(process.env.USERPROFILE, "Desktop", "Extra Special Studio");
const ess = fs.readdirSync(studio).find((n) => n.startsWith("Where we"));
const SECRETS = path.join(studio, ess, "SECRETS.local.md");

const VER = {
  "1.20.1": 9990,
  "1.21.1": 11779,
  Forge: 7498,
  NeoForge: 10150,
  Client: 9638,
  Server: 9639,
  "Java 17": 8326,
  "Java 21": 11135,
};

const CF_IDS = {
  "dead-air": 1453495,
  "apocalypse-structures-radio-towers-and-airdrops": 1193033,
};

const DA_CL =
  "Companion release for RadioTowers 1.2.2 (Forge + NeoForge). Use that build for airdrop descent, tower-density mixin, and ESL wave delivery fixes.";
const RT_CL = `RadioTowers 1.2.2
Update by: Extra_Special_K
Fixed:
Forge launch crash from tower spacing mixin / refmap on 1.20.1.
Airdrop crates hanging in the sky after plane drop (positional descent).
Changed:
ESL waves preferred for zombie waves; Berezka remains an optional fallback.`;

const JOBS = [
  {
    mrSlug: "dead-air",
    cfSlug: "dead-air",
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air\\build\\libs\\dead_air-2.1.3+1.20.1-forge.jar",
    version_number: "2.1.3+1.20.1-forge",
    name: "2.1.3 (Forge 1.20.1)",
    loaders: ["forge"],
    game_versions: ["1.20.1"],
    changelog: DA_CL,
    cfDisplayName: "dead_air-2.1.3+1.20.1-forge",
    cfVersionNames: ["1.20.1", "Forge", "Client", "Server", "Java 17"],
    skipMr: true, // 6qqjncb5
    skipCf: false,
  },
  {
    mrSlug: "dead-air",
    cfSlug: "dead-air",
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - 1.21.1 NeoForge\\build\\libs\\dead_air-2.1.3+1.21.1-neoforge.jar",
    version_number: "2.1.3+1.21.1-neoforge",
    name: "2.1.3 (NeoForge 1.21.1)",
    loaders: ["neoforge"],
    game_versions: ["1.21.1"],
    changelog: DA_CL,
    cfDisplayName: "dead_air-2.1.3+1.21.1-neoforge",
    cfVersionNames: ["1.21.1", "NeoForge", "Client", "Server", "Java 21"],
  },
  {
    mrSlug: "apocalypse-structures-radio-towers-and-airdrops",
    cfSlug: "apocalypse-structures-radio-towers-and-airdrops",
    jar: "C:\\Users\\Ksivi\\MCreatorWorkspaces\\radioos\\build\\libs\\radiotowers-1.2.2+1.20.1-forge.jar",
    version_number: "1.2.2+1.20.1-forge",
    name: "1.2.2 (Forge 1.20.1)",
    loaders: ["forge"],
    game_versions: ["1.20.1"],
    changelog: RT_CL,
    cfDisplayName: "radiotowers-1.2.2+1.20.1-forge",
    cfVersionNames: ["1.20.1", "Forge", "Client", "Server", "Java 17"],
  },
  {
    mrSlug: "apocalypse-structures-radio-towers-and-airdrops",
    cfSlug: "apocalypse-structures-radio-towers-and-airdrops",
    jar: "C:\\Users\\Ksivi\\IdeaProjects\\RadioTowers - 1.21.1 NeoForge\\build\\libs\\radiotowers-1.2.2+1.21.1-neoforge.jar",
    version_number: "1.2.2+1.21.1-neoforge",
    name: "1.2.2 (NeoForge 1.21.1)",
    loaders: ["neoforge"],
    game_versions: ["1.21.1"],
    changelog: RT_CL,
    cfDisplayName: "radiotowers-1.2.2+1.21.1-neoforge",
    cfVersionNames: ["1.21.1", "NeoForge", "Client", "Server", "Java 21"],
    skipMr: true, // r0x451Ur
    skipCf: true, // 8818467
  },
];

function readSecret(heading) {
  const text = fs.readFileSync(SECRETS, "utf8");
  const re = new RegExp(`### ${heading}\\s*\\r?\\n\`\`\`\\s*\\r?\\n([^\\r\\n\`]+)`);
  const m = text.match(re);
  if (!m) throw new Error(`${heading} not found`);
  return m[1].trim();
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function withRetry(label, fn, attempts = 4) {
  let last;
  for (let i = 1; i <= attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      last = e;
      console.warn(`  ${label} attempt ${i}/${attempts} failed:`, e.message || e);
      if (i < attempts) await sleep(5000 * i);
    }
  }
  throw last;
}

async function mrProjectId(token, slug) {
  const res = await fetch(`https://api.modrinth.com/v2/project/${slug}`, {
    headers: { Authorization: token, "User-Agent": "ESS-DA-RT-Upload/1.0" },
  });
  const t = await res.text();
  if (!res.ok) throw new Error(`Modrinth project ${slug}: ${res.status} ${t.slice(0, 300)}`);
  return JSON.parse(t).id;
}

async function ensureLoaders(token, projectId, loaders) {
  const res = await fetch(`https://api.modrinth.com/v2/project/${projectId}`, {
    headers: { Authorization: token, "User-Agent": "ESS-DA-RT-Upload/1.0" },
  });
  const proj = JSON.parse(await res.text());
  const have = new Set(proj.loaders || []);
  const need = loaders.filter((l) => !have.has(l));
  if (!need.length) return;
  const next = [...have, ...need];
  const patch = await fetch(`https://api.modrinth.com/v2/project/${projectId}`, {
    method: "PATCH",
    headers: {
      Authorization: token,
      "Content-Type": "application/json",
      "User-Agent": "ESS-DA-RT-Upload/1.0",
    },
    body: JSON.stringify({ loaders: next }),
  });
  if (!patch.ok) console.warn("  warn loaders patch", await patch.text());
  else console.log("  patched loaders ->", next.join(", "));
}

async function mrVersionExists(token, projectId, versionNumber) {
  const res = await fetch(`https://api.modrinth.com/v2/project/${projectId}/version`, {
    headers: { Authorization: token, "User-Agent": "ESS-DA-RT-Upload/1.0" },
  });
  if (!res.ok) return null;
  const list = JSON.parse(await res.text());
  return list.find((v) => v.version_number === versionNumber) || null;
}

async function uploadModrinth(token, projectId, job) {
  const data = {
    name: job.name,
    version_number: job.version_number,
    changelog: job.changelog,
    dependencies: [],
    game_versions: job.game_versions,
    version_type: "release",
    loaders: job.loaders,
    featured: false,
    status: "listed",
    project_id: projectId,
    file_parts: ["file"],
    primary_file: "file",
  };
  const form = new FormData();
  form.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }));
  form.append("file", new Blob([fs.readFileSync(job.jar)]), path.basename(job.jar));
  const res = await fetch("https://api.modrinth.com/v2/version", {
    method: "POST",
    headers: { Authorization: token, "User-Agent": "ESS-DA-RT-Upload/1.0" },
    body: form,
  });
  const t = await res.text();
  if (!res.ok) throw new Error(`Modrinth ${job.name}: ${res.status} ${t.slice(0, 500)}`);
  return JSON.parse(t);
}

async function uploadCf(authorToken, projectId, job) {
  const gameVersions = job.cfVersionNames.map((n) => {
    if (!(n in VER)) throw new Error(`Unknown CF version name ${n}`);
    return VER[n];
  });
  const displayName = job.cfDisplayName || path.basename(job.jar, ".jar");
  const metadata = {
    changelog: job.changelog,
    changelogType: "markdown",
    displayName,
    gameVersions,
    releaseType: "release",
  };
  const form = new FormData();
  form.append("metadata", JSON.stringify(metadata));
  form.append("file", new Blob([fs.readFileSync(job.jar)]), path.basename(job.jar));
  const res = await fetch(`https://minecraft.curseforge.com/api/projects/${projectId}/upload-file`, {
    method: "POST",
    headers: { "X-Api-Token": authorToken },
    body: form,
  });
  const t = await res.text();
  if (!res.ok) throw new Error(`CF ${job.cfDisplayName}: ${res.status} ${t.slice(0, 200)}`);
  try {
    return JSON.parse(t);
  } catch {
    return { raw: t };
  }
}

async function main() {
  const mrToken = readSecret("MODRINTH_TOKEN");
  const cfAuthor = readSecret("CURSEFORGE_AUTHOR_TOKEN");
  const results = [];
  const mrCache = {};

  for (const job of JOBS) {
    console.log("\n==", job.name);
    if (!fs.existsSync(job.jar)) throw new Error("Missing " + job.jar);

    let mrId = null;
    if (!job.skipMr) {
      try {
        if (!mrCache[job.mrSlug]) {
          mrCache[job.mrSlug] = await withRetry("mr project", () => mrProjectId(mrToken, job.mrSlug));
          console.log("  Modrinth", job.mrSlug, mrCache[job.mrSlug]);
        }
        await ensureLoaders(mrToken, mrCache[job.mrSlug], job.loaders);
        const existing = await mrVersionExists(mrToken, mrCache[job.mrSlug], job.version_number);
        if (existing) {
          mrId = existing.id;
          console.log("  Modrinth skip (exists)", mrId);
        } else {
          const mr = await withRetry("modrinth upload", () =>
            uploadModrinth(mrToken, mrCache[job.mrSlug], job)
          );
          mrId = mr.id;
          console.log("  Modrinth OK", mrId);
        }
      } catch (e) {
        console.error("  Modrinth FAIL", e.message || e);
      }
    } else {
      console.log("  Modrinth skip (already done)");
    }

    let cfResult = null;
    if (!job.skipCf) {
      try {
        const pid = CF_IDS[job.cfSlug];
        cfResult = await withRetry("cf upload", () => uploadCf(cfAuthor, pid, job), 5);
        console.log("  CurseForge OK", JSON.stringify(cfResult).slice(0, 200));
      } catch (e) {
        console.error("  CurseForge FAIL", e.message || e);
        cfResult = { error: String(e.message || e) };
      }
    } else {
      console.log("  CurseForge skip (already done)");
    }

    results.push({ name: job.name, modrinth: mrId, curseforge: cfResult });
    await sleep(2000);
  }

  const out = path.join(__dirname, "upload-da-rt-retry-results.json");
  fs.writeFileSync(out, JSON.stringify(results, null, 2));
  console.log("\nWrote", out);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
