# Dead Air - CurseForge Page Description

## Short Summary (for CurseForge short description field)

Dead Air is a survival-driven radio network mod for Zombiecraft that transforms radio towers into broadcast nodes and walkie-talkies into exploration tools. It integrates seamlessly with Apocalypse Structures: Radio Towers and Airdrops to create an immersive radio system where players hunt signals, discover stations, and tune into music broadcasts while navigating a dangerous world.

---

## Full Description (for CurseForge long description field)

Dead Air

Dead Air is a companion mod for Zombiecraft that transforms the world into a living radio network. It doesn't replace your walkie-talkie mod or tower structures - it works alongside them to create an immersive survival experience where radio signals guide exploration, music stations provide atmosphere, and emergency broadcasts create tension. Players hunt for signals, discover new stations, and tune into broadcasts while navigating a post-apocalyptic world.

 

What It Does:

Dead Air integrates with Apocalypse Structures: Radio Towers and Airdrops to turn radio towers into active broadcast nodes. Each tower can broadcast different music stations - from vanilla Minecraft tracks on Bedrock Radio to Zombiecraft music on Zombiecraft Radio, medieval themes on Medieval FM, jukebox tracks on Jukebox FM, or a mix of everything on Remix Radio. The mod also automatically discovers music from other installed mods and creates dynamic stations for them.

 

The Emergency Broadcast frequency (88.5 MHz) provides critical information when towers are activated. All towers can broadcast on this frequency, creating a network of emergency communication across your world. Signal strength is calculated dynamically based on distance, line-of-sight, and weather conditions - get closer to towers for stronger signals, or watch your bars drop as you move away.

 

Players discover stations by entering the broadcast range of a tower while holding a powered-on walkie-talkie. Once discovered, stations are permanently unlocked and can be tuned into from anywhere. The mod supports both official towers spawned via commands and player-built towers using Radio Panel blocks, giving players the freedom to create their own radio network.

 

How It Works:

Dead Air scans for Radio Panel blocks from the RadioTowers mod and registers them as broadcast towers. Standard towers always start powered and begin broadcasting immediately, while Fenced and Overrun towers require activation via their Radio Panels. Once activated, towers persist their power state across world reloads, so you won't lose your radio network when logging out.

 

The mod tracks every tower's power state, broadcast range, and assigned station. When you tune your walkie-talkie to a frequency, Dead Air calculates signal strength from all towers broadcasting that station within range. Music plays dynamically based on signal strength - strong signals mean clear audio, while weak signals add static and reduce volume. The system supports mid-song tuning, so you can switch stations at any time and hear tracks from wherever they're currently playing.

 

Signal strength is displayed as 0-5 bars in both the tuning GUI and the in-game overlay. The bars use a gradient system (red to green) and update in real-time as you move. Emergency Broadcast always shows signal when near any powered tower, while music stations only show signal when tuned to a tower broadcasting that specific station.

 

How To Use It:

Just install Dead Air alongside Apocalypse Structures: Radio Towers and Airdrops and your walkie-talkie mod. The mod automatically detects Radio Panel blocks and registers them as broadcast towers. Hold any walkie-talkie (wooden, iron, gold, diamond, or any tier) and press **H** (default keybind) to open the tuning screen.

 

The tuning screen features a retro radio dial interface inspired by Fallout 4's Pip-Boy radio. Use the frequency slider (88.0-108.0 MHz) to tune through available stations, or click on a station in the list to quick-tune. The dial shows your current frequency with a tuning needle, and signal bars display real-time strength. A volume slider lets you adjust playback volume, and a "Ping Location" button sends your coordinates to chat.

 

When you enter the range of a new tower while holding a powered-on walkie-talkie, you'll receive a discovery message. Stations are automatically unlocked and added to your station list. Tune to any unlocked station to hear its music, or tune to Emergency Broadcast (88.5 MHz) for emergency alerts.

 

For server admins, there's a command to spawn test towers:
 

/dead_air spawntower <type> [x] [y] [z] - Spawns a tower at the specified location (or your current position). Types: standard, fenced, overrun

 

The mod is fully configurable - you can adjust broadcast ranges, power consumption, signal strength calculations, weather effects, and more in the server config file. There's also an option to toggle whether walkie-talkies play music only while held or also when in inventory/placed down.

Important: This mod requires Apocalypse Structures: Radio Towers and Airdrops (radiotowers) and a walkie-talkie mod (walkietalkie) to function. It does not replace these mods - it extends them with a complete radio broadcasting system. The RadioTowers mod still handles tower structures and airdrops; Dead Air adds the radio network, station discovery, and music playback on top of that foundation.
