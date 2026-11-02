package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.Contract;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Контракты отрядов. У каждого отряда до трёх открытых заданий, новые приходят
 * сами. Задания — пять типов, и все они завязаны на то, что игроки и так делают:
 * <ul>
 *   <li>TRACE — три пеленга с результатом;</li>
 *   <li>HACK — взлом стойки другого отряда;</li>
 *   <li>TOWER — захват вышки;</li>
 *   <li>KILL — два убийства врагов;</li>
 *   <li>DELIVER — сдать отряду предметы из инвентаря (проверяется, что они у отряда есть).</li>
 * </ul>
 * Отчаянный контракт получает самый отстающий отряд, один раз за катку, награда вдвое.
 * Срок задания — contractMinutes, без выполнения сгорает без награды.
 */
public final class Contracts {
    /** Открытых заданий на отряд. */
    public static final int SLOTS = 3;
    /** Потолок крипты на кошельке, как у остальных начислений. */
    private static final int CRYPTO_CAP = 5000;
    private static final String[] DELIVER_ITEMS = {"exploit_firmware", "zero_day", "false_contract", "signal_sample"};

    private Contracts() {}

    // ---------- отображение (общее для сервера и клиента) ----------

    /** Текст задания: клиент собирает его тем же методом, что и сервер. */
    public static Component titleOf(String kind, int target, String item) {
        return switch (kind) {
            case Contract.TRACE -> Component.translatable("blackchannel.contract.title.trace", target);
            case Contract.HACK -> Component.translatable("blackchannel.contract.title.hack");
            case Contract.TOWER -> Component.translatable("blackchannel.contract.title.tower");
            case Contract.KILL -> Component.translatable("blackchannel.contract.title.kill", target);
            case Contract.DELIVER -> Component.translatable("blackchannel.contract.title.deliver",
                    Component.translatable("item.blackchannel." + item), target);
            default -> Component.empty();
        };
    }

    // ---------- жизненный цикл ----------

    /** Раз в секунду: сгорают просроченные, отряды добирают задания до трёх. */
    public static void tick(MinecraftServer server, MatchData match, long now) {
        if (!match.active || match.finalActive) return;
        expire(server, match, now);
        Map<String, Integer> lowest = lowestCrews(match);
        for (String crew : crews(match)) {
            boolean weakest = lowest.containsKey(crew) && !hadDesperate(match, crew);
            refill(server, match, crew, now, weakest);
        }
        match.contracts.removeIf(c -> !c.open(now) && !c.desperate);
        match.setDirty();
    }

    /** Старт новой катки: прошлые задания забываем. */
    public static void reset(MatchData match) {
        match.contracts.clear();
        match.contractSeq = 1;
        match.setDirty();
    }

    private static void expire(MinecraftServer server, MatchData match, long now) {
        for (Contract c : match.contracts) {
            if (c.done || now < c.expiresTick) continue;
            if (c.expiresTick <= 0L) continue;
            // сгорел один раз: помечаем, чтобы не объявлять дважды
            c.expiresTick = 0L;
            notifyCrew(server, match, c.crew, Msg.paint(Component.translatable("blackchannel.contract.expired",
                    titleOf(c.kind, c.target, c.item)).withStyle(ChatFormatting.GRAY)), null);
        }
    }

    private static void refill(MinecraftServer server, MatchData match, String crew, long now, boolean weakest) {
        int open = countOpen(match, crew, now);
        boolean desperateNext = weakest;
        RandomSource random = server.overworld().getRandom();
        int guard = 0;
        while (open < SLOTS && guard++ < 8) {
            Contract c = null;
            List<String> kinds = new ArrayList<>(List.of(Contract.TRACE, Contract.HACK, Contract.TOWER,
                    Contract.KILL, Contract.DELIVER));
            Collections.shuffle(kinds, new java.util.Random(random.nextLong()));
            for (String kind : kinds) {
                if (hasOpen(match, crew, kind, now)) continue;
                c = build(server, match, crew, kind, now, random);
                if (c != null) break;
            }
            if (c == null) return;
            c.id = match.contractSeq++;
            c.desperate = desperateNext;
            if (desperateNext) {
                c.reward *= 2;
                desperateNext = false;
            }
            match.contracts.add(c);
            open++;
            Component line = Msg.paint(Component.translatable("blackchannel.contract.new",
                    titleOf(c.kind, c.target, c.item), c.reward).withStyle(c.desperate ? ChatFormatting.RED : ChatFormatting.AQUA));
            notifyCrew(server, match, crew, line, SoundEvents.NOTE_BLOCK_CHIME.value());
        }
    }

