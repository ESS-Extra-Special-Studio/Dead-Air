# Changelog

All notable changes to Dead Air will be documented in this file.

2.1.3

Update by: Extra_Special_K

Changed:

- Companion release for **RadioTowers 1.2.2** (Forge + NeoForge). Use that build for airdrop descent, tower-density mixin, and ESL wave delivery fixes.

2.1.2

Update by: Extra_Special_K

Added:

- Field Guide now notes that T1.Radio is default guaranteed loot in RadioTowers radio tower chests (separate from airdrop crates).

Changed:

- Requires ExtraSpecialCore (ESC) **2.0.0** or newer (`[2.0.0,3.0)`), which needs ExtraSpecialLIB (ESL).
- Airdrop, loot-table, and zombie-wave knobs live in RadioTowers config, not Dead Air.

2.1.1

Update by: Extra_Special_K

Added:

- Detected-tower memory: chunk scans persist tower positions so the walkie can point within station range even when the chunk is unloaded (music/unlock still require panel activation).

Changed:

- Deactivating a panel keeps its detected position for walkie direction (no longer clears known-tower coords).

2.1.0

Update by: Extra_Special_K

Added:

- Companion radio support: shared `RadioLiveDisplay`, `WalkieItem` GUI/LCD hooks, tune-screen chrome hooks, and `DeadAirAPI.registerWalkieGuiOpener` for non-`WalkieItem` shells (e.g. Pip-Boy).
- Item tags `dead_air:walkie_radios` / `walkie_t1` / `walkie_t2` so tagged companion radios count as walkies.
- Resource-pack compatibility for alternate T1/T2 models; T2 LCD falls back to the corner HUD on incompatible small atlases.

Changed:

- **ESS domain swap** — Java packages moved from `uk.creatopia.unbound.dead_air…` to `uk.co.extraspecialstudio.dead_air…`.
- Depends on Extra Special Core **1.3.0+** (`EscTabBar` and related UI helpers).
- Tune key (N) prefers an off-hand walkie when the main hand is not a radio.
- Static Notes removed from the RadioTowers Call Airdrop catalog; delivery crates only contain selected loot (tower structure crates still guarantee a T1.Radio).
- Live LCD rotation/scale options for companion wrist screens (degrees + wide-atlas glyph scaling).

2.0.0

Update by: Extra_Special_K

Added:

-Built-in Dead Air Walkie (T1.Radio) and Walkie Link (T2.Radio) with GeckoLib models — Flaton Walkie-Talkie mod no longer required.
-Craft recipes for the full progression chain: Static Note → T1, nine Notes → Resonant Chord (floor merge), Chord → Dimensional Relay / Radio Panel, Relay → T2 + Signal Upgrade, Disc Compendiums → Jukebox Upgrade.
-Static Note: drops from mob kills while listening (T1 15% / T2 30% from the one active radio only); glowing pickup; placeable; RadioTowers airdrop catalog entry when present.
-T1.Radio in RadioTowers airdrops: Call Airdrop catalog (5 points), guaranteed in tower structure crates, ~10% in standard airdrop loot.
-Signal Upgrade (Tower Boost): craftable; install on a radio panel like Jukebox Upgrade to enable T2 Link to that tower.
-T2 Link: link to any Signal-Upgraded station in walkie range (no need to stand at the panel). Link button toggles link/unlink; T1 GUI has no Link controls.
-Linked walkies follow their panel: re-assigning the panel’s station retunes the linked walkie automatically.
-Cross-dimension music resume: changing dimensions keeps the same track near the same playback position instead of restarting or skipping.
-One active tuned walkie per player: tuning, powering on a tuned walkie, or linking untunes every other walkie so stations cannot overlap.
-Client↔server walkie state sync so Static Note drops and server logic match the client’s active radio.
-Field Guide reorganised: Intro, How To Play, Materials (per-item recipes), Current Expansions, Planned Material.

Changed:

-Requires GeckoLib 4.x; ExtraSpecialCore; voicechat optional. Flaton Walkie-Talkie no longer required.
-Music is driven every client tick (not HUD-only): keeps playing from inventory when tuned/on; dropped radios do not play.
-Static Note drop chance is based only on the player’s single active radio (multiple radios in inventory no longer stack chance).
-Version bump to 2.0.0 (Cross-Dim Radio).

Fixed:

-Overlapping stations when swapping walkies, opening ESC, or dropping radios.
-Music stopping when scrolling the active radio off the hotbar while it remained tuned in inventory.
-Music restarting on a new track (or going silent for a long pause) after Nether/portal dimension changes.
-Phantom “still playing” state after the sound engine wiped all sources on dimension change.

1.4.6

Update by: Extra_Special_K

Changed:

-Field Guide **Current Expansions** page updated: removed inaccurate Breakline FM future-expansion note.
-In-development expansions now listed as Pop Paradise Radio, Frequency X, and Parallel Horizons FM.
-Walkie display-name and genre overrides pre-registered for those three expansion mod IDs.

