package uk.creatopia.unbound.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.TowerManager;
import uk.creatopia.unbound.dead_air.station.StationUnlockManager;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerType;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawn tower command for Apocalypse Structures: Radio Towers and Airdrops.
 *
 * Canonical names (from mod creator): "I used MCreator to make the mod and no it's not using any libs.
 * Here are the file names in mcreator: Radiotower, Radiotower2, Radiotoweroverrun.
 * Their registry names are: radiotower, radiotower_2, radiotoweroverrun.
 * You got 5 different towers bc there are two impostors: a loot table and a creative tab with similar names.
 * The creative tab is radiotowers and the loot table is radiotoweroverrunloot."
 *
 * For placement we use the structure TEMPLATE IDs (from template_pool "location"), not the worldgen structure IDs.
 * In radiotowers-1.0.0 JAR: data/radiotowers/structures/*.nbt and template_pool "location" values are:
 *   tower.nbt -> radiotowers:tower,  tower_2.nbt -> radiotowers:tower_2,  tower_overrun.nbt -> radiotowers:tower_overrun.
 * Do not use worldgen IDs (radiotower, radiotower_2, radiotoweroverrun) for getStructureManager().get().
 *
 * Usage: /dead_air spawntower &lt;standard|fenced|overrun&gt; [x y z]
 */
@SuppressWarnings("null")
public class SpawnTowerCommand {

    private static final String NAMESPACE = "radiotowers";

