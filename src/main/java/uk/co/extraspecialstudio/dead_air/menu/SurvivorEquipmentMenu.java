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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import uk.co.extraspecialstudio.dead_air.compat.SurvivorGunCompat;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

import javax.annotation.Nullable;

/** Armor + hands + scavenger satchel (when applicable). */
public class SurvivorEquipmentMenu extends AbstractContainerMenu {
    private static final int EQUIP_SLOTS = 6;

    @Nullable
    private final SurvivorEntity survivor;
    private final IItemHandler survivorInv;
    private final boolean hasLootPack;
    private final int lootSlotStart;
    private final int playerSlotStart;

    public SurvivorEquipmentMenu(int id, Inventory playerInv, @Nullable SurvivorEntity survivor) {
        super(DeadAirMenus.SURVIVOR_EQUIPMENT.get(), id);
        this.survivor = survivor;
        this.hasLootPack = survivor != null && survivor.usesLootPack();
        this.survivorInv = survivor != null
            ? new SurvivorEquipmentHandler(survivor)
            : new ItemStackHandler(EQUIP_SLOTS);
        this.lootSlotStart = EQUIP_SLOTS;
        this.playerSlotStart = EQUIP_SLOTS + (hasLootPack ? SurvivorEntity.LOOT_PACK_SIZE : 0);

        // 0 head, 1 chest, 2 legs, 3 feet, 4 main, 5 off
        addSlot(new EquipSlot(survivorInv, 0, 26, 18, EquipmentSlot.HEAD));
        addSlot(new EquipSlot(survivorInv, 1, 26, 36, EquipmentSlot.CHEST));
        addSlot(new EquipSlot(survivorInv, 2, 26, 54, EquipmentSlot.LEGS));
        addSlot(new EquipSlot(survivorInv, 3, 26, 72, EquipmentSlot.FEET));
        addSlot(new EquipSlot(survivorInv, 4, 80, 36, EquipmentSlot.MAINHAND));
        addSlot(new EquipSlot(survivorInv, 5, 98, 36, EquipmentSlot.OFFHAND));

        if (hasLootPack && survivor != null) {
            IItemHandler pack = new InvWrapper(survivor.getLootPack());
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int idx = col + row * 3;
                    addSlot(new SlotItemHandler(pack, idx, 116 + col * 18, 18 + row * 18));
                }
            }
        }

        int invY = hasLootPack ? 140 : 102;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, invY + 58));
        }
    }

    public boolean hasLootPack() {
        return hasLootPack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (survivor != null && !survivor.level().isClientSide) {
            survivor.endConversation(player);
        }
    }

    public static SurvivorEquipmentMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        var entity = inv.player.level().getEntity(entityId);
        SurvivorEntity survivor = entity instanceof SurvivorEntity s ? s : null;
        return new SurvivorEquipmentMenu(id, inv, survivor);
    }

    @Override
    public boolean stillValid(Player player) {
        return survivor != null && survivor.isAlive() && survivor.isOwnedBy(player)
            && survivor.distanceTo(player) < 12;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();
        int lootEnd = playerSlotStart;
        if (index < EQUIP_SLOTS) {
            if (!moveItemStackTo(stack, playerSlotStart, slots.size(), true)) return ItemStack.EMPTY;
        } else if (hasLootPack && index < lootEnd) {
            if (!moveItemStackTo(stack, playerSlotStart, slots.size(), true)) return ItemStack.EMPTY;
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
            if (hasLootPack && moveItemStackTo(stack, lootSlotStart, lootEnd, false)) {
                if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                else slot.setChanged();
                return result;
            }
            if (!moveItemStackTo(stack, 0, EQUIP_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
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
            if (equipmentSlot.getType() == EquipmentSlot.Type.ARMOR) {
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
            return EQUIP_SLOTS;
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
                if (SLOTS[slot] == EquipmentSlot.MAINHAND) {
                    if (SurvivorGunCompat.isGun(one)) {
                        SurvivorGunCompat.prepareGunForNpc(one);
                    }
                    if (isWeapon(one) && survivor.getOwnerUuid() != null
                        && survivor.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        net.minecraft.server.level.ServerPlayer owner =
                            sl.getServer().getPlayerList().getPlayer(survivor.getOwnerUuid());
                        if (owner != null) {
                            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(owner,
                                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_ARM);
                        }
                    }
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
            if (es.getType() == EquipmentSlot.Type.ARMOR) {
                return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == es;
            }
            return true;
        }
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (SurvivorGunCompat.isGun(stack)) return true;
        var item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem
            || item instanceof BowItem || item instanceof CrossbowItem
            || item instanceof TridentItem;
    }
}
