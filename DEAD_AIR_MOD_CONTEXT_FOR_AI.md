# Dead Air Mod – Full Context for AI Assistance

Use this document as a prompt or context when asking an AI (e.g. ChatGPT) to help debug or extend the **Dead Air** Minecraft mod. It explains how the mod works, what is broken, what has been tried, and what we are trying to achieve.

---

## 1. What the mod is

- **Name:** Dead Air  
- **Platform:** Minecraft 1.20.1, Forge  
- **Purpose:** Survival-driven radio network: players use a **walkie-talkie** to tune into **radio stations** broadcast by **radio towers**. Music plays when tuned to a station and in range of a powered tower. Integrates with **Zombiecraft** and **Apocalypse Structures** (Radio Towers / Radio Panels).  
- **Key idea:** Towers from Apocalypse Structures have a **Radio Panel** block. Once a player **right-clicks** that panel, we consider that tower “activated” and want it to stay active **after a full world restart**. Tuning to that tower’s station in the walkie GUI should then give **signal** and **music** when the player is in range—without needing to scan chunks or rely on in-memory tower lists.

---

## 2. How each part of the mod works

### 2.1 Radio towers and panels (external + our logic)

- **Source of towers:** Apocalypse Structures mod adds **Radio Panel** blocks (and tower structures). We do **not** own the block; we react to it.
- **Detection:** `ApocalypseTowerDetector` checks if a block is a Radio Panel and gets tower type (STANDARD, FENCED, OVERRUN, UNKNOWN/player-built).
- **Panel activation:** When a player **right-clicks** a Radio Panel, `RadioPanelInteractionHandler.onRightClickBlock` runs. We:
  - Call `RadioPanelManager.activatePanel(level, pos)` (persist “panel activated”).
  - Find or **register** the tower (if not yet in our `TowerManager`).
  - Call `KnownTowerStorage.addKnownTower(level, towerPos, panelPos, station.getId())` so this tower is persisted as “known” for signal resolution later.
  - Update tower power and trigger station discovery for nearby players.

### 2.2 Station assignment (no two towers in range share a station)

- **TowerManager.determineStationForTower(level, pos, towerType):**
  - Builds a set of “stations already in range” from (1) in-memory towers and (2) **KnownTowerStorage** (persisted known towers), so we don’t assign the same station to two towers within `minTowerSpacing` (e.g. 400 blocks).
  - Picks a station (type-based preferences: e.g. STANDARD → Bedrock Radio, OVERRUN → Zombiecraft) that is **not** in that set when possible.
- Used when registering a tower from panel click, chunk scan, block place, or spawn command.

### 2.3 Persistence: two storage systems

We have **two** world-level persistences (overworld only, no player data):

**A) Panel activation (legacy; no longer used for signal)**

- **PanelActivationStorage** (SavedData + backup file `world/data/dead_air_panels.dat`).
- Stores which **panel positions** are “activated.”
- **Current design:** We **do not** use this for deciding if a tower gives signal. The user said “don’t use the backup as a reference for working panels, because they didn’t work then either.” So signal and music do **not** depend on this file.

**B) Known towers (used for signal and “stations staying active”)**

- **KnownTowerStorage** (file only: `world/data/dead_air_known_towers.dat`).
- Stores a list of **known towers:** each entry is `(towerPos, panelPos, stationId)`.
- **When written:** When a player right-clicks a Radio Panel and we have a tower, we call `KnownTowerStorage.addKnownTower(...)` and immediately write the file.
- **When read:** When the server needs to answer “what signal does this station have at this player position?”, it loads this file (or uses an in-memory cache populated from it) and does **not** touch SavedData or chunk/block access. No chunk scanning.

### 2.4 Signal resolution (server, no chunk scan)

- **Entry point:** Client sends `RadioSignalRequestPacket(stationId)` every 2 ticks while tuned to a station (see ClientEvents).
- **Server handler** (`RadioSignalRequestPacket.handle`):
  1. Ensures overworld is “ready”: `ChunkEvents.ensureOverworldReadyForSignal(serverLevel)` which loads `PanelActivationStorage` and `KnownTowerStorage` from disk into caches.
  2. If dimension is overworld and station is valid:  
     `signal = KnownTowerStorage.getSignalFromKnownTowersOnly(serverLevel, msg.stationId, player.position())`.
  3. Sends back `RadioSignalResponsePacket(msg.stationId, signal)`.

