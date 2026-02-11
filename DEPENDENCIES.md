# Dead Air Mod - Required Dependencies for Test Modpack

## Minecraft & Forge Version
- **Minecraft**: 1.20.1
- **Forge**: 47.4.0 or higher (47.4.x series)
- **Loader Version Range**: [47,)

## Hard Dependencies (Required)

### 1. Apocalypse Structures: Radio Towers and Airdrops
- **Mod ID**: Any mod that adds radio tower blocks (dynamically detected)
- **Version**: Any version for MC 1.20.1
- **Purpose**: Provides radio tower structures and airdrop events
- **CurseForge**: Search for "Apocalypse Structures" or "Apocalypse Rebooted" for MC 1.20.1
- **Required**: NO - The mod will automatically detect radio tower blocks from any installed mod
- **Note**: The mod dynamically scans for blocks matching radio tower patterns, so it works with any mod that adds radio towers

### 2. Walkie-Talkie Mod
- **Mod ID**: `walkietalkie` (primary)
- **Alternative IDs**: `radio`, `walkie_talkie` (fallback support)
- **Version**: Any version for MC 1.20.1
- **Purpose**: Provides the walkie-talkie item for tuning stations
- **CurseForge**: Search for "Walkie Talkie" mod for MC 1.20.1
- **Required**: YES - Mod will not function without this
- **Note**: Mod has fallback detection, but primary mod ID `walkietalkie` is recommended

## Optional Dependencies (Recommended but not Required)

### 3. Immersive Engineering
- **Mod ID**: `immersiveengineering`
- **Version**: Any version for MC 1.20.1
- **Purpose**: Provides generators and cables for powering radio towers
- **CurseForge**: Search for "Immersive Engineering" for MC 1.20.1
- **Required**: NO - Towers can use any FE-compatible power source

### 4. Thermal Series
- **Mod ID**: `thermal`
- **Version**: Any version for MC 1.20.1
- **Purpose**: Alternative power sources for radio towers
- **CurseForge**: Search for "Thermal" mods for MC 1.20.1
- **Required**: NO - Towers can use any FE-compatible power source

## Power System Compatibility

The mod uses **Forge Energy (FE)** API, which is compatible with:
- Immersive Engineering generators/cables
- Thermal Series generators
- Any mod that provides FE-compatible power sources
- Custom generators that implement Forge Energy capability

## Modpack Installation Order

1. **Minecraft 1.20.1**
2. **Forge 47.4.0+** (any 47.4.x version)
3. **Apocalypse Structures: Radio Towers and Airdrops** (optional - any mod with radio towers works)
4. **Walkie-Talkie Mod** (required - try `walkietalkie` first)
5. **Dead Air** (this mod)
6. **Immersive Engineering** (optional, for power)
7. **Thermal Series** (optional, for power)

## Testing Checklist

- [ ] Minecraft 1.20.1 installed
- [ ] Forge 47.4.0 or higher installed
- [ ] Apocalypse Structures mod installed
- [ ] Walkie-Talkie mod installed
- [ ] Dead Air mod installed
- [ ] (Optional) Immersive Engineering installed
- [ ] (Optional) Thermal mods installed

## Known Compatible Mod Lists

If you're building a modpack, these mods are known to work well together:
- Apocalypse Structures
- Walkie-Talkie (any implementation)
- Immersive Engineering
- Thermal Series
- Any other mods that add music tracks (will be auto-discovered)

## Troubleshooting

### Mod Not Loading?
- Check Forge version is 47.4.0 or higher
- Verify all hard dependencies are installed
- Check mod loading order in logs

### Towers Not Detected?
- Ensure Apocalypse Structures is loaded
- Check logs for "Registered tower block" messages
- Visit areas with towers to trigger chunk loading

### Walkie-Talkie Not Working?
- Verify walkie-talkie mod is installed
- Check logs for "Found walkie-talkie item" message
- Try holding the walkie-talkie item and pressing R key
