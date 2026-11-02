package dev.blackchannel.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Диктор канала — усталый диспетчер. Текст в чат и голос из
 * assets/blackchannel/sounds/announcer/&lt;key&gt;.ogg (sounds.json: announcer.&lt;key&gt;).
 * <p>
 * Голос играет у каждого игрока в наушники, без серверного voicechat.
 * Чтобы заменить фразу, достаточно положить ogg с тем же именем.
 */
public final class Announcer {
    private static final float VOLUME = 0.9f;

    private Announcer() {}

    /** Чат всем + голос всем. */
    public static void call(MinecraftServer server, String key, Object... args) {
        Component line = Msg.paint(Component.translatable("blackchannel.announcer." + key, args));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
            voice(player, key);
        }
    }

    /** Чат одному игроку + голос ему. */
    public static void to(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Msg.paint(Component.translatable("blackchannel.announcer." + key, args)));
        voice(player, key);
    }

    /** Только голос всем: текст уже был в чате, второй строки не нужно. */
    public static void voiceAll(MinecraftServer server, String key) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            voice(player, key);
        }
    }

    /** Только голос одному игроку. */
    public static void voice(ServerPlayer player, String key) {
        SoundEvent sound = SoundEvent.createVariableRangeEvent(new ResourceLocation("blackchannel", "announcer." + key));
        player.playNotifySound(sound, SoundSource.MASTER, VOLUME, 1f);
    }
}
