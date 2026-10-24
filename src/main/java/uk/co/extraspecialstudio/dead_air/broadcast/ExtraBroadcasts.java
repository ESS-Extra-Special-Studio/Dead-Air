package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.music.ExpansionPacks;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorRoster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extra Emergency Broadcast events: auction, convoy, base attack, missing recruit,
 * pirate FM interruption, downed plane.
 */
public final class ExtraBroadcasts {
    public static final ResourceLocation DEAD_CHANNEL_AUCTION =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "dead_channel_auction");
    public static final ResourceLocation REFUGEE_CONVOY =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "refugee_convoy");
    public static final ResourceLocation BASE_UNDER_ATTACK =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "base_under_attack");
    public static final ResourceLocation MISSING_RECRUIT =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "missing_recruit");
    public static final ResourceLocation PIRATE_FM =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "pirate_fm_interruption");
    public static final ResourceLocation DOWNED_PLANE =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "downed_plane");

    public static void registerDefaults() {
        BroadcastEventRegistry.register(new DeadChannelAuction());
        BroadcastEventRegistry.register(new RefugeeConvoy());
        BroadcastEventRegistry.register(new BaseUnderAttack());
        BroadcastEventRegistry.register(new MissingRecruit());
        BroadcastEventRegistry.register(new PirateFmInterruption());
        BroadcastEventRegistry.register(new DownedPlane());
    }

    private static long today(ServerPlayer player) {
        return player.server.overworld().getDayTime() / 24000L;
    }

    private static boolean cooled(long lastDay, long today) {
        if (lastDay < 0) return true;
        return today - lastDay >= Config.sideEventCooldownDays;
    }

    /**
     * True when a high-priority story EB can fire. Must not call {@link BroadcastEventRegistry#eligible}
     * â€” side events (incl. Pirate FM, which is STORY) call this from {@code canTrigger}, which would recurse.
     */
    private static boolean hasCriticalStory(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
        for (BroadcastEvent e : BroadcastEventRegistry.all()) {
            if (e.category() != BroadcastEvent.Category.STORY) continue;
            if (e.priority() < 5) continue;
            try {
                if (e.canTrigger(player, progress)) return true;
            } catch (Exception ex) {
                Dead_air.LOGGER.warn("[Dead Air] Critical-story check failed for {}", e.id(), ex);
            }
        }
        return false;
    }

    /** Dead channel auction â€” temporary scrap trader camp. */
    static final class DeadChannelAuction implements BroadcastEvent {
        @Override public ResourceLocation id() { return DEAD_CHANNEL_AUCTION; }
        @Override public Category category() { return Category.TRADERS; }
        @Override public int priority() { return -2; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (!Config.survivorsEnabled || !BroadcastScheduler.hasT3(player)) return false;
            if (hasCriticalStory(player, progress)) return false;
            if (!cooled(progress.lastAuctionDay, today(player))) return false;
            return !PendingSites.hasUnplaced(player.server, player.getUUID(), PendingSites.KIND_AUCTION);
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            PendingSites.Site site = PendingSites.create(player, PendingSites.KIND_AUCTION,
                "survivor_camp/auction", 120, 280);
            if (site == null) return List.of();
            progress.lastAuctionDay = today(player);
            return List.of(
                "DEAD CHANNEL. SOMEONE IS SELLING SCRAP OFF THE GRID.",
                "THEY SAY THE STALL PACKS UP IN A FEW DAYS. BRING EMERALDS OR NOTES.",
                "AUCTION FIX: X " + site.center.getX() + "  Z " + site.center.getZ() + ".");
        }
    }

    /** Refugee convoy â€” 2â€“3 stranded survivors at one camp. */
    static final class RefugeeConvoy implements BroadcastEvent {
        @Override public ResourceLocation id() { return REFUGEE_CONVOY; }
        @Override public Category category() { return Category.SURVIVOR; }
        @Override public int priority() { return -1; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (!Config.survivorsEnabled) return false;
            if (!BroadcastScheduler.serverHasEbAudience(player.server)) return false;
            if (!BroadcastScheduler.canHearEmergency(player)) return false;
            if (hasCriticalStory(player, progress)) return false;
            SurvivorRoster roster = SurvivorRoster.get(player.server);
            if (roster != null && roster.count(player.getUUID()) >= Config.maxSurvivorsPerPlayer) return false;
            if (!cooled(progress.lastConvoyDay, today(player))) return false;
            return !PendingSites.hasUnplaced(player.server, player.getUUID(), PendingSites.KIND_CONVOY)
                && !PendingSites.hasUnplaced(player.server, player.getUUID(), PendingSites.KIND_CAMP);
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            PendingSites.Site site = PendingSites.create(player, PendingSites.KIND_CONVOY,
                "survivor_camp/convoy", 150, 350);
            if (site == null) return List.of();
            progress.lastConvoyDay = today(player);
            return List.of(
                "MORE THAN ONE VOICE ON THIS CHANNEL.",
                "A CONVOY BROKE DOWN. TWO OR THREE STILL ANSWERING.",
                "CAMP FIX: X " + site.center.getX() + "  Z " + site.center.getZ() + ".");
        }
    }

    /** Base under attack â€” only with a set panel base. */
    static final class BaseUnderAttack implements BroadcastEvent {
        @Override public ResourceLocation id() { return BASE_UNDER_ATTACK; }
        @Override public Category category() { return Category.DEFENSE; }
        @Override public int priority() { return -3; }
        @Override public String title() { return "BASE ALERT"; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (!PlayerBaseManager.hasBase(player.server, player.getUUID())) return false;
            if (BaseAttackManager.hasActive(player.getUUID())) return false;
            if (hasCriticalStory(player, progress)) return false;
            long day = today(player);
            if (progress.lastBaseAttackDay >= 0
                && day - progress.lastBaseAttackDay < Config.baseAttackCooldownDays) return false;
            return true;
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            PlayerBaseStorage.Base base = PlayerBaseManager.getBase(player.server, player.getUUID());
            if (base == null) return List.of();
            progress.lastBaseAttackDay = today(player);
            BaseAttackManager.startWave(player, base);
            BlockPos p = base.panelPos;
            return List.of(
                "YOUR PANEL IS SCREAMING. HOSTILES ON THE GRID.",
                "SETTLED SURVIVORS â€” IF ANY â€” ARE MOVING TO DEFEND.",
                "BASE AT X " + p.getX() + "  Z " + p.getZ() + ". HOLD THE LINE.");
        }
    }

    /** Missing recruit â€” needs â‰¥1 SETTLED owned survivor. */
    static final class MissingRecruit implements BroadcastEvent {
        @Override public ResourceLocation id() { return MISSING_RECRUIT; }
        @Override public Category category() { return Category.SURVIVOR; }
        @Override public int priority() { return -1; }
        @Override public String title() { return "MISSING SIGNAL"; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (!Config.survivorsEnabled) return false;
            if (!BroadcastScheduler.serverHasEbAudience(player.server)) return false;
            if (!BroadcastScheduler.canHearEmergency(player)) return false;
            if (progress.missingSurvivorId != null) return false;
            if (hasCriticalStory(player, progress)) return false;
            if (!cooled(progress.lastMissingDay, today(player))) return false;
            return !findSettled(player).isEmpty();
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            List<SurvivorEntity> settled = findSettled(player);
            if (settled.isEmpty()) return List.of();
            SurvivorEntity pick = settled.get(player.getRandom().nextInt(settled.size()));
            PendingSites.Site site = PendingSites.create(player, PendingSites.KIND_MISSING,
                "marker", 80, 200);
            if (site == null) return List.of();

            int y = player.server.overworld().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                site.center.getX(), site.center.getZ());
            BlockPos at = new BlockPos(site.center.getX(), y, site.center.getZ());
            pick.markMissingAt(at);
            progress.missingSurvivorId = pick.getUUID();
            progress.lastMissingDay = today(player);

            String name = pick.getIdentity().name;
            return List.of(
                name.toUpperCase() + " HAS GONE QUIET ON THE ROSTER.",
                "LAST KNOWN PING BEFORE THE STATIC.",
                "SEARCH X " + site.center.getX() + "  Z " + site.center.getZ() + ".");
        }

        private static List<SurvivorEntity> findSettled(ServerPlayer player) {
            List<SurvivorEntity> out = new ArrayList<>();
            UUID id = player.getUUID();
            for (ServerLevel level : player.server.getAllLevels()) {
                // Prefer base area, then a wide box around the player in overworld
                PlayerBaseStorage.Base base = PlayerBaseManager.getBase(player.server, id);
                AABB box;
                if (base != null && level.dimension() == base.dimension) {
                    int r = PlayerBaseManager.signalRange(level, base.panelPos) + 32;
                    box = new AABB(base.panelPos).inflate(r);
                } else if (level == player.serverLevel()) {
                    box = player.getBoundingBox().inflate(256);
                } else {
                    continue;
                }
                out.addAll(level.getEntitiesOfClass(SurvivorEntity.class, box,
                    s -> s.isOwnedBy(player) && s.getSurvivorState() == SurvivorEntity.SETTLED
                        && !s.isMissing()));
            }
            return out;
        }
    }

    /**
     * Soft expansion-pack hook: one-shot pirate FM interruption when a known soundtrack pack is loaded.
     * Pack authors can also {@link uk.co.extraspecialstudio.dead_air.api.DeadAirAPI#registerBroadcastEvent}.
     */
    static final class PirateFmInterruption implements BroadcastEvent {
        @Override public ResourceLocation id() { return PIRATE_FM; }
        @Override public Category category() { return Category.STORY; }
        @Override public int priority() { return -8; }
        @Override public String title() { return "PIRATE INTERRUPTION"; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (progress.pirateFmHeard) return false;
            if (hasCriticalStory(player, progress)) return false;
            if (!BroadcastScheduler.hasT3(player)) return false;
            List<String> packs = ExpansionPacks.installedKnownPacks();
            if (!packs.isEmpty()) return true;
            // Also accept config-listed expansion stations present in the registry
            for (String ns : Config.expansionStations) {
                if (ns == null || ns.isBlank()) continue;
                RadioStation s = StationRegistry.getStation(ExpansionPacks.stationIdFor(ns.trim()));
                if (s != null) return true;
            }
            return false;
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            progress.pirateFmHeard = true;
            String packNs = null;
            String packName = null;
            List<String> installed = ExpansionPacks.installedKnownPacks();
            if (!installed.isEmpty()) {
                packNs = installed.get(player.getRandom().nextInt(installed.size()));
                packName = ExpansionPacks.displayName(packNs);
            } else {
                for (String ns : Config.expansionStations) {
                    if (ns == null || ns.isBlank()) continue;
                    packNs = ns.trim();
                    packName = ExpansionPacks.displayName(packNs);
                    break;
                }
            }
            if (packNs == null) return List.of();
            RadioStation station = ExpansionPacks.ensureStation(packNs);
            String freq = station != null
                ? String.format("%.1f", station.getFrequency())
                : "??.?";
            return List.of(
                "UNAUTHORIZED TRANSMISSION CUTTING THROUGH THE STATIC.",
                "PIRATE TAG: " + (packName != null ? packName.toUpperCase() : packNs.toUpperCase()) + ".",
                "TUNE " + freq + " MHZ IF YOU WANT TO HEAR WHAT THEY'RE BLASTING.");
        }
    }

    /** Downed plane crash site â€” RT plane soft dep, debris fallback. */
    static final class DownedPlane implements BroadcastEvent {
        @Override public ResourceLocation id() { return DOWNED_PLANE; }
        @Override public Category category() { return Category.SURVIVOR; }
        @Override public int priority() { return -2; }
        @Override public String title() { return "CRASH BEACON"; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (!BroadcastScheduler.serverHasEbAudience(player.server)) return false;
            if (!BroadcastScheduler.canHearEmergency(player)) return false;
            if (hasCriticalStory(player, progress)) return false;
            if (!cooled(progress.lastPlaneDay, today(player))) return false;
            return !PendingSites.hasUnplaced(player.server, player.getUUID(), PendingSites.KIND_CRASH);
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            PendingSites.Site site = PendingSites.create(player, PendingSites.KIND_CRASH,
                "procedural_crash", 180, 420);
            if (site == null) return List.of();
            progress.lastPlaneDay = today(player);
            boolean rt = ModList.get().isLoaded("radiotowers");
            return List.of(
                "EMERGENCY LOCATOR. A SUPPLY PLANE NEVER MADE THE DROP.",
                rt ? "WRECKAGE MATCHES THE AIRDROP FLEET. APPROACH CAREFUL."
                    : "METAL AND SMOKE ON THE HORIZON. LOOT IF YOU DARE.",
                "CRASH FIX: X " + site.center.getX() + "  Z " + site.center.getZ() + ".");
        }
    }

    private ExtraBroadcasts() {}
}
