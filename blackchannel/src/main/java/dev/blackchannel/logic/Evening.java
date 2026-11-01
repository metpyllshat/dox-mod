package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * События вечера: раз в eventIntervalMinutes канал объявляет одно из четырёх.
 * Это не наказание за скуку, а повод подняться с базы: шторм выгоняет из укрытий,
 * тишина даёт окно для скрытного броска, аукцион стягивает всех на рынок,
 * сводка напоминает, кто впереди.
 */
public final class Evening {
    private Evening() {}

    public static void tick(MinecraftServer server, MatchData match) {
        if (!Config.EVENT_ENABLED.get() || !match.active) return;
        long now = server.overworld().getGameTime();
        if (match.nextEventTick <= 0L) {
            match.nextEventTick = match.matchStartTick + Config.EVENT_FIRST_MINUTE.get() * 1200L;
            match.setDirty();
            return;
        }
        if (now < match.nextEventTick) return;
        match.nextEventTick = now + Config.EVENT_INTERVAL_MINUTES.get() * 1200L;
        fire(server, match, now);
        match.setDirty();
    }

    private static void fire(MinecraftServer server, MatchData match, long now) {
        RandomSource random = server.overworld().getRandom();
        int pick = random.nextInt(Config.AUCTION_ENABLED.get() ? 4 : 3);
        switch (pick) {
            case 0 -> storm(server, now);
            case 1 -> silence(server, match, now);
            case 2 -> summary(server, match);
            default -> auction(server, now, random);
        }
    }

    /** ЭМИ-шторм: терминалы у всех глохнут на двадцать секунд. */
    private static void storm(MinecraftServer server, long now) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetData.Profile profile = NetData.get(player).profile(player.getUUID());
            profile.empUntil = Math.max(profile.empUntil, (int) now + 400);
            player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 0.5f, 1.4f);
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.event.storm")
                    .withStyle(ChatFormatting.AQUA)));
        }
    }

    /** Тишина: полторы минуты в эфире нельзя ничего: ни TRACE, ни рынка, ни вызова. */
    private static void silence(MinecraftServer server, MatchData match, long now) {
        match.silenceUntil = now + 1800L;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.MASTER, 0.7f, 0.6f);
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.event.silence")
                    .withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Сводка: кто сколько набрал и сколько ещё живых. */
    private static void summary(MinecraftServer server, MatchData match) {
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(match.teamScores.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder line = new StringBuilder();
        for (Map.Entry<String, Integer> row : rows) {
            if (line.length() > 0) line.append("  ");
            line.append(Minigame.teamName(server, row.getKey())).append(' ').append(row.getValue());
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.event.summary", line.toString())
                    .withStyle(ChatFormatting.GOLD)));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.6f, 1.1f);
        }
    }

    /**
     * Аукцион вслепую: на рынок выставляют один лот, но никто не видит, что внутри.
     * Купить — рискнуть; не купить — упустить. Один такой лот за событие.
     */
    private static void auction(MinecraftServer server, long now, RandomSource random) {
        NetData data = NetData.get(server);
        List<ItemStack> roll = Minigame.rollLoot(Config.CACHE_LATE.get(), 1, random);
        if (roll.isEmpty()) return;
        NetData.Listing lot = new NetData.Listing();
        lot.id = data.listingSeq++;
        lot.sellerHandle = "канал";
        lot.blind = true;
        lot.stack = roll.get(0).save(new CompoundTag());
        lot.price = 60 + random.nextInt(8) * 10;
        data.listings.add(lot);
        data.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.event.auction", lot.price)
                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6f, 0.9f);
        }
    }
}
