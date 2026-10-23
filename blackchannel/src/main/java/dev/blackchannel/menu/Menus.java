package dev.blackchannel.menu;

import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
        public LaptopMenu(int id, Inventory inv) {
            super(ModContent.LAPTOP_MENU.get(), id);
            if (inv.player instanceof ServerPlayer sp) ModNetwork.syncFull(sp);
        }

        @Override
        public boolean stillValid(Player player) {
            return player.getMainHandItem().is(ModContent.LAPTOP.get()) || player.getOffhandItem().is(ModContent.LAPTOP.get());
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
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
