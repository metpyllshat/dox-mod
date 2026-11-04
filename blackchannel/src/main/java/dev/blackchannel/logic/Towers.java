package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Вышки связи: шесть мачт по кольцу 0.33 стороны карты.
 * Каждая даёт: пеленг 400, карту сектора 600 раз в 60с, 2 крипты/мин, чат 800 без стойки, перехват.
 * Взлом 20с (с эксплойтом 10с), шумит 60 на 400, ломается ЭМИ/C4 на 5 мин.
 */
public final class Towers {
    public static final int HEIGHT = 18;
    public static final int PLATFORM = 4;
    public static final int TERMINAL_OFFSET_Y = HEIGHT + 2;
    public static final int TERMINAL_OFFSET_Z = -2;

    private Towers() {}

    /** Сколько попыток даём на одну вышку, прежде чем сдаться. */
    private static final int ATTEMPTS = 60;
    /** Радиусы кольца в долях стороны карты. Первый — основной, как было в PR #47. */
    private static final double[] RADII = {0.33, 0.27, 0.39, 0.22, 0.44};
    /** Номер вышки, которую ставим сейчас, и номер попытки для неё. Сбрасываются в destroyAll. */
    private static int pendingSlot;
    private static int pendingAttempt;

    public static void resetRuntime(MatchData match) {
        pendingSlot = match != null && match.active && !match.towersPlaced ? match.towerInfos.size() : 0;
        pendingAttempt = 0;
        ZONE_INSIDE.clear();
    }

    /**
     * Постановка вышек по тикам, по одной за тик. Чанк дозагружается через Minigame.chunkStatus,
     * потому что точки кольца обычно далеко от игроков и их чанки не загружены.
     * Возвращает true, когда все вышки стоят или места под них не нашлось.
     */
    public static boolean placeStep(ServerLevel level, MatchData match) {
        int count = Config.TOWER_COUNT.get();
        if (count <= 0) return true;
        if (pendingSlot < match.towerInfos.size()) pendingSlot = match.towerInfos.size();
        int size = Math.max(2, match.size);
        // минимальное расстояние между вышками: половина дуги кольца, чтобы они не стояли кучно
        double arc = Math.PI * 2.0 * size * RADII[0] / count;
        double spacing = Math.max(24.0, arc * 0.5);
        int budget = 16;
        while (pendingSlot < count && budget-- > 0) {
            int attempt = pendingAttempt;
            double radius = size * RADII[attempt % RADII.length];
            // первый круг попыток — ровное кольцо как в PR #47; дальше угол шагает на 30°, чтобы обойти базы
            double angle = (Math.PI * 2.0 / count) * pendingSlot + Math.PI / count + (attempt / RADII.length) * (Math.PI / 6.0);
            int x = match.centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = match.centerZ + (int) Math.round(Math.sin(angle) * radius);
            // середина чанка: площадка 9×9 и шахта целиком в одном чанке
            x = (x & ~15) + 8;
            z = (z & ~15) + 8;
            int status = Minigame.chunkStatus(level, x >> 4, z >> 4);
            if (status == Minigame.CHUNK_WAIT) return false;
            BlockPos ground = null;
            if (status == Minigame.CHUNK_READY) {
                // держим чанк загруженным до конца проверки и стройки
                level.getChunk(x >> 4, z >> 4);
                int minBase = attempt < ATTEMPTS / 2 ? 180 : 110;
                ground = groundAt(level, match, x, z, minBase, spacing);
            }
            if (ground != null) {
                build(level, match, ground);
                match.towers.add(ground);
                MatchData.TowerInfo info = new MatchData.TowerInfo(ground);
                match.towerClockVersion = 1;
                long now = level.getGameTime();
                info.nextCryptoTick = now + 1200;
                info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
                match.towerInfos.add(info);
                match.setDirty();
                pendingSlot++;
                pendingAttempt = 0;
                break;
            }
            if (++pendingAttempt >= ATTEMPTS) {
                dev.blackchannel.BlackChannelMod.LOGGER.warn("Вышка {} не нашла места на карте (размер {})", pendingSlot + 1, size);
                pendingSlot++;
                pendingAttempt = 0;
            }
        }
        if (pendingSlot >= count) {
            pendingSlot = 0;
            pendingAttempt = 0;
            return true;
        }
        return false;
    }

    /** Подходящая земля для вышки в точке (x, z) или null. */
    private static BlockPos groundAt(ServerLevel level, MatchData match, int x, int z, int minBase, double spacing) {
        if (nearBase(match, x, z, minBase)) return null;
        for (BlockPos t : match.towers) {
            if (Math.hypot(t.getX() - x, t.getZ() - z) < spacing) return null;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        // ищем твёрдую землю вниз до 12 блоков, иначе не спавним (был баг — спавн под землей в пещере)
        for (int dy = 0; dy < 12; dy++) {
            BlockPos p = new BlockPos(x, y - dy, z);
            if (!level.getBlockState(p).isAir() && level.getBlockState(p).getBlock() != Blocks.WATER) { y = p.getY(); break; }
        }
        // Раньше порог был minBuild+4: на суперфлэте трава на y=-61, и вышки не ставились вовсе.
        // Отсекаем только совсем дно мира.
        if (y <= level.getMinBuildHeight() + 1) return null;
        BlockPos ground = new BlockPos(x, y, z);
        if (!match.inside(ground)) return null;
        if (!level.getBlockState(ground).isSolidRender(level, ground)) return null;
        if (!level.getBlockState(ground).getFluidState().isEmpty()
                || y + HEIGHT + 9 >= level.getMaxBuildHeight()) return null;

        int minY = y;
        int maxY = y;
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
                BlockState surfaceState = level.getBlockState(new BlockPos(x + dx, surface, z + dz));
                if (!surfaceState.getFluidState().isEmpty()) return null;
                minY = Math.min(minY, surface);
                maxY = Math.max(maxY, surface);
            }
        }
        if (maxY - minY > 3) return null;

