# Existing World Compatibility

## How Dead Air Works with Existing Worlds/Servers

The Dead Air mod is designed to work seamlessly with existing worlds that already have radio towers from Apocalypse Structures.

## Detection Methods

### 1. **On World Load** (Primary Method)
When a world loads, the mod automatically:
- Scans all currently loaded chunks around spawn (32 chunk radius = 512 blocks)
- Detects existing radio tower blocks
- Registers them with the Dead Air system
- Assigns stations based on tower distribution

### 2. **As Chunks Load** (Ongoing Detection)
- When chunks are loaded (player exploration, chunk loading), they are automatically scanned
- New towers discovered are immediately registered
- Works for both existing and new chunks

### 3. **On Block Placement** (Real-time Detection)
- When a tower block is placed, it's immediately detected and registered
- No scanning needed - instant integration

## Tower Detection

The mod detects towers by:
1. **Block ID Matching**: Looks for blocks with IDs containing:
   - `apocalypse_structures:radio_tower`
   - `apocalypse_structures:radio_tower_top`
   - `apocalypse_structures:radio_tower_base`
   - `apocalypse_structures:tower_block`
   - Any block with "radio_tower" or "tower" in the path

2. **Fallback Detection**: If exact IDs don't match, uses pattern matching on block paths

## Station Assignment

When towers are detected in existing worlds:
- **First tower found** → Assigned to Emergency Broadcast station
- **Subsequent towers** → Assigned to random music stations
- **Assignment is deterministic** → Uses tower position as seed, so same towers always get same stations

## Compatibility Guarantees

✅ **No World Modification**: The mod doesn't modify existing blocks or structures
✅ **No Data Loss**: Existing towers remain unchanged
✅ **Retroactive Detection**: Works with towers placed before the mod was installed
✅ **Duplicate Prevention**: Won't register the same tower twice
✅ **Progressive Scanning**: Chunks are scanned as they load, so no performance hit on world load

## What Happens When You Add the Mod

1. **Server/World Starts**: Mod initializes
2. **World Loads**: Scans spawn area for existing towers
3. **Chunks Load**: Each chunk is scanned as it loads
4. **Towers Detected**: Existing towers are registered and become functional
5. **Players Can Use**: Walkie-talkies immediately work with existing towers

## Performance Considerations

- **Initial Scan**: Only scans loaded chunks around spawn (non-blocking)
- **Progressive**: Other chunks scanned as they naturally load
- **Efficient**: Uses position-based checks to avoid duplicate registrations
- **No Lag**: Scanning happens asynchronously when possible

## Troubleshooting

### Towers Not Detected?

1. **Check Logs**: Look for "Registered tower block: [id]" messages
2. **Verify Block IDs**: Ensure Apocalypse Structures blocks match expected IDs
3. **Check Chunk Loading**: Towers in unloaded chunks will be detected when chunks load
4. **Manual Trigger**: Visit the area with towers - this will load chunks and trigger detection

### Towers Detected But Not Working?

1. **Check Power**: Towers need FE power to broadcast
2. **Check Range**: Ensure you're within broadcast range (500-2000 blocks)
3. **Check Walkie-Talkie**: Make sure you're holding a walkie-talkie and it's turned on
4. **Check Station Unlock**: Interact with tower to unlock its station

## Example Scenario

**Existing World Setup:**
- World has 5 radio towers from Apocalypse Structures
- Towers are scattered across the map
- World was created before Dead Air mod was added

**What Happens:**
1. Mod loads → Detects tower block types
2. Spawn area scanned → Finds 1 tower near spawn
3. Player explores → Chunks load → 4 more towers detected
4. All 5 towers now functional with Dead Air system
5. Players can tune walkie-talkies and receive broadcasts

## Future Enhancements

Planned improvements:
- Command to force-scan entire world: `/deadair scanworld`
- Config option to scan larger radius on world load
- Visual indicator showing which towers are detected
- Save/load tower data to prevent re-scanning
