package dev.blackchannel.client;

import net.minecraft.util.Mth;

/**
 * Мини-игра взлома: резонанс частоты 6с.
 * Ползунок ходит, нужно попасть в узкую зону и удержать 1.2с.
 */
public final class HackGame {
    public float pos = 0.5f; // 0..1
    public float vel = 0.008f;
    public float target = 0.62f;
    public float zone = 0.07f; // ширина резонанса
    public float hold = 0f;
    public int ticks = 0;
    public boolean failed = false;
    public boolean success = false;

    public HackGame(int seed) {
        target = 0.25f + (Math.abs(seed * 0.618f) % 0.5f);
    }

    public void tick(boolean holding) {
        if (success || failed) return;
        ticks++;
        pos += vel + (float)Math.sin(ticks * 0.07) * 0.006f;
        if (pos > 0.98f || pos < 0.02f) vel = -vel;
        pos = Mth.clamp(pos, 0f, 1f);
        boolean inside = Math.abs(pos - target) < zone;
        if (holding && inside) {
            hold += 0.05f; // 20 тиков = 1с
            if (hold >= 1.2f) success = true;
        } else if (!inside) {
            hold = Math.max(0, hold - 0.08f);
        }
        if (ticks > 120) failed = true; // 6с
    }

    public float dist() {
        return Math.abs(pos - target);
    }
}
