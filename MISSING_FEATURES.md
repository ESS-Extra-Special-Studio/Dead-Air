# Missing Features Analysis

After reviewing the design briefs, here are the CRITICAL missing features:

## ❌ Missing Core Features:

1. **Tower Interaction GUI** - Design brief says: "Player discovers powered tower → interacts with panel → unlocks stations"
   - Currently: No way to interact with towers
   - Needed: Right-click tower block to open GUI, unlock stations

2. **Walkie-Talkie Tuning System** - Design brief says: "Tune walkie to Emergency Broadcast"
   - Currently: No way for players to actually tune to stations
   - Needed: Keybind + GUI to select/tune to stations

3. **Station Unlocking System** - Design brief says: "Stations unlock when players find towers"
   - Currently: All stations are always available
   - Needed: Per-player station unlock tracking

4. **Signal Hunting** - Design brief says: "follow faint signals to discover towers"
   - Currently: Signal just appears/disappears
   - Needed: Directional signal indicator, signal strength increases as you approach

5. **Audio Blending** - Design brief says: "Multiple stations can overlap, blending audio"
   - Currently: Only one station plays at a time
   - Needed: Multiple station audio mixing

6. **Zombie Attraction** - Design brief says: "active broadcasts can attract zombies"
   - Currently: Not implemented
   - Needed: Zombie AI modification to be attracted to active towers

Let me implement these now!
