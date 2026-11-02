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
    public float zone = 0.12f; // шире — было невозможно попасть
    public float hold = 0f;
    public int ticks = 0;
    public boolean failed = false;
    public boolean success = false;

    public HackGame(int seed) {
        target = 0.30f + (Math.abs(seed * 0.618f) % 0.40f); // 0.30-0.70 — всегда в досягаемости
    }

    public void tick(boolean holding) {
        if (success || failed) return;
        ticks++;
        // медленнее и предсказуемее
        pos += vel * 0.7f + (float)Math.sin(ticks * 0.05) * 0.003f;
        // игрок может подправлять ползунок удержанием
        if (holding) pos += 0.008f;
        else pos -= 0.004f;
        if (pos > 0.98f || pos < 0.02f) vel = -vel;
        pos = Mth.clamp(pos, 0f, 1f);
        boolean inside = Math.abs(pos - target) < zone;
        if (inside) {
            // внутри — копим, удержание ускоряет
            hold += holding ? 0.09f : 0.04f;
            if (hold >= 0.9f) success = true;
        } else {
            hold = Math.max(0, hold - 0.03f);
        }
        if (ticks > 160) failed = true; // 8с — дали больше времени
    }

    public float dist() {
        return Math.abs(pos - target);
    }
}
