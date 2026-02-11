# Dead Air Mod - Testing Guide

## Quick Start Testing

### 1. Opening the Tuning GUI
**Keybind:** Press **H** (default key)
- Hold any walkie-talkie (any tier: wooden, iron, gold, diamond, etc.) in your main or off hand
- Press **H** to open the tuning screen
- If it doesn't open, check Controls settings for "key.dead_air.tune_walkie"
- **Note:** Changed from G to H to avoid conflicts with other mods

### 2. Walkie-Talkie Support
**All Tiers Supported:**
- ✅ Wooden Walkie-Talkie
- ✅ Iron Walkie-Talkie  
- ✅ Gold Walkie-Talkie
- ✅ Diamond Walkie-Talkie
- ✅ Netherite Walkie-Talkie (if available)
- ✅ Any other tier variants

The mod automatically detects ALL walkie-talkie items by scanning for items with "walkie" and "talkie" in their name/path.

## Testing Checklist

### Basic Functionality
- [ ] Hold walkie-talkie (any tier) in hand
- [ ] Press **R** to open tuning GUI
- [ ] See walkie-talkie overlay appear (signal bars, station info)
- [ ] Tune to a station using the GUI
- [ ] Hear audio when tuned to a station (if tower is in range)

### Tower Detection
- [ ] Find or place a radio tower from Apocalypse Structures
- [ ] Check logs for "Registered tower block" or "Radio tower placed"
- [ ] Power the tower with FE (Forge Energy)
- [ ] Hold walkie-talkie and tune to Emergency Broadcast
- [ ] Should see signal bars increase when near powered tower

### Station Unlocking
- [ ] Discover a radio tower (visit it)
- [ ] Interact with tower (right-click) to unlock stations
- [ ] Open tuning GUI (R key) - should see unlocked stations
- [ ] Tune to different stations

### Emergency Broadcasts
- [ ] Tune walkie-talkie to Emergency Broadcast station
- [ ] Trigger an airdrop from Apocalypse Structures
- [ ] Should receive alert message with direction and distance
- [ ] Follow the compass direction to find the airdrop

### Signal Strength
- [ ] Hold walkie-talkie and tune to a station
- [ ] Move closer to tower - signal bars should increase
- [ ] Move away from tower - signal bars should decrease
- [ ] Go behind obstacles - signal should weaken
- [ ] Check overlay shows signal strength (1-5 bars)

### Audio Features
- [ ] Tune to Music station - should hear music
- [ ] Tune to Emergency Broadcast - should hear alerts
- [ ] Move between two towers - should hear audio blending
- [ ] Signal strength affects volume and static

### Zombie Attraction
- [ ] Power a radio tower
- [ ] Tune walkie-talkie to a station
- [ ] Wait near the tower
- [ ] Zombies should be attracted to the active broadcast

### Signal Hunting
- [ ] Hold walkie-talkie and tune to a station
- [ ] Look at overlay - should show directional arrow if signal is weak
- [ ] Follow the arrow to find the tower
- [ ] Signal should get stronger as you approach

## Troubleshooting

### GUI Won't Open
- **Check:** Are you holding a walkie-talkie? (any tier works)
- **Check:** Controls → Search for "dead_air" or "tune_walkie"
- **Check:** Keybind might be conflicting - change it in Controls
- **Check:** Logs for "Found walkie-talkie item" messages

### No Signal
- **Check:** Is there a radio tower nearby?
- **Check:** Is the tower powered? (needs FE/Forge Energy)
- **Check:** Are you tuned to the correct station?
- **Check:** Is the walkie-talkie turned ON? (use Power button in GUI)

### No Stations Available
- **Check:** Have you discovered any towers yet?
- **Check:** Interact with a tower (right-click) to unlock stations
- **Check:** Logs for "Station unlocked" messages

### Audio Not Playing
- **Check:** Is walkie-talkie turned ON?
- **Check:** Are you tuned to a station?
- **Check:** Is there a powered tower in range?
- **Check:** Minecraft sound settings (not muted)

## Keybind Information

**Default Key:** H (changed from G to avoid conflicts with other mods)
**Category:** Dead Air
**Translation Key:** `key.dead_air.tune_walkie`
**Can be changed in:** Controls → Search "dead_air" or "tune"

## Walkie-Talkie Tier Support

The mod supports **ALL tiers** of walkie-talkies:
- Detection is based on item name/path containing "walkie" and "talkie"
- Works with: Wooden, Iron, Gold, Diamond, Netherite, and any custom tiers
- All tiers function identically - no tier-specific restrictions

## Log Messages to Watch For

**On Mod Load:**
- `"Detected X walkie-talkie item(s) - all tiers supported"`
- `"Found walkie-talkie item (tier X): [item_id]"`

**When Holding Walkie-Talkie:**
- Overlay should appear showing signal bars

**When Tuning:**
- `"Tuned to: [station name]"`

**When Finding Towers:**
- `"Registered tower block: [block_id]"`
- `"Radio tower placed at [pos] broadcasting station [name]"`

**When Airdrops Spawn:**
- `"Airdrop detected from Apocalypse Structures at [pos]"`
