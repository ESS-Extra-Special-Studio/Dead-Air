package uk.co.extraspecialstudio.dead_air.commands;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType;
import uk.co.extraspecialstudio.dead_air.events.ChunkEvents;
import uk.co.extraspecialstudio.dead_air.events.ModEvents;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage;
import uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager;

import java.util.ArrayList;
import java.util.List;
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
 * For placement we use the structure TEMPLATE IDs (from template_pool "location") — these worked with the
 * original RadioTowers mod. In radiotowers JAR: data/radiotowers/structures/*.nbt and template_pool "location":
 *   tower.nbt -> radiotowers:tower,  tower_2.nbt -> radiotowers:tower_2,  tower_overrun.nbt -> radiotowers:tower_overrun.
 * We also try worldgen-style fallbacks (radiotower, radiotower_2, radiotoweroverrun) in case a different
 * RadioTowers build ships the .nbt under those paths.
 *
 * Placement strips structure blocks and replaces wet-sponge placeholders with grass
 * (worldgen pools use {@code block_ignore} on sponge, which leaves holes when we place the
 * raw template on flat ground via this command).
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
    /** Fallback IDs (worldgen / MCreator build may ship .nbt under these paths). */
    private static final ResourceLocation FALLBACK_STANDARD   = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "radiotower");
    private static final ResourceLocation FALLBACK_FENCED     = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "radiotower_2");
    private static final ResourceLocation FALLBACK_OVERRUN    = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "radiotoweroverrun");

    private static final int SCAN_DELAY_TICKS = 5;
    private static final int PANEL_SEARCH_RADIUS = 25;
    /** Max blocks to scan down from heightmap to find solid ground (avoid tree tops). */
    private static final int GROUND_SEARCH_DEPTH = 40;
    /** Blocks in front of the player to place the tower when no position is given. */
    private static final int PLACE_DISTANCE_IN_FRONT = 5;

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
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.translatable("command.dead_air.spawntower.error.permission"));
            return 0;
        }
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.translatable("command.dead_air.spawntower.error.player_only"));
            return 0;
        }

        ResourceLocation structureId = structureIdForType(typeArg);
        if (structureId == null) {
            source.sendFailure(Component.translatable("command.dead_air.spawntower.error.invalid_type"));
            return 0;
        }

        BlockPos origin;
        if (targetPos != null) {
            int x = targetPos.getX();
            int z = targetPos.getZ();
            int groundY = findGroundY(serverLevel, x, z);
            origin = new BlockPos(x, groundY, z);
        } else {
            // Place directly in front of the player, on solid ground (not on tree tops)
            Direction horizontalFacing = player.getDirection();
            BlockPos inFront = player.blockPosition().relative(horizontalFacing, PLACE_DISTANCE_IN_FRONT);
            int x = inFront.getX();
            int z = inFront.getZ();
            int groundY = findGroundY(serverLevel, x, z);
            origin = new BlockPos(x, groundY, z);
        }

        var templateManager = serverLevel.getStructureManager();
        java.util.Optional<StructureTemplate> opt = getTemplate(templateManager, structureId, typeArg);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.dead_air.spawntower.error.structure_not_found", structureId.toString()));
            Dead_air.LOGGER.warn("Structure not found: {}", structureId);
            return 0;
        }

        StructureTemplate template = opt.get();
        // Structure blocks: ignore. Wet sponge: fill as grass (placeholders act as foundation in the NBT).
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false)
            .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_BLOCK)))
            .addProcessor(new RuleProcessor(List.of(
                new ProcessorRule(
                    new BlockMatchTest(Blocks.WET_SPONGE),
                    AlwaysTrueTest.INSTANCE,
                    Blocks.GRASS_BLOCK.defaultBlockState()
                ),
                new ProcessorRule(
                    new BlockMatchTest(Blocks.SPONGE),
                    AlwaysTrueTest.INSTANCE,
                    Blocks.GRASS_BLOCK.defaultBlockState()
                )
            )));

        try {
            template.placeInWorld(serverLevel, origin, origin, settings, serverLevel.getRandom(), Block.UPDATE_CLIENTS);
        } catch (Exception e) {
            Dead_air.LOGGER.error("Failed to place structure at {}", origin, e);
            source.sendFailure(Component.translatable("command.dead_air.spawntower.error.place_failed", e.getMessage()));
            return 0;
        }

        ApocalypseTowerType towerType = towerTypeFromString(typeArg);
        BlockPos panelOffset = getPanelOffsetForType(typeArg);
        BlockPos panelPos = panelOffset != null ? origin.offset(panelOffset) : null;

        if (panelPos != null && ApocalypseTowerDetector.isRadioPanel(serverLevel, panelPos) && !TowerManager.isTowerRegistered(serverLevel, panelPos)) {
            RadioStation station = TowerManager.determineStationForTower(serverLevel, panelPos, towerType);
            if (station != null) {
                TowerManager.registerTower(serverLevel, panelPos, station, towerType);
                activateSpawnedTowerPanel(serverLevel, panelPos);
                triggerStationDiscovery(serverLevel, panelPos, station);
                source.sendSuccess(() -> Component.translatable("command.dead_air.spawntower.success", typeArg, origin.toShortString()), true);
                return 1;
            }
        }

        // Immediate scan so spawned standard/fenced/overrun towers are active without right-click (panel offset unknown, so search around origin)
        if (scanForPanelAndRegister(serverLevel, origin, typeArg)) {
            source.sendSuccess(() -> Component.translatable("command.dead_air.spawntower.success", typeArg, origin.toShortString()), true);
            return 1;
        }
        schedulePostPlaceScan(serverLevel, origin, typeArg);
        source.sendSuccess(() -> Component.translatable("command.dead_air.spawntower.success", typeArg, origin.toShortString()), true);
        return 1;
    }

    // ---------- Structure ID and type ----------

    /** Tries primary structure ID then fallback IDs (MCreator may ship templates under worldgen-style names). */
    private static java.util.Optional<StructureTemplate> getTemplate(StructureTemplateManager manager, ResourceLocation primaryId, String typeArg) {
        var opt = manager.get(primaryId);
        if (opt.isPresent()) return opt;
        ResourceLocation fallback = switch (typeArg) {
            case "standard" -> FALLBACK_STANDARD;
            case "fenced"   -> FALLBACK_FENCED;
            case "overrun"  -> FALLBACK_OVERRUN;
            default -> null;
        };
        if (fallback != null) opt = manager.get(fallback);
        return opt;
    }

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

    /** Offset from structure origin to the Radio Panel block; null = scan for panel. */
    private static BlockPos getPanelOffsetForType(String type) {
        return null;
    }

    /**
     * Finds the Y of the solid ground at (x, z) by scanning down from the heightmap.
     * Avoids placing towers on tree tops, leaves, or logs; returns the first "ground" block Y.
     * Only dirt, grass, stone, sand, etc. count as ground — logs and leaves are skipped.
     */
    private static int findGroundY(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        int startY = top.getY();
        int minY = level.getMinBuildHeight();
        int limit = Math.min(GROUND_SEARCH_DEPTH, startY - minY);
        for (int d = 0; d <= limit; d++) {
            int y = startY - d;
            if (y < minY) break;
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (isSolidGround(state)) return y;
        }
        // Fallback if no solid ground found in range (e.g. all air) — use heightmap Y
        return startY;
    }

    /** True if the block is typical solid ground (dirt, grass, stone, sand, etc.), not logs/leaves. */
    private static boolean isSolidGround(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();
        return block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.PODZOL
            || block == Blocks.MYCELIUM || block == Blocks.STONE || block == Blocks.SAND
            || block == Blocks.SANDSTONE || block == Blocks.RED_SAND || block == Blocks.GRAVEL
            || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT || block == Blocks.MUD
            || block == Blocks.MUDDY_MANGROVE_ROOTS || block == Blocks.CLAY
            || block == Blocks.SNOW_BLOCK || block == Blocks.ICE || block == Blocks.PACKED_ICE
            || block == Blocks.NETHERRACK || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL
            || block == Blocks.BASALT || block == Blocks.BLACKSTONE || block == Blocks.TERRACOTTA;
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

    /** Scan for a Radio Panel near spawnPos, register tower and activate panel. Returns true if a panel was found and registered. */
    private static boolean scanForPanelAndRegister(ServerLevel level, BlockPos spawnPos, String towerTypeStr) {
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
                    activateSpawnedTowerPanel(level, panelPos);
                    triggerStationDiscovery(level, panelPos, station);
                    return true;
                }
            }
        }
        Dead_air.LOGGER.warn("No Radio Panel found within {} blocks of {}", PANEL_SEARCH_RADIUS, spawnPos);
        return false;
    }

    public static void clearPendingScans(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        PENDING_SCANS.remove(dimension);
    }

    public static void clearAllPendingScans() {
        PENDING_SCANS.clear();
    }

    /** Make a spawned tower's panel active and add to known towers so it is immediately tunable without right-clicking. */
    private static void activateSpawnedTowerPanel(ServerLevel level, BlockPos panelPos) {
        ChunkEvents.ensureOverworldReadyForSignal(level);
        RadioPanelManager.activatePanel(level, panelPos);
        RadioTower tower = TowerManager.getTowerAt(level, panelPos);
        if (tower != null) {
            BlockPos towerPos = tower.getPosition();
            BlockPos panel = tower.getRadioPanelPos() != null ? tower.getRadioPanelPos() : panelPos;
            KnownTowerStorage.addKnownTower(level, towerPos, panel, tower.getStation().getId());
            var server = level.getServer();
            if (server != null) {
                for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.level() == level) ModEvents.syncKnownTowersToPlayer(player);
                }
            }
        }
    }

    // ---------- Discovery ----------

    private static void triggerStationDiscovery(ServerLevel level, BlockPos towerPos, RadioStation station) {
        if (station.getId().equals(StationRegistry.JUKEBOX_FM_ID)) {
            return;
        }
        Vec3 towerVec = Vec3.atCenterOf(towerPos);
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) continue;
            if (player.position().distanceTo(towerVec) > station.getBroadcastRange()) continue;
            if (StationUnlockManager.hasUnlocked(player, station)) continue;
            StationUnlockManager.unlockStation(player, station);
            player.sendSystemMessage(Component.literal("§6[Radio] §rNew station discovered!"));
        }
    }
}
