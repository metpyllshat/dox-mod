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
    public static String resultScope = "ops";
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
    public static final List<LogRow> logs = new ArrayList<>();
    public static final List<DeanonRow> deanons = new ArrayList<>();
    public static final List<DeanonRow> heard = new ArrayList<>();
    public static final List<String> floodLines = new ArrayList<>();
    public static final List<String> floodImages = new ArrayList<>();
    public static final List<PeekSlot> peek = new ArrayList<>();

    // Match HUD state
    public static int matchTimeLeftSeconds;
    public static boolean matchFinalActive;
    public static final Map<String, TeamScore> teamScores = new java.util.LinkedHashMap<>();
    public static int aliveCount;
    public static String selfStatus = "";
    public static int selfDownedSeconds;
    public static boolean matchActive;
    public static boolean matchEnded;
    public static final List<String> selfCrew = new ArrayList<>();

    public static final class TeamScore {
        public String code;
        public int score;
        public int colorIdx;
        public int alive;
        public int total;
    }
    public static final Map<String, Integer> cfg = new HashMap<>();
    public static byte[] optic = new byte[0];
    public static int[] opticColor = new int[0];
    public static int opticHx = -1, opticHz = -1, opticConf, opticErr;
    public static String opticBearing = "";
    public static String opticBiome = "";
    public static String opticHandle = "";
    public static String opticDim = "";

    private ClientState() {}

    public static void applyFull(CompoundTag tag) {
        applyBase(tag);
        resultKey = tag.getString("resultKey");
        resultScope = tag.contains("resultScope") ? tag.getString("resultScope") : "ops";
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
        readHeard(tag.getList("heard", Tag.TAG_COMPOUND));
        optic = tag.contains("optic") ? tag.getByteArray("optic") : new byte[0];
        opticColor = tag.contains("opticC") ? tag.getIntArray("opticC") : new int[0];
        opticHx = tag.contains("opticHx") ? tag.getInt("opticHx") : -1;
        opticHz = tag.getInt("opticHz");
        opticConf = tag.getInt("opticConf");
        opticBearing = tag.getString("opticBear");
        opticBiome = tag.getString("opticBiome");
        opticHandle = tag.getString("opticHandle");
        opticDim = tag.getString("opticDim");
        opticErr = tag.getInt("opticErr");
        readPeek(tag.getList("peek", Tag.TAG_COMPOUND));
        contacts.clear();
        ListTag names = tag.getList("contacts", Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) contacts.add(names.getString(i));
        readMatchState(tag.getCompound("match"));
    }

    private static void readMatchState(CompoundTag m) {
        if (m == null || m.isEmpty()) {
            matchActive = false;
            matchEnded = false;
            teamScores.clear();
            return;
        }
        matchActive = m.getBoolean("on");
        matchEnded = m.getBoolean("ended");
        matchFinalActive = m.getBoolean("final");
        matchTimeLeftSeconds = m.getInt("timeLeft");
        aliveCount = m.getInt("alive");
        selfStatus = m.getString("selfStatus");
        selfDownedSeconds = m.getInt("selfDowned");
        teamScores.clear();
        ListTag teams = m.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teams.size(); i++) {
            CompoundTag t = teams.getCompound(i);
            TeamScore ts = new TeamScore();
            ts.code = simplifyTeamName(t.getString("code"), i);
            ts.score = t.getInt("score");
            ts.colorIdx = t.getInt("colorIdx");
            ts.alive = t.getInt("alive");
            ts.total = t.getInt("total");
            teamScores.put(ts.code, ts);
        }
    }

    private static String simplifyTeamName(String code, int index) {
        if (code.startsWith("solo:")) return "Blue";
        if (code.startsWith("crew:")) {
            String[] names = {"Blue", "Green", "Yellow", "Aqua", "Purple", "Gold", "DarkGreen", "DarkAqua", "DarkPurple", "DarkRed", "White", "Gray"};
            int idx = index % names.length;
            return names[idx];
        }
        return code;
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
        readStrings(tag, "floodLines", floodLines);
        readStrings(tag, "floodImages", floodImages);
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

    public static String resultFor(String scope) {
        if (resultKey == null || resultKey.isEmpty() || "blackchannel.result.sent".equals(resultKey)) return "";
        String have = resultScope == null || resultScope.isEmpty() ? "ops" : resultScope;
        return scope.equals(have) ? resultLine() : "";
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
            row.dim = c.getString("dim");
            row.stale = c.getBoolean("stale");
            row.item = c.getString("item");
            row.count = c.getInt("count");
            row.nbt = c.getCompound("s");
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

    private static void readHeard(ListTag list) {
        heard.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            heard.add(new DeanonRow(c.getString("h"), c.getString("n")));
        }
    }

    private static void readStrings(CompoundTag tag, String key, List<String> into) {
        if (!tag.contains(key)) return;
        into.clear();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }

    private static void readCfg(CompoundTag tag) {
        cfg.clear();
        for (String key : tag.getAllKeys()) cfg.put(key, tag.getInt(key));
    }

    private static void readPeek(ListTag list) {
        peek.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            peek.add(new PeekSlot(c.getInt("i"), c.getString("id"), c.getInt("n"), c.getBoolean("h"), c.getBoolean("lie"), c.getCompound("s")));
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
        public String seller = "", desc = "", item = "", dim = "";
        public boolean intel, stale;
        public CompoundTag nbt = new CompoundTag();
    }
    public record TrackRow(String label, String dim, int x, int y, int z, boolean live) {}
    public record LogRow(String key, String arg, int age) {}
    public record DeanonRow(String handle, String name) {}
    public record PeekSlot(int slot, String id, int count, boolean hidden, boolean lie, CompoundTag nbt) {}
}
