package dev.blackchannel.client;

/**
 * Мини-игра взлома «резонанс»: стрелка ходит туда-сюда, её надо держать в зоне.
 * <p>
 * Игрок зажимает ЛКМ, пока стрелка в зоне: набирается прогресс {@link #HOLD_TICKS}
 * тиков (1.2 с). Вне зоны прогресс медленно падает. Лимит — {@link #LIMIT_TICKS} (8 с).
 * Вызывается раз в игровой тик (containerTick), поэтому темп не зависит от кадров.
 */
public final class HackGame {
    /** Сколько тиков в зоне нужно, чтобы взлом прошёл: 1.2 секунды. */
    public static final int HOLD_TICKS = 24;
    /** Общий лимит на взлом: 8 секунд. */
    public static final int LIMIT_TICKS = 160;
    /** Полуширина зоны в долях шкалы. */
    static final float ZONE = 0.09f;
    /** Шаг стрелки за тик. Полный проход туда-обратно ~ 4 секунды. */
    static final float SPEED = 0.0125f;

    /** Позиция стрелки 0..1. */
    public float pos;
    /** Центр зоны 0..1. */
    public final float target;
    /** Полуширина зоны. */
    public final float zone = ZONE;
    /** Прогресс удержания 0..1. */
    public float progress;
    public int ticks;
    public boolean success;
    public boolean failed;
    /** Позывной, которому уйдёт TRACE. Фиксируется на старте, чтобы поле ввода не влияло. */
    public final String handle;
    /** Направление движения стрелки: +1 или -1. */
    private float dir = 1f;

    public HackGame(String handle, int seed) {
        this.handle = handle;
        this.pos = 0f;
        // зона всегда в досягаемой части шкалы
        this.target = 0.25f + (Math.abs(seed * 0.618f) % 0.5f);
    }

    public boolean finished() {
        return success || failed;
    }

    /** Один игровой тик. holding — зажата ли ЛКМ. */
    public void tick(boolean holding) {
        if (finished()) return;
        ticks++;
        pos += dir * SPEED;
        if (pos >= 1f) { pos = 1f; dir = -1f; }
        if (pos <= 0f) { pos = 0f; dir = 1f; }
        boolean inside = Math.abs(pos - target) <= zone;
        if (inside && holding) {
            progress += 1f / HOLD_TICKS;
        } else {
            // уход из зоны или отпущенная кнопка: прогресс сползает, а не сбрасывается
            progress = Math.max(0f, progress - 1f / (HOLD_TICKS * 2f));
        }
        if (progress >= 1f) success = true;
        if (!success && ticks >= LIMIT_TICKS) failed = true;
    }

    public int secondsLeft() {
        return Math.max(0, (LIMIT_TICKS - ticks) / 20);
    }
}
