package uk.co.extraspecialstudio.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.item.DeadAirTags;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages radio state for players (Dead Air T1/T2 items).
 */
@SuppressWarnings({"null", "deprecation"})
public class RadioManager {
    private static final Map<UUID, RadioState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, RadioStation> LAST_TUNED_STATION = new ConcurrentHashMap<>();

    private static final String NBT_STATION = "DeadAirStation";
    private static final String NBT_ON = "DeadAirOn";
    private static final String NBT_LINK_MODE = "DeadAirLinkMode";
    private static final String NBT_LINK_DIM = "DeadAirLinkDim";
    private static final String NBT_LINK_X = "DeadAirLinkX";
    private static final String NBT_LINK_Y = "DeadAirLinkY";
    private static final String NBT_LINK_Z = "DeadAirLinkZ";
    private static final String NBT_LINK_STATION = "DeadAirLinkStation";

    public enum LinkMode {
        LOCAL,
        LINKED
    }

    public static class RadioState {
        private RadioStation currentStation;
        private float frequency;
        private boolean isOn;

        public RadioState() {
            this.currentStation = null;
            this.frequency = 0.0f;
            this.isOn = true;
        }

        public RadioStation getCurrentStation() {
            return currentStation;
        }

        public void setCurrentStation(RadioStation station) {
            this.currentStation = station;
            if (station != null) {
                this.frequency = station.getFrequency();
            }
        }

        public float getFrequency() {
            return frequency;
        }

        public void setFrequency(float frequency) {
            this.frequency = frequency;
        }

        public boolean isOn() {
            return isOn;
        }

        public void setOn(boolean on) {
            this.isOn = on;
        }
    }

    public static void initialize() {
        // Dead Air radios are registered via DeadAirItems — no external scan needed.
    }

