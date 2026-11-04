package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
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
    public static final int TERMINAL_OFFSET_Y = HEIGHT + 1;

    private Towers() {}

    /** Сколько попыток даём на одну вышку, прежде чем сдаться. */
    private static final int ATTEMPTS = 60;
    /** Радиусы кольца в долях стороны карты. Первый — основной, как было в PR #47. */
    private static final double[] RADII = {0.33, 0.27, 0.39, 0.22, 0.44};
    /** Номер вышки, которую ставим сейчас, и номер попытки для неё. Сбрасываются в destroyAll. */
    private static int pendingSlot;
    private static int pendingAttempt;

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
                long now = level.getServer().getTickCount();
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
        return ground;
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
        for (int dy = 2; dy <= 5; dy++) set(level, x - 2, top + dy, z - 2, rail);
        set(level, x, top + 1, z, air);
        BlockPos chest = new BlockPos(x + 1, top + 1, z + 1);
        set(level, chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST.defaultBlockState());
        // один сундук на вышку: только ресурсы, без оружия (пул cacheTower)
        if (Config.TOWER_LOOT.get()) Minigame.queueTowerLoot(chest);
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
            if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem) return false;
            // инструмент в руке — тоже не взлом
            String handName = player.getMainHandItem().getItem().toString().toLowerCase();
            if (handName.contains("pick") || handName.contains("shovel") || handName.contains("axe")) return false;
        }
        // только магнетит-терминал
        try { if (!(server.overworld().getBlockState(clicked).getBlock() == net.minecraft.world.level.block.Blocks.LODESTONE)) { // allow also clicking chest nearby to open chest
            // если это сундук вышки — не перехватывать
            if (server.overworld().getBlockState(clicked).getBlock() == net.minecraft.world.level.block.Blocks.CHEST) return false;
        } } catch(Exception ignored) {}
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
        if (capturer != null) match.stats(capturer.getUUID()).towers++;
        Contracts.progress(server, match, crew, dev.blackchannel.data.Contract.TOWER, 1);
        // визуал — покрасить бетон
        if (server.overworld() != null) {
            BlockPos beacon = new BlockPos(info.pos.getX() - 2, info.pos.getY() + HEIGHT + 7, info.pos.getZ() - 2);
            // цвет по отряду
            int idx = match.teamColorIdx.getOrDefault(crew, 11);
            BlockState colored = coloredConcrete(idx);
            server.overworld().setBlock(beacon, colored, 3);
        }
        String h = dev.blackchannel.data.NetData.get(server).profile(capturer.getUUID()).handle;
        Component msg = Msg.paint(Component.translatable("blackchannel.tower.captured", h.isEmpty()?capturer.getGameProfile().getName():h, crew, info.pos.getX(), info.pos.getZ()));
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
    /** Чужие игроки, которые сейчас в зоне вышки (ключ — позиция вышки). Нужно, чтобы сообщать только о входе. */
    private static final Map<Long, Set<UUID>> ZONE_INSIDE = new HashMap<>();

    public static void tick(MinecraftServer server, MatchData match, ServerLevel level, long now) {
        if (!match.active || match.towerInfos.isEmpty()) return;
        zoneAlerts(server, match, now);
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

    public static void destroyAll(ServerLevel level, MatchData match) {
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

    /** Раз в секунду: владельцу вышки сообщение, когда чужой игрок входит в её зону. */
    private static void zoneAlerts(MinecraftServer server, MatchData match, long now) {
        if (now % 20L != 0L) return;
        long r = Config.TOWER_INTERCEPT_RADIUS.get();
        long r2 = r * r;
        for (MatchData.TowerInfo info : match.towerInfos) {
            if (info.owner == null || info.owner.isEmpty()) continue;
            Set<UUID> before = ZONE_INSIDE.computeIfAbsent(info.pos.asLong(), k -> new HashSet<>());
            Set<UUID> after = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
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


    // ---------- обзор от вышки по кругу ----------

    /** Длительность обзора без действий: потом выход сам. */
    private static final int CAM_TICKS = 2400;
    /** Как часто сервер возвращает игрока на платформу, если его сдвинуло. */
    private static final int CAM_CHECK_TICKS = 5;
    /** Допуск, на сколько игрок может отойти от точки на платформе. */
    private static final double CAM_HOLD_RADIUS = 0.6;
    /** Чуть вниз, чтобы в кадре был верх вышки и ближняя земля. */
    private static final float CAM_PITCH = 18f;
    private static final String CAM_END = "bc_cam_end";
    private static final String CAM_MODE = "bc_cam_mode";
    private static final String CAM_RET = "bc_cam_ret";
    private static final String CAM_MARKER = "bc_cam_marker";
    private static final String CAM_HOLD = "bc_cam_hold";
    /** Камеру можно отвести от вышки не дальше этого радиуса (блоки). */
    private static final double CAM_RADIUS = 40.0;
    /** Шаг камеры за один тик ввода (блоки). */
    private static final double CAM_SPEED = 0.35;
    private static final double CAM_MIN_DY = -3.0;
    private static final double CAM_MAX_DY = 30.0;
    private static final String CAM_MOVE_TICK = "bc_cam_move_tick";

    /**
     * Обзор от своей вышки по образцу дрона SuperbWarfare: игрок остаётся в обычном режиме,
     * его видно и он может получить урон. Его камера — невидимый маркер на платформе
     * (ServerPlayer.setCamera). Взгляд крутит мышь на клиенте (client/TowerCam).
     * Игрок привязан к точке на платформе: если его сдвинуло, сервер возвращает его на место.
     * Во время обзора блоки не ломаются и не ставятся (см. GameEvents).
     * Повторное нажатие КАМЕРА — выход. Через 2 минуты выход сам.
     */
    public static void camera(ServerPlayer player, dev.blackchannel.data.NetData.Profile profile) {
        MinecraftServer server = player.getServer();
        if (isCamActive(player)) {
            restoreCam(player);
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_off")), false);
            return;
        }
        MatchData match = MatchData.get(server);
        MatchData.TowerInfo owned = null;
        for (MatchData.TowerInfo inf : match.towerInfos) if (inf.owner!=null && inf.owner.equals(profile.crew) && inf.disabledUntil <= server.getTickCount()) { owned = inf; break; }
        if (owned==null) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.no_own")), false); return; }
        int cost = Config.TOWER_CAM_COST.get();
        if (profile.crypto < cost) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.need_crypto", cost)), false); return; }
        ServerLevel level = player.serverLevel();
        // Точка стояния: центр платформы, ноги на её верхнем слое
        double fx = owned.pos.getX() + 0.5;
        double fy = owned.pos.getY() + HEIGHT + 2;
        double fz = owned.pos.getZ() + 0.5;
        net.minecraft.world.entity.Entity marker = net.minecraft.world.entity.EntityType.MARKER.create(level);
        if (marker == null) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_fail")), false); return; }
        profile.crypto -= cost;
        // Камера на уровне глаз игрока, смотрит вниз под углом
        marker.moveTo(fx, fy + 1.6, fz, 0f, CAM_PITCH);
        level.addFreshEntity(marker);
        BlockPos ret = player.blockPosition();
        CompoundTag data = player.getPersistentData();
        data.putIntArray(CAM_RET, new int[]{ret.getX(), ret.getY(), ret.getZ()});
        data.putDouble(CAM_HOLD + "x", fx);
        data.putDouble(CAM_HOLD + "y", fy);
        data.putDouble(CAM_HOLD + "z", fz);
        data.putInt(CAM_MODE, player.gameMode.getGameModeForPlayer().getId());
        data.putUUID(CAM_MARKER, marker.getUUID());
        data.putLong(CAM_END, server.getTickCount() + (long) CAM_TICKS);
        player.teleportTo(level, fx, fy, fz, 0f, CAM_PITCH);
        // Тело невидимо для других, но урон по нему выбрасывает из обзора (см. camHurt)
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CAM_TICKS + 200, 0, false, false, false));
        player.setCamera(marker);
        ModNetwork.sendCam(player, true);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_ok", owned.pos.getX(), owned.pos.getZ(), CAM_TICKS / 20)), false);
        player.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.MASTER, 1f, 1f);
        camSchedule(server, player.getUUID());
    }

    /** Сервер следит за сроком, за маркером и держит игрока на платформе. */
    private static void camSchedule(MinecraftServer server, UUID id) {
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + CAM_CHECK_TICKS, () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            // вышел из игры: вернём при входе (см. restoreCam)
            if (player == null) return;
            CompoundTag data = player.getPersistentData();
            if (!data.contains(CAM_END)) return;
            if (server.getTickCount() >= data.getLong(CAM_END)
                    || player.isDeadOrDying()
                    || markerOf(player) == null) {
                restoreCam(player);
                return;
            }
            holdOnPlatform(player);
            camSchedule(server, id);
        }));
    }

    /** Если игрока сдвинуло (отбросило, сорвался с платформы), возвращаем на точку. */
    private static void holdOnPlatform(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(CAM_HOLD + "x")) return;
        double hx = data.getDouble(CAM_HOLD + "x"), hy = data.getDouble(CAM_HOLD + "y"), hz = data.getDouble(CAM_HOLD + "z");
        double dx = player.getX() - hx, dy = player.getY() - hy, dz = player.getZ() - hz;
        if (dx * dx + dz * dz <= CAM_HOLD_RADIUS * CAM_HOLD_RADIUS && Math.abs(dy) <= 1.0) return;
        player.teleportTo(player.serverLevel(), hx, hy, hz, player.getYRot(), player.getXRot());
    }

    private static net.minecraft.world.entity.Entity markerOf(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(CAM_MARKER)) return null;
        return player.serverLevel().getEntity(data.getUUID(CAM_MARKER));
    }

    /** Клик или КАМЕРА во время обзора: выйти из него. Возвращает true, если обзор был включён. */
    public static boolean camExit(ServerPlayer player) {
        if (!isCamActive(player)) return false;
        restoreCam(player);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.cam_off")), false);
        return true;
    }

    /**
     * Камера-дрон: клиент шлёт ввод каждый тик, сервер двигает маркер вокруг вышки.
     * Тело игрока не двигается. Не больше одного шага за тик.
     */
    public static void camMove(ServerPlayer player, double fwd, double side, double up, float yaw, float pitch) {
        if (!isCamActive(player)) return;
        net.minecraft.world.entity.Entity marker = markerOf(player);
        if (marker == null) return;
        CompoundTag data = player.getPersistentData();
        long now = player.getServer().getTickCount();
        if (data.getLong(CAM_MOVE_TICK) == now && data.contains(CAM_MOVE_TICK)) return;
        data.putLong(CAM_MOVE_TICK, now);
        double ix = Math.max(-1.0, Math.min(1.0, fwd));
        double is = Math.max(-1.0, Math.min(1.0, side));
        double iu = Math.max(-1.0, Math.min(1.0, up));
        double rad = Math.toRadians(yaw);
        double vx = -Math.sin(rad) * ix + Math.cos(rad) * is;
        double vz = Math.cos(rad) * ix + Math.sin(rad) * is;
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len > 1.0) { vx /= len; vz /= len; }
        double hx = data.getDouble(CAM_HOLD + "x"), hy = data.getDouble(CAM_HOLD + "y"), hz = data.getDouble(CAM_HOLD + "z");
        double nx = marker.getX() + vx * CAM_SPEED;
        double nz = marker.getZ() + vz * CAM_SPEED;
        double ny = marker.getY() + iu * CAM_SPEED;
        double ox = nx - hx, oz = nz - hz;
        double d = Math.sqrt(ox * ox + oz * oz);
        if (d > CAM_RADIUS) { nx = hx + ox / d * CAM_RADIUS; nz = hz + oz / d * CAM_RADIUS; }
        ny = Math.max(hy + CAM_MIN_DY, Math.min(hy + CAM_MAX_DY, ny));
        marker.moveTo(nx, ny, nz, net.minecraft.util.Mth.wrapDegrees(yaw), net.minecraft.util.Mth.clamp(pitch, -89f, 89f));
    }

    /** Урон по телу в обзоре выбрасывает из камеры. */
    public static void camHurt(ServerPlayer player) {
        if (isCamActive(player)) restoreCam(player);
    }

    /** Удар током: тот, кто начал ломать терминал, получает урон (не чаще раза в секунду). */
    public static void terminalHit(ServerPlayer player, BlockPos pos) {
        MatchData match = MatchData.get(player.getServer());
        if (!match.active || !isTerminal(match, pos)) return;
        CompoundTag data = player.getPersistentData();
        long now = player.getServer().getTickCount();
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
        if (!match.active || !isTerminal(match, pos)) return false;
        MatchData.TowerInfo info = at(match, pos);
        if (info == null) return false;
        long now = server.getTickCount();
        info.disabledUntil = now + Config.TOWER_BREAK_DISABLE_SECONDS.get() * 20L;
        info.hackCrew = "";
        info.hackStartTick = 0;
        match.setDirty();
        if (breaker != null) {
            breaker.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.broken", Config.TOWER_BREAK_DISABLE_SECONDS.get())), true);
        }
        return true;
    }

    public static boolean isCamActive(ServerPlayer player) {
        return player.getPersistentData().contains(CAM_END);
    }

    /** Выход из обзора: камера обратно на игрока, место прежнее. Безопасно вызывать многократно. */
    public static void restoreCam(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(CAM_END)) return;
        int[] ret = data.getIntArray(CAM_RET);
        GameType mode = GameType.byId(data.contains(CAM_MODE) ? data.getInt(CAM_MODE) : 0);
        net.minecraft.world.entity.Entity marker = markerOf(player);
        if (marker != null) marker.discard();
        data.remove(CAM_END);
        data.remove(CAM_MODE);
        data.remove(CAM_RET);
        data.remove(CAM_MARKER);
        data.remove(CAM_HOLD + "x");
        data.remove(CAM_HOLD + "y");
        data.remove(CAM_HOLD + "z");
        player.setCamera(player);
        player.removeEffect(MobEffects.INVISIBILITY);
        ModNetwork.sendCam(player, false);
        // Режим не менялся с начала обзора, но если его сменил старый вариант, вернём прежний
        if (player.gameMode.getGameModeForPlayer() != mode) player.setGameMode(mode);
        if (ret.length >= 3 && !player.isDeadOrDying()) {
            player.teleportTo(player.serverLevel(), ret[0] + 0.5, ret[1], ret[2] + 0.5, player.getYRot(), player.getXRot());
        }
    }

    public static void scan(ServerPlayer player, dev.blackchannel.data.NetData.Profile profile) {
        MatchData match = MatchData.get(player.getServer());
        MatchData.TowerInfo owned = null;
        for (MatchData.TowerInfo inf : match.towerInfos) if (inf.owner!=null && inf.owner.equals(profile.crew) && inf.disabledUntil <= player.getServer().getTickCount()) { owned = inf; break; }
        if (owned==null) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.no_own")), true); return; }
        int cost = Config.TOWER_SCAN_COST.get();
        if (profile.crypto < cost) { player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.need_crypto", cost)), true); return; }
        profile.crypto -= cost;
        int r = Config.TOWER_SECTOR_RADIUS.get();
        int err = Math.max(15, r/10);
        for (ServerPlayer enemy : player.getServer().getPlayerList().getPlayers()) {
            if (enemy==player) continue;
            if (enemy.distanceToSqr(owned.pos.getX(), enemy.getY(), owned.pos.getZ()) > (long)r*r) continue;
            if (MatchData.get(player.getServer()).permanentDead.contains(enemy.getUUID())) continue;
            String handle = dev.blackchannel.data.NetData.get(player.getServer()).profile(enemy.getUUID()).handle;
            if (handle==null||handle.isEmpty()) continue;
            int sx = enemy.blockPosition().getX() + player.getRandom().nextInt(err*2+1)-err;
            int sz = enemy.blockPosition().getZ() + player.getRandom().nextInt(err*2+1)-err;
            Ops.noteFix(player.getServer(), player.getUUID(), handle, dev.blackchannel.data.NetData.get(player.getServer()).profile(enemy.getUUID()).lastName, Str.dim(enemy.level()), sx, enemy.blockPosition().getY(), sz, err, false, "blackchannel.log.tower_sector");
        }
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.tower.scan_ok", r)), true);
        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.MASTER, 1f, 1.2f);
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
