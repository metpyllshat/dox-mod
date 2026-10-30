package dev.blackchannel.item;

import dev.blackchannel.logic.Ops;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ToolItem extends Item {
    public enum Kind {
        SCANNER, HASH, SIM, SAT, VPN, VPN2, IDS, HARDEN, EXPLOIT, ZERO, CONTRACT, SAMPLE, CELL
    }

    public final Kind kind;

    public ToolItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        switch (kind) {
            case SCANNER -> Ops.scanSelf(sp);
            case HASH -> {
                if (player.isShiftKeyDown()) Ops.withdraw(sp);
                else Ops.deposit(sp, stack);
            }
            case SIM -> {
                if (!player.isShiftKeyDown()) sp.displayClientMessage(Component.translatable("blackchannel.hint.sim_sneak"), true);
                else Ops.wipeFromSim(sp);
            }
            case SAMPLE -> sp.displayClientMessage(Component.translatable("blackchannel.hint.passive"), true);
            case SAT, VPN, VPN2, IDS, HARDEN, CELL -> Ops.install(sp, kind, stack);
            default -> sp.displayClientMessage(Component.translatable("blackchannel.hint.passive"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (kind != Kind.SCANNER) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (context.getPlayer() instanceof ServerPlayer sp) Ops.scanBlock(sp, context.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return kind == Kind.ZERO || kind == Kind.SAT || kind == Kind.CONTRACT;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blackchannel." + kind.name().toLowerCase()));
        if (kind == Kind.SAMPLE && stack.hasTag()) {
            if (stack.getTag().getBoolean("Enc")) tooltip.add(Component.translatable("tooltip.blackchannel.sample_enc"));
            else if (stack.getTag().contains("Shown")) tooltip.add(Component.literal(stack.getTag().getString("Shown")));
        }
    }
}
