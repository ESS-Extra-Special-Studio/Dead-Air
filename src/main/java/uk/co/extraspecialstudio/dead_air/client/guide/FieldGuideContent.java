package uk.co.extraspecialstudio.dead_air.client.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Field Guide sections and flat page indices. Flat 0 is the index; Forward advances
 * through every content page in section order.
 */
@SuppressWarnings("null")
public final class FieldGuideContent {

    private static final List<GuideSection> SECTIONS = buildSections();
    /** Flat index of the first page of each section (same order as SECTIONS). */
    private static final int[] SECTION_START_FLAT = computeSectionStarts();
    private static final Map<String, Integer> SECTION_ID_TO_START_FLAT = mapSectionStarts();
    private static final int MAX_FLAT_INDEX;

    static {
        int pages = 0;
        for (GuideSection s : SECTIONS) {
            pages += s.pages().size();
        }
        MAX_FLAT_INDEX = pages;
    }

    private FieldGuideContent() {}

    public static int getMaxFlatIndex() {
        return MAX_FLAT_INDEX;
    }

    /** Flat index 0 is the interactive index; flat 1+ are content pages in reading order. */
    public static boolean isIndex(int flatIndex) {
        return flatIndex <= 0;
    }

    public static List<GuideSection> sections() {
        return SECTIONS;
    }

    public static int getFlatForSectionStart(String sectionId) {
        Integer start = SECTION_ID_TO_START_FLAT.get(sectionId);
        return start == null ? 1 : start;
    }

    private static int[] resolveContentPosition(int flatIndex) {
        if (flatIndex <= 0 || flatIndex > MAX_FLAT_INDEX) {
            throw new IllegalArgumentException("Invalid flat index: " + flatIndex);
        }
        int remaining = flatIndex;
        for (int sectionIdx = 0; sectionIdx < SECTIONS.size(); sectionIdx++) {
            GuideSection section = SECTIONS.get(sectionIdx);
            int n = section.pages().size();
            if (remaining <= n) {
                int pageIdx = remaining - 1;
                return new int[] { sectionIdx, pageIdx };
            }
            remaining -= n;
        }
        throw new IllegalArgumentException("Invalid flat index: " + flatIndex);
    }

    /** Section title for a non-index flat page. */
    public static String getSectionTitleForFlat(int flatIndex) {
        int[] pos = resolveContentPosition(flatIndex);
        return SECTIONS.get(pos[0]).title();
    }

    /** One-based page number within its section for a non-index flat page. */
    public static int getPageOneBasedForFlat(int flatIndex) {
        int[] pos = resolveContentPosition(flatIndex);
        return pos[1] + 1;
    }

    /** Total page count for the section containing this non-index flat page. */
    public static int getPagesInSectionForFlat(int flatIndex) {
        int[] pos = resolveContentPosition(flatIndex);
        return SECTIONS.get(pos[0]).pages().size();
    }

    /** Body lines for a non-index flat page. */
    public static List<String> getLinesForFlat(int flatIndex) {
        int[] pos = resolveContentPosition(flatIndex);
        return SECTIONS.get(pos[0]).pages().get(pos[1]);
    }

    private static int[] computeSectionStarts() {
        int[] starts = new int[SECTIONS.size()];
        int flat = 1;
        for (int i = 0; i < SECTIONS.size(); i++) {
            starts[i] = flat;
            flat += SECTIONS.get(i).pages().size();
        }
        return starts;
    }

