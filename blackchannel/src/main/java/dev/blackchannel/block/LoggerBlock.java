package dev.blackchannel.block;

import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LoggerBlock extends BaseEntityBlock {
    public LoggerBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6f).sound(SoundType.METAL));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoggerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModContent.LOGGER_BE.get(), LoggerBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (!(level.getBlockEntity(pos) instanceof LoggerBlockEntity be)) return;
        int charges = 3;
        if (stack.hasTag() && stack.getTag().contains("Charges")) charges = stack.getTag().getInt("Charges");
        be.charges = Math.max(0, Math.min(3, charges));
        if (placer instanceof ServerPlayer sp) be.owner = sp.getUUID();
        else if (placer != null) be.owner = placer.getUUID();
        be.setChanged();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        int charges = 3;
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof LoggerBlockEntity logger) charges = logger.charges;
        if (charges <= 0) return List.of();
        ItemStack stack = new ItemStack(ModContent.LOGGER_ITEM.get());
        stack.getOrCreateTag().putInt("Charges", charges);
        return List.of(stack);
    }
}
