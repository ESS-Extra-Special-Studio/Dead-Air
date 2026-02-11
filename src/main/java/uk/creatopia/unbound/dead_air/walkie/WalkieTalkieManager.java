package uk.creatopia.unbound.dead_air.walkie;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages walkie-talkie state for players.
 * Integrates with external walkie-talkie mod if available.
 */
@SuppressWarnings("null")
public class WalkieTalkieManager {
    private static final Map<UUID, WalkieTalkieState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static ResourceLocation WALKIE_TALKIE_ITEM_ID = null;

    /** NBT keys on the walkie-talkie item stack so tuned station and power survive reload. */
    private static final String NBT_STATION = "DeadAirStation";
    private static final String NBT_ON = "DeadAirOn";
    
    /**
     * State of a player's walkie-talkie.
     */
    public static class WalkieTalkieState {
        private RadioStation currentStation;
        private float frequency;
        private boolean isOn;
        
        public WalkieTalkieState() {
            this.currentStation = null;
            this.frequency = 0.0f;
            this.isOn = true; // Default to ON for easier station discovery
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
    
    /**
     * Initialize walkie-talkie item detection.
     * Scans for all walkie-talkie items including all tiers (wooden, iron, gold, diamond, etc.)
     */
    public static void initialize() {
        // Scan all registered items for walkie-talkie patterns
        // This supports all tiers: wooden_walkie_talkie, iron_walkie_talkie, gold_walkie_talkie, diamond_walkie_talkie, etc.
        int foundCount = 0;
        
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation itemId = entry.getKey().location();
            String path = itemId.getPath().toLowerCase();
            String namespace = itemId.getNamespace().toLowerCase();
            
            // Check if item matches walkie-talkie patterns
            // Supports: walkie_talkie, wooden_walkie_talkie, iron_walkie_talkie, gold_walkie_talkie, diamond_walkie_talkie, etc.
            boolean isWalkieTalkie = (path.contains("walkie") && path.contains("talkie")) ||
                                     (path.contains("walkie_talkie")) ||
                                     (namespace.contains("walkietalkie") && path.contains("walkie"));
            
            if (isWalkieTalkie) {
                foundCount++;
                if (WALKIE_TALKIE_ITEM_ID == null) {
                    // Store the first one found as primary (for backwards compatibility)
                    WALKIE_TALKIE_ITEM_ID = itemId;
                }
                Dead_air.LOGGER.info("Found walkie-talkie item (tier {}): {}", foundCount, itemId);
            }
        }
        
        // Also check common known IDs for all tiers
        String[] knownIds = {
            "walkietalkie:walkie_talkie",
            "walkietalkie:wooden_walkie_talkie",
            "walkietalkie:iron_walkie_talkie",
            "walkietalkie:gold_walkie_talkie",
            "walkietalkie:diamond_walkie_talkie",
            "walkietalkie:netherite_walkie_talkie",
            "radio:walkie_talkie",
            "walkie_talkie:walkie_talkie"
        };
        
        for (String id : knownIds) {
            ResourceLocation loc = ResourceLocation.parse(id);
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            if (item != null && WALKIE_TALKIE_ITEM_ID == null) {
                WALKIE_TALKIE_ITEM_ID = loc;
                foundCount++;
                Dead_air.LOGGER.info("Found known walkie-talkie item: {}", id);
            }
        }
        
        if (foundCount > 0) {
            Dead_air.LOGGER.info("Detected {} walkie-talkie item(s) - all tiers supported", foundCount);
        } else {
            Dead_air.LOGGER.warn("No walkie-talkie items found. Using fallback detection (name/path matching).");
        }
    }
    
    /**
     * Check if a player is holding a walkie-talkie.
     * Supports all tiers: wooden, iron, gold, diamond, netherite, etc.
     */
    public static boolean isHoldingWalkieTalkie(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        
        // Primary check: use fallback detection which works for all tiers
        // This checks item path/name for "walkie" and "talkie" patterns
        return isWalkieTalkieItem(mainHand) || isWalkieTalkieItem(offHand);
    }
    
    /**
     * Check if a player has a walkie-talkie in their inventory (hand, hotbar, or inventory).
     * Used when radioAlwaysOn config is enabled.
     */
    public static boolean hasWalkieTalkieInInventory(Player player) {
        // Check main hand and offhand first
        if (isHoldingWalkieTalkie(player)) {
            return true;
        }
        
        // Check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWalkieTalkieItem(stack)) {
                return true;
            }
        }
        
