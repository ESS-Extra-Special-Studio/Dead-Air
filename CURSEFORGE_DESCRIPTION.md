# Dead Air - CurseForge Page Description

## Short Summary (for CurseForge short description field — under 200 characters)

Extra Special Studio presents Dead Air 3.0.0: Emergency Broadcasts, The Listener, survivors, T3 radio & soundtrack towers. Requires RadioTowers, GeckoLib, ESL & ESC.

*(199 characters)*

---

## Full Description (for CurseForge long description field — paste below the line)

---

**3.0.0 trailer:** [https://youtu.be/a8XrjZXm2S0](https://youtu.be/a8XrjZXm2S0)

---

**PLENTY MORE CONTENT COMING SOON!**

- More Radio Panel upgrades beyond Signal and Jukebox
- More soundtrack expansions (Pop Paradise, Frequency X, Parallel Horizons, and friends)
- Further materials and crafts as the progression deepens

---

**Dead Air:**

Turns radio towers into broadcast nodes. Each tower can broadcast a different station. Examples include **Bedrock Radio**, **Creatopia Radio**, **Jukebox FM**, **Remix Radio**, optional **internet** streams, and **dynamic** stations from expansion mods / `autoDiscoverModMusic`.

**Radios** — **T1.Radio**, **T2.Radio (Walkie Link)**, and **T3.Radio** with animated GeckoLib models. No third-party walkie mod required. T3 + **Signal Decoder** unlock **Emergency Broadcasts**; **Transmit** answers **The Listener**.

**3.0.0 — Emergency Broadcasts & survivors**

- Night scheduler, map markers, proximity prompts, and procedural sites (distress camps, refugee convoys, auctions, crash wrecks, broken relays, base under attack, Pirate FM)
- Story spine through relays → **It Hears You** → **Signal Breaker** (acoustic weapon) → **The Listener** boss
- Recruit survivors (professions, party HUD / ESC action wheel, settle at a radio-panel base)
- Camp décor for EB sites (tents, firepits, folding chairs, clothesline, and more)

**Progression & materials**

- **Static Notes** drop while listening (one active radio: T1 15% / T2 30%); nine Notes on the ground merge into a **Resonant Chord**
- Craft **Dimensional Relay**, **T1 / T2 / T3**, **Signal Upgrade**, **Disc Compendiums → Jukebox Upgrade**, **Signal Decoder / Signal Breaker** components, and a **Radio Panel** recipe (when RadioTowers is present)
- Install **Signal Upgrade** on a panel to unlock **T2 Link**: listen to that tower across dimensions; the link follows if you re-assign the panel’s station
- In-game **Field Guide** covers Intro, How To Play, Materials (recipes), Emergency Broadcasts, The Listener, Current Expansions, and Planned Material

You can still create custom stations and playlists:

**Custom stations:** put **`.ogg`** files in `config/dead_air/custom stations/<Station Name>/` (one subfolder per station; restart).

**Extra tracks for built-in stations:** set **`customMusicPath`** to a folder with `assets/dead_air/sounds/music/custom/*.ogg`.

**Soundtrack packs on servers:** expansion packs stay **client-only**. Players install them; the server does not need the large audio jars. Official ESS packs are announced when a player who has one joins. Third-party packs: add the mod id to `[stations] expansionStations` in `dead_air-common.toml` and restart.

---

**How it works**

Dead Air uses **Radio Panel** blocks (Apocalypse Structures / RadioTowers) as broadcast towers. You can craft panels for your own bases.

**Emergency Broadcast** (88.5 MHz) can come from any powered tower. With **T3** decoded, story and side transmissions drive the 3.0.0 content above.

**Signal** is shown as 0–5 bars (distance, line-of-sight, weather). Bars use **75-block steps**: 5/5 within 75 blocks, 4/5 at 150, 3/5 at 225, 2/5 at 300, 1/5 at 375, none beyond that.

**Discovery:** with the radio on, being in range of a tower unlocks its station; unlocked stations stay available.

**Tower types:** **Standard** towers work immediately. **Fenced** and **Overrun** need one **Radio Panel** right-click to activate; that state saves.

**Audio:** music plays from your **one active** tuned radio (hand, hotbar, or inventory—configurable). Volume follows signal. Weak signal or bad tuning adds **static**. Cross-dimension travel resumes the same track near where it left off when Linked / in range again. Dropped radios do not play.

---

**How to use it**

Hold a **T1** or **T2** Dead Air radio and press **N** (default) or **right-click** to open the tuning screen.

The screen has a retro dial (**88.0–108.0 MHz**). Tune with the slider or by picking a station. On **T2**, **Link Selected Station** / **Unlink Station** locks onto a Signal-Upgraded tower in range (no need to stand at the panel). **Ping Location** sends your name and coordinates to chat.

Further options: **dead_air-common.toml** and the in-game config (ranges, volume, overlay, **`radioAlwaysOn`**, etc.).

---

**Server commands**

`/dead_air structures tower <type> [x] [y] [z]` — spawns a tower at your position or the given coords. Types: **standard**, **fenced**, **overrun**. Spawned towers are active immediately.

`/dead_air structures camp|auction|convoy|crash` — places that Emergency Broadcast site kit at your feet.

`/dead_air structures relay [small|medium|large]` — places a broken relay (optional variant; defaults from story progress).

`/dead_air structures list` — lists your pending EB sites.

---

**Current expansions**

Optional companion soundtrack packs — work standalone or plug into Dead Air as stations:

- **Dead Air - Wayfarer Radio** — adventure / RPG
- **Dead Air - Frontline FM** — high-energy battle / combat
- **Dead Air - After Hours FM** — Lo-Fi / chillout
- **Dead Air - Block Beats FM** — beats
- **Dead Air - Broken Youth Radio** — pop punk
- **Dead Air - Iron Rain FM** — metal
- **Dead Air - Zero Gravity** — pop rock / indie pop

---

**Dependencies**

- **Required:** **Apocalypse Structures: Radio Towers and Airdrops** (RadioTowers), **GeckoLib 4.x**, **ExtraSpecialLIB (ESL)**, **ExtraSpecialCore (ESC)**
- **Optional:** **Berezka’s Library** + **Berezka’s Zombie Waves API** (full airdrop / wave flows with RadioTowers); soft map / gun / survival mod hooks listed in the changelog

Dead Air does **not** replace RadioTowers — it adds broadcasts, stations, signal, music, radios, and progression on top.

**Trailer:** [https://youtu.be/a8XrjZXm2S0](https://youtu.be/a8XrjZXm2S0)

---

**Music rights**

ALL CUSTOM MUSIC TRACKS ARE OWNED BY ME AND MY TEAM. You may use these in any modpack, and play these tracks in YouTube videos / Twitch streams and similar. **Do not distribute the music files individually.**

---