        // Do not silently erase trees, buildings or block entities around the shaft/platform.
        int top = y + HEIGHT + 1;
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                for (int dy = top; dy <= top + 7; dy++) {
                    BlockPos check = new BlockPos(x + dx, dy, z + dz);
                    BlockState existing = level.getBlockState(check);
                    if (!replaceableForTower(existing) || level.getBlockEntity(check) != null) return null;
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = y + 1; dy < top; dy++) {
                    BlockPos check = new BlockPos(x + dx, dy, z + dz);
                    BlockState existing = level.getBlockState(check);
                    if (!replaceableForTower(existing) || level.getBlockEntity(check) != null) return null;
                }
            }
        }
        return ground;
    }

    private static boolean replaceableForTower(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
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
        // Основание не кладём: грунт под вышкой остаётся нетронутым, и снос не оставляет ямы.
        // Шахта стоит прямо на нём, с y+1 (проверка groundAt гарантирует твёрдый блок).
        for (int dy = 1; dy <= HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean wall = dx != 0 || dz != 0;
                    set(level, x + dx, y + dy, z + dz, wall ? brick : air);
                }
            }
            set(level, x, y + dy, z, ladder);
        }
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
        // Лестница проходит через центральный люк, а не упирается в сплошной пол.
        set(level, x, top, z, ladder);
        for (int dy = 2; dy <= 5; dy++) set(level, x - 2, top + dy, z - 2, rail);
        set(level, x, top + 1, z, air);
        BlockPos chest = new BlockPos(x + 1, top + 1, z + 1);
        set(level, chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST.defaultBlockState());
        // один сундук на вышку: только ресурсы, без оружия (пул cacheTower)
        if (Config.TOWER_LOOT.get()) {
            Minigame.queueTowerLoot(chest);
        } else if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box) {
            // Generic chunk scanning must not fill a chest disabled by the tower-loot setting.
            box.getPersistentData().putBoolean("bcLoot", true);
            box.setChanged();
        }
        BlockPos terminal = new BlockPos(x, top + 1, z + TERMINAL_OFFSET_Z);
        set(level, terminal.getX(), terminal.getY(), terminal.getZ(),
                ModContent.TOWER_TERMINAL.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        // Маячок цвета владельца — цветной бетон сверху антенны, изначально серый
        set(level, x - 2, top + 6, z - 2, Blocks.GRAY_CONCRETE.defaultBlockState());
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    /** Upgrades towers from the old lodestone terminal without rebuilding the structure. */
    public static void migrateTerminals(ServerLevel level, MatchData match) {
        boolean changed = false;
        if (match.towerClockVersion < 1) {
            long now = level.getGameTime();
            for (MatchData.TowerInfo info : match.towerInfos) {
                info.disabledUntil = 0;
                info.nextCryptoTick = now + 1200;
                info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
                clearHack(info);
            }
            match.towerClockVersion = 1;
            changed = true;
        }
        for (MatchData.TowerInfo info : match.towerInfos) {
            BlockPos oldTerminal = info.pos.above(HEIGHT + 2);
            BlockPos terminal = terminalPos(info);
            level.getChunk(terminal.getX() >> 4, terminal.getZ() >> 4);
            boolean legacy = level.getBlockState(oldTerminal).is(Blocks.LODESTONE)
                    || level.getBlockState(terminal).is(Blocks.LODESTONE);
            if (!legacy) continue;
            if (level.getBlockState(oldTerminal).is(Blocks.LODESTONE)) {
                level.setBlock(oldTerminal, Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(terminal, ModContent.TOWER_TERMINAL.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
            BlockPos hatch = info.pos.above(HEIGHT + 1);
            level.setBlock(hatch, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 3);
            changed = true;
        }
        if (changed) match.setDirty();
    }

    public static MatchData.TowerInfo at(MatchData match, BlockPos pos) {
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (terminalPos(info).equals(pos)) return info;
        }
        return null;
    }

    public static boolean isTerminal(MatchData match, BlockPos pos) {
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (terminalPos(info).equals(pos)) return true;
        }
        return false;
    }

    public static BlockPos terminalPos(MatchData.TowerInfo info) {
        return info.pos.offset(0, TERMINAL_OFFSET_Y, TERMINAL_OFFSET_Z);
    }

    /** ПКМ по терминалу — начать/продолжить взлом. Требует пустую руку или эксплойт, чтобы не ломать стройку. */
    public static boolean onUse(MinecraftServer server, ServerPlayer player, BlockPos clicked) {
        MatchData match = MatchData.get(server);
        if (!match.active || !Str.dim(player.level()).equals(match.dim)
                || !player.serverLevel().getBlockState(clicked).is(ModContent.TOWER_TERMINAL.get())) return false;
        MatchData.TowerInfo info = at(match, clicked);
        if (info == null) return false;
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && !held.is(ModContent.EXPLOIT.get())) return false;
        String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
        if (crew == null || crew.isEmpty()) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.need_crew")), true);
            return true;
        }
        long now = player.serverLevel().getGameTime();
        if (info.disabledUntil > now) {
            long left = (info.disabledUntil - now) / 20;
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.disabled", left)), true);
            return true;
        }
        if (crew.equals(info.owner)) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.own", info.owner)), true);
            return true;
        }
        // Владеем без взлома 20с (с эксплойтом 12с). Шум 60 на 400 — все в радиусе видят.
        boolean hasExploit = hasExploit(player);
        int need = Config.TOWER_HACK_SECONDS.get() * 20;
        if (hasExploit) need = Math.max(40, need * 3 / 5);
        if (info.hackCrew == null || !info.hackCrew.equals(crew) || info.hackStartTick == 0) {
            info.hackCrew = crew;
            info.hackStartTick = now;
            info.hackDuration = need;
            info.hacker = player.getUUID();
            match.setDirty();
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.hack_start", need / 20)), true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1f, 0.8f);
            // шум для пеленга: все в радиусе 400 получают сектор
            emitNoise(server, info.pos, 60, player);
            return true;
        }
        long elapsed = now - info.hackStartTick;
        if (info.hackDuration <= 0) info.hackDuration = need;
        long left = info.hackDuration - elapsed;
        if (left <= 0) {
            capture(server, info, crew, player);
        } else {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.hack_progress", (left + 19) / 20)), true);
            if (elapsed % 40 == 0) emitNoise(server, info.pos, 30, player);
        }
        return true;
    }

    private static void capture(MinecraftServer server, MatchData.TowerInfo info, String crew, ServerPlayer capturer) {
        MatchData match = MatchData.get(server);
        info.owner = crew;
        info.hackCrew = "";
        info.hackStartTick = 0;
        info.hackDuration = 0;
        info.hacker = null;
        info.disabledUntil = 0;
        ServerLevel level = Ops.levelOf(server, match.dim);
        long now = level == null ? server.overworld().getGameTime() : level.getGameTime();
        info.nextCryptoTick = now + 1200;
        info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
        match.setDirty();
        // очки за захват
        match.addScore(crew, 5);
        if (capturer != null) match.stats(capturer.getUUID()).towers++;
        Contracts.progress(server, match, crew, dev.blackchannel.data.Contract.TOWER, 1);
        // визуал — покрасить бетон
        if (level != null) {
            BlockPos beacon = new BlockPos(info.pos.getX() - 2, info.pos.getY() + HEIGHT + 7, info.pos.getZ() - 2);
            // цвет по отряду
            int idx = match.teamColorIdx.getOrDefault(crew, 11);
            BlockState colored = coloredConcrete(idx);
            level.setBlock(beacon, colored, 3);
        }
        String h = capturer == null ? "?" : dev.blackchannel.data.NetData.get(server).profile(capturer.getUUID()).handle;
        String name = capturer == null ? "?" : capturer.getGameProfile().getName();
        Component msg = Msg.paint(Component.translatable("blackchannel.tower.captured", h.isEmpty() ? name : h, crew, info.pos.getX(), info.pos.getZ()));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
        Announcer.voiceAll(server, "tower_captured");
    }

    private static BlockState coloredConcrete(int idx) {
        return switch (idx % 12) {
            case 0 -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case 1 -> Blocks.LIME_CONCRETE.defaultBlockState();
            case 2 -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case 3 -> Blocks.CYAN_CONCRETE.defaultBlockState();
            case 4 -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
            case 5 -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case 6 -> Blocks.GREEN_CONCRETE.defaultBlockState();
            case 7 -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case 8 -> Blocks.PURPLE_CONCRETE.defaultBlockState();
            case 9 -> Blocks.RED_CONCRETE.defaultBlockState();
            case 10 -> Blocks.WHITE_CONCRETE.defaultBlockState();
            default -> Blocks.GRAY_CONCRETE.defaultBlockState();
        };
    }

    private static boolean hasExploit(ServerPlayer player) {
        return Devices.has(player, ModContent.EXPLOIT.get());
    }

    private static void emitNoise(MinecraftServer server, BlockPos pos, int amount, ServerPlayer source) {
        if (source == null || amount <= 0) return;
        dev.blackchannel.data.NetData data = dev.blackchannel.data.NetData.get(server);
        Ops.noise(data.profile(source.getUUID()), amount);
        data.setDirty();
        onNoise(server, source, amount);
    }

    /** Тик вышек: крипта, сектор, перехват. */
    /** Чужие игроки, которые сейчас в зоне вышки (ключ — позиция вышки). Нужно, чтобы сообщать только о входе. */
    private static final Map<Long, Set<UUID>> ZONE_INSIDE = new HashMap<>();

    public static void tick(MinecraftServer server, MatchData match, ServerLevel level, long now) {
        tickCameras(server, match, level, now);
        if (!match.active || match.towerInfos.isEmpty()) return;
        zoneAlerts(server, match, now);
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.disabledUntil > now) continue;
            if (info.owner == null || info.owner.isEmpty()) continue;
            // Крипта 2/мин
            if (now >= info.nextCryptoTick) {
                info.nextCryptoTick = now + 1200;
                dev.blackchannel.data.NetData netData = dev.blackchannel.data.NetData.get(server);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    String crew = match.playerCrew.getOrDefault(p.getUUID(), "");
                    if (crew.equals(info.owner)) {
                        // прямое зачисление в профиль
                        netData.profile(p.getUUID()).crypto = Math.min(5000,
                                netData.profile(p.getUUID()).crypto + Config.TOWER_CRYPTO_PER_MIN.get());
                    }
                }
                netData.setDirty();
                match.setDirty();
            }
            // Карта сектора 600 раз в 60с: даёт точки с погрешностью
            if (now >= info.nextSectorTick) {
                info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
                int r = Config.TOWER_SECTOR_RADIUS.get();
                for (ServerPlayer enemy : server.getPlayerList().getPlayers()) {
                    if (enemy.serverLevel() != level) continue;
                    String eCrew = match.playerCrew.getOrDefault(enemy.getUUID(), "");
                    if (eCrew.equals(info.owner) || eCrew.isEmpty()) continue;
                    if (enemy.distanceToSqr(info.pos.getX(), enemy.getY(), info.pos.getZ()) > (long) r * r) continue;
                    if (match.permanentDead.contains(enemy.getUUID())) continue;
                    // даём владельцам пеленг на этого врага
                    for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
                        String oCrew = match.playerCrew.getOrDefault(owner.getUUID(), "");
                        if (!oCrew.equals(info.owner)) continue;
                        String handle = dev.blackchannel.data.NetData.get(server).profile(enemy.getUUID()).handle;
                        if (handle == null || handle.isEmpty()) continue;
                        // создаём intel с погрешностью 8% карты ~ trace base
                        int err = Math.max(20, Config.TOWER_SECTOR_RADIUS.get() / 8);
                        // смещаем как в Leak
                        int sx = enemy.blockPosition().getX() + owner.getRandom().nextInt(err * 2 + 1) - err;
                        int sz = enemy.blockPosition().getZ() + owner.getRandom().nextInt(err * 2 + 1) - err;
                        dev.blackchannel.logic.Ops.noteFix(server, owner.getUUID(), handle, dev.blackchannel.data.NetData.get(server).profile(enemy.getUUID()).lastName, Str.dim(enemy.level()), sx, enemy.blockPosition().getY(), sz, err, false, "blackchannel.log.tower_sector");
                    }
                }
                match.setDirty();
            }
        }
        // Захват ведёт конкретный игрок: ускорение эксплойтом хранится вместе с сессией.
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.hackCrew == null || info.hackCrew.isEmpty() || info.hackStartTick == 0) continue;
            if (info.hackDuration <= 0) info.hackDuration = Config.TOWER_HACK_SECONDS.get() * 20;
            ServerPlayer hacker = info.hacker == null ? null : server.getPlayerList().getPlayer(info.hacker);
            boolean valid = hacker != null && hacker.serverLevel() == level && !hacker.isDeadOrDying()
                    && !Minigame.isDowned(hacker)
                    && info.hackCrew.equals(match.playerCrew.getOrDefault(hacker.getUUID(), ""))
                    && hacker.blockPosition().distSqr(terminalPos(info)) <= 8L * 8L
                    && towerPresent(level, info);
            if (!valid) {
                clearHack(info);
                match.setDirty();
                continue;
            }
            if (now - info.hackStartTick >= info.hackDuration) capture(server, info, info.hackCrew, hacker);
        }
    }

    public static void destroyAll(ServerLevel level, MatchData match) {
        stopAllCameras(level.getServer());
        for (MatchData.TowerInfo inf : match.towerInfos) {
            BlockPos g = inf.pos;
            // Выгруженный чанк: setBlock в нём молча ничего не делает, поэтому грузим его до сноса
            for (int dx : new int[]{-PLATFORM, PLATFORM}) for (int dz : new int[]{-PLATFORM, PLATFORM}) {
                level.getChunk((g.getX() + dx) >> 4, (g.getZ() + dz) >> 4);
            }
            int top = g.getY()+HEIGHT+1;
            for(int dx=-PLATFORM; dx<=PLATFORM; dx++) for(int dz=-PLATFORM; dz<=PLATFORM; dz++) level.setBlock(new BlockPos(g.getX()+dx, top, g.getZ()+dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            for(int dy=1; dy<=HEIGHT+6; dy++) for(int dx=-1; dx<=1; dx++) for(int dz=-1; dz<=1; dz++) level.setBlock(new BlockPos(g.getX()+dx, g.getY()+dy, g.getZ()+dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(g.getX(), top+1, g.getZ()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            level.setBlock(new BlockPos(g.getX(), top+1, g.getZ()+1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            // перила по краю площадки и антенна с маячком, которых не было в очистке выше
            for (int dx=-PLATFORM; dx<=PLATFORM; dx++) for (int dz=-PLATFORM; dz<=PLATFORM; dz++) {
                level.setBlock(new BlockPos(g.getX()+dx, top+1, g.getZ()+dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
            for (int dy=2; dy<=6; dy++) level.setBlock(new BlockPos(g.getX()-2, top+dy, g.getZ()-2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        match.towerInfos.clear(); match.towers.clear(); match.towersPlaced=false; pendingSlot=0; pendingAttempt=0; match.setDirty();
        ZONE_INSIDE.clear();
    }
    /** EMP affects only towers in the same match dimension and keeps the caller's radius/duration. */
    public static void pulse(MatchData match, ServerLevel level, BlockPos center, int radius, int seconds) {
        if (match == null || level == null || !Str.dim(level).equals(match.dim) || radius <= 0 || seconds <= 0) return;
        long now = level.getGameTime();
        long radiusSqr = (long) radius * radius;
        int dur = seconds * 20;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.pos.distSqr(center) <= radiusSqr) {
                info.disabledUntil = now + dur;
                clearHack(info);
            }
        }
        match.setDirty();
    }

    private static void clearHack(MatchData.TowerInfo info) {
        info.hackCrew = "";
        info.hackStartTick = 0;
        info.hackDuration = 0;
        info.hacker = null;
    }

    /** Раз в секунду: владельцу вышки сообщение, когда чужой игрок входит в её зону. */
    private static void zoneAlerts(MinecraftServer server, MatchData match, long now) {
        if (now % 20L != 0L) return;
        long r = Config.TOWER_INTERCEPT_RADIUS.get();
        long r2 = r * r;
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null) return;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.owner == null || info.owner.isEmpty() || info.disabledUntil > now) {
                ZONE_INSIDE.remove(info.pos.asLong());
                continue;
            }
            Set<UUID> before = ZONE_INSIDE.computeIfAbsent(info.pos.asLong(), k -> new HashSet<>());
            Set<UUID> after = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.serverLevel() != level) continue;
                String crew = match.playerCrew.getOrDefault(p.getUUID(), "");
                if (crew.isEmpty() || crew.equals(info.owner)) continue;
                if (p.blockPosition().distSqr(info.pos) > r2) continue;
                after.add(p.getUUID());
                if (before.contains(p.getUUID())) continue;
                String handle = dev.blackchannel.data.NetData.get(server).profile(p.getUUID()).handle;
                if (handle == null || handle.isEmpty()) handle = "?";
                for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
                    if (!info.owner.equals(match.playerCrew.get(owner.getUUID()))) continue;
                    owner.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.tower.zone_enter",
                            info.pos.getX(), info.pos.getZ(), handle)));
                    owner.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.6f, 1.0f);
                }
            }
            before.clear();
            before.addAll(after);
        }
    }

    /** Перехват: владелец видит чужие действия в радиусе. */
    public static void intercept(MinecraftServer server, MatchData match, ServerPlayer actor, String action) {
        if (actor == null || action == null) return;
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null || actor.serverLevel() != level) return;
        long now = level.getGameTime();
        BlockPos at = actor.blockPosition();
        String crew = match.playerCrew.getOrDefault(actor.getUUID(), "");
        String handle = dev.blackchannel.data.NetData.get(server).profile(actor.getUUID()).handle;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.owner == null || info.owner.isEmpty() || info.owner.equals(crew)) continue;
            if (info.disabledUntil > now) continue;
            if (at.distSqr(info.pos) > (long) Config.TOWER_INTERCEPT_RADIUS.get() * Config.TOWER_INTERCEPT_RADIUS.get()) continue;
            for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
                String oCrew = match.playerCrew.getOrDefault(owner.getUUID(), "");
                if (!oCrew.equals(info.owner)) continue;
                owner.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.tower.intercept", action, handle, at.getX(), at.getZ(), info.pos.getX(), info.pos.getZ())));
                owner.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.7f, 1.5f);
                // пеленг 400 уже даётся через общий механизм — дополнительно даём сектор
                // создаём лёгкий intel без точки — только сообщение
            }
        }
    }

    /** Чат без стойки в радиусе 800 от своей вышки. */
    public static boolean hasTowerChat(MinecraftServer server, MatchData match, ServerPlayer player) {
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null || player.serverLevel() != level) return false;
        long now = level.getGameTime();
        String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
        if (crew == null || crew.isEmpty()) return false;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (!crew.equals(info.owner)) continue;
            if (info.disabledUntil > now) continue;
            if (player.blockPosition().distSqr(info.pos) <= (long) Config.TOWER_CHAT_RADIUS.get() * Config.TOWER_CHAT_RADIUS.get()) return true;
        }
        return false;
    }


    // ---------- безопасный обзор от вышки ----------

    private static final int CAM_TICKS = 20 * 60;
    private static final int CAM_CHUNK_RADIUS = 1;
    private static final int CAM_ENTITY_RADIUS = 15;
    private static final int CAM_CHECK_TICKS = 5;
    private static final int CAM_CHUNK_REFRESH_TICKS = 100;
    private static final int MAX_CAMERA_STREAMS = 2;
    private static final int RESYNC_CHUNKS_PER_TICK = 1;
    private static final int RESYNC_IMMEDIATE_RADIUS = 1;
    private static final int RESYNC_MAX_CHUNKS = 1024;
    private static final int RESYNC_MAX_TICKS = 20 * 60;
    private static final float CAM_PITCH = 18f;
    private static final TicketType<UUID> CAMERA_TICKET = TicketType.create(
            "blackchannel_camera", Comparator.<UUID>naturalOrder(), CAM_TICKS + 200);
    private static final Map<UUID, CameraSession> CAMERAS = new HashMap<>();
    private static final Map<UUID, ChunkResync> CHUNK_RESYNCS = new HashMap<>();

    // Keys written by the removed teleporting implementation. They are cleared once on upgrade.
    private static final String LEGACY_CAM_END = "bc_cam_end";
    private static final String LEGACY_CAM_RET = "bc_cam_ret";
    private static final String LEGACY_CAM_MARKER = "bc_cam_marker";

    private static final class CameraSession {
        final UUID playerId;
        final BlockPos tower;
        final BlockPos camera;
        final Vec3 body;
        final ChunkPos bodyChunk;
        final ChunkPos cameraChunk;
        final long endTick;
        final boolean remote;

        CameraSession(ServerPlayer player, MatchData.TowerInfo info, BlockPos camera, long endTick, boolean remote) {
            this.playerId = player.getUUID();
            this.tower = info.pos.immutable();
            this.camera = camera.immutable();
            this.body = player.position();
            this.bodyChunk = player.chunkPosition();
            this.cameraChunk = new ChunkPos(camera);
            this.endTick = endTick;
            this.remote = remote;
        }
    }

    private static final class ChunkResync {
        final String dimension;
        final int viewDistance;
        final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
        final long deadline;

        ChunkResync(String dimension, ChunkPos origin, int viewDistance, long deadline) {
            this.dimension = dimension;
            this.viewDistance = viewDistance;
            this.deadline = deadline;
            List<ChunkPos> add = new ArrayList<>();
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= RESYNC_IMMEDIATE_RADIUS) continue;
                    add.add(new ChunkPos(origin.x + dx, origin.z + dz));
                }
            }
            add.sort(Comparator.comparingInt(pos -> Math.max(
                    Math.abs(pos.x - origin.x), Math.abs(pos.z - origin.z))));
            pending.addAll(add.subList(0, Math.min(RESYNC_MAX_CHUNKS, add.size())));
        }
    }

    /** Starts a render-only camera. The ServerPlayer position, effects and game mode stay untouched. */
    public static void camera(ServerPlayer player, dev.blackchannel.data.NetData.Profile profile) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (isCamActive(player)) {
            stopCamera(player, true);
            return;
        }
        if (CAMERAS.size() >= MAX_CAMERA_STREAMS || CHUNK_RESYNCS.containsKey(player.getUUID())) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_busy")), true);
            return;
        }
        MatchData match = MatchData.get(server);
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (!match.active || level == null || player.serverLevel() != level || player.isSpectator()
                || player.isDeadOrDying() || !player.onGround() || Minigame.isDowned(player)
                || match.permanentDead.contains(player.getUUID())) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_unavailable")), true);
            return;
        }
        if (!(player.containerMenu instanceof Menus.PhoneMenu) || Devices.battery(phoneInHand(player)) <= 0) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_phone")), true);
            return;
        }
        String crew = match.playerCrew.getOrDefault(player.getUUID(), profile.crew == null ? "" : profile.crew);
        long now = level.getGameTime();
        MatchData.TowerInfo owned = null;
        double best = Double.MAX_VALUE;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (!crew.equals(info.owner) || info.disabledUntil > now) continue;
            double distance = horizontalDistanceSqr(player.position(), info.pos);
            if (distance < best) {
                best = distance;
                owned = info;
            }
        }
        if (owned == null) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.no_own")), true);
            return;
        }
        if (!loadTowerPresent(level, owned)) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_unavailable")), true);
            return;
        }
        int cost = Config.TOWER_CAM_COST.get();
        if (profile.crypto < cost) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.need_crypto", cost)), true);
            return;
        }

        BlockPos camera = cameraPos(owned);
        ChunkPos cameraChunk = new ChunkPos(camera);
        int viewDistance = Math.max(2, server.getPlayerList().getViewDistance());
        boolean remote = Math.max(Math.abs(cameraChunk.x - player.chunkPosition().x),
                Math.abs(cameraChunk.z - player.chunkPosition().z)) >= viewDistance;
        CameraSession session = new CameraSession(player, owned, camera, now + CAM_TICKS, remote);
        CAMERAS.put(player.getUUID(), session);
        float yaw = yawTowards(camera, match.centerX, match.centerZ);
        player.closeContainer();
        // Freeze the client before changing its only chunk-cache center.
        ModNetwork.sendCam(player, true, camera.getX(), camera.getY(), camera.getZ(), yaw, CAM_PITCH, CAM_TICKS);

        if (remote) {
            try {
                openRemoteView(player, level, session);
            } catch (RuntimeException error) {
                CAMERAS.remove(player.getUUID());
                try {
                    restoreRemoteView(player, level, session);
                } catch (RuntimeException restoreError) {
                    removeCameraTicket(level, session);
                    player.connection.send(new ClientboundSetChunkCacheCenterPacket(
                            session.bodyChunk.x, session.bodyChunk.z));
                }
                ModNetwork.sendCam(player, false, 0, 0, 0, 0f, 0f, 0);
                player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_fail")), true);
                dev.blackchannel.BlackChannelMod.LOGGER.warn("Tower camera stream failed at {}: {}", camera, error.toString());
                return;
            }
        }
        profile.crypto -= cost;
        dev.blackchannel.data.NetData.get(server).setDirty();
        ModNetwork.syncStatus(player);
        player.displayClientMessage(Msg.paint(Component.translatable(
                "blackchannel.tower.cam_ok", owned.pos.getX(), owned.pos.getZ(), CAM_TICKS / 20)), false);
        player.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.MASTER, 1f, 1f);
    }

    private static void tickCameras(MinecraftServer server, MatchData match, ServerLevel level, long now) {
        if (CAMERAS.isEmpty() || now % CAM_CHECK_TICKS != 0L) return;
        for (CameraSession session : new ArrayList<>(CAMERAS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                removeCameraTicket(level, session);
                CAMERAS.remove(session.playerId);
                continue;
            }
            MatchData.TowerInfo info = towerAtBase(match, session.tower);
            String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
            var profile = dev.blackchannel.data.NetData.get(server).profile(player.getUUID());
            boolean valid = match.active && player.serverLevel() == level && !player.isDeadOrDying()
                    && !player.isSpectator() && player.onGround() && !Minigame.isDowned(player)
                    && !match.permanentDead.contains(player.getUUID())
                    && info != null && crew.equals(info.owner) && info.disabledUntil <= now
                    && towerPresent(level, info) && player.position().distanceToSqr(session.body) <= 4.0
                    && Devices.battery(phoneInHand(player)) > 0
                    && !SignalEnv.emp(profile, server) && profile.bombUntil <= Ops.now(server)
                    && now < session.endTick;
            if (!valid) {
                stopCamera(player, false);
                continue;
            }
            try {
                sendCameraTargets(server, player, level, session, match);
                if (session.remote) {
                    if (now % CAM_CHUNK_REFRESH_TICKS == 0L) {
                        sendChunkSquare(player, level, session.cameraChunk, CAM_CHUNK_RADIUS);
                    }
                }
            } catch (RuntimeException error) {
                dev.blackchannel.BlackChannelMod.LOGGER.warn("Tower camera update failed for {}: {}",
                        player.getGameProfile().getName(), error.toString());
                stopCamera(player, false);
            }
        }
    }

    private static void openRemoteView(ServerPlayer player, ServerLevel level, CameraSession session) {
        level.getChunkSource().addRegionTicket(CAMERA_TICKET, session.cameraChunk,
                CAM_CHUNK_RADIUS + 1, session.playerId);
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(session.cameraChunk.x, session.cameraChunk.z));
        sendChunkSquare(player, level, session.cameraChunk, CAM_CHUNK_RADIUS);
    }

    private static void restoreRemoteView(ServerPlayer player, ServerLevel level, CameraSession session) {
        if (!session.remote) return;
        ChunkPos bodyChunk = player.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(bodyChunk.x, bodyChunk.z));
        sendChunkSquare(player, level, bodyChunk, RESYNC_IMMEDIATE_RADIUS);

        // Resend the remaining tracked view over subsequent ticks to avoid a 441+ packet burst.
        int viewDistance = Math.max(2, player.getServer().getPlayerList().getViewDistance());
        CHUNK_RESYNCS.put(player.getUUID(), new ChunkResync(
                Str.dim(level), bodyChunk, viewDistance, level.getGameTime() + RESYNC_MAX_TICKS));
        removeCameraTicket(level, session);
    }

    private static void sendChunkSquare(ServerPlayer player, ServerLevel level, ChunkPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                sendChunk(player, level, new ChunkPos(center.x + dx, center.z + dz));
            }
        }
    }

    private static void sendChunk(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunk(pos.x, pos.z);
        player.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, level.getChunkSource().getLightEngine(), null, null));
    }

    private static boolean sendLoadedChunk(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) {
            player.connection.send(new ClientboundLevelChunkWithLightPacket(
                    chunk, level.getChunkSource().getLightEngine(), null, null));
            return true;
        }
        return false;
    }

    private static void sendCameraTargets(MinecraftServer server, ServerPlayer player, ServerLevel level,
                                          CameraSession session, MatchData match) {
        Vec3 center = Vec3.atCenterOf(session.camera);
        AABB area = new AABB(center.x - CAM_ENTITY_RADIUS, center.y - 32, center.z - CAM_ENTITY_RADIUS,
                center.x + CAM_ENTITY_RADIUS, center.y + 32, center.z + CAM_ENTITY_RADIUS);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !entity.isInvisible());
        entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        String viewerCrew = match.playerCrew.getOrDefault(player.getUUID(), "");
        List<ModNetwork.CamTarget> targets = new ArrayList<>();
        for (LivingEntity entity : entities) {
            Vec3 targetPos = new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
            if (level.clip(new ClipContext(center, targetPos, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            int kind = 2;
            String label = "";
            if (entity instanceof ServerPlayer target) {
                if (target.isSpectator()) continue;
                String targetCrew = match.playerCrew.getOrDefault(target.getUUID(), "");
                kind = !viewerCrew.isEmpty() && viewerCrew.equals(targetCrew) ? 1 : 0;
                if (kind == 1) {
                    var targetProfile = dev.blackchannel.data.NetData.get(server).profile(target.getUUID());
                    label = targetProfile.handle == null || targetProfile.handle.isEmpty()
                            ? target.getGameProfile().getName() : targetProfile.handle;
                }
            }
            targets.add(new ModNetwork.CamTarget((float) entity.getX(),
                    (float) (entity.getY() + entity.getBbHeight() * 0.5), (float) entity.getZ(), label, kind));
            if (targets.size() >= 32) break;
        }
        ModNetwork.sendCamTargets(player, targets);
    }

    private static void removeCameraTicket(ServerLevel level, CameraSession session) {
        if (session.remote) {
            level.getChunkSource().removeRegionTicket(CAMERA_TICKET, session.cameraChunk,
                    CAM_CHUNK_RADIUS + 1, session.playerId);
        }
    }

    private static MatchData.TowerInfo towerAtBase(MatchData match, BlockPos base) {
        for (MatchData.TowerInfo info : match.towerInfos) if (info.pos.equals(base)) return info;
        return null;
    }

    private static boolean towerPresent(ServerLevel level, MatchData.TowerInfo info) {
        BlockPos pos = terminalPos(info);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockState(pos).is(ModContent.TOWER_TERMINAL.get());
    }

    private static boolean loadTowerPresent(ServerLevel level, MatchData.TowerInfo info) {
        BlockPos pos = terminalPos(info);
        return level.getChunk(pos.getX() >> 4, pos.getZ() >> 4)
                .getBlockState(pos).is(ModContent.TOWER_TERMINAL.get());
    }

    private static ItemStack phoneInHand(ServerPlayer player) {
        if (player.getMainHandItem().is(ModContent.PHONE.get())) return player.getMainHandItem();
        if (player.getOffhandItem().is(ModContent.PHONE.get())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private static BlockPos cameraPos(MatchData.TowerInfo info) {
        return info.pos.offset(-2, HEIGHT + 9, -2);
    }

    private static double horizontalDistanceSqr(Vec3 from, BlockPos to) {
        double dx = from.x - to.getX();
        double dz = from.z - to.getZ();
        return dx * dx + dz * dz;
    }

    private static float yawTowards(BlockPos from, int x, int z) {
        double dx = x + 0.5 - (from.getX() + 0.5);
        double dz = z + 0.5 - (from.getZ() + 0.5);
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Click or CAMERA while viewing exits. */
    public static boolean camExit(ServerPlayer player) {
        if (!isCamActive(player)) return false;
        stopCamera(player, true);
        return true;
    }

    /** Damage applies to the normal body and then terminates the feed. */
    public static void camHurt(ServerPlayer player) {
        if (isCamActive(player)) stopCamera(player, false);
    }

    /** Удар током: тот, кто начал ломать терминал, получает урон (не чаще раза в секунду). */
    public static void terminalHit(ServerPlayer player, BlockPos pos) {
        MatchData match = MatchData.get(player.getServer());
        if (!match.active || !Str.dim(player.level()).equals(match.dim) || !isTerminal(match, pos)
                || !player.serverLevel().getBlockState(pos).is(ModContent.TOWER_TERMINAL.get())) return;
        CompoundTag data = player.getPersistentData();
        long now = Ops.now(player.getServer());
        if (now < data.getLong("bc_shock_next")) return;
        data.putLong("bc_shock_next", now + 20);
        float dmg = (float) Config.TOWER_SHOCK_DAMAGE.get().doubleValue();
        if (dmg > 0f) player.hurt(player.damageSources().magic(), dmg);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.shock")), true);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 1f, 0.5f);
    }

    /**
     * Ломание терминала не уничтожает его: блок остаётся, а вышка отключается на
     * towerBreakDisableSeconds и потом работает снова. Возвращает true, если событие обработано.
     */
    public static boolean onTerminalBreak(MinecraftServer server, ServerPlayer breaker, BlockPos pos) {
        MatchData match = MatchData.get(server);
        if (!match.active || breaker == null || !Str.dim(breaker.level()).equals(match.dim)
                || !isTerminal(match, pos)
                || !breaker.serverLevel().getBlockState(pos).is(ModContent.TOWER_TERMINAL.get())) return false;
        MatchData.TowerInfo info = at(match, pos);
        if (info == null) return false;
        long now = breaker.serverLevel().getGameTime();
        info.disabledUntil = now + Config.TOWER_BREAK_DISABLE_SECONDS.get() * 20L;
        clearHack(info);
        match.setDirty();
        if (breaker != null) {
            breaker.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.broken", Config.TOWER_BREAK_DISABLE_SECONDS.get())), true);
        }
        return true;
    }

    /** Explosions disable a terminal instead of deleting the block and orphaning tower state. */
    public static void onExplosion(ServerLevel level, Vec3 center, List<BlockPos> affected) {
        if (level == null || center == null || affected == null) return;
        MatchData match = MatchData.get(level.getServer());
        if (!match.active || !Str.dim(level).equals(match.dim)) return;
        long now = level.getGameTime();
        boolean changed = false;
        for (MatchData.TowerInfo info : match.towerInfos) {
            BlockPos pos = terminalPos(info);
            Vec3 target = Vec3.atCenterOf(pos);
            var ray = level.clip(new ClipContext(center, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, null));
            boolean hit = affected.contains(pos) || (center.distanceToSqr(target) <= 5.0 * 5.0
                    && ray.getBlockPos().equals(pos));
            if (!hit || !level.getBlockState(pos).is(ModContent.TOWER_TERMINAL.get())) continue;
            affected.removeIf(pos::equals);
            info.disabledUntil = Math.max(info.disabledUntil,
                    now + Config.TOWER_BREAK_DISABLE_SECONDS.get() * 20L);
            clearHack(info);
            changed = true;
        }
        if (changed) match.setDirty();
    }

    public static boolean isCamActive(ServerPlayer player) {
        return player != null && CAMERAS.containsKey(player.getUUID());
    }

    private static void stopCamera(ServerPlayer player, boolean notify) {
        CameraSession session = CAMERAS.remove(player.getUUID());
        if (session == null) {
            clearLegacyCamera(player);
            return;
        }
        MinecraftServer server = player.getServer();
        ServerLevel matchLevel = server == null ? null : Ops.levelOf(server, MatchData.get(server).dim);
        if (matchLevel != null) {
            try {
                if (player.serverLevel() == matchLevel) restoreRemoteView(player, matchLevel, session);
                else removeCameraTicket(matchLevel, session);
            } catch (RuntimeException error) {
                removeCameraTicket(matchLevel, session);
                if (player.serverLevel() == matchLevel) {
                    player.connection.send(new ClientboundSetChunkCacheCenterPacket(
                            player.chunkPosition().x, player.chunkPosition().z));
                }
                dev.blackchannel.BlackChannelMod.LOGGER.warn("Tower camera cleanup failed for {}: {}",
                        player.getGameProfile().getName(), error.toString());
            }
        }
        ModNetwork.sendCam(player, false, 0, 0, 0, 0f, 0f, 0);
        if (notify) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_off")), false);
        }
    }

    public static void stopAllCameras(MinecraftServer server) {
        if (server == null || CAMERAS.isEmpty()) return;
        ServerLevel level = Ops.levelOf(server, MatchData.get(server).dim);
        for (UUID id : new ArrayList<>(CAMERAS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) stopCamera(player, false);
            else {
                CameraSession session = CAMERAS.remove(id);
                if (level != null && session != null) removeCameraTicket(level, session);
            }
        }
    }

    /** Disconnect/server-stop cleanup: never serializes chunks into a closing connection. */
    public static void disconnectCamera(ServerPlayer player) {
        if (player == null) return;
        CameraSession session = CAMERAS.remove(player.getUUID());
        CHUNK_RESYNCS.remove(player.getUUID());
        if (session == null || player.getServer() == null) return;
        ServerLevel level = Ops.levelOf(player.getServer(), MatchData.get(player.getServer()).dim);
        if (level != null) removeCameraTicket(level, session);
    }

    public static void discardAllCameras(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = Ops.levelOf(server, MatchData.get(server).dim);
        if (level != null) {
            for (CameraSession session : CAMERAS.values()) removeCameraTicket(level, session);
        }
        CAMERAS.clear();
        CHUNK_RESYNCS.clear();
    }

    /** Restores stale body chunks in bounded batches after a remote feed closes. */
    public static void tickChunkResync(MinecraftServer server) {
        if (server == null || CHUNK_RESYNCS.isEmpty()) return;
        for (var entry : new ArrayList<>(CHUNK_RESYNCS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ChunkResync resync = entry.getValue();
            ServerLevel level = Ops.levelOf(server, resync.dimension);
            if (player == null || level == null || player.serverLevel() != level) {
                CHUNK_RESYNCS.remove(entry.getKey());
                continue;
            }
            ChunkPos current = player.chunkPosition();
            if (level.getGameTime() >= resync.deadline) {
                CHUNK_RESYNCS.remove(entry.getKey());
                continue;
            }
            int sent = 0;
            int attempts = 0;
            int maxAttempts = Math.min(resync.pending.size(), RESYNC_CHUNKS_PER_TICK * 4);
            while (sent < RESYNC_CHUNKS_PER_TICK && attempts++ < maxAttempts && !resync.pending.isEmpty()) {
                ChunkPos next = resync.pending.removeFirst();
                if (Math.max(Math.abs(next.x - current.x), Math.abs(next.z - current.z)) > resync.viewDistance) continue;
                if (sendLoadedChunk(player, level, next)) {
                    sent++;
                } else {
                    resync.pending.addLast(next);
                }
            }
            if (resync.pending.isEmpty()) CHUNK_RESYNCS.remove(entry.getKey());
        }
    }

    /** Idempotent cleanup used by login/death/logout and by old saves. */
    public static void restoreCam(ServerPlayer player) {
        if (isCamActive(player)) stopCamera(player, false);
        clearLegacyCamera(player);
    }

    private static void clearLegacyCamera(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(LEGACY_CAM_END)) return;
        int[] ret = data.getIntArray(LEGACY_CAM_RET);
        Entity marker = null;
        if (data.hasUUID(LEGACY_CAM_MARKER) && player.getServer() != null) {
            UUID markerId = data.getUUID(LEGACY_CAM_MARKER);
            for (ServerLevel level : player.getServer().getAllLevels()) {
                marker = level.getEntity(markerId);
                if (marker != null) break;
            }
        }
        if (marker != null) marker.discard();
        data.remove(LEGACY_CAM_END);
        data.remove(LEGACY_CAM_RET);
        data.remove(LEGACY_CAM_MARKER);
        data.remove("bc_cam_mode");
        data.remove("bc_cam_holdx");
        data.remove("bc_cam_holdy");
        data.remove("bc_cam_holdz");
        data.remove("bc_cam_move_tick");
        player.removeEffect(MobEffects.INVISIBILITY);
        if (ret.length >= 3 && !player.isDeadOrDying()) {
            player.teleportTo(player.serverLevel(), ret[0] + 0.5, ret[1], ret[2] + 0.5, player.getYRot(), player.getXRot());
        }
        ModNetwork.sendCam(player, false, 0, 0, 0, 0f, 0f, 0);
    }

    public static void scan(ServerPlayer player, dev.blackchannel.data.NetData.Profile profile) {
        MinecraftServer server = player.getServer();
        MatchData match = MatchData.get(server);
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (!match.active || level == null || player.serverLevel() != level || player.isDeadOrDying()
                || player.isSpectator() || Minigame.isDowned(player)
                || !(player.containerMenu instanceof Menus.PhoneMenu)
                || Devices.battery(phoneInHand(player)) <= 0) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_unavailable")), true);
            return;
        }
        String crew = match.playerCrew.getOrDefault(player.getUUID(), profile.crew == null ? "" : profile.crew);
        long now = level.getGameTime();
        MatchData.TowerInfo owned = null;
        double best = Double.MAX_VALUE;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (!crew.equals(info.owner) || info.disabledUntil > now) continue;
            double distance = horizontalDistanceSqr(player.position(), info.pos);
            if (distance < best) { best = distance; owned = info; }
        }
        if (owned==null) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.no_own")), true); return; }
        int cost = Config.TOWER_SCAN_COST.get();
        if (profile.crypto < cost) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.need_crypto", cost)), true); return; }
        profile.crypto -= cost;
        int r = Config.TOWER_SECTOR_RADIUS.get();
        int err = Math.max(15, r/10);
        for (ServerPlayer enemy : server.getPlayerList().getPlayers()) {
            if (enemy==player) continue;
            if (enemy.serverLevel() != level) continue;
            if (crew.equals(match.playerCrew.getOrDefault(enemy.getUUID(), ""))) continue;
            if (enemy.distanceToSqr(owned.pos.getX(), enemy.getY(), owned.pos.getZ()) > (long)r*r) continue;
            if (match.permanentDead.contains(enemy.getUUID())) continue;
            String handle = dev.blackchannel.data.NetData.get(server).profile(enemy.getUUID()).handle;
            if (handle==null||handle.isEmpty()) continue;
            int sx = enemy.blockPosition().getX() + player.getRandom().nextInt(err*2+1)-err;
            int sz = enemy.blockPosition().getZ() + player.getRandom().nextInt(err*2+1)-err;
            Ops.noteFix(server, player.getUUID(), handle, dev.blackchannel.data.NetData.get(server).profile(enemy.getUUID()).lastName, Str.dim(enemy.level()), sx, enemy.blockPosition().getY(), sz, err, false, "blackchannel.log.tower_sector");
        }
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.scan_ok", r)), true);
        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.MASTER, 1f, 1.2f);
    }

    /** Пеленг на шум 400 — даёт владельцам вышек сектор на шумящего. */
    public static void onNoise(MinecraftServer server, ServerPlayer noiser, int amount) {
        if (noiser == null || amount <= 0) return;
        MatchData match = MatchData.get(server);
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (!match.active || level == null || noiser.serverLevel() != level) return;
        long now = level.getGameTime();
        BlockPos at = noiser.blockPosition();
        String crew = match.playerCrew.getOrDefault(noiser.getUUID(), "");
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.owner == null || info.owner.isEmpty() || info.owner.equals(crew)) continue;
            if (info.disabledUntil > now) continue;
            if (at.distSqr(info.pos) > (long) Config.TOWER_PELENG_RADIUS.get() * Config.TOWER_PELENG_RADIUS.get()) continue;
            String handle = dev.blackchannel.data.NetData.get(server).profile(noiser.getUUID()).handle;
            if (handle == null || handle.isEmpty()) continue;
            for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
                String oCrew = match.playerCrew.getOrDefault(owner.getUUID(), "");
                if (!oCrew.equals(info.owner)) continue;
                // создаём луч шума
                int err = Math.max(20, 120 - amount);
                Ops.noteFix(server, owner.getUUID(), handle, dev.blackchannel.data.NetData.get(server).profile(noiser.getUUID()).lastName, Str.dim(noiser.level()), at.getX(), at.getY(), at.getZ(), err, false, "blackchannel.log.tower_peleng");
            }
        }
    }
}
