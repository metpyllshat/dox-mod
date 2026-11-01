package dev.blackchannel.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Дефибриллятор: поднимает себя или тиммейта сразу, без пяти секунд стояния рядом.
 * Работает только внутри катки и тратится на один разряд.
 */
public class DefibItem extends Item {
    public DefibItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blackchannel.defib"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