    /** Structure template IDs used by getStructureManager().get() - from template_pool "location" (the .nbt files). */
    private static final ResourceLocation STRUCTURE_STANDARD  = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "tower");
    private static final ResourceLocation STRUCTURE_FENCED   = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "tower_2");
    private static final ResourceLocation STRUCTURE_OVERRUN   = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "tower_overrun");

    private static final int SCAN_DELAY_TICKS = 5;
    private static final int PANEL_SEARCH_RADIUS = 25;

    /** Pending scans: dimension -> (spawnPos -> (ticksLeft, towerTypeString)) */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            ConcurrentHashMap<BlockPos, Map.Entry<Integer, String>>> PENDING_SCANS = new ConcurrentHashMap<>();

    // ---------- Command registration ----------

    /**
     * Use literal subcommands (standard, fenced, overrun) so the client always shows them as suggestions
     * after "/dead_air spawntower ". Register only in RegisterCommandsEvent.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var spawntower = Commands.literal("spawntower").requires(cs -> true);
        for (String type : new String[] { "standard", "fenced", "overrun" }) {
            spawntower = spawntower
                .then(Commands.literal(type)
                    .executes(ctx -> runSpawn(ctx, type, null))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> runSpawn(ctx, type, BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
        }
        dispatcher.register(
            Commands.literal("dead_air")
                .requires(cs -> true)
                .then(spawntower));
    }

    private static int runSpawn(CommandContext<CommandSourceStack> ctx, String typeArg, BlockPos targetPos) {
        CommandSourceStack source = ctx.getSource();
        var level = source.getLevel();
        if (level == null || level.isClientSide() || !(level instanceof ServerLevel)) {
            // Command ran on client or without a server level; must run on server to place structures
            if (level != null && !level.isClientSide()) {
                Dead_air.LOGGER.warn("Dead Air: runSpawn aborted - level is null or not server");
            }
            return 0;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        Dead_air.LOGGER.info("Dead Air: runSpawn executing type={} on server", typeArg);
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("You need OP level 2 to use this command."));
            return 0;
        }
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ResourceLocation structureId = structureIdForType(typeArg);
        if (structureId == null) {
            source.sendFailure(Component.literal("Invalid type. Use: standard, fenced, or overrun"));
            return 0;
        }

        BlockPos origin;
        if (targetPos != null) {
            origin = targetPos;
        } else {
            Direction facing = player.getDirection();
            Vec3 pos = player.position();
            origin = serverLevel.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,
                BlockPos.containing(pos.x + facing.getStepX() * 10, pos.y, pos.z + facing.getStepZ() * 10));
        }

        var templateManager = serverLevel.getStructureManager();
        var opt = templateManager.get(structureId);
        if (opt.isEmpty()) {
            source.sendFailure(Component.literal("Structure not found: " + structureId + ". Is RadioTowers mod installed?"));
            Dead_air.LOGGER.warn("Structure not found: {}", structureId);
            return 0;
        }

        StructureTemplate template = opt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false);

        try {
            template.placeInWorld(serverLevel, origin, origin, settings, serverLevel.getRandom(), Block.UPDATE_CLIENTS);
        } catch (Exception e) {
            Dead_air.LOGGER.error("Failed to place structure at {}", origin, e);
            source.sendFailure(Component.literal("Failed to place structure: " + e.getMessage()));
            return 0;
        }

        ApocalypseTowerType towerType = towerTypeFromString(typeArg);
        BlockPos panelOffset = getPanelOffsetForType(typeArg);
        BlockPos panelPos = panelOffset != null ? origin.offset(panelOffset) : null;

        if (panelPos != null && ApocalypseTowerDetector.isRadioPanel(serverLevel, panelPos) && !TowerManager.isTowerRegistered(serverLevel, panelPos)) {
            RadioStation station = TowerManager.determineStationForTower(serverLevel, panelPos, towerType);
            if (station != null) {
                TowerManager.registerTower(serverLevel, panelPos, station, towerType);
                triggerStationDiscovery(serverLevel, panelPos, station);
                source.sendSuccess(() -> Component.literal("Spawned " + typeArg + " tower at " + origin.toShortString()), true);
                return 1;
            }
        }

        schedulePostPlaceScan(serverLevel, origin, typeArg);
        source.sendSuccess(() -> Component.literal("Spawned " + typeArg + " tower at " + origin.toShortString()), true);
        return 1;
    }

    // ---------- Structure ID and type ----------

    /** Returns the structure ID for the given type string, or null if invalid. */
    private static ResourceLocation structureIdForType(String type) {
        return switch (type) {
            case "standard" -> STRUCTURE_STANDARD;
            case "fenced"   -> STRUCTURE_FENCED;
            case "overrun"  -> STRUCTURE_OVERRUN;
            default -> null;
        };
    }

    private static ApocalypseTowerType towerTypeFromString(String type) {
        return switch (type) {
            case "standard" -> ApocalypseTowerType.STANDARD;
            case "fenced"   -> ApocalypseTowerType.FENCED;
            case "overrun"  -> ApocalypseTowerType.OVERRUN;
            default -> ApocalypseTowerType.STANDARD;
        };
    }

    /**
     * Offset from structure origin to the Radio Panel block.
     * When provided by the mod maker, we register the tower immediately instead of scanning.
     */
    private static BlockPos getPanelOffsetForType(String type) {
        // TODO: set from structure NBT (e.g. case "standard": return new BlockPos(x, y, z);)
        return null;
    }

    // ---------- Delayed scan (fallback when offset unknown) ----------

    private static void schedulePostPlaceScan(ServerLevel level, BlockPos spawnPos, String towerType) {
        PENDING_SCANS
            .computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
            .put(spawnPos.immutable(), Map.entry(SCAN_DELAY_TICKS, towerType));
    }

    public static void processPendingScans(net.minecraft.server.MinecraftServer server) {
        for (var dimEntry : PENDING_SCANS.entrySet()) {
            var dimension = dimEntry.getKey();
            var byPos = dimEntry.getValue();
            var toRun = new ArrayList<Map.Entry<BlockPos, String>>();
            var toRemove = new ArrayList<BlockPos>();

            for (var e : byPos.entrySet()) {
                BlockPos pos = e.getKey();
                int ticks = e.getValue().getKey();
                String type = e.getValue().getValue();
                if (ticks <= 0) {
                    toRun.add(Map.entry(pos, type));
                    toRemove.add(pos);
                } else {
                    byPos.put(pos, Map.entry(ticks - 1, type));
                }
            }
            for (BlockPos pos : toRemove) {
                byPos.remove(pos);
            }

            ServerLevel level = server.getLevel(dimension);
            if (level != null) {
                for (var e : toRun) {
                    scanForPanelAndRegister(level, e.getKey(), e.getValue());
                }
            }
        }
    }

    private static void scanForPanelAndRegister(ServerLevel level, BlockPos spawnPos, String towerTypeStr) {
        ApocalypseTowerType towerType = towerTypeFromString(towerTypeStr);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = -PANEL_SEARCH_RADIUS; x <= PANEL_SEARCH_RADIUS; x++) {
            for (int y = -PANEL_SEARCH_RADIUS; y <= PANEL_SEARCH_RADIUS; y++) {
                for (int z = -PANEL_SEARCH_RADIUS; z <= PANEL_SEARCH_RADIUS; z++) {
                    mutable.set(spawnPos.getX() + x, spawnPos.getY() + y, spawnPos.getZ() + z);
                    if (!ApocalypseTowerDetector.isRadioPanel(level, mutable)) continue;
                    BlockPos panelPos = mutable.immutable();
                    if (TowerManager.isTowerRegistered(level, panelPos)) continue;
                    RadioStation station = TowerManager.determineStationForTower(level, panelPos, towerType);
                    if (station == null) continue;
                    TowerManager.registerTower(level, panelPos, station, towerType);
                    triggerStationDiscovery(level, panelPos, station);
                    Dead_air.LOGGER.debug("Registered {} tower at {} (panel at {})", towerType, spawnPos, panelPos);
                    return;
                }
            }
        }
        Dead_air.LOGGER.warn("No Radio Panel found within {} blocks of {}", PANEL_SEARCH_RADIUS, spawnPos);
    }

    public static void clearPendingScans(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        PENDING_SCANS.remove(dimension);
    }

    public static void clearAllPendingScans() {
        PENDING_SCANS.clear();
    }

    // ---------- Discovery ----------

    private static void triggerStationDiscovery(ServerLevel level, BlockPos towerPos, RadioStation station) {
        Vec3 towerVec = Vec3.atCenterOf(towerPos);
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) continue;
            if (player.position().distanceTo(towerVec) > station.getBroadcastRange()) continue;
            if (StationUnlockManager.hasUnlocked(player, station)) continue;
            StationUnlockManager.unlockStation(player, station);
            player.sendSystemMessage(Component.literal("§6[Radio] §rDiscovered new station: §e" + station.getName() + " §7(" + String.format("%.1f", station.getFrequency()) + " MHz)"));
        }
    }
}
