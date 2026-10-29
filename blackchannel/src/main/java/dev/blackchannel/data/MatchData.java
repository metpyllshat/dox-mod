package dev.blackchannel.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MatchData extends SavedData {
    public boolean active;
    public boolean centerSet;
    public int centerX;
    public int centerY;
    public int centerZ;
    public String dim = "";
    public int size;
    public long graceUntil;
    public boolean borderSaved;
    public double prevSize = 5.9999968E7;
    public double prevCenterX;
    public double prevCenterZ;
    public boolean keepSaved;
    public boolean prevKeepInv;
    public final Map<UUID, Drop> drops = new HashMap<>();
    public final Map<String, Drop> anchors = new HashMap<>();
    public final Map<UUID, Boolean> kitted = new HashMap<>();

    public static MatchData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MatchData::load, MatchData::new, "blackchannel_match");
    }

    public static MatchData load(CompoundTag tag) {
        MatchData data = new MatchData();
        data.active = tag.getBoolean("active");
        data.centerSet = tag.getBoolean("centerSet");
        data.centerX = tag.getInt("cx");
        data.centerY = tag.getInt("cy");
        data.centerZ = tag.getInt("cz");
        data.dim = tag.getString("dim");
        data.size = tag.getInt("size");
        data.graceUntil = tag.getLong("grace");
        data.borderSaved = tag.getBoolean("borderSaved");
        data.prevSize = tag.contains("prevSize") ? tag.getDouble("prevSize") : 5.9999968E7;
        data.prevCenterX = tag.getDouble("prevCx");
        data.prevCenterZ = tag.getDouble("prevCz");
        data.keepSaved = tag.getBoolean("keepSaved");
        data.prevKeepInv = tag.getBoolean("prevKeep");
        readDrops(tag.getList("drops", Tag.TAG_COMPOUND), data.drops);
        ListTag anchors = tag.getList("anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchors.size(); i++) {
            CompoundTag row = anchors.getCompound(i);
            data.anchors.put(row.getString("key"), Drop.load(row));
        }
        ListTag kits = tag.getList("kits", Tag.TAG_COMPOUND);
        for (int i = 0; i < kits.size(); i++) {
            CompoundTag row = kits.getCompound(i);
            if (row.hasUUID("id")) data.kitted.put(row.getUUID("id"), true);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("active", active);
        tag.putBoolean("centerSet", centerSet);
        tag.putInt("cx", centerX);
        tag.putInt("cy", centerY);
        tag.putInt("cz", centerZ);
        tag.putString("dim", dim == null ? "" : dim);
        tag.putInt("size", size);
        tag.putLong("grace", graceUntil);
        tag.putBoolean("borderSaved", borderSaved);
        tag.putDouble("prevSize", prevSize);
        tag.putDouble("prevCx", prevCenterX);
        tag.putDouble("prevCz", prevCenterZ);
        tag.putBoolean("keepSaved", keepSaved);
        tag.putBoolean("prevKeep", prevKeepInv);
        ListTag drops = new ListTag();
        for (Map.Entry<UUID, Drop> entry : this.drops.entrySet()) drops.add(entry.getValue().save(entry.getKey()));
        tag.put("drops", drops);
        ListTag anchors = new ListTag();
        for (Map.Entry<String, Drop> entry : this.anchors.entrySet()) {
            CompoundTag row = entry.getValue().save(null);
            row.putString("key", entry.getKey());
            anchors.add(row);
        }
        tag.put("anchors", anchors);
        ListTag kits = new ListTag();
        for (UUID id : kitted.keySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", id);
            kits.add(row);
        }
        tag.put("kits", kits);
        return tag;
    }

    public boolean inside(BlockPos pos) {
        if (size < 2) return true;
        int half = size / 2;
        return Math.abs(pos.getX() - centerX) <= half && Math.abs(pos.getZ() - centerZ) <= half;
    }

    public int baseGap() {
        if (size < 1) return 80;
        return Math.max(80, size * 400 / 2500);
    }

    private static void readDrops(ListTag list, Map<UUID, Drop> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("id")) continue;
            into.put(row.getUUID("id"), Drop.load(row));
        }
    }

    public static final class Drop {
        public int x;
        public int y;
        public int z;
        public float yaw;

        public CompoundTag save(UUID id) {
            CompoundTag tag = new CompoundTag();
            if (id != null) tag.putUUID("id", id);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putFloat("yaw", yaw);
            return tag;
        }

        public static Drop load(CompoundTag tag) {
            Drop drop = new Drop();
            drop.x = tag.getInt("x");
            drop.y = tag.getInt("y");
            drop.z = tag.getInt("z");
            drop.yaw = tag.getFloat("yaw");
            return drop;
        }

        public static Drop at(int x, int y, int z, float yaw) {
            Drop drop = new Drop();
            drop.x = x;
            drop.y = y;
            drop.z = z;
            drop.yaw = yaw;
            return drop;
        }
    }
}
