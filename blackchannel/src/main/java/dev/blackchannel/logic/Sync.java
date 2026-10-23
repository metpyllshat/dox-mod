package dev.blackchannel.logic;

import dev.blackchannel.block.NodeBlockEntity;
import dev.blackchannel.data.NetData;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.registry.ModContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class Sync {
    private Sync() {}

    public static CompoundTag full(ServerPlayer player) {
        CompoundTag tag = base(player);
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        MinecraftServer server = player.getServer();
        int now = server.getTickCount();
        tag.putString("resultKey", profile.resultKey == null ? "" : profile.resultKey);
        ListTag args = new ListTag();
        for (String arg : profile.resultArgs) args.add(StringTag.valueOf(arg));
        tag.put("resultArgs", args);
        tag.putString("channel", profile.channel);
        tag.putString("dm", profile.dm == null ? "" : profile.dm);
        tag.putInt("sat", profile.satCharges);
        tag.putString("crew", profile.crew == null ? "" : profile.crew);
        tag.putBoolean("ids", profile.ids);
        tag.putBoolean("hard", profile.hardened);

        ListTag intel = new ListTag();
        for (NetData.Intel row : profile.intel) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", row.id);
            c.putString("h", row.handle);
            c.putString("dim", Str.dimShort(row.dim));
            c.putInt("x", row.x);
            c.putInt("y", row.y);
            c.putInt("z", row.z);
            c.putInt("e", row.err);
            c.putInt("age", Math.max(0, (now - row.tick) / 20));
            c.putBoolean("stale", row.stale);
            intel.add(c);
        }
        tag.put("intel", intel);

        ListTag dean = new ListTag();
        profile.deanons.forEach((handle, name) -> {
            CompoundTag c = new CompoundTag();
            c.putString("h", handle);
            c.putString("n", name);
            dean.add(c);
        });
        tag.put("deanons", dean);

        ListTag chat = new ListTag();
        String want = chatChannel(profile);
        int added = 0;
        for (int i = data.chat.size() - 1; i >= 0 && added < 36; i--) {
            NetData.ChatLine line = data.chat.get(i);
            if (!accept(player, profile, line, want)) continue;
            CompoundTag c = new CompoundTag();
            c.putString("f", line.from);
            c.putString("t", line.text);
            c.putBoolean("e", line.encrypted);
            c.putInt("age", Math.max(0, (now - line.tick) / 20));
            chat.add(0, c);
            added++;
        }
        tag.put("chat", chat);

        ListTag listings = new ListTag();
        for (NetData.Listing listing : data.listings) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", listing.id);
            c.putString("seller", listing.sellerHandle);
            c.putInt("price", listing.price);
            c.putBoolean("intel", listing.intel);
            if (listing.intel) {
                c.putString("desc", listing.handle + " " + Str.dimShort(listing.dim) + " " + listing.x + " " + listing.y + " " + listing.z + " ±" + listing.err);
            } else {
                ItemStack stack = ItemStack.of(listing.stack);
                c.putString("item", Bridges.itemName(stack));
                c.putInt("count", Math.max(1, stack.getCount()));
            }
            listings.add(c);
        }
        tag.put("listings", listings);

        ListTag logs = new ListTag();
        for (NetData.LogLine log : profile.logs) {
            CompoundTag c = new CompoundTag();
            c.putString("k", log.key);
            c.putString("a", log.arg);
            c.putInt("age", Math.max(0, (now - log.tick) / 20));
            logs.add(c);
        }
        tag.put("logs", logs);

        ListTag tracks = new ListTag();
        for (SignalEnv.Track track : SignalEnv.tracks(player, profile)) {
            CompoundTag c = new CompoundTag();
            c.putString("label", track.label);
            c.putString("dim", Str.dimShort(track.dim));
            c.putInt("x", track.x);
            c.putInt("y", track.y);
            c.putInt("z", track.z);
            c.putBoolean("live", track.live);
            tracks.add(c);
        }
        tag.put("trackers", tracks);

        ListTag contacts = new ListTag();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            NetData.Profile op = data.profile(other.getUUID());
            if (SignalEnv.transmitting(other, op) && SignalEnv.link(other, op).mode != SignalEnv.Link.Mode.NONE) {
                contacts.add(StringTag.valueOf(op.handle));
            }
        }
        tag.put("contacts", contacts);

        ListTag bounties = new ListTag();
        for (NetData.Bounty bounty : data.bounties) {
            CompoundTag c = new CompoundTag();
            c.putString("h", bounty.handle);
            c.putInt("reward", bounty.reward);
            bounties.add(c);
        }
        tag.put("bounties", bounties);

        if (player.containerMenu instanceof Menus.RackMenu menu && player.level().getBlockEntity(menu.pos) instanceof NodeBlockEntity be) {
            tag.putInt("rackCharge", be.charge);
            tag.putBoolean("rackHackable", be.owner != null && !be.owner.equals(player.getUUID()));
        }
        return tag;
    }

    public static CompoundTag status(ServerPlayer player) {
        CompoundTag tag = base(player);
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        tag.putBoolean("radarHud", profile.radarHud);
        if (profile.radarHud) putBlips(tag, player, profile);
        return tag;
    }

    private static CompoundTag base(ServerPlayer player) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        MinecraftServer server = player.getServer();
        int now = server.getTickCount();
        CompoundTag tag = new CompoundTag();
        tag.putString("handle", profile.handle);
        tag.putString("name", profile.lastName);
        tag.putInt("crypto", profile.crypto);
        tag.putInt("vpn", profile.vpn);
        tag.putBoolean("airplane", profile.airplane);
        tag.putBoolean("dnd", profile.dnd);
        tag.putInt("battery", Devices.battery(player));
        tag.putInt("signal", SignalEnv.signalBars(player, profile));
        tag.putInt("emp", Math.max(0, profile.empUntil - now));
        tag.putInt("bomb", Math.max(0, profile.bombUntil - now));
        tag.putBoolean("jammed", SignalEnv.jammed(player.serverLevel(), player.blockPosition()));
        tag.putBoolean("faraday", SignalEnv.faraday(player.serverLevel(), player.blockPosition()));
        SignalEnv.Link link = SignalEnv.link(player, profile);
        tag.putBoolean("uplink", link.mode != SignalEnv.Link.Mode.NONE);
        tag.putBoolean("dish", link.dish);
        tag.putBoolean("radarHud", profile.radarHud);
        tag.putInt("cdT", secs(profile.cdTrace, now));
        tag.putInt("cdD", secs(profile.cdDeanon, now));
        tag.putInt("cdS", secs(profile.cdSwat, now));
        tag.putInt("cdB", secs(profile.cdBomb, now));
        tag.putInt("toastId", profile.toastId);
        tag.putString("toastKey", profile.toastKey == null ? "" : profile.toastKey);
        tag.putString("toastArg", profile.toastArg == null ? "" : profile.toastArg);
        tag.putInt("radarR", SignalEnv.radarRange(player, profile));
        putStrikes(tag, player, profile);
        putBlips(tag, player, profile);
        return tag;
    }

    private static void putBlips(CompoundTag tag, ServerPlayer player, NetData.Profile profile) {
        ListTag blips = new ListTag();
        for (SignalEnv.Blip blip : SignalEnv.blips(player, profile)) {
            CompoundTag c = new CompoundTag();
            c.putInt("dx", blip.dx);
            c.putInt("dz", blip.dz);
            c.putInt("dist", blip.dist);
            c.putString("kind", blip.kind);
            c.putString("label", blip.label);
            blips.add(c);
        }
        tag.put("blips", blips);
    }

    private static void putStrikes(CompoundTag tag, ServerPlayer player, NetData.Profile profile) {
        int best = 0;
        int x = 0, y = 0, z = 0;
        String dim = "";
        int own = 0;
        for (NetData.Strike strike : NetData.get(player).strikes) {
            if (strike.attacker.equals(player.getUUID())) own = Math.max(own, strike.ticksLeft);
            boolean target = strike.target.equals(player.getUUID());
            boolean close = Str.dim(player.level()).equals(strike.dim) && horiz(player, strike) <= 48;
            if ((target || close) && strike.ticksLeft > best) {
                best = strike.ticksLeft;
                x = strike.x;
                y = strike.y;
                z = strike.z;
                dim = Str.dimShort(strike.dim);
            }
        }
        tag.putInt("strikeT", best);
        tag.putInt("strikeX", x);
        tag.putInt("strikeY", y);
        tag.putInt("strikeZ", z);
        tag.putString("strikeDim", dim);
        tag.putInt("ownStrikeT", own);
    }

    private static boolean accept(ServerPlayer player, NetData.Profile profile, NetData.ChatLine line, String want) {
        if (!want.equals(line.channel)) return false;
        if ("local".equals(want)) {
            if (!Str.dim(player.level()).equals(line.dim)) return false;
            long dx = player.blockPosition().getX() - line.x;
            long dz = player.blockPosition().getZ() - line.z;
            return dx * dx + dz * dz <= 96L * 96L;
        }
        return true;
    }

    private static String chatChannel(NetData.Profile profile) {
        if ("dm".equals(profile.channel)) return dm(profile.handle, profile.dm);
        if ("local".equals(profile.channel)) return "local";
        return "darknet";
    }

    private static String dm(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.compareTo(b) <= 0) return "dm:" + a + ":" + b;
        return "dm:" + b + ":" + a;
    }

    private static int secs(int ready, int now) {
        return ready <= now ? 0 : (ready - now + 19) / 20;
    }

    private static double horiz(ServerPlayer player, NetData.Strike strike) {
        double dx = player.getX() - strike.x;
        double dz = player.getZ() - strike.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