        // Check rest of inventory (slots 9-35)
        for (int i = 9; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWalkieTalkieItem(stack)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get the "primary" walkie-talkie stack for a player (held first, then first in inventory).
     * Used to read/write tuned station and power state so it persists across reload.
     */
    private static ItemStack getPrimaryWalkieStack(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (isWalkieTalkieItem(player.getMainHandItem())) return player.getMainHandItem();
        if (isWalkieTalkieItem(player.getOffhandItem())) return player.getOffhandItem();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWalkieTalkieItem(stack)) return stack;
        }
        for (int i = 9; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWalkieTalkieItem(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Load tuned station and power from walkie-talkie item NBT (persisted state).
     */
    private static void loadStateFromStack(ItemStack stack, WalkieTalkieState state) {
        if (stack.isEmpty() || state == null) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_STATION)) return;
        try {
            String idStr = tag.getString(NBT_STATION);
            if (idStr.isEmpty()) return;
            ResourceLocation id = ResourceLocation.parse(idStr);
            RadioStation station = StationRegistry.getStation(id);
            state.setCurrentStation(station);
            state.setOn(tag.getBoolean(NBT_ON));
        } catch (Exception e) {
            Dead_air.LOGGER.debug("Could not load Dead Air state from walkie NBT: {}", e.getMessage());
        }
    }

    /**
     * Save tuned station and power to walkie-talkie item NBT so it persists across reload.
     */
    private static void saveStateToStack(ItemStack stack, RadioStation station, boolean on) {
        if (stack.isEmpty() || !isWalkieTalkieItem(stack)) return;
        CompoundTag tag = stack.getOrCreateTag();
        if (station != null) {
            tag.putString(NBT_STATION, station.getId().toString());
        } else {
            tag.remove(NBT_STATION);
        }
        tag.putBoolean(NBT_ON, on);
    }

    /**
     * Check if an ItemStack is a walkie-talkie by examining its properties.
     */
    private static boolean isWalkieTalkieItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // Check item registry name
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            String path = itemId.getPath().toLowerCase();
            if (path.contains("walkie") || path.contains("talkie") || path.contains("radio")) {
                return true;
            }
        }
        
        // Check NBT data (if mixin is available)
        if (stack.getTag() != null) {
            var tag = stack.getTag();
            if (tag.contains("WalkieTalkie") || tag.contains("RadioFrequency") || tag.contains("IsRadio")) {
                return true;
            }
        }
        
        // Check display name (last resort)
        String displayName = stack.getDisplayName().getString().toLowerCase();
        return displayName.contains("walkie") || displayName.contains("talkie") || displayName.contains("radio");
    }
    
    /**
     * Get walkie-talkie state for a player.
     * When the player has a walkie-talkie, state is synced from the item's NBT so tuned station
     * and power persist after save/reload.
     */
    public static WalkieTalkieState getState(Player player) {
        WalkieTalkieState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), k -> new WalkieTalkieState());
        ItemStack primary = getPrimaryWalkieStack(player);
        if (!primary.isEmpty()) {
            loadStateFromStack(primary, state);
        }
        return state;
    }
    
    /**
     * Check if a player is tuned to a specific station.
     */
    public static boolean isPlayerTunedTo(Player player, RadioStation station) {
        if (!isHoldingWalkieTalkie(player)) {
            return false;
        }
        
        WalkieTalkieState state = getState(player);
        return state.isOn() && state.getCurrentStation() != null && 
               state.getCurrentStation().getId().equals(station.getId());
    }
    
    /**
     * Tune player's walkie-talkie to a station.
     * Persists to the primary walkie-talkie item NBT so it survives reload.
     */
    public static void tuneToStation(Player player, RadioStation station) {
        WalkieTalkieState state = getState(player);
        state.setCurrentStation(station);
        state.setOn(true);
        ItemStack primary = getPrimaryWalkieStack(player);
        if (!primary.isEmpty()) {
            saveStateToStack(primary, station, true);
        }
    }
    
    /**
     * Turn on walkie-talkie.
     * Persists to the primary walkie-talkie item NBT.
     */
    public static void turnOn(Player player) {
        WalkieTalkieState state = getState(player);
        state.setOn(true);
        ItemStack primary = getPrimaryWalkieStack(player);
        if (!primary.isEmpty()) {
            saveStateToStack(primary, state.getCurrentStation(), true);
        }
    }

    /**
     * Turn off walkie-talkie.
     * Persists to the primary walkie-talkie item NBT.
     */
    public static void turnOff(Player player) {
        WalkieTalkieState state = getState(player);
        state.setOn(false);
        ItemStack primary = getPrimaryWalkieStack(player);
        if (!primary.isEmpty()) {
            saveStateToStack(primary, state.getCurrentStation(), false);
        }
    }
    
    /**
     * Remove player state (when they disconnect).
     */
    public static void removePlayer(Player player) {
        PLAYER_STATES.remove(player.getUUID());
    }
}
