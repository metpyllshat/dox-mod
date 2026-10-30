package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.block.NodeBlockEntity;
import dev.blackchannel.data.MatchData;
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
        tag.putString("resultScope", profile.resultScope == null || profile.resultScope.isEmpty() ? "ops" : profile.resultScope);
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
        ListTag heard = new ListTag();
        profile.heard.forEach((handle, name) -> {
            CompoundTag c = new CompoundTag();
            c.putString("h", handle);
            c.putString("n", name == null ? "" : name);
            heard.add(c);
        });
        tag.put("heard", heard);

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
                c.putString("desc", listing.handle);
                c.putString("dim", Str.dimShort(listing.dim));
                c.putBoolean("stale", listing.stale);
            } else {
                ItemStack stack = ItemStack.of(listing.stack);
                c.putString("item", Bridges.itemName(stack));
                c.putInt("count", Math.max(1, stack.getCount()));
                CompoundTag preview = previewStack(stack);
                if (!preview.isEmpty()) c.put("s", preview);
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
        if (profile.optic != null && profile.optic.length == Optics.GRID * Optics.GRID) {
            tag.putByteArray("optic", profile.optic);
            if (profile.opticColor != null && profile.opticColor.length == profile.optic.length) tag.putIntArray("opticC", profile.opticColor);
            tag.putInt("opticHx", profile.opticHx);
            tag.putInt("opticHz", profile.opticHz);
            tag.putInt("opticConf", profile.opticConf);
            tag.putString("opticBear", profile.opticBearing == null ? "" : profile.opticBearing);
            tag.putString("opticBiome", profile.opticBiome == null ? "" : profile.opticBiome);
            tag.putString("opticHandle", profile.opticHandle == null ? "" : profile.opticHandle);
            tag.putString("opticDim", profile.opticDim == null ? "" : profile.opticDim);
            tag.putInt("opticErr", profile.opticErr);
        }
        tag.put("peek", Optics.peekTag(profile));

        if (player.containerMenu instanceof Menus.RackMenu menu && player.level().getBlockEntity(menu.pos) instanceof NodeBlockEntity be) {
            tag.putInt("rackCharge", be.charge);
            tag.putBoolean("rackHackable", be.owner != null && !be.owner.equals(player.getUUID()));
        }
        putMatchState(tag, player, profile, now);
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
        putCfg(tag);
        putStrikes(tag, player, profile);
        putBlips(tag, player, profile);
        return tag;
    }

    private static CompoundTag previewStack(ItemStack stack) {
        if (stack.isEmpty()) return new CompoundTag();
        ItemStack shown = new ItemStack(stack.getItem(), Math.max(1, Math.min(64, stack.getCount())));
        if (stack.isDamageableItem()) shown.setDamageValue(stack.getDamageValue());
        CompoundTag src = stack.getTag();
        if (src != null) {
            CompoundTag dst = shown.getOrCreateTag();
            copyKey(src, dst, "Enchantments");
            copyKey(src, dst, "StoredEnchantments");
            copyKey(src, dst, "display");
            copyKey(src, dst, "Potion");
            copyKey(src, dst, "CustomPotionEffects");
            copyKey(src, dst, "CustomPotionColor");
            copyKey(src, dst, "Trim");
            copyKey(src, dst, "AttributeModifiers");
            if (src.getBoolean("Unbreakable")) dst.putBoolean("Unbreakable", true);
        }
        return shown.save(new CompoundTag());
    }

    private static void copyKey(CompoundTag src, CompoundTag dst, String key) {
        if (src.contains(key)) dst.put(key, src.get(key).copy());
    }

    private static void putCfg(CompoundTag tag) {
        CompoundTag cfg = new CompoundTag();
        cfg.putInt("traceBase", Config.TRACE_BASE.get());
        cfg.putInt("traceVpnMul", (int) Math.round(Config.TRACE_VPN_MUL.get() * 100.0));
        cfg.putInt("traceVpnDeep", (int) Math.round(Config.TRACE_VPN_DEEP.get() * 100.0));
        cfg.putInt("traceStale", Config.TRACE_STALE.get());
        cfg.putInt("traceFail", Config.TRACE_FAIL.get());
        cfg.putInt("traceFailVpn", Config.TRACE_FAIL_VPN.get());
        cfg.putInt("traceFailDeep", Config.TRACE_FAIL_DEEP.get());
        cfg.putInt("traceCost", Config.TRACE_COST.get());
        cfg.putInt("deanonCost", Config.DEANON_COST.get());
        cfg.putInt("swatCost", Config.SWAT_COST.get());
        cfg.putInt("swatContract", Config.SWAT_CONTRACT_COST.get());
        cfg.putInt("swatDelay", Config.SWAT_DELAY.get());
        cfg.putInt("bombCost", Config.BOMB_COST.get());
        cfg.putInt("uavCost", Config.UAV_COST.get());
        cfg.putInt("uavRange", Config.UAV_RANGE.get());
        cfg.putInt("uavError", Config.UAV_ERROR.get());
        cfg.putInt("opticCost", Config.OPTIC_COST.get());
        cfg.putInt("peekCost", Config.PEEK_COST.get());
        cfg.putInt("radarPhone", Config.RADAR_PHONE.get());
        cfg.putInt("radarLaptop", Config.RADAR_LAPTOP.get());
        cfg.putInt("dishBonus", Config.DISH_RADAR_BONUS.get());
        cfg.putInt("structure", Config.STRUCTURE_RADAR.get());
        cfg.putInt("structurePhone", Config.STRUCTURE_PHONE.get());
        cfg.putInt("refundVpn", half("vpn"));
        cfg.putInt("refundDeep", half("deep"));
        cfg.putInt("refundIds", half("ids"));
        cfg.putInt("refundHarden", half("harden"));
        tag.put("cfg", cfg);
        tag.put("floodLines", floodList(Config.FLOOD_LINES.get(), 24));
        tag.put("floodImages", floodList(Config.FLOOD_IMAGES.get(), 8));
    }

    private static int half(String id) {
        Catalog.Entry entry = Catalog.byId(id);
        return entry == null ? 0 : entry.price() / 2;
    }

    private static ListTag floodList(java.util.List<? extends String> src, int max) {
        ListTag list = new ListTag();
        if (src == null) return list;
        int n = 0;
        for (String s : src) {
            if (s == null || s.isBlank()) continue;
            list.add(StringTag.valueOf(s.length() > 80 ? s.substring(0, 80) : s));
            if (++n >= max) break;
        }
        return list;
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

    private static void putMatchState(CompoundTag tag, ServerPlayer player, NetData.Profile profile, int now) {
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) {
            tag.put("match", new CompoundTag());
            return;
        }
        long matchTime = player.getServer().overworld().getGameTime();
        long endTick = match.finalActive ? match.finalEndTick : match.matchEndTick;
        int timeLeftSec = (int) Math.max(0, (endTick - matchTime) / 20L);
        CompoundTag m = new CompoundTag();
        m.putBoolean("on", true);
        m.putBoolean("final", match.finalActive);
        m.putInt("timeLeft", timeLeftSec);
        m.putString("selfStatus", Minigame.selfStatusFor(player, match));
        m.putInt("selfDowned", Minigame.selfDownedSeconds(player, match));

        // Team scores & alive
        java.util.Map<String, Integer> teamTotal = new java.util.HashMap<>();
        java.util.Map<String, Integer> teamAlive = new java.util.HashMap<>();
        int aliveCount = 0;
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            String code = NetData.get(p).profile(p.getUUID()).crew;
            if (code == null || code.isEmpty()) code = "solo:" + p.getUUID();
            teamTotal.merge(code, 1, Integer::sum);
            boolean dead = match.permanentDead.contains(p.getUUID());
            boolean downed = match.downedPlayers.containsKey(p.getUUID());
            if (!dead && !downed) {
                teamAlive.merge(code, 1, Integer::sum);
                aliveCount++;
            }
        }
        m.putInt("alive", aliveCount);
        ListTag teams = new ListTag();
        for (String code : match.teamScores.keySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("code", code);
            t.putInt("score", match.teamScores.getOrDefault(code, 0));
            t.putInt("colorIdx", match.teamColorIdx.getOrDefault(code, 11));
            t.putInt("alive", teamAlive.getOrDefault(code, 0));
            t.putInt("total", teamTotal.getOrDefault(code, 0));
            teams.add(t);
        }
        m.put("teams", teams);
        tag.put("match", m);
    }
}
