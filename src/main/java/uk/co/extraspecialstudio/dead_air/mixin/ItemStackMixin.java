package uk.co.extraspecialstudio.dead_air.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to detect walkie-talkie items by checking NBT data.
 */
@Mixin(ItemStack.class)
@SuppressWarnings("null")
public abstract class ItemStackMixin {
    
    @Shadow
    public abstract CompoundTag getTag();
    
    // Removed getDisplayName inject - it was interfering with walkie-talkie mod's right-click functionality
    // Walkie-talkie detection now happens entirely in WalkieTalkieManager using item registry lookups
    
    /**
     * Helper method to check if item has walkie-talkie NBT data.
     */
    public boolean dead_air$isWalkieTalkie() {
        CompoundTag tag = getTag();
        if (tag != null) {
            // Check for walkie-talkie specific NBT tags
            return tag.contains("WalkieTalkie") || 
                   tag.contains("RadioFrequency") ||
                   tag.contains("IsRadio");
        }
        return false;
    }
}
