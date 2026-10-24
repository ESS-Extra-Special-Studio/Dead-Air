package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorSpawner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Broken relays and survivor camps promised by a broadcast. Each is recorded at a rough X/Z first and only
 * built once a player is close and its chunks are loaded, so nothing is generated in unloaded terrain.
 */
@SuppressWarnings("null")
public class PendingSites extends SavedData {
    private static final String DATA_NAME = Dead_air.MODID + "_pending_sites";
    public static final String KIND_RELAY = "relay";
    public static final String KIND_CAMP = "camp";
    public static final String KIND_AUCTION = "auction";
    public static final String KIND_CONVOY = "convoy";
    public static final String KIND_CRASH = "crash";
    public static final String KIND_MISSING = "missing";
    private static final int PLACE_RADIUS = 96;
    private static final int PLAYER_BLOCK_CLEARANCE = 48;
    private static final int FLAT_SAMPLE_RADIUS = 4;
    private static final int MAX_FLAT_DELTA = 3;
    private static final int MAX_CREATE_ATTEMPTS = 40;
    private static final int MAX_DRIFT_ATTEMPTS = 8;

    public static final class Site {
        public UUID owner;
        public UUID siteId;
        public String kind;
        public String template;
        public BlockPos center;
        public boolean placed;
        public BlockPos keyPos;
        public int shifts;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", owner);
            if (siteId != null) t.putUUID("siteId", siteId);
            t.putString("kind", kind);
            t.putString("template", template);
            t.putLong("center", center.asLong());
            t.putBoolean("placed", placed);
            if (keyPos != null) t.putLong("key", keyPos.asLong());
            t.putInt("shifts", shifts);
            return t;
        }

