package dev.blackchannel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientState {
    public static String handle = "";
    public static String name = "";
    public static String channel = "darknet";
    public static String dm = "";
    public static String crew = "";
    public static String resultKey = "";
    public static String[] resultArgs = new String[0];
    public static String toastKey = "";
    public static String toastArg = "";
    public static String toastLine = "";
    public static String strikeDim = "";
    public static int crypto, vpn, battery, signal, emp, bomb, cdT, cdD, cdS, cdB, radarR = 52;
    public static int strikeT, strikeX, strikeY, strikeZ, ownStrikeT, toastId, seenToast, sat, rackCharge;
    public static boolean airplane, dnd, jammed, faraday, uplink, dish, ids, hard, radarHud, rackHackable;
    public static long toastUntil;
    public static final List<IntelRow> intel = new ArrayList<>();
    public static final List<ChatRow> chat = new ArrayList<>();
    public static final List<Blip> blips = new ArrayList<>();
    public static final List<ListingRow> listings = new ArrayList<>();
    public static final List<TrackRow> trackers = new ArrayList<>();
    public static final List<String> contacts = new ArrayList<>();
    public static final List<BountyRow> bounties = new ArrayList<>();
    public static final List<LogRow> logs = new ArrayList<>();
    public static final List<DeanonRow> deanons = new ArrayList<>();
    public static final List<PeekSlot> peek = new ArrayList<>();
    public static final Map<String, Integer> cfg = new HashMap<>();
    public static byte[] optic = new byte[0];
    public static int[] opticColor = new int[0];
    public static int opticHx = -1, opticHz = -1, opticConf;
    public static String opticBearing = "";

    private ClientState() {}

    public static void applyFull(CompoundTag tag) {
        applyBase(tag);
        resultKey = tag.getString("resultKey");
        ListTag args = tag.getList("resultArgs", Tag.TAG_STRING);
        resultArgs = new String[args.size()];
        for (int i = 0; i < args.size(); i++) resultArgs[i] = args.getString(i);
        channel = tag.contains("channel") ? tag.getString("channel") : "darknet";
        dm = tag.getString("dm");
        crew = tag.getString("crew");
        sat = tag.getInt("sat");
        ids = tag.getBoolean("ids");
        hard = tag.getBoolean("hard");
        rackCharge = tag.getInt("rackCharge");
        rackHackable = tag.getBoolean("rackHackable");
        readIntel(tag.getList("intel", Tag.TAG_COMPOUND));
        readChat(tag.getList("chat", Tag.TAG_COMPOUND));
        readListings(tag.getList("listings", Tag.TAG_COMPOUND));
        readTracks(tag.getList("trackers", Tag.TAG_COMPOUND));
        readLogs(tag.getList("logs", Tag.TAG_COMPOUND));
        readDean(tag.getList("deanons", Tag.TAG_COMPOUND));
        readBounties(tag.getList("bounties", Tag.TAG_COMPOUND));
        optic = tag.contains("optic") ? tag.getByteArray("optic") : new byte[0];
        opticColor = tag.contains("opticC") ? tag.getIntArray("opticC") : new int[0];
        opticHx = tag.contains("opticHx") ? tag.getInt("opticHx") : -1;
        opticHz = tag.getInt("opticHz");
        opticConf = tag.getInt("opticConf");
        opticBearing = tag.getString("opticBear");
        readPeek(tag.getList("peek", Tag.TAG_COMPOUND));
        contacts.clear();
        ListTag names = tag.getList("contacts", Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) contacts.add(names.getString(i));
    }

    public static void applyStatus(CompoundTag tag) {
        applyBase(tag);
        if (tag.contains("radarHud")) radarHud = tag.getBoolean("radarHud");
    }

    private static void applyBase(CompoundTag tag) {
        if (tag == null) return;
        handle = tag.getString("handle");
        name = tag.getString("name");
        crypto = tag.getInt("crypto");
        vpn = tag.getInt("vpn");
        battery = tag.getInt("battery");
        signal = tag.getInt("signal");
        emp = tag.getInt("emp");
        bomb = tag.getInt("bomb");
        airplane = tag.getBoolean("airplane");
        dnd = tag.getBoolean("dnd");
        jammed = tag.getBoolean("jammed");
        faraday = tag.getBoolean("faraday");
        uplink = tag.getBoolean("uplink");
        dish = tag.getBoolean("dish");
        cdT = tag.getInt("cdT");
        cdD = tag.getInt("cdD");
        cdS = tag.getInt("cdS");
        cdB = tag.getInt("cdB");
        radarR = Math.max(8, tag.getInt("radarR"));
        strikeT = tag.getInt("strikeT");
        strikeX = tag.getInt("strikeX");
        strikeY = tag.getInt("strikeY");
        strikeZ = tag.getInt("strikeZ");
        strikeDim = tag.getString("strikeDim");
        ownStrikeT = tag.getInt("ownStrikeT");
        toastId = tag.getInt("toastId");
        toastKey = tag.getString("toastKey");
        toastArg = tag.getString("toastArg");
        if (tag.contains("radarHud")) radarHud = tag.getBoolean("radarHud");
        if (tag.contains("blips")) readBlips(tag.getList("blips", Tag.TAG_COMPOUND));
        if (tag.contains("cfg")) readCfg(tag.getCompound("cfg"));
        if (toastId != seenToast && toastKey != null && !toastKey.isEmpty()) {
            seenToast = toastId;
            toastLine = Component.translatable(toastKey, toastArg).getString();
            toastUntil = System.currentTimeMillis() + 4200;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.45f, 1.5f);
            }
        }
    }

    public static int cfg(String key, int fallback) {
        return cfg.getOrDefault(key, fallback);
    }

    public static String tip(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    public static String resultLine() {
        if (resultKey == null || resultKey.isEmpty()) return "";
        Object[] args = new Object[resultArgs.length];
        System.arraycopy(resultArgs, 0, args, 0, resultArgs.length);
        return Component.translatable(resultKey, args).getString();
    }

    private static void readIntel(ListTag list) {
        intel.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            IntelRow row = new IntelRow();
            row.id = c.getInt("id");
            row.handle = c.getString("h");
            row.dim = c.getString("dim");
            row.x = c.getInt("x");
            row.y = c.getInt("y");
            row.z = c.getInt("z");
            row.err = c.getInt("e");
            row.age = c.getInt("age");
            row.stale = c.getBoolean("stale");
            intel.add(row);
        }
    }

    private static void readChat(ListTag list) {
        chat.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            chat.add(new ChatRow(c.getString("f"), c.getString("t"), c.getBoolean("e"), c.getInt("age")));
        }
    }

    private static void readBlips(ListTag list) {
        blips.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            blips.add(new Blip(c.getInt("dx"), c.getInt("dz"), c.getInt("dist"), c.getString("kind"), c.getString("label")));
        }
    }

    private static void readListings(ListTag list) {
        listings.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ListingRow row = new ListingRow();
            row.id = c.getInt("id");
            row.seller = c.getString("seller");
            row.price = c.getInt("price");
            row.intel = c.getBoolean("intel");
            row.desc = c.getString("desc");
            row.item = c.getString("item");
            row.count = c.getInt("count");
            listings.add(row);
        }
    }

    private static void readTracks(ListTag list) {
        trackers.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            trackers.add(new TrackRow(c.getString("label"), c.getString("dim"), c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getBoolean("live")));
        }
    }

    private static void readLogs(ListTag list) {
        logs.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            logs.add(new LogRow(c.getString("k"), c.getString("a"), c.getInt("age")));
        }
    }

    private static void readDean(ListTag list) {
        deanons.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            deanons.add(new DeanonRow(c.getString("h"), c.getString("n")));
        }
    }

    private static void readCfg(CompoundTag tag) {
        cfg.clear();
        for (String key : tag.getAllKeys()) cfg.put(key, tag.getInt(key));
    }

    private static void readPeek(ListTag list) {
        peek.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            peek.add(new PeekSlot(c.getInt("i"), c.getString("id"), c.getInt("n"), c.getBoolean("h"), c.getBoolean("lie")));
        }
    }

    private static void readBounties(ListTag list) {
        bounties.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            bounties.add(new BountyRow(c.getString("h"), c.getInt("reward")));
        }
    }

    public static final class IntelRow {
        public int id, x, y, z, err, age;
        public String handle = "", dim = "";
        public boolean stale;
    }

    public record ChatRow(String from, String text, boolean enc, int age) {}
    public record Blip(int dx, int dz, int dist, String kind, String label) {}
    public static final class ListingRow {
        public int id, price, count;
        public String seller = "", desc = "", item = "";
        public boolean intel;
    }
    public record TrackRow(String label, String dim, int x, int y, int z, boolean live) {}
    public record LogRow(String key, String arg, int age) {}
    public record DeanonRow(String handle, String name) {}
    public record BountyRow(String handle, int reward) {}
    public record PeekSlot(int slot, String id, int count, boolean hidden, boolean lie) {}
}
