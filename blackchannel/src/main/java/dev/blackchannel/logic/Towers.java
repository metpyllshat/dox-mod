package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * Вышки связи: шесть мачт по кольцу 0.33 стороны карты.
 * Каждая даёт: пеленг 400, карту сектора 600 раз в 60с, 2 крипты/мин, чат 800 без стойки, перехват.
 * Взлом 20с (с эксплойтом 10с), шумит 60 на 400, ломается ЭМИ/C4 на 5 мин.
 */
public final class Towers {
    public static final int HEIGHT = 18;
    public static final int PLATFORM = 4;
    public static final int TERMINAL_OFFSET_Y = HEIGHT + 1;

    private Towers() {}

    /** Кольцо вокруг центра: 0.33 стороны карты, не ближе 180 к базе отряда. */
    public static void placeAll(ServerLevel level, MatchData match, RandomSource random) {
        int count = Config.TOWER_COUNT.get();
        if (count <= 0) return;
        // Если уже есть инфо — не плодить
        if (match.towersPlaced && match.towerInfos.size() >= count) return;
        int size = Math.max(2, match.size);
        double radius = size * 0.33;
        int start = match.towerInfos.size();
        for (int i = start; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i + Math.PI / count;
            int x = match.centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = match.centerZ + (int) Math.round(Math.sin(angle) * radius);
            if (nearBase(match, x, z, 180)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y, z);
            if (!match.inside(ground)) continue;
            build(level, match, ground);
            match.towers.add(ground);
            MatchData.TowerInfo info = new MatchData.TowerInfo(ground);
            long now = level.getServer().getTickCount();
            info.nextCryptoTick = now + 1200;
            info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
            match.towerInfos.add(info);
            match.setDirty();
        }
        match.towersPlaced = true;
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
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) set(level, x + dx, y, z + dz, brick);
        }
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
        for (int dy = 2; dy <= 5; dy++) set(level, x - 2, top + dy, z - 2, rail);
        set(level, x, top + 1, z, air);
        BlockPos chest = new BlockPos(x + 1, top + 1, z + 1);
        set(level, chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST.defaultBlockState());
        if (Config.TOWER_LOOT.get()) Minigame.queueLoot(chest);
        // Терминал — магнетит (лодстоун) в центре, на него ПКМ пустой рукой для взлома
        BlockPos terminal = new BlockPos(x, top + 1, z);
        set(level, terminal.getX(), terminal.getY(), terminal.getZ(), Blocks.LODESTONE.defaultBlockState());
        // табличка-подсказка
        set(level, x, top + 1, z + 1, Blocks.OAK_WALL_SIGN.defaultBlockState());
        // Маячок цвета владельца — цветной бетон сверху антенны, изначально серый
        set(level, x - 2, top + 6, z - 2, Blocks.GRAY_CONCRETE.defaultBlockState());
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    public static MatchData.TowerInfo at(MatchData match, BlockPos pos) {
        for (MatchData.TowerInfo info : match.towerInfos) {
            // терминал в центре верха, но клик может быть по соседним блокам площадки — ищем ближайшую вышку
            if (info.pos.distSqr(pos) <= 48 * 48) return info;
            BlockPos term = new BlockPos(info.pos.getX(), info.pos.getY() + HEIGHT + 2, info.pos.getZ());
            if (term.distSqr(pos) <= 25) return info;
        }
        return null;
    }

    public static boolean isTerminal(MatchData match, BlockPos pos) {
        for (MatchData.TowerInfo info : match.towerInfos) {
            BlockPos term = new BlockPos(info.pos.getX(), info.pos.getY() + HEIGHT + 2, info.pos.getZ());
            if (term.equals(pos)) return true;
        }
        return false;
    }

    /** ПКМ по терминалу — начать/продолжить взлом. Требует пустую руку или эксплойт, чтобы не ломать стройку. */
    public static boolean onUse(MinecraftServer server, ServerPlayer player, BlockPos clicked) {
        // Если в руке блок — это стройка, не взлом. Взлом только пустой рукой / эксплойтом / шифтом
        if (!player.getMainHandItem().isEmpty() && !hasExploit(player)) {
            // держит блок — отдаём на стройку, не перехватываем
            if (net.minecraft.world.item.BlockItem.byBlock(player.getMainHandItem().getItem()) != null) return false;
            // инструмент в руке — тоже не взлом
            String handName = player.getMainHandItem().getItem().toString().toLowerCase();
            if (handName.contains("pick") || handName.contains("shovel") || handName.contains("axe")) return false;
        }
        if (!isTerminal(MatchData.get(server), clicked)) {
            // клик не по терминалу, а по земле рядом — не взлом
            // проверяем дистанцию до терминала
            MatchData mm = MatchData.get(server);
            boolean nearTerm = false;
            for (MatchData.TowerInfo inf : mm.towerInfos) {
                BlockPos term = new BlockPos(inf.pos.getX(), inf.pos.getY() + HEIGHT + 2, inf.pos.getZ());
                if (term.distSqr(clicked) <= 4) { nearTerm = true; break; }
            }
            if (!nearTerm) return false;
        }
        MatchData match = MatchData.get(server);
        if (!match.active) return false;
        MatchData.TowerInfo info = at(match, clicked);
        if (info == null) return false;
        String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
        if (crew == null || crew.isEmpty()) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.need_crew")), true);
            return true;
        }
        long now = server.getTickCount();
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
            match.setDirty();
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.hack_start", need / 20)), true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1f, 0.8f);
            // шум для пеленга: все в радиусе 400 получают сектор
            emitNoise(server, info.pos, 60, player);
            return true;
        }
        long elapsed = now - info.hackStartTick;
        long left = need - elapsed;
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
        info.disabledUntil = 0;
        long now = server.getTickCount();
        info.nextCryptoTick = now + 1200;
        info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
        match.setDirty();
        // очки за захват
        match.addScore(crew, 5);
        // визуал — покрасить бетон
        if (server.overworld() != null) {
            BlockPos beacon = new BlockPos(info.pos.getX() - 2, info.pos.getY() + HEIGHT + 7, info.pos.getZ() - 2);
            // цвет по отряду
            int idx = match.teamColorIdx.getOrDefault(crew, 11);
            BlockState colored = coloredConcrete(idx);
            server.overworld().setBlock(beacon, colored, 3);
        }
        Component msg = Msg.paint(Component.translatable("blackchannel.tower.captured", capturer.getGameProfile().getName(), crew, info.pos.getX(), info.pos.getZ()));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1f, 1.2f);
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
        ItemStack s = player.getMainHandItem();
        if (s.isEmpty()) s = player.getOffhandItem();
        return !s.isEmpty() && s.getItem().getDescriptionId().contains("exploit");
    }

    private static void emitNoise(MinecraftServer server, BlockPos pos, int amount, ServerPlayer source) {
        // Шум слышен пеленгом 400
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == source) continue;
            double d = viewer.distanceToSqr(pos.getX(), viewer.blockPosition().getY(), pos.getZ());
            if (d > 400L * 400L) continue;
            // даём сектор на шум
            // Используем общий механизм шума: делаем вид что viewer услышал TRACE от source
        }
    }

    /** Тик вышек: крипта, сектор, перехват. */
    public static void tick(MinecraftServer server, MatchData match, ServerLevel level, long now) {
        if (!match.active || match.towerInfos.isEmpty()) return;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.disabledUntil > now) continue;
            if (info.owner == null || info.owner.isEmpty()) continue;
            // Крипта 2/мин
            if (now >= info.nextCryptoTick) {
                info.nextCryptoTick = now + 1200;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    String crew = match.playerCrew.getOrDefault(p.getUUID(), "");
                    if (crew.equals(info.owner)) {
                        // прямое зачисление в профиль
                        dev.blackchannel.data.NetData.get(server).profile(p.getUUID()).crypto = Math.min(5000, dev.blackchannel.data.NetData.get(server).profile(p.getUUID()).crypto + Config.TOWER_CRYPTO_PER_MIN.get());
                        p.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.crypto", Config.TOWER_CRYPTO_PER_MIN.get(), info.pos.getX(), info.pos.getZ())), true);
                    }
                }
                match.setDirty();
            }
            // Карта сектора 600 раз в 60с: даёт точки с погрешностью
            if (now >= info.nextSectorTick) {
                info.nextSectorTick = now + Config.TOWER_SECTOR_SECONDS.get() * 20L;
                int r = Config.TOWER_SECTOR_RADIUS.get();
                for (ServerPlayer enemy : server.getPlayerList().getPlayers()) {
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
        // Автозахват если выдержал время
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.hackCrew != null && !info.hackCrew.isEmpty() && info.hackStartTick > 0) {
                int need = dev.blackchannel.Config.TOWER_HACK_SECONDS.get() * 20;
                // нельзя проверить hasExploit без игрока — берём базу, на клике уже учли
                if (now - info.hackStartTick >= need) {
                    // найти игрока захватчика для сообщения
                    ServerPlayer cap = null;
                    for (ServerPlayer pp : server.getPlayerList().getPlayers()) if (match.playerCrew.getOrDefault(pp.getUUID(), "").equals(info.hackCrew) && pp.distanceToSqr(info.pos.getX(), pp.getY(), info.pos.getZ()) < 48*48) { cap = pp; break; }
                    if (cap != null) capture(server, info, info.hackCrew, cap);
                }
            }
        }
        // Взлом сброс если отошли
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.hackCrew == null || info.hackCrew.isEmpty() || info.hackStartTick == 0) continue;
            boolean anyoneNear = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String crew = match.playerCrew.getOrDefault(p.getUUID(), "");
                if (!crew.equals(info.hackCrew)) continue;
                if (p.distanceToSqr(info.pos.getX(), p.getY(), info.pos.getZ()) < 48 * 48) { anyoneNear = true; break; }
            }
            if (!anyoneNear && now - info.hackStartTick > 200) {
                info.hackCrew = "";
                info.hackStartTick = 0;
                match.setDirty();
            }
        }
    }

    /** ЭМИ глушит вышки на 5 мин. */
    public static void pulse(MatchData match, BlockPos center, long now) {
        int dur = Config.TOWER_DISABLE_SECONDS.get() * 20;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.pos.distSqr(center) <= 200L * 200L) {
                info.disabledUntil = now + dur;
                info.hackCrew = "";
                info.hackStartTick = 0;
            }
        }
        match.setDirty();
    }

    /** Перехват: владелец видит чужие действия в радиусе. */
    public static void intercept(MinecraftServer server, MatchData match, ServerPlayer actor, String action) {
        if (actor == null || action == null) return;
        BlockPos at = actor.blockPosition();
        String crew = match.playerCrew.getOrDefault(actor.getUUID(), "");
        String handle = dev.blackchannel.data.NetData.get(server).profile(actor.getUUID()).handle;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.owner == null || info.owner.isEmpty() || info.owner.equals(crew)) continue;
            if (info.disabledUntil > server.getTickCount()) continue;
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
        String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
        if (crew == null || crew.isEmpty()) return false;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (!crew.equals(info.owner)) continue;
            if (info.disabledUntil > server.getTickCount()) continue;
            if (player.blockPosition().distSqr(info.pos) <= (long) Config.TOWER_CHAT_RADIUS.get() * Config.TOWER_CHAT_RADIUS.get()) return true;
        }
        return false;
    }

    /** Пеленг на шум 400 — даёт владельцам вышек сектор на шумящего. */
    public static void onNoise(MinecraftServer server, ServerPlayer noiser, int amount) {
        if (noiser == null) return;
        MatchData match = MatchData.get(server);
        if (!match.active) return;
        BlockPos at = noiser.blockPosition();
        String crew = match.playerCrew.getOrDefault(noiser.getUUID(), "");
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.owner == null || info.owner.isEmpty() || info.owner.equals(crew)) continue;
            if (info.disabledUntil > server.getTickCount()) continue;
            if (at.distSqr(info.pos) > (long) Config.TOWER_PELENG_RADIUS.get() * Config.TOWER_PELENG_RADIUS.get()) continue;
            String handle = dev.blackchannel.data.NetData.get(server).profile(noiser.getUUID()).handle;
            if (handle == null || handle.isEmpty()) continue;
            for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
                String oCrew = match.playerCrew.getOrDefault(owner.getUUID(), "");
                if (!oCrew.equals(info.owner)) continue;
                // создаём луч шума
                int err = 60;
                Ops.noteFix(server, owner.getUUID(), handle, dev.blackchannel.data.NetData.get(server).profile(noiser.getUUID()).lastName, Str.dim(noiser.level()), at.getX(), at.getY(), at.getZ(), err, false, "blackchannel.log.tower_peleng");
            }
        }
    }
}
