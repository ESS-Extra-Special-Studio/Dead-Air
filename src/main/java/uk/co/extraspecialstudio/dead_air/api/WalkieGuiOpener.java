package uk.co.extraspecialstudio.dead_air.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side hook so companion radios can open their own GUI from Dead Air's walkie keybind
 * without subclassing {@link uk.co.extraspecialstudio.dead_air.item.WalkieItem}.
 */
@FunctionalInterface
public interface WalkieGuiOpener {
    /**
     * @return {@code true} if this opener handled the stack (screen opened or intentionally skipped)
     */
    boolean tryOpen(InteractionHand hand, ItemStack stack);
}
