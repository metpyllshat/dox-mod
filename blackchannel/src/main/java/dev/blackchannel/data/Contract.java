package dev.blackchannel.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Контракт отряда на время одной катки. Задание общее для отряда:
 * прогресс дают действия любого игрока команды.
 */
public final class Contract {
    public static final String TRACE = "trace";
    public static final String HACK = "hack";
    public static final String TOWER = "tower";
    public static final String KILL = "kill";
    public static final String DELIVER = "deliver";

    public int id;
    public String crew = "";
    public String kind = "";
    public int target;
    public int progress;
    public int reward;
    public boolean desperate;
    /** Тик, после которого контракт сгорает без награды. */
    public long expiresTick;
    /** Предмет для DELIVER (id реестра), иначе пусто. */
    public String item = "";
    public boolean done;

    public Contract() {}

    public boolean open(long now) {
        return !done && now < expiresTick;
    }

    public CompoundTag save() {
        CompoundTag c = new CompoundTag();
        c.putInt("id", id);
        c.putString("crew", crew);
        c.putString("kind", kind);
        c.putInt("target", target);
        c.putInt("progress", progress);
        c.putInt("reward", reward);
        c.putBoolean("desperate", desperate);
        c.putLong("expires", expiresTick);
        c.putString("item", item);
        c.putBoolean("done", done);
        return c;
    }

    public static Contract load(CompoundTag c) {
        Contract k = new Contract();
        k.id = c.getInt("id");
        k.crew = c.getString("crew");
        k.kind = c.getString("kind");
        k.target = c.getInt("target");
        k.progress = c.getInt("progress");
        k.reward = c.getInt("reward");
        k.desperate = c.getBoolean("desperate");
        k.expiresTick = c.getLong("expires");
        k.item = c.getString("item");
        k.done = c.getBoolean("done");
        return k;
    }
}
