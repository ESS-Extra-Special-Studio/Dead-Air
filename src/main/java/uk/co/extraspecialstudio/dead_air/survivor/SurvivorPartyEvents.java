package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.SurvivorPartySyncPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Dead_air.MODID)
public final class SurvivorPartyEvents {
    /** Players who last received a non-empty party sync (so we can send one empty clear). */
    private static final Set<UUID> HAD_PARTY = new HashSet<>();

    private SurvivorPartyEvents() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        HAD_PARTY.remove(id);
        try {
            SurvivorPartyOrders.clearPlayer(id);
        } catch (LinkageError | Exception e) {
            // ModuleClassLoader can CNF SurvivorPartyOrders on logout — never crash the server.
            Dead_air.LOGGER.error("Dead Air: party clear on logout failed", e);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        if (!Config.survivorsEnabled) {
            // Clear any lingering party HUD if survivors were disabled mid-session.
            if (!HAD_PARTY.isEmpty()) {
                int max = Config.maxPartySize;
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    if (HAD_PARTY.remove(player.getUUID())) {
                        DeadAirNet.sendTo(player, new SurvivorPartySyncPacket(max, 0, List.of()));
                    }
                }
                HAD_PARTY.clear();
            }
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            syncPlayerNow(player);
        }
    }

    /** Push current party snapshot to one player (periodic tick + post-order). */
    public static void syncPlayerNow(ServerPlayer player) {
        if (player == null || player.server == null) return;
        if (!Config.survivorsEnabled) return;
        List<SurvivorEntity> party = SurvivorParty.listFollowing(player.server, player.getUUID());
        int partyCount = SurvivorParty.countFollowing(player.server, player.getUUID());
        List<SurvivorPartySyncPacket.Member> members = new ArrayList<>(party.size());
        for (SurvivorEntity s : party) {
            SurvivorIdentity id = s.getIdentity();
            String name = id != null && id.name != null ? id.name : s.getName().getString();
            String profession = s.getProfession() != null ? s.getProfession().id : "scavenger";
            int dist = s.level() == player.level()
                ? (int) Math.round(Math.sqrt(s.distanceToSqr(player)))
                : 999;
            int level = id != null ? id.level : 1;
            int xp = id != null ? id.xp : 0;
            members.add(new SurvivorPartySyncPacket.Member(
                s.getId(),
                name,
                profession,
                s.getHealth(),
                s.getMaxHealth(),
                s.isDowned(),
                dist,
                id != null ? id.gender.ordinal() : 0,
                id != null ? id.skinVariant : 0,
                level,
                xp,
                SurvivorProgress.xpToNextLevel(level),
                id != null ? id.skillScavenge : 0,
                id != null ? id.skillMedicine : 0,
                id != null ? id.skillRadio : 0,
                id != null ? id.skillCombat : 0,
                s.getPartyOrder()));
        }
        UUID id = player.getUUID();
        int max = Config.maxPartySize;
        if (partyCount <= 0 && members.isEmpty()) {
            if (!HAD_PARTY.remove(id)) return;
            DeadAirNet.sendTo(player, new SurvivorPartySyncPacket(max, 0, List.of()));
            return;
        }
        HAD_PARTY.add(id);
        DeadAirNet.sendTo(player, new SurvivorPartySyncPacket(max, partyCount, members));
    }
}