    private static Contract build(MinecraftServer server, MatchData match, String crew, String kind, long now,
                                  RandomSource random) {
        Contract c = new Contract();
        c.crew = crew;
        c.kind = kind;
        c.expiresTick = now + Config.CONTRACT_MINUTES.get() * 1200L;
        switch (kind) {
            case Contract.TRACE -> { c.target = 3; c.reward = 12; }
            case Contract.HACK -> { c.target = 1; c.reward = 15; }
            case Contract.TOWER -> { c.target = 1; c.reward = 20; }
            case Contract.KILL -> { c.target = 2; c.reward = 15; }
            case Contract.DELIVER -> {
                // проверка инвентаря отряда: задание только на то, что у отряда уже есть
                List<String> held = new ArrayList<>();
                for (String item : DELIVER_ITEMS) {
                    if (countCrewItem(server, match, crew, itemOf(item)) > 0) held.add(item);
                }
                if (held.isEmpty()) return null;
                c.item = held.get(random.nextInt(held.size()));
                int have = countCrewItem(server, match, crew, itemOf(c.item));
                c.target = Math.min(2, have);
                c.reward = 16 * c.target;
            }
            default -> { return null; }
        }
        return c;
    }

    // ---------- прогресс ----------

    /** Прибавить прогресс открытым заданиям отряда нужного типа. */
    public static void progress(MinecraftServer server, MatchData match, String crew, String kind, int amount) {
        if (crew == null || crew.isEmpty() || !match.active || match.finalActive) return;
        long now = server.overworld().getGameTime();
        for (Contract c : match.contracts) {
            if (!c.crew.equals(crew) || !c.kind.equals(kind) || !c.open(now)) continue;
            c.progress = Math.min(c.target, c.progress + amount);
            if (c.progress >= c.target) complete(server, match, c);
        }
        match.setDirty();
    }

    private static void complete(MinecraftServer server, MatchData match, Contract c) {
        c.done = true;
        NetData data = NetData.get(server);
        // Награда — всем, кто числится в отряде, онлайн или нет: отряд заработал вместе
        for (Map.Entry<UUID, String> entry : match.playerCrew.entrySet()) {
            if (!c.crew.equals(entry.getValue())) continue;
            NetData.Profile profile = data.profile(entry.getKey());
            profile.crypto = Math.min(CRYPTO_CAP, profile.crypto + c.reward);
            match.stats(entry.getKey()).cryptoEarned += c.reward;
        }
        data.setDirty();
        Component line = Msg.paint(Component.translatable("blackchannel.contract.done",
                titleOf(c.kind, c.target, c.item), c.reward).withStyle(ChatFormatting.GREEN));
        notifyCrew(server, match, c.crew, line, SoundEvents.PLAYER_LEVELUP);
    }

    // ---------- сдача предметов ----------

