package uk.co.extraspecialstudio.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.*;

/**
 * Detects radio towers from Apocalypse Structures: Radio Towers and Airdrops (MCreator, no libs).
 * Mod ID: radiotowers. Exactly 3 tower registry names: radiotower, radiotower_2, radiotoweroverrun
 * (MCreator file names: Radiotower, Radiotower2, Radiotoweroverrun). Impostors to ignore:
 * radiotoweroverrunloot (loot table), radiotowers (creative tab).
 */
@SuppressWarnings("null")
public class ApocalypseTowerDetector {
    private static final String RADIOTOWERS_MODID = "radiotowers";
    
    // Known block IDs from RadioTowers mod (MCreator block names may vary; we also scan by path)
    private static final Set<ResourceLocation> RADIO_PANEL_BLOCKS = new HashSet<>();
    
    /**
     * Initialize detection for RadioTowers mod blocks.
     */
    public static void initialize() {
        // MCreator block registry names may use different conventions; try common variants
        String[] radioPanelIds = {
            "radiotowers:radio_panel",
            "radiotowers:radiopanel"
        };
        
        for (String id : radioPanelIds) {
            ResourceLocation loc = ResourceLocation.parse(id);
            Block block = ForgeRegistries.BLOCKS.getValue(loc);
            if (block != null) {
                RADIO_PANEL_BLOCKS.add(loc);
            }
        }
        
        // Scan for blocks from radiotowers namespace
        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries()) {
            ResourceLocation blockId = entry.getKey().location();
            
            if (RADIOTOWERS_MODID.equals(blockId.getNamespace())) {
                String path = blockId.getPath().toLowerCase();
                
                // Check for radio panel blocks
                if (path.contains("radio_panel") || path.contains("panel")) {
                    RADIO_PANEL_BLOCKS.add(blockId);
                }
            }
        }
        
        if (RADIO_PANEL_BLOCKS.isEmpty()) {
            Dead_air.LOGGER.error("No RadioTowers radio panel blocks detected! Make sure 'RadioTowers' mod is installed.");
        }
    }
    
    /**
     * Check if a position is part of a RadioTowers tower structure.
     * Since towers are structures, we check if there's a Radio Panel nearby (all towers have Radio Panels).
     */
    public static boolean isApocalypseTower(ServerLevel level, BlockPos pos) {
        // Check if there's a Radio Panel nearby (towers have Radio Panels)
        BlockPos panelPos = findRadioPanel(level, pos);
        if (panelPos != null) {
            return true;
        }
        
        // Also check if this position itself is a Radio Panel (towers contain Radio Panels)
        if (isRadioPanel(level, pos)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Determine the type of RadioTowers tower (registry: radiotower, radiotower_2, radiotoweroverrun).
     * Uses block pattern analysis:
     * - FENCED (radiotower_2): Has metal_bars blocks
     * - OVERRUN (radiotoweroverrun): Different patterns (to be determined)
     * - STANDARD (radiotower): Default, no special blocks
     */
    public static ApocalypseTowerType getTowerType(ServerLevel level, BlockPos pos) {
        // Use the position provided (should be Radio Panel position)
        BlockPos searchPos = pos;
        
        // If pos is not a Radio Panel, try to find one nearby
        if (!isRadioPanel(level, pos)) {
            BlockPos panelPos = findRadioPanel(level, pos);
            if (panelPos != null) {
                searchPos = panelPos;
            } else {
                // No Radio Panel found, can't determine tower type
                return ApocalypseTowerType.UNKNOWN;
            }
        }
        
        // Check nearby blocks for clues about tower type
        // Fenced towers (radiotower_2) have metal_bars blocks
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        boolean foundMetalBars = false;
        
        // Search in a radius around the Radio Panel
        for (int x = -15; x <= 15; x++) {
            for (int y = -15; y <= 15; y++) {
                for (int z = -15; z <= 15; z++) {
                    checkPos.set(searchPos.getX() + x, searchPos.getY() + y, searchPos.getZ() + z);
                    BlockState state = level.getBlockState(checkPos);
                    ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    
                    if (blockId != null && blockId.getNamespace().equals(RADIOTOWERS_MODID)) {
                        String path = blockId.getPath().toLowerCase();
                        
                        // Fenced towers (radiotower_2) have metal_bars
                        if (path.contains("metal_bars")) {
                            foundMetalBars = true;
                        }
                    }
                }
            }
        }
        
        // If we found metal_bars, it's a fenced tower (radiotower_2)
        if (foundMetalBars) {
            return ApocalypseTowerType.FENCED;
        }

        // Overrun towers (radiotoweroverrun) include debris blocks such as trashblock / airdrop crate
        BlockPos.MutableBlockPos overrunCheck = new BlockPos.MutableBlockPos();
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                for (int z = -20; z <= 20; z++) {
                    overrunCheck.set(searchPos.getX() + x, searchPos.getY() + y, searchPos.getZ() + z);
                    BlockState state = level.getBlockState(overrunCheck);
                    ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    if (blockId != null && RADIOTOWERS_MODID.equals(blockId.getNamespace())) {
                        String path = blockId.getPath().toLowerCase();
                        if (path.contains("trashblock") || path.contains("airdrop_crate")) {
                            return ApocalypseTowerType.OVERRUN;
                        }
                    }
                }
            }
        }
        
        // Default: if we found a Radio Panel but no metal_bars, assume standard tower
        return ApocalypseTowerType.STANDARD;
    }
    
    /**
     * Check if a block is a Radio Panel from RadioTowers mod.
     * Works with both ServerLevel and ClientLevel.
     */
    public static boolean isRadioPanel(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        
        if (blockId == null) {
            return false;
        }
        
        return RADIO_PANEL_BLOCKS.contains(blockId) ||
               (RADIOTOWERS_MODID.equals(blockId.getNamespace()) && 
                (blockId.getPath().contains("radio_panel") || blockId.getPath().contains("panel")));
    }
    
    /**
     * Client-side version that works with any Level.
     */
    public static boolean isRadioPanel(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        
        if (blockId == null) {
            return false;
        }
        
        return RADIO_PANEL_BLOCKS.contains(blockId) ||
               (RADIOTOWERS_MODID.equals(blockId.getNamespace()) && 
                (blockId.getPath().contains("radio_panel") || blockId.getPath().contains("panel")));
    }
    
    /**
     * Find the Radio Panel associated with a tower (usually nearby).
     * Radio Panels are typically within the tower structure.
     */
    public static BlockPos findRadioPanel(ServerLevel level, BlockPos towerPos) {
        // Radio panels are typically within 10 blocks of any tower position
        int searchRadius = 10;
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = towerPos.offset(x, y, z);
                    if (isRadioPanel(level, checkPos)) {
                        return checkPos;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Scan for RadioTowers towers in a chunk.
     * Towers are detected by finding Radio Panels (all towers have Radio Panels).
     */
    public static void scanChunkForTowers(ServerLevel level, int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        
        // Scan for Radio Panels - each tower has one
        for (int x = chunkX * 16; x < (chunkX + 1) * 16; x++) {
            for (int z = chunkZ * 16; z < (chunkZ + 1) * 16; z++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    pos.set(x, y, z);
                    
                    if (isRadioPanel(level, pos)) {
                        // Found a Radio Panel - this indicates a tower structure
                        // The tower will be registered by TowerManager when it processes this
                    }
                }
            }
        }
    }
}
