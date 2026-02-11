# Panel persistence and music – design plan

## Goal
- **Panel:** One activation → stays on after reload. Stored in world only (`world/data/dead_air_panels.dat`), no player data.
- **Music:** Plays when tuned to a station that has a **powered** tower in range. Must work in singleplayer after world load.

## Why music stopped working
Music needs `TowerManager.getBestTower(level, pos, station)` to return a tower with `isPowered() == true`. That requires:
1. The tower to be **registered** (we found the panel block and called `registerTower`).
2. The tower to be **powered** (panel in our backup file → `isPanelActivated()` true when we register).

So we must (a) load the backup file so we know which panels are “on”, and (b) **discover** the tower (scan the chunk that contains the panel) so it exists in the list and gets `setPowered(true)` from storage.

## What must NOT happen on world load
- **No chunk access during `LevelEvent.Load`.** Calling `level.getChunk()` or `level.hasChunk()` (or anything that triggers chunk load) during world load can block or deadlock the main thread and causes “stuck on world load”. Forge/Minecraft chunk loading is not safe to invoke from that event.

## Safe flow

### On world load (`LevelEvent.Load`)
- **Do nothing** for panel/storage: no `PanelActivationStorage.get()`, no `loadBackupInto`, no `updateTowerPower`. Calling `get(serverLevel)` uses `dataStorage.computeIfAbsent()` and can block or deadlock during world init.
- Only log and put a dimension into `WORLD_LOAD_DELAYS` if needed elsewhere.
- Panel backup load and `READY_WORLDS` happen on **first overworld chunk load** (see below).

### Tower discovery (only in safe contexts)
1. **ChunkEvent.Load (first overworld chunk)** – If overworld and `READY_WORLDS` not set: load backup into storage, set `READY_WORLDS`, call `updateTowerPower`. Then (for every chunk load when ready): scan that chunk; register any panels; `isPanelActivated()` sees storage and sets tower powered.
2. **RadioSignalRequestPacket (lazy scan)** – When the client asks for signal and we have no tower, scan the **player’s chunk** once (throttled). Chunk is already loaded. Register tower and return signal.

### Music
- Client calls `AudioManager.update()` with tuned station.
- Uses `TowerManager.getBestTower(mc.level, playerPos, station)` (shared static list in singleplayer).
- If tower exists and is powered → compute signal and play. If no tower or not powered → use `SERVER_SIGNAL_CACHE` from server response; if server sent signal ≥ 0.1 we play from that.
- So as long as the server has registered the tower (from chunk load or lazy scan) and set it powered from the backup file, music will play.

## Summary
- **World load:** File read + flag only; no chunk work.
- **Tower discovery:** ChunkEvent.Load + lazy scan on first signal request.
- **Music:** Works when tower is in the list and powered; server signal fallback when client has no tower yet.
