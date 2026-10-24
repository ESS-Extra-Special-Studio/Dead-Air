package uk.co.extraspecialstudio.dead_air.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import uk.co.extraspecialstudio.dead_air.compat.SurvivorGunCompat;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

import javax.annotation.Nullable;

/** Armor + main/offhand for a survivor. */
public class SurvivorEquipmentMenu extends AbstractContainerMenu {
    @Nullable
    private final SurvivorEntity survivor;
    private final IItemHandler survivorInv;

    public SurvivorEquipmentMenu(int id, Inventory playerInv, @Nullable SurvivorEntity survivor) {
        super(DeadAirMenus.SURVIVOR_EQUIPMENT.get(), id);
        this.survivor = survivor;
        this.survivorInv = survivor != null
            ? new SurvivorEquipmentHandler(survivor)
            : new ItemStackHandler(6);

        addSlot(new EquipSlot(survivorInv, 0, 26, 18, EquipmentSlot.HEAD));
        addSlot(new EquipSlot(survivorInv, 1, 26, 36, EquipmentSlot.CHEST));
        addSlot(new EquipSlot(survivorInv, 2, 26, 54, EquipmentSlot.LEGS));
        addSlot(new EquipSlot(survivorInv, 3, 26, 72, EquipmentSlot.FEET));
        addSlot(new EquipSlot(survivorInv, 4, 80, 36, EquipmentSlot.MAINHAND));
        addSlot(new EquipSlot(survivorInv, 5, 98, 36, EquipmentSlot.OFFHAND));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 160));
        }
    }

    public static SurvivorEquipmentMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        var entity = inv.player.level().getEntity(entityId);
        SurvivorEntity survivor = entity instanceof SurvivorEntity s ? s : null;
        // Soft-fail if entity not synced yet â€” stillValid closes the screen; never hard-crash the client.
        return new SurvivorEquipmentMenu(id, inv, survivor);
    }

    @Override
    public boolean stillValid(Player player) {
        return survivor != null && survivor.isAlive() && survivor.isOwnedBy(player)
            && survivor.distanceTo(player) < 8;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 6) {
                if (!moveItemStackTo(stack, 6, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (stack.getItem() instanceof ArmorItem armor) {
                    EquipmentSlot es = armor.getEquipmentSlot();
                    int dest = switch (es) {
                        case HEAD -> 0;
                        case CHEST -> 1;
                        case LEGS -> 2;
                        case FEET -> 3;
                        default -> -1;
                    };
                    if (dest >= 0 && moveItemStackTo(stack, dest, dest + 1, false)) {
                        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                        else slot.setChanged();
                        return result;
                    }
                }
                if (!moveItemStackTo(stack, 0, 6, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Nullable
    public SurvivorEntity getSurvivor() {
        return survivor;
    }

    public static final class Provider implements MenuProvider {
        private final SurvivorEntity survivor;

        public Provider(SurvivorEntity survivor) {
            this.survivor = survivor;
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("screen.dead_air.survivor.equipment_title", survivor.getName());
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new SurvivorEquipmentMenu(id, inv, survivor);
        }
    }

    private static final class EquipSlot extends SlotItemHandler {
        private final EquipmentSlot equipmentSlot;

        EquipSlot(IItemHandler handler, int index, int x, int y, EquipmentSlot slot) {
            super(handler, index, x, y);
            this.equipmentSlot = slot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (equipmentSlot.isArmor()) {
                return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == equipmentSlot;
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ItemStack stack = getItem();
            if (equipmentSlot == EquipmentSlot.MAINHAND && SurvivorGunCompat.isGun(stack)) {
                SurvivorGunCompat.prepareGunForNpc(stack);
            }
        }
    }

    private static final class SurvivorEquipmentHandler implements IItemHandler {
        private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        private final SurvivorEntity survivor;

        SurvivorEquipmentHandler(SurvivorEntity survivor) {
            this.survivor = survivor;
        }

        @Override
        public int getSlots() {
            return 6;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return survivor.getItemBySlot(SLOTS[slot]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack existing = getStackInSlot(slot);
            if (!existing.isEmpty()) return stack;
            if (!isItemValid(slot, stack)) return stack;
            ItemStack one = stack.copy();
            one.setCount(1);
            if (!simulate) {
                survivor.setItemSlot(SLOTS[slot], one);
                if (SLOTS[slot] == EquipmentSlot.MAINHAND && SurvivorGunCompat.isGun(one)) {
                    SurvivorGunCompat.prepareGunForNpc(one);
                }
            }
            ItemStack rest = stack.copy();
            rest.shrink(1);
            return rest.isEmpty() ? ItemStack.EMPTY : rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack out = existing.copy();
            out.setCount(1);
            if (!simulate) survivor.setItemSlot(SLOTS[slot], ItemStack.EMPTY);
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            EquipmentSlot es = SLOTS[slot];
            if (es.isArmor()) {
                return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == es;
            }
            return true;
        }
    }
}