    public static boolean isRadioItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof RadioItem
            || stack.is(DeadAirItems.RADIO_T1.get())
            || stack.is(DeadAirItems.RADIO_T2.get())
            || stack.is(DeadAirItems.RADIO_T3.get())
            || stack.is(DeadAirTags.RADIOS)
            || stack.is(DeadAirTags.WALKIE_RADIOS);
    }

    public static boolean isT1(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof RadioItem radio) {
            return radio.getTier() == RadioItem.Tier.T1;
        }
        return stack.is(DeadAirTags.RADIO_T1)
            || stack.is(DeadAirTags.WALKIE_T1)
            || stack.is(DeadAirItems.RADIO_T1.get());
    }

    /** T2 or better: T3 keeps every T2 feature. */
    public static boolean isT2(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof RadioItem radio) {
            return radio.getTier() != RadioItem.Tier.T1;
        }
        return stack.is(DeadAirTags.RADIO_T2)
            || stack.is(DeadAirTags.WALKIE_T2)
            || stack.is(DeadAirTags.RADIO_T3)
            || stack.is(DeadAirItems.RADIO_T2.get())
            || stack.is(DeadAirItems.RADIO_T3.get());
    }

    public static boolean isT3(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RadioItem radio && radio.getTier() == RadioItem.Tier.T3;
    }

    public static boolean canLinkCrossDim(ItemStack stack) {
        if (stack.getItem() instanceof RadioItem radio) {
            return radio.isLinkCapable();
        }
        return isT2(stack);
    }

    /** Note-drop chance while listening (on + tuned): from the single active radio only — T1 15%, T2 30%. */
    public static float getStaticNoteDropChance(Player player) {
        ensureSingleActiveListening(player);
        ItemStack active = findListeningRadio(player);
        if (active.isEmpty()) return 0f;
        // Explicitly the active listening radio only — extra radios in inventory do not stack chance.
        return isT2(active) ? 0.30f : 0.15f;
    }

    /**
     * True when any radio on the player (hand, hotbar, or bag) is powered on and tuned to a station.
     * Independent of {@link #shouldPlayRadio} — drops must not require holding the radio.
     */
    public static boolean isListeningToRadio(Player player) {
        return !findListeningRadio(player).isEmpty();
    }

    /** Clear station tune from this radio (power / link NBT left alone). */
    public static void clearTune(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isRadioItem(stack)) return;
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(NBT_STATION);
    }

    /**
     * Only one radio may be tuned per player. Clears station NBT from every other radio
     * on this player so T1+T2 cannot both drive music.
     */
    public static void claimAsActiveRadio(Player player, ItemStack active) {
        if (player == null || active == null || active.isEmpty() || !isRadioItem(active)) return;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isRadioItem(main) && main != active) clearTune(main);
        if (isRadioItem(off) && off != active && off != main) clearTune(off);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isRadioItem(stack) || stack == active || stack == main || stack == off) continue;
            clearTune(stack);
        }
    }

    /**
     * If multiple radios are somehow on+tuned, keep hand preference (main → off → first inventory)
     * and untune the rest. Safe to call every tick.
     */
    public static void ensureSingleActiveListening(Player player) {
        if (player == null) return;
        ItemStack keep = ItemStack.EMPTY;
        int listeningCount = 0;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isRadioListening(main)) {
            keep = main;
            listeningCount++;
        }
        if (isRadioListening(off) && off != main) {
            if (keep.isEmpty()) keep = off;
            listeningCount++;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isRadioListening(stack) || stack == main || stack == off) continue;
            if (keep.isEmpty()) keep = stack;
            listeningCount++;
        }
        if (listeningCount > 1 && !keep.isEmpty()) {
            claimAsActiveRadio(player, keep);
        }
    }

    /**
     * Prefer a listening radio in hand (main, then offhand), otherwise any listening radio in inventory.
     * Empty if none are on+tuned. At most one listening radio after {@link #ensureSingleActiveListening}.
     */
    public static ItemStack findListeningRadio(Player player) {
        if (player == null) return ItemStack.EMPTY;
        ensureSingleActiveListening(player);
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isRadioListening(main)) return main;
        if (isRadioListening(off)) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isRadioListening(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Radio NBT: powered on (or legacy missing On flag) and tuned to a known station. */
    public static boolean isRadioListening(ItemStack stack) {
        if (stack.isEmpty() || !isRadioItem(stack)) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_STATION)) return false;
        String idStr = tag.getString(NBT_STATION);
        if (idStr == null || idStr.isEmpty()) return false;
        // Explicit power-off only; missing key = on (legacy stacks)
        if (tag.contains(NBT_ON) && !tag.getBoolean(NBT_ON)) return false;
        try {
            return StationRegistry.getStation(ResourceLocation.parse(idStr)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isHoldingRadio(Player player) {
        return isRadioItem(player.getMainHandItem()) || isRadioItem(player.getOffhandItem());
    }

    public static boolean hasRadioTunedToStationOnPlayer(Player player, ResourceLocation stationId) {
        if (player == null || stationId == null) return false;
        if (isRadioItem(player.getMainHandItem()) && isRadioTunedToStation(player.getMainHandItem(), stationId)) return true;
        if (isRadioItem(player.getOffhandItem()) && isRadioTunedToStation(player.getOffhandItem(), stationId)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isRadioItem(stack) && isRadioTunedToStation(stack, stationId)) return true;
        }
        return false;
    }

    /**
     * Radio plays from the single active listening radio on the player (inventory OK).
     * Dropped radios are not on the player, so they never keep music going.
     * When {@code radioAlwaysOn} is false, still requires holding a radio.
     */
    public static boolean shouldPlayRadio(Player player) {
        if (player == null) return false;
        if (!isListeningToRadio(player)) return false;
        if (uk.co.extraspecialstudio.dead_air.Config.radioAlwaysOn) return true;
        return isHoldingRadio(player);
    }

    public static boolean hasRadioInInventory(Player player) {
        if (isHoldingRadio(player)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isRadioItem(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    /**
     * Radio used for tune/power NBT and overlay state.
     * Prefer the radio actually in hand (main hand first when both held) so T1 and T2
     * do not share one tune session. Inventory scan only when neither hand holds a radio.
     */
    public static ItemStack getPrimaryRadioStack(Player player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isRadioItem(main)) return main;
        if (isRadioItem(off)) return off;
        ItemStack any = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isRadioItem(stack)) continue;
            if (any.isEmpty()) any = stack;
            if (isT2(stack)) return stack;
        }
        return any;
    }

    private static void loadStateFromStack(Player player, ItemStack stack, RadioState state) {
        if (stack.isEmpty() || state == null || player == null) return;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_STATION)) {
            try {
                String idStr = tag.getString(NBT_STATION);
                if (!idStr.isEmpty()) {
                    ResourceLocation id = ResourceLocation.parse(idStr);
                    RadioStation station = StationRegistry.getStation(id);
                    if (station != null) {
                        // Always apply this stack's NBT so switching held radios swaps tune state.
                        state.setCurrentStation(station);
                        LAST_TUNED_STATION.put(player.getUUID(), station);
                        // Explicit power-off only; missing key = on (legacy stacks)
                        state.setOn(!tag.contains(NBT_ON) || tag.getBoolean(NBT_ON));
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // this radio has no station NBT — clear player runtime so we don't leak the other radio's tune.
        state.setCurrentStation(null);
        state.setOn(tag == null || !tag.contains(NBT_ON) || tag.getBoolean(NBT_ON));
    }

    private static void saveStateToStack(ItemStack stack, RadioStation station, boolean on) {
        if (stack.isEmpty() || !isRadioItem(stack)) return;
        CompoundTag tag = stack.getOrCreateTag();
        if (station != null) {
            tag.putString(NBT_STATION, station.getId().toString());
        } else {
            tag.remove(NBT_STATION);
        }
        tag.putBoolean(NBT_ON, on);
    }

    public static boolean isRadioTunedToStation(ItemStack stack, ResourceLocation stationId) {
        if (stack.isEmpty() || !isRadioItem(stack) || stationId == null) return false;
        ResourceLocation tuned = getTunedStationId(stack);
        return stationId.equals(tuned);
    }

    /** Station stored on this specific radio, independent of shared player runtime state. */
    public static ResourceLocation getTunedStationId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isRadioItem(stack)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_STATION)) return null;
        return ResourceLocation.tryParse(tag.getString(NBT_STATION));
    }

    public static RadioState getState(Player player) {
        ItemStack listening = findListeningRadio(player);
        if (!listening.isEmpty()) {
            return getState(player, listening);
        }
        return getState(player, getPrimaryRadioStack(player));
    }

    /** Load runtime state from a specific radio stack (used by the tuning GUI bound to that item). */
    public static RadioState getState(Player player, ItemStack radio) {
        RadioState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new RadioState());
        if (radio != null && !radio.isEmpty() && isRadioItem(radio)) {
            loadStateFromStack(player, radio, state);
        }
        return state;
    }

    public static boolean isPlayerTunedTo(Player player, RadioStation station) {
        if (!isHoldingRadio(player) && !hasRadioInInventory(player)) {
            return false;
        }
        RadioState state = getState(player);
        return state.isOn() && state.getCurrentStation() != null
            && state.getCurrentStation().getId().equals(station.getId());
    }

    public static void tuneToStation(Player player, RadioStation station) {
        tuneToStation(player, station, getPrimaryRadioStack(player));
    }

    public static void tuneToStation(Player player, RadioStation station, ItemStack radio) {
        // EB channel is T3-only — ignore / refuse on T1–T2 (and empty radio stacks).
        if (station != null
            && station.getId().equals(StationRegistry.EMERGENCY_BROADCAST_ID)
            && (radio == null || radio.isEmpty() || !isT3(radio))) {
            return;
        }
        if (radio != null && !radio.isEmpty()) {
            claimAsActiveRadio(player, radio);
        }
        LAST_TUNED_STATION.put(player.getUUID(), station);
        RadioState state = getState(player, radio);
        state.setCurrentStation(station);
        state.setOn(true);
        if (radio != null && !radio.isEmpty()) {
            saveStateToStack(radio, station, true);
            syncRadioStateToServer(player, radio);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && station != null) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(sp,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.RADIO_FIRST_TUNE);
        }
    }

    public static void persistTunedStationPreservingPower(Player player, RadioStation station) {
        persistTunedStationPreservingPower(player, station, getPrimaryRadioStack(player));
    }

    public static void persistTunedStationPreservingPower(Player player, RadioStation station, ItemStack radio) {
        if (player == null || station == null) return;
        if (radio != null && !radio.isEmpty()) {
            claimAsActiveRadio(player, radio);
        }
        LAST_TUNED_STATION.put(player.getUUID(), station);
        RadioState state = getState(player, radio);
        state.setCurrentStation(station);
        if (radio != null && !radio.isEmpty()) {
            saveStateToStack(radio, station, state.isOn());
            syncRadioStateToServer(player, radio);
        }
    }

    public static void turnOn(Player player) {
        turnOn(player, getPrimaryRadioStack(player));
    }

    public static void turnOn(Player player, ItemStack radio) {
        RadioState state = getState(player, radio);
        state.setOn(true);
        if (radio != null && !radio.isEmpty()) {
            // Turning on a tuned radio claims it as the only active radio.
            if (state.getCurrentStation() != null || getTunedStationId(radio) != null) {
                claimAsActiveRadio(player, radio);
            }
            saveStateToStack(radio, state.getCurrentStation(), true);
            syncRadioStateToServer(player, radio);
        }
    }

    public static void turnOff(Player player) {
        turnOff(player, getPrimaryRadioStack(player));
    }

    public static void turnOff(Player player, ItemStack radio) {
        RadioState state = getState(player, radio);
        state.setOn(false);
        if (radio != null && !radio.isEmpty()) {
            saveStateToStack(radio, state.getCurrentStation(), false);
            syncRadioStateToServer(player, radio);
        }
    }

    public static void removePlayer(Player player) {
        PLAYER_STATES.remove(player.getUUID());
        LAST_TUNED_STATION.remove(player.getUUID());
    }

    // --- Cross-dim link NBT ---

    public static LinkMode getLinkMode(ItemStack stack) {
        if (!isRadioItem(stack) || stack.getTag() == null) return LinkMode.LOCAL;
        return stack.getTag().getBoolean(NBT_LINK_MODE) ? LinkMode.LINKED : LinkMode.LOCAL;
    }

    public static void setLinkMode(ItemStack stack, LinkMode mode) {
        if (!canLinkCrossDim(stack)) return;
        stack.getOrCreateTag().putBoolean(NBT_LINK_MODE, mode == LinkMode.LINKED);
    }

    public static void toggleLinkMode(ItemStack stack) {
        if (!canLinkCrossDim(stack)) return;
        setLinkMode(stack, getLinkMode(stack) == LinkMode.LINKED ? LinkMode.LOCAL : LinkMode.LINKED);
    }

    public static boolean hasLinkedTower(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_LINK_DIM) && tag.contains(NBT_LINK_X);
    }

    public static void syncToTower(ItemStack stack, ResourceKey<Level> dimension, BlockPos panelPos, RadioStation station) {
        if (!canLinkCrossDim(stack) || dimension == null || panelPos == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(NBT_LINK_DIM, dimension.location().toString());
        tag.putInt(NBT_LINK_X, panelPos.getX());
        tag.putInt(NBT_LINK_Y, panelPos.getY());
        tag.putInt(NBT_LINK_Z, panelPos.getZ());
        if (station != null) {
            tag.putString(NBT_LINK_STATION, station.getId().toString());
            tag.putString(NBT_STATION, station.getId().toString());
        }
        tag.putBoolean(NBT_LINK_MODE, true);
        tag.putBoolean(NBT_ON, true);
    }

    /** Link + claim this radio as the player's only active tuned radio. */
    public static void syncToTower(Player player, ItemStack stack, ResourceKey<Level> dimension, BlockPos panelPos, RadioStation station) {
        syncToTower(stack, dimension, panelPos, station);
        if (player != null && stack != null && !stack.isEmpty()) {
            claimAsActiveRadio(player, stack);
            if (station != null) {
                RadioState state = getState(player, stack);
                state.setCurrentStation(station);
                state.setOn(true);
                LAST_TUNED_STATION.put(player.getUUID(), station);
            }
            syncRadioStateToServer(player, stack);
        }
    }

    /** Clear cross-dim tower link and return to local discovery. */
    public static void clearLink(ItemStack stack) {
        if (!canLinkCrossDim(stack) || stack.getTag() == null) return;
        CompoundTag tag = stack.getTag();
        tag.remove(NBT_LINK_DIM);
        tag.remove(NBT_LINK_X);
        tag.remove(NBT_LINK_Y);
        tag.remove(NBT_LINK_Z);
        tag.remove(NBT_LINK_STATION);
        tag.putBoolean(NBT_LINK_MODE, false);
    }

    public static void clearLink(Player player, ItemStack stack) {
        clearLink(stack);
        if (player != null && stack != null && !stack.isEmpty()) {
            syncRadioStateToServer(player, stack);
        }
    }

    /**
     * Client-only: a linked radio follows its panel. Re-assigning the panel to another station at the tower
     * would otherwise leave the radio pointing at a station that panel no longer broadcasts.
     * Does nothing while the panel is outside the synced tower cache (e.g. you are in another dimension).
     */
    public static void refreshLinkedStation(Player player) {
        if (player == null || !player.level().isClientSide()) return;
        ItemStack radio = findListeningRadio(player);
        if (radio.isEmpty()) radio = getPrimaryRadioStack(player);
        if (radio == null || radio.isEmpty() || !isUsingLinkedMode(radio)) return;
        BlockPos panel = getLinkedPanelPos(radio);
        if (panel == null) return;
        ResourceLocation panelStationId = uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.getStationForPanel(panel);
        if (panelStationId == null || panelStationId.equals(getLinkedStationId(radio))) return;
        RadioStation station = StationRegistry.getStation(panelStationId);
        if (station == null) return;
        CompoundTag tag = radio.getOrCreateTag();
        tag.putString(NBT_LINK_STATION, panelStationId.toString());
        tag.putString(NBT_STATION, panelStationId.toString());
        LAST_TUNED_STATION.put(player.getUUID(), station);
        getState(player, radio).setCurrentStation(station);
        syncRadioStateToServer(player, radio);
        if (uk.co.extraspecialstudio.dead_air.audio.AudioManager.LOG_PLAYBACK) {
            uk.co.extraspecialstudio.dead_air.Dead_air.LOGGER.info("[radio] linked radio followed panel {} to station {}", panel, panelStationId);
        }
    }

    /** Inventory slot for sync packet: index in {@code items}, or -1 for offhand. -2 if not found. */
    public static int findRadioSlot(Player player, ItemStack radio) {
        if (player == null || radio == null || radio.isEmpty()) return -2;
        if (player.getOffhandItem() == radio) return -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i) == radio) return i;
        }
        return -2;
    }

    /**
     * Client-only: push this radio's tune/power/link to the server and untune other radios there.
     * No-op on dedicated/logical server threads.
     */
    public static void syncRadioStateToServer(Player player, ItemStack radio) {
        if (player == null || radio == null || radio.isEmpty()) return;
        if (!player.level().isClientSide()) return;
        int slot = findRadioSlot(player, radio);
        if (slot == -2) return;
        try {
            uk.co.extraspecialstudio.dead_air.net.DeadAirNet.CHANNEL.sendToServer(
                uk.co.extraspecialstudio.dead_air.net.RadioStateSyncPacket.fromRadio(slot, radio));
        } catch (Exception ignored) {
        }
    }

    /** Server: set tune + power on a radio after a client sync (already claimed active). */
    public static void applyServerTune(Player player, ItemStack radio, RadioStation station, boolean on) {
        if (player == null || radio == null || station == null) return;
        LAST_TUNED_STATION.put(player.getUUID(), station);
        RadioState state = getState(player, radio);
        state.setCurrentStation(station);
        state.setOn(on);
        saveStateToStack(radio, station, on);
        // Normal GUI tuning lands here (not tuneToStation) — grant first-tune on server.
        if (on && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(sp,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.RADIO_FIRST_TUNE);
        }
    }

    /** Server: power flag only; preserves existing station NBT when present. */
    public static void applyServerPower(Player player, ItemStack radio, boolean on) {
        if (player == null || radio == null) return;
        RadioState state = getState(player, radio);
        state.setOn(on);
        ResourceLocation id = getTunedStationId(radio);
        RadioStation station = id != null ? StationRegistry.getStation(id) : state.getCurrentStation();
        state.setCurrentStation(station);
        saveStateToStack(radio, station, on);
        // Power-on of an already-tuned radio (no stationId change in sync) still counts as first tune.
        if (on && station != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(sp,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.RADIO_FIRST_TUNE);
        }
    }

    /** Server: write or clear link NBT after client sync. */
    public static void applyServerLink(ItemStack radio, ResourceKey<Level> dimension, BlockPos panelPos,
                                      RadioStation station, boolean linkedMode) {
        if (!canLinkCrossDim(radio) || dimension == null || panelPos == null) return;
        CompoundTag tag = radio.getOrCreateTag();
        tag.putString(NBT_LINK_DIM, dimension.location().toString());
        tag.putInt(NBT_LINK_X, panelPos.getX());
        tag.putInt(NBT_LINK_Y, panelPos.getY());
        tag.putInt(NBT_LINK_Z, panelPos.getZ());
        if (station != null) {
            tag.putString(NBT_LINK_STATION, station.getId().toString());
        }
        tag.putBoolean(NBT_LINK_MODE, linkedMode);
    }

    public static ResourceKey<Level> getLinkedDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_LINK_DIM)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(NBT_LINK_DIM));
        if (id == null) return null;
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static BlockPos getLinkedPanelPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_LINK_X)) return null;
        return new BlockPos(tag.getInt(NBT_LINK_X), tag.getInt(NBT_LINK_Y), tag.getInt(NBT_LINK_Z));
    }

    public static ResourceLocation getLinkedStationId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        if (tag.contains(NBT_LINK_STATION)) {
            return ResourceLocation.tryParse(tag.getString(NBT_LINK_STATION));
        }
        if (tag.contains(NBT_STATION)) {
            return ResourceLocation.tryParse(tag.getString(NBT_STATION));
        }
        return null;
    }

    /** True when this radio is in Linked mode with a stored tower link. */
    public static boolean isUsingLinkedMode(ItemStack radio) {
        return canLinkCrossDim(radio)
            && getLinkMode(radio) == LinkMode.LINKED
            && hasLinkedTower(radio);
    }

    /** True when the player's active listening radio (or primary) is in Linked mode. */
    public static boolean isUsingLinkedMode(Player player) {
        ItemStack listening = findListeningRadio(player);
        if (!listening.isEmpty()) return isUsingLinkedMode(listening);
        return isUsingLinkedMode(getPrimaryRadioStack(player));
    }
}
