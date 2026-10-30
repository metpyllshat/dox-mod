package dev.blackchannel.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Match timing (ticks)
    public long matchEndTick;
    public long finalEndTick;
    public boolean finalActive;
    public boolean ended;

    // Team state
    public final Map<String, Integer> teamScores = new HashMap<>();
    public final Map<String, BlockPos> teamBeacons = new HashMap<>();
    public final Map<String, Integer> teamColorIdx = new HashMap<>();
    public final Set<String> eliminatedTeams = new HashSet<>();

    // Player beds & state
    public final Map<UUID, BlockPos> bedPositions = new HashMap<>();
    public final Map<UUID, DownedState> downedPlayers = new HashMap<>();
    public final Set<UUID> reviveUsed = new HashSet<>();
    public final Set<UUID> permanentDead = new HashSet<>();
    public final Map<UUID, String> playerCrew = new HashMap<>();
    public final Map<UUID, Integer> playerColorIdx = new HashMap<>();

    // Пошаговое размещение схронов
    public int cacheTick;
    public int cacheTotal;
    public BlockPos[] cachePositions = new BlockPos[0];

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
        data.matchEndTick = tag.getLong("matchEnd");
        data.finalEndTick = tag.getLong("finalEnd");
        data.finalActive = tag.getBoolean("finalActive");
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
        readScores(tag.getList("scores", Tag.TAG_COMPOUND), data.teamScores);
        readBeacons(tag.getList("beacons", Tag.TAG_COMPOUND), data.teamBeacons);
        readColorIdx(tag.getList("colors", Tag.TAG_COMPOUND), data.teamColorIdx);
        readBeds(tag.getList("beds", Tag.TAG_COMPOUND), data.bedPositions);
        readDowned(tag.getList("downed", Tag.TAG_COMPOUND), data.downedPlayers);
        readUUIDSet(tag.getList("reviveUsed", Tag.TAG_COMPOUND), data.reviveUsed, "id");
        readUUIDSet(tag.getList("dead", Tag.TAG_COMPOUND), data.permanentDead, "id");
        readUUIDMap(tag.getList("crew", Tag.TAG_COMPOUND), data.playerCrew);
        readUUIDIntMap(tag.getList("pcolors", Tag.TAG_COMPOUND), data.playerColorIdx);
        data.cacheTick = tag.getInt("cacheTick");
        data.cacheTotal = tag.getInt("cacheTotal");
        ListTag cpos = tag.getList("cachePos", Tag.TAG_COMPOUND);
        data.cachePositions = new BlockPos[cpos.size()];
        for (int i = 0; i < cpos.size(); i++) {
            CompoundTag c = cpos.getCompound(i);
            data.cachePositions[i] = new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
        }
        ListTag elim = tag.getList("eliminated", Tag.TAG_STRING);
        for (int i = 0; i < elim.size(); i++) data.eliminatedTeams.add(elim.getString(i));
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
        tag.putLong("matchEnd", matchEndTick);
        tag.putLong("finalEnd", finalEndTick);
        tag.putBoolean("finalActive", finalActive);
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
        tag.put("scores", writeScores(teamScores));
        tag.put("beacons", writeBeacons(teamBeacons));
        tag.put("colors", writeColorIdx(teamColorIdx));
        tag.put("beds", writeBeds(bedPositions));
        tag.put("downed", writeDowned(downedPlayers));
        tag.put("reviveUsed", writeUUIDSet(reviveUsed, "id"));
        tag.put("dead", writeUUIDSet(permanentDead, "id"));
        tag.put("crew", writeUUIDMap(playerCrew));
        tag.put("pcolors", writeUUIDIntMap(playerColorIdx));
        tag.putInt("cacheTick", cacheTick);
        tag.putInt("cacheTotal", cacheTotal);
        ListTag cpos = new ListTag();
        for (BlockPos p : cachePositions) {
            if (p == null) {
                CompoundTag c = new CompoundTag();
                c.putInt("x", 0); c.putInt("y", 0); c.putInt("z", 0);
                cpos.add(c);
            } else {
                CompoundTag c = new CompoundTag();
                c.putInt("x", p.getX()); c.putInt("y", p.getY()); c.putInt("z", p.getZ());
                cpos.add(c);
            }
        }
        tag.put("cachePos", cpos);
        ListTag elim = new ListTag();
        for (String code : eliminatedTeams) elim.add(net.minecraft.nbt.StringTag.valueOf(code));
        tag.put("eliminated", elim);
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

    public int getScore(String crew) {
        if (crew == null || crew.isEmpty()) return 0;
        return teamScores.getOrDefault(crew, 0);
    }

    public void addScore(String crew, int delta) {
        if (crew == null || crew.isEmpty()) return;
        teamScores.merge(crew, delta, Integer::sum);
    }

    public void setBed(UUID player, BlockPos pos) {
        if (pos == null) bedPositions.remove(player);
        else bedPositions.put(player, pos.immutable());
    }

    public BlockPos getBed(UUID player) {
        return bedPositions.get(player);
    }

    public void setBeacon(String crew, BlockPos pos) {
        if (pos == null) teamBeacons.remove(crew);
        else teamBeacons.put(crew, pos.immutable());
    }

    public BlockPos getBeacon(String crew) {
        return teamBeacons.get(crew);
    }

    public void down(UUID player, DownedState state) {
        downedPlayers.put(player, state);
    }

    public DownedState getDowned(UUID player) {
        return downedPlayers.get(player);
    }

    public void clearDowned(UUID player) {
        downedPlayers.remove(player);
    }

    private static void readDrops(ListTag list, Map<UUID, Drop> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("id")) continue;
            into.put(row.getUUID("id"), Drop.load(row));
        }
    }

    private static void readScores(ListTag list, Map<String, Integer> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            into.put(c.getString("k"), c.getInt("s"));
        }
    }

    private static ListTag writeScores(Map<String, Integer> map) {
        ListTag out = new ListTag();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putInt("s", e.getValue());
            out.add(c);
        }
        return out;
    }

    private static void readBeacons(ListTag list, Map<String, BlockPos> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            into.put(c.getString("k"), new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
        }
    }

    private static ListTag writeBeacons(Map<String, BlockPos> map) {
        ListTag out = new ListTag();
        for (Map.Entry<String, BlockPos> e : map.entrySet()) {
            BlockPos p = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putInt("x", p.getX());
            c.putInt("y", p.getY());
            c.putInt("z", p.getZ());
            out.add(c);
        }
        return out;
    }

    private static void readColorIdx(ListTag list, Map<String, Integer> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            into.put(c.getString("k"), c.getInt("v"));
        }
    }

    private static ListTag writeColorIdx(Map<String, Integer> map) {
        ListTag out = new ListTag();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putInt("v", e.getValue());
            out.add(c);
        }
        return out;
    }

    private static void readBeds(ListTag list, Map<UUID, BlockPos> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("id")) into.put(c.getUUID("id"), new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
        }
    }

    private static ListTag writeBeds(Map<UUID, BlockPos> map) {
        ListTag out = new ListTag();
        for (Map.Entry<UUID, BlockPos> e : map.entrySet()) {
            BlockPos p = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putInt("x", p.getX());
            c.putInt("y", p.getY());
            c.putInt("z", p.getZ());
            out.add(c);
        }
        return out;
    }

    private static void readDowned(ListTag list, Map<UUID, DownedState> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("id")) continue;
            DownedState s = new DownedState();
            s.downedAtTick = c.getLong("t");
            s.killerHandle = c.getString("kh");
            s.killerCrew = c.getString("kc");
            s.pos = new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            s.dim = c.getString("d");
            into.put(c.getUUID("id"), s);
        }
    }

    private static ListTag writeDowned(Map<UUID, DownedState> map) {
        ListTag out = new ListTag();
        for (Map.Entry<UUID, DownedState> e : map.entrySet()) {
            DownedState s = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putLong("t", s.downedAtTick);
            c.putString("kh", s.killerHandle == null ? "" : s.killerHandle);
            c.putString("kc", s.killerCrew == null ? "" : s.killerCrew);
            c.putInt("x", s.pos.getX());
            c.putInt("y", s.pos.getY());
            c.putInt("z", s.pos.getZ());
            c.putString("d", s.dim == null ? "" : s.dim);
            out.add(c);
        }
        return out;
    }

    private static void readUUIDSet(ListTag list, Set<UUID> into, String keyName) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID(keyName)) into.add(c.getUUID(keyName));
        }
    }

    private static ListTag writeUUIDSet(Set<UUID> set, String keyName) {
        ListTag out = new ListTag();
        for (UUID id : set) {
            CompoundTag c = new CompoundTag();
            c.putUUID(keyName, id);
            out.add(c);
        }
        return out;
    }

    private static void readUUIDMap(ListTag list, Map<UUID, String> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("id")) into.put(c.getUUID("id"), c.getString("v"));
        }
    }

    private static ListTag writeUUIDMap(Map<UUID, String> map) {
        ListTag out = new ListTag();
        for (Map.Entry<UUID, String> e : map.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putString("v", e.getValue() == null ? "" : e.getValue());
            out.add(c);
        }
        return out;
    }

    private static void readUUIDIntMap(ListTag list, Map<UUID, Integer> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("id")) into.put(c.getUUID("id"), c.getInt("v"));
        }
    }

    private static ListTag writeUUIDIntMap(Map<UUID, Integer> map) {
        ListTag out = new ListTag();
        for (Map.Entry<UUID, Integer> e : map.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putInt("v", e.getValue());
            out.add(c);
        }
        return out;
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

    public static final class DownedState {
        public long downedAtTick;
        public String killerHandle = "";
        public String killerCrew = "";
        public BlockPos pos;
        public String dim = "";
    }
}
