package uk.co.extraspecialstudio.dead_air.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage;
import uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Public API for Dead Air mod.
 * Allows other mods and data packs to register custom stations and music tracks.
 */
@SuppressWarnings("null")
public class DeadAirAPI {

    private static final CopyOnWriteArrayList<RegisteredRadioGuiOpener> RADIO_GUI_OPENERS =
        new CopyOnWriteArrayList<>();

    /**
     * Register a client-side radio GUI opener for companion radios that are not
     * {@link uk.co.extraspecialstudio.dead_air.item.RadioItem} subclasses.
     * Call from the companion's client setup when Dead Air is present.
     */
    public static void registerRadioGuiOpener(Predicate<ItemStack> match, RadioGuiOpener opener) {
        if (match == null || opener == null) {
            return;
        }
        RADIO_GUI_OPENERS.add(new RegisteredRadioGuiOpener(match, opener));
    }

    /**
     * @deprecated Use {@link #registerRadioGuiOpener(Predicate, RadioGuiOpener)}.
     */
    @Deprecated
    public static void registerWalkieGuiOpener(Predicate<ItemStack> match, RadioGuiOpener opener) {
        registerRadioGuiOpener(match, opener);
    }

    /**
     * Try companion openers for the held radio. Used by Dead Air's tune keybind.
     * @return true if a companion handled the open
     */
    public static boolean tryOpenCompanionRadioGui(InteractionHand hand, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (RegisteredRadioGuiOpener entry : RADIO_GUI_OPENERS) {
            if (entry.match.test(stack) && entry.opener.tryOpen(hand, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @deprecated Use {@link #tryOpenCompanionRadioGui(InteractionHand, ItemStack)}.
     */
    @Deprecated
    public static boolean tryOpenCompanionWalkieGui(InteractionHand hand, ItemStack stack) {
        return tryOpenCompanionRadioGui(hand, stack);
    }

    private record RegisteredRadioGuiOpener(Predicate<ItemStack> match, RadioGuiOpener opener) {}
    
    /**
     * Register a custom radio station.
     * Can be called from mod initialization or data pack loading.
     * 
     * @param station The radio station to register
     * @return true if registration was successful, false if a station with this ID already exists
     */
    public static boolean registerStation(RadioStation station) {
        if (StationRegistry.getStation(station.getId()) != null) {
            return false; // Station already exists
        }
        StationRegistry.registerStation(station);
        return true;
    }
    
    /**
     * Register a music track to an existing station.
     * If the station doesn't exist, it will be created automatically.
     * 
     * @param stationId The ID of the station to add the track to
     * @param trackId The resource location of the sound event to play
     * @return true if the track was added successfully
     */
    public static boolean addTrackToStation(ResourceLocation stationId, ResourceLocation trackId) {
        RadioStation station = StationRegistry.getStation(stationId);
        
        // If station doesn't exist, create a default one
        if (station == null) {
            station = new RadioStation(
                stationId,
                stationId.getPath().replace("_", " "), // Auto-generate name from ID
                RadioStation.StationType.MUSIC,
                "Custom",
                generateFrequency(stationId),
                375, // Default range (5/5 within 75, 4/5 at 150, 3/5 at 225, 2/5 at 300, 1/5 at 375, 0/5 beyond)
                400    // Default spacing
            );
            StationRegistry.registerStation(station);
        }

        // Add track to the station
        List<ResourceLocation> tracks = MusicStationManager.getTracksForStation(stationId);
        if (!tracks.contains(trackId)) {
            // Access the internal track map (we'll need to make this public or add a method)
            // For now, we'll need to add a method to MusicStationManager
            return MusicStationManager.addTrack(stationId, trackId);
        }
        
        return true;
    }
    
    /**
     * Create and register a custom music station with tracks.
     * 
     * @param stationId Unique ID for the station
     * @param name Display name of the station
     * @param genre Genre/category of the station
     * @param frequency Radio frequency (88.0 - 108.0 MHz)
     * @param tracks List of sound event resource locations to play
     * @return The created station, or null if a station with this ID already exists
     */
    public static RadioStation createMusicStation(ResourceLocation stationId, String name, 
                                                  String genre, float frequency, 
                                                  List<ResourceLocation> tracks) {
        // Check if station already exists
        if (StationRegistry.getStation(stationId) != null) {
            return null;
        }
        
        // Create station
        RadioStation station = new RadioStation(
            stationId,
            name,
            RadioStation.StationType.MUSIC,
            genre,
            frequency,
            375, // Default range (5/5 within 75, 4/5 at 150, 3/5 at 225, 2/5 at 300, 1/5 at 375, 0/5 beyond)
            400    // Default spacing
        );
        
        // Register station
        StationRegistry.registerStation(station);
        
        // Add tracks
        for (ResourceLocation trackId : tracks) {
            MusicStationManager.addTrack(stationId, trackId);
        }
        
        return station;
    }
    
    /**
     * Create and register a custom lore station.
     * 
     * @param stationId Unique ID for the station
     * @param name Display name of the station
     * @param genre Genre/category of the station
     * @param frequency Radio frequency (88.0 - 108.0 MHz)
     * @param tracks List of sound event resource locations (story recordings)
     * @return The created station, or null if a station with this ID already exists
     */
    public static RadioStation createLoreStation(ResourceLocation stationId, String name,
                                                String genre, float frequency,
                                                List<ResourceLocation> tracks) {
        if (StationRegistry.getStation(stationId) != null) {
            return null;
        }
        
        RadioStation station = new RadioStation(
            stationId,
            name,
            RadioStation.StationType.LORE,
            genre,
            frequency,
            1200, // Slightly shorter range for lore stations
            350    // Closer spacing
        );
        
        StationRegistry.registerStation(station);
        
        for (ResourceLocation trackId : tracks) {
            MusicStationManager.addTrack(stationId, trackId);
        }
        
        return station;
    }
    
    /**
     * Get all registered stations.
     * 
     * @return Unmodifiable collection of all stations
     */
    public static java.util.Collection<RadioStation> getAllStations() {
        return StationRegistry.getAllStations();
    }
    
    /**
     * Get a station by ID.
     * 
     * @param stationId The station ID
     * @return The station, or null if not found
     */
    public static RadioStation getStation(ResourceLocation stationId) {
        return StationRegistry.getStation(stationId);
    }
    
    /**
     * Get all tracks for a station.
     * 
     * @param stationId The station ID
     * @return List of track resource locations
     */
    public static List<ResourceLocation> getStationTracks(ResourceLocation stationId) {
        return MusicStationManager.getTracksForStation(stationId);
    }

    /**
     * Activate a radio panel at the given position so it broadcasts a station (turn on radio station).
     * Call this from other mods (e.g. RadioTowers) when the player uses a "Turn on radio station" button in a GUI.
     * Must be called on the server; no-op if level is client or null.
     *
     * @param level Server level containing the panel
     * @param pos   Block position of the radio panel
     */
    public static void activatePanelAt(ServerLevel level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        RadioPanelManager.activatePanel(level, pos);
    }

    /**
     * Activate a radio panel at the given position and set which station it broadcasts.
     * Use from other mods (e.g. RadioTowers) when the player chooses a station in the panel GUI.
     * If the panel already has a tower registered, its station is updated; otherwise a player tower is registered.
     * Must be called on the server; no-op if level is client or null or stationId is invalid.
     *
     * @param level     Server level containing the panel
     * @param pos       Block position of the radio panel
     * @param stationId Station to broadcast (e.g. dead_air:emergency_broadcast)
     */
    public static void activatePanelAt(ServerLevel level, BlockPos pos, ResourceLocation stationId) {
        if (level == null || level.isClientSide() || stationId == null) return;
        if (StationRegistry.JUKEBOX_FM_ID.equals(stationId)) {
            if (!TowerModuleStorage.get(level).getCapabilities(pos).isJukeboxModuleInstalled()) {
                return;
            }
        }
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return;
        RadioPanelManager.activatePanel(level, pos);
        uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType towerType = uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.UNKNOWN;
        uk.co.extraspecialstudio.dead_air.radio.RadioTower existing = TowerManager.findTowerForPanel(level, pos);
        BlockPos registerPos = pos;
        if (existing != null) {
            towerType = existing.getTowerType();
            registerPos = existing.getPosition();
            TowerManager.removeTowerForPanel(level, pos);
        }
        if (towerType != uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.UNKNOWN) {
            TowerManager.registerTower(level, registerPos, station, towerType);
        } else {
            TowerManager.registerPlayerTower(level, pos, station);
        }
        uk.co.extraspecialstudio.dead_air.radio.RadioTower reg = TowerManager.findTowerForPanel(level, pos);
        if (reg != null && reg.getRadioPanelPos() != null) {
            boolean powered = RadioPanelManager.isPanelActivated(level, reg.getRadioPanelPos());
            reg.setPowered(powered || reg.getTowerType() == uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.STANDARD);
        }
        BlockPos towerPosForStorage = reg != null ? reg.getPosition() : pos;
        uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.addKnownTower(level, towerPosForStorage, pos, stationId);
    }

    /**
     * Same as {@link #activatePanelAt(ServerLevel, BlockPos, ResourceLocation)} but also:
     * - If the player is in broadcast range and has not yet unlocked the new station, they get "New station discovered!".
     * - <strong>Optional radio follow:</strong> if the player is actively set up to hear radio ({@link RadioManager#shouldPlayRadio},
     * radio on) and their tuned station matches what <em>this panel was already broadcasting</em> before the change,
     * they are retuned to the new station so the same tower does not leave them on a dead frequency. No retune if they have
     * no radio, radio off, or were listening to a different station (panel change does not hijack unrelated tuning).
     */
    public static void activatePanelAt(ServerLevel level, BlockPos pos, ResourceLocation stationId, ServerPlayer triggerPlayer) {
        if (level != null && !level.isClientSide() && StationRegistry.JUKEBOX_FM_ID.equals(stationId)
            && !TowerModuleStorage.get(level).getCapabilities(pos).isJukeboxModuleInstalled()) {
            return;
        }
        ResourceLocation oldStationId = KnownTowerStorage.getStationForPanel(level, pos);
        activatePanelAt(level, pos, stationId);
        if (triggerPlayer == null) return;
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return;

        if (RadioManager.shouldPlayRadio(triggerPlayer)) {
            RadioManager.RadioState st = RadioManager.getState(triggerPlayer);
            RadioStation tuned = st.getCurrentStation();
            if (st.isOn() && tuned != null && oldStationId != null && oldStationId.equals(tuned.getId())) {
                RadioManager.tuneToStation(triggerPlayer, station);
            }
        }

        // Discover: in range and not yet unlocked -> unlock and chat message (Jukebox FM is upgrade-only)
        if (!StationRegistry.JUKEBOX_FM_ID.equals(station.getId())) {
            double distSq = triggerPlayer.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            int range = station.getBroadcastRange();
            if (distSq <= (double) (range * range) && !StationUnlockManager.hasUnlocked(triggerPlayer, station)) {
                StationUnlockManager.unlockStation(triggerPlayer, station);
                triggerPlayer.sendSystemMessage(
                    Component.literal("[Radio] ").withStyle(net.minecraft.ChatFormatting.GOLD)
                        .append(Component.translatable("message.dead_air.radio.new_station_discovered").withStyle(net.minecraft.ChatFormatting.WHITE))
                );
            }
        }
    }

    /**
     * Deactivate a radio panel at the given position (turn off broadcasting from this panel).
     * Call from other mods (e.g. RadioTowers) when the player uses a "Turn off" button in the panel GUI.
     * Must be called on the server; no-op if level is client or null.
     *
     * @param level Server level containing the panel
     * @param pos   Block position of the radio panel
     */
    public static void deactivatePanelAt(ServerLevel level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        TowerManager.removeTower(level, pos);
        RadioPanelManager.deactivatePanel(level, pos);
        KnownTowerStorage.deactivateToDetected(level, pos);
    }

    /**
     * Resync known towers to a player (e.g. after turning off a panel from another mod's GUI).
     * Call from the mod that sent deactivatePanelAt so the client's tower list updates.
     */
    public static void syncKnownTowersToPlayer(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        uk.co.extraspecialstudio.dead_air.events.ModEvents.syncKnownTowersToPlayer(player);
    }

    /**
     * Tune the player's radio to the Emergency Broadcast station (e.g. when calling an airdrop).
     * Call from other mods on the server after the player triggers an airdrop so they are on the tower's emergency frequency.
     */
    public static void tunePlayerToEmergencyBroadcast(ServerPlayer player) {
        if (player == null) return;
        RadioStation emergency = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
        if (emergency != null) {
            uk.co.extraspecialstudio.dead_air.radio.RadioManager.tuneToStation(player, emergency);
        }
    }

    /**
     * Returns block positions of all active (powered) radio towers in the level.
     * Used by other mods (e.g. RadioTowers) for zombie attraction to towers when Dead Air is present.
     */
    public static java.util.List<BlockPos> getActiveTowerBlockPositions(ServerLevel level) {
        if (level == null || level.isClientSide()) return java.util.Collections.emptyList();
        var all = TowerManager.getTowersInRange(level, net.minecraft.world.phys.Vec3.ZERO);
        return all.stream().filter(RadioTower::isPowered).map(RadioTower::getPosition).map(BlockPos::immutable).toList();
    }

    /**
     * Returns true if the player has their radio on and tuned to a music station (not Emergency Broadcast).
     * Used by other mods for zombie attraction to players playing music.
     */
    public static boolean isPlayerPlayingMusic(net.minecraft.world.entity.player.Player player) {
        if (player == null) return false;
        var state = RadioManager.getState(player);
        if (!state.isOn() || state.getCurrentStation() == null) return false;
        RadioStation station = state.getCurrentStation();
        return station.getType() == RadioStation.StationType.MUSIC;
    }
    
    // ---------- Dead Air 3.0: broadcasts and The Listener ----------

    /**
     * Register a story, survivor, trader, or defense broadcast. Call during common setup.
     * <p>
     * Expansion / soundtrack pack authors: implement {@link uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEvent}
     * (pick a {@link uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEvent.Category}, gate with
     * {@code canTrigger}, return transmission lines from {@code start}) and register here.
     * Use a low {@code priority()} (negative) so Listener story progression is not blocked.
     * Soft-detect your pack with {@code ModList.get().isLoaded("your_mod_id")} — no hard dep required.
     * Built-in example: {@code dead_air:pirate_fm_interruption} when a known FM pack is installed.
     * Debug: {@code /deadair listener broadcast <id>} or {@code /deadair listener piratefm}.
     */
    public static void registerBroadcastEvent(uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEvent event) {
        uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEventRegistry.register(event);
    }

    /**
     * Deliver a registered broadcast to this player now (same path as night scheduler / debug force).
     * Returns false if the event cancelled itself (empty lines).
     */
    public static boolean sendBroadcast(ServerPlayer player, ResourceLocation eventId) {
        var event = uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEventRegistry.get(eventId);
        return player != null && event != null
            && uk.co.extraspecialstudio.dead_air.broadcast.BroadcastScheduler.deliver(player, event);
    }

    /** All registered broadcast event ids (for pack tooling / debug UIs). */
    public static java.util.Collection<ResourceLocation> listBroadcastEvents() {
        java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
        for (var e : uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEventRegistry.all()) {
            ids.add(e.id());
        }
        return ids;
    }

    /**
     * Claim the radio panel at {@code panelPos} as this player's base.
     * Used by RadioTowers Base tab (soft-compat).
     */
    public static boolean setPlayerBase(ServerPlayer player, BlockPos panelPos) {
        return player != null && panelPos != null
            && uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager.setBase(player, panelPos);
    }

    /** Clear the player's radio-panel base. */
    public static boolean unsetPlayerBase(ServerPlayer player) {
        return player != null
            && uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager.unsetBase(player);
    }

    /**
     * GUI info for the RadioTowers Base tab.
     * @return {@code Object[]{ statusLine (String), isThisPanel (Boolean) }}
     */
    public static Object[] getPlayerBaseGuiInfo(ServerPlayer player, BlockPos panelPos) {
        if (player == null) {
            return new Object[]{"No player", false};
        }
        var base = uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager.getBase(player.server, player.getUUID());
        if (base == null) {
            return new Object[]{"No base set. Claim this panel to make it your home.", false};
        }
        var level = player.server.getLevel(base.dimension);
        int range = level != null
            ? uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager.signalRange(level, base.panelPos)
            : -1;
        boolean isThis = panelPos != null
            && base.dimension.equals(player.level().dimension())
            && base.panelPos.equals(panelPos);
        String line = "Base @ " + base.panelPos.toShortString()
            + " (" + base.dimension.location() + "), range " + range
            + (isThis ? " — this panel" : " — different panel");
        return new Object[]{line, isThis};
    }

    /** True once this player has survived the first encounter (Zombie Waves Boss Mode gate). */
    public static boolean isBossModeUnlocked(ServerPlayer player) {
        var data = uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress.get(player.server);
        return data != null && data.get(player.getUUID()).bossModeUnlocked;
    }

    /** True once this player has killed The Listener (Endless Waves gate). */
    public static boolean isListenerDefeated(ServerPlayer player) {
        var data = uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress.get(player.server);
        return data != null && data.get(player.getUUID()).finalDefeated;
    }

    /**
     * Start the final, killable encounter next to this player (e.g. as the last Boss Mode boss).
     * Only Signal Breaker hits hurt him. Returns false if one is already running for the player.
     */
    public static boolean startListenerFinalEncounter(ServerPlayer player) {
        return player != null && uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter.start(
            player, uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter.Mode.FINAL) != null;
    }

    /**
     * Generate a unique frequency for a station ID.
     */
    private static float generateFrequency(ResourceLocation stationId) {
        int hash = stationId.hashCode();
        float frequency = 88.0f + (Math.abs(hash % 200) / 10.0f);
        return Math.round(frequency * 10.0f) / 10.0f;
    }
}
