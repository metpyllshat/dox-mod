package dev.blackchannel.block;

import dev.blackchannel.logic.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TowerTerminalBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NORTH = Shapes.or(
            box(2, 0, 2, 14, 3, 14),
            box(3, 3, 7, 13, 12, 14),
            box(3, 9, 3, 13, 15, 8),
            box(4, 3, 1, 5, 13, 3));
    private static final VoxelShape SOUTH = Shapes.or(
            box(2, 0, 2, 14, 3, 14),
            box(3, 3, 2, 13, 12, 9),
            box(3, 9, 8, 13, 15, 13),
            box(11, 3, 13, 12, 13, 15));
    private static final VoxelShape EAST = Shapes.or(
            box(2, 0, 2, 14, 3, 14),
            box(2, 3, 3, 9, 12, 13),
            box(8, 9, 3, 13, 15, 13),
            box(13, 3, 4, 15, 13, 5));
    private static final VoxelShape WEST = Shapes.or(
            box(2, 0, 2, 14, 3, 14),
            box(7, 3, 3, 14, 12, 13),
            box(3, 9, 3, 8, 15, 13),
            box(1, 3, 11, 3, 13, 12));

    public TowerTerminalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .sound(SoundType.METAL)
                .strength(5.0f, 1200.0f)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && Towers.onUse(serverPlayer.getServer(), serverPlayer, pos)) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
