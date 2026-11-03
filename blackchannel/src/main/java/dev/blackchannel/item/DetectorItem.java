package dev.blackchannel.item;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.logic.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Металлоискатель. Нажал — он ищет ближайший схрон в радиусе detectorRadius:
 * если нашёл, пишет расстояние и направление стрелкой и пищит чаще, чем ближе схрон.
 * Ничего не нашёл — молчит. Каждое использование стоит прочности, ломается сам.
 * Работает только во время катки.
 */
public class DetectorItem extends Item {
    /** Пауза между включениями: иначе им можно сканировать каждый тик. */
    private static final int COOLDOWN_TICKS = 40;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public DetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        MatchData match = MatchData.get(sp.getServer());
        if (!match.active) {
            sp.displayClientMessage(Msg.paint(Component.translatable("blackchannel.detector.off")), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        ServerLevel sl = sp.serverLevel();
        int radius = Config.DETECTOR_RADIUS.get();
        double r2 = (double) radius * radius;
        BlockPos origin = sp.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        for (BlockPos pos : match.cacheChests) {
            if (pos == null || !sl.hasChunkAt(pos)) continue;
            if (!(sl.getBlockEntity(pos) instanceof ChestBlockEntity)) continue;
            double d2 = pos.distSqr(origin);
            if (d2 <= r2 && d2 < bestD2) {
                bestD2 = d2;
                best = pos;
            }
        }

        if (best == null) {
            sp.displayClientMessage(Msg.paint(Component.translatable("blackchannel.detector.quiet")), true);
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5f, 0.7f);
        } else {
            double dist = Math.sqrt(bestD2);
            // Направление: 0 — север (минус Z), по часовой стрелке
            double bearing = Math.atan2(best.getX() - origin.getX(), -(best.getZ() - origin.getZ()));
            int idx = (int) Math.round(bearing / (Math.PI / 4)) & 7;
            sp.displayClientMessage(Msg.paint(Component.translatable("blackchannel.detector.hit",
                    Math.round(dist), ARROWS[idx])), true);
            // Чем ближе схрон, тем выше и чаще писк (фактор 0..1)
            float closeness = (float) (1.0 - Math.min(1.0, dist / Math.max(1, radius)));
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 0.8f + closeness);
        }
        stack.hurtAndBreak(1, sp, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blackchannel.metal_detector"));
    }
}
