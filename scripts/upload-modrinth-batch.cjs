/**
 * Upload Radio Towers + Dead Air (+ expansions) to existing Modrinth projects.
 * Token: Desktop SECRETS.local.md (MODRINTH_TOKEN).
 *
 *   node scripts/upload-modrinth-batch.cjs --dry-run
 *   node scripts/upload-modrinth-batch.cjs
 */
const fs = require("fs");
const { openAsBlob } = require("fs");
const path = require("path");
const { assertModrinthUploadsNotPaused } = require("./modrinth-pause-guard.cjs");

assertModrinthUploadsNotPaused();

const ROOT = path.join(__dirname);
const SECRETS = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\SECRETS.local.md";
const MAP = "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\modrinth-import\\mods-map.json";
const OUT = path.join(ROOT, "..", "modrinth-upload-results.json");
const DRY = process.argv.includes("--dry-run");

const RETRY_DELAYS_MS = [5000, 15000, 30000];
const UPLOAD_TIMEOUT_MS = 600000;

const JAR_DIRS = [
  "C:\\Users\\Ksivi\\curseforge\\minecraft\\Instances\\C.Ideas\\mods",
  "C:\\Users\\Ksivi\\MCreatorWorkspaces\\radioos\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Frontline FM\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Wayfarer Radio\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air After Hours FM\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Iron Rain FM\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Broken Youth Radio\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Beat Blocks\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air Zero Gravity\\build\\libs",
  "C:\\Users\\Ksivi\\IdeaProjects\\Dead Air - Pip-Boy Radio Conversion\\build\\libs",
];

const UPLOADS = [
  {
    slug: "apocalypse-structures-radio-towers-and-airdrops",
    label: "Radio Towers",
    patterns: [/^radiotowers-/i],
    changelog:
      "Maintenance build with Dead Air integration (Extra Special Studio collaborator).\n\nOriginal mod by [that1lilguy](https://www.curseforge.com/members/that1lilguy/projects).",
  },
  {
    slug: "dead-air",
    label: "Dead Air",
    patterns: [/^dead_air-\d/i, /^dead-air-\d/i],
    exclude: /after|block|broken|frontline|iron|wayfarer|zero|pip|letters/i,
    changelog: "Forge 1.20.1 — Dead Air 2.1.2 with Radio Towers integration.",
  },
  { slug: "dead-air-frontline-fm", patterns: [/frontline/i] },
  { slug: "dead-air-wayfarer-radio", patterns: [/wayfarer/i] },
  { slug: "dead-air-after-hours-fm", patterns: [/after.?hours/i] },
  { slug: "dead-air-iron-rain-fm", patterns: [/iron.?rain/i] },
  { slug: "dead-air-broken-youth-radio", patterns: [/broken.?youth/i] },
  { slug: "dead-air-block-beats", patterns: [/block.?beats|beat.?blocks/i] },
  { slug: "dead-air-zero-gravity", patterns: [/zero.?gravity/i] },
  { slug: "dead-air-pip-boy-radio-conversion", patterns: [/pip.?boy/i] },
];

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function readToken() {
  const text = fs.readFileSync(SECRETS, "utf8");
  const m = text.match(/### MODRINTH_TOKEN\s*\r?\n```\s*\r?\n([^\r\n`]+)/);
  if (!m) throw new Error("MODRINTH_TOKEN not found in SECRETS.local.md");
  return m[1].trim();
}

function loadTitleBySlug() {
  const mods = JSON.parse(fs.readFileSync(MAP, "utf8").replace(/^\uFEFF/, ""));
  const map = new Map();
  for (const mod of mods) {
    if (mod.cfSlug) map.set(mod.cfSlug, mod.mrTitle || mod.name);
  }
  return map;
}

function listJars() {
  const jars = [];
  for (const dir of JAR_DIRS) {
    if (!fs.existsSync(dir)) continue;
    for (const name of fs.readdirSync(dir)) {
      if (!name.endsWith(".jar") || /sources|javadoc/i.test(name)) continue;
      jars.push({ name, path: path.join(dir, name), mtime: fs.statSync(path.join(dir, name)).mtimeMs });
    }
  }
  return jars;
}

function findJar(spec, jars) {
  let hits = jars.filter((j) => spec.patterns.some((re) => re.test(j.name)));
  if (spec.exclude) hits = hits.filter((j) => !spec.exclude.test(j.name));
  if (!hits.length) return null;
  return hits.sort((a, b) => b.mtime - a.mtime)[0];
}

function parseVersion(name) {
  const m = name.match(/(\d+\.\d+\.\d+(?:\+[^\s]+)?)/);
  return m ? m[1].replace(/\.jar$/i, "") : "1.0.0";
}

function uploadTimeoutMs(filePath) {
  const mb = fs.statSync(filePath).size / (1024 * 1024);
  return Math.min(7200000, Math.max(900000, Math.ceil(mb * 120000)));
}

function isRetryableError(err) {
  if (!err) return false;
  const msg = String(err.message || err);
  if (/fetch failed|timeout|ECONNRESET|ETIMEDOUT|ENOTFOUND|socket hang up/i.test(msg)) return true;
  if (err.status >= 500) return true;
  if (err.status === 429) return true;
  return false;
}

async function withRetry(label, fn) {
  let lastErr;
  for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastErr = err;
      if (attempt >= RETRY_DELAYS_MS.length || !isRetryableError(err)) throw err;
      const wait = RETRY_DELAYS_MS[attempt];
      console.log(`  retry ${label} in ${wait / 1000}s (${attempt + 1}/${RETRY_DELAYS_MS.length}): ${err.message}`);
      await sleep(wait);
    }
  }
  throw lastErr;
}