    /** Сдать предметы отряду: забираем у онлайн-игроков команды, сколько нужно и есть. */
    public static void deliver(ServerPlayer player, String arg) {
        MinecraftServer server = player.getServer();
        MatchData match = MatchData.get(server);
        long now = server.overworld().getGameTime();
        String crew = match.playerCrew.getOrDefault(player.getUUID(), "");
        Contract c = findOpen(match, arg, now);
        if (c == null || crew.isEmpty() || !c.crew.equals(crew) || !c.kind.equals(Contract.DELIVER)) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.contract.not_yours")));
            return;
        }
        Item item = itemOf(c.item);
        int need = c.target - c.progress;
        int have = countCrewItem(server, match, crew, item);
        int take = Math.min(need, have);
        if (take <= 0) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.contract.short")
                    .withStyle(ChatFormatting.RED)));
            return;
        }
        // сначала свои предметы, потом у остальных онлайн-членов отряда
        List<ServerPlayer> members = new ArrayList<>();
        members.add(player);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player && crew.equals(match.playerCrew.get(other.getUUID()))) members.add(other);
        }
        int left = take;
        for (ServerPlayer member : members) {
            left -= removeItem(member.getInventory(), item, left);
            if (left <= 0) break;
        }
        c.progress += take;
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.contract.delivered",
                take, Component.translatable("item.blackchannel." + c.item), c.target - c.progress)
                .withStyle(ChatFormatting.AQUA)));
        if (c.progress >= c.target) complete(server, match, c);
        match.setDirty();
        ModNetwork.syncFull(player);
    }

    // ---------- события матча ----------

    /** Игрок успешно сделал TRACE с результатом. */
    public static void onTrace(ServerPlayer player) {
        MatchData match = MatchData.get(player.getServer());
        match.stats(player.getUUID()).traces++;
        progress(player.getServer(), match, match.playerCrew.getOrDefault(player.getUUID(), ""), Contract.TRACE, 1);
    }

    /** Взлом стойки: засчитывается, если стойка чужого отряда. */
    public static void onHack(ServerPlayer player, String ownerCrew) {
        MatchData match = MatchData.get(player.getServer());
        String mine = match.playerCrew.getOrDefault(player.getUUID(), "");
        if (mine.isEmpty() || mine.equals(ownerCrew)) return;
        progress(player.getServer(), match, mine, Contract.HACK, 1);
    }

    // ---------- данные для телефона ----------

    /** Открытые задания отряда игрока: идут в синхронизацию телефона. */
    public static ListTag rowsFor(MatchData match, String crew, long now) {
        ListTag list = new ListTag();
        if (crew == null || crew.isEmpty() || !match.active) return list;
        for (Contract c : match.contracts) {
            if (!c.crew.equals(crew) || !c.open(now)) continue;
            CompoundTag row = new CompoundTag();
            row.putInt("id", c.id);
            row.putString("kind", c.kind);
            row.putInt("target", c.target);
            row.putInt("progress", c.progress);
            row.putInt("reward", c.reward);
            row.putBoolean("desperate", c.desperate);
            row.putInt("left", (int) Math.max(0L, (c.expiresTick - now) / 20L));
            row.putString("item", c.item);
            list.add(row);
        }
        return list;
    }

    // ---------- внутреннее ----------

    static Item itemOf(String id) {
        return switch (id) {
            case "exploit_firmware" -> ModContent.EXPLOIT.get();
            case "zero_day" -> ModContent.ZERO.get();
            case "false_contract" -> ModContent.CONTRACT.get();
            case "signal_sample" -> ModContent.SAMPLE.get();
            default -> null;
        };
    }

    private static Set<String> crews(MatchData match) {
        Set<String> crews = new LinkedHashSet<>();
        for (String crew : match.playerCrew.values()) {
            if (crew != null && !crew.isEmpty() && !match.eliminatedTeams.contains(crew)) crews.add(crew);
        }
        return crews;
    }

    /** Отряды с наименьшим счётом, если их меньше, чем всех: отстающий один (или ничья). */
    private static Map<String, Integer> lowestCrews(MatchData match) {
        Map<String, Integer> out = new java.util.HashMap<>();
        Set<String> crews = crews(match);
        if (crews.size() < 2) return out;
        int min = Integer.MAX_VALUE;
        for (String crew : crews) min = Math.min(min, match.getScore(crew));
        for (String crew : crews) {
            if (match.getScore(crew) == min) out.put(crew, min);
        }
        // ничья у нескольких отстающих — отчаянный получает только если один
        return out.size() == 1 ? out : Map.of();
    }

    private static boolean hadDesperate(MatchData match, String crew) {
        for (Contract c : match.contracts) {
            if (c.crew.equals(crew) && c.desperate) return true;
        }
        return false;
    }

    private static int countOpen(MatchData match, String crew, long now) {
        int n = 0;
        for (Contract c : match.contracts) if (c.crew.equals(crew) && c.open(now)) n++;
        return n;
    }

    private static boolean hasOpen(MatchData match, String crew, String kind, long now) {
        for (Contract c : match.contracts) {
            if (c.crew.equals(crew) && c.kind.equals(kind) && c.open(now)) return true;
        }
        return false;
    }

    private static Contract findOpen(MatchData match, String arg, long now) {
        int id;
        try {
            id = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        for (Contract c : match.contracts) {
            if (c.id == id && c.open(now)) return c;
        }
        return null;
    }

    private static int countCrewItem(MinecraftServer server, MatchData match, String crew, Item item) {
        if (item == null) return 0;
        int n = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!crew.equals(match.playerCrew.get(player.getUUID()))) continue;
            n += countIn(player.getInventory(), item);
        }
        return n;
    }

    private static int countIn(Inventory inv, Item item) {
        int n = 0;
        for (ItemStack stack : inv.items) if (stack.is(item)) n += stack.getCount();
        for (ItemStack stack : inv.offhand) if (stack.is(item)) n += stack.getCount();
        return n;
    }

    /** Убирает до limit штук предмета, возвращает сколько убрано. */
    private static int removeItem(Inventory inv, Item item, int limit) {
        int removed = 0;
        for (List<ItemStack> part : List.of(inv.items, inv.offhand)) {
            for (ItemStack stack : part) {
                if (removed >= limit) return removed;
                if (!stack.is(item)) continue;
                int take = Math.min(stack.getCount(), limit - removed);
                stack.shrink(take);
                removed += take;
            }
        }
        return removed;
    }

    private static void notifyCrew(MinecraftServer server, MatchData match, String crew, Component line, net.minecraft.sounds.SoundEvent sound) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!crew.equals(match.playerCrew.get(player.getUUID()))) continue;
            player.sendSystemMessage(line);
            if (sound != null) player.playNotifySound(sound, SoundSource.MASTER, 0.7f, 1.1f);
        }
    }
}
