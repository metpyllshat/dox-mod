package dev.blackchannel.client;

import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Клиентская часть обзора вышки, по образцу дрона SuperbWarfare.
 * Мышь крутит камеру штатным механизмом игры (как у наблюдателя). WASD и Пробел/Shift
 * сервер получает пакетом и двигает камеру-маркер вокруг вышки; тело игрока остаётся на месте.
 * Ввод движения тела во время обзора отключён, чтобы тело не дёргалось.
 */
public final class TowerCam {
    private static boolean active;

    private TowerCam() {}

    /** Пакет от сервера: обзор включён или выключен. */
    public static void apply(boolean on) {
        active = on;
    }

    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) return;
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (mc.player == null || camera == null || camera == mc.player) return;
        // Пока открыт экран (телефон, меню) или мышь не захвачена, камера стоит
        boolean free = mc.screen == null && mc.mouseHandler.isMouseGrabbed();
        double fwd = free ? axis(mc.options.keyUp, mc.options.keyDown) : 0.0;
        double side = free ? axis(mc.options.keyRight, mc.options.keyLeft) : 0.0;
        double up = free ? axis(mc.options.keyJump, mc.options.keyShift) : 0.0;
        // Углы отправляем каждый тик: сервер хранит тот же взгляд, и обновления не откатывают камеру
        ModNetwork.sendCamMove(fwd, side, up, camera.getYRot(), camera.getXRot());
    }

    /** Тело не двигаем, пока активен обзор: движение задаёт камера. */
    public static void input(MovementInputUpdateEvent event) {
        if (!active) return;
        var input = event.getInput();
        input.forwardImpulse = 0f;
        input.leftImpulse = 0f;
        input.up = false;
        input.down = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    private static double axis(KeyMapping plus, KeyMapping minus) {
        double v = 0.0;
        if (plus.isDown()) v += 1.0;
        if (minus.isDown()) v -= 1.0;
        return v;
    }
}
