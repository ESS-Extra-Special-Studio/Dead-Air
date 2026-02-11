package uk.creatopia.unbound.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import uk.creatopia.unbound.dead_air.radio.RadioTower;

/**
 * Container menu for radio tower interaction.
 * This is a placeholder - full implementation would have a proper GUI.
 */
@SuppressWarnings("null")
public class TowerMenu extends AbstractContainerMenu {
    private final BlockPos towerPos;
    private final RadioTower tower;
    
    public TowerMenu(int containerId, Inventory playerInventory, BlockPos towerPos, RadioTower tower) {
        super(null, containerId); // MenuType would be registered
        this.towerPos = towerPos;
        this.tower = tower;
    }
    
    public TowerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, data.readBlockPos(), null); // Tower would be looked up
    }
    
    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(towerPos.getX() + 0.5, towerPos.getY() + 0.5, towerPos.getZ() + 0.5) <= 64.0;
    }
    
    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY; // No items to move
    }
    
    public RadioTower getTower() {
        return tower;
    }
    
    public BlockPos getTowerPos() {
        return towerPos;
    }
}