        static Site load(CompoundTag t) {
            Site s = new Site();
            s.owner = t.getUUID("owner");
            s.siteId = t.hasUUID("siteId") ? t.getUUID("siteId") : UUID.randomUUID();
            s.kind = t.getString("kind");
            s.template = t.getString("template");
            s.center = BlockPos.of(t.getLong("center"));
            s.placed = t.getBoolean("placed");
            if (t.contains("key")) s.keyPos = BlockPos.of(t.getLong("key"));
            s.shifts = t.getInt("shifts");
            return s;
        }
    }

    private final List<Site> sites = new ArrayList<>();

    public static PendingSites load(CompoundTag nbt, HolderLookup.Provider provider) {
        PendingSites s = new PendingSites();
        ListTag list = nbt.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) s.sites.add(Site.load(list.getCompound(i)));
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Site s : sites) list.add(s.save());
        nbt.put("sites", list);
        return nbt;
    }

    public List<Site> getSites() {
        return sites;
    }

    public static PendingSites get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PendingSites::new, PendingSites::load), DATA_NAME);
    }

    /** Record a new site 'min'..'max' blocks from the player on dry, flat land. Returns the rough centre. */
    public static Site create(ServerPlayer player, String kind, String template, int min, int max) {
        ServerLevel level = player.server.overworld();
        PendingSites data = get(player.server);
        if (data == null) return null;
        RandomSource random = player.getRandom();
        BlockPos origin = player.level() == level ? player.blockPosition() : level.getSharedSpawnPos();
        BlockPos chosen = null;
        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS && chosen == null; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = min + random.nextInt(Math.max(1, max - min));
            BlockPos candidate = new BlockPos(origin.getX() + (int) (Math.cos(angle) * dist), 64,
                origin.getZ() + (int) (Math.sin(angle) * dist));
            if (isDryLand(level, candidate) && isValidBuildSite(level, candidate.getX(), candidate.getZ())) {
                chosen = candidate;
            }
        }
        if (chosen == null) {
            chosen = findNearbyValid(level, origin.getX(), origin.getZ(), min, max, random);
        }
        if (chosen == null) {
            chosen = new BlockPos(origin.getX() + min, 64, origin.getZ());
        }
        Site site = new Site();
        site.owner = player.getUUID();
        site.siteId = UUID.randomUUID();
        site.kind = kind;
        site.template = template;
        site.center = chosen;
        data.sites.add(site);
        data.setDirty();
        int y = surfaceY(level, chosen.getX(), chosen.getZ());
        markSiteWaypoint(player, kind, new BlockPos(chosen.getX(), y, chosen.getZ()));
        return site;
    }

    /** Place a site immediately next to the player (debug). */
    public static Site createHere(ServerPlayer player, String kind, String template) {
        PendingSites data = get(player.server);
        if (data == null) return null;
        Site site = new Site();
        site.owner = player.getUUID();
        site.siteId = UUID.randomUUID();
        site.kind = kind;
        site.template = template;
        BlockPos p = player.blockPosition().relative(player.getDirection(), 12);
        ServerLevel level = player.server.overworld();
        BlockPos valid = findNearbyValid(level, p.getX(), p.getZ(), 0, 24, player.getRandom());
        site.center = valid != null ? valid : new BlockPos(p.getX(), 64, p.getZ());
        data.sites.add(site);
        data.setDirty();
        int y = surfaceY(level, site.center.getX(), site.center.getZ());
        markSiteWaypoint(player, kind, new BlockPos(site.center.getX(), y, site.center.getZ()));
        tryPlace(level, data, site, true);
        return site;
    }

    private static void markSiteWaypoint(ServerPlayer player, String kind, BlockPos marker) {
        try {
            String label = switch (kind) {
                case KIND_RELAY -> "DA Relay";
                case KIND_CAMP -> "DA Camp";
                case KIND_CONVOY -> "DA Convoy";
                case KIND_AUCTION -> "DA Auction";
                case KIND_CRASH -> "DA Crash";
                case KIND_MISSING -> "DA Missing";
                default -> "DA Signal";
            };
            uk.co.extraspecialstudio.dead_air.compat.MapWaypointCompat.markEbSite(player, marker, label);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshWaypoint(ServerLevel level, Site site) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner);
        if (owner == null) return;
        BlockPos at = site.keyPos != null ? site.keyPos
            : new BlockPos(site.center.getX(), surfaceY(level, site.center.getX(), site.center.getZ()), site.center.getZ());
        markSiteWaypoint(owner, site.kind, at);
    }

    private static boolean isDryLand(ServerLevel level, BlockPos pos) {
        try {
            Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(64), QuartPos.fromBlock(pos.getZ()),
                level.getChunkSource().randomState().sampler());
            return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_DEEP_OCEAN);
        } catch (Exception e) {
            return true;
        }
    }

    /** Flat-ish dry ground: no fluid columns, no steep ravine drops in a small sample radius. */
    public static boolean isValidBuildSite(ServerLevel level, int x, int z) {
        if (!isDryLand(level, new BlockPos(x, 64, z))) return false;
        if (!level.hasChunk(x >> 4, z >> 4)) {
            // Unloaded: accept dry-biome candidates at create time; re-check on place.
            return true;
        }
        int centerY = surfaceY(level, x, z);
        BlockPos ground = new BlockPos(x, centerY - 1, z);
        if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) return false;
        BlockState below = level.getBlockState(ground);
        if (below.isAir() || below.canBeReplaced()) return false;

        int minY = centerY;
        int maxY = centerY;
        for (int dx = -FLAT_SAMPLE_RADIUS; dx <= FLAT_SAMPLE_RADIUS; dx += FLAT_SAMPLE_RADIUS) {
            for (int dz = -FLAT_SAMPLE_RADIUS; dz <= FLAT_SAMPLE_RADIUS; dz += FLAT_SAMPLE_RADIUS) {
                if (dx == 0 && dz == 0) continue;
                int sx = x + dx;
                int sz = z + dz;
                if (!level.hasChunk(sx >> 4, sz >> 4)) continue;
                int sy = surfaceY(level, sx, sz);
                minY = Math.min(minY, sy);
                maxY = Math.max(maxY, sy);
                BlockPos sample = new BlockPos(sx, sy - 1, sz);
                if (!level.getFluidState(sample).isEmpty()) return false;
            }
        }
        return (maxY - minY) <= MAX_FLAT_DELTA;
    }

    private static int surfaceY(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return 64;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static BlockPos findNearbyValid(ServerLevel level, int ox, int oz, int min, int max, RandomSource random) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = Math.max(0, min) + (max > min ? random.nextInt(Math.max(1, max - min)) : random.nextInt(17));
            int x = ox + (int) (Math.cos(angle) * dist);
            int z = oz + (int) (Math.sin(angle) * dist);
            if (isValidBuildSite(level, x, z)) return new BlockPos(x, 64, z);
        }
        return null;
    }

    private static boolean driftToValid(ServerLevel level, Site site) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            int ox = site.center.getX() + (random.nextBoolean() ? 1 : -1) * (32 + random.nextInt(48));
            int oz = site.center.getZ() + (random.nextBoolean() ? 1 : -1) * (32 + random.nextInt(48));
            if (isValidBuildSite(level, ox, oz) && !nearPlayerBlocks(level, new BlockPos(ox, 64, oz))) {
                site.center = new BlockPos(ox, 64, oz);
                site.shifts++;
                return true;
            }
        }
        // Fallback compass offsets
        int[][] offsets = {{64, 0}, {-64, 0}, {0, 64}, {0, -64}, {48, 48}, {-48, 48}, {48, -48}, {-48, -48}};
        for (int[] o : offsets) {
            int x = site.center.getX() + o[0];
            int z = site.center.getZ() + o[1];
            if (isValidBuildSite(level, x, z)) {
                site.center = new BlockPos(x, 64, z);
                site.shifts++;
                return true;
            }
        }
        site.center = site.center.offset(64, 0, 0);
        site.shifts++;
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 20) return;
        PendingSites data = get(server);
        if (data == null || data.sites.isEmpty()) return;
        ServerLevel level = server.overworld();
        for (Site site : new ArrayList<>(data.sites)) {
            if (site.placed) continue;
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - site.center.getX();
                double dz = p.getZ() - site.center.getZ();
                if (dx * dx + dz * dz < PLACE_RADIUS * PLACE_RADIUS) {
                    near = true;
                    break;
                }
            }
            if (near) tryPlace(level, data, site, false);
        }
    }

    private static boolean chunksLoaded(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x += 16) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 16) {
                if (!level.hasChunk(x >> 4, z >> 4)) return false;
            }
        }
        return true;
    }

    private static boolean nearPlayerBlocks(ServerLevel level, BlockPos center) {
        int r = PLAYER_BLOCK_CLEARANCE;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockPos be : chunk.getBlockEntities().keySet()) {
                    if (be.distSqr(new BlockPos(center.getX(), be.getY(), center.getZ())) < r * r) {
                        BlockState s = chunk.getBlockState(be);
                        if (s.is(DeadAirBlocks.BROKEN_RELAY.get())) continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void tryPlace(ServerLevel level, PendingSites data, Site site, boolean force) {
        if (!force && !chunksLoaded(level, site.center, 24)) return;

        boolean needsDrift = !force && site.shifts < MAX_DRIFT_ATTEMPTS
            && (nearPlayerBlocks(level, site.center)
            || isWaterAt(level, site.center)
            || !isValidBuildSite(level, site.center.getX(), site.center.getZ()));
        if (needsDrift) {
            driftToValid(level, site);
            data.setDirty();
            notifyDrift(level, site);
            refreshWaypoint(level, site);
            return;
        }

        boolean drifted = site.shifts > 0 && !site.placed;
        if (KIND_CRASH.equals(site.kind) || "procedural_crash".equals(site.template)) {
            if (!placeCrashSite(level, data, site)) {
                Dead_air.LOGGER.warn("[Dead Air] Crash place failed at {}; leaving unplaced", site.center);
                return;
            }
            refreshWaypoint(level, site);
            if (drifted) notifyLocked(level, site);
            return;
        }
        if (KIND_MISSING.equals(site.kind)) {
            int groundY = surfaceY(level, site.center.getX(), site.center.getZ());
            site.keyPos = new BlockPos(site.center.getX(), groundY, site.center.getZ());
            site.placed = true;
            data.setDirty();
            refreshWaypoint(level, site);
            if (drifted) notifyLocked(level, site);
            return;
        }

        // Prefer procedural kits for camp / auction / convoy / relay (distinct silhouettes).
        if (usesProceduralKit(site)) {
            BlockPos key = EventSitePlacer.place(level, site);
            if (key == null) {
                Dead_air.LOGGER.warn("[Dead Air] Procedural {} place failed at {}", site.kind, site.center);
                return;
            }
            site.keyPos = key;
            site.placed = true;
            data.setDirty();
            Dead_air.LOGGER.info("[Dead Air] Placed procedural {} at {}", site.kind, site.keyPos);
            refreshWaypoint(level, site);
            if (drifted) notifyLocked(level, site);
            afterPlaceSpawns(level, site);
            return;
        }

        Optional<StructureTemplate> opt = level.getStructureManager().get(
            ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, site.template));
        if (opt.isEmpty()) {
            // Fallback to procedural if NBT missing.
            BlockPos key = EventSitePlacer.place(level, site);
            if (key == null) {
                Dead_air.LOGGER.error("[Dead Air] Missing structure template {} and procedural failed", site.template);
                return;
            }
            site.keyPos = key;
            site.placed = true;
            data.setDirty();
            refreshWaypoint(level, site);
            if (drifted) notifyLocked(level, site);
            afterPlaceSpawns(level, site);
            return;
        }
        StructureTemplate template = opt.get();
        RandomSource random = level.getRandom();
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(Rotation.getRandom(random))
            .setIgnoreEntities(true);
        int sx = template.getSize().getX();
        int sz = template.getSize().getZ();
        int groundY = surfaceY(level, site.center.getX(), site.center.getZ()) - 1;
        BlockPos corner = new BlockPos(site.center.getX() - sx / 2, groundY, site.center.getZ() - sz / 2);
        BoundingBox box = template.getBoundingBox(settings, corner);
        BlockPos shift = new BlockPos(site.center.getX() - box.getCenter().getX(), 0, site.center.getZ() - box.getCenter().getZ());
        corner = corner.offset(shift);
        box = template.getBoundingBox(settings, corner);

        fillFoundation(level, box);
        template.placeInWorld(level, corner, corner, settings, random, Block.UPDATE_CLIENTS);
        site.placed = true;
        site.keyPos = findKeyPos(level, box, site.kind);
        data.setDirty();
        Dead_air.LOGGER.info("[Dead Air] Placed {} site {} at {}", site.kind, site.template, site.keyPos);
        refreshWaypoint(level, site);
        if (drifted) notifyLocked(level, site);
        afterPlaceSpawns(level, site);
    }

    private static boolean usesProceduralKit(Site site) {
        if (site.template == null) return true;
        return site.template.contains("camp_v2")
            || site.template.contains("auction")
            || site.template.contains("convoy")
            || site.template.startsWith("broken_relay/")
            || KIND_CAMP.equals(site.kind)
            || KIND_AUCTION.equals(site.kind)
            || KIND_CONVOY.equals(site.kind)
            || KIND_RELAY.equals(site.kind);
    }

    private static void afterPlaceSpawns(ServerLevel level, Site site) {
        if (KIND_RELAY.equals(site.kind)) {
            BroadcastProgress progress = BroadcastProgress.get(level.getServer());
            if (progress != null && site.keyPos != null) {
                BroadcastProgress.PlayerProgress p = progress.get(site.owner);
                if (p.targetRelay != null && horizontalDist(p.targetRelay, site.center) < 400) {
                    p.targetRelay = site.keyPos;
                    progress.setDirty();
                }
            }
        } else if (KIND_CAMP.equals(site.kind) && site.keyPos != null) {
            SurvivorSpawner.spawnStranded(level, site.keyPos, site.owner);
        } else if (KIND_CONVOY.equals(site.kind) && site.keyPos != null) {
            int count = 2 + level.getRandom().nextInt(2);
            for (int i = 0; i < count; i++) {
                BlockPos at = site.keyPos.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
                SurvivorSpawner.spawnStranded(level, at, site.owner);
            }
        } else if (KIND_AUCTION.equals(site.kind) && site.keyPos != null) {
            uk.co.extraspecialstudio.dead_air.survivor.AuctionTraderSpawner.spawn(level, site.keyPos, site.owner);
        }
    }

    /** @return true if a visible wreck + chest were placed */
    private static boolean placeCrashSite(ServerLevel level, PendingSites data, Site site) {
        int groundY = surfaceY(level, site.center.getX(), site.center.getZ());
        BlockPos center = new BlockPos(site.center.getX(), groundY, site.center.getZ());
        boolean plane = uk.co.extraspecialstudio.dead_air.compat.RadioTowersPlaneCompat
            .trySpawnGroundedWreck(level, center);
        if (!plane) {
            uk.co.extraspecialstudio.dead_air.broadcast.CrashSitePlacer.placeDebrisWreck(level, center);
        } else {
            uk.co.extraspecialstudio.dead_air.broadcast.CrashSitePlacer.placeLootAndScars(level, center);
        }
        // Always ensure a visible footprint even if RT plane soft-failed silently.
        if (!CrashSitePlacer.hasVisibleWreck(level, center)) {
            CrashSitePlacer.placeDebrisWreck(level, center);
        }
        if (!CrashSitePlacer.hasVisibleWreck(level, center)) {
            return false;
        }
        CrashSitePlacer.spawnLightHostiles(level, center);
        try {
            uk.co.extraspecialstudio.dead_air.compat.VehicleCompat.decorateCrashSite(level, center);
            uk.co.extraspecialstudio.dead_air.compat.AircraftCompat.decorateCrashSite(level, center, plane);
        } catch (Throwable ignored) {
        }
        site.keyPos = center;
        site.placed = true;
        data.setDirty();
        Dead_air.LOGGER.info("[Dead Air] Placed crash site at {} (rtPlane={})", center, plane);
        return true;
    }

    private static boolean isWaterAt(ServerLevel level, BlockPos center) {
        if (!level.hasChunk(center.getX() >> 4, center.getZ() >> 4)) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ()) - 1;
        return !level.getFluidState(new BlockPos(center.getX(), y, center.getZ())).isEmpty();
    }

    private static void notifyDrift(ServerLevel level, Site site) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner);
        if (owner == null) return;
        int x = site.center.getX();
        int z = site.center.getZ();
        if (site.shifts == 1) {
            owner.sendSystemMessage(Component.translatable("message.dead_air.site.drifted", x, z));
        } else {
            owner.displayClientMessage(Component.translatable("message.dead_air.site.drifted_update", x, z), true);
        }
    }

    private static void notifyLocked(ServerLevel level, Site site) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner);
        if (owner == null) return;
        BlockPos at = site.keyPos != null ? site.keyPos : site.center;
        owner.sendSystemMessage(Component.translatable("message.dead_air.site.locked", at.getX(), at.getZ()));
    }

    private static double horizontalDist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void fillFoundation(ServerLevel level, BoundingBox box) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY() - 1, n = 0; n < 16; y--, n++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty()) break;
                    level.setBlock(p, n < 2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static BlockPos findKeyPos(ServerLevel level, BoundingBox box, String kind) {
        BlockPos campfire = null;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (KIND_RELAY.equals(kind) && s.is(DeadAirBlocks.BROKEN_RELAY.get())) return p.immutable();
            if ((KIND_CAMP.equals(kind) || KIND_AUCTION.equals(kind) || KIND_CONVOY.equals(kind))
                && (s.is(Blocks.CAMPFIRE) || s.is(DeadAirBlocks.SIGNAL_FIREPIT.get()))) {
                campfire = p.immutable();
                break;
            }
        }
        if (campfire != null) {
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int d = 2; d <= 3; d++) {
                    BlockPos c = campfire.relative(dir, d);
                    if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir()) return c;
                }
            }
            return campfire.above();
        }
        boolean campLike = KIND_CAMP.equals(kind) || KIND_AUCTION.equals(kind) || KIND_CONVOY.equals(kind);
        return campLike ? new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ()) : null;
    }

    public static boolean hasUnplaced(MinecraftServer server, UUID owner, String kind) {
        for (Site s : forOwner(server, owner)) {
            if (kind.equals(s.kind) && !s.placed) return true;
        }
        return false;
    }

    public static List<Site> forOwner(MinecraftServer server, UUID owner) {
        List<Site> out = new ArrayList<>();
        PendingSites data = get(server);
        if (data == null) return out;
        for (Site s : data.sites) if (s.owner.equals(owner)) out.add(s);
        return out;
    }

    public static ChunkPos chunkOf(Site site) {
        return new ChunkPos(site.center);
    }
}