async function mrFetch(token, method, apiPath, { json, form, timeoutMs = UPLOAD_TIMEOUT_MS } = {}) {
  const headers = { Authorization: token, "User-Agent": "ESS-Modrinth-Batch/1.0" };
  let body;
  if (form) body = form;
  else if (json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(json);
  }
  const res = await fetch(`https://api.modrinth.com/v2${apiPath}`, {
    method,
    headers,
    body,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await res.text();
  if (!res.ok) {
    const err = new Error(`${method} ${apiPath} -> ${res.status}: ${text.slice(0, 400)}`);
    err.status = res.status;
    throw err;
  }
  return text ? JSON.parse(text) : null;
}

async function uploadVersion(token, projectId, jar, changelog) {
  const versionNumber = parseVersion(jar.name);
  const sizeMb = Math.round((fs.statSync(jar.path).size / (1024 * 1024)) * 10) / 10;
  const timeoutMs = uploadTimeoutMs(jar.path);
  console.log(`  uploading ${jar.name} (${sizeMb}MB, timeout ${Math.round(timeoutMs / 60000)}m)`);
  const loaders = [/neoforge/i.test(jar.name) ? "neoforge" : "forge"];
  const gv = [/1\.21/.test(jar.name) ? "1.21.1" : "1.20.1"];
  const data = {
    name: versionNumber,
    version_number: versionNumber,
    changelog: changelog || `Release ${versionNumber}`,
    dependencies: [],
    game_versions: gv,
    version_type: "release",
    loaders,
    featured: true,
    status: "listed",
    project_id: projectId,
    file_parts: ["file"],
    primary_file: "file",
  };
  const form = new FormData();
  form.append("data", new Blob([JSON.stringify(data)], { type: "application/json" }));
  form.append("file", await openAsBlob(jar.path), jar.name);
  return withRetry(`POST /version ${versionNumber}`, () =>
    mrFetch(token, "POST", "/version", { form, timeoutMs })
  );
}

async function main() {
  const token = readToken();
  const titles = loadTitleBySlug();
  const jars = listJars();
  const results = [];

  for (const spec of UPLOADS) {
    const label = titles.get(spec.slug) || spec.label || spec.slug;
    const row = { slug: spec.slug, label };
    try {
      const jar = findJar(spec, jars);
      if (!jar) {
        row.skipped = "no local JAR";
        results.push(row);
        console.log(`SKIP ${label} — no JAR`);
        continue;
      }
      row.jar = jar.name;
      const project = await withRetry(`GET /project/${spec.slug}`, () =>
        mrFetch(token, "GET", `/project/${spec.slug}`, { timeoutMs: 60000 })
      );
      row.projectId = project.id;
      if (DRY) {
        row.dryRun = true;
        console.log(`DRY ${label} -> ${jar.name} (${project.id})`);
      } else {
        const ver = await uploadVersion(
          token,
          project.id,
          jar,
          spec.changelog || `Dead Air expansion — ${label}.`
        );
        row.versionId = ver.id;
        console.log(`OK ${label} ${ver.version_number}`);
      }
      row.ok = true;
    } catch (e) {
      row.ok = false;
      row.error = String(e.message || e);
      console.log(`FAIL ${label}: ${row.error}`);
    }
    results.push(row);
  }

  fs.writeFileSync(OUT, JSON.stringify(results, null, 2));
  console.log("\nWrote", OUT);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
