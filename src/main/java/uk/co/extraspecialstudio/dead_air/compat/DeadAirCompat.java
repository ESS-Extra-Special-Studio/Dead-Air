package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraftforge.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft-compat hub. Call once from commonSetup. Never hard-depends on third-party mods —
 * each integrator uses {@link ModList#isLoaded} + reflection / tags / loot fills and soft-fails.
 */
public final class DeadAirCompat {
    private DeadAirCompat() {}

    public static void register() {
        try {
            SurvivorTaczCompat.isLoaded(); // probe TaCZ gun operator
            PointBlankCompat.probe();
            MapWaypointCompat.probeServer();
            FtbChunksCompat.probe();
            HordeCompat.probe();
            VehicleCompat.probe();
            AircraftCompat.probe();
            ColdSweatCompat.probe();
            ToughAsNailsCompat.probe();
            if (SurvivorApocalypseGearCompat.anyLoaded()) {
                Dead_air.LOGGER.info("[Dead Air] Apocalypse gear soft-compat ready (AN/SI/ZSK/Chovy/zmodfood)");
            }
            AweRoadsCompat.probe();
            Dead_air.LOGGER.info("[Dead Air] Soft-compat registered: {}", shortSummary());
        } catch (Throwable t) {
            Dead_air.LOGGER.warn("[Dead Air] Soft-compat register failed (ignored): {}", t.toString());
        }
    }

    public static String shortSummary() {
        return String.join(", ", statusLines(true));
    }

    /** Lines for {@code /deadair compat status}. */
    public static List<String> statusLines() {
        return statusLines(false);
    }

    private static List<String> statusLines(boolean compact) {
        List<String> out = new ArrayList<>();
        out.add(line("tacz", "tacz", Config.compatTacz, SurvivorTaczCompat.isLoaded(),
            "survivor equip/shoot + guns loot", compact));
        out.add(line("journeymap", "journeymap", Config.compatJourneymap, ModList.get().isLoaded("journeymap"),
            "EB site waypoints", compact));
        boolean xaero = ModList.get().isLoaded("xaerominimap") || ModList.get().isLoaded("xaeroworldmap");
        out.add(line("xaero", "xaerominimap/xaeroworldmap", Config.compatXaero, xaero,
            "EB site waypoints", compact));
        out.add(line("ftbchunks", "ftbchunks", Config.compatFtbChunks, ModList.get().isLoaded("ftbchunks"),
            "base claim/highlight best-effort", compact));
        out.add(line("cold_sweat", "cold_sweat", Config.compatColdSweat, ColdSweatCompat.isLoaded(),
            "medic/auction temp loot", compact));
        out.add(line("toughasnails", "toughasnails", Config.compatToughAsNails, ToughAsNailsCompat.isLoaded(),
            "medic/survivor water loot", compact));
        out.add(line("vehicle", VehicleCompat.detectedModId(), Config.compatVehicle, VehicleCompat.isLoaded(),
            "crash-site parts chest", compact));
        out.add(line("immersive_aircraft", "immersive_aircraft", Config.compatImmersiveAircraft,
            AircraftCompat.isLoaded(), "crash wreck loot flavour", compact));
        out.add(line("pointblank", PointBlankCompat.detectedModId(), Config.compatPointBlank,
            PointBlankCompat.isLoaded(), "survivor gun equip + guns loot", compact));
        out.add(line("horde", HordeCompat.detectedModId(), Config.compatHorde, HordeCompat.isLoaded(),
            "base-attack wave bump", compact));
        boolean apo = SurvivorApocalypseGearCompat.anyLoaded();
        out.add(line("apocalypse_gear", "apocalypsenow/survival_instinct/zombiekit/chovy/zmodfood",
            Config.compatApocalypseGear, apo,
            "survivor apparel + EB food/med/gear loot", compact));
        out.add(line("awe_roads", "apocalyptic_world_engine", true, AweRoadsCompat.isLoaded(),
            "EB avoid corridors; convoy snap to roads", compact));
        return out;
    }

    private static String line(String key, String modId, boolean enabled, boolean loaded, String what, boolean compact) {
        String state = !enabled ? "off" : (loaded ? "loaded+on" : "missing");
        if (compact) return key + "=" + state;
        return key + " [" + modId + "] " + state + " — " + what;
    }
}
