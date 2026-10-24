const fs = require("fs");

const PAUSE_FILE =
  "C:\\Users\\Ksivi\\Desktop\\Extra Special Studio\\Where we're at now\\modrinth-import\\UPLOADS_PAUSED.md";

function assertModrinthUploadsNotPaused() {
  if (process.argv.includes("--dry-run")) return;
  if (process.argv.includes("--force")) return;
  if (fs.existsSync(PAUSE_FILE)) {
    console.error(
      "Modrinth uploads are PAUSED (waiting for Radio Towers team permissions).\n" +
        "See: " +
        PAUSE_FILE +
        "\nUse --dry-run to preview, or remove the pause file when ready."
    );
    process.exit(1);
  }
}

module.exports = { assertModrinthUploadsNotPaused, PAUSE_FILE };
