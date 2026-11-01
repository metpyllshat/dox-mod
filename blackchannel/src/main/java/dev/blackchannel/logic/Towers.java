package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Вышки связи: высокие мачты на карте. Сверху площадка с хорошим лутом,
 * внутри шахта с лестницей, поэтому забраться можно, а уйти тихо нельзя.
 */
public final class Towers {
    public static final int HEIGHT = 18;
    public static final int PLATFORM = 4;

    private Towers() {}

    /** Кольцо вокруг центра: 0.33 стороны карты, не ближе 180 к базе отряда. */
    public static void placeAll(ServerLevel level, MatchData match, RandomSource random) {
        int count = Config.TOWER_COUNT.get();
        if (count <= 0 || match.towers.size() >= count) return;
        int size = Math.max(2, match.size);
        double radius = size * 0.33;
        for (int i = match.towers.size(); i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i + Math.PI / count;
            int x = match.centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = match.centerZ + (int) Math.round(Math.sin(angle) * radius);
            if (nearBase(match, x, z, 180)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y, z);
            if (!match.inside(ground)) continue;
            build(level, match, ground);
            match.towers.add(ground);
            match.setDirty();
        }
    }

    private static boolean nearBase(MatchData match, int x, int z, int dist) {
        for (MatchData.Drop drop : match.anchors.values()) {
            if (drop == null) continue;
            if (Math.hypot(drop.x - x, drop.z - z) < dist) return true;
        }
        return false;
    }

    private static void build(ServerLevel level, MatchData match, BlockPos ground) {
        int x = ground.getX();
        int z = ground.getZ();
        int y = ground.getY();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        BlockState rail = Blocks.IRON_BARS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST);
        // Пятак в основании, чтобы мачта стояла на склоне
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) set(level, x + dx, y, z + dz, brick);
        }
        // Мачта 3×3 со шахтой и лестницей на западной стене
        for (int dy = 1; dy <= HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean wall = dx != 0 || dz != 0;
                    set(level, x + dx, y + dy, z + dz, wall ? brick : air);
                }
            }
            set(level, x, y + dy, z, ladder);
        }
        // Площадка сверху: пол, бортик, антенна
        int top = y + HEIGHT + 1;
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                boolean edge = Math.abs(dx) == PLATFORM || Math.abs(dz) == PLATFORM;
                if (edge) {
                    set(level, x + dx, top, z + dz, slab);
                    set(level, x + dx, top + 1, z + dz, rail);
                } else {
                    set(level, x + dx, top, z + dz, brick);
                    set(level, x + dx, top + 1, z + dz, air);
                    set(level, x + dx, top + 2, z + dz, air);
                }
            }
        }
        for (int dy = 2; dy <= 5; dy++) set(level, x - 2, top + dy, z - 2, rail);
        set(level, x, top + 1, z, air);
        BlockPos chest = new BlockPos(x + 1, top + 1, z + 1);
        set(level, chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST.defaultBlockState());
        if (Config.TOWER_LOOT.get()) Minigame.queueLoot(chest);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }
}
