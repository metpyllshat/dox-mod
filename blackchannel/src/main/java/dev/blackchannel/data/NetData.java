package dev.blackchannel.data;

import dev.blackchannel.logic.Str;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetData extends SavedData {
    public final Map<UUID, Profile> profiles = new HashMap<>();
    public final List<ChatLine> chat = new ArrayList<>();
    public final List<Listing> listings = new ArrayList<>();
    public final List<Strike> strikes = new ArrayList<>();
    public final List<BlockBug> blockBugs = new ArrayList<>();
    public int listingSeq = 1;
    public int strikeSeq = 1;
    public long nextRumorTick;

    public static NetData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NetData::load, NetData::new, "blackchannel");
    }

    public static NetData get(ServerPlayer player) {
        return get(player.getServer());
    }

    public static NetData load(CompoundTag tag) {
        NetData data = new NetData();
        ListTag profiles = tag.getList("profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = Profile.load(profiles.getCompound(i));
            data.profiles.put(profile.id, profile);
        }
        readChat(tag.getList("chat", Tag.TAG_COMPOUND), data.chat);
        ListTag listings = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listings.size(); i++) data.listings.add(Listing.load(listings.getCompound(i)));
        ListTag strikes = tag.getList("strikes", Tag.TAG_COMPOUND);
        for (int i = 0; i < strikes.size(); i++) data.strikes.add(Strike.load(strikes.getCompound(i)));
        ListTag bugs = tag.getList("blockBugs", Tag.TAG_COMPOUND);
        for (int i = 0; i < bugs.size(); i++) data.blockBugs.add(BlockBug.load(bugs.getCompound(i)));
        data.listingSeq = tag.getInt("listingSeq");
        data.strikeSeq = tag.getInt("strikeSeq");
        data.nextRumorTick = tag.getLong("nextRumor");
        if (data.listingSeq < 1) data.listingSeq = 1;
        if (data.strikeSeq < 1) data.strikeSeq = 1;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag profiles = new ListTag();
        for (Profile profile : this.profiles.values()) profiles.add(profile.save());
        tag.put("profiles", profiles);
        tag.put("chat", writeChat(chat));
        ListTag listings = new ListTag();
        for (Listing listing : this.listings) listings.add(listing.save());
        tag.put("listings", listings);
        ListTag strikes = new ListTag();
        for (Strike strike : this.strikes) strikes.add(strike.save());
        tag.put("strikes", strikes);
        ListTag bugs = new ListTag();
        for (BlockBug bug : blockBugs) bugs.add(bug.save());
        tag.put("blockBugs", bugs);
        tag.putInt("listingSeq", listingSeq);
        tag.putInt("strikeSeq", strikeSeq);
        tag.putLong("nextRumor", nextRumorTick);
        return tag;
    }

    public Profile profile(UUID id) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            profile = new Profile(id);
            profile.handle = uniqueHandle(RandomSource.create(id.getLeastSignificantBits() ^ id.getMostSignificantBits()));
            profiles.put(id, profile);
            setDirty();
        }
        if (profile.handle == null || profile.handle.isEmpty()) {
            profile.handle = uniqueHandle(RandomSource.create(id.getLeastSignificantBits() + 17));
            setDirty();
        }
        return profile;
    }

    public Profile byHandle(String handle) {
        String key = Str.norm(handle);
        if (key.isEmpty()) return null;
        for (Profile profile : profiles.values()) {
            if (key.equals(profile.handle)) return profile;
        }
        return null;
    }

    public String uniqueHandle(RandomSource random) {
        for (int i = 0; i < 40; i++) {
            String handle = Str.rollHandle(random);
            if (byHandle(handle) == null) return handle;
        }
        return "node-" + (1000 + random.nextInt(9000));
    }

    public void addChat(ChatLine line) {
        chat.add(line);
        while (chat.size() > 240) chat.remove(0);
        setDirty();
    }

    public void prune(long tick) {
        strikes.removeIf(s -> s.ticksLeft < -200);
        blockBugs.removeIf(b -> b.expireTick < tick);
        for (Profile profile : profiles.values()) {
            profile.bugs.removeIf(b -> b.expireTick < tick);
            while (profile.intel.size() > 12) profile.intel.remove(0);
            while (profile.logs.size() > 16) profile.logs.remove(0);
        }
    }

    private static void readChat(ListTag list, List<ChatLine> into) {
        for (int i = 0; i < list.size(); i++) into.add(ChatLine.load(list.getCompound(i)));
    }

    private static ListTag writeChat(List<ChatLine> lines) {
        ListTag list = new ListTag();
        int start = Math.max(0, lines.size() - 240);
        for (int i = start; i < lines.size(); i++) list.add(lines.get(i).save());
        return list;
    }

    public static final class Profile {
        public final UUID id;
        public String handle = "";
        public String lastName = "";
        public int crypto;
        public int vpn;
        public boolean airplane;
        public boolean dnd;
        public boolean ids;
        public boolean hardened;
        public boolean radarHud;
        public int satCharges;
        public String crew = "";
        public String channel = "darknet";
        public String dm = "";
        public int empUntil;
        public int bombUntil;
        public int floodPicsFor;
        public int cdTrace, cdDeanon, cdSwat, cdBomb, cdOptic, cdPeek;
        public byte[] optic = new byte[0];
        public int[] opticColor = new int[0];
        public int opticHx = -1, opticHz = -1, opticConf, opticErr;
        public String opticBearing = "";
        public String opticBiome = "";
        public String opticHandle = "";
        public String opticDim = "";
        public final List<CompoundTag> peek = new ArrayList<>();
        public int lastPosTick;
        public String lastDim = "";
        public int lastX, lastY, lastZ;
        public int mintTick;
        public String mintDim = "";
        public int mintX, mintY, mintZ;
        public String resultKey = "";
        public String resultScope = "ops";
        public final List<String> resultArgs = new ArrayList<>();
        public int toastId;
        public String toastKey = "";
        public String toastArg = "";
        public boolean welcomed;
        public int chatWindow;
        public int chatCount;
        public int intelSeq = 1;
        public final List<Intel> intel = new ArrayList<>();
        public final Map<String, String> deanons = new LinkedHashMap<>();
        public final Map<String, String> heard = new LinkedHashMap<>();
        public final List<Bug> bugs = new ArrayList<>();
        public final List<LogLine> logs = new ArrayList<>();

        public Profile(UUID id) {
            this.id = id;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("handle", handle);
            tag.putString("name", lastName);
            tag.putInt("crypto", crypto);
            tag.putInt("vpn", vpn);
            tag.putBoolean("air", airplane);
            tag.putBoolean("dnd", dnd);
            tag.putBoolean("ids", ids);
            tag.putBoolean("hard", hardened);
            tag.putBoolean("radar", radarHud);
            tag.putInt("sat", satCharges);
            tag.putString("crew", crew);
            tag.putString("chan", channel);
            tag.putString("dm", dm);
            tag.putInt("emp", empUntil);
            tag.putInt("bomb", bombUntil);
            tag.putInt("cdT", cdTrace);
            tag.putInt("cdD", cdDeanon);
            tag.putInt("cdS", cdSwat);
            tag.putInt("cdB", cdBomb);
            tag.putInt("cdO", cdOptic);
            tag.putInt("cdP", cdPeek);
            tag.putInt("lpT", lastPosTick);
            tag.putString("lpD", lastDim);
            tag.putInt("lpX", lastX);
            tag.putInt("lpY", lastY);
            tag.putInt("lpZ", lastZ);
            tag.putInt("mintT", mintTick);
            tag.putString("mintD", mintDim == null ? "" : mintDim);
            tag.putInt("mintX", mintX);
            tag.putInt("mintY", mintY);
            tag.putInt("mintZ", mintZ);
            tag.putString("rk", resultKey);
            tag.putString("rs", resultScope == null ? "ops" : resultScope);
            ListTag args = new ListTag();
            for (String arg : resultArgs) args.add(StringTag.valueOf(arg));
            tag.put("ra", args);
            tag.putInt("toast", toastId);
            tag.putString("tk", toastKey);
            tag.putString("ta", toastArg);
            tag.putBoolean("wel", welcomed);
            tag.putInt("seq", intelSeq);
            ListTag intel = new ListTag();
            for (Intel row : this.intel) intel.add(row.save());
            tag.put("intel", intel);
            CompoundTag dean = new CompoundTag();
            for (Map.Entry<String, String> e : deanons.entrySet()) dean.putString(e.getKey(), e.getValue());
            tag.put("dean", dean);
            CompoundTag heard = new CompoundTag();
            for (Map.Entry<String, String> e : this.heard.entrySet()) heard.putString(e.getKey(), e.getValue() == null ? "" : e.getValue());
            tag.put("heard", heard);
            ListTag bugs = new ListTag();
            for (Bug bug : this.bugs) bugs.add(bug.save());
            tag.put("bugs", bugs);
            ListTag logs = new ListTag();
            for (LogLine log : this.logs) logs.add(log.save());
            tag.put("logs", logs);
            return tag;
        }

        public static Profile load(CompoundTag tag) {
            Profile profile = new Profile(tag.getUUID("id"));
            profile.handle = tag.getString("handle");
            profile.lastName = tag.getString("name");
            profile.crypto = tag.getInt("crypto");
            profile.vpn = tag.getInt("vpn");
            profile.airplane = tag.getBoolean("air");
            profile.dnd = tag.getBoolean("dnd");
            profile.ids = tag.getBoolean("ids");
            profile.hardened = tag.getBoolean("hard");
            profile.radarHud = tag.getBoolean("radar");
            profile.satCharges = tag.getInt("sat");
            profile.crew = tag.getString("crew");
            profile.channel = tag.contains("chan") ? tag.getString("chan") : "darknet";
            profile.dm = tag.getString("dm");
            profile.empUntil = tag.getInt("emp");
            profile.bombUntil = tag.getInt("bomb");
            profile.cdTrace = tag.getInt("cdT");
            profile.cdDeanon = tag.getInt("cdD");
            profile.cdSwat = tag.getInt("cdS");
            profile.cdBomb = tag.getInt("cdB");
            profile.cdOptic = tag.getInt("cdO");
            profile.cdPeek = tag.getInt("cdP");
            profile.lastPosTick = tag.getInt("lpT");
            profile.lastDim = tag.getString("lpD");
            profile.lastX = tag.getInt("lpX");
            profile.lastY = tag.getInt("lpY");
            profile.lastZ = tag.getInt("lpZ");
            profile.mintTick = tag.getInt("mintT");
            profile.mintDim = tag.getString("mintD");
            profile.mintX = tag.getInt("mintX");
            profile.mintY = tag.getInt("mintY");
            profile.mintZ = tag.getInt("mintZ");
            profile.resultKey = tag.getString("rk");
            profile.resultScope = tag.contains("rs") ? tag.getString("rs") : "ops";
            ListTag args = tag.getList("ra", Tag.TAG_STRING);
            for (int i = 0; i < args.size(); i++) profile.resultArgs.add(args.getString(i));
            profile.toastId = tag.getInt("toast");
            profile.toastKey = tag.getString("tk");
            profile.toastArg = tag.getString("ta");
            profile.welcomed = tag.getBoolean("wel");
            profile.intelSeq = Math.max(1, tag.getInt("seq"));
            ListTag intel = tag.getList("intel", Tag.TAG_COMPOUND);
            for (int i = 0; i < intel.size(); i++) profile.intel.add(Intel.load(intel.getCompound(i)));
            CompoundTag dean = tag.getCompound("dean");
            for (String key : dean.getAllKeys()) profile.deanons.put(key, dean.getString(key));
            CompoundTag heard = tag.getCompound("heard");
            for (String key : heard.getAllKeys()) profile.heard.put(key, heard.getString(key));
            ListTag bugs = tag.getList("bugs", Tag.TAG_COMPOUND);
            for (int i = 0; i < bugs.size(); i++) profile.bugs.add(Bug.load(bugs.getCompound(i)));
            ListTag logs = tag.getList("logs", Tag.TAG_COMPOUND);
            for (int i = 0; i < logs.size(); i++) profile.logs.add(LogLine.load(logs.getCompound(i)));
            return profile;
        }

        public void result(String key, String... args) {
            resultKey = key;
            resultArgs.clear();
            if (args != null) {
                for (String arg : args) resultArgs.add(arg == null ? "" : arg);
            }
        }

        public void toast(String key, String arg) {
            toastId++;
            toastKey = key;
            toastArg = arg == null ? "" : arg;
        }

        public void log(String key, String arg, int tick) {
            logs.add(new LogLine(key, arg == null ? "" : arg, tick));
            while (logs.size() > 16) logs.remove(0);
        }
    }

    public static final class Intel {
        public int id;
        public String handle = "";
        public String dim = "";
        public int x, y, z, err;
        public int tick;
        public boolean stale;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putString("h", handle);
            tag.putString("d", dim);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("e", err);
            tag.putInt("t", tick);
            tag.putBoolean("s", stale);
            return tag;
        }

        public static Intel load(CompoundTag tag) {
            Intel intel = new Intel();
            intel.id = tag.getInt("id");
            intel.handle = tag.getString("h");
            intel.dim = tag.getString("d");
            intel.x = tag.getInt("x");
            intel.y = tag.getInt("y");
            intel.z = tag.getInt("z");
            intel.err = tag.getInt("e");
            intel.tick = tag.getInt("t");
            intel.stale = tag.getBoolean("s");
            return intel;
        }
    }

    public static final class Bug {
        public UUID owner = new UUID(0, 0);
        public String ownerHandle = "";
        public long expireTick;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("o", owner);
            tag.putString("h", ownerHandle);
            tag.putLong("e", expireTick);
            return tag;
        }

        public static Bug load(CompoundTag tag) {
            Bug bug = new Bug();
            if (tag.hasUUID("o")) bug.owner = tag.getUUID("o");
            bug.ownerHandle = tag.getString("h");
            bug.expireTick = tag.getLong("e");
            return bug;
        }
    }

    public static final class BlockBug {
        public UUID owner = new UUID(0, 0);
        public String ownerHandle = "";
        public String dim = "";
        public int x, y, z;
        public long expireTick;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("o", owner);
            tag.putString("h", ownerHandle);
            tag.putString("d", dim);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putLong("e", expireTick);
            return tag;
        }

        public static BlockBug load(CompoundTag tag) {
            BlockBug bug = new BlockBug();
            if (tag.hasUUID("o")) bug.owner = tag.getUUID("o");
            bug.ownerHandle = tag.getString("h");
            bug.dim = tag.getString("d");
            bug.x = tag.getInt("x");
            bug.y = tag.getInt("y");
            bug.z = tag.getInt("z");
            bug.expireTick = tag.getLong("e");
            return bug;
        }
    }

    public static final class LogLine {
        public String key = "";
        public String arg = "";
        public int tick;

        public LogLine(String key, String arg, int tick) {
            this.key = key;
            this.arg = arg;
            this.tick = tick;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("k", key);
            tag.putString("a", arg);
            tag.putInt("t", tick);
            return tag;
        }

        public static LogLine load(CompoundTag tag) {
            return new LogLine(tag.getString("k"), tag.getString("a"), tag.getInt("t"));
        }
    }

    public static final class ChatLine {
        public String channel = "darknet";
        public String from = "";
        public String text = "";
        public int tick;
        public boolean encrypted;
        public String dim = "";
        public int x, y, z;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("c", channel);
            tag.putString("f", from);
            tag.putString("t", text);
            tag.putInt("k", tick);
            tag.putBoolean("e", encrypted);
            tag.putString("d", dim);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            return tag;
        }

        public static ChatLine load(CompoundTag tag) {
            ChatLine line = new ChatLine();
            line.channel = tag.getString("c");
            line.from = tag.getString("f");
            line.text = tag.getString("t");
            line.tick = tag.getInt("k");
            line.encrypted = tag.getBoolean("e");
            line.dim = tag.getString("d");
            line.x = tag.getInt("x");
            line.y = tag.getInt("y");
            line.z = tag.getInt("z");
            return line;
        }
    }

    public static final class Listing {
        public int id;
        public UUID seller = new UUID(0, 0);
        public String sellerHandle = "";
        public boolean intel;
        public CompoundTag stack = new CompoundTag();
        public int price;
        public String handle = "";
        public String dim = "";
        public int x, y, z, err;
        public boolean stale;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putUUID("s", seller);
            tag.putString("sh", sellerHandle);
            tag.putBoolean("i", intel);
            tag.put("stack", stack);
            tag.putInt("p", price);
            tag.putString("h", handle);
            tag.putString("d", dim);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("e", err);
            tag.putBoolean("st", stale);
            return tag;
        }

        public static Listing load(CompoundTag tag) {
            Listing listing = new Listing();
            listing.id = tag.getInt("id");
            if (tag.hasUUID("s")) listing.seller = tag.getUUID("s");
            listing.sellerHandle = tag.getString("sh");
            listing.intel = tag.getBoolean("i");
            listing.stack = tag.getCompound("stack");
            listing.price = tag.getInt("p");
            listing.handle = tag.getString("h");
            listing.dim = tag.getString("d");
            listing.x = tag.getInt("x");
            listing.y = tag.getInt("y");
            listing.z = tag.getInt("z");
            listing.err = tag.getInt("e");
            listing.stale = tag.getBoolean("st");
            return listing;
        }
    }

    public static final class Strike {
        public int id;
        public UUID attacker = new UUID(0, 0);
        public UUID target = new UUID(0, 0);
        public String handle = "";
        public String dim = "";
        public int x, y, z;
        public int ticksLeft;
        public boolean warned;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putUUID("a", attacker);
            tag.putUUID("t", target);
            tag.putString("h", handle);
            tag.putString("d", dim);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("l", ticksLeft);
            tag.putBoolean("w", warned);
            return tag;
        }

        public static Strike load(CompoundTag tag) {
            Strike strike = new Strike();
            strike.id = tag.getInt("id");
            if (tag.hasUUID("a")) strike.attacker = tag.getUUID("a");
            if (tag.hasUUID("t")) strike.target = tag.getUUID("t");
            strike.handle = tag.getString("h");
            strike.dim = tag.getString("d");
            strike.x = tag.getInt("x");
            strike.y = tag.getInt("y");
            strike.z = tag.getInt("z");
            strike.ticksLeft = tag.getInt("l");
            strike.warned = tag.getBoolean("w");
            return strike;
        }
    }
}
