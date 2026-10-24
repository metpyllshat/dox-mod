package dev.blackchannel.menu;

import dev.blackchannel.logic.Devices;
import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import dev.blackchannel.net.ModNetwork;

public final class Menus {
    private Menus() {}

    public static class PhoneMenu extends AbstractContainerMenu {
        public PhoneMenu(int id, Inventory inv) {
            super(ModContent.PHONE_MENU.get(), id);
            if (inv.player instanceof ServerPlayer sp) ModNetwork.syncFull(sp);
        }

        @Override
        public boolean stillValid(Player player) {
            return player.getMainHandItem().is(ModContent.PHONE.get()) || player.getOffhandItem().is(ModContent.PHONE.get());
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    public static class LaptopMenu extends AbstractContainerMenu {
        public static final int INV_X = 8;
        public static final int INV_Y = 166;
        public static final int SALE_X = 186;
        public static final int SALE_Y = 184;

        public final SimpleContainer sale = new SimpleContainer(1);

        public LaptopMenu(int id, Inventory inv) {
            super(ModContent.LAPTOP_MENU.get(), id);
            addSlot(new Slot(sale, 0, SALE_X, SALE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !stack.isEmpty() && !Devices.isDevice(stack);
                }
            });
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
            }
            if (inv.player instanceof ServerPlayer sp) ModNetwork.syncFull(sp);
        }

        @Override
        public boolean stillValid(Player player) {
            return Devices.hasLaptop(player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = this.slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 1, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (Devices.isDevice(stack) || !moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return copy;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (player.level().isClientSide) return;
            ItemStack stack = sale.removeItemNoUpdate(0);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    public static class RackMenu extends AbstractContainerMenu {
        public final BlockPos pos;

        public RackMenu(int id, Inventory inv, BlockPos pos) {
            super(ModContent.RACK_MENU.get(), id);
            this.pos = pos;
            if (inv.player instanceof ServerPlayer sp) ModNetwork.syncFull(sp);
        }

        @Override
        public boolean stillValid(Player player) {
            return player.level().getBlockEntity(pos) != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
