# Dead Air Mod - Integration Verification Guide

This document outlines the integration points with dependency mods and how to verify they work correctly.

## Dependency Mods

### 1. Apocalypse Structures: Radio Towers and Airdrops
**Expected Mod ID:** Dynamically detected (works with any mod that adds radio towers)
**Integration Points:**
- Radio tower block detection (dynamic pattern matching)
- Airdrop entity detection (heuristic-based)
**Note:** The mod dynamically scans for radio tower blocks from any installed mod, making it compatible with Apocalypse Structures regardless of the exact mod ID.

### 2. Walkie-Talkie Mod
**Expected Mod IDs:** `walkietalkie`, `radio`, or `walkie_talkie`
**Integration Points:**
- Walkie-talkie item detection
- Item usage tracking

## Integration Methods

### Tower Detection

**Method 1: Apocalypse Structures (Radio Towers)**
- Location: `ApocalypseTowerDetector.initialize()`
- Detects Radio Panel blocks from radiotowers mod (registry: radiotower, radiotower_2, radiotoweroverrun).

**Method 2: Block Placement Event**
- Location: `BlockEvents.onBlockPlace()`
- Hooks into Forge's `BlockEvent.EntityPlaceEvent`
- Automatically registers towers when a Radio Panel is placed.

**Method 3: Chunk Loading**
- Location: `ChunkEvents.onChunkLoad()`
- Scans chunks for Radio Panel blocks when they load and registers towers via `ApocalypseTowerDetector`.

**Verification:**
1. Place a radio tower block from Apocalypse Structures
2. Check logs for: "Radio tower placed at [pos] broadcasting station [name]"
3. Hold walkie-talkie and tune to a station - should receive signal if tower is powered

### Walkie-Talkie Detection

**Method 1: Item Registry Lookup**
- Location: `WalkieTalkieManager.initialize()`
- Checks for items with IDs:
  - `walkietalkie:walkie_talkie`
  - `radio:walkie_talkie`
  - `walkie_talkie:walkie_talkie`

**Method 2: Item Name/Path Detection**
- Location: `WalkieTalkieManager.isWalkieTalkieItem()`
- Checks item path for keywords: `walkie`, `talkie`, `radio`
- Checks display name for keywords

**Method 3: NBT Data Detection**
- Location: `WalkieTalkieManager.isWalkieTalkieItem()`
- Checks for NBT tags: `WalkieTalkie`, `RadioFrequency`, `IsRadio`

**Verification:**
1. Hold a walkie-talkie item in main or off hand
2. Check logs for: "Found walkie-talkie item: [id]"
3. UI overlay should appear showing station info
4. Audio should play when tuned to a station

### Airdrop Detection

**Status:** Deferred. Airdrop integration was removed; Apocalypse Structures is reworking their airdrop system. `EmergencyBroadcast` is a no-op until re-integration.

## Mixins Created

### 1. ChunkMixin
- **Target:** `LevelChunk`
- **Purpose:** Detect tower blocks when chunks are modified
- **Method:** `onBlockSet()` - hooks into block state changes

### 2. ItemStackMixin
- **Target:** `ItemStack`
- **Purpose:** Add helper method to check if item is walkie-talkie
- **Method:** `dead_air$isWalkieTalkie()` - checks NBT data

### 3. PlayerMixin
- **Target:** `Player`
- **Purpose:** Hook into player tick for walkie-talkie processing
- **Method:** `onPlayerTick()` - called every tick

## Fallback Mechanisms

If dependency mods are not found or use different IDs:

1. **Tower Detection:** Falls back to scanning for blocks with `radio_tower` or `tower` in path
2. **Walkie-Talkie:** Falls back to name/path matching and NBT detection
3. **Airdrops:** Falls back to heuristics (high altitude, large item stacks)

## Testing Checklist

- [ ] Place radio tower block - verify registration in logs
- [ ] Break radio tower block - verify removal in logs
- [ ] Hold walkie-talkie - verify UI overlay appears
- [ ] Tune to station - verify audio plays
- [ ] Move away from tower - verify signal strength decreases
- [ ] (Airdrop deferred) Trigger airdrop when re-enabled
- [ ] Power tower with FE - verify broadcast works
- [ ] Remove power - verify broadcast stops

## Troubleshooting

### Towers Not Detected
1. Verify RadioTowers (Apocalypse Structures: Radio Towers and Airdrops) is loaded
2. Check structure IDs: radiotowers:radiotower, radiotower_2, radiotoweroverrun
3. Use `/dead_air spawntower standard` to test; check logs for registration
4. Enable debug logging in `ApocalypseTowerDetector` if needed

### Walkie-Talkie Not Working
1. Check item ID in logs: "Found walkie-talkie item: [id]"
2. Verify walkie-talkie mod is loaded
3. Check item is in main or off hand
4. Try fallback detection methods

### Airdrops Not Detected
Airdrop integration is currently disabled; will be re-added when Apocalypse Structures airdrop system is available.

## API Compatibility Notes

All methods used are standard Forge/Minecraft APIs:
- `ForgeRegistries.BLOCKS.getValue()` - Standard block registry
- `ForgeRegistries.ITEMS.getValue()` - Standard item registry
- `BlockEvent.EntityPlaceEvent` - Standard Forge event
- `ChunkEvent.Load` - Standard Forge event
- `EntityJoinLevelEvent` - Standard Forge event
- `ForgeCapabilities.ENERGY` - Standard FE API

No direct API calls to dependency mods are made - all integration is through standard Minecraft/Forge interfaces.
