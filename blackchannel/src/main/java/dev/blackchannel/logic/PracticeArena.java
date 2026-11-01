package dev.blackchannel.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Полигон: маленькая площадка под ногами игрока. Нужна, чтобы потрогать телефон,
 * ноутбук и пеленг без катки и без риска. Ничего не портит: строится в воздухе над игроком.
 */
public final class PracticeArena {
    private PracticeArena() {}

    public static void build(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cz = center.getZ();
        int y = Math.max(center.getY() + 2, 4);
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        // Пол 25×25
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), floor, 2);
                for (int dy = 0; dy <= 6; dy++) {
                    level.setBlock(new BlockPos(cx + dx, y + dy, cz + dz), Blocks.AIR.defaultBlockState(), 2);
                }
                if (Math.abs(dx) == 12 || Math.abs(dz) == 12) {
                    level.setBlock(new BlockPos(cx + dx, y, cz + dz), wall, 2);
                    level.setBlock(new BlockPos(cx + dx, y + 1, cz + dz), glass, 2);
                }
            }
        }
        // Две смотровые вышки: с них удобно ловить второй луч
        tower(level, cx - 8, cz + 8, y);
        tower(level, cx + 8, cz - 8, y);
        // Стойка и сундук с расходниками
        level.setBlock(new BlockPos(cx, y, cz + 10), Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx + 2, y, cz + 10), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(cx + 4, y, cz + 10), Blocks.ANVIL.defaultBlockState(), 2);
    }

    private static void tower(ServerLevel level, int x, int z, int y) {
        for (int dy = 0; dy <= 6; dy++) {
            level.setBlock(new BlockPos(x, y + dy, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(x + dx, y + 7, z + dz), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x + dx, y + 6, z + dz), (Math.abs(dx) == 1 && Math.abs(dz) == 1)
                        ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
