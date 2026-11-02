package dev.blackchannel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Звуки интерфейса: кнопки, пеленг, глушение, взлом. Ключи совпадают с sounds.json
 * (ui.click, bearing.ping, jam.hum).
 */
public final class Sfx {
    private static final float VOLUME = 0.6f;

    private Sfx() {}

    public static void play(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        SoundEvent sound = SoundEvent.createVariableRangeEvent(new ResourceLocation("blackchannel", key));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, VOLUME));
    }
}
