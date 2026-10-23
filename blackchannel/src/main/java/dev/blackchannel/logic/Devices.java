package dev.blackchannel.logic;

import dev.blackchannel.registry.ModContent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Devices {
    private Devices() {}

    public static boolean isDevice(ItemStack stack) {
        return stack.is(ModContent.PHONE.get()) || stack.is(ModContent.LAPTOP.get());
    }

    public static void forEach(Player player, Consumer<ItemStack> fn) {
        for (ItemStack stack : player.getInventory().items) if (isDevice(stack)) fn.accept(stack);
        for (ItemStack stack : player.getInventory().offhand) if (isDevice(stack)) fn.accept(stack);
        for (ItemStack stack : Bridges.curios(player)) if (isDevice(stack)) fn.accept(stack);
    }

    public static boolean has(Player player, Item item) {
        if (count(player, item) > 0) return true;
        for (ItemStack stack : Bridges.curios(player)) if (stack.is(item)) return true;
        return false;
    }

    public static boolean hasDevice(Player player) {
        boolean[] found = {false};
        forEach(player, s -> found[0] = true);
        return found[0];
    }

    public static boolean hasLaptop(Player player) {
        return has(player, ModContent.LAPTOP.get());
    }

    public static int count(Player player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) n += stack.getCount();
        }
        for (ItemStack stack : Bridges.curios(player)) if (stack.is(item)) n += stack.getCount();
        return n;
    }

    public static boolean consume(Player player, Item item, int n) {
        if (player.getAbilities().instabuild) return true;
        if (count(player, item) < n) return false;
        int left = n;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        return left == 0;
    }

    public static int battery(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!stack.hasTag() || !stack.getTag().contains("Battery")) return 100;
        return Mth.clamp(stack.getTag().getInt("Battery"), 0, 100);
    }

    public static int battery(Player player) {
        if (player.getAbilities().instabuild) return 100;
        int[] best = {0};
        boolean[] any = {false};
        forEach(player, stack -> {
            any[0] = true;
            int b = battery(stack);
            if (b > best[0]) best[0] = b;
        });
        return any[0] ? best[0] : 0;
    }

    public static void drain(Player player, int amount) {
        if (amount <= 0 || player.getAbilities().instabuild) return;
        ItemStack[] pick = {ItemStack.EMPTY};
        forEach(player, stack -> {
            if (pick[0].isEmpty() && battery(stack) > 0) pick[0] = stack;
        });
        if (pick[0].isEmpty()) return;
        int next = Math.max(0, battery(pick[0]) - amount);
        pick[0].getOrCreateTag().putInt("Battery", next);
    }

    public static void setAll(Player player, int value) {
        int v = Mth.clamp(value, 0, 100);
        forEach(player, stack -> stack.getOrCreateTag().putInt("Battery", v));
    }

    public static void charge(Player player, int amount) {
        if (player.getAbilities().instabuild) {
            setAll(player, 100);
            return;
        }
        forEach(player, stack -> {
            int next = Mth.clamp(battery(stack) + amount, 0, 100);
            stack.getOrCreateTag().putInt("Battery", next);
        });
    }

    public static ItemStack sample(String handle, boolean encrypted) {
        ItemStack stack = new ItemStack(ModContent.SAMPLE.get());
        stack.getOrCreateTag().putString("Handle", handle == null ? "" : handle);
        stack.getOrCreateTag().putBoolean("Enc", encrypted);
        if (!encrypted) stack.getOrCreateTag().putString("Shown", handle == null ? "" : handle);
        return stack;
    }

    public static String sampleHandle(ItemStack stack) {
        if (!stack.is(ModContent.SAMPLE.get()) || !stack.hasTag()) return "";
        return stack.getTag().getString("Handle");
    }

    public static boolean sampleEncrypted(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Enc");
    }

    public static ItemStack takeSample(Player player, String handle, boolean allowEncrypted, boolean consumeZero) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModContent.SAMPLE.get())) continue;
            if (!handle.equals(sampleHandle(stack))) continue;
            boolean enc = sampleEncrypted(stack);
            if (enc && !allowEncrypted) continue;
            if (enc && consumeZero && !has(player, ModContent.ZERO.get())) continue;
            found = stack;
            break;
        }
        if (found.isEmpty()) return ItemStack.EMPTY;
        boolean enc = sampleEncrypted(found);
        if (enc && consumeZero && !player.getAbilities().instabuild) consume(player, ModContent.ZERO.get(), 1);
        ItemStack proof = found.copy();
        if (!player.getAbilities().instabuild) found.shrink(1);
        return proof;
    }

    public static List<ItemStack> all(Player player) {
        List<ItemStack> out = new ArrayList<>();
        forEach(player, out::add);
        return out;
    }
}