    private static Map<String, Integer> mapSectionStarts() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < SECTIONS.size(); i++) {
            map.put(SECTIONS.get(i).id(), SECTION_START_FLAT[i]);
        }
        return Map.copyOf(map);
    }

    private static List<GuideSection> buildSections() {
        List<GuideSection> list = new ArrayList<>();

        // ---- Index order (exact titles): Intro, How To Play, Materials,
        // Current Expansions, Planned Material ----

        list.add(new GuideSection(
            "intro",
            "Intro",
            List.of(
                List.of(
                    "Welcome to Dead Air.",
                    "",
                    "Dead Air turns radio towers into broadcast nodes. Each tower can",
                    "broadcast a different station with its own music or audio—Bedrock",
                    "Radio, Creatopia Radio, Jukebox FM, Remix Radio, optional internet",
                    "streams, and dynamic stations from expansion mods."
                ),
                List.of(
                    "Dead Air finds Radio Panel blocks (from Apocalypse Structures /",
                    "RadioTowers) and treats them as broadcast towers.",
                    "",
                    "Emergency Broadcast (88.5 MHz) can come from any powered tower.",
                    "With a radio on, being in range of a tower unlocks its station;",
                    "unlocked stations stay available."
                ),
                List.of(
                    "Tower types: Standard towers work immediately. Fenced and Overrun",
                    "need one Radio Panel right-click to activate; that state saves.",
                    "",
                    "Audio plays when a Dead Air radio is on you (hand, hotbar, or",
                    "inventory—configurable). Volume follows signal. Weak signal or bad",
                    "tuning adds static.",
                    "",
                    "Use the Index for How To Play, Materials, Current Expansions, and",
                    "Planned Material—or press Forward to read in order."
                )
            )
        ));

        list.add(new GuideSection(
            "how_to_play",
            "How To Play",
            List.of(
                List.of(
                    "Your radios",
                    "",
                    "Craft a T1.Radio (see Materials), or find one as default guaranteed",
                    "loot in RadioTowers radio tower chests. Optionally upgrade to",
                    "T2.Radio (Walkie Link) with a Dimensional Relay.",
                    "",
                    "Hold a Dead Air radio and press N (default) or right-click to open",
                    "the tuning screen. Use the frequency dial (88.0–108.0 MHz), the",
                    "slider, or click a station in the list."
                ),
                List.of(
                    "Tuning and signal",
                    "",
                    "You lock onto a station when within about 0.2 MHz of its frequency.",
                    "Signal shows as 0–5 bars (distance, line-of-sight, weather).",
                    "Bars use ~75-block steps from the tuned tower: 5/5 within 75,",
                    "4/5 at 150, 3/5 at 225, 2/5 at 300, 1/5 at 375, none beyond.",
                    "",
                    "On a T2, Link Selected Station locks the tuned station when that",
                    "station is coming from a Signal-Upgraded tower in range (no need",
                    "to stand at the panel). Unlink Station clears that lock. Without a",
                    "link, the list shows nearby stations only.",
                    "",
                    "A link follows its panel: re-assign that panel to another station",
                    "and the linked walkie retunes to it automatically."
                ),
                List.of(
                    "Panels and progression",
                    "",
                    "Radio Panels change which station a tower broadcasts. With",
                    "RadioTowers installed you also get airdrop / wave flows and panel",
                    "GUIs. Dead Air adds broadcasts, stations, signal, and music on top.",
                    "",
                    "Ping Location (tuning screen) sends your name and coordinates to",
                    "chat. Further options live in dead_air-common.toml and the in-game",
                    "config (ranges, volume, HUD, radioAlwaysOn, and more)."
                ),
                List.of(
                    "Default stations (MHz)",
                    "- Emergency Broadcast — 88.5",
                    "- Bedrock Radio — 95.5",
                    "- Creatopia Radio — 97.5",
                    "- Remix Radio — 99.1",
                    "- Jukebox FM — 105.9 (unlock with Jukebox Upgrade on a panel;",
                    "  recipes under Materials → Disc Compendiums)",
                    "",
                    "Internet streams when available: Creatopia Radio 1 (108.1),",
                    "Ambiance FM (108.5). Expansion stations appear when those mods",
                    "are installed—see Current Expansions."
                ),
                List.of(
                    "Airdrops (with RadioTowers)",
                    "",
                    "Call airdrops from panels: pick catalog items by point cost, then",
                    "defend waves where Zombie Waves is present. Dead Air adds T1.Radio",
                    "(walkie_t1) to the catalog when both mods load.",
                    "",
                    "Configure catalog loot, standard loot tables, cooldowns, waves,",
                    "and tower density in config/radiotowers-common.toml — not in",
                    "Dead Air’s music config."
                )
            )
        ));

        list.add(new GuideSection(
            "materials",
            "Materials",
            List.of(
                List.of(
                    "Dead Air’s unique crafting materials",
                    "",
                    "This section covers every Dead Air-made resource and how you get",
                    "or craft it. Each material has its own pages below:",
                    "",
                    "- Static Note",
                    "- Resonant Chord",
                    "- Disc Compendium (I) and (II)",
                    "- Dimensional Relay",
                    "",
                    "Related crafts that use these materials (T1 / T2 radios, Signal",
                    "Upgrade, Jukebox Upgrade, Radio Panel) are listed on those pages."
                ),
                // ---- Static Note ----
                List.of(
                    "Static Note",
                    "",
                    "A glowing scrap of broadcast residue. It does not craft—you earn",
                    "it by listening.",
                    "",
                    "While a Dead Air radio in your inventory is powered on and tuned",
                    "to a station, mobs you kill have a chance to drop a Static Note",
                    "based only on your one active radio (extra radios do not help):",
                    "- T1.Radio — 15% chance",
                    "- T2.Radio — 30% chance",
                    "",
                    "Notes can be placed as decorations (right-click a block face)."
                ),
                List.of(
                    "Static Note — uses",
                    "",
                    "Recipe: none (enemy drops while listening only).",
                    "",
                    "T1.Radio — shaped crafting (3×3):",
                    "  I R I",
                    "  R N R",
                    "  I R I",
                    "I = Iron Ingot, R = Redstone, N = Static Note.",
                    "Result: 1× T1.Radio.",
                    "",
                    "Also default guaranteed loot in RadioTowers radio tower chests",
                    "(structure crates). Airdrop crates are separate and only contain",
                    "what you ordered.",
                    "",
                    "Nine Static Notes thrown into a pile on the ground merge into one",
                    "Resonant Chord (see next pages)."
                ),
                // ---- Resonant Chord ----
                List.of(
                    "Resonant Chord",
                    "",
                    "A fused bundle of Static Notes. Crafted in the world, not at a",
                    "crafting table: throw Static Notes onto the ground until nine",
                    "are piled together—they merge into one Resonant Chord.",
                    "",
                    "Chords can be placed as decorations (right-click a block face).",
                    "They feed higher-tier crafts: Dimensional Relay, Radio Panel."
                ),
                List.of(
                    "Resonant Chord — recipes that use it",
                    "",
                    "Dimensional Relay — shaped (3×3):",
                    "  G C G",
                    "  C E C",
                    "  G C G",
                    "G = Gold Ingot, C = Resonant Chord, E = Eye of Ender.",
                    "Result: 1× Dimensional Relay.",
                    "",
                    "Radio Panel (when RadioTowers is installed) — shaped (3×3):",
                    "  I C I",
                    "  E T E",
                    "  I C I",
                    "I = Iron Ingot, C = Resonant Chord, E = Echo Shard,",
                    "T = T1.Radio. Result: 1× Radio Panel (radiotowers)."
                ),
                // ---- Disc Compendium I ----
                List.of(
                    "Disc Compendium (I)",
                    "",
                    "A bound set of early vanilla music discs. Placeable as a block",
                    "(breaks back into a pickupable item).",
                    "",
                    "Recipe — shapeless (crafting table), nine discs:",
                    "13, Cat, Blocks, Chirp, Far, Mall, Mellohi, Stal, Strad.",
                    "(Item ids: minecraft:music_disc_13 … music_disc_strad.)",
                    "",
                    "Result: 1× Disc Compendium (I)."
                ),
                // ---- Disc Compendium II ----
                List.of(
                    "Disc Compendium (II)",
                    "",
                    "The remaining vanilla discs for Minecraft 1.20.1. Also placeable",
                    "as a block.",
                    "",
                    "Recipe — shapeless (crafting table), seven discs:",
                    "Ward, 11, Wait, Otherside, 5, Pigstep, Relic.",
                    "(minecraft:music_disc_ward … music_disc_relic.)",
                    "",
                    "Result: 1× Disc Compendium (II).",
                    "",
                    "Compendium (I) + (II) cover every vanilla disc in 1.20.1—nothing",
                    "is skipped. A 3×3 grid cannot hold all sixteen at once, so Dead",
                    "Air splits them into two crafts."
                ),
                List.of(
                    "Disc Compendiums → Jukebox Upgrade",
                    "",
                    "Jukebox Upgrade — shapeless, three ingredients:",
                    "Disc Compendium (I), Disc Compendium (II), and one Echo Shard",
                    "(minecraft:echo_shard). Result: 1× Jukebox Upgrade.",
                    "",
                    "Install: hold the upgrade and right-click a Radio Panel (once per",
                    "panel). Listeners in range unlock Jukebox FM. Until then,",
                    "overworld towers are not randomly assigned Jukebox FM, and Remix",
                    "leaves disc tracks out of your shuffle."
                ),
                // ---- Dimensional Relay ----
                List.of(
                    "Dimensional Relay",
                    "",
                    "A cross-dimensional relay core. Required for T2.Radio and the",
                    "Signal Upgrade (tower boost for T2 Link).",
                    "",
                    "Recipe — shaped (3×3):",
                    "  G C G",
                    "  C E C",
                    "  G C G",
                    "G = Gold Ingot, C = Resonant Chord, E = Eye of Ender.",
                    "Result: 1× Dimensional Relay."
                ),
                List.of(
                    "Dimensional Relay — crafts that use it",
                    "",
                    "T2.Radio (Walkie Link) — shaped (3×3):",
                    "  G R G",
                    "  I W I",
                    "  I I I",
                    "G = Gold Ingot, R = Dimensional Relay, I = Iron Ingot,",
                    "W = T1.Radio. Result: 1× T2.Radio.",
                    "",
                    "Signal Upgrade — shaped (3×3):",
                    "  G I G",
                    "  I R I",
                    "  G I G",
                    "G = Gold Ingot, I = Iron Ingot, R = Dimensional Relay.",
                    "Result: 1× Signal Upgrade. Install on a Radio Panel to enable",
                    "T2 Link / cross-dim listening."
                )
            )
        ));

        list.add(new GuideSection(
            "expansions",
            "Current Expansions",
            List.of(
                List.of(
                    "Dead Air expansions are optional companion mods. Each adds a",
                    "curated soundtrack pack as new stations that plug into Dead Air",
                    "when you install its JAR alongside the main mod. They show up as",
                    "dynamic stations once discovered like any other station."
                ),
                List.of(
                    "Current Dead Air expansions:",
                    "- Dead Air - Wayfarer Radio",
                    "- Dead Air - Frontline FM",
                    "- Dead Air - After Hours FM (Lo-Fi)",
                    "- Dead Air - Block Beats FM (Beats)",
                    "- Dead Air - Broken Youth Radio (Pop Punk)",
                    "- Dead Air - Iron Rain FM (Metal)",
                    "- Dead Air - Zero Gravity (Pop Rock / Indie Pop)",
                    "",
                    "Station display names are defined so in-game names stay readable",
                    "instead of raw mod IDs."
                )
            )
        ));

        list.add(new GuideSection(
            "planned",
            "Planned Material",
            List.of(
                List.of(
                    "Planned expansions (not yet released):",
                    "- Dead Air - Pop Paradise Radio (Pop)",
                    "- Dead Air - Frequency X (Electronic / Synthwave)",
                    "- Dead Air - Parallel Horizons FM",
                    "",
                    "Names and themes may change before release."
                ),
                List.of(
                    "Planned gameplay / items (timing and balance may change):",
                    "",
                    "More panel upgrades beyond Signal and Jukebox—for example airdrop",
                    "tier boosts or safe-zone style effects around a panel’s footprint.",
                    "",
                    "Further materials and crafts will be documented here as they ship.",
                    "This section is forward-looking; details land in future updates."
                ),
                List.of(
                    "Older roadmap notes still under consideration:",
                    "- Extra tower upgrades (airdrop tier, safezone, longer range)",
                    "- More dimensional relay / structure play outside the Overworld",
                    "",
                    "Nothing here is a promise—only a direction of travel."
                )
            )
        ));

        return List.copyOf(list);
    }

    public record GuideSection(String id, String title, List<List<String>> pages) {}

    /** Index screen intro lines (no inline buttons here—sections are buttons only). */
    public static List<String> getIndexIntroLines() {
        return List.of(
            "Welcome to Dead Air.",
            "Topics: Intro · How To Play · Materials · Current Expansions ·",
            "Planned Material.",
            "",
            "Pick a topic below, or press Forward to read from the start.",
            "Tip: Back / Forward move one page; Index returns here."
        );
    }
}
