package dev.blackchannel.logic;

import com.mojang.brigadier.CommandDispatcher;
import dev.blackchannel.Config;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.block.LoggerBlockEntity;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;
import dev.blackchannel.data.TrapData;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Minigame {
    private static final Map<UUID, Long> PROTECT = new HashMap<>();
    private static final Map<UUID, Long> REVIVE = new HashMap<>();
    /** Кто последним ударил игрока: нужно, чтобы засчитать очко, если тот умер от падения. */
    private static final Map<UUID, LastHit> LAST_HIT = new HashMap<>();
    private static final long KILL_CREDIT_TICKS = 600L;
    /** Не спамим сообщением «PvP отключён» на каждый удар. */
    private static final Map<UUID, Long> PVP_WARN = new HashMap<>();
    private static final Map<UUID, Long> ALLY_WARN = new HashMap<>();
    private static final Map<String, Long> BEACON_ALERT = new HashMap<>();
    // Схроны: 14 на карту 1000×1000 с четырьмя отрядами, дальше — линейно
    private static final double CACHE_PER_TEAM = 3.5;
    private static final int CACHE_MIN = 8;
    private static final int CACHE_MAX = 56;

    private record LastHit(UUID attacker, long tick) {}

    /** База, которую ещё предстоит поставить в мир (маяк + высадка игроков). */
    private record PendingBase(String crew, int x, int z, float yaw, List<UUID> players) {}

    /** Очередь высадки: одна база за тик, чтобы не грузить десятки чанков разом. */
    private static final List<PendingBase> DEPLOY = new ArrayList<>();

    private static final int CHUNK_READY = 1;
    private static final int CHUNK_WAIT = 0;
    private static final int CHUNK_FAIL = -1;
    private static final long CHUNK_WAIT_LIMIT = 200L;
    private static CompletableFuture<?> CHUNK_WAIT_FUTURE;
    private static int chunkWaitX = Integer.MIN_VALUE;
    private static int chunkWaitZ = Integer.MIN_VALUE;
    private static long chunkWaitSince;
    /** Паузы между генерациями: сервер успевает доделать свет и сущности чанка. */
    private static long nextDeployTick;
    private static long nextCacheTick;
    private static final int DEPLOY_GAP_TICKS = 20;
    /** Одна генерация в секунду — это ровно тот темп, с каким игрок идёт пешком по новым местам. */
    private static final int CACHE_GAP_TICKS = 20;
    /** Схроны начинаем ставить только после высадки, чтобы первые секунды никто не лагал. */
    private static final int CACHE_START_DELAY = 60;
    /**
     * Лут в чужие сундуки кладём не в момент загрузки чанка (там нельзя менять мир),
     * а из тика, по нескольку контейнеров за раз.
     */
    private static final ArrayDeque<BlockPos> LOOT_QUEUE = new ArrayDeque<>();
    private static final int LOOT_PER_TICK = 4;
    private static final int LOOT_QUEUE_LIMIT = 2048;
    /** Границу включаем после высадки: пока базы грузятся, игроки стоят вне зоны. */
    private static boolean borderPending;

    private static final List<ChatFormatting> TEAM_COLORS = List.of(
            ChatFormatting.RED, ChatFormatting.GREEN, ChatFormatting.YELLOW,
            ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD,
            ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_PURPLE,
            ChatFormatting.DARK_RED, ChatFormatting.WHITE, ChatFormatting.GRAY
    );
    private Minigame() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minigame")
                .executes(c -> help(c.getSource()))
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("team").executes(c -> openTeamMenu(c.getSource())))
                .then(Commands.literal("crew").executes(c -> openTeamMenu(c.getSource())))
                .then(Commands.literal("center").requires(Minigame::op)
                        .executes(c -> centerHere(c.getSource()))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer(-64, 320))
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> centerAt(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "y"),
                                                        IntegerArgumentType.getInteger(c, "z")))))))
                .then(Commands.literal("size").requires(Minigame::op)
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(256, 20000))
                                .executes(c -> size(c.getSource(), IntegerArgumentType.getInteger(c, "blocks")))))
                .then(Commands.literal("cache").requires(Minigame::op).executes(c -> spawnCache(c.getSource())))
                .then(Commands.literal("start").requires(Minigame::op).executes(c -> start(c.getSource())))
                .then(Commands.literal("stop").requires(Minigame::op).executes(c -> stop(c.getSource()))));
    }

    private static final Map<UUID, Integer> PENDING_TEAM_MENU = new HashMap<>();

    public static void tick(MinecraftServer server) {
        flushTeamMenu(server);
        MatchData match = MatchData.get(server);
        if (match.active) {
            ServerLevel level = Ops.levelOf(server, match.dim);
            if (level != null) tickMatch(server, match, level);
        }
        if (server.getTickCount() % 10 != 0) return;
        TrapData data = TrapData.get(server);
        if (data.traps.isEmpty()) return;
        boolean dirty = false;
        Iterator<TrapData.Trap> it = data.traps.iterator();
        while (it.hasNext()) {
            TrapData.Trap trap = it.next();
            ServerLevel level = Ops.levelOf(server, trap.dim);
            if (level == null) continue;
            BlockPos pos = new BlockPos(trap.x, trap.y, trap.z);
            if (!level.isLoaded(pos)) continue;
            if (!isContainer(level.getBlockState(pos).getBlock())) {
                it.remove();
                dirty = true;
                continue;
            }
            int next = scanLogger(level, pos, trap.owner, trap.charges, trap.seen, null);
            if (next != trap.charges) {
                trap.charges = next;
                dirty = true;
            }
        }
        if (dirty) data.setDirty();
    }

    private static final String SCOREBOARD_OBJECTIVE = "bc_match";
    private static final String LINE_TEAM_PREFIX = "bcs";
    private static final List<String> lineNames = new ArrayList<>();
    private static PlayerTeam[] lineTeams = new PlayerTeam[0];

    private static void initScoreboard(MinecraftServer server, MatchData match) {
        Scoreboard scoreboard = server.overworld().getScoreboard();
        Objective existing = scoreboard.getObjective(SCOREBOARD_OBJECTIVE);
        if (existing != null) scoreboard.removeObjective(existing);
        clearLineTeams(scoreboard);
        Objective obj = scoreboard.addObjective(SCOREBOARD_OBJECTIVE, ObjectiveCriteria.DUMMY,
                titleFor(server, match, server.overworld().getGameTime()), ObjectiveCriteria.RenderType.INTEGER);
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, obj);
        lineTeams = new PlayerTeam[Math.max(1, Teams.ALL.size())];
        for (int i = 0; i < lineTeams.length; i++) {
            PlayerTeam team = scoreboard.addPlayerTeam(LINE_TEAM_PREFIX + i);
            team.setColor(Teams.ALL.get(i % Teams.ALL.size()).color());
            lineTeams[i] = team;
        }
    }

    private static void clearLineTeams(Scoreboard scoreboard) {
        for (int i = 0; i < lineTeams.length; i++) {
            if (lineTeams[i] != null) scoreboard.removePlayerTeam(lineTeams[i]);
            lineTeams[i] = null;
        }
        lineTeams = new PlayerTeam[0];
    }

    private static Component titleFor(MinecraftServer server, MatchData match, long now) {
        long endTick = match.finalActive ? match.finalEndTick : match.matchEndTick;
        long left = Math.max(0L, endTick - now) / 20L;
        String clock = String.format("%d:%02d", left / 60, left % 60);
        int alive = match.getAliveCount(server);
        int total = server.getPlayerList().getPlayerCount();
        MutableComponent title = Component.empty();
        if (match.finalActive) {
            title.append(Component.literal("ФИНАЛ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else {
            title.append(Component.literal("ЧЁРНЫЙ КАНАЛ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        title.append(Component.literal("  " + clock).withStyle(ChatFormatting.WHITE));
        title.append(Component.literal("  " + alive + "/" + total).withStyle(ChatFormatting.GRAY));
        return title;
    }

    private static void updateScoreboard(MinecraftServer server, MatchData match) {
        Scoreboard scoreboard = server.overworld().getScoreboard();
        Objective obj = scoreboard.getObjective(SCOREBOARD_OBJECTIVE);
        if (obj == null) {
            initScoreboard(server, match);
            return;
        }
        obj.setDisplayName(titleFor(server, match, server.overworld().getGameTime()));
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(match.teamScores.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Set<String> keep = new HashSet<>();
        int index = 0;
        for (Map.Entry<String, Integer> row : rows) {
            String code = row.getKey();
            String label = teamLabel(server, code);
            if (label.isEmpty() || !keep.add(label)) continue;
            scoreboard.getOrCreatePlayerScore(label, obj).setScore(row.getValue());
            PlayerTeam team = index < lineTeams.length ? lineTeams[index] : null;
            // Не трогаем настоящих игроков: у них своя команда для ника
            if (team != null && server.getPlayerList().getPlayerByName(label) == null) {
                boolean out = match.eliminatedTeams.contains(code) || !match.teamAlive(code);
                team.setColor(out ? ChatFormatting.DARK_GRAY : scoreColor(match, code));
                scoreboard.addPlayerToTeam(label, team);
            }
            index++;
        }
        for (String old : lineNames) {
            if (!keep.contains(old)) scoreboard.resetPlayerScore(old, obj);
        }
        lineNames.clear();
        lineNames.addAll(keep);
    }

    private static ChatFormatting scoreColor(MatchData match, String code) {
        Teams.Team team = Teams.byId(code);
        if (team != null) return team.color();
        Integer idx = match.teamColorIdx.get(code);
        if (idx != null) return TEAM_COLORS.get(Math.abs(idx) % TEAM_COLORS.size());
        return ChatFormatting.WHITE;
    }

    private static String teamLabel(MinecraftServer server, String code) {
        if (code == null || code.isEmpty()) return "";
        Teams.Team team = Teams.byId(code);
        if (team != null) return team.name();
        if (code.startsWith("solo:")) {
            String raw = code.substring(5);
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(raw));
                if (player != null) return player.getGameProfile().getName();
            } catch (IllegalArgumentException ignored) {
            }
            return "Соло " + raw.substring(0, Math.min(6, raw.length()));
        }
        return code;
    }

    private static void clearScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.overworld().getScoreboard();
        Objective obj = scoreboard.getObjective(SCOREBOARD_OBJECTIVE);
        if (obj != null) {
            for (String name : lineNames) scoreboard.resetPlayerScore(name, obj);
            scoreboard.removeObjective(obj);
        }
        lineNames.clear();
        clearLineTeams(scoreboard);
    }

    private static void tickMatch(MinecraftServer server, MatchData match, ServerLevel level) {
        long now = level.getGameTime();
        // Каждую секунду: скорборд и проверка победы
        if (now % 20 == 0) {
            updateScoreboard(server, match);
            checkVictory(server, match);
            if (!match.active) return;
        }
        // Треть и две трети матча: сначала приблизительные координаты игроков, потом точные маяки
        long matchLength = Math.max(1L, match.matchEndTick - match.matchStartTick);
        if (!match.playersLeaked && now >= match.matchStartTick + matchLength / 3L) {
            match.playersLeaked = true;
            match.setDirty();
            announcePlayerCoords(server, match, level);
        }
        if (!match.beaconsLeaked && now >= match.matchStartTick + matchLength * 2L / 3L) {
            match.beaconsLeaked = true;
            match.setDirty();
            announceBeaconCoords(server, match);
        }
        // Аирдропы: первый через airDropFirstMinute, дальше по интервалу
        if (Config.AIRDROP_ENABLED.get() && match.nextAirdropTick > 0L && now >= match.nextAirdropTick) {
            dropAirdrop(server, match, level);
            match.nextAirdropTick = now + Config.AIRDROP_INTERVAL_MINUTES.get() * 1200L;
            match.setDirty();
        }
        // Проверка окончания матча
        if (match.finalActive && now >= match.finalEndTick) {
            endMatch(server, match);
            return;
        }
        // Проверка начала финала
        if (!match.finalActive && now >= match.matchEndTick) {
            match.finalActive = true;
            match.setDirty();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.final_start", Config.FINAL_DURATION_MINUTES.get())));
                player.playNotifySound(net.minecraft.sounds.SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.MASTER, 1.0f, 1.0f);
            }
            // Убить всех downed игроков в финале
            List<UUID> toKill = new ArrayList<>(match.downedPlayers.keySet());
            for (UUID id : toKill) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    match.permanentDead.add(id);
                    match.clearDowned(id);
                    REVIVE.remove(id);
                    LAST_HIT.remove(id);
                    // Снимаем позу лежачего, иначе наблюдатель останется пластом и без физики
                    player.setForcedPose(null);
                    player.setPose(Pose.STANDING);
                    player.noPhysics = false;
                    player.removeEffect(MobEffects.BLINDNESS);
                    player.removeEffect(MobEffects.INVISIBILITY);
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.permanent_dead")));
                    checkTeamElimination(match, match.playerCrew.getOrDefault(id, ""));
                }
            }
            match.setDirty();
        }
        // Лут в найденные сундуки — по чуть-чуть каждый тик
        lootStep(match, level);
        // Высадка идёт первой: пока базы не расставлены, схроны не трогаем
        if (!DEPLOY.isEmpty()) {
            deployStep(server, match, level);
            return;
        }
        // Разбиваем placeCaches на тики (cacheTick хранит индекс следующего схрона)
        if (match.cacheTick < match.cacheTotal) {
            placeCacheStep(server, match, level);
        }
    }

    /**
     * 1/3 матча: всем показываем координаты каждого игрока.
     * Погрешность — 10% стороны карты, то есть при 2500 это ±250 блоков.
     */
    private static void announcePlayerCoords(MinecraftServer server, MatchData match, ServerLevel level) {
        int error = Math.max(8, (int) Math.round(0.10 * Math.max(2, match.size)));
        Component header = Component.translatable("blackchannel.minigame.coords_leak_header", error);
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            receiver.sendSystemMessage(Msg.paint(header));
            receiver.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.7f, 0.8f);
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            UUID id = target.getUUID();
            if (!match.playerCrew.containsKey(id) || match.permanentDead.contains(id)) continue;
            BlockPos at = target.blockPosition();
            int x = at.getX() + random.nextInt(error * 2 + 1) - error;
            int z = at.getZ() + random.nextInt(error * 2 + 1) - error;
            int y = at.getY() + random.nextInt(41) - 20;
            Component line = Component.translatable("blackchannel.minigame.coords_leak",
                    target.getGameProfile().getName(), x, y, z);
            for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
                receiver.sendSystemMessage(Msg.paint(line));
            }
        }
    }

    /** 2/3 матча: точные координаты маяков отрядов. */
    private static void announceBeaconCoords(MinecraftServer server, MatchData match) {
        Component header = Component.translatable("blackchannel.minigame.beacon_leak_header");
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            receiver.sendSystemMessage(Msg.paint(header));
            receiver.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.9f, 0.5f);
        }
        for (Map.Entry<String, BlockPos> entry : match.teamBeacons.entrySet()) {
            BlockPos pos = entry.getValue();
            if (pos == null) continue;
            Component line = Component.translatable("blackchannel.minigame.beacon_coords",
                    teamLabel(server, entry.getKey()), pos.getX(), pos.getY(), pos.getZ());
            for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
                receiver.sendSystemMessage(Msg.paint(line));
            }
        }
    }

    /** Матч заканчивается, как только в игре осталась одна команда (или ни одной). */
    private static void checkVictory(MinecraftServer server, MatchData match) {
        if (!match.active) return;
        if (match.teamScores.size() < 2) return;
        Set<String> alive = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            String crew = match.playerCrew.get(id);
            if (crew == null || crew.isEmpty()) continue;
            if (match.permanentDead.contains(id)) continue;
            alive.add(crew);
        }
        if (alive.size() > 1) return;
        String winner = alive.isEmpty() ? "" : alive.iterator().next();
        endMatch(server, match, winner);
    }

    public static void endMatch(MinecraftServer server, MatchData match) {
        endMatch(server, match, null);
    }

    public static void endMatch(MinecraftServer server, MatchData match, String survivor) {
        boolean wasActive = match.active;
        match.active = false;
        match.finalActive = false;
        match.ended = true;
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level != null) {
            // Маяки отрядов и схроны не должны оставаться в мире после матча
            removeBeacons(level, match);
            removeCaches(level, match);
            restoreBorder(level, match);
            restoreKeep(level, match);
        }
        removeTeamNametags(server);
        clearScoreboard(server);
        REVIVE.clear();
        LAST_HIT.clear();
        DEPLOY.clear();
        resetChunkWait();
        PVP_WARN.clear();
        ALLY_WARN.clear();
        borderPending = false;
        nextDeployTick = 0;
        nextCacheTick = 0;
        LOOT_QUEUE.clear();
        match.downedPlayers.clear();
        String winner;
        if (survivor != null) {
            winner = survivor;
        } else {
            winner = "";
            int maxScore = -1;
            for (Map.Entry<String, Integer> e : match.teamScores.entrySet()) {
                if (e.getValue() > maxScore) {
                    maxScore = e.getValue();
                    winner = e.getKey();
                }
            }
        }
        String winnerLabel = winner == null || winner.isEmpty() ? "" : teamLabel(server, winner);
        int winnerScore = winner == null || winner.isEmpty() ? 0 : match.getScore(winner);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(Component.empty(), true);
            player.setForcedPose(null);
            player.setPose(Pose.STANDING);
            player.noPhysics = false;
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            if (winnerLabel.isEmpty()) {
                player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.match_end_none")));
            } else {
                player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.match_end", winnerLabel, winnerScore)));
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0f, 1.2f);
            }
        }
        if (wasActive) BlackChannelMod.LOGGER.info("BlackChannel match finished, winner: {}", winnerLabel);
        match.setDirty();
    }

    private static void placeCacheStep(MinecraftServer server, MatchData match, ServerLevel level) {
        if (match.cacheTick >= match.cacheTotal) return;
        if (level.getGameTime() < nextCacheTick) return;
        int index = match.cacheTick;
        if (index < 0 || index >= match.cachePositions.length) {
            match.cacheTick = match.cacheTotal;
            announceCaches(server, match);
            return;
        }
        BlockPos cand = match.cachePositions[index];
        if (cand == null) {
            match.cacheTick++;
            match.setDirty();
            return;
        }
        // Чанк грузится в фоне: тик сервера не стоит, генерация идёт в рабочих потоках.
        // Один запрос за раз и пауза между ними: генерация дальнего чанка тянет за собой
        // соседей (почти сотню чанков), пачкой это и вешало сервер.
        long started = System.nanoTime();
        int status = chunkStatus(level, cand.getX() >> 4, cand.getZ() >> 4);
        if (status == CHUNK_WAIT) return;
        // Уже загруженный чанк — ставим сразу; новый — только одну генерацию в секунду
        nextCacheTick = level.getGameTime() + (status == CHUNK_READY ? 4 : CACHE_GAP_TICKS);
        match.cacheTick++;
        match.setDirty();
        if (status == CHUNK_READY) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cand.getX(), cand.getZ());
            BlockPos feet = new BlockPos(cand.getX(), y, cand.getZ());
            if (match.inside(feet) && standableHere(level, feet)) placeChest(level, match, feet, level.getRandom());
        }
        logStep("cache " + match.cacheTick + "/" + match.cacheTotal, started);
        if (match.cacheTick >= match.cacheTotal) announceCaches(server, match);
    }

    private static void announceCaches(MinecraftServer server, MatchData match) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.caches_done", match.cacheTotal)));
        }
    }

    /**
     * Статус чанка: готов, ещё грузится или не удалось.
     * Один асинхронный запрос на чанк: тик сервера не стоит, а повторных запросов нет,
     * иначе на каждый тик улетал бы новый тикет и чанковая система захлёбывалась.
     */
    private static int chunkStatus(ServerLevel level, int cx, int cz) {
        if (level.getChunkSource().getChunkNow(cx, cz) != null) {
            if (chunkWaitX == cx && chunkWaitZ == cz) resetChunkWait();
            return CHUNK_READY;
        }
        long now = level.getGameTime();
        if (CHUNK_WAIT_FUTURE == null || chunkWaitX != cx || chunkWaitZ != cz) {
            CHUNK_WAIT_FUTURE = level.getChunkSource().getChunkFuture(cx, cz, ChunkStatus.FULL, true);
            chunkWaitX = cx;
            chunkWaitZ = cz;
            chunkWaitSince = now;
            return CHUNK_WAIT;
        }
        if (!CHUNK_WAIT_FUTURE.isDone()) {
            if (now - chunkWaitSince <= CHUNK_WAIT_LIMIT) return CHUNK_WAIT;
            resetChunkWait();
            return CHUNK_FAIL;
        }
        // Будущее готово, но в видимой карте чанка нет: не гоняемся за ним повторно
        resetChunkWait();
        return CHUNK_FAIL;
    }

    private static void resetChunkWait() {
        CHUNK_WAIT_FUTURE = null;
        chunkWaitX = Integer.MIN_VALUE;
        chunkWaitZ = Integer.MIN_VALUE;
    }

    private record Bunker(BlockPos beacon, BlockPos hatch) {}

    /**
     * Маяк в бункере: зал 5×5×3 под землёй, вокруг кирпичная коробка, чтобы зал не залило
     * и не засыпало, вход — шахта с лестницей и железный люк на поверхности.
     * Сверху видно только люк, поэтому найти чужой маяк с радара или с дороги нельзя.
     */
    private static Bunker buildBunker(ServerLevel level, BlockPos surface) {
        int cx = surface.getX();
        int cz = surface.getZ();
        int top = surface.getY();
        int floorY = Math.max(level.getMinBuildHeight() + 8, top - 7);
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        // Коробка: пол, стены и потолок
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                for (int y = floorY; y <= floorY + 4; y++) {
                    level.setBlock(new BlockPos(x, y, z), brick, 2);
                }
            }
        }
        // Внутри — воздух
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = floorY + 1; y <= floorY + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // Шахта в углу зала: стена под лестницу, сама лестница и люк на поверхности
        int sx = cx - 2;
        int sz = cz - 2;
        for (int y = floorY + 1; y <= top - 2; y++) {
            level.setBlock(new BlockPos(sx - 1, y, sz), brick, 2);
            level.setBlock(new BlockPos(sx, y, sz), Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.EAST), 2);
        }
        BlockPos hatch = new BlockPos(sx, top - 1, sz);
        level.setBlock(hatch, Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH), 3);
        return new Bunker(new BlockPos(cx, floorY + 1, cz), hatch);
    }

    /** Сколько секунд щита у маяка осталось. Ноль — щит кончился или выключен. */
    private static long beaconShieldLeft(MatchData match, long now) {
        int seconds = Config.BEACON_SHIELD_SECONDS.get();
        if (seconds <= 0 || !match.active) return 0L;
        long until = match.matchStartTick + seconds * 20L;
        return Math.max(0L, (until - now + 19L) / 20L);
    }

    /** Своих предупреждаем, что маяк ковыряют: без этого щит выглядит как неуязвимость без причины. */
    private static void warnBeaconAttack(MinecraftServer server, MatchData match, String crew, long now) {
        Long last = BEACON_ALERT.get(crew);
        if (last != null && now - last < 600L) return;
        BEACON_ALERT.put(crew, now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!crew.equals(match.playerCrew.getOrDefault(player.getUUID(), ""))) continue;
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.beacon_under_attack")));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 1.0f, 0.6f);
        }
    }

    /** Взрывы не должны снимать щит с маяка в первые минуты. */
    public static void onExplode(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MatchData match = MatchData.get(level.getServer());
        if (!match.active) return;
        if (beaconShieldLeft(match, level.getGameTime()) <= 0) return;
        for (BlockPos pos : match.teamBeacons.values()) {
            if (pos != null) event.getAffectedBlocks().remove(pos);
        }
    }

    /** Аирдроп: открытый сундук с верхушкой лута в случайной точке карты, координаты видят все. */
    private static void dropAirdrop(MinecraftServer server, MatchData match, ServerLevel level) {
        RandomSource random = level.getRandom();
        int half = Math.max(32, match.size / 2 - 32);
        BlockPos spot = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = match.centerX + random.nextInt(half * 2 + 1) - half;
            int z = match.centerZ + random.nextInt(half * 2 + 1) - half;
            BlockPos feet = loadedGround(level, x, z, random);
            if (chestFits(level, feet)) {
                spot = feet;
                break;
            }
        }
        if (spot == null) return;
        level.setBlock(spot, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.from2DDataValue(random.nextInt(4))), 3);
        if (!(level.getBlockEntity(spot) instanceof ChestBlockEntity chest)) return;
        addStacks(chest, rollLoot(Config.CACHE_AIRDROP.get(), Config.AIRDROP_ITEMS.get(), random));
        chest.setCustomName(Component.translatable("block.blackchannel.field_cache"));
        chest.getPersistentData().putBoolean("bcLoot", true);
        chest.setChanged();
        match.cacheChests.add(spot.immutable());
        match.setDirty();
        Component header = Component.translatable("blackchannel.minigame.airdrop_header");
        Component line = Component.translatable("blackchannel.minigame.airdrop_at", spot.getX(), spot.getY(), spot.getZ());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Msg.paint(header));
            player.sendSystemMessage(Msg.paint(line));
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    /** Одна база за тик: поставить маяк и высадить её игроков. */
    private static void deployStep(MinecraftServer server, MatchData match, ServerLevel level) {
        if (level.getGameTime() < nextDeployTick) return;
        PendingBase base = DEPLOY.get(0);
        int status = chunkStatus(level, base.x() >> 4, base.z() >> 4);
        if (status == CHUNK_WAIT) return;
        // Крайний случай (генерация не удалась за 10 с): догружаем одну базу синхронно,
        // иначе игроки останутся на старом месте
        long started = System.nanoTime();
        if (status == CHUNK_FAIL) level.getChunk(base.x() >> 4, base.z() >> 4);
        BlockPos feet = loadedGround(level, base.x(), base.z(), level.getRandom());
        // Маяк больше не стоит открыто на поверхности: бункер под базой и люк на земле
        Bunker bunker = buildBunker(level, feet);
        level.setBlock(bunker.beacon(), ModContent.BEACON.get().defaultBlockState(), 3);
        match.setBeacon(base.crew(), bunker.beacon());
        for (UUID id : base.players()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            BlockPos spot = loadedGround(level, feet.getX(), feet.getZ(), player.getRandom());
            match.drops.put(id, MatchData.Drop.at(spot.getX(), spot.getY(), spot.getZ(), base.yaw()));
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setForcedPose(null);
            player.setPose(Pose.STANDING);
            place(player, level, spot, base.yaw());
            kit(player, match);
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.bunker_hint",
                    bunker.hatch().getX(), bunker.hatch().getY(), bunker.hatch().getZ())));
        }
        match.setDirty();
        DEPLOY.remove(0);
        nextDeployTick = level.getGameTime() + DEPLOY_GAP_TICKS;
        logStep("base " + base.crew() + " at " + feet.toShortString(), started);
        if (DEPLOY.isEmpty() && borderPending) {
            borderPending = false;
            applyBorder(level, match, true);
            BlackChannelMod.LOGGER.info("BlackChannel: all bases deployed, border applied");
        }
    }

    /** Пишем в лог только заметные по времени шаги: по нему видно, что именно тормозит. */
    private static void logStep(String what, long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (ms >= 50L) BlackChannelMod.LOGGER.warn("BlackChannel: {} took {} ms", what, ms);
    }

    /** Земля в уже загруженном чанке: перебираем точки, не выходя за загруженные чанки. */
    private static BlockPos loadedGround(ServerLevel level, int x, int z, RandomSource random) {
        BlockPos fallback = null;
        for (int attempt = 0; attempt < 16; attempt++) {
            int sx = x + (attempt == 0 ? 0 : random.nextInt(13) - 6);
            int sz = z + (attempt == 0 ? 0 : random.nextInt(13) - 6);
            if (level.getChunkSource().getChunkNow(sx >> 4, sz >> 4) == null) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            BlockPos feet = new BlockPos(sx, y, sz);
            if (fallback == null) fallback = feet;
            if (standableHere(level, feet)) return feet;
        }
        if (fallback != null) return fallback;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    public static void onServerStarted(MinecraftServer server) {
        MatchData match = MatchData.get(server);
        if (!match.active) return;
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null) return;
        applyBorder(level, match, false);
        applyKeep(level, match, false);
    }

    /** Загрузка чанка: только запоминаем контейнеры, мир здесь менять нельзя. */
    public static void onChunk(ServerLevel level, LevelChunk chunk) {
        MatchData match = MatchData.get(level.getServer());
        if (!match.active || !Str.dim(level).equals(match.dim)) return;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof RandomizableContainerBlockEntity box)) continue;
            if (!isContainer(be.getBlockState().getBlock())) continue;
            if (!match.inside(be.getBlockPos())) continue;
            if (LOOT_QUEUE.size() >= LOOT_QUEUE_LIMIT) return;
            LOOT_QUEUE.add(be.getBlockPos().immutable());
        }
    }

    /** Разбор очереди сундуков: несколько штук за тик, уже в нормальном контексте тика. */
    private static void lootStep(MatchData match, ServerLevel level) {
        for (int i = 0; i < LOOT_PER_TICK && !LOOT_QUEUE.isEmpty(); i++) {
            BlockPos pos = LOOT_QUEUE.poll();
            if (pos == null) return;
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity box)) continue;
            if (!isContainer(box.getBlockState().getBlock())) continue;
            try {
                injectLoot(level, match, box);
            } catch (RuntimeException e) {
                BlackChannelMod.LOGGER.warn("field chest skip at {}: {}", pos, e.toString());
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide()) return;
        MatchData match = MatchData.get(victim.getServer());
        if (!match.active) return;
        // Если уже downed — просто игнорим (должен быть неуязвим)
        if (match.downedPlayers.containsKey(victim.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (match.permanentDead.contains(victim.getUUID())) {
            event.setCanceled(true);
            return;
        }
        String crew = match.playerCrew.getOrDefault(victim.getUUID(), "");
        // Определяем killer: прямой убийца или тот, кто последним бил (падение, лава, добивание)
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp && sp != victim ? sp : null;
        if (killer == null) killer = lastAttacker(victim, match);
        if (killer != null) awardKill(killer, victim, match);

        ServerLevel level = Ops.levelOf(victim.getServer(), match.dim);
        BlockPos respawn = null;
        String kind = "";
        if (!match.finalActive) {
            // Кровать — точка возврата только пока цел маяк отряда
            BlockPos beaconPos = match.getBeacon(crew);
            boolean beaconUp = beaconValid(level, match, beaconPos);
            BlockPos bedPos = match.getBed(victim.getUUID());
            if (beaconUp) {
                if (bedValid(level, match, bedPos)) {
                    respawn = bedPos;
                    kind = "bed";
                } else if (beaconPos != null) {
                    respawn = beaconPos;
                    kind = "beacon";
                }
            } else if (bedPos != null) {
                victim.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.bed_needs_beacon")));
            }
        }
        if (respawn == null || kind.isEmpty()) {
            killPermanently(victim, match, crew);
            match.setDirty();
            event.setCanceled(true);
            return;
        }
        int downedTicks = Config.DOWNED_DURATION_TICKS.get();
        MatchData.DownedState ds = new MatchData.DownedState();
        ds.downedAtTick = victim.level().getGameTime();
        ds.pos = victim.blockPosition();
        ds.dim = Str.dim(victim.level());
        ds.respawnPos = respawn;
        ds.respawnKind = kind;
        if (killer != null) {
            NetData.Profile kp = NetData.get(killer).profile(killer.getUUID());
            ds.killerHandle = kp.handle == null ? "" : kp.handle;
            ds.killerCrew = match.playerCrew.getOrDefault(killer.getUUID(), "");
        }
        match.down(victim.getUUID(), ds);
        victim.setHealth(victim.getMaxHealth());
        victim.setDeltaMovement(Vec3.ZERO);
        // Лежит пластом. Поза сна без кровати клиент не поворачивает (модель остаётся стоять),
        // а поза плавания как раз кладёт модель на землю.
        victim.setForcedPose(Pose.SWIMMING);
        victim.setPose(Pose.SWIMMING);
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, downedTicks + 40, 1, false, false));
        victim.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.downed", downedTicks / 20)));
        victim.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.downed_help", downedTicks / 20)));
        victim.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.downed_seconds",
                downedTicks / 20,
                Component.translatable(kind.equals("bed") ? "blackchannel.minigame.respawn_bed" : "blackchannel.minigame.respawn_beacon"))));
        victim.getServer().getPlayerList().broadcastSystemMessage(Msg.paint(
                Component.translatable("blackchannel.minigame.downed_announce",
                        victim.getGameProfile().getName(), downedTicks / 20)), false);
        match.setDirty();
        event.setCanceled(true);
    }

    private static boolean bedValid(ServerLevel level, MatchData match, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!match.inside(pos)) return false;
        if (!level.isLoaded(pos)) level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (level.getBlockState(pos).getBlock() instanceof BedBlock) return true;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(dir)).getBlock() instanceof BedBlock) return true;
        }
        return false;
    }

    private static boolean beaconValid(ServerLevel level, MatchData match, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!match.inside(pos)) return false;
        if (!level.isLoaded(pos)) level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return level.getBlockState(pos).getBlock() == ModContent.BEACON.get();
    }

    private static void killPermanently(ServerPlayer player, MatchData match, String crew) {
        match.permanentDead.add(player.getUUID());
        match.clearDowned(player.getUUID());
        REVIVE.remove(player.getUUID());
        LAST_HIT.remove(player.getUUID());
        player.setHealth(player.getMaxHealth());
        player.setForcedPose(null);
        player.setPose(Pose.STANDING);
        player.noPhysics = false;
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.permanent_dead")));
        player.getServer().getPlayerList().broadcastSystemMessage(Msg.paint(
                Component.translatable("blackchannel.minigame.permanent_dead_announce", player.getGameProfile().getName())), false);
        checkTeamElimination(match, crew);
    }

    /** Возврат на кровать или маяк после ранения. */
    private static void respawnTo(ServerPlayer player, MatchData match, MatchData.DownedState ds) {
        ServerLevel level = Ops.levelOf(player.getServer(), match.dim);
        player.setForcedPose(null);
        player.setPose(Pose.STANDING);
        player.noPhysics = false;
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos pos = ds == null ? null : ds.respawnPos;
        String kind = ds == null || ds.respawnKind == null ? "" : ds.respawnKind;
        if (pos != null && level != null) {
            BlockPos feet = findSafe(level, pos.getX(), pos.getZ(), player.getRandom());
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            protect(player, level.getGameTime() + 100L);
        } else {
            assign(player, match, true);
            protect(player, player.serverLevel().getGameTime() + 100L);
        }
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.respawned",
                Component.translatable(kind.equals("beacon") ? "blackchannel.minigame.respawn_beacon" : "blackchannel.minigame.respawn_bed"))));
    }

    private static void awardKill(ServerPlayer killer, ServerPlayer victim, MatchData match) {
        String killerCrew = match.playerCrew.getOrDefault(killer.getUUID(), "");
        String victimCrew = match.playerCrew.getOrDefault(victim.getUUID(), "");
        if (killerCrew.isEmpty() || killerCrew.equals(victimCrew)) return;
        int pts = Config.SCORE_KILL.get();
        if (pts > 0) {
            match.addScore(killerCrew, pts);
            killer.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.score_kill", pts)));
        }
    }

    /** Команда выбывает, когда все её игроки погибли окончательно. */
    private static void checkTeamElimination(MatchData match, String crew) {
        if (crew == null || crew.isEmpty()) return;
        if (!match.playerCrew.containsValue(crew)) return;
        if (match.teamAlive(crew)) return;
        match.eliminatedTeams.add(crew);
    }

    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        MatchData match = MatchData.get(victim.getServer());
        if (!match.active) {
            if (event.getSource().getEntity() instanceof ServerPlayer other && other != victim) Ops.learnHit(victim, other);
            return;
        }
        // Раненый и погибший не получают урон вообще
        if (match.downedPlayers.containsKey(victim.getUUID()) || match.permanentDead.contains(victim.getUUID())) {
            event.setCanceled(true);
            return;
        }
        long now = victim.serverLevel().getGameTime();
        ServerPlayer attacker = attackerOf(event.getSource(), victim);
        // Урон не от игрока (мобы, падение, огонь, лава) проходит всегда
        if (attacker == null) return;
        // Своих не бьём ничем: ни мечом, ни стрелой, ни взрывом
        if (allies(match, attacker, victim)) {
            event.setCanceled(true);
            warnAlly(attacker, now);
            return;
        }
        // Отсутствие PvP в начале матча: игрок по игроку не проходит вообще
        if (pvpBlocked(match, now)) {
            event.setCanceled(true);
            return;
        }
        LAST_HIT.put(victim.getUUID(), new LastHit(attacker.getUUID(), now));
        boolean wall = shielded(victim) || shielded(attacker);
        if (shielded(attacker)) PROTECT.remove(attacker.getUUID());
        if (wall) {
            event.setCanceled(true);
            return;
        }
        Ops.learnHit(victim, attacker);
    }

    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) return;
        MatchData.DownedState ds = match.getDowned(player.getUUID());
        if (ds == null) return;
        // Раненый лежит: ни ходить, ни прыгать, ни взаимодействовать
        freezeDowned(player, ds);
        long now = player.level().getGameTime();
        ServerLevel matchLevel = Ops.levelOf(player.getServer(), match.dim);
        // Маяк отряда — общий якорь: сломали его, и кровать тоже перестаёт держать игрока
        boolean beaconUp = beaconValid(matchLevel, match, match.getBeacon(match.playerCrew.getOrDefault(player.getUUID(), "")));
        boolean pointOk = beaconUp && (ds.respawnKind != null && ds.respawnKind.equals("bed")
                ? bedValid(matchLevel, match, ds.respawnPos)
                : beaconValid(matchLevel, match, ds.respawnPos));
        // Точка возрождения пропала — погибает окончательно
        if (!pointOk) {
            REVIVE.remove(player.getUUID());
            killPermanently(player, match, match.playerCrew.getOrDefault(player.getUUID(), ""));
            match.setDirty();
            return;
        }
        long duration = Config.DOWNED_DURATION_TICKS.get();
        if (now >= ds.downedAtTick + duration) {
            match.clearDowned(player.getUUID());
            respawnTo(player, match, ds);
            match.setDirty();
            return;
        }
        // Проверяем воскрешение тиммейтом (REVIVE map)
        Long reviveUntil = REVIVE.get(player.getUUID());
        if (reviveUntil != null && now >= reviveUntil) {
            REVIVE.remove(player.getUUID());
            match.clearDowned(player.getUUID());
            match.reviveUsed.add(player.getUUID());
            // Встаёт там, где лежал, без переноса на базу
            reviveInPlace(player);
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.revived")));
            player.getServer().getPlayerList().broadcastSystemMessage(Msg.paint(
                    Component.translatable("blackchannel.minigame.revived_announce", player.getGameProfile().getName())), false);
            match.setDirty();
        }
    }

    /** Лежит на месте, не двигается и не взаимодействует с миром. */
    private static void freezeDowned(ServerPlayer player, MatchData.DownedState ds) {
        // ForcedPose держит позу на сервере, setPose отправляет её клиентам, чтобы игрок лежал пластом.
        if (player.getForcedPose() != Pose.SWIMMING) player.setForcedPose(Pose.SWIMMING);
        if (player.getPose() != Pose.SWIMMING) player.setPose(Pose.SWIMMING);
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.noPhysics = true;
        if (ds.pos == null || ds.dim == null || !ds.dim.equals(Str.dim(player.level()))) return;
        BlockPos p = ds.pos;
        double dx = player.getX() - (p.getX() + 0.5D);
        double dz = player.getZ() - (p.getZ() + 0.5D);
        double dy = player.getY() - p.getY();
        if (dx * dx + dz * dz > 0.0016D || Math.abs(dy) > 0.5D) {
            player.teleportTo(player.serverLevel(), p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0f;
        }
    }

    /** Воскрешение другом: встаёт на том же месте. */
    private static void reviveInPlace(ServerPlayer player) {
        player.setForcedPose(null);
        player.setPose(Pose.STANDING);
        player.noPhysics = false;
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setDeltaMovement(Vec3.ZERO);
        protect(player, player.level().getGameTime() + 100L);
    }

    /** Игрок сейчас лежит раненым. */
    public static boolean isDowned(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        MatchData match = MatchData.get(player.getServer());
        return match.active && match.downedPlayers.containsKey(player.getUUID());
    }

    private static ServerPlayer lastAttacker(ServerPlayer victim, MatchData match) {
        LastHit hit = LAST_HIT.get(victim.getUUID());
        if (hit == null) return null;
        long now = victim.level().getGameTime();
        if (now - hit.tick() > KILL_CREDIT_TICKS) {
            LAST_HIT.remove(victim.getUUID());
            return null;
        }
        ServerPlayer attacker = victim.getServer().getPlayerList().getPlayer(hit.attacker());
        if (attacker == null || attacker == victim) return null;
        if (!match.playerCrew.containsKey(attacker.getUUID())) return null;
        return attacker;
    }

    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (isDowned(attacker)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer)) return;
        MatchData match = MatchData.get(attacker.getServer());
        long now = attacker.serverLevel().getGameTime();
        // Свой не получает даже отбрасывания: замах не проходит
        if (match.active && event.getTarget() instanceof ServerPlayer ally && allies(match, attacker, ally)) {
            event.setCanceled(true);
            warnAlly(attacker, now);
            return;
        }
        // Замах по игроку не проходит вовсе: ни урона, ни отбрасывания
        if (pvpBlocked(match, now)) {
            event.setCanceled(true);
            warnPvpOff(attacker, match, now);
            return;
        }
        if (!shielded(attacker)) return;
        PROTECT.remove(attacker.getUUID());
        event.setCanceled(true);
    }

    /** PvP запрещён в начале матча. */
    /** Кто ударил: сам игрок или владелец стрелы, зелья, взрывчатки. */
    private static ServerPlayer attackerOf(net.minecraft.world.damagesource.DamageSource source, ServerPlayer victim) {
        if (source == null) return null;
        net.minecraft.world.entity.Entity direct = source.getEntity();
        if (direct instanceof ServerPlayer sp) return sp == victim ? null : sp;
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile shot
                && shot.getOwner() instanceof ServerPlayer sp) return sp == victim ? null : sp;
        return null;
    }

    /** Игроки в одном отряде: урон по своим не проходит (как friendlyFire false у ванильных команд). */
    private static boolean allies(MatchData match, ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null || a == b || !match.active) return false;
        String crewA = match.playerCrew.get(a.getUUID());
        String crewB = match.playerCrew.get(b.getUUID());
        return crewA != null && !crewA.isEmpty() && crewA.equals(crewB);
    }

    private static void warnAlly(ServerPlayer player, long now) {
        Long last = ALLY_WARN.get(player.getUUID());
        if (last != null && now - last < 60L) return;
        ALLY_WARN.put(player.getUUID(), now);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.minigame.ally_fire")), true);
    }

    private static boolean pvpBlocked(MatchData match, long now) {
        return match.active && now < match.pvpFreeUntil;
    }

    /** Короткое сообщение, чтобы игрок понимал, почему удар не проходит. */
    private static void warnPvpOff(ServerPlayer player, MatchData match, long now) {
        Long last = PVP_WARN.get(player.getUUID());
        if (last != null && now - last < 40L) return;
        PVP_WARN.put(player.getUUID(), now);
        long left = Math.max(0L, (match.pvpFreeUntil - now) / 20L);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.minigame.pvp_off", left)), true);
    }

    public static void onRespawn(ServerPlayer player) {
        MatchData match = MatchData.get(player.getServer());
        player.setForcedPose(null);
        player.setPose(Pose.STANDING);
        if (!match.active) return;
        // Если permanently dead — сразу в наблюдатели
        if (match.permanentDead.contains(player.getUUID())) {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            return;
        }
        protect(player, player.serverLevel().getGameTime() + 160L);
        ServerLevel level = Ops.levelOf(player.getServer(), match.dim);
        // Своя кровать важнее точки сбора, но работает только пока цел маяк отряда
        BlockPos bed = match.getBed(player.getUUID());
        BlockPos beacon = match.getBeacon(match.playerCrew.getOrDefault(player.getUUID(), ""));
        if (level != null && beaconValid(level, match, beacon) && bedValid(level, match, bed)) {
            BlockPos feet = findSafe(level, bed.getX(), bed.getZ(), player.getRandom());
            place(player, level, feet, player.getYRot());
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.respawned",
                    Component.translatable("blackchannel.minigame.respawn_bed"))));
            return;
        }
        // Иначе — на точку старта
        MatchData.Drop drop = match.drops.get(player.getUUID());
        if (drop == null || level == null) {
            assign(player, match, true);
            return;
        }
        BlockPos feet = findSafe(level, drop.x, drop.z, player.getRandom());
        place(player, level, feet, drop.yaw);
    }

    public static void onLogin(ServerPlayer player) {
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) {
            // Перед матчем игрок выбирает команду в меню (с задержкой, чтобы экран загрузки закрылся)
            PENDING_TEAM_MENU.put(player.getUUID(), player.getServer().getTickCount() + 40);
            return;
        }
        if (match.permanentDead.contains(player.getUUID())) {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            return;
        }
        if (!match.drops.containsKey(player.getUUID())) assign(player, match, true);
        // Вошёл в идущий матч — играет в выживании, как и все
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        kit(player, match);
        match.setDirty();
    }

    public static void onInteractEntity(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isDowned(player)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) return;
        if (!match.downedPlayers.containsKey(target.getUUID())) return;
        // Проверяем: тиммейт?
        String playerCrew = match.playerCrew.getOrDefault(player.getUUID(), "");
        String targetCrew = match.playerCrew.getOrDefault(target.getUUID(), "");
        if (playerCrew.isEmpty() || !playerCrew.equals(targetCrew)) return;
        // Проверяем: уже использовал воскрешение?
        if (match.reviveUsed.contains(target.getUUID())) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.revive_already")));
            return;
        }
        // Проверяем дистанцию
        if (player.distanceToSqr(target) > 2.0) return;
        // Проверяем: уже идёт воскрешение?
        if (REVIVE.containsKey(target.getUUID())) return;
        // Начинаем воскрешение
        long standTime = Config.REVIVE_STAND_SECONDS.get() * 20L;
        REVIVE.put(target.getUUID(), player.level().getGameTime() + standTime);
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.revive_stand", Config.REVIVE_STAND_SECONDS.get())));
        target.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.revive_stand_target", player.getGameProfile().getName(), Config.REVIVE_STAND_SECONDS.get())));
    }

    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (isDowned(player)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isContainer(level.getBlockState(event.getPos()).getBlock())) return;
        TrapData data = TrapData.get(player.getServer());
        TrapData.Trap trap = data.at(Str.dim(level), event.getPos());
        if (trap != null) {
            int next = scanLogger(level, event.getPos(), trap.owner, trap.charges, trap.seen, player);
            if (next != trap.charges) {
                trap.charges = next;
                data.setDirty();
            }
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = event.getPos().relative(dir);
            if (!(level.getBlockEntity(neighbor) instanceof LoggerBlockEntity be)) continue;
            int next = scanLogger(level, neighbor, be.owner, be.charges, be.seen, player);
            if (next != be.charges) {
                be.charges = next;
                be.setChanged();
            }
        }
    }

    public static void onPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isDowned(player)) {
            event.setCanceled(true);
            return;
        }
        MatchData match = MatchData.get(level.getServer());
        if (!match.active) return;
        Block block = event.getPlacedBlock().getBlock();
        if (block instanceof BedBlock) {
            match.setBed(player.getUUID(), event.getPos());
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.bed_placed")));
        }
        if (block == ModContent.BEACON.get()) {
            String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
            if (!crew.isEmpty()) {
                match.setBeacon(crew, event.getPos());
                player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.beacon_placed", crew)));
                level.getServer().getPlayerList().broadcastSystemMessage(Msg.paint(
                        Component.translatable("blackchannel.minigame.beacon_placed_announce",
                                player.getGameProfile().getName(), crew)), false);
            }
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MatchData match = MatchData.get(level.getServer());
        Block block = event.getState().getBlock();
        ServerPlayer breaker = event.getPlayer() instanceof ServerPlayer sp ? sp : null;
        if (breaker != null && isDowned(breaker)) {
            event.setCanceled(true);
            return;
        }
        // Обработка слома кровати
        if (block instanceof BedBlock && match.active && breaker != null) {
            BlockPos pos = event.getPos();
            UUID bedOwner = findBedOwner(match, pos);
            if (bedOwner != null) {
                String breakerCrew = match.playerCrew.getOrDefault(breaker.getUUID(), "");
                String ownerCrew = match.playerCrew.getOrDefault(bedOwner, "");
                if (!breakerCrew.equals(ownerCrew)) {
                    match.setBed(bedOwner, null);
                    int pts = Config.SCORE_BED.get();
                    if (pts > 0) {
                        match.addScore(breakerCrew, pts);
                        breaker.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.bed_destroyed", pts)));
                    }
                    level.getServer().getPlayerList().broadcastSystemMessage(Msg.paint(
                            Component.translatable("blackchannel.minigame.bed_destroyed_announce",
                                    breaker.getGameProfile().getName(), colored(ownerCrew))), false);
                }
            }
        }
        // Обработка слома маяка отряда — нельзя сломать свой маяк
        if (block == ModContent.BEACON.get() && match.active && breaker != null) {
            BlockPos pos = event.getPos();
            String beaconCrew = findBeaconOwner(match, pos);
            if (beaconCrew != null) {
                String breakerCrew = match.playerCrew.getOrDefault(breaker.getUUID(), "");
                if (!breakerCrew.equals(beaconCrew)) {
                    // Первые beaconShieldSeconds секунд маяк не ломается: он ещё в бункере, но щит честнее
                    long shieldLeft = beaconShieldLeft(match, level.getGameTime());
                    if (shieldLeft > 0L) {
                        event.setCanceled(true);
                        breaker.sendSystemMessage(Msg.paint(Component.translatable(
                                "blackchannel.minigame.beacon_shield_left", shieldLeft)));
                        warnBeaconAttack(level.getServer(), match, beaconCrew, level.getGameTime());
                        return;
                    }
                    match.setBeacon(beaconCrew, null);
                    int pts = Config.SCORE_BEACON.get();
                    if (pts > 0) {
                        match.addScore(breakerCrew, pts);
                        breaker.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.beacon_destroyed", pts, colored(beaconCrew))));
                    }
                    level.getServer().getPlayerList().broadcastSystemMessage(Msg.paint(
                            Component.translatable("blackchannel.minigame.beacon_destroyed_announce",
                                    breaker.getGameProfile().getName(), colored(beaconCrew))), false);
                } else {
                    // Нельзя сломать свой маяк
                    event.setCanceled(true);
                    breaker.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.beacon_own")));
                }
            }
        }
        // Учёт ловушек в сундуках, как и раньше
        if (isContainer(block)) {
            TrapData.get(level.getServer()).removeAt(Str.dim(level), event.getPos());
        }
    }

    /** Имя команды с её цветом для чата. */
    private static Component colored(String crew) {
        String label = crew == null || crew.isEmpty() ? "?" : crew;
        Teams.Team team = Teams.byId(crew);
        if (team != null) return Component.literal(team.name()).withStyle(team.color());
        return Component.literal(label);
    }

    /** Убирает поставленные модом схроны: сундук и крышку над ним. */
    private static void removeCaches(ServerLevel level, MatchData match) {
        for (BlockPos pos : new ArrayList<>(match.cacheChests)) {
            if (pos == null) continue;
            // Схронов немного: чанк можно и загрузить, зато блок точно уберётся
            if (!level.isLoaded(pos)) level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
            if (!chest.getPersistentData().getBoolean("bcLoot")) continue;
            // Оставляем на месте сундука каменистую землю, как и на крышке: ямы после матча не остаётся
            level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
        }
        match.cacheChests.clear();
    }

    /** Убирает блоки маяков отряда из мира. */
    private static void removeBeacons(ServerLevel level, MatchData match) {
        for (BlockPos pos : new ArrayList<>(match.teamBeacons.values())) {
            if (pos == null) continue;
            // Маяков максимум по числу отрядов: чанк можно и загрузить, зато блок точно уберётся
            if (!level.isLoaded(pos)) level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (level.getBlockState(pos).getBlock() == ModContent.BEACON.get()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        match.teamBeacons.clear();
    }

    private static UUID findBedOwner(MatchData match, BlockPos pos) {
        for (Map.Entry<UUID, BlockPos> e : match.bedPositions.entrySet()) {
            BlockPos bed = e.getValue();
            if (bed.equals(pos)) return e.getKey();
            // Кровать занимает два блока: если сломали вторую половину, владельца всё равно надо найти
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (bed.relative(dir).equals(pos)) return e.getKey();
            }
        }
        return null;
    }

    private static String findBeaconOwner(MatchData match, BlockPos pos) {
        for (Map.Entry<String, BlockPos> e : match.teamBeacons.entrySet()) {
            if (e.getValue().equals(pos)) return e.getKey();
        }
        return null;
    }

    public static boolean isContainer(Block block) {
        return block instanceof ChestBlock || block instanceof BarrelBlock;
    }

    public static void attachLogger(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        if (!(context.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = context.getClickedPos();
        TrapData data = TrapData.get(player.getServer());
        if (data.at(Str.dim(level), pos) != null) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.logger_full")), true);
            return;
        }
        ItemStack stack = context.getItemInHand();
        int charges = chargesOf(stack);
        if (charges <= 0) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.logger_dead")), true);
            return;
        }
        TrapData.Trap trap = new TrapData.Trap();
        trap.dim = Str.dim(level);
        trap.x = pos.getX();
        trap.y = pos.getY();
        trap.z = pos.getZ();
        trap.owner = player.getUUID();
        trap.charges = charges;
        data.traps.add(trap);
        data.setDirty();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.result.logger_set")), true);
    }

    public static boolean scanBlock(ServerLevel level, BlockPos pos, UUID owner, List<UUID> seen) {
        if (owner == null) return false;
        int radius = Config.LOGGER_RADIUS.get();
        long r2 = (long) radius * radius;
        int before = seen.size();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r2) continue;
            logOne(level, pos, owner, 1, seen, player, false);
        }
        return seen.size() != before;
    }

    public static int scanLogger(ServerLevel level, BlockPos pos, UUID owner, int charges, List<UUID> seen, ServerPlayer only) {
        if (charges <= 0 || owner == null) return charges;
        if (only != null) return logOne(level, pos, owner, charges, seen, only, true);
        int left = charges;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12.25) continue;
            left = logOne(level, pos, owner, left, seen, player, true);
            if (left <= 0) break;
        }
        return left;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.help")), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        MatchData match = MatchData.get(source.getServer());
        String crew = "-";
        if (source.getEntity() instanceof ServerPlayer player) {
            String code = NetData.get(player).profile(player.getUUID()).crew;
            crew = code == null || code.isEmpty() ? "-" : code;
        }
        long pvpFree = 0;
        ServerLevel level = Ops.levelOf(source.getServer(), match.dim);
        if (match.active && level != null) {
            pvpFree = Math.max(0, (match.pvpFreeUntil - level.getGameTime()) / 20L);
        }
        int online = teamsOnline(source.getServer());
        int gap = match.active && match.spread > 0
                ? match.spread
                : minSpread(match, online);
        int caches = cacheCount(match, online);
        String center = match.centerSet ? match.centerX + " " + match.centerY + " " + match.centerZ : "-";
        String crewShown = crew;
        String state = match.active ? "on" : "off";
        long pvpLeft = pvpFree;
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.status",
                Component.translatable("blackchannel.minigame." + state), center, match.size, gap, crewShown, pvpLeft, caches)), false);
        return 1;
    }

    private static void flushTeamMenu(MinecraftServer server) {
        if (PENDING_TEAM_MENU.isEmpty()) return;
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = PENDING_TEAM_MENU.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (entry.getValue() > now) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (MatchData.get(server).active) continue;
            ModNetwork.open(player, "team");
        }
    }

    private static int openTeamMenu(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.need_player")));
            return 0;
        }
        ModNetwork.open(player, "team");
        return 1;
    }

    /** Выбор команды из меню. Доступен только до старта матча. */
    public static void setTeam(ServerPlayer player, String teamId) {
        MatchData match = MatchData.get(player.getServer());
        if (match.active) {
            player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.minigame.team_locked")), true);
            return;
        }
        Teams.Team team = Teams.byId(teamId);
        if (team == null) return;
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        profile.crew = team.id();
        data.setDirty();
        ModNetwork.syncFull(player);
        player.displayClientMessage(Msg.paint(Component.translatable("blackchannel.minigame.team_set", team.name())), true);
    }

    private static int centerHere(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.need_player")));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        return setCenter(source, player.serverLevel(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static int centerAt(CommandSourceStack source, int x, int y, int z) {
        return setCenter(source, source.getLevel(), x, y, z);
    }

    private static int setCenter(CommandSourceStack source, ServerLevel level, int x, int y, int z) {
        MatchData match = MatchData.get(source.getServer());
        if (match.active) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.already")));
            return 0;
        }
        match.centerSet = true;
        match.centerX = x;
        match.centerY = y;
        match.centerZ = z;
        match.dim = Str.dim(level);
        match.setDirty();
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.center_set", x, y, z, Str.dimShort(match.dim))), true);
        return 1;
    }

    private static int size(CommandSourceStack source, int blocks) {
        MatchData match = MatchData.get(source.getServer());
        if (match.active) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.already")));
            return 0;
        }
        match.size = blocks;
        match.setDirty();
        int online = teamsOnline(source.getServer());
        int gap = minSpread(match, online);
        int caches = cacheCount(match, online);
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.size_set", blocks, gap, caches)), true);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        MatchData match = MatchData.get(server);
        if (match.active) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.already")));
            return 0;
        }
        if (!match.centerSet || match.size < 256) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.need_setup")));
            return 0;
        }
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.no_level")));
            return 0;
        }
        Map<String, List<ServerPlayer>> groups = new java.util.LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String code = NetData.get(server).profile(player.getUUID()).crew;
            String key = code == null || code.isEmpty() ? "solo:" + player.getUUID() : "crew:" + code;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(player);
        }
        // Расстановка баз: безопасная область, запретная середина и минимальное расстояние между базами.
        // Только арифметика, без обращения к миру: точки проверяются на местности уже при высадке.
        Layout plan = layout(match, Math.max(1, groups.size()), level.getRandom());
        int gap = plan.distance();
        boolean cramped = plan.relaxed();
        match.spread = gap;
        match.drops.clear();
        match.anchors.clear();
        match.kitted.clear();
        match.teamScores.clear();
        // Маяки и схроны от прошлой игры: сначала убрать из мира, потом забыть координаты
        removeBeacons(level, match);
        removeCaches(level, match);
        match.bedPositions.clear();
        match.downedPlayers.clear();
        match.reviveUsed.clear();
        match.permanentDead.clear();
        match.playerCrew.clear();
        match.playerColorIdx.clear();
        match.eliminatedTeams.clear();
        PROTECT.clear();
        REVIVE.clear();
        LAST_HIT.clear();
        List<String> keys = new ArrayList<>(groups.keySet());
        List<int[]> spots = plan.spots();
        RandomSource random = level.getRandom();
        DEPLOY.clear();
        resetChunkWait();
        PVP_WARN.clear();
        ALLY_WARN.clear();
        nextDeployTick = 0;
        nextCacheTick = level.getGameTime() + CACHE_START_DELAY;
        LOOT_QUEUE.clear();
        borderPending = true;
        // Генерацию чанков, постановку маяка и телепорт раскладываем по тикам:
        // десятки чанков в одном тике на обычном мире вешают сервер на минуты.
        for (int i = 0; i < keys.size(); i++) {
            int[] spot = spots.get(i);
            String key = keys.get(i);
            int colorIdx = i % TEAM_COLORS.size();
            String code = key.startsWith("crew:") ? key.substring(5) : key;
            match.teamScores.put(code, 0);
            match.teamColorIdx.put(code, colorIdx);
            // Смотрим в случайную сторону: по позе нельзя угадать, где центр и чужие базы
            float yaw = random.nextFloat() * 360f;
            match.anchors.put(key, MatchData.Drop.at(spot[0], match.centerY, spot[1], yaw));
            List<UUID> members = new ArrayList<>();
            for (ServerPlayer player : groups.get(key)) {
                members.add(player.getUUID());
                match.playerCrew.put(player.getUUID(), code);
                match.playerColorIdx.put(player.getUUID(), colorIdx);
                // Все переходят в выживание сразу, даже если пришли из креатива или наблюдателей
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setForcedPose(null);
                player.setPose(Pose.STANDING);
                player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.started_you", teamLabel(server, code), gap)));
                if (Config.PVP_FREE_SECONDS.get() > 0) {
                    player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.pvp_free", Config.PVP_FREE_SECONDS.get())));
                }
            }
            // Центр базы держим в середине чанка: постановка маяка не заденет соседние чанки
            int bx = (spot[0] & ~15) + 5;
            int bz = (spot[1] & ~15) + 5;
            DEPLOY.add(new PendingBase(code, bx, bz, yaw, members));
        }
        applyKeep(level, match, true);
        long startTick = level.getGameTime();
        match.matchStartTick = startTick;
        match.playersLeaked = false;
        match.beaconsLeaked = false;
        match.pvpFreeUntil = startTick + Config.PVP_FREE_SECONDS.get() * 20L;
        match.nextAirdropTick = Config.AIRDROP_ENABLED.get()
                ? startTick + Config.AIRDROP_FIRST_MINUTE.get() * 1200L
                : 0L;
        long matchDuration = Config.MATCH_DURATION_MINUTES.get() * 60L * 20L;
        long finalDuration = Config.FINAL_DURATION_MINUTES.get() * 60L * 20L;
        match.matchEndTick = startTick + matchDuration;
        match.finalEndTick = match.matchEndTick + finalDuration;
        match.finalActive = false;
        match.ended = false;
        match.active = true;
        match.setDirty();
        applyTeamNametags(server, match);
        initScoreboard(server, match);
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.started", groups.size(), gap)), true);
        if (cramped) source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.cramped", gap)), true);
        announceMatchStart(server, match);
        prepareCaches(level, match);
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.caches_prep", match.cacheTotal)), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        MatchData match = MatchData.get(source.getServer());
        if (!match.active) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.not_running")));
            return 0;
        }
        ServerLevel level = Ops.levelOf(source.getServer(), match.dim);
        match.active = false;
        match.pvpFreeUntil = 0;
        match.spread = 0;
        DEPLOY.clear();
        resetChunkWait();
        PVP_WARN.clear();
        ALLY_WARN.clear();
        borderPending = false;
        nextDeployTick = 0;
        nextCacheTick = 0;
        LOOT_QUEUE.clear();
        match.matchEndTick = 0;
        match.finalEndTick = 0;
        match.nextAirdropTick = 0;
        match.finalActive = false;
        match.drops.clear();
        match.anchors.clear();
        match.kitted.clear();
        match.teamScores.clear();
        match.bedPositions.clear();
        match.downedPlayers.clear();
        match.reviveUsed.clear();
        match.permanentDead.clear();
        match.playerCrew.clear();
        match.playerColorIdx.clear();
        match.eliminatedTeams.clear();
        PROTECT.clear();
        REVIVE.clear();
        LAST_HIT.clear();
        // Маяки убираем, пока карта teamBeacons ещё не очищена
        if (level != null) {
            removeBeacons(level, match);
            removeCaches(level, match);
            restoreBorder(level, match);
            restoreKeep(level, match);
        }
        match.teamBeacons.clear();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            player.setForcedPose(null);
            player.setPose(Pose.STANDING);
            player.noPhysics = false;
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        removeTeamNametags(source.getServer());
        clearScoreboard(source.getServer());
        match.ended = false;
        match.setDirty();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.stopped")));
        }
        source.sendSuccess(() -> Msg.paint(Component.translatable("blackchannel.minigame.stopped")), true);
        return 1;
    }

    private static void applyTeamNametags(MinecraftServer server, MatchData match) {
        removeTeamNametags(server);
        Scoreboard scoreboard = server.overworld().getScoreboard();
        Map<String, PlayerTeam> teams = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String crew = match.playerCrew.get(player.getUUID());
            if (crew == null || crew.isEmpty()) continue;
            PlayerTeam team = teams.get(crew);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamTag(crew));
                team.setNameTagVisibility(PlayerTeam.Visibility.ALWAYS);
                team.setColor(scoreColor(match, crew));
                // Ванильная же защита от дружественного огня внутри команды
                team.setAllowFriendlyFire(false);
                teams.put(crew, team);
            }
            scoreboard.addPlayerToTeam(player.getGameProfile().getName(), team);
        }
    }

    private static String teamTag(String crew) {
        StringBuilder sb = new StringBuilder("bc_");
        for (int i = 0; i < crew.length() && sb.length() < 12; i++) {
            char c = crew.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') sb.append(Character.toLowerCase(c));
        }
        sb.append('_').append(Math.abs(crew.hashCode()) % 97);
        return sb.toString();
    }

    private static void removeTeamNametags(MinecraftServer server) {
        if (server == null) return;
        Scoreboard scoreboard = server.overworld().getScoreboard();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTeam team = scoreboard.getPlayersTeam(player.getGameProfile().getName());
            if (team != null && team.getName().startsWith("bc_")) {
                scoreboard.removePlayerFromTeam(player.getGameProfile().getName(), team);
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    private static void announceMatchStart(MinecraftServer server, MatchData match) {
        int minutes = Config.MATCH_DURATION_MINUTES.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.match_start", minutes)));
        }
    }

    private static void assign(ServerPlayer player, MatchData match, boolean teleport) {
        ServerLevel level = Ops.levelOf(player.getServer(), match.dim);
        if (level == null) return;
        String code = NetData.get(player).profile(player.getUUID()).crew;
        String key = code == null || code.isEmpty() ? "solo:" + player.getUUID() : "crew:" + code;
        MatchData.Drop anchor = match.anchors.get(key);
        if (anchor == null) {
            int[] spot = freeAnchor(level, match);
            anchor = MatchData.Drop.at(spot[0], match.centerY, spot[1], player.getRandom().nextFloat() * 360f);
            match.anchors.put(key, anchor);
        }
        float yaw = anchor.yaw;
        BlockPos feet = scatter(level, anchor.x, anchor.z, player.getRandom());
        match.drops.put(player.getUUID(), MatchData.Drop.at(feet.getX(), feet.getY(), feet.getZ(), yaw));
        if (teleport) place(player, level, feet, yaw);
        match.setDirty();
    }

    private static void kit(ServerPlayer player, MatchData match) {
        if (match.kitted.containsKey(player.getUUID())) return;
        // Чистый старт: пустой инвентарь, броня, эффекты и опыт
        player.getInventory().clearContent();
        player.getInventory().setChanged();
        player.removeAllEffects();
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.containerMenu.broadcastChanges();
        if (!Devices.has(player, ModContent.PHONE.get())) {
            ItemStack phone = new ItemStack(ModContent.PHONE.get());
            phone.getOrCreateTag().putInt("Battery", 100);
            give(player, phone);
        }
        give(player, new ItemStack(ModContent.CELL.get()));
        give(player, new ItemStack(Items.RED_BED));
        give(player, new ItemStack(Items.STONE_SHOVEL));
        give(player, new ItemStack(Items.STONE_HOE));
        give(player, new ItemStack(Items.STONE_AXE));
        give(player, new ItemStack(Items.IRON_PICKAXE));
        give(player, new ItemStack(Items.BREAD, 2));
        match.kitted.put(player.getUUID(), true);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void place(ServerPlayer player, ServerLevel level, BlockPos feet, float yaw) {
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        protect(player, level.getGameTime() + 160L);
    }

    private static void protect(ServerPlayer player, long until) {
        PROTECT.put(player.getUUID(), until);
    }

    private static boolean shielded(ServerPlayer player) {
        Long until = PROTECT.get(player.getUUID());
        if (until == null) return false;
        if (player.serverLevel().getGameTime() >= until) {
            PROTECT.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** Игрок лёг в кровать — она становится его точкой возрождения. */
    public static void onSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) return;
        if (!match.inside(event.getPos())) return;
        match.setBed(player.getUUID(), event.getPos());
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.minigame.bed_placed")));
        match.setDirty();
    }

    /** Сколько отрядов (и соло) сейчас в игре: от этого зависит минимальное расстояние между базами. */
    private static int teamsOnline(MinecraftServer server) {
        Set<String> crews = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String code = NetData.get(server).profile(player.getUUID()).crew;
            crews.add(code == null || code.isEmpty() ? "solo:" + player.getUUID() : code);
        }
        return Math.max(2, crews.size());
    }

    /** Готовый план расстановки: точки баз и фактическое минимальное расстояние между ними. */
    private record Layout(List<int[]> spots, int distance, boolean relaxed) {}

    /** Отступ от барьера: 10% стороны карты. */
    private static int safeInset(MatchData match) {
        return Math.max(16, (int) Math.round(0.10 * Math.max(2, match.size)));
    }

    /** Запретная зона в центре карты: 15% стороны. */
    private static int coreRadius(MatchData match) {
        return Math.max(16, (int) Math.round(0.15 * Math.max(2, match.size)));
    }

    /**
     * Минимально допустимое расстояние между базами:
     * d = max(0.08N, min(0.45N, 0.60N / sqrt(K))).
     */
    private static int minSpread(MatchData match, int teams) {
        int n = Math.max(2, match.size);
        int k = Math.max(1, teams);
        double d = 0.60 * n / Math.sqrt(k);
        d = Math.min(0.45 * n, d);
        d = Math.max(0.08 * n, d);
        return (int) Math.round(d);
    }

    /**
     * Расстановка баз: случайные точки в безопасной области без запретной середины,
     * между любыми двумя базами — не меньше d (выборка по типу Poisson-disc).
     * Если впихнуть все команды с нужным d не выходит, d понемногу ослабляется.
     */
    private static Layout layout(MatchData match, int teams, RandomSource random) {
        if (teams <= 1) {
            List<int[]> single = new ArrayList<>();
            single.add(new int[]{match.centerX, match.centerZ});
            return new Layout(single, minSpread(match, 2), false);
        }
        int inset = safeInset(match);
        int core = coreRadius(match);
        int ideal = minSpread(match, teams);
        int floor = Math.max(32, (int) Math.round(0.04 * Math.max(2, match.size)));
        int d = ideal;
        for (int step = 0; step < 20 && d >= floor; step++) {
            List<int[]> spots = poisson(match, teams, d, inset, core, random);
            if (spots != null) return new Layout(spots, spotsMin(spots), d < ideal);
            d = (int) Math.round(d * 0.95);
        }
        // Даже ослабленный минимум не влез: раскладываем по кольцу и честно сообщаем админу
        List<int[]> ring = ring(match, teams, inset, core, random);
        return new Layout(ring, spotsMin(ring), true);
    }

    /** Фактическое минимальное расстояние между точками раскладки. */
    private static int spotsMin(List<int[]> spots) {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            for (int j = i + 1; j < spots.size(); j++) {
                long dx = spots.get(i)[0] - spots.get(j)[0];
                long dz = spots.get(i)[1] - spots.get(j)[1];
                best = Math.min(best, (int) Math.round(Math.sqrt((double) (dx * dx + dz * dz))));
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    /** Случайная выборка точек с гарантией минимального расстояния между ними. */
    private static List<int[]> poisson(MatchData match, int teams, int d, int inset, int core, RandomSource random) {
        int half = Math.max(8, match.size / 2 - inset);
        long need = (long) d * d;
        long coreSq = (long) core * core;
        List<int[]> spots = new ArrayList<>(teams);
        int limit = teams * 3000 + 4000;
        for (int attempt = 0; attempt < limit && spots.size() < teams; attempt++) {
            int x = match.centerX + random.nextInt(half * 2 + 1) - half;
            int z = match.centerZ + random.nextInt(half * 2 + 1) - half;
            long dxc = x - match.centerX;
            long dzc = z - match.centerZ;
            if (dxc * dxc + dzc * dzc < coreSq) continue;
            boolean clash = false;
            for (int[] other : spots) {
                long dx = other[0] - x;
                long dz = other[1] - z;
                if (dx * dx + dz * dz < need) {
                    clash = true;
                    break;
                }
            }
            if (clash) continue;
            spots.add(new int[]{x, z});
        }
        return spots.size() == teams ? spots : null;
    }

    /** Крайний случай: кольцо с случайным поворотом. */
    private static List<int[]> ring(MatchData match, int teams, int inset, int core, RandomSource random) {
        List<int[]> spots = new ArrayList<>(teams);
        double radius = Math.max(core + 16, match.size / 2.0 - inset - 0.05 * match.size);
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < teams; i++) {
            double ang = start + 2 * Math.PI * i / teams;
            int x = match.centerX + (int) Math.round(Math.cos(ang) * radius);
            int z = match.centerZ + (int) Math.round(Math.sin(ang) * radius);
            spots.add(clamp(match, x, z, inset));
        }
        return spots;
    }

    /** База для команды, пришедшей уже по ходу матча. */
    private static int[] freeAnchor(ServerLevel level, MatchData match) {
        int gap = Math.max(48, match.spread > 0 ? match.spread : match.baseGap());
        int inset = safeInset(match);
        int core = coreRadius(match);
        RandomSource random = level.getRandom();
        int half = Math.max(8, match.size / 2 - inset);
        for (int attempt = 0; attempt < 48; attempt++) {
            int x = match.centerX + random.nextInt(half * 2 + 1) - half;
            int z = match.centerZ + random.nextInt(half * 2 + 1) - half;
            long dxc = x - match.centerX;
            long dzc = z - match.centerZ;
            if (dxc * dxc + dzc * dzc < (long) core * core) continue;
            if (crowded(match, x, z, gap)) continue;
            return new int[]{x, z};
        }
        return clamp(match, match.centerX, match.centerZ, inset);
    }

    private static boolean crowded(MatchData match, int x, int z, int gap) {
        long need = (long) gap * gap;
        for (MatchData.Drop anchor : match.anchors.values()) {
            long dx = anchor.x - x;
            long dz = anchor.z - z;
            if (dx * dx + dz * dz < need) return true;
        }
        return false;
    }

    private static int[] clamp(MatchData match, int x, int z, int edge) {
        int half = Math.max(0, match.size / 2 - edge);
        return new int[]{
                Mth.clamp(x, match.centerX - half, match.centerX + half),
                Mth.clamp(z, match.centerZ - half, match.centerZ + half)
        };
    }

    private static BlockPos scatter(ServerLevel level, int x, int z, RandomSource random) {
        int dx = random.nextInt(17) - 8;
        int dz = random.nextInt(17) - 8;
        if (dx * dx + dz * dz < 16) {
            dx = 4 + random.nextInt(8);
            dz = random.nextInt(9) - 4;
        }
        return findSafe(level, x + dx, z + dz, random);
    }

    private static BlockPos findSafe(ServerLevel level, int x, int z, RandomSource random) {
        // Ищем только по уже загруженным чанкам: генерация нового чанка — самая тяжёлая операция на сервере
        for (int attempt = 0; attempt < 12; attempt++) {
            int sx = x + (attempt == 0 ? 0 : random.nextInt(25) - 12);
            int sz = z + (attempt == 0 ? 0 : random.nextInt(25) - 12);
            if (level.getChunkSource().getChunkNow(sx >> 4, sz >> 4) == null) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            BlockPos feet = new BlockPos(sx, y, sz);
            if (standable(level, feet)) return feet;
        }
        // Точка нужна прямо сейчас: один блокирующий запрос, без перебора и каскада генераций
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, Math.max(level.getMinBuildHeight() + 1, y), z);
    }

    /** То же, но только по загруженным чанкам: не провоцирует синхронную генерацию соседей. */
    private static boolean standableHere(ServerLevel level, BlockPos feet) {
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        if (level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) == null) return false;
        if (level.getChunkSource().getChunkNow(ground.getX() >> 4, ground.getZ() >> 4) == null) return false;
        if (level.getChunkSource().getChunkNow(head.getX() >> 4, head.getZ() >> 4) == null) return false;
        return standable(level, feet);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(ground).isEmpty()) return false;
        if (level.getBlockState(ground).is(Blocks.LAVA) || level.getBlockState(ground).is(Blocks.MAGMA_BLOCK)) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    private static void applyBorder(ServerLevel level, MatchData match, boolean capture) {
        WorldBorder border = level.getWorldBorder();
        if (capture && !match.borderSaved) {
            match.prevSize = border.getSize();
            match.prevCenterX = border.getCenterX();
            match.prevCenterZ = border.getCenterZ();
            match.borderSaved = true;
        }
        border.setCenter(match.centerX + 0.5, match.centerZ + 0.5);
        border.setSize(match.size);
        border.lerpSizeBetween(match.size, match.size, 0L);
        border.setWarningBlocks(16);
        border.setDamagePerBlock(0.2);
        border.setDamageSafeZone(0);
    }

    private static void restoreBorder(ServerLevel level, MatchData match) {
        if (!match.borderSaved) return;
        WorldBorder border = level.getWorldBorder();
        border.setCenter(match.prevCenterX, match.prevCenterZ);
        border.setSize(match.prevSize);
        border.lerpSizeBetween(match.prevSize, match.prevSize, 0L);
        match.borderSaved = false;
    }

    private static void applyKeep(ServerLevel level, MatchData match, boolean capture) {
        GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        if (capture && !match.keepSaved) {
            match.prevKeepInv = rule.get();
            match.keepSaved = true;
        }
        rule.set(false, level.getServer());
    }

    private static void restoreKeep(ServerLevel level, MatchData match) {
        if (!match.keepSaved) return;
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(match.prevKeepInv, level.getServer());
        match.keepSaved = false;
    }

    /**
     * Сколько схронов ставить. Эталон: карта 1000×1000 и 4 отряда — 14 схронов.
     * Линейно по числу отрядов (за каждым отрядом стоят игроки) и по стороне карты:
     * 2 отряда на 1000 — 7 (поднимаем до минимума 8), 8 отрядов на 1000 — 28,
     * 4 отряда на 2500 — 35. Крайние значения — от 8 до 56 схронов.
     */
    public static int cacheCount(MatchData match, int teams) {
        int side = Math.max(2, match.size);
        double raw = CACHE_PER_TEAM * Math.max(1, teams) * side / 1000.0;
        return Mth.clamp((int) Math.round(raw), CACHE_MIN, CACHE_MAX);
    }

    private static void prepareCaches(ServerLevel level, MatchData match) {
        int count = cacheCount(match, Math.max(1, match.teamScores.size()));
        int avoid = Math.min(150, Math.max(32, match.size / 8));
        RandomSource random = level.getRandom();
        int half = Math.max(8, match.size / 2 - 8);
        List<BlockPos> positions = new ArrayList<>();
        int guard = 0;
        // Только арифметика: чанки грузим потом, по одному за тик
        while (positions.size() < count && guard++ < count * 16) {
            int x = match.centerX + random.nextInt(half * 2 + 1) - half;
            int z = match.centerZ + random.nextInt(half * 2 + 1) - half;
            if (crowded(match, x, z, avoid)) continue;
            // Ставим точку в середину своего чанка: иначе проверка земли дёргает соседние чанки
            x = (x & ~15) + 2 + random.nextInt(12);
            z = (z & ~15) + 2 + random.nextInt(12);
            positions.add(new BlockPos(x, match.centerY, z));
        }
        match.cachePositions = positions.toArray(new BlockPos[0]);
        match.cacheTotal = positions.size();
        match.cacheTick = 0;
    }

    /**
     * /minigame cache: поставить случайный схрон рядом с игроком.
     * В отличие от схронов катки сундук стоит открыто на земле: не надо копать, чтобы проверить.
     */
    private static int spawnCache(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.need_player")));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        MatchData match = MatchData.get(source.getServer());
        RandomSource random = level.getRandom();
        BlockPos spot = nearbyChestSpot(level, player, random);
        if (spot == null) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.cache_no_room")));
            return 0;
        }
        // Сундук развёрнут к игроку, чтобы сразу было видно, что он не декоративный
        Direction facing = Direction.getNearest(
                player.getX() - (spot.getX() + 0.5D), 0.0D, player.getZ() - (spot.getZ() + 0.5D));
        level.setBlock(spot, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
        if (!(level.getBlockEntity(spot) instanceof ChestBlockEntity chest)) {
            source.sendFailure(Msg.paint(Component.translatable("blackchannel.minigame.cache_no_room")));
            return 0;
        }
        fill(chest, random, wavePool(match, level.getGameTime()));
        chest.setCustomName(Component.translatable("block.blackchannel.field_cache"));
        chest.getPersistentData().putBoolean("bcLoot", true);
        chest.setChanged();
        // Внутри катки схрон попадёт в общую уборку, вне катки останется в мире
        boolean tracked = match.active && Str.dim(level).equals(match.dim);
        if (tracked) {
            match.cacheChests.add(spot.immutable());
            match.setDirty();
        }
        source.sendSuccess(() -> Msg.paint(tracked
                ? Component.translatable("blackchannel.minigame.cache_ok", spot.getX(), spot.getY(), spot.getZ())
                : Component.translatable("blackchannel.minigame.cache_ok_free", spot.getX(), spot.getY(), spot.getZ())), true);
        return 1;
    }

    /** Ближайшее кольцо вокруг игрока, в котором помещается открытый сундук; точка берётся случайная. */
    private static BlockPos nearbyChestSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos base = player.blockPosition();
        for (int r = 2; r <= 6; r++) {
            List<BlockPos> spots = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Только край кольца: внутренние точки уже проверены на меньшем радиусе
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos pos = new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                        if (chestFits(level, pos)) spots.add(pos.immutable());
                    }
                }
            }
            if (!spots.isEmpty()) return spots.get(random.nextInt(spots.size()));
        }
        return null;
    }

    /** Место свободно, под ним твёрдая земля и нет воды или лавы. */
    private static boolean chestFits(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;
        if (!level.getFluidState(pos).isEmpty()) return false;
        BlockPos ground = pos.below();
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) return false;
        return level.getFluidState(ground).isEmpty();
    }

    private static boolean placeChest(ServerLevel level, MatchData match, BlockPos feet, RandomSource random) {
        // Сундук под слоем каменистой земли
        BlockPos chestPos = feet.below();
        Direction facing = Direction.from2DDataValue(random.nextInt(4));
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
        level.setBlock(feet, Blocks.COARSE_DIRT.defaultBlockState(), 3);
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) return false;
        fill(chest, random, wavePool(match, level.getGameTime()));
        chest.setCustomName(Component.translatable("block.blackchannel.field_cache"));
        chest.getPersistentData().putBoolean("bcLoot", true);
        chest.setChanged();
        // Запоминаем свой схрон: после матча его надо убрать из мира
        match.cacheChests.add(chestPos.immutable());
        match.setDirty();
        return true;
    }

    /**
     * Лут кладётся в любой сундук или бочку в зоне матча, а не только в данжах.
     * Сгенерированный таблицей лут заменяется нашим, из игроцкого сундука ничего не выкидываем.
     */
    private static void injectLoot(ServerLevel level, MatchData match, RandomizableContainerBlockEntity box) {
        if (box.getPersistentData().getBoolean("bcLoot")) return;
        if (!match.inside(box.getBlockPos())) return;
        RandomSource random = level.getRandom();
        List<ItemStack> stacks = rollLoot(Config.CACHE_DUNGEON.get(), 3 + random.nextInt(3), random);
        if (stacks.isEmpty()) return;
        CompoundTag saved = box.saveWithoutMetadata();
        // 1.20.1 не отдаёт id таблицы методом. Он лежит в теге, пока сундук не открыли.
        boolean generated = saved.contains("LootTable", 8);
        if (generated) {
            box.unpackLootTable(null);
            // Ванильный лут заменяется, иначе наши вещи просто дописываются в пустые слоты
            box.clearContent();
        }
        addStacks(box, stacks);
        box.getPersistentData().putBoolean("bcLoot", true);
        box.setChanged();
    }

    /** Один предмет пула: сколько выдать и с каким весом он попадается. */
    private record LootEntry(Item item, int min, int max, int weight) {}

    /** Пул по волне матча: первая треть — ранняя, вторая — средняя, дальше поздняя. */
    private static List<? extends String> wavePool(MatchData match, long now) {
        if (!match.active || match.matchEndTick <= match.matchStartTick) return Config.CACHE_MID.get();
        long length = match.matchEndTick - match.matchStartTick;
        long elapsed = Math.max(0L, now - match.matchStartTick);
        if (elapsed * 3L < length) return Config.CACHE_EARLY.get();
        if (elapsed * 3L < length * 2L) return Config.CACHE_MID.get();
        return Config.CACHE_LATE.get();
    }

    /** Разбор строки пула: id, id*count, id*min-max и вес через собаку. */
    private static LootEntry entryOf(String raw) {
        if (raw == null) return null;
        String spec = raw.trim();
        if (spec.isEmpty()) return null;
        int weight = 10;
        int at = spec.lastIndexOf('@');
        if (at >= 0) {
            try {
                weight = Math.max(1, Integer.parseInt(spec.substring(at + 1).trim()));
            } catch (NumberFormatException e) {
                return null;
            }
            spec = spec.substring(0, at).trim();
        }
        int min = 1;
        int max = 1;
        int star = spec.indexOf('*');
        if (star >= 0) {
            String num = spec.substring(star + 1).trim();
            spec = spec.substring(0, star).trim();
            try {
                int dash = num.indexOf('-');
                if (dash >= 0) {
                    min = Integer.parseInt(num.substring(0, dash).trim());
                    max = Integer.parseInt(num.substring(dash + 1).trim());
                } else {
                    min = Integer.parseInt(num);
                    max = min;
                }
            } catch (NumberFormatException e) {
                return null;
            }
            if (max < min) {
                int swap = min;
                min = max;
                max = swap;
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return null;
        return new LootEntry(item, Math.max(1, Math.min(64, min)), Math.max(1, Math.min(64, max)), weight);
    }

    /** Случайная выборка по весам без повторов: чем больше вес строки, тем чаще предмет. */
    private static List<ItemStack> rollLoot(List<? extends String> lines, int count, RandomSource random) {
        List<LootEntry> pool = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                LootEntry entry = entryOf(line);
                if (entry != null) pool.add(entry);
            }
        }
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int total = 0;
            for (LootEntry entry : pool) total += entry.weight();
            int pick = random.nextInt(Math.max(1, total));
            int idx = 0;
            for (int j = 0; j < pool.size(); j++) {
                pick -= pool.get(j).weight();
                if (pick < 0) {
                    idx = j;
                    break;
                }
            }
            LootEntry chosen = pool.remove(idx);
            int n = chosen.min() == chosen.max()
                    ? chosen.min()
                    : chosen.min() + random.nextInt(chosen.max() - chosen.min() + 1);
            out.add(new ItemStack(chosen.item(), Math.max(1, Math.min(64, n))));
        }
        return out;
    }

    private static void fill(ChestBlockEntity chest, RandomSource random, List<? extends String> pool) {
        int min = Math.max(1, Config.CACHE_ITEMS_MIN.get());
        int max = Math.max(min, Config.CACHE_ITEMS_MAX.get());
        int count = min == max ? min : min + random.nextInt(max - min + 1);
        addStacks(chest, rollLoot(pool, count, random));
    }

    private static void addStacks(RandomizableContainerBlockEntity box, List<ItemStack> stacks) {
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            while (slot < box.getContainerSize() && !box.getItem(slot).isEmpty()) slot++;
            if (slot >= box.getContainerSize()) return;
            box.setItem(slot, stack);
            slot++;
        }
    }

    private static int logOne(ServerLevel level, BlockPos pos, UUID owner, int charges, List<UUID> seen, ServerPlayer player, boolean consume) {
        if (player.getUUID().equals(owner) || seen.contains(player.getUUID())) return charges;
        if (consume && charges <= 0) return charges;
        NetData data = NetData.get(level.getServer());
        NetData.Profile self = data.profile(owner);
        NetData.Profile other = data.profile(player.getUUID());
        if (other.lastName == null || other.lastName.isEmpty()) other.lastName = player.getGameProfile().getName();
        if (self.crew != null && !self.crew.isEmpty() && self.crew.equals(other.crew)) return charges;
        seen.add(player.getUUID());
        int left = consume ? charges - 1 : charges;
        String shown = other.vpn > 0 ? "" : other.lastName;
        boolean fresh = Ops.noteHeard(self, other.handle, shown, false);
        data.setDirty();
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (fresh && ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Msg.paint(Component.translatable(shown.isEmpty() ? "blackchannel.result.net_handle" : "blackchannel.result.net_named", other.handle, shown)));
            ModNetwork.syncFull(ownerPlayer);
        }
        if (other.ids) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.chat.ids_logger", bearing(player, pos))));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.8f, 0.6f);
        }
        return left;
    }

    private static String bearing(ServerPlayer from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.getX();
        double dz = to.getZ() + 0.5 - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    private static int chargesOf(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("Charges")) return Mth.clamp(stack.getTag().getInt("Charges"), 0, 3);
        return 3;
    }

    private static boolean op(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    public static String selfStatusFor(ServerPlayer player, MatchData match) {
        if (!match.active) return "";
        if (match.permanentDead.contains(player.getUUID())) return "dead";
        if (match.downedPlayers.containsKey(player.getUUID())) return "downed";
        if (match.finalActive) return "final";
        return "alive";
    }

    public static int selfDownedSeconds(ServerPlayer player, MatchData match) {
        MatchData.DownedState ds = match.getDowned(player.getUUID());
        if (ds == null) return 0;
        long elapsed = player.level().getGameTime() - ds.downedAtTick;
        int duration = Config.DOWNED_DURATION_TICKS.get();
        int left = (int) Math.max(0, (duration - elapsed) / 20L);
        return left;
    }
}
