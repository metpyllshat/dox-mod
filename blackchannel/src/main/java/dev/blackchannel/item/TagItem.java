package dev.blackchannel.item;

import dev.blackchannel.logic.Ops;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TagItem extends Item {
    public TagItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (player instanceof ServerPlayer sp) Ops.fileTag(sp, stack);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blackchannel.dog_tag"));
        if (!stack.hasTag() || !stack.getTag().contains("Handle")) return;
        String handle = stack.getTag().getString("Handle");
        String name = stack.getTag().getString("Name");
        tooltip.add(Component.literal(handle + " = " + (name.isEmpty() ? "?" : name)).withStyle(ChatFormatting.GRAY));
    }
}