- **getSignalFromKnownTowersOnly:**
  - Loads known towers from file for overworld (re-reads from disk so a fresh world load sees the file).
  - Iterates known tower entries for that **stationId**; for each, checks if player is within that station’s **broadcast range**; computes signal (distance, optional LOS/weather) via `SignalStrength.getFinalSignalStrengthFromPosition`.
  - Returns the best signal (0 if none in range). **No panel-activation check** and **no chunk access**.

### 2.5 Client: receiving signal and playing music

- **RadioSignalResponsePacket** (server → client): carries `(stationId, signal)`.
- **Handler:** `AudioManager.setServerSignal(stationId, signal)` (stores in `SERVER_SIGNAL_CACHE`), then if `signal >= 0.1f` calls `AudioManager.tryPlayFromServerSignal(stationId, signal)`.
- **tryPlayFromServerSignal:** Checks walkie is on, current station matches `stationId`, not Emergency Broadcast; gets `RadioStation` from registry; calls `AudioManager.update(mc, station, playerPos)`.
- **AudioManager.update:**
  - Prefers a **local** tower (e.g. singleplayer shared tower list) if present and powered; otherwise uses **SERVER_SIGNAL_CACHE** for that station.
  - If signal &lt; 0.1: logs why not playing, stops station, returns.
  - Otherwise: keeps or creates a `RadioSoundInstance` for that station and calls `mc.getSoundManager().play(sound)`. We treat “need new sound” as: `sound == null` OR switching station OR (`sound.isStopped()` and `signal >= 0.1f`).

### 2.6 World/chunk load (no heavy work to avoid hang)

- **LevelEvent.Load / ChunkEvent.Load:** We **do not** load storage or scan chunks here (to avoid world-load freezes).
- **READY_WORLDS:** Set only when the first **signal request** is handled (in `ensureOverworldReadyForSignal`). Chunk scanning for towers is only used in other code paths (e.g. chunk load **after** ready); signal for the GUI comes **only** from known towers + file.

### 2.7 Other components (brief)

- **TowerManager:** In-memory map of towers per dimension; used for in-world logic and for “stations already in range” when assigning stations. Not the source of truth for signal after restart.
- **StationRegistry:** All stations (Emergency Broadcast, Bedrock Radio, Zombiecraft, Medieval FM, etc.) with ids, broadcast range, minTowerSpacing.
- **StationUnlockManager / UnlockedStationsStorage:** Which stations the player has “discovered” (for GUI and discovery messages).
- **WalkieTalkieManager / tuning screen:** Player equips walkie, opens GUI, selects station. Current station and on/off state drive `ClientEvents` and thus signal requests and `AudioManager.update`.
- **MusicStationManager / RadioSoundInstance:** Picks tracks per station and manages the actual sound playback.

### 2.8 Audio design (per design brief)

- **Radio replaces vanilla music entirely.** Vanilla background music is disabled via `MusicManagerMixin`; no music plays except through Dead Air radio stations.
- **Radio always uses `SoundSource.MUSIC`.** Never use AMBIENT or RECORDS. Music only plays when tuned to a station and in range of a powered tower.
- **Ambient sounds** (cave, biome, etc.) continue playing naturally; we do not override them.

---

## 3. What is broken

1. **Stations not staying active after full game restart only**  
   - **Station list vs active:** After a full game exit and relaunch, the stations the player previously discovered are still *listed* in the tuning GUI (discovery/unlock list is persisted). They are no longer *active*: tuning to them shows no signal (STATIC, 0 bars) and no music.  
   - **Exit to main menu then re-enter world:** Stations stay active (signal works when tuned; music still may not play).  
   - **Save, fully close the game, then relaunch and load the world:** Stations appear in the list but are not active; tuning gives no signal.  
   - **Intended “reactivate via GUI”:** The design is that the player should be able to **click/tune to a station in the GUI** (e.g. “TUNE: Medieval FM”) and get signal and music when in range, without having to visit the tower and right‑click the panel again. That “tune in GUI = reactivate for listening” behaviour is not working after a full process exit, for the same reason: known towers are either not written to disk on shutdown, written to a path that isn’t used on next launch, or not read back correctly when the game starts.

