package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import javax.annotation.Nonnull;

/** Static Note resource — drops while listening to radio music. Right-click to place as decor. */
public class StaticNoteItem extends Item {
    public StaticNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public @Nonnull InteractionResult useOn(@Nonnull UseOnContext context) {
        InteractionResult placed = PlaceableDecorations.tryPlace(context);
        return placed.consumesAction() ? placed : super.useOn(context);
    }
}
