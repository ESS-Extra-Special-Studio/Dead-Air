package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastScheduler;
import uk.co.extraspecialstudio.dead_air.broadcast.EbHuntTracker;
import uk.co.extraspecialstudio.dead_air.broadcast.EmergencyProximity;
import uk.co.extraspecialstudio.dead_air.broadcast.ExploreDiscovery;
import uk.co.extraspecialstudio.dead_air.broadcast.PendingSites;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirBaseCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirCompatCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirListenerCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirStructuresCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirSurvivorCommand;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;

/** Server hooks for broadcasts, relay/camp placement and The Listener. */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
public final class ListenerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server == null || !server.isRunning()) return;
        try {
            ListenerEncounter.tick(server);
            BroadcastScheduler.tick(server);
            ExploreDiscovery.tick(server);
            PendingSites.tick(server);
            EmergencyProximity.tick(server);
            EbHuntTracker.tick(server);
            uk.co.extraspecialstudio.dead_air.broadcast.BaseAttackManager.tick(server);
            uk.co.extraspecialstudio.dead_air.broadcast.ExtraBroadcasts.reconcileMissingRecruits(server);
            uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager.validateBases(server);
            uk.co.extraspecialstudio.dead_air.survivor.AuctionTraderSpawner.tick(server.overworld());
        } catch (Exception e) {
            Dead_air.LOGGER.error("[Dead Air] Broadcast/encounter tick failed", e);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ListenerEncounter.onPlayerGone(player);
            EmergencyProximity.clearPending(player.getUUID());
            ExploreDiscovery.clearPlayer(player.getUUID());
            EbHuntTracker.clearPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ListenerEncounter.onPlayerGone(player);
            EmergencyProximity.clearPending(player.getUUID());
            ExploreDiscovery.clearPlayer(player.getUUID());
            EbHuntTracker.clearPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ListenerEncounter.stopAll();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DeadAirListenerCommand.register(event.getDispatcher());
        DeadAirBaseCommand.register(event.getDispatcher());
        DeadAirSurvivorCommand.register(event.getDispatcher());
        DeadAirCompatCommand.register(event.getDispatcher());
        DeadAirStructuresCommand.register(event.getDispatcher());
    }

    /**
     * Shaped T3 craft consumes a T2 — copy tune/link NBT onto the result (mirrors SignalDecoderItem use).
     * Decoder-in-hand upgrade remains the canonical one-step path; this keeps craft from wiping presets.
     */
    @SubscribeEvent
    public static void onRadioT3Crafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!(result.getItem() instanceof RadioItem out) || out.getTier() != RadioItem.Tier.T3) return;
        var craftInv = event.getInventory();
        for (int i = 0; i < craftInv.getContainerSize(); i++) {
            ItemStack slot = craftInv.getItem(i);
            if (slot.getItem() instanceof RadioItem w && w.getTier() == RadioItem.Tier.T2) {
                CompoundTag tag = slot.getTag();
                if (tag != null && !tag.isEmpty()) {
                    result.setTag(tag.copy());
                }
                return;
            }
        }
    }

    private ListenerEvents() {}
}
