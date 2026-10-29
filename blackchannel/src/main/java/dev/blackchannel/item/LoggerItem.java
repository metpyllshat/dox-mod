package dev.blackchannel.item;

import dev.blackchannel.logic.Minigame;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LoggerItem extends BlockItem {
    public LoggerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && !context.getPlayer().isShiftKeyDown() && Minigame.isContainer(context.getLevel().getBlockState(context.getClickedPos()).getBlock())) {
            if (!context.getLevel().isClientSide) Minigame.attachLogger(context);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int charges = 3;
        if (stack.hasTag() && stack.getTag().contains("Charges")) charges = stack.getTag().getInt("Charges");
        tooltip.add(Component.translatable("tooltip.blackchannel.logger", charges));
    }
}