1.4.5

Update by: Extra_Special_K

Changed:

-Walkie tuning and in-mod config screens finished on ESC anchor layout (title bar, footer, and panel insets).
-Walkie config gear button aligned flush with the right edge of the signal panel.
-Config screen title and Done button spaced inside the panel border instead of overlapping it.

1.4.4

Update by: Extra_Special_K

Added:

-Zero Gravity soundtrack expansion support: walkie shows the station as "Zero Gravity" (no Dead Air prefix) with genre Pop Rock / Indie Pop.
-Field Guide Current Expansions list updated to include Zero Gravity (and existing Broken Youth / Iron Rain entries).

1.4.3

Update by: Extra_Special_K

Fixed:

-Radio playlists now advance to the next track when a song finishes, instead of repeating the same track on all stations.
-Walkie tuning screen tab bar (Radio / Walkie) is centred correctly on all screen sizes.
-Expansion station display names (e.g. Broken Youth Radio, Iron Rain FM) show correctly instead of auto-generated labels.

Changed:

-Requires ExtraSpecialCore 1.2.1+ for updated walkie screen layout helpers.

1.4.2

Update by: Extra_Special_K

Fixed:

-Field Guide was granted again on every dedicated-server login: tracking lived only on player persistent NBT, which did not reliably survive sessions. Grant state is now stored in overworld `SavedData` (`dead_air_field_guide_grants`, per-player UUID); legacy `dead_air.field_guide_granted` on the player is still honoured once so existing worlds do not double-grant after updating.

Changed:

-Release version bump to 1.4.2.

1.4.1

Update by: Extra_Special_K

Changed:

-Integrated ExtraSpecialCore (ESC) as a required dependency for shared UI foundations.
-Updated in-mod config screen wiring to use ESC panel/button helpers for consistent styling with the wider Creatopia stack.
-Release/version bump for ESC-compat deployment line.

1.4

Update by: Extra_Special_K

Added:

-Dead Air Field Guide item scaffold added (`dead_air_field_guide`) with custom model/texture assets ready for in-game testing.
-Jukebox Upgrade content pass: new upgrade item flow, recipe-chain support (Disc Compendium + upgrade path), and panel module capability wiring for upgrade-aware stations.

Fixed:

-Music playback now uses real per-track durations where available, with safer early-stop handling so songs are far less likely to cut off before full length.
-Custom station resource-pack mounting flow was hardened so config-based custom station folders can be discovered/mounted more reliably.
-Dynamic station naming/genre overrides extended for current and upcoming expansions (including Block Beats and Breakline metadata support).
-Panel Jukebox gating is now enforced server-side: Jukebox FM cannot be assigned/broadcast from a panel unless that panel has the Jukebox Upgrade installed.
-Known-tower sync and signal cache now carry per-panel Jukebox module state, preventing partial/invalid client states (e.g. “Now Playing: ?” on blocked Jukebox paths).
-Panel upgrade interaction now consumes right-click correctly and applies from either hand, so using the upgrade item no longer falls through to open the airdrop GUI.
-Localization support expanded: recent chat/system messages, tower command output, and tower menu naming now use translation keys (translator-ready `en_us` updates included).

1.0.3

Update by: Extra_Special_K

Added:

-Version 1.0.3 – Same core radio / walkie / API behaviour as 1.2 hotfix; packaging/version label update (replaces prior 1.3 string).

-Use with RadioTowers 1.0.6 for tower panels, walkie integration, and Berezka airdrop waves (defense-wave + NMS fixes, reconnect sweeps not killing active waves, cooldown enforcement).

1.2 hotfix

Update by: Extra_Special_K

Added:

-Walkie station list matches live tower broadcasts; turn-off gives real no-signal (no quiet fallback); tower/panel identity deduped so the walkie UI doesn’t accumulate stale stations.

1.2

Update by: Extra_Special_K

Added:

-Integration API for RadioTowers (ground wave position, panel activate/deactivate, sync towers, emergency tune helper).

-Internet radio – MP3 streams in the Dead Air audio stack; panel Internet path aligned with RadioTowers.

-Dynamic stations and custom folders – Better addon discovery; config/dead_air/custom stations folder flow; Wayfarer / Frontline display names.

-Walkie and playback – Persistence and retune fixes; Creatopia Radio in default set.

Fixed:

-Panel station duplication; unwanted emergency retunes; Frontline FM silent-play bug.

Dependencies:

-JAR only. Optional: Wayfarer Radio, Frontline FM expansion mods.

1.1

Update by: Extra_Special_K

Added:

-Volume scales with signal strength; compass flashes green when facing the tuned tower; known-towers client cache; custom .ogg music folder support.

1.0

Update by: Extra_Special_K

Added:

-Radio network (towers broadcast, walkie tunes stations); music when walkie carried; 75-block signal steps. Requires Apocalypse Structures + Walkie-Talkie.
