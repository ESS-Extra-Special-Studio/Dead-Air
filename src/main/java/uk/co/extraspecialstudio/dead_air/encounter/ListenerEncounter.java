package uk.co.extraspecialstudio.dead_air.encounter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents;
import uk.co.extraspecialstudio.dead_air.api.events.types.ListenerDefeatedEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.ListenerFirstEncounterSurvivedEvent;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.StoryStage;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;
import uk.co.extraspecialstudio.dead_air.entity.ListenerEntity;
import uk.co.extraspecialstudio.dead_air.entity.SignalAnchorEntity;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EncounterStatePacket;
import uk.co.extraspecialstudio.dead_air.net.ForceEmergencyTunePacket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Active fights with The Listener. FIRST: unwinnable, ends on timer or when every Signal Anchor is
 * broken. FINAL: ends when he dies (Signal Breaker hits only). Everything is non-destructive: no blocks change.
 */
public final class ListenerEncounter {
    public enum Mode { FIRST, FINAL }

    public enum EndReason { SURVIVED, DEFEATED, FAILED, ABORTED }

    private static final double PARTICIPANT_RADIUS = 48;
    private static final Map<UUID, Encounter> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> BRIEF_JAMS = new HashMap<>();

    public static final class Encounter {
        public final UUID id = UUID.randomUUID();
        public final Mode mode;
        public final ServerLevel level;
        public final BlockPos center;
        public final UUID summoner;
        public final Set<UUID> participants = new LinkedHashSet<>();
        public final List<UUID> anchors = new ArrayList<>();
        public UUID listenerId;
        public int ticksLeft;
        public final int totalTicks;
        public int anchorsBroken;
        private boolean ended;

        Encounter(Mode mode, ServerLevel level, BlockPos center, UUID summoner, int totalTicks) {
            this.mode = mode;
            this.level = level;
            this.center = center;
            this.summoner = summoner;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
        }
    }

    public static boolean isActive(UUID encounterId) {
        return encounterId != null && ACTIVE.containsKey(encounterId);
    }

    public static boolean isParticipant(ServerPlayer player) {
        for (Encounter e : ACTIVE.values()) if (e.participants.contains(player.getUUID())) return true;
        return false;
    }

    /** True if this player is still tied to any live fight (active participant or summoner). */
    public static boolean isInvolved(ServerPlayer player) {
        UUID id = player.getUUID();
        for (Encounter e : ACTIVE.values()) {
            if (e.participants.contains(id) || id.equals(e.summoner)) {
                return true;
            }
        }
        return false;
    }

    public static Collection<Encounter> all() {
        return new ArrayList<>(ACTIVE.values());
    }

    /** Start a fight next to the player. Returns null (with a message) if one is already running for them. */
    public static Encounter start(ServerPlayer player, Mode mode) {
        if (isParticipant(player)) {
            player.sendSystemMessage(Component.translatable("message.dead_air.listener.already").withStyle(ChatFormatting.RED));
            return null;
        }
        ServerLevel level = player.serverLevel();
        int total = mode == Mode.FIRST ? Config.encounterDurationSeconds * 20 : 0;
        Encounter enc = new Encounter(mode, level, player.blockPosition(), player.getUUID(), total);

        ListenerEntity listener = DeadAirEntities.THE_LISTENER.get().create(level);
        if (listener == null) return null;
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        BlockPos spawn = ground(level, player.blockPosition().offset((int) (look.x * 14), 0, (int) (look.z * 14)));
        listener.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot() + 180f, 0);
        listener.setMode(mode == Mode.FIRST ? ListenerEntity.Mode.FIRST : ListenerEntity.Mode.FINAL, enc.id);
        listener.setTarget(player);
        ACTIVE.put(enc.id, enc);
        level.addFreshEntity(listener);
        listener.triggerAnim("action", "jump_in");
        enc.listenerId = listener.getUUID();

