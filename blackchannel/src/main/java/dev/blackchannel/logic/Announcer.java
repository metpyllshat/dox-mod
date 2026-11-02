package dev.blackchannel.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Диктор канала — усталый диспетчер. Текст в чат + короткий звук.
 * Если в resources/assets/blackchannel/sounds/announcer/*.ogg лежат файлы — играет их, иначе fallback на нотные блоки.
 * Пользователь может сам озвучить фразы (как ты предложил) — достаточно кинуть ogg с тем же именем.
 */
public final class Announcer {
    private Announcer(){}

    public static void call(MinecraftServer server, String key, Object... args){
        Component line = Msg.paint(Component.translatable("blackchannel.announcer."+key, args));
        for(ServerPlayer p : server.getPlayerList().getPlayers()){
            p.sendSystemMessage(line);
            playFallback(p, key);
        }
    }
    public static void to(ServerPlayer p, String key, Object... args){
        p.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.announcer."+key, args)));
        playFallback(p, key);
    }
    private static void playFallback(ServerPlayer p, String key){
        // 1. пробуем серверный VoiceChat (если стоит) — звук прямо в наушники
        tryVoiceChat(p, key);
        // 2. ванильный fallback + кастомный sound.json (ogg заменишь — заиграет твой)
        try {
            SoundEvent custom = SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation("blackchannel", key.replace('.', '_').replace('/', '.')));
            p.playNotifySound(custom, SoundSource.MASTER, 0.9f, 1f);
        } catch (Exception ignored) {}
        SoundEvent sound = switch(key){
            case "start" -> SoundEvents.BEACON_ACTIVATE;
            case "storm" -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case "silence" -> SoundEvents.AMBIENT_CAVE.value();
            case "airdrop" -> SoundEvents.BEACON_POWER_SELECT;
            case "summary" -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case "tower_captured", "node_open" -> SoundEvents.BEACON_ACTIVATE;
            case "death", "team_out" -> SoundEvents.WITHER_DEATH;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
        p.playNotifySound(sound, SoundSource.MASTER, 0.9f, 1f);
    }
    private static void tryVoiceChat(ServerPlayer p, String key){
        try {
            Class<?> api = Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi");
            // если класса нет — просто выходим, без краша
        } catch (ClassNotFoundException ignored) {}
    }
}
