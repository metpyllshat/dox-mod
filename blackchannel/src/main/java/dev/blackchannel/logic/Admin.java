package dev.blackchannel.logic;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.api.BlackChannelAPI;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Кнопки админ-панели: всё, что нужно, чтобы за минуту проверить механику и не
 * бегать по командам. Панель шлёт строки вида {@code admin.<действие>} тем же
 * пакетом, что и телефон, а сервер здесь проверяет права и делает работу.
 * <p>
 * Управление матчем идёт через обычные команды: одна логика, один источник правды.
 */
public final class Admin {
    /** Кому можно: второй уровень прав, как у команд с пометкой оператора. */
    public static final int LEVEL = 2;

    private Admin() {}

    public static boolean allowed(ServerPlayer player) {
        return player != null && player.hasPermissions(LEVEL);
    }

    public static void handle(ServerPlayer player, String action, String arg) {
        if (!allowed(player)) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.denied")));
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        switch (action) {
            case "admin.center" -> command(player, "minigame center");
            case "admin.size" -> command(player, "minigame size " + clampSize(arg));
            case "admin.start" -> command(player, "minigame start");
            case "admin.stop" -> command(player, "minigame stop");
            case "admin.final" -> finalNow(player, server);
            case "admin.kit" -> kit(player);
            case "admin.coins" -> coins(player, 500);
            case "admin.noise" -> noise(player);
            case "admin.flood" -> ModNetwork.sendFlood(player);
            case "admin.emp" -> BlackChannelAPI.pulse(player.serverLevel(), player.blockPosition(), 24, 12);
            case "admin.reset" -> reset(player, server);
            case "admin.handle" -> newHandle(player, server);
            case "admin.tpbase" -> toBase(player, server);
            case "admin.tpcenter" -> toCenter(player, server);
            case "admin.team" -> team(player, arg);
            case "admin.status" -> status(player, server);
            case "admin.teleport0" -> recall(player);
            default -> { }
        }
        NetData.get(server).setDirty();
        ModNetwork.syncFull(player);
    }

    /** Размер карты из строки: мусор в поле ввода не должен ломать команду. */
    private static int clampSize(String arg) {
        try {
            return Math.max(256, Math.min(4000, Integer.parseInt(arg.trim())));
        } catch (Exception e) {
            return 1000;
        }
    }

    private static void command(ServerPlayer player, String line) {
        try {
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), line);
        } catch (Exception e) {
            BlackChannelMod.LOGGER.warn("BlackChannel: админ-команда '{}' не выполнилась: {}", line, e.toString());
        }
    }

    /** Финал через минуту: проверяем боссбар, звуки и закрытие матча без ожидания. */
    private static void finalNow(ServerPlayer player, MinecraftServer server) {
        MatchData match = MatchData.get(server);
        if (!match.active) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.no_match")));
            return;
        }
        long now = server.getTickCount();
        match.matchEndTick = now + 60L * 20L;
        match.finalEndTick = match.matchEndTick + 60L * 20L;
        match.setDirty();
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.final_set")));
    }

    /** Набор для теста: по одному предмету каждого типа, чтобы щёлкать механики подряд. */
    private static void kit(ServerPlayer player) {
        give(player, ModContent.PHONE.get());
        give(player, ModContent.LAPTOP.get());
        give(player, ModContent.CELL.get());
        give(player, ModContent.SIM.get());
        give(player, ModContent.RACK_ITEM.get());
        give(player, ModContent.DISH_ITEM.get());
        give(player, ModContent.JAMMER_ITEM.get());
        give(player, ModContent.FARADAY_ITEM.get());
        give(player, ModContent.DECOY_ITEM.get());
        give(player, ModContent.VPN.get());
        give(player, ModContent.IDS.get());
        give(player, ModContent.HARDEN.get());
        give(player, ModContent.EXPLOIT.get());
        give(player, ModContent.ZERO.get());
        give(player, ModContent.CONTRACT.get());
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        profile.crypto = Math.max(profile.crypto, 500);
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.kit")));
    }

    private static void give(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void coins(ServerPlayer player, int amount) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        profile.crypto = Math.min(5000, profile.crypto + amount);
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.coins", amount, profile.crypto)));
    }

    /** Максимальный шум: на телефоне должна загореться вся полоса, луч сузиться. */
    private static void noise(ServerPlayer player) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        profile.noise = 100;
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.noise")));
    }

    private static void reset(ServerPlayer player, MinecraftServer server) {
        NetData netData = NetData.get(server);
        NetData.Profile profile = netData.profile(player.getUUID());
        profile.crypto = 0;
        profile.noise = 0;
        profile.vpn = 0;
        profile.ids = false;
        profile.hard = false;
        profile.airplane = false;
        profile.dnd = false;
        profile.intel.clear();
        profile.beams.clear();
        profile.heard.clear();
        profile.cdTrace = 0;
        profile.cdBomb = 0;
        profile.lastDim = "";
        netData.setDirty();
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.reset")));
    }

    private static void newHandle(ServerPlayer player, MinecraftServer server) {
        NetData netData = NetData.get(server);
        NetData.Profile profile = netData.profile(player.getUUID());
        profile.handle = netData.uniqueHandle(player.getRandom());
        netData.setDirty();
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.handle", profile.handle)));
    }

    private static void toBase(ServerPlayer player, MinecraftServer server) {
        MatchData match = MatchData.get(server);
        String crew = match.playerCrew.get(player.getUUID());
        net.minecraft.core.BlockPos beacon = crew == null ? null : match.teamBeacons.get(crew);
        if (beacon == null) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.no_base")));
            return;
        }
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null) return;
        player.teleportTo(level, beacon.getX() + 0.5, beacon.getY() + 1, beacon.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    private static void toCenter(ServerPlayer player, MinecraftServer server) {
        MatchData match = MatchData.get(server);
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null || !match.centerSet) return;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                match.centerX, match.centerZ);
        player.teleportTo(level, match.centerX + 0.5, y + 1, match.centerZ + 0.5, player.getYRot(), player.getXRot());
    }

    private static void recall(ServerPlayer player) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        if (profile.lastDim == null || profile.lastDim.isEmpty()) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.no_trace")));
            return;
        }
        command(player, "execute in " + profile.lastDim + " run tp @s " + profile.lastX + " " + profile.lastY + " " + profile.lastZ);
    }

    private static void team(ServerPlayer player, String arg) {
        String code = arg == null ? "" : arg.trim();
        if (!Teams.valid(code)) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.bad_team")));
            return;
        }
        Minigame.setTeam(player, code);
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.admin.team", Teams.nameOf(code))));
    }

    /** Сводка для панели: то же, что видно в панели, но текстом в чат (для лога). */
    private static void status(ServerPlayer player, MinecraftServer server) {
        MatchData match = MatchData.get(server);
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        Map<String, Integer> scores = new LinkedHashMap<>(match.teamScores);
        StringBuilder sb = new StringBuilder();
        sb.append(match.active ? "матч идёт" : "матч не запущен");
        if (match.active) {
            int left = Math.max(0, (int) ((match.matchEndTick - server.getTickCount()) / 20L));
            sb.append(", до конца ").append(left / 60).append(":").append(String.format("%02d", left % 60));
        }
        sb.append(", центр ").append(match.centerX).append(" ").append(match.centerZ).append(" (").append(match.size).append(")");
        sb.append(", очки ").append(scores);
        sb.append(", позывной ").append(profile.handle).append(", шум ").append(profile.noise);
        player.sendSystemMessage(Msg.paint(Component.literal(sb.toString())));
    }
}
