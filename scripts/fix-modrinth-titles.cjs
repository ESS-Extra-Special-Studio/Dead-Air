/**
 * PATCH Modrinth titles for Dead Air + expansions to CurseForge-aligned mrTitle values.
 *
 *   node scripts/fix-modrinth-titles.cjs
 *   node scripts/fix-modrinth-titles.cjs --dry-run
 */
const { spawnSync } = require("child_process");
const path = require("path");

const script = path.join(__dirname, "create-dead-air-modrinth.cjs");
const args = [script, "--titles-only", ...process.argv.slice(2).filter((a) => a !== "--dry-run")];
if (process.argv.includes("--dry-run")) args.push("--dry-run");

const result = spawnSync(process.execPath, args, { stdio: "inherit" });
process.exit(result.status ?? 1);
