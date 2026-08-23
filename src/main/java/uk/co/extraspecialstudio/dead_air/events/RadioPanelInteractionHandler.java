package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage;
import uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerCapabilities;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;

/**
 * Handles player interactions with Radio Panels from RadioTowers mod.
 * Once a panel is clicked by any player, we store it and it stays active (we override the power system).
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
@SuppressWarnings("null")
public class RadioPanelInteractionHandler {
    
    /**
     * Handle right-click on Radio Panel blocks. Runs at HIGHEST so we record the click before
     * any other mod consumes the event. One click = panel stays active (persisted to backup).
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!ApocalypseTowerDetector.isRadioPanel(event.getLevel(), pos)) return;

            if (event.getLevel().isClientSide()) {
                if (getHeldUpgrade(event.getEntity()) != null) {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                }
                return;
            }
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                HeldUpgrade held = getHeldUpgrade(serverPlayer);
                if (held != null) {
                    ItemStack upgradeStack = serverPlayer.getItemInHand(held.hand);
                    TowerCapabilities caps = TowerModuleStorage.get(level).getCapabilities(pos);
                    if (held.type == UpgradeType.JUKEBOX) {
                        if (!caps.isJukeboxModuleInstalled()) {
                            caps.setJukeboxModuleInstalled(true);
                            TowerManager.saveCapabilities(level, pos, caps);
                            if (!serverPlayer.getAbilities().instabuild) {
                                upgradeStack.shrink(1);
                            }
                            RadioTower towerForRange = findTowerAtPanel(level, pos);
                            Vec3 origin = towerForRange != null ? towerForRange.getPositionVec() : Vec3.atCenterOf(pos);
                            double rangeBlocks = towerForRange != null
                                ? towerForRange.getStation().getBroadcastRange()
                                : (double) Config.musicStationRange;
                            RadioStation jukeboxStation = StationRegistry.getStation(StationRegistry.JUKEBOX_FM_ID);
                            if (jukeboxStation != null) {
                                for (ServerPlayer p : level.players()) {
                                    if (p.position().distanceTo(origin) > rangeBlocks) {
                                        continue;
                                    }
                                    if (!StationUnlockManager.hasUnlocked(p, jukeboxStation)) {
                                        StationUnlockManager.unlockStation(p, jukeboxStation);
                                        p.sendSystemMessage(
                                            Component.literal("[Radio] ").withStyle(ChatFormatting.GOLD)
                                                .append(Component.translatable("message.dead_air.radio.new_station_discovered").withStyle(ChatFormatting.WHITE))
                                        );
                                    }
                                }
                            }
                            for (ServerPlayer p : level.players()) {
                                ModEvents.syncKnownTowersToPlayer(p);
                            }
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            event.setCanceled(true);
                            return;
                        }
                        serverPlayer.sendSystemMessage(
                            Component.literal("[Radio] ").withStyle(ChatFormatting.GOLD)
                                .append(Component.translatable("message.dead_air.radio.jukebox_upgrade_already_installed").withStyle(ChatFormatting.WHITE))
                        );
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
                        return;
                    }
                    if (held.type == UpgradeType.SIGNAL) {
                        if (!caps.isSignalBoostInstalled()) {
                            caps.setSignalBoostInstalled(true);
                            TowerManager.saveCapabilities(level, pos, caps);
                            if (!serverPlayer.getAbilities().instabuild) {
                                upgradeStack.shrink(1);
                            }
                            serverPlayer.sendSystemMessage(
                                Component.literal("[Radio] ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.translatable("message.dead_air.radio.signal_upgrade_installed").withStyle(ChatFormatting.WHITE))
                            );
                            for (ServerPlayer p : level.players()) {
                                ModEvents.syncKnownTowersToPlayer(p);
                            }
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            event.setCanceled(true);
                            return;
                        }
                        serverPlayer.sendSystemMessage(
                            Component.literal("[Radio] ").withStyle(ChatFormatting.GOLD)
                                .append(Component.translatable("message.dead_air.radio.signal_upgrade_already_installed").withStyle(ChatFormatting.WHITE))
                        );
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
                        return;
                    }
                }
            }

            // Check if panel is already known (locked to a station) - never re-assign
            net.minecraft.resources.ResourceLocation existingStation = KnownTowerStorage.getStationForPanel(level, pos);
            if (existingStation != null) {
                // Panel already activated and locked to a station - only update power, do not register again
                safeActivatePanel(level, pos);
                RadioTower tower = findTowerAtPanel(level, pos);
                if (tower == null) {
                    // Tower not in TowerManager but we have it in KnownTowerStorage - restore it
                    uk.co.extraspecialstudio.dead_air.radio.RadioStation station =
                        uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getStation(existingStation);
                    if (station != null) {
                        ApocalypseTowerType towerType = ApocalypseTowerDetector.getTowerType(level, pos);
                        if (towerType != ApocalypseTowerType.UNKNOWN) {
                            TowerManager.registerTower(level, pos, station, towerType);
                        } else {
                            TowerManager.registerPlayerTower(level, pos, station);
                        }
                        tower = findTowerAtPanel(level, pos);
                    }
                }
                if (tower != null) {
                    updateTowerPowerImmediately(level, tower);
                    if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                        ModEvents.syncKnownTowersToPlayer(serverPlayer);
                    }
                    if (tower.isPowered()) {
                        triggerStationDiscovery(level, tower, event.getEntity());
                    }
                }
                return; // Done - panel locked, no new registration
            }

            // First-time activation - register tower and assign station
            safeActivatePanel(level, pos);
            RadioTower tower = findTowerAtPanel(level, pos);

            if (tower == null) {
                ApocalypseTowerType towerType = ApocalypseTowerDetector.getTowerType(level, pos);
                uk.co.extraspecialstudio.dead_air.radio.RadioStation station;

                if (towerType != ApocalypseTowerType.UNKNOWN) {
                    station = TowerManager.determineStationForTower(level, pos, towerType);
                    if (station != null) {
                        TowerManager.registerTower(level, pos, station, towerType);
                        tower = findTowerAtPanel(level, pos);
                    }
                } else {
                    station = uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getStation(
                        uk.co.extraspecialstudio.dead_air.radio.StationRegistry.EMERGENCY_BROADCAST_ID);
                    if (station != null) {
                        TowerManager.registerPlayerTower(level, pos, station);
                        tower = findTowerAtPanel(level, pos);
                    }
                }
            }

            if (tower != null) {
                // Persist as "known tower" so tuning to this station in GUI works without chunk scan
                BlockPos panelPos = tower.getRadioPanelPos() != null ? tower.getRadioPanelPos() : pos;
                KnownTowerStorage.addKnownTower(level, tower.getPosition(), panelPos, tower.getStation().getId());
                if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                    ModEvents.syncKnownTowersToPlayer(serverPlayer);
                }
                // Immediately update tower power state based on panel activation
                updateTowerPowerImmediately(level, tower);
                if (tower.isPowered()) {
                    triggerStationDiscovery(level, tower, event.getEntity());
                }
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.error("[Dead Air] Error handling radio panel right-click at {}: {}", event.getPos(), t.getMessage(), t);
        }
    }
    
    /** Call RadioPanelManager.activatePanel; no-op if class not available (avoids NoClassDefFoundError on some setups). */
    private static void safeActivatePanel(ServerLevel level, BlockPos pos) {
        try {
            RadioPanelManager.activatePanel(level, pos);
        } catch (LinkageError | Exception e) {
            Dead_air.LOGGER.warn("Dead Air: could not activate panel at {} (tower will still register): {}", pos, e.toString());
        }
    }

    /** Call RadioPanelManager.isPanelActivated; returns true if class not available so towers stay powered. */
    private static boolean safeIsPanelActivated(ServerLevel level, BlockPos pos) {
        try {
            return RadioPanelManager.isPanelActivated(level, pos);
        } catch (LinkageError | Exception e) {
            return true;
        }
    }

    /**
     * Find the tower associated with a Radio Panel position.
     */
    private static RadioTower findTowerAtPanel(ServerLevel level, BlockPos panelPos) {
        return TowerManager.findTowerForPanel(level, panelPos);
    }

    private enum UpgradeType { JUKEBOX, SIGNAL }

    private static final class HeldUpgrade {
        final InteractionHand hand;
        final UpgradeType type;

        HeldUpgrade(InteractionHand hand, UpgradeType type) {
            this.hand = hand;
            this.type = type;
        }
    }

    private static HeldUpgrade getHeldUpgrade(net.minecraft.world.entity.player.Player player) {
        if (player == null) return null;
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!mainHand.isEmpty() && mainHand.is(DeadAirItems.JUKEBOX_UPGRADE.get())) {
            return new HeldUpgrade(InteractionHand.MAIN_HAND, UpgradeType.JUKEBOX);
        }
        if (!mainHand.isEmpty() && mainHand.is(DeadAirItems.SIGNAL_UPGRADE.get())) {
            return new HeldUpgrade(InteractionHand.MAIN_HAND, UpgradeType.SIGNAL);
        }
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHand.isEmpty() && offHand.is(DeadAirItems.JUKEBOX_UPGRADE.get())) {
            return new HeldUpgrade(InteractionHand.OFF_HAND, UpgradeType.JUKEBOX);
        }
        if (!offHand.isEmpty() && offHand.is(DeadAirItems.SIGNAL_UPGRADE.get())) {
            return new HeldUpgrade(InteractionHand.OFF_HAND, UpgradeType.SIGNAL);
        }
        return null;
    }
    
    /**
     * Immediately update tower power state based on Radio Panel activation.
     * This is called right after panel activation to ensure immediate discovery.
     */
    private static void updateTowerPowerImmediately(ServerLevel level, RadioTower tower) {
        boolean powered = false;
        
        switch (tower.getTowerType()) {
            case STANDARD:
                // Standard towers always broadcast
                powered = true;
                break;
                
            case FENCED:
                // Fenced towers: check if Radio Panel is activated OR if it started powered
                if (tower.getRadioPanelPos() != null) {
                    boolean panelActivated = safeIsPanelActivated(level, tower.getRadioPanelPos());
                    powered = panelActivated;
                    // If panel is not activated but tower is currently powered, it must have started powered
                    if (!powered && tower.isPowered()) {
                        powered = true; // Keep it on if it started powered
                    }
                } else {
                    // No panel found, but tower exists - assume it started powered if currently powered
                    powered = tower.isPowered();
                }
                break;
                
            case OVERRUN:
                // Overrun towers: only on if Radio Panel is activated
                if (tower.getRadioPanelPos() != null) {
                    powered = safeIsPanelActivated(level, tower.getRadioPanelPos());
                }
                break;
                
            default:
                // Player-built towers: only on if Radio Panel is activated
                if (tower.getRadioPanelPos() != null) {
                    powered = safeIsPanelActivated(level, tower.getRadioPanelPos());
                }
                break;
        }
        
        tower.setPowered(powered);
    }
    
    /**
     * Trigger station discovery for players near the activated tower.
     */
    private static void triggerStationDiscovery(ServerLevel level, RadioTower tower, net.minecraft.world.entity.player.Player activatingPlayer) {
        if (!tower.isPowered()) {
            return;
        }
        if (tower.getStation().getId().equals(StationRegistry.JUKEBOX_FM_ID)) {
            return;
        }

        // Check all players in range of the tower
        var players = level.players();
        for (net.minecraft.world.entity.player.Player player : players) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                // Check if player is in range and has walkie-talkie
                double distance = player.position().distanceTo(tower.getPositionVec());
                if (distance <= tower.getStation().getBroadcastRange()) {
                    // Check if player has unlocked this station
                    boolean isUnlocked = StationUnlockManager.hasUnlocked(serverPlayer, tower.getStation());
                    
                    if (!isUnlocked) {
                        // Unlock the station
                        StationUnlockManager.unlockStation(serverPlayer, tower.getStation());
                        
                        // Send discovery message (GUI shows station details)
                        serverPlayer.sendSystemMessage(Component.literal("§6[Radio] §rNew station discovered!"));
                    }
                }
            }
        }
    }
}
