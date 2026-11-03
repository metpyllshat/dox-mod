package dev.blackchannel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;

/**
 * Поворот обзора вышки мышью. Когда камера — не сам игрок (сервер назначил маркер на вышке),
 * движение мыши крутит маркер так же, как ванильный поворот игрока: Entity.turn.
 * Сервер направление не получает: это только картинка на экране клиента.
 */
public final class TowerCam {
    private TowerCam() {}

    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (mc.player == null || camera == null || camera == mc.player) return;
        // Пока открыт экран (телефон, меню) мышь не должна крутить камеру
        if (mc.screen != null || !mc.mouseHandler.isMouseGrabbed()) return;
        double f = mc.options.sensitivity().get() * 0.6 + 0.2;
        double k = f * f * f * 8.0;
        double dx = mc.mouseHandler.getXVelocity() * k;
        double dy = mc.mouseHandler.getYVelocity() * k * (mc.options.invertYMouse().get() ? -1.0 : 1.0);
        if (dx == 0.0 && dy == 0.0) return;
        camera.turn(dx, dy);
    }
}