2. **Music not playing when tuned to an active station**  
   Even when the user considers a station “active” (they activated the panel before, or re-activated after load), music often does not play when they tune to that station and are in range. So either the server is sending 0 (or low) signal, or the client is not starting/restarting playback when it has a valid server signal.

---

## 4. What has been tried (fixes already in the codebase)

- **Removing chunk scanning for signal:** Signal is resolved only from `KnownTowerStorage.getSignalFromKnownTowersOnly` (known towers file). No chunk scanning or in-memory tower lookup for the packet handler.
- **Not using panel backup for signal:** Panel activation backup is no longer used to decide if a tower gives signal; only “known towers” (tower pos + station) and player-in-range are used.
- **Known towers persistence:** On panel right-click we call `KnownTowerStorage.addKnownTower(...)` and write `dead_air_known_towers.dat`. When the file is missing we cache an empty list so we don’t leave the dimension “unloaded”; on read error we also cache an empty list.
- **Loading known towers on every signal request:** In `getSignalFromKnownTowersOnly`, for overworld we always call `loadBackupFileDirect(level)` so we re-read from disk and use the latest known towers (including after a world restart).
- **Client playback:** `SERVER_SIGNAL_CACHE` and `ACTIVE_SOUNDS` are `ConcurrentHashMap`. When we have server signal we treat “need new sound” as null or switching or (stopped and signal ≥ 0.1) and create/play a new sound so music starts or restarts.
- **Response handler:** On receiving `RadioSignalResponsePacket` we call `setServerSignal` and `tryPlayFromServerSignal` so playback is triggered as soon as the packet is handled, not only on the next tick.
- **Station assignment:** `determineStationForTower` uses both in-memory towers and known towers to build “stations already in range” so two towers within the same signal range don’t get the same station; new towers get a distinct station and can trigger “Discovered new station.”

Despite these changes, **stations still do not reliably stay active after restart** and **music still does not reliably play when tuned in**.

---

## 5. What we are trying to achieve

- **Stations stay active after full game restart:** One activation of a tower’s Radio Panel should be enough. After a full game exit and relaunch, that tower’s station should still provide signal when the player tunes to it in the walkie GUI (clicking the station or “TUNE”) and is within broadcast range—no need to visit the tower and right‑click the panel again. The station list (discovery) already persists; the missing piece is known-towers persistence so that “tune in GUI” gives signal after full restart.
- **Music plays when tuned to an active station:** When the player is tuned to a station that has at least one known, in-range tower, the client should receive a non-zero signal from the server and music should play (and restart if it had stopped).
- **No world-load hang:** We must not do heavy work (storage load, file I/O, chunk access) during level/chunk load; the current design defers that to the first signal request.
- **No chunk scanning for signal:** Signal for the GUI should come only from the known-towers file and player position (no chunk scanning).

---

## 6. Key files to look at when debugging

- **KnownTowerStorage.java** – Reading/writing `dead_air_known_towers.dat`, `getSignalFromKnownTowersOnly`, cache when file missing/error.
- **RadioSignalRequestPacket.java** – Server handler: ensureOverworldReadyForSignal, then getSignalFromKnownTowersOnly, then send response.
- **RadioSignalResponsePacket.java** – Client handler: setServerSignal, tryPlayFromServerSignal.
- **AudioManager.java** – SERVER_SIGNAL_CACHE, update(), tryPlayFromServerSignal, when we create/play sound (needNewSound).
- **RadioPanelInteractionHandler.java** – Panel right-click: activatePanel, register tower, addKnownTower.
- **ChunkEvents.java** – ensureOverworldReadyForSignal (loads both panel backup and known towers).
- **TowerManager.java** – determineStationForTower, getStationsAlreadyInRangeOf (uses KnownTowerStorage for exclusion).

---

## 7. Test setup (for reference)

- **Instance:** e.g. `C:\Users\Ksivi\curseforge\minecraft\Instances\dead air tests`
- **World data:** e.g. `...\saves\<WorldName>\data\dead_air_known_towers.dat` (and `dead_air_panels.dat` for legacy)
- **Logs:** `...\logs\latest.log` (look for `[Dead Air]` messages: known towers loaded, music started, etc.)

---

Please use this document to understand the mod’s design and the current failures, then suggest or implement fixes so that (1) known towers persist and are used correctly after a full world restart, and (2) music reliably plays when the player is tuned to a station that has a known tower in range.
