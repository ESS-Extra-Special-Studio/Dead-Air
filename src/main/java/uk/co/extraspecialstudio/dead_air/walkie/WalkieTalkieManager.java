package uk.co.extraspecialstudio.dead_air.walkie;

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
import uk.co.extraspecialstudio.dead_air.item.WalkieItem;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages walkie-talkie state for players (Dead Air T1/T2 items).
 */
@SuppressWarnings("null")
public class WalkieTalkieManager {
    private static final Map<UUID, WalkieTalkieState> PLAYER_STATES = new ConcurrentHashMap<>();
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

    public static class WalkieTalkieState {
        private RadioStation currentStation;
        private float frequency;
        private boolean isOn;

        public WalkieTalkieState() {
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
        // Dead Air walkies are registered via DeadAirItems — no external scan needed.
    }

    public static boolean isWalkieTalkieItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof WalkieItem
            || stack.is(DeadAirItems.WALKIE_T1.get())
            || stack.is(DeadAirItems.WALKIE_T2.get())
            || stack.is(DeadAirTags.WALKIE_RADIOS);
    }

    public static boolean isT1(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof WalkieItem walkie) {
            return walkie.getTier() == WalkieItem.Tier.T1;
        }
        return stack.is(DeadAirTags.WALKIE_T1) || stack.is(DeadAirItems.WALKIE_T1.get());
    }

    public static boolean isT2(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof WalkieItem walkie) {
            return walkie.getTier() == WalkieItem.Tier.T2;
        }
        return stack.is(DeadAirTags.WALKIE_T2) || stack.is(DeadAirItems.WALKIE_T2.get());
    }

    public static boolean canLinkCrossDim(ItemStack stack) {
        if (stack.getItem() instanceof WalkieItem walkie) {
            return walkie.isLinkCapable();
        }
        return isT2(stack);
    }

    /** Note-drop chance while listening (on + tuned): from the single active radio only — T1 15%, T2 30%. */
    public static float getStaticNoteDropChance(Player player) {
        ensureSingleActiveListening(player);
        ItemStack active = findListeningWalkie(player);
        if (active.isEmpty()) return 0f;
        // Explicitly the active listening walkie only — extra radios in inventory do not stack chance.
        return isT2(active) ? 0.30f : 0.15f;
    }

    /**
     * True when any walkie on the player (hand, hotbar, or bag) is powered on and tuned to a station.
     * Independent of {@link #shouldPlayRadio} — drops must not require holding the radio.
     */
    public static boolean isListeningToRadio(Player player) {
        return !findListeningWalkie(player).isEmpty();
    }

    /** Clear station tune from this walkie (power / link NBT left alone). */
    public static void clearTune(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isWalkieTalkieItem(stack)) return;
        CompoundTag tag = WalkieNbt.get(stack);
        if (tag == null) return;
        tag.remove(NBT_STATION);
        WalkieNbt.set(stack, tag);
    }

    /**
     * Only one walkie may be tuned per player. Clears station NBT from every other walkie
     * on this player so T1+T2 cannot both drive music.
     */
    public static void claimAsActiveRadio(Player player, ItemStack active) {
        if (player == null || active == null || active.isEmpty() || !isWalkieTalkieItem(active)) return;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isWalkieTalkieItem(main) && main != active) clearTune(main);
        if (isWalkieTalkieItem(off) && off != active && off != main) clearTune(off);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isWalkieTalkieItem(stack) || stack == active || stack == main || stack == off) continue;
            clearTune(stack);
        }
    }

    /**
     * If multiple walkies are somehow on+tuned, keep hand preference (main → off → first inventory)
     * and untune the rest. Safe to call every tick.
     */
    public static void ensureSingleActiveListening(Player player) {
        if (player == null) return;
        ItemStack keep = ItemStack.EMPTY;
        int listeningCount = 0;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isWalkieListening(main)) {
            keep = main;
            listeningCount++;
        }
        if (isWalkieListening(off) && off != main) {
            if (keep.isEmpty()) keep = off;
            listeningCount++;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isWalkieListening(stack) || stack == main || stack == off) continue;
            if (keep.isEmpty()) keep = stack;
            listeningCount++;
        }
        if (listeningCount > 1 && !keep.isEmpty()) {
            claimAsActiveRadio(player, keep);
        }
    }

    /**
     * Prefer a listening walkie in hand (main, then offhand), otherwise any listening walkie in inventory.
     * Empty if none are on+tuned. At most one listening walkie after {@link #ensureSingleActiveListening}.
     */
    public static ItemStack findListeningWalkie(Player player) {
        if (player == null) return ItemStack.EMPTY;
        ensureSingleActiveListening(player);
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isWalkieListening(main)) return main;
        if (isWalkieListening(off)) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWalkieListening(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Walkie NBT: powered on (or legacy missing On flag) and tuned to a known station. */
    public static boolean isWalkieListening(ItemStack stack) {
        if (stack.isEmpty() || !isWalkieTalkieItem(stack)) return false;
        CompoundTag tag = WalkieNbt.get(stack);
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

    public static boolean isHoldingWalkieTalkie(Player player) {
        return isWalkieTalkieItem(player.getMainHandItem()) || isWalkieTalkieItem(player.getOffhandItem());
    }

    public static boolean hasWalkieTunedToStationOnPlayer(Player player, ResourceLocation stationId) {
        if (player == null || stationId == null) return false;
        if (isWalkieTalkieItem(player.getMainHandItem()) && isWalkieTunedToStation(player.getMainHandItem(), stationId)) return true;
        if (isWalkieTalkieItem(player.getOffhandItem()) && isWalkieTunedToStation(player.getOffhandItem(), stationId)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWalkieTalkieItem(stack) && isWalkieTunedToStation(stack, stationId)) return true;
        }
        return false;
    }

    /**
     * Radio plays from the single active listening walkie on the player (inventory OK).
     * Dropped radios are not on the player, so they never keep music going.
     * When {@code radioAlwaysOn} is false, still requires holding a walkie.
     */
    public static boolean shouldPlayRadio(Player player) {
        if (player == null) return false;
        if (!isListeningToRadio(player)) return false;
        if (uk.co.extraspecialstudio.dead_air.Config.radioAlwaysOn) return true;
        return isHoldingWalkieTalkie(player);
    }

    public static boolean hasWalkieTalkieInInventory(Player player) {
        if (isHoldingWalkieTalkie(player)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isWalkieTalkieItem(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    /**
     * Walkie used for tune/power NBT and overlay state.
     * Prefer the walkie actually in hand (main hand first when both held) so T1 and T2
     * do not share one tune session. Inventory scan only when neither hand holds a walkie.
     */
    public static ItemStack getPrimaryWalkieStack(Player player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isWalkieTalkieItem(main)) return main;
        if (isWalkieTalkieItem(off)) return off;
        ItemStack any = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isWalkieTalkieItem(stack)) continue;
            if (any.isEmpty()) any = stack;
            if (isT2(stack)) return stack;
        }
        return any;
    }

    private static void loadStateFromStack(Player player, ItemStack stack, WalkieTalkieState state) {
        if (stack.isEmpty() || state == null || player == null) return;
        CompoundTag tag = WalkieNbt.get(stack);
        if (tag != null && tag.contains(NBT_STATION)) {
            try {
                String idStr = tag.getString(NBT_STATION);
                if (!idStr.isEmpty()) {
                    ResourceLocation id = ResourceLocation.parse(idStr);
                    RadioStation station = StationRegistry.getStation(id);
                    if (station != null) {
                        // Always apply this stack's NBT so switching held walkies swaps tune state.
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
        // This walkie has no station NBT — clear player runtime so we don't leak the other walkie's tune.
        state.setCurrentStation(null);
        state.setOn(tag == null || !tag.contains(NBT_ON) || tag.getBoolean(NBT_ON));
    }

    private static void saveStateToStack(ItemStack stack, RadioStation station, boolean on) {
        if (stack.isEmpty() || !isWalkieTalkieItem(stack)) return;
        CompoundTag tag = WalkieNbt.getOrCreate(stack);
        if (station != null) {
            tag.putString(NBT_STATION, station.getId().toString());
        } else {
            tag.remove(NBT_STATION);
        }
        tag.putBoolean(NBT_ON, on);
        WalkieNbt.set(stack, tag);
    }

    public static boolean isWalkieTunedToStation(ItemStack stack, ResourceLocation stationId) {
        if (stack.isEmpty() || !isWalkieTalkieItem(stack) || stationId == null) return false;
        ResourceLocation tuned = getTunedStationId(stack);
        return stationId.equals(tuned);
    }

    /** Station stored on this specific walkie, independent of shared player runtime state. */
    public static ResourceLocation getTunedStationId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isWalkieTalkieItem(stack)) return null;
        CompoundTag tag = WalkieNbt.get(stack);
        if (tag == null || !tag.contains(NBT_STATION)) return null;
        return ResourceLocation.tryParse(tag.getString(NBT_STATION));
    }

    public static WalkieTalkieState getState(Player player) {
        ItemStack listening = findListeningWalkie(player);
        if (!listening.isEmpty()) {
            return getState(player, listening);
        }
        return getState(player, getPrimaryWalkieStack(player));
    }

    /** Load runtime state from a specific walkie stack (used by the tuning GUI bound to that item). */
    public static WalkieTalkieState getState(Player player, ItemStack walkie) {
        WalkieTalkieState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new WalkieTalkieState());
        if (walkie != null && !walkie.isEmpty() && isWalkieTalkieItem(walkie)) {
            loadStateFromStack(player, walkie, state);
        }
        return state;
    }

    public static boolean isPlayerTunedTo(Player player, RadioStation station) {
        if (!isHoldingWalkieTalkie(player) && !hasWalkieTalkieInInventory(player)) {
            return false;
        }
        WalkieTalkieState state = getState(player);
        return state.isOn() && state.getCurrentStation() != null
            && state.getCurrentStation().getId().equals(station.getId());
    }

    public static void tuneToStation(Player player, RadioStation station) {
        tuneToStation(player, station, getPrimaryWalkieStack(player));
    }

    public static void tuneToStation(Player player, RadioStation station, ItemStack walkie) {
        if (walkie != null && !walkie.isEmpty()) {
            claimAsActiveRadio(player, walkie);
        }
        LAST_TUNED_STATION.put(player.getUUID(), station);
        WalkieTalkieState state = getState(player, walkie);
        state.setCurrentStation(station);
        state.setOn(true);
        if (walkie != null && !walkie.isEmpty()) {
            saveStateToStack(walkie, station, true);
            syncWalkieStateToServer(player, walkie);
        }
    }

    public static void persistTunedStationPreservingPower(Player player, RadioStation station) {
        persistTunedStationPreservingPower(player, station, getPrimaryWalkieStack(player));
    }

    public static void persistTunedStationPreservingPower(Player player, RadioStation station, ItemStack walkie) {
        if (player == null || station == null) return;
        if (walkie != null && !walkie.isEmpty()) {
            claimAsActiveRadio(player, walkie);
        }
        LAST_TUNED_STATION.put(player.getUUID(), station);
        WalkieTalkieState state = getState(player, walkie);
        state.setCurrentStation(station);
        if (walkie != null && !walkie.isEmpty()) {
            saveStateToStack(walkie, station, state.isOn());
            syncWalkieStateToServer(player, walkie);
        }
    }

    public static void turnOn(Player player) {
        turnOn(player, getPrimaryWalkieStack(player));
    }

    public static void turnOn(Player player, ItemStack walkie) {
        WalkieTalkieState state = getState(player, walkie);
        state.setOn(true);
        if (walkie != null && !walkie.isEmpty()) {
            // Turning on a tuned walkie claims it as the only active radio.
            if (state.getCurrentStation() != null || getTunedStationId(walkie) != null) {
                claimAsActiveRadio(player, walkie);
            }
            saveStateToStack(walkie, state.getCurrentStation(), true);
            syncWalkieStateToServer(player, walkie);
        }
    }

    public static void turnOff(Player player) {
        turnOff(player, getPrimaryWalkieStack(player));
    }

    public static void turnOff(Player player, ItemStack walkie) {
        WalkieTalkieState state = getState(player, walkie);
        state.setOn(false);
        if (walkie != null && !walkie.isEmpty()) {
            saveStateToStack(walkie, state.getCurrentStation(), false);
            syncWalkieStateToServer(player, walkie);
        }
    }

    public static void removePlayer(Player player) {
        PLAYER_STATES.remove(player.getUUID());
        LAST_TUNED_STATION.remove(player.getUUID());
    }

    // --- Cross-dim link NBT ---

    public static LinkMode getLinkMode(ItemStack stack) {
        if (!isWalkieTalkieItem(stack) || WalkieNbt.get(stack) == null) return LinkMode.LOCAL;
        return WalkieNbt.get(stack).getBoolean(NBT_LINK_MODE) ? LinkMode.LINKED : LinkMode.LOCAL;
    }

    public static void setLinkMode(ItemStack stack, LinkMode mode) {
        if (!canLinkCrossDim(stack)) return;
        WalkieNbt.update(stack, tag -> tag.putBoolean(NBT_LINK_MODE, mode == LinkMode.LINKED));
    }

    public static void toggleLinkMode(ItemStack stack) {
        if (!canLinkCrossDim(stack)) return;
        setLinkMode(stack, getLinkMode(stack) == LinkMode.LINKED ? LinkMode.LOCAL : LinkMode.LINKED);
    }

    public static boolean hasLinkedTower(ItemStack stack) {
        CompoundTag tag = WalkieNbt.get(stack);
        return tag != null && tag.contains(NBT_LINK_DIM) && tag.contains(NBT_LINK_X);
    }

    public static void syncToTower(ItemStack stack, ResourceKey<Level> dimension, BlockPos panelPos, RadioStation station) {
        if (!canLinkCrossDim(stack) || dimension == null || panelPos == null) return;
        CompoundTag tag = WalkieNbt.getOrCreate(stack);
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
        WalkieNbt.set(stack, tag);
    }

    /** Link + claim this walkie as the player's only active tuned radio. */
    public static void syncToTower(Player player, ItemStack stack, ResourceKey<Level> dimension, BlockPos panelPos, RadioStation station) {
        syncToTower(stack, dimension, panelPos, station);
        if (player != null && stack != null && !stack.isEmpty()) {
            claimAsActiveRadio(player, stack);
            if (station != null) {
                WalkieTalkieState state = getState(player, stack);
                state.setCurrentStation(station);
                state.setOn(true);
                LAST_TUNED_STATION.put(player.getUUID(), station);
            }
            syncWalkieStateToServer(player, stack);
        }
    }

    /** Clear cross-dim tower link and return to local discovery. */
    public static void clearLink(ItemStack stack) {
        if (!canLinkCrossDim(stack) || WalkieNbt.get(stack) == null) return;
        CompoundTag tag = WalkieNbt.get(stack);
        tag.remove(NBT_LINK_DIM);
        tag.remove(NBT_LINK_X);
        tag.remove(NBT_LINK_Y);
        tag.remove(NBT_LINK_Z);
        tag.remove(NBT_LINK_STATION);
        tag.putBoolean(NBT_LINK_MODE, false);
        WalkieNbt.set(stack, tag);
    }

    public static void clearLink(Player player, ItemStack stack) {
        clearLink(stack);
        if (player != null && stack != null && !stack.isEmpty()) {
            syncWalkieStateToServer(player, stack);
        }
    }

    /**
     * Client-only: a linked walkie follows its panel. Re-assigning the panel to another station at the tower
     * would otherwise leave the walkie pointing at a station that panel no longer broadcasts.
     * Does nothing while the panel is outside the synced tower cache (e.g. you are in another dimension).
     */
    public static void refreshLinkedStation(Player player) {
        if (player == null || !player.level().isClientSide()) return;
        ItemStack walkie = findListeningWalkie(player);
        if (walkie.isEmpty()) walkie = getPrimaryWalkieStack(player);
        if (walkie == null || walkie.isEmpty() || !isUsingLinkedMode(walkie)) return;
        BlockPos panel = getLinkedPanelPos(walkie);
        if (panel == null) return;
        ResourceLocation panelStationId = uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.getStationForPanel(panel);
        if (panelStationId == null || panelStationId.equals(getLinkedStationId(walkie))) return;
        RadioStation station = StationRegistry.getStation(panelStationId);
        if (station == null) return;
        CompoundTag tag = WalkieNbt.getOrCreate(walkie);
        tag.putString(NBT_LINK_STATION, panelStationId.toString());
        tag.putString(NBT_STATION, panelStationId.toString());
        WalkieNbt.set(walkie, tag);
        LAST_TUNED_STATION.put(player.getUUID(), station);
        getState(player, walkie).setCurrentStation(station);
        syncWalkieStateToServer(player, walkie);
        if (uk.co.extraspecialstudio.dead_air.audio.AudioManager.LOG_PLAYBACK) {
            uk.co.extraspecialstudio.dead_air.Dead_air.LOGGER.info("[radio] linked walkie followed panel {} to station {}", panel, panelStationId);
        }
    }

    /** Inventory slot for sync packet: index in {@code items}, or -1 for offhand. -2 if not found. */
    public static int findWalkieSlot(Player player, ItemStack walkie) {
        if (player == null || walkie == null || walkie.isEmpty()) return -2;
        if (player.getOffhandItem() == walkie) return -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i) == walkie) return i;
        }
        return -2;
    }

    /**
     * Client-only: push this walkie's tune/power/link to the server and untune other radios there.
     * No-op on dedicated/logical server threads.
     */
    public static void syncWalkieStateToServer(Player player, ItemStack walkie) {
        if (player == null || walkie == null || walkie.isEmpty()) return;
        if (!player.level().isClientSide()) return;
        int slot = findWalkieSlot(player, walkie);
        if (slot == -2) return;
        try {
            uk.co.extraspecialstudio.dead_air.net.DeadAirNet.sendToServer(
                uk.co.extraspecialstudio.dead_air.net.WalkieStateSyncPacket.fromWalkie(slot, walkie));
        } catch (Exception ignored) {
        }
    }

    /** Server: set tune + power on a walkie after a client sync (already claimed active). */
    public static void applyServerTune(Player player, ItemStack walkie, RadioStation station, boolean on) {
        if (player == null || walkie == null || station == null) return;
        LAST_TUNED_STATION.put(player.getUUID(), station);
        WalkieTalkieState state = getState(player, walkie);
        state.setCurrentStation(station);
        state.setOn(on);
        saveStateToStack(walkie, station, on);
    }

    /** Server: power flag only; preserves existing station NBT when present. */
    public static void applyServerPower(Player player, ItemStack walkie, boolean on) {
        if (player == null || walkie == null) return;
        WalkieTalkieState state = getState(player, walkie);
        state.setOn(on);
        ResourceLocation id = getTunedStationId(walkie);
        RadioStation station = id != null ? StationRegistry.getStation(id) : state.getCurrentStation();
        state.setCurrentStation(station);
        saveStateToStack(walkie, station, on);
    }

    /** Server: write or clear link NBT after client sync. */
    public static void applyServerLink(ItemStack walkie, ResourceKey<Level> dimension, BlockPos panelPos,
                                      RadioStation station, boolean linkedMode) {
        if (!canLinkCrossDim(walkie) || dimension == null || panelPos == null) return;
        CompoundTag tag = WalkieNbt.getOrCreate(walkie);
        tag.putString(NBT_LINK_DIM, dimension.location().toString());
        tag.putInt(NBT_LINK_X, panelPos.getX());
        tag.putInt(NBT_LINK_Y, panelPos.getY());
        tag.putInt(NBT_LINK_Z, panelPos.getZ());
        if (station != null) {
            tag.putString(NBT_LINK_STATION, station.getId().toString());
        }
        tag.putBoolean(NBT_LINK_MODE, linkedMode);
        WalkieNbt.set(walkie, tag);
    }

    public static ResourceKey<Level> getLinkedDimension(ItemStack stack) {
        CompoundTag tag = WalkieNbt.get(stack);
        if (tag == null || !tag.contains(NBT_LINK_DIM)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(NBT_LINK_DIM));
        if (id == null) return null;
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static BlockPos getLinkedPanelPos(ItemStack stack) {
        CompoundTag tag = WalkieNbt.get(stack);
        if (tag == null || !tag.contains(NBT_LINK_X)) return null;
        return new BlockPos(tag.getInt(NBT_LINK_X), tag.getInt(NBT_LINK_Y), tag.getInt(NBT_LINK_Z));
    }

    public static ResourceLocation getLinkedStationId(ItemStack stack) {
        CompoundTag tag = WalkieNbt.get(stack);
        if (tag == null) return null;
        if (tag.contains(NBT_LINK_STATION)) {
            return ResourceLocation.tryParse(tag.getString(NBT_LINK_STATION));
        }
        if (tag.contains(NBT_STATION)) {
            return ResourceLocation.tryParse(tag.getString(NBT_STATION));
        }
        return null;
    }

    /** True when this walkie is in Linked mode with a stored tower link. */
    public static boolean isUsingLinkedMode(ItemStack walkie) {
        return canLinkCrossDim(walkie)
            && getLinkMode(walkie) == LinkMode.LINKED
            && hasLinkedTower(walkie);
    }

    /** True when the player's active listening walkie (or primary) is in Linked mode. */
    public static boolean isUsingLinkedMode(Player player) {
        ItemStack listening = findListeningWalkie(player);
        if (!listening.isEmpty()) return isUsingLinkedMode(listening);
        return isUsingLinkedMode(getPrimaryWalkieStack(player));
    }
}
