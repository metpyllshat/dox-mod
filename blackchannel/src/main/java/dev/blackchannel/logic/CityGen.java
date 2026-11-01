package dev.blackchannel.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Коряжма в миниатюре. Настоящий город не перенести один в один, но его характер
 * читается: целлюлозно-бумажный комбинат с трубами на севере, панельные пятиэтажки
 * кварталами, частный сектор, широкие улицы, пустырь и стадион. Всё строится по сетке
 * вокруг центра карты, поверх рельефа, поэтому подходит под любую сид-карту.
 */
public final class CityGen {
    private static final int CELL = 32;
    private static final int STREET = 7;

    private CityGen() {}

    /** Возвращает число поставленных зданий. */
    public static int build(ServerLevel level, int cx, int cz, int side, RandomSource random) {
        int half = Math.max(120, Math.min(side / 2, 320));
        int houses = 0;
        BlockState asphalt = Blocks.STONE.defaultBlockState();
        BlockState curb = Blocks.SMOOTH_STONE.defaultBlockState();
        // Улицы: сетка с шагом CELL, ширина STREET. Центр квартала остаётся под застройку.
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                if (Math.abs((x - cx) % CELL) >= STREET / 2 + 1 && Math.abs((z - cz) % CELL) >= STREET / 2 + 1) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                level.setBlock(new BlockPos(x, y - 1, z), (Math.abs((x - cx) % CELL) == STREET / 2 + 1
                        || Math.abs((z - cz) % CELL) == STREET / 2 + 1) ? curb : asphalt, 2);
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Кварталы
        for (int qx = -half / CELL; qx <= half / CELL; qx++) {
            for (int qz = -half / CELL; qz <= half / CELL; qz++) {
                int bx = cx + qx * CELL;
                int bz = cz + qz * CELL;
                if (Math.abs(bx - cx) > half || Math.abs(bz - cz) > half) continue;
                float roll = random.nextFloat();
                // Север города — комбинат: длинные цеха и трубы
                if (bz < cz - half / 3) {
                    if (roll < 0.55f) {
                        chimney(level, bx + 6, bz + 6, random);
                        houses++;
                    } else if (roll < 0.8f) {
                        workshop(level, bx, bz, random);
                        houses++;
                    }
                } else if (roll < 0.45f) {
                    panel(level, bx, bz, random);
                    houses++;
                } else if (roll < 0.72f) {
                    wooden(level, bx, bz, random);
                    houses++;
                }
            }
        }
        return houses;
    }

    /** Панельная пятиэтажка: коробка 12×9, окна рядами, плоская крыша. */
    private static void panel(ServerLevel level, int bx, int bz, RandomSource random) {
        int w = 11 + random.nextInt(3);
        int d = 8 + random.nextInt(2);
        int floors = 4 + random.nextInt(2);
        int y = safeHeight(level, bx, bz);
        BlockState wall = random.nextBoolean() ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState trim = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState window = Blocks.GLASS.defaultBlockState();
        for (int floor = 0; floor < floors; floor++) {
            int fy = y + floor * 3;
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < d; dz++) {
                    boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                    boolean win = edge && (dx + dz) % 3 == 1 && floor > 0;
                    for (int dy = 0; dy < 3; dy++) {
                        BlockState state = edge ? (win && dy < 2 ? window : wall) : Blocks.AIR.defaultBlockState();
                        set(level, bx + dx, fy + dy, bz + dz, state);
                    }
                }
            }
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < d; dz++) {
                    if (dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1) set(level, bx + dx, fy + 3, bz + dz, trim);
                }
            }
        }
        // Крыша и подъезд
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) set(level, bx + dx, y + floors * 3 + 1, bz + dz, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (int dz = 0; dz < d; dz++) set(level, bx, y + 1, bz + dz, Blocks.AIR.defaultBlockState());
    }

    /** Частный сектор: деревянный дом с двускатной крышей и забором. */
    private static void wooden(ServerLevel level, int bx, int bz, RandomSource random) {
        int w = 6 + random.nextInt(3);
        int d = 5 + random.nextInt(2);
        int y = safeHeight(level, bx, bz);
        BlockState wall = random.nextBoolean() ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_STAIRS.defaultBlockState();
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                for (int dy = 0; dy < 3; dy++) {
                    set(level, bx + dx, y + dy, bz + dz, edge ? wall : Blocks.AIR.defaultBlockState());
                }
                // Крыша ступеньками
                set(level, bx + dx, y + 3, bz + dz, roof);
                if (dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1) set(level, bx + dx, y + 4, bz + dz, Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
        }
        for (int dz = 1; dz < d - 1; dz++) set(level, bx, y + 1, bz + dz, Blocks.AIR.defaultBlockState());
        // Забор по периметру участка
        for (int i = -1; i <= w; i++) {
            set(level, bx + i, y, bz - 2, Blocks.OAK_FENCE.defaultBlockState());
            set(level, bx + i, y, bz + d + 1, Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int i = -1; i <= d + 1; i++) {
            set(level, bx - 2, y, bz + i, Blocks.OAK_FENCE.defaultBlockState());
            set(level, bx + w + 1, y, bz + i, Blocks.OAK_FENCE.defaultBlockState());
        }
    }

    /** Цех комбината: длинный ангар с фонарём на крыше. */
    private static void workshop(ServerLevel level, int bx, int bz, RandomSource random) {
        int w = 16 + random.nextInt(8);
        int d = 10 + random.nextInt(6);
        int y = safeHeight(level, bx, bz);
        BlockState wall = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                for (int dy = 0; dy < 6; dy++) {
                    BlockState state = Blocks.AIR.defaultBlockState();
                    if (edge) state = (dy == 4) ? glass : wall;
                    set(level, bx + dx, y + dy, bz + dz, state);
                }
                set(level, bx + dx, y + 6, bz + dz, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int dz = 2; dz < 5; dz++) set(level, bx, y + 1, bz + dz, Blocks.AIR.defaultBlockState());
    }

    /** Труба комбината: 3×3, высотой 24, с красной полосой. */
    private static void chimney(ServerLevel level, int bx, int bz, RandomSource random) {
        int y = safeHeight(level, bx, bz);
        int h = 20 + random.nextInt(8);
        BlockState brick = Blocks.BRICKS.defaultBlockState();
        BlockState band = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int dy = 0; dy < h; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && dy < h - 1) continue;
                    BlockState state = (dy > h - 4) ? band : brick;
                    set(level, bx + dx, y + dy, bz + dz, state);
                }
            }
        }
    }

    private static int safeHeight(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }
}
