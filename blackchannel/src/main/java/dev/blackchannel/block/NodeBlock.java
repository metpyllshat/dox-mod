package dev.blackchannel.block;

import dev.blackchannel.data.NetData;
import dev.blackchannel.logic.Ops;
import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class NodeBlock extends BaseEntityBlock {
    public enum Kind { RACK, DISH, FARADAY, JAMMER, BEACON, DECOY }

    public final Kind kind;

    public NodeBlock(Kind kind, MapColor color) {
        super(properties(kind, color));
        this.kind = kind;
    }

    private static Properties properties(Kind kind, MapColor color) {
        Properties props = Properties.of().mapColor(color).sound(net.minecraft.world.level.block.SoundType.METAL)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
        if (kind == Kind.BEACON) {
            // Маяк отряда — главная цель: долго ломается и держит взрыв
            props.strength(60f, 1800f).requiresCorrectToolForDrops();
        } else {
            props.strength(3.5f, 8f);
        }
        return props;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NodeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModContent.NODE.get(), NodeBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof NodeBlockEntity be && placer != null) {
            be.owner = placer.getUUID();
            be.charge = 280;
            if (placer instanceof ServerPlayer sp) {
                NetData.Profile profile = NetData.get(sp).profile(sp.getUUID());
                be.code = profile.crew;
                if (kind == Kind.DECOY) {
                    be.fakeHandle = NetData.get(sp).uniqueHandle(sp.getRandom());
                    be.decoyExpire = level.getGameTime() + 20L * 60 * 8;
                }
            }
            be.setChanged();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof NodeBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (be.tryFuel(player, held)) {
            sp.displayClientMessage(Component.translatable("blackchannel.result.fuel", be.charge), true);
            return InteractionResult.CONSUME;
        }
        if (kind == Kind.RACK && (held.is(ModContent.PHONE.get()) || held.is(ModContent.LAPTOP.get())) && be.chargeDevice(held)) {
            sp.displayClientMessage(Component.translatable("blackchannel.result.rack_charged_device"), true);
            return InteractionResult.CONSUME;
        }
        if (kind == Kind.RACK) {
            if (player.isShiftKeyDown() && held.isEmpty()) {
                Ops.killSwitch(sp, pos);
                return InteractionResult.CONSUME;
            }
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new dev.blackchannel.menu.Menus.RackMenu(id, inv, pos),
                    Component.translatable("screen.blackchannel.rack")), pos);
            return InteractionResult.CONSUME;
        }
        if (kind == Kind.BEACON) {
            NetData.Profile profile = NetData.get(sp).profile(sp.getUUID());
            be.code = profile.crew;
            be.owner = sp.getUUID();
            be.setChanged();
            sp.displayClientMessage(Component.translatable("blackchannel.result.beacon_set", be.code.isEmpty() ? "-" : be.code), true);
            return InteractionResult.CONSUME;
        }
        if (kind == Kind.DECOY) {
            be.fakeHandle = NetData.get(sp).uniqueHandle(sp.getRandom());
            be.decoyExpire = level.getGameTime() + 20L * 60 * 8;
            be.owner = sp.getUUID();
            if (be.charge < 80) be.charge = 80;
            be.setChanged();
            sp.displayClientMessage(Component.translatable("blackchannel.result.decoy_set", be.fakeHandle), true);
            return InteractionResult.CONSUME;
        }
        sp.displayClientMessage(Component.translatable(be.powered() ? "blackchannel.result.node_on" : "blackchannel.result.node_off", be.charge), true);
        return InteractionResult.CONSUME;
    }
}