        if (mode == Mode.FIRST) {
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI * 2 / 3 + level.random.nextDouble() * 0.8;
                double dist = 10 + level.random.nextDouble() * 10;
                BlockPos p = ground(level, enc.center.offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist)));
                spawnAnchor(enc, p);
            }
        }

        refreshParticipants(enc);
        enc.participants.add(player.getUUID());
        for (ServerPlayer p : players(enc)) onJoin(enc, p);
        Dead_air.LOGGER.info("[Dead Air] Listener encounter {} started ({}) by {}", enc.id, mode, player.getGameProfile().getName());
        return enc;
    }

    public static void spawnAnchor(Encounter enc, BlockPos pos) {
        SignalAnchorEntity anchor = DeadAirEntities.SIGNAL_ANCHOR.get().create(enc.level);
        if (anchor == null) return;
        anchor.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, enc.level.random.nextFloat() * 360f, 0);
        anchor.setEncounter(enc.id);
        enc.level.addFreshEntity(anchor);
        enc.anchors.add(anchor.getUUID());
        enc.level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
            pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.8, 0.3, 0.2);
    }

    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static void onJoin(Encounter enc, ServerPlayer p) {
        DeadAirNet.sendTo(p, new EncounterStatePacket(true, 0, 0));
        DeadAirNet.sendTo(p, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.JAM_ON));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.dead_air.listener.arrives")
            .withStyle(ChatFormatting.DARK_PURPLE)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(enc.mode == Mode.FIRST
            ? "title.dead_air.listener.first_sub" : "title.dead_air.listener.final_sub").withStyle(ChatFormatting.GRAY)));
        ListenerEntity l = listener(enc);
        if (l != null) {
            if (enc.mode == Mode.FIRST) refreshTimerHud(enc, l, true);
            l.getBossEvent().addPlayer(p);
        }
        if (enc.mode == Mode.FIRST) {
            int secs = Math.max(0, enc.ticksLeft / 20);
            p.sendSystemMessage(Component.translatable("message.dead_air.listener.briefing")
                .withStyle(ChatFormatting.AQUA));
            p.sendSystemMessage(Component.translatable("message.dead_air.listener.timer_actionbar",
                secs / 60, String.format("%02d", secs % 60)).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void onLeave(Encounter enc, ServerPlayer p) {
        DeadAirNet.sendTo(p, new EncounterStatePacket(false, 0, 0));
        DeadAirNet.sendTo(p, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.JAM_OFF));
        ListenerEntity l = listener(enc);
        if (l != null) l.getBossEvent().removePlayer(p);
    }

    private static List<ServerPlayer> players(Encounter enc) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : enc.participants) {
            ServerPlayer p = enc.level.getServer().getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static ListenerEntity listener(Encounter enc) {
        if (enc.listenerId == null) return null;
        Entity e = enc.level.getEntity(enc.listenerId);
        return e instanceof ListenerEntity l ? l : null;
    }

    private static void refreshParticipants(Encounter enc) {
        Vec3 c = listener(enc) != null ? listener(enc).position() : Vec3.atCenterOf(enc.center);
        for (ServerPlayer p : enc.level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.position().distanceTo(c) <= PARTICIPANT_RADIUS && enc.participants.add(p.getUUID())) {
                onJoin(enc, p);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (!BRIEF_JAMS.isEmpty()) {
            for (UUID id : new ArrayList<>(BRIEF_JAMS.keySet())) {
                int left = BRIEF_JAMS.get(id) - 1;
                if (left > 0) {
                    BRIEF_JAMS.put(id, left);
                    continue;
                }
                BRIEF_JAMS.remove(id);
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && !isParticipant(p)) DeadAirNet.sendTo(p, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.JAM_OFF));
            }
        }
        if (ACTIVE.isEmpty()) return;
        for (Encounter enc : new ArrayList<>(ACTIVE.values())) {
            try {
                tickEncounter(enc);
            } catch (Exception e) {
                Dead_air.LOGGER.error("[Dead Air] Listener encounter tick failed", e);
                end(enc, EndReason.ABORTED);
            }
        }
    }

    private static void tickEncounter(Encounter enc) {
        if (enc.ended) return;
        ListenerEntity listener = listener(enc);
        if (listener == null) {
            end(enc, EndReason.ABORTED);
            return;
        }
        if (!listener.isAlive()) return;
        if (enc.level.getGameTime() % 20 == 0) refreshParticipants(enc);

        for (UUID id : new ArrayList<>(enc.participants)) {
            ServerPlayer p = enc.level.getServer().getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive() || p.level() != enc.level) {
                enc.participants.remove(id);
                if (p != null) onLeave(enc, p);
            }
        }
        if (enc.participants.isEmpty()) {
            end(enc, EndReason.FAILED);
            return;
        }
        LivingTargetKeeper.keepTarget(listener, players(enc));

        if (enc.mode == Mode.FIRST) {
            enc.ticksLeft--;
            if (enc.level.getGameTime() % 10 == 0) {
                refreshTimerHud(enc, listener, false);
            }
            if (enc.ticksLeft <= 0) {
                end(enc, EndReason.SURVIVED);
                return;
            }
        }

        List<ServerPlayer> players = players(enc);
        long t = enc.level.getGameTime();
        if (t % 160 == 0 || enc.level.random.nextInt(300) == 0) {
            ServerPlayer target = players.get(enc.level.random.nextInt(players.size()));
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(enc.level);
            if (bolt != null) {
                BlockPos at = ground(enc.level, target.blockPosition().offset(enc.level.random.nextInt(24) - 12, 0, enc.level.random.nextInt(24) - 12));
                bolt.moveTo(Vec3.atBottomCenterOf(at));
                bolt.setVisualOnly(true);
                enc.level.addFreshEntity(bolt);
            }
        }
        if (t % 200 == 0) {
            for (ServerPlayer p : players) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
        }
    }

    /** Boss bar always shows remaining time; action bar refreshes every second as a backup. */
    private static void refreshTimerHud(Encounter enc, ListenerEntity listener, boolean forceActionBar) {
        int secs = Math.max(0, enc.ticksLeft / 20);
        String mm = Integer.toString(secs / 60);
        String ss = String.format("%02d", secs % 60);
        listener.getBossEvent().setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        listener.getBossEvent().setName(Component.translatable("bossbar.dead_air.listener.timer", mm, ss)
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        listener.getBossEvent().setProgress(Math.max(0f, enc.ticksLeft / (float) Math.max(1, enc.totalTicks)));
        if (forceActionBar || enc.ticksLeft % 20 == 0) {
            Component bar = Component.translatable("message.dead_air.listener.timer_actionbar", mm, ss)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
            for (ServerPlayer p : players(enc)) {
                p.displayClientMessage(bar, true);
            }
        }
    }

    public static void onAnchorBroken(UUID encounterId) {
        Encounter enc = ACTIVE.get(encounterId);
        if (enc == null) return;
        enc.anchorsBroken++;
        int left = enc.anchors.size() - enc.anchorsBroken;
        for (ServerPlayer p : players(enc)) {
            p.displayClientMessage(Component.translatable("message.dead_air.listener.anchor_broken", Math.max(0, left))
                .withStyle(ChatFormatting.AQUA), true);
        }
        if (left <= 0 && enc.mode == Mode.FIRST) end(enc, EndReason.SURVIVED);
    }

    public static void onListenerDied(ListenerEntity listener, DamageSource source) {
        for (Encounter enc : new ArrayList<>(ACTIVE.values())) {
            if (listener.getUUID().equals(enc.listenerId)) {
                if (enc.mode == Mode.FINAL) {
                    dropVictoryLoot(enc, listener);
                    end(enc, EndReason.DEFEATED);
                } else {
                    end(enc, EndReason.ABORTED);
                }
                return;
            }
        }
    }

    /** Admin / recovery: abort without victory credit or loot. */
    public static void abortForListener(ListenerEntity listener) {
        if (listener == null) return;
        for (Encounter enc : new ArrayList<>(ACTIVE.values())) {
            if (listener.getUUID().equals(enc.listenerId)) {
                end(enc, EndReason.ABORTED);
                return;
            }
        }
    }

    private static void dropVictoryLoot(Encounter enc, ListenerEntity listener) {
        List<ItemStack> loot = List.of(
            new ItemStack(Items.NETHER_STAR),
            new ItemStack(Items.DIAMOND, 6),
            new ItemStack(Items.ECHO_SHARD, 8),
            new ItemStack(DeadAirItems.RESONANT_CHORD.get(), 4),
            new ItemStack(DeadAirItems.STATIC_NOTE.get(), 24));
        for (ItemStack s : loot) {
            ItemEntity item = new ItemEntity(enc.level, listener.getX(), listener.getY() + 1, listener.getZ(), s);
            item.setGlowingTag(true);
            enc.level.addFreshEntity(item);
        }
    }

    /** Player died or logged out mid-fight. */
    public static void onPlayerGone(ServerPlayer player) {
        for (Encounter enc : new ArrayList<>(ACTIVE.values())) {
            if (enc.participants.remove(player.getUUID())) {
                onLeave(enc, player);
                if (enc.participants.isEmpty()) end(enc, EndReason.FAILED);
            }
        }
    }

    public static void end(Encounter enc, EndReason reason) {
        if (enc.ended) return;
        enc.ended = true;
        List<ServerPlayer> players = players(enc);
        ListenerEntity listener = listener(enc);
        if (listener != null) {
            // Always clear the boss bar â€” abort/death paths previously left it sticky when dissolve was skipped.
            listener.getBossEvent().removeAllPlayers();
            if (listener.isAlive()) listener.dissolve();
        }
        for (UUID id : enc.anchors) {
            Entity a = enc.level.getEntity(id);
            if (a != null) a.discard();
        }
        ACTIVE.remove(enc.id);
        for (ServerPlayer p : players) onLeave(enc, p);

        BroadcastProgress data = BroadcastProgress.get(enc.level.getServer());
        if (reason == EndReason.SURVIVED && enc.mode == Mode.FIRST && data != null) {
            for (ServerPlayer p : players) {
                BroadcastProgress.PlayerProgress prog = data.get(p.getUUID());
                prog.firstEncounterSurvived = true;
                prog.bossModeUnlocked = true;
                if (prog.stage < StoryStage.FIRST_SURVIVED) prog.stage = StoryStage.FIRST_SURVIVED;
                p.sendSystemMessage(Component.translatable("message.dead_air.listener.survived").withStyle(ChatFormatting.AQUA));
                p.sendSystemMessage(Component.translatable("message.dead_air.listener.boss_mode").withStyle(ChatFormatting.GOLD));
                DeadAirEvents.post(new ListenerFirstEncounterSurvivedEvent(p));
            }
            data.setDirty();
        } else if (reason == EndReason.DEFEATED && data != null) {
            for (ServerPlayer p : players) {
                BroadcastProgress.PlayerProgress prog = data.get(p.getUUID());
                prog.finalDefeated = true;
                prog.stage = StoryStage.DEFEATED;
                p.sendSystemMessage(Component.translatable("message.dead_air.listener.defeated").withStyle(ChatFormatting.GOLD));
                DeadAirEvents.post(new ListenerDefeatedEvent(p, true));
            }
            data.setDirty();
        } else if (reason == EndReason.FAILED) {
            ServerPlayer summoner = enc.level.getServer().getPlayerList().getPlayer(enc.summoner);
            if (summoner != null) {
                summoner.sendSystemMessage(Component.translatable("message.dead_air.listener.failed").withStyle(ChatFormatting.GRAY));
            }
        }
        Dead_air.LOGGER.info("[Dead Air] Listener encounter {} ended: {}", enc.id, reason);
    }

    public static int stopAll() {
        int n = 0;
        for (Encounter enc : new ArrayList<>(ACTIVE.values())) {
            end(enc, EndReason.ABORTED);
            n++;
        }
        return n;
    }

    /**
     * Dedicated-server recovery: abort every encounter, discard every Listener and Signal Anchor
     * in loaded chunks, clear boss bars, and reset encounter fog/jam on every online player.
     */
    public static PurgeResult adminPurge(MinecraftServer server) {
        int encounters = stopAll();
        int listeners = 0;
        int anchors = 0;
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                List<ListenerEntity> lingering = new ArrayList<>();
                List<SignalAnchorEntity> orphanAnchors = new ArrayList<>();
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof ListenerEntity l && !l.isRemoved()) lingering.add(l);
                    else if (e instanceof SignalAnchorEntity a && !a.isRemoved()) orphanAnchors.add(a);
                }
                for (ListenerEntity l : lingering) {
                    l.killFromCommand();
                    listeners++;
                }
                for (SignalAnchorEntity a : orphanAnchors) {
                    a.discard();
                    anchors++;
                }
            }
            clearClientFx(server);
        }
        BRIEF_JAMS.clear();
        Dead_air.LOGGER.warn("[Dead Air] Listener admin purge: encounters={} listeners={} anchors={}",
            encounters, listeners, anchors);
        return new PurgeResult(encounters, listeners, anchors);
    }

    public static void clearClientFx(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            DeadAirNet.sendTo(p, new EncounterStatePacket(false, 0, 0));
            DeadAirNet.sendTo(p, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.JAM_OFF));
        }
    }

    public record PurgeResult(int encounters, int listeners, int anchors) {}

    public static Encounter forPlayer(ServerPlayer player) {
        for (Encounter e : ACTIVE.values()) if (e.participants.contains(player.getUUID())) return e;
        return null;
    }

    /** Radios near a wild Listener's scream go to static for a few seconds. */
    public static void jamBriefly(ServerPlayer player) {
        DeadAirNet.sendTo(player, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.JAM_ON));
        BRIEF_JAMS.put(player.getUUID(), 100);
    }

    /** Keeps the Listener on a living participant when its target dies or leaves. */
    static final class LivingTargetKeeper {
        static void keepTarget(ListenerEntity listener, List<ServerPlayer> players) {
            var current = listener.getTarget();
            if (current != null && current.isAlive() && players.contains(current)) return;
            ServerPlayer nearest = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                if (p.isCreative() || p.isSpectator()) continue;
                double d = p.distanceToSqr(listener);
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest != null) listener.setTarget(nearest);
        }
    }

    private ListenerEncounter() {}
}
