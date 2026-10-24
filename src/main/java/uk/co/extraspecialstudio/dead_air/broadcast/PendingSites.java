package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorSpawner;

import javax.annotation.Nullable;
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
        /** Stable id — survives center drift so hunt / proximity progress keys stay valid. */
        public UUID siteId;
        public UUID owner;
        public String kind;
        public String template;
        public BlockPos center;
        public boolean placed;
        public BlockPos keyPos;
        public int shifts;
        /** Overworld day the site was promised; -1 = legacy / unknown. */
        public long createdDay = -1;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            if (siteId != null) t.putUUID("siteId", siteId);
            t.putUUID("owner", owner);
            t.putString("kind", kind);
            t.putString("template", template);
            t.putLong("center", center.asLong());
            t.putBoolean("placed", placed);
            if (keyPos != null) t.putLong("key", keyPos.asLong());
            t.putInt("shifts", shifts);
            t.putLong("createdDay", createdDay);
            return t;
        }

        /** @return null if the tag is corrupt (missing owner). */
        @Nullable
        static Site load(CompoundTag t) {
            if (t == null || !t.hasUUID("owner")) return null;
            Site s = new Site();
            s.siteId = t.hasUUID("siteId") ? t.getUUID("siteId") : UUID.randomUUID();
            s.owner = t.getUUID("owner");
            s.kind = t.getString("kind");
            s.template = t.getString("template");
            s.center = BlockPos.of(t.getLong("center"));
            s.placed = t.getBoolean("placed");
            if (t.contains("key")) s.keyPos = BlockPos.of(t.getLong("key"));
            s.shifts = t.getInt("shifts");
            s.createdDay = t.contains("createdDay") ? t.getLong("createdDay") : -1;
            return s;
        }
    }

    private final List<Site> sites = new ArrayList<>();

    public static PendingSites load(CompoundTag nbt) {
        PendingSites s = new PendingSites();
        ListTag list = nbt.getList("sites", Tag.TAG_COMPOUND);
        boolean migratedIds = false;
        boolean droppedCorrupt = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("siteId")) migratedIds = true;
            Site site = Site.load(tag);
            if (site == null) {
                droppedCorrupt = true;
                continue;
            }
            s.sites.add(site);
        }
        if (migratedIds || droppedCorrupt) s.setDirty();
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
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
        return overworld.getDataStorage().computeIfAbsent(PendingSites::load, PendingSites::new, DATA_NAME);
    }

    /** Record a new site 'min'..'max' blocks from the player on dry, flat land. Returns the rough centre. */
    public static Site create(ServerPlayer player, String kind, String template, int min, int max) {
        ServerLevel level = player.server.overworld();
        PendingSites data = get(player.server);
        if (data == null) return null;
        RandomSource random = player.getRandom();
        BlockPos origin = player.level() == level ? player.blockPosition() : level.getSharedSpawnPos();
        BlockPos chosen = null;

        // Refugee convoys prefer an already-paved AWE road corridor when one exists in range.
        if (KIND_CONVOY.equals(kind)) {
            for (int attempt = 0; attempt < 8 && chosen == null; attempt++) {
                BlockPos onRoad = uk.co.extraspecialstudio.dead_air.compat.AweRoadsCompat
                    .pickConvoyOnRoad(level, origin.getX(), origin.getZ(), min, max, random);
                if (onRoad == null) break;
                Site probe = new Site();
                probe.kind = KIND_CONVOY;
                if (isDryLand(level, onRoad)
                    && isValidBuildSite(level, onRoad.getX(), onRoad.getZ(), DEFAULT_CLEAR_RADIUS, probe)) {
                    chosen = onRoad;
                }
            }
        }

        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS && chosen == null; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = min + random.nextInt(Math.max(1, max - min + 1));
            BlockPos candidate = new BlockPos(origin.getX() + (int) (Math.cos(angle) * dist), 64,
                origin.getZ() + (int) (Math.sin(angle) * dist));
            Site probe = new Site();
            probe.kind = kind;
            if (isDryLand(level, candidate)
                && isValidBuildSite(level, candidate.getX(), candidate.getZ(), DEFAULT_CLEAR_RADIUS, probe)) {
                chosen = candidate;
            }
        }
        if (chosen == null) {
            chosen = findNearbyValid(level, origin.getX(), origin.getZ(), min, max, random, kind);
        }
        if (chosen == null) {
            chosen = new BlockPos(origin.getX() + min, 64, origin.getZ());
        }
        Site site = new Site();
        site.siteId = UUID.randomUUID();
        site.owner = player.getUUID();
        site.kind = kind;
        site.template = template;
        site.center = chosen;
        site.createdDay = level.getDayTime() / 24000L;
        data.sites.add(site);
        data.setDirty();
        int y = surfaceY(level, chosen.getX(), chosen.getZ());
        markSiteWaypoint(player, site, new BlockPos(chosen.getX(), y, chosen.getZ()));
        notifySitePin(player, kind, chosen.getX(), chosen.getZ());
        return site;
    }

    private static void notifySitePin(ServerPlayer player, String kind, int x, int z) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
            "message.dead_air.site.pin", kind, x, z)
            .withStyle(net.minecraft.ChatFormatting.AQUA));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
            "message.dead_air.broadcast.tune_hint")
            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
    }

    /** Place a site immediately next to the player (debug). */
    public static Site createHere(ServerPlayer player, String kind, String template) {
        PendingSites data = get(player.server);
        if (data == null) return null;
        Site site = new Site();
        site.siteId = UUID.randomUUID();
        site.owner = player.getUUID();
        site.kind = kind;
        site.template = template;
        BlockPos p = player.blockPosition().relative(player.getDirection(), 12);
        ServerLevel level = player.server.overworld();
        BlockPos valid = findNearbyValid(level, p.getX(), p.getZ(), 0, 24, player.getRandom(), kind);
        site.center = valid != null ? valid : new BlockPos(p.getX(), 64, p.getZ());
        site.createdDay = level.getDayTime() / 24000L;
        data.sites.add(site);
        data.setDirty();
        int y = surfaceY(level, site.center.getX(), site.center.getZ());
        markSiteWaypoint(player, site, new BlockPos(site.center.getX(), y, site.center.getZ()));
        tryPlace(level, data, site, true);
        return site;
    }

    /** Player-facing map pin label — keep create / place / proximity identical. */
    public static String waypointLabel(String kind) {
        if (kind == null) return "Dead Air Signal";
        return switch (kind) {
            case KIND_RELAY -> "Broken Relay";
            case KIND_CAMP -> "Civilian Camp";
            case KIND_CONVOY -> "Refugee Convoy";
            case KIND_AUCTION -> "Dead-Channel Auction";
            case KIND_CRASH -> "Crash Site";
            case KIND_MISSING -> "Missing Recruit";
            default -> "Dead Air Signal";
        };
    }

    public static String waypointStableId(Site site) {
        if (site == null || site.siteId == null) return "da_unknown";
        return "eb_" + site.siteId;
    }

    private static void markSiteWaypoint(ServerPlayer player, Site site, BlockPos marker) {
        try {
            uk.co.extraspecialstudio.dead_air.compat.MapWaypointCompat.markEbSite(
                player, marker, waypointLabel(site.kind), waypointStableId(site));
        } catch (Throwable ignored) {
        }
    }

    private static void refreshWaypoint(ServerLevel level, Site site) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner);
        if (owner == null) return;
        BlockPos at = site.keyPos != null ? site.keyPos
            : new BlockPos(site.center.getX(), surfaceY(level, site.center.getX(), site.center.getZ()), site.center.getZ());
        markSiteWaypoint(owner, site, at);
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

    /** Default clear footprint used for claim/structure sampling when kind unknown. */
    public static final int DEFAULT_CLEAR_RADIUS = 8;

    /** Flat-ish dry ground + claim/structure/density gates. Unloaded chunks skip structure query. */
    public static boolean isValidBuildSite(ServerLevel level, int x, int z) {
        return isValidBuildSite(level, x, z, DEFAULT_CLEAR_RADIUS, null);
    }

    public static boolean isValidBuildSite(ServerLevel level, int x, int z, int clearRadius,
                                           Site exclude) {
        if (!isDryLand(level, new BlockPos(x, 64, z))) return false;
        BlockPos center = new BlockPos(x, 64, z);
        if (!SitePlacementRules.allows(level, center, clearRadius, exclude)) return false;
        if (!level.hasChunk(x >> 4, z >> 4)) {
            // Unloaded: dry + density/claim soft checks only; re-check terrain on place.
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

    private static BlockPos findNearbyValid(ServerLevel level, int ox, int oz, int min, int max,
                                            RandomSource random, String kind) {
        Site probe = null;
        if (kind != null) {
            probe = new Site();
            probe.kind = kind;
        }
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = Math.max(0, min) + (max > min ? random.nextInt(Math.max(1, max - min)) : random.nextInt(17));
            int x = ox + (int) (Math.cos(angle) * dist);
            int z = oz + (int) (Math.sin(angle) * dist);
            if (isValidBuildSite(level, x, z, DEFAULT_CLEAR_RADIUS, probe)) return new BlockPos(x, 64, z);
        }
        return null;
    }

    private static boolean driftToValid(ServerLevel level, Site site) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            int ox = site.center.getX() + (random.nextBoolean() ? 1 : -1) * (32 + random.nextInt(48));
            int oz = site.center.getZ() + (random.nextBoolean() ? 1 : -1) * (32 + random.nextInt(48));
            if (isValidBuildSite(level, ox, oz, DEFAULT_CLEAR_RADIUS, site)
                && !nearPlayerBlocks(level, new BlockPos(ox, 64, oz))) {
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
            if (isValidBuildSite(level, x, z, DEFAULT_CLEAR_RADIUS, site)) {
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
        long day = level.getDayTime() / 24000L;
        int expireAfter = Math.max(5, Config.sideEventCooldownDays * 2);
        for (Site site : new ArrayList<>(data.sites)) {
            // Legacy saves: stamp today once so we don't instantly purge.
            if (site.createdDay < 0) {
                site.createdDay = day;
                data.setDirty();
            }
            // Relays + missing-recruit markers stay until resolved (expiring missing drops the only
            // waypoint while missingSurvivorId still blocks the EB).
            boolean sticky = KIND_RELAY.equals(site.kind) || KIND_MISSING.equals(site.kind);
            if (!sticky && site.createdDay >= 0 && day - site.createdDay >= expireAfter) {
                boolean wasUnplaced = !site.placed;
                data.sites.remove(site);
                data.setDirty();
                if (wasUnplaced) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(site.owner);
                    if (owner != null) {
                        owner.sendSystemMessage(Component.translatable("message.dead_air.site.expired")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                }
                continue;
            }
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

        // Missing-recruit: survivor was already teleported to center at create — never drift the
        // marker away from them (waypoint would lie while missingSurvivorId stays locked).
        boolean badSpot = !force && !KIND_MISSING.equals(site.kind)
            && (nearPlayerBlocks(level, site.center)
            || isWaterAt(level, site.center)
            || !isValidBuildSite(level, site.center.getX(), site.center.getZ(), DEFAULT_CLEAR_RADIUS, site));
        if (badSpot) {
            if (site.shifts < MAX_DRIFT_ATTEMPTS) {
                driftToValid(level, site);
                syncRelayTarget(level, site, site.center);
                data.setDirty();
                notifyDrift(level, site);
                refreshWaypoint(level, site);
                return;
            }
            // Exhausted drifts — abandon so sticky relays / hasUnplaced cannot soft-lock forever.
            abandonUnplaceable(level, data, site);
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
            resyncMissingRecruit(level, site);
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
            // Always retarget — large drift used to leave targetRelay pointing at the old center.
            if (site.keyPos != null) syncRelayTarget(level, site, site.keyPos);
        } else if (KIND_CAMP.equals(site.kind) && site.keyPos != null) {
            SurvivorSpawner.spawnStranded(level, site.keyPos, site.owner);
        } else if (KIND_CONVOY.equals(site.kind) && site.keyPos != null) {
            int count = 2 + level.getRandom().nextInt(2);
            for (int i = 0; i < count; i++) {
                BlockPos at = findSafeStandNear(level, site.keyPos, 4);
                SurvivorSpawner.spawnStranded(level, at, site.owner);
            }
        } else if (KIND_AUCTION.equals(site.kind) && site.keyPos != null) {
            uk.co.extraspecialstudio.dead_air.survivor.AuctionTraderSpawner.spawn(level, site.keyPos, site.owner);
        }
    }

    /** Keep story compass / rebroadcast coords aligned with the live relay site. */
    private static void syncRelayTarget(ServerLevel level, Site site, BlockPos at) {
        if (!KIND_RELAY.equals(site.kind) || at == null || site.owner == null) return;
        BroadcastProgress progress = BroadcastProgress.get(level.getServer());
        if (progress == null) return;
        BroadcastProgress.PlayerProgress p = progress.get(site.owner);
        // Only update when this site is (or was) the player's promised relay.
        if (p.targetRelay != null && horizontalDist(p.targetRelay, site.center) > 512
            && horizontalDist(p.targetRelay, at) > 512) {
            return;
        }
        p.targetRelay = at.immutable();
        progress.setDirty();
    }

    /**
     * Drop an unplaceable pin after max drift. Clears sticky relay {@code targetRelay} so
     * story broadcasts can create a fresh site instead of soft-locking forever.
     */
    private static void abandonUnplaceable(ServerLevel level, PendingSites data, Site site) {
        Dead_air.LOGGER.warn("[Dead Air] Site {} abandoned at {} after {} drifts (claim/structure/density/terrain)",
            site.kind, site.center, site.shifts);
        data.sites.remove(site);
        data.setDirty();
        if (KIND_RELAY.equals(site.kind) && site.owner != null) {
            BroadcastProgress progress = BroadcastProgress.get(level.getServer());
            if (progress != null) {
                BroadcastProgress.PlayerProgress p = progress.get(site.owner);
                if (p.targetRelay != null && (horizontalDist(p.targetRelay, site.center) < 512
                    || (site.keyPos != null && horizontalDist(p.targetRelay, site.keyPos) < 512))) {
                    p.targetRelay = null;
                    progress.setDirty();
                }
            }
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.dead_air.site.abandoned")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    /** After chunks load, snap the missing recruit to the real surface under the pin. */
    private static void resyncMissingRecruit(ServerLevel level, Site site) {
        if (site.keyPos == null || site.owner == null) return;
        BroadcastProgress progress = BroadcastProgress.get(level.getServer());
        if (progress == null) return;
        UUID missingId = progress.get(site.owner).missingSurvivorId;
        if (missingId == null) return;
        net.minecraft.world.entity.Entity e = level.getEntity(missingId);
        if (e instanceof uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity survivor
            && survivor.isAlive()) {
            survivor.teleportTo(site.keyPos.getX() + 0.5, site.keyPos.getY(), site.keyPos.getZ() + 0.5);
        }
    }

    /** Prefer air + solid footing near {@code origin}; fall back to origin. */
    private static BlockPos findSafeStandNear(ServerLevel level, BlockPos origin, int radius) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos at = origin.offset(dx, 0, dz);
            if (level.getBlockState(at).isAir()
                && level.getBlockState(at.above()).isAir()
                && !level.getBlockState(at.below()).isAir()
                && level.getBlockState(at.below()).getFluidState().isEmpty()) {
                return at;
            }
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int d = 1; d <= radius; d++) {
                BlockPos at = origin.relative(dir, d);
                if (level.getBlockState(at).isAir()
                    && level.getBlockState(at.above()).isAir()
                    && !level.getBlockState(at.below()).isAir()) {
                    return at;
                }
            }
        }
        return origin;
    }

    /** Remove placed relay sites near an investigated cabinet (density + hunt cleanup). */
    public static void clearRelayNear(MinecraftServer server, UUID owner, BlockPos pos) {
        if (server == null || owner == null || pos == null) return;
        PendingSites data = get(server);
        if (data == null || data.sites.isEmpty()) return;
        boolean removed = false;
        for (Site s : new ArrayList<>(data.sites)) {
            if (!KIND_RELAY.equals(s.kind) || !owner.equals(s.owner)) continue;
            BlockPos at = s.keyPos != null ? s.keyPos : s.center;
            if (at == null) continue;
            if (at.distSqr(pos) < 96 * 96) {
                data.sites.remove(s);
                removed = true;
            }
        }
        if (removed) data.setDirty();
    }

    /** Soft RT plane spawn — ModuleClassLoader can CNF RadioTowersPlaneCompat; never crash the server. */
    private static boolean trySpawnRtPlaneWreck(ServerLevel level, BlockPos center) {
        try {
            return uk.co.extraspecialstudio.dead_air.compat.RadioTowersPlaneCompat
                .trySpawnGroundedWreck(level, center);
        } catch (LinkageError | Exception e) {
            Dead_air.LOGGER.warn("[Dead Air] RT plane wreck unavailable, using debris: {}", e.toString());
            return false;
        }
    }

    /** @return true if a wreck + chest were placed (world may already be mutated on failure). */
    private static boolean placeCrashSite(ServerLevel level, PendingSites data, Site site) {
        int groundY = surfaceY(level, site.center.getX(), site.center.getZ());
        BlockPos center = new BlockPos(site.center.getX(), groundY, site.center.getZ());
        boolean plane = trySpawnRtPlaneWreck(level, center);
        if (plane) {
            // RT crashed plane is the silhouette — loot/scars only, never pile iron scrap on top.
            CrashSitePlacer.placeLootAndScars(level, center);
        } else {
            CrashSitePlacer.placeDebrisWreck(level, center);
            // Debris already mutated the world — never leave placed=false or the next tick doubles chests.
            if (!CrashSitePlacer.hasVisibleWreck(level, center)) {
                Dead_air.LOGGER.warn("[Dead Air] Crash debris at {} failed visibility check; marking placed anyway",
                    center);
            }
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
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()) - 1;
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

    public static boolean hasPlaced(MinecraftServer server, UUID owner, String kind) {
        for (Site s : forOwner(server, owner)) {
            if (kind.equals(s.kind) && s.placed) return true;
        }
        return false;
    }

    /** Drop missing-recruit markers once the recruit is recovered (or the soft-lock cleared). */
    public static void clearMissingFor(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return;
        PendingSites data = get(server);
        if (data == null || data.sites.isEmpty()) return;
        boolean removed = false;
        for (Site s : new ArrayList<>(data.sites)) {
            if (KIND_MISSING.equals(s.kind) && owner.equals(s.owner)) {
                data.sites.remove(s);
                removed = true;
            }
        }
        if (removed) data.setDirty();
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
