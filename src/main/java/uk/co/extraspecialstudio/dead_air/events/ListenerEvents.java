package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastScheduler;
import uk.co.extraspecialstudio.dead_air.broadcast.EmergencyProximity;
import uk.co.extraspecialstudio.dead_air.broadcast.PendingSites;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirBaseCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirListenerCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirCompatCommand;
import uk.co.extraspecialstudio.dead_air.commands.DeadAirSurvivorCommand;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;
import uk.co.extraspecialstudio.dead_air.radio.RadioNbt;

/** Server hooks for broadcasts, relay/camp placement and The Listener. */
@EventBusSubscriber(modid = Dead_air.MODID)
public final class ListenerEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null || !server.isRunning()) return;
        try {
            ListenerEncounter.tick(server);
            BroadcastScheduler.tick(server);
            PendingSites.tick(server);
            EmergencyProximity.tick(server);
            uk.co.extraspecialstudio.dead_air.broadcast.BaseAttackManager.tick(server);
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
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ListenerEncounter.onPlayerGone(player);
            EmergencyProximity.clearPending(player.getUUID());
        }
    }

    /**
     * Dim change is not death/logout — encounter tick already drops {@code p.level() != enc.level}
     * and ends empty fights as FAILED (no defeat credit). Clear any wild Listener targeting them.
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Encounter participants are handled every tick via level mismatch; nothing extra here.
        // Wild Listeners targeting this player despawn in ListenerEntity.dropCrossDimensionTarget.
        EmergencyProximity.clearPending(player.getUUID());
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
    }

    /**
     * Shaped T3 craft consumes a T2 â€” copy tune/link NBT onto the result (mirrors SignalDecoderItem use).
     */
    @SubscribeEvent
    public static void onRadioT3Crafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!(result.getItem() instanceof RadioItem out) || out.getTier() != RadioItem.Tier.T3) return;
        var craftInv = event.getInventory();
        for (int i = 0; i < craftInv.getContainerSize(); i++) {
            ItemStack slot = craftInv.getItem(i);
            if (slot.getItem() instanceof RadioItem w && w.getTier() == RadioItem.Tier.T2) {
                CompoundTag tag = RadioNbt.get(slot);
                if (tag != null && !tag.isEmpty()) {
                    RadioNbt.set(result, tag.copy());
                }
                return;
            }
        }
    }

    private ListenerEvents() {}
}
