package dev.blackchannel.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrapData extends SavedData {
    public final List<Trap> traps = new ArrayList<>();

    public static TrapData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TrapData::load, TrapData::new, "blackchannel_traps");
    }

    public static TrapData load(CompoundTag tag) {
        TrapData data = new TrapData();
        ListTag list = tag.getList("traps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.traps.add(Trap.load(list.getCompound(i)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Trap trap : traps) list.add(trap.save());
        tag.put("traps", list);
        return tag;
    }

    public Trap at(String dim, BlockPos pos) {
        for (Trap trap : traps) {
            if (trap.x == pos.getX() && trap.y == pos.getY() && trap.z == pos.getZ() && dim.equals(trap.dim)) return trap;
        }
        return null;
    }

    public void removeAt(String dim, BlockPos pos) {
        if (traps.removeIf(trap -> trap.x == pos.getX() && trap.y == pos.getY() && trap.z == pos.getZ() && dim.equals(trap.dim))) {
            setDirty();
        }
    }

    public static final class Trap {
        public String dim = "";
        public int x;
        public int y;
        public int z;
        public UUID owner;
        public int charges = 3;
        public final List<UUID> seen = new ArrayList<>();

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dim == null ? "" : dim);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            if (owner != null) tag.putUUID("owner", owner);
            tag.putInt("charges", charges);
            ListTag seen = new ListTag();
            for (UUID id : this.seen) seen.add(StringTag.valueOf(id.toString()));
            tag.put("seen", seen);
            return tag;
        }

        public static Trap load(CompoundTag tag) {
            Trap trap = new Trap();
            trap.dim = tag.getString("dim");
            trap.x = tag.getInt("x");
            trap.y = tag.getInt("y");
            trap.z = tag.getInt("z");
            if (tag.hasUUID("owner")) trap.owner = tag.getUUID("owner");
            trap.charges = tag.getInt("charges");
            ListTag seen = tag.getList("seen", Tag.TAG_STRING);
            for (int i = 0; i < seen.size(); i++) {
                try {
                    trap.seen.add(UUID.fromString(seen.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // skip a broken id rather than drop the whole trap
                }
            }
            return trap;
        }
    }
}
