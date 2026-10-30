package dev.blackchannel.logic;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.Config;
import dev.blackchannel.block.NodeBlockEntity;
import dev.blackchannel.data.NetData;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.item.ToolItem;
import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class Ops {
    private Ops() {}

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        NetData data = NetData.get(server);
        Bridges.tickWatch(server);
        tickStrikes(server, data, tick);
        if (tick % 20 != 0) return;
        try {
            SignalEnv.refreshJammers(server);
        } catch (Throwable t) {
            BlackChannelMod.LOGGER.warn("Jammer refresh failed: {}", t.toString());
        }
        try {
            Bridges.scanDroneEmp(server);
        } catch (Throwable t) {
            BlackChannelMod.LOGGER.warn("Drone EMP scan failed: {}", t.toString());
        }
        if (tick % 100 == 0) {
            try {
                Bridges.refreshForeign(server);
            } catch (Throwable t) {
                BlackChannelMod.LOGGER.warn("Foreign station scan failed: {}", t.toString());
            }
        }
        try {
            Bridges.suppressForeign(server);
        } catch (Throwable t) {
            BlackChannelMod.LOGGER.warn("Foreign suppress failed: {}", t.toString());
        }
        data.prune(tick);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(server, data, player, tick);
        tickRumor(server, data, tick);
        data.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof Menus.PhoneMenu || player.containerMenu instanceof Menus.LaptopMenu || player.containerMenu instanceof Menus.RackMenu) {
                ModNetwork.syncFull(player);
            } else {
                ModNetwork.syncStatus(player);
            }
        }
    }

    private static final ThreadLocal<String> SCOPE = ThreadLocal.withInitial(() -> "ops");

    public static void action(ServerPlayer player, String action, String arg) {
        if (player == null || action == null) return;
        if (arg == null) arg = "";
        if (arg.length() > 180) arg = arg.substring(0, 180);
        SCOPE.set(scopeFor(action));
        try {
            actionInner(player, action, arg);
        } finally {
            SCOPE.set("ops");
        }
    }

    private static void actionInner(ServerPlayer player, String action, String arg) {
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        int now = player.getServer().getTickCount();
        if (profile.bombUntil > now && !profile.airplane && locks(action)) {
            result(player, profile, "blackchannel.result.bomb_locked");
            return;
        }
        switch (action) {
            case Actions.SAY -> say(player, profile, arg);
            case Actions.DM -> dm(player, profile, arg);
            case Actions.CHAN -> chan(player, profile, arg);
            case Actions.AIR -> toggleAir(player, profile);
            case Actions.DND -> { profile.dnd = !profile.dnd; result(player, profile, profile.dnd ? "blackchannel.result.dnd_on" : "blackchannel.result.dnd_off"); }
            case Actions.RADAR -> { profile.radarHud = !profile.radarHud; result(player, profile, profile.radarHud ? "blackchannel.result.radar_on" : "blackchannel.result.radar_off"); }
            case Actions.TEAM -> Minigame.setTeam(player, arg);
            case Actions.TRACE -> trace(player, profile, arg);
            case Actions.DEANON -> deanon(player, profile, arg);
            case Actions.SWAT -> swat(player, profile, arg);
            case Actions.BOMB -> bomb(player, profile, arg);
            case Actions.UAV -> traceUav(player, profile);
            case Actions.WIPE -> wipe(player, profile, "confirm".equals(arg), false);
            case Actions.BUY -> buy(player, profile, arg);
            case Actions.BUY_LIST -> buyList(player, profile, arg);
            case Actions.SELL -> sell(player, profile, arg);
            case Actions.SELL_BUFF -> sellBuff(player, profile, arg);
            case Actions.OPTIC -> Optics.optic(player, profile, arg);
            case Actions.PEEK -> Optics.peek(player, profile, arg);
            case Actions.HACK -> hack(player, profile);
            case Actions.KILL -> killSwitch(player, rackPos(player));
            default -> { }
        }
        data.setDirty();
    }

    public static void toggleAir(ServerPlayer player, NetData.Profile profile) {
        profile.airplane = !profile.airplane;
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.MASTER, 0.5f, profile.airplane ? 0.7f : 1.6f);
        result(player, profile, profile.airplane ? "blackchannel.result.air_on" : "blackchannel.result.air_off");
    }

    public static InteractionResult plantPlayer(ServerPlayer attacker, ServerPlayer victim, ItemStack stack) {
        if (!attacker.isShiftKeyDown()) {
            attacker.displayClientMessage(Component.translatable("blackchannel.hint.tracker_sneak"), true);
            return InteractionResult.CONSUME;
        }
        if (attacker.distanceToSqr(victim) > 25) return InteractionResult.FAIL;
        NetData data = NetData.get(attacker);
        NetData.Profile self = data.profile(attacker.getUUID());
        NetData.Profile other = data.profile(victim.getUUID());
        if (other.bugs.size() >= 3) {
            result(attacker, self, "blackchannel.result.plant_fail");
            return InteractionResult.FAIL;
        }
        NetData.Bug bug = new NetData.Bug();
        bug.owner = attacker.getUUID();
        bug.ownerHandle = self.handle;
        bug.expireTick = attacker.getServer().getTickCount() + 20L * 60 * 8;
        other.bugs.add(bug);
        if (!attacker.getAbilities().instabuild) stack.shrink(1);
        float chance = other.ids || Devices.has(victim, ModContent.SCANNER.get()) ? 1f : 0.18f;
        if (attacker.getRandom().nextFloat() < chance) {
            String bearing = bearing(victim, attacker);
            victim.displayClientMessage(Component.translatable("blackchannel.chat.ids_bug", bearing), true);
            other.log("blackchannel.chat.ids_bug", bearing, attacker.getServer().getTickCount());
        }
        result(attacker, self, "blackchannel.result.plant_ok");
        data.setDirty();
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult plantBlock(ServerPlayer attacker, BlockPos pos, ItemStack stack) {
        if (!attacker.isShiftKeyDown()) {
            attacker.displayClientMessage(Component.translatable("blackchannel.hint.tracker_sneak"), true);
            return InteractionResult.CONSUME;
        }
        NetData data = NetData.get(attacker);
        NetData.Profile self = data.profile(attacker.getUUID());
        NetData.BlockBug bug = new NetData.BlockBug();
        bug.owner = attacker.getUUID();
        bug.ownerHandle = self.handle;
        bug.dim = Str.dim(attacker.level());
        bug.x = pos.getX();
        bug.y = pos.getY();
        bug.z = pos.getZ();
        bug.expireTick = attacker.getServer().getTickCount() + 20L * 60 * 10;
        data.blockBugs.add(bug);
        if (!attacker.getAbilities().instabuild) stack.shrink(1);
        result(attacker, self, "blackchannel.result.plant_ok");
        data.setDirty();
        return InteractionResult.SUCCESS;
    }

    public static void scanSelf(ServerPlayer player) {
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        if (profile.bugs.isEmpty()) {
            result(player, profile, "blackchannel.result.scan_none");
            return;
        }
        NetData.Bug bug = profile.bugs.remove(0);
        give(player, Devices.sample(bug.ownerHandle, false));
        result(player, profile, "blackchannel.result.scan_ok", bug.ownerHandle);
        ServerPlayer owner = player.getServer().getPlayerList().getPlayer(bug.owner);
        if (owner != null) owner.sendSystemMessage(Component.translatable("blackchannel.chat.bug_found"));
        data.setDirty();
    }

    public static void scanBlock(ServerPlayer player, BlockPos pos) {
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        String dim = Str.dim(player.level());
        NetData.BlockBug found = null;
        for (NetData.BlockBug bug : data.blockBugs) {
            if (!dim.equals(bug.dim)) continue;
            if (bug.x == pos.getX() && bug.y == pos.getY() && bug.z == pos.getZ()) {
                found = bug;
                break;
            }
        }
        if (found == null) {
            result(player, profile, "blackchannel.result.scan_none");
            return;
        }
        data.blockBugs.remove(found);
        give(player, Devices.sample(found.ownerHandle, false));
        result(player, profile, "blackchannel.result.scan_ok", found.ownerHandle);
        data.setDirty();
    }

    public static void identify(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker.distanceToSqr(victim) > 36) {
            attacker.displayClientMessage(Component.translatable("blackchannel.result.identify_fail"), true);
            return;
        }
        NetData data = NetData.get(attacker);
        NetData.Profile self = data.profile(attacker.getUUID());
        NetData.Profile other = data.profile(victim.getUUID());
        if (!SignalEnv.transmitting(victim, other) || other.vpn > 0 || SignalEnv.emp(other, attacker.getServer())) {
            result(attacker, self, "blackchannel.result.identify_fail");
            return;
        }
        remember(self, other);
        result(attacker, self, "blackchannel.result.identify_ok", other.handle, other.lastName);
        if (other.ids) alert(victim, attacker, "blackchannel.chat.ids_deanon");
        data.setDirty();
    }

    public static void bodyRead(ServerPlayer reader, ServerPlayer target) {
        if (reader == target) return;
        if (reader.distanceToSqr(target) > 25.0) {
            reader.displayClientMessage(Component.translatable("blackchannel.result.body_far"), true);
            return;
        }
        if (!reader.getAbilities().instabuild && Devices.battery(reader) < 8) {
            reader.displayClientMessage(Component.translatable("blackchannel.result.no_battery"), true);
            return;
        }
        Devices.drain(reader, 8);
        learn(reader, target, true);
    }

    public static void learnHit(ServerPlayer victim, ServerPlayer attacker) {
        if (victim == null || attacker == null || victim == attacker) return;
        if (!Devices.has(victim, ModContent.PHONE.get())) return;
        learn(victim, attacker, false);
    }

    public static void fileTag(ServerPlayer player, ItemStack stack) {
        if (stack == null || !stack.hasTag() || !stack.getTag().contains("Handle")) return;
        if (!Devices.has(player, ModContent.PHONE.get())) {
            player.displayClientMessage(Component.translatable("blackchannel.result.tag_need_phone"), true);
            return;
        }
        CompoundTag tag = stack.getTag();
        String handle = tag.getString("Handle");
        String name = tag.getString("Name");
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        noteHeard(profile, handle, name, true);
        rememberName(profile, handle, name);
        profile.log("blackchannel.log.tag", handle, player.getServer().getTickCount());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        data.setDirty();
        player.displayClientMessage(Component.translatable("blackchannel.result.tag_ok", handle, name.isEmpty() ? "?" : name), true);
        ModNetwork.syncFull(player);
    }

    public static void hearRadar(ServerPlayer viewer, NetData.Profile self) {
        int range = SignalEnv.radarRange(viewer, self);
        if (range <= 0 || self.airplane || SignalEnv.emp(self, viewer.getServer())) return;
        ServerLevel level = viewer.serverLevel();
        if (SignalEnv.jammed(level, viewer.blockPosition()) || SignalEnv.faraday(level, viewer.blockPosition())) return;
        long r2 = (long) range * range;
        NetData data = NetData.get(viewer);
        boolean any = false;
        for (ServerPlayer other : level.players()) {
            if (other == viewer) continue;
            NetData.Profile op = data.profile(other.getUUID());
            if (!SignalEnv.transmitting(other, op)) continue;
            if (viewer.distanceToSqr(other) > r2) continue;
            String name = op.vpn > 0 ? "" : op.lastName;
            if (!noteHeard(self, op.handle, name, false)) continue;
            any = true;
            viewer.displayClientMessage(Component.translatable(name.isEmpty() ? "blackchannel.result.net_handle" : "blackchannel.result.net_named", op.handle, name), true);
        }
        if (any) {
            data.setDirty();
            ModNetwork.syncFull(viewer);
        }
    }

    public static boolean noteHeard(NetData.Profile self, String handle, String name, boolean revealName) {
        if (self == null || handle == null || handle.isEmpty() || self.deanons.containsKey(handle)) return false;
        String shown = revealName && name != null ? name : (name == null ? "" : name);
        if (!revealName && (name == null || name.isEmpty())) shown = "";
        String prev = self.heard.get(handle);
        if (prev != null && (shown.isEmpty() || shown.equals(prev))) return false;
        self.heard.put(handle, shown);
        while (self.heard.size() > 24) {
            String first = self.heard.keySet().iterator().next();
            self.heard.remove(first);
        }
        return prev == null;
    }

    public static void noteFix(MinecraftServer server, UUID owner, String handle, String name, String dim, int x, int y, int z, int err, boolean stale, String logKey) {
        if (server == null || owner == null || handle == null || handle.isEmpty()) return;
        NetData data = NetData.get(server);
        NetData.Profile profile = data.profile(owner);
        addIntel(profile, handle, dim, x, y, z, err, server.getTickCount(), stale);
        rememberName(profile, handle, name);
        profile.log(logKey, handle, server.getTickCount());
        data.setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) ModNetwork.syncFull(online);
    }

    private static void learn(ServerPlayer reader, ServerPlayer target, boolean alwaysTell) {
        NetData data = NetData.get(reader);
        NetData.Profile self = data.profile(reader.getUUID());
        NetData.Profile other = data.profile(target.getUUID());
        if (other.lastName == null || other.lastName.isEmpty()) other.lastName = target.getGameProfile().getName();
        boolean known = self.deanons.containsKey(other.handle);
        if (!alwaysTell && known) return;
        remember(self, other);
        self.log("blackchannel.log.body", other.handle, reader.getServer().getTickCount());
        data.setDirty();
        if (alwaysTell || !known) {
            reader.displayClientMessage(Component.translatable("blackchannel.result.body_ok", other.handle, other.lastName), true);
            ModNetwork.syncFull(reader);
        }
    }

    private static void rememberName(NetData.Profile profile, String handle, String name) {
        if (handle == null || handle.isEmpty() || name == null || name.isEmpty()) return;
        profile.deanons.put(handle, name);
        while (profile.deanons.size() > 24) {
            String first = profile.deanons.keySet().iterator().next();
            profile.deanons.remove(first);
        }
    }

    public static void install(ServerPlayer player, ToolItem.Kind kind, ItemStack stack) {
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        switch (kind) {
            case VPN -> {
                if (profile.vpn >= 1) result(player, profile, "blackchannel.result.already");
                else { profile.vpn = 1; shrink(player, stack); result(player, profile, "blackchannel.result.installed"); }
            }
            case VPN2 -> { profile.vpn = 2; shrink(player, stack); result(player, profile, "blackchannel.result.installed"); }
            case IDS -> {
                if (profile.ids) result(player, profile, "blackchannel.result.already");
                else { profile.ids = true; shrink(player, stack); result(player, profile, "blackchannel.result.installed"); }
            }
            case HARDEN -> {
                if (profile.hardened) result(player, profile, "blackchannel.result.already");
                else { profile.hardened = true; shrink(player, stack); result(player, profile, "blackchannel.result.installed"); }
            }
            case SAT -> {
                profile.satCharges = Math.min(9, profile.satCharges + 3);
                shrink(player, stack);
                result(player, profile, "blackchannel.result.loaded", String.valueOf(profile.satCharges));
            }
            case CELL -> {
                Devices.charge(player, 60);
                shrink(player, stack);
                result(player, profile, "blackchannel.result.charged");
            }
            default -> player.displayClientMessage(Component.translatable("blackchannel.hint.passive"), true);
        }
        data.setDirty();
    }

    public static void deposit(ServerPlayer player, ItemStack stack) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        int n = stack.getCount();
        if (!player.getAbilities().instabuild) stack.shrink(n);
        profile.crypto = Math.min(5000, profile.crypto + n);
        result(player, profile, "blackchannel.result.deposit", String.valueOf(n), String.valueOf(profile.crypto));
    }

    public static void withdraw(ServerPlayer player) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        int n = Math.min(16, profile.crypto);
        if (n <= 0) {
            result(player, profile, "blackchannel.result.no_crypto", "1");
            return;
        }
        profile.crypto -= n;
        give(player, new ItemStack(ModContent.HASH.get(), n));
        result(player, profile, "blackchannel.result.withdraw", String.valueOf(n));
    }

    public static void killSwitch(ServerPlayer player, BlockPos pos) {
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        if (pos == null) {
            result(player, profile, "blackchannel.result.no_strike");
            return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof NodeBlockEntity be) || !SignalEnv.friendly(profile, be.owner, player.getServer())) {
            result(player, profile, "blackchannel.result.not_owner");
            return;
        }
        if (be.charge < 200 && !player.getAbilities().instabuild) {
            result(player, profile, "blackchannel.result.no_crypto", "200");
            return;
        }
        if (!player.getAbilities().instabuild) be.charge -= 200;
        int n = abortNear(data, Str.dim(player.level()), pos, 48);
        be.setChanged();
        result(player, profile, n > 0 ? "blackchannel.result.killswitch" : "blackchannel.result.no_strike");
        data.setDirty();
    }

    public static ServerLevel levelOf(MinecraftServer server, String dim) {
        if (server == null || dim == null || dim.isEmpty()) return null;
        ResourceLocation loc = ResourceLocation.tryParse(dim);
        if (loc == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
    }

    private static void tickPlayer(MinecraftServer server, NetData data, ServerPlayer player, int tick) {
        NetData.Profile profile = data.profile(player.getUUID());
        profile.lastName = player.getGameProfile().getName();
        if (!profile.welcomed) {
            profile.welcomed = true;
            player.sendSystemMessage(Component.translatable("blackchannel.chat.welcome", profile.handle));
        }
        boolean tx = SignalEnv.transmitting(player, profile);
        if (tx) {
            profile.lastDim = Str.dim(player.level());
            profile.lastX = player.blockPosition().getX();
            profile.lastY = player.blockPosition().getY();
            profile.lastZ = player.blockPosition().getZ();
            profile.lastPosTick = tick;
            int chips = (profile.vpn > 0 ? 1 : 0) + (profile.ids ? 1 : 0) + (profile.hardened ? 1 : 0);
            if (tick % 300 == 0) Devices.drain(player, 1 + chips);
            SignalEnv.Link link = SignalEnv.link(player, profile);
            if (link.mode == SignalEnv.Link.Mode.SAT && tick % 1200 == 0 && profile.satCharges > 0) profile.satCharges--;
        }
        if (tick % 20 == 0) hearRadar(player, profile);
        if (profile.bombUntil > tick && profile.floodPicsFor != profile.bombUntil) {
            profile.floodPicsFor = profile.bombUntil;
            ModNetwork.sendFlood(player);
        }
        if (profile.bombUntil > tick && !profile.airplane) {
            int soundEvery = Math.max(2, Config.FLOOD_SOUND_TICKS.get());
            int chatEvery = Math.max(5, Config.FLOOD_CHAT_TICKS.get());
            if (profile.dnd) {
                soundEvery *= 2;
                chatEvery *= 2;
            }
            if (tick % soundEvery == 0) Flood.play(player, tick);
            if (tick % 10 == 0) Flood.spam(player, tick, true);
            if (tick % chatEvery == 0) Flood.spam(player, tick + 3, false);
            if (tick % 40 == 0) Devices.drain(player, profile.dnd ? 1 : 3);
        }
        if (tick % 600 == 0 && Devices.hasLaptop(player)) sniff(player, profile);
    }

    private static void sniff(ServerPlayer player, NetData.Profile profile) {
        ServerPlayer nearest = null;
        double best = 12 * 12;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player) continue;
            double d = player.distanceToSqr(other);
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        if (nearest == null) return;
        NetData.Profile other = NetData.get(player).profile(nearest.getUUID());
        if (!SignalEnv.transmitting(nearest, other)) return;
        if (hasSample(player, other.handle)) return;
        give(player, Devices.sample(other.handle, other.vpn > 0));
        player.displayClientMessage(Component.translatable("blackchannel.result.sniff"), true);
    }

    private static void say(ServerPlayer player, NetData.Profile profile, String text) {
        text = Str.clean(text);
        if (text.isEmpty()) return;
        if (!radio(player, profile, false)) return;
        if (!chatRoom(player, profile)) return;
        if (!rate(player, profile)) return;
        NetData.ChatLine line = baseLine(player, profile, text);
        line.channel = "dm".equals(profile.channel) ? dmId(profile.handle, profile.dm) : profile.channel;
        if ("dm".equals(profile.channel) && data(player).byHandle(profile.dm) == null) {
            result(player, profile, "blackchannel.result.bad_handle");
            return;
        }
        data(player).addChat(line);
        profile.result("");
        ModNetwork.syncFull(player);
    }

    private static void dm(ServerPlayer player, NetData.Profile profile, String arg) {
        int sp = arg.indexOf(' ');
        String handle = Str.norm(sp < 0 ? arg : arg.substring(0, sp));
        String text = Str.clean(sp < 0 ? "" : arg.substring(sp + 1));
        if (handle.isEmpty() || text.isEmpty()) {
            result(player, profile, "blackchannel.result.bad_handle");
            return;
        }
        if (data(player).byHandle(handle) == null) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        profile.channel = "dm";
        profile.dm = handle;
        if (!radio(player, profile, true)) return;
        if (!rate(player, profile)) return;
        NetData.ChatLine line = baseLine(player, profile, text);
        line.channel = dmId(profile.handle, handle);
        data(player).addChat(line);
        profile.result("");
        ModNetwork.syncFull(player);
    }

    private static void chan(ServerPlayer player, NetData.Profile profile, String arg) {
        if (arg.startsWith("dm:")) {
            profile.channel = "dm";
            profile.dm = Str.norm(arg.substring(3));
        } else if ("local".equals(arg) || "darknet".equals(arg)) {
            profile.channel = arg;
        }
        profile.result("");
        ModNetwork.syncFull(player);
    }

    private static void trace(ServerPlayer player, NetData.Profile profile, String arg) {
        if (!gate(player, profile, true, true)) return;
        if (!cd(player, profile, profile.cdTrace)) return;
        String handle = Str.norm(arg);
        NetData.Profile target = data(player).byHandle(handle);
        if (target == null) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        if (!pay(player, profile, Config.TRACE_COST.get())) return;
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(target.id);
        int now = player.getServer().getTickCount();
        boolean live = online != null && SignalEnv.transmitting(online, target);
        boolean memory = !target.lastDim.isEmpty() && now - target.lastPosTick < Config.MEMORY.get() * 20;
        if (!live && !memory) {
            profile.cdTrace = now + Config.TRACE_CD.get() * 20;
            result(player, profile, "blackchannel.result.no_carrier");
            if (online != null && target.ids) alert(online, player, "blackchannel.chat.ids_trace");
            return;
        }
        boolean exploit = takeOptional(player, ModContent.EXPLOIT.get());
        RandomSource random = player.getRandom();
        int failPct = target.vpn >= 2 ? Config.TRACE_FAIL_DEEP.get() : target.vpn == 1 ? Config.TRACE_FAIL_VPN.get() : Config.TRACE_FAIL.get();
        double fail = failPct / 100.0;
        if (exploit) fail *= 0.4;
        if (!free(player) && random.nextFloat() < fail) {
            profile.cdTrace = now + Config.TRACE_CD.get() * 20;
            result(player, profile, "blackchannel.result.trace_fail");
            if (online != null) alert(online, player, "blackchannel.chat.ids_trace");
            return;
        }
        double mul = 1.0;
        if (target.vpn >= 2) mul = Config.TRACE_VPN_DEEP.get();
        else if (target.vpn == 1) mul = Config.TRACE_VPN_MUL.get();
        int err = (int) Math.round(Config.TRACE_BASE.get() * mul);
        if (err < 0) err = 0;
        if (SignalEnv.dishNear(player, profile, 128)) err = Math.max(3, (int) (err * 0.55));
        if (exploit) err = Math.max(2, (int) (err * 0.65));
        boolean stale = !live;
        if (stale) err += Config.TRACE_STALE.get();
        int x = live ? online.blockPosition().getX() : target.lastX;
        int y = live ? online.blockPosition().getY() : target.lastY;
        int z = live ? online.blockPosition().getZ() : target.lastZ;
        String dim = live ? Str.dim(online.level()) : target.lastDim;
        x += random.nextInt(err * 2 + 1) - err;
        z += random.nextInt(err * 2 + 1) - err;
        addIntel(profile, handle, dim, x, y, z, err, now, stale);
        profile.cdTrace = now + Config.TRACE_CD.get() * 20;
        result(player, profile, stale ? "blackchannel.result.trace_stale" : "blackchannel.result.trace_ok",
                handle, Str.dimShort(dim), String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(err));
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.7f, 1.4f);
        if (online != null && (target.ids || random.nextFloat() < 0.4f)) alert(online, player, "blackchannel.chat.ids_trace");
        if (Config.LOG_OPS.get()) BlackChannelMod.LOGGER.info("TRACE {} -> {} ±{}", player.getGameProfile().getName(), handle, err);
    }

    private static void deanon(ServerPlayer player, NetData.Profile profile, String arg) {
        if (!gate(player, profile, true, true)) return;
        if (!cd(player, profile, profile.cdDeanon)) return;
        String handle = Str.norm(arg);
        NetData.Profile target = data(player).byHandle(handle);
        if (target == null) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        if (!pay(player, profile, Config.DEANON_COST.get())) return;
        boolean zero = takeOptional(player, ModContent.ZERO.get());
        boolean sample = !Devices.takeSample(player, handle, zero, zero).isEmpty() || hasSample(player, handle);
        boolean traced = false;
        for (NetData.Intel intel : profile.intel) if (handle.equals(intel.handle)) traced = true;
        if (target.vpn > 0 && !zero) {
            profile.cdDeanon = player.getServer().getTickCount() + Config.DEANON_CD.get() * 20;
            result(player, profile, "blackchannel.result.vpn_blocks");
            return;
        }
        if (!sample && !traced && !zero) {
            result(player, profile, "blackchannel.result.deanon_need");
            return;
        }
        remember(profile, target);
        profile.cdDeanon = player.getServer().getTickCount() + Config.DEANON_CD.get() * 20;
        result(player, profile, "blackchannel.result.deanon_ok", handle, target.lastName.isEmpty() ? "?" : target.lastName);
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(target.id);
        if (online != null && target.ids) alert(online, player, "blackchannel.chat.ids_deanon");
    }

    private static void swat(ServerPlayer player, NetData.Profile profile, String arg) {
        if (!Config.SWAT_ENABLED.get()) {
            result(player, profile, "blackchannel.result.swat_disabled");
            return;
        }
        if (!gate(player, profile, true, true)) return;
        if (!cd(player, profile, profile.cdSwat)) return;
        int id;
        try {
            id = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            result(player, profile, "blackchannel.result.swat_need_intel");
            return;
        }
        NetData.Intel intel = null;
        for (NetData.Intel row : profile.intel) if (row.id == id) intel = row;
        if (intel == null) {
            result(player, profile, "blackchannel.result.swat_need_intel");
            return;
        }
        boolean contract = free(player) || Devices.count(player, ModContent.CONTRACT.get()) > 0;
        int cost = contract ? Config.SWAT_CONTRACT_COST.get() : Config.SWAT_COST.get();
        if (!pay(player, profile, cost)) return;
        if (contract && !free(player)) Devices.consume(player, ModContent.CONTRACT.get(), 1);
        int now = player.getServer().getTickCount();
        int age = (now - intel.tick) / 20;
        boolean stale = intel.stale || age > Config.INTEL_STALE.get();
        int x = intel.x;
        int z = intel.z;
        if (stale) {
            int extra = 36;
            x += player.getRandom().nextInt(extra * 2 + 1) - extra;
            z += player.getRandom().nextInt(extra * 2 + 1) - extra;
        }
        NetData.Strike strike = new NetData.Strike();
        strike.id = data(player).strikeSeq++;
        strike.attacker = player.getUUID();
        NetData.Profile target = data(player).byHandle(intel.handle);
        strike.target = target == null ? new UUID(0, 0) : target.id;
        strike.handle = intel.handle;
        strike.dim = intel.dim;
        strike.x = x;
        strike.y = intel.y;
        strike.z = z;
        strike.ticksLeft = Config.SWAT_DELAY.get() * 20;
        data(player).strikes.add(strike);
        profile.cdSwat = now + Config.SWAT_CD.get() * 20;
        result(player, profile, "blackchannel.result.swat_ok", String.valueOf(Config.SWAT_DELAY.get()), String.valueOf(x), String.valueOf(z));
        if (target != null) {
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(target.id);
            if (online != null) {
                online.sendSystemMessage(Component.translatable("blackchannel.chat.strike_target", Config.SWAT_DELAY.get(), x, intel.y, z, Str.dimShort(intel.dim)));
                online.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1f, 0.5f);
            }
        }
        if (Config.LOG_OPS.get()) {
            BlackChannelMod.LOGGER.info("SWAT {} -> {} at {} {} {} {}", player.getGameProfile().getName(), intel.handle, intel.dim, x, intel.y, z);
        }
    }

    private static void bomb(ServerPlayer player, NetData.Profile profile, String arg) {
        if (!Config.BOMB_ENABLED.get()) {
            result(player, profile, "blackchannel.result.bomb_disabled");
            return;
        }
        if (!gate(player, profile, true, true)) return;
        if (!cd(player, profile, profile.cdBomb)) return;
        NetData.Profile target = data(player).byHandle(Str.norm(arg));
        if (target == null) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(target.id);
        if (online == null || !SignalEnv.transmitting(online, target)) {
            result(player, profile, "blackchannel.result.no_carrier");
            return;
        }
        if (!pay(player, profile, Config.BOMB_COST.get())) return;
        int dur = target.dnd ? 12 : 25;
        if (target.vpn > 0) dur = Math.max(6, dur - 6);
        int now = player.getServer().getTickCount();
        target.bombUntil = now + dur * 20;
        target.toast("blackchannel.spam.1", "");
        profile.cdBomb = now + Config.BOMB_CD.get() * 20;
        result(player, profile, "blackchannel.result.bomb_ok", target.handle, String.valueOf(dur));
        if (target.ids) alert(online, player, "blackchannel.chat.ids_bomb");
        else online.displayClientMessage(Component.translatable("blackchannel.chat.ids_bomb_soft"), true);
    }

    private static void traceUav(ServerPlayer player, NetData.Profile profile) {
        if (!gate(player, profile, true, false)) return;
        if (!cd(player, profile, profile.cdTrace)) return;
        Entity drone = Bridges.nearestDrone(player, Config.UAV_RANGE.get());
        if (drone == null) {
            result(player, profile, "blackchannel.result.drone_none");
            return;
        }
        UUID controller = Bridges.droneController(drone);
        ServerPlayer op = controller == null ? null : player.getServer().getPlayerList().getPlayer(controller);
        if (op == null) {
            result(player, profile, "blackchannel.result.drone_blind");
            return;
        }
        if (!pay(player, profile, Config.UAV_COST.get())) return;
        NetData.Profile target = data(player).profile(op.getUUID());
        int err = Config.UAV_ERROR.get() + player.getRandom().nextInt(12);
        int x = op.blockPosition().getX() + player.getRandom().nextInt(err * 2 + 1) - err;
        int z = op.blockPosition().getZ() + player.getRandom().nextInt(err * 2 + 1) - err;
        int now = player.getServer().getTickCount();
        addIntel(profile, target.handle, Str.dim(op.level()), x, op.blockPosition().getY(), z, err, now, false);
        profile.cdTrace = now + Config.TRACE_CD.get() * 20;
        result(player, profile, "blackchannel.result.drone_ok", target.handle, String.valueOf(x), String.valueOf(z), String.valueOf(err));
        if (target.ids) alert(op, player, "blackchannel.chat.ids_trace");
    }

    public static void wipeFromSim(ServerPlayer player) {
        wipe(player, NetData.get(player).profile(player.getUUID()), true, true);
    }

    private static void wipe(ServerPlayer player, NetData.Profile profile, boolean confirmed, boolean fromSim) {
        if (!confirmed && !free(player)) {
            result(player, profile, "blackchannel.result.wipe_need_confirm");
            return;
        }
        boolean sim = fromSim || Devices.count(player, ModContent.SIM.get()) > 0;
        if (!free(player) && !sim && profile.crypto < 24) {
            result(player, profile, "blackchannel.result.need_sim");
            return;
        }
        if (!free(player)) {
            if (sim) Devices.consume(player, ModContent.SIM.get(), 1);
            else profile.crypto -= 24;
        }
        profile.handle = data(player).uniqueHandle(player.getRandom());
        result(player, profile, "blackchannel.result.wiped", profile.handle);
    }

    private static void buy(ServerPlayer player, NetData.Profile profile, String id) {
        Catalog.Entry entry = Catalog.byId(id.trim());
        if (entry == null) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        if (!pay(player, profile, entry.price())) return;
        give(player, new ItemStack(entry.item().get(), entry.count()));
        result(player, profile, "blackchannel.result.bought", id);
    }

    private static void buyList(ServerPlayer player, NetData.Profile profile, String arg) {
        int id;
        try {
            id = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        NetData data = data(player);
        NetData.Listing listing = null;
        for (NetData.Listing row : data.listings) if (row.id == id) listing = row;
        if (listing == null) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        if (!pay(player, profile, listing.price)) return;
        NetData.Profile seller = data.profiles.get(listing.seller);
        if (seller != null) seller.crypto = Math.min(5000, seller.crypto + listing.price);
        if (listing.intel) {
            addIntel(profile, listing.handle, listing.dim, listing.x, listing.y, listing.z, listing.err, player.getServer().getTickCount(), listing.stale);
        } else {
            ItemStack stack = ItemStack.of(listing.stack);
            if (!stack.isEmpty()) give(player, stack);
        }
        data.listings.remove(listing);
        result(player, profile, "blackchannel.result.bought", String.valueOf(id));
    }

    private static void sell(ServerPlayer player, NetData.Profile profile, String arg) {
        String[] parts = arg.trim().split(" ");
        if (parts.length < 2) {
            result(player, profile, "blackchannel.result.bad_handle");
            return;
        }
        NetData data = data(player);
        if (data.listings.size() >= 32) {
            result(player, profile, "blackchannel.result.no_room");
            return;
        }
        try {
            if ("item".equals(parts[0])) {
                int price = clampPrice(Integer.parseInt(parts[1]));
                if (!(player.containerMenu instanceof Menus.LaptopMenu menu)) {
                    result(player, profile, "blackchannel.result.need_slot");
                    return;
                }
                ItemStack held = menu.sale.getItem(0);
                if (held.isEmpty() || Devices.isDevice(held)) {
                    result(player, profile, "blackchannel.result.need_slot");
                    return;
                }
                ItemStack stack = held.copy();
                if (!player.getAbilities().instabuild) menu.sale.setItem(0, ItemStack.EMPTY);
                NetData.Listing listing = newListing(data, player, profile, price);
                listing.stack = stack.save(new CompoundTag());
                data.listings.add(listing);
                result(player, profile, "blackchannel.result.sold", String.valueOf(price));
            } else if ("intel".equals(parts[0]) && parts.length >= 3) {
                int intelId = Integer.parseInt(parts[1]);
                int price = clampPrice(Integer.parseInt(parts[2]));
                NetData.Intel intel = null;
                for (NetData.Intel row : profile.intel) if (row.id == intelId) intel = row;
                if (intel == null) {
                    result(player, profile, "blackchannel.result.swat_need_intel");
                    return;
                }
                NetData.Listing listing = newListing(data, player, profile, price);
                listing.intel = true;
                listing.handle = intel.handle;
                listing.dim = intel.dim;
                listing.x = intel.x;
                listing.y = intel.y;
                listing.z = intel.z;
                listing.err = intel.err;
                listing.stale = intel.stale;
                profile.intel.remove(intel);
                data.listings.add(listing);
                result(player, profile, "blackchannel.result.sold", String.valueOf(price));
            }
        } catch (NumberFormatException e) {
            result(player, profile, "blackchannel.result.bad_handle");
        }
    }

    private static void hack(ServerPlayer player, NetData.Profile profile) {
        if (!(player.containerMenu instanceof Menus.RackMenu menu)) {
            result(player, profile, "blackchannel.result.no_target");
            return;
        }
        if (!(player.level().getBlockEntity(menu.pos) instanceof NodeBlockEntity be) || be.owner == null || be.owner.equals(player.getUUID())) {
            result(player, profile, "blackchannel.result.not_owner");
            return;
        }
        if (!free(player) && !Devices.consume(player, ModContent.EXPLOIT.get(), 1)) {
            result(player, profile, "blackchannel.result.deanon_need");
            return;
        }
        be.charge = 0;
        be.setChanged();
        NetData.Profile owner = data(player).profile(be.owner);
        give(player, Devices.sample(owner.handle, false));
        result(player, profile, "blackchannel.result.hack_ok", owner.handle);
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(be.owner);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("blackchannel.chat.ids_hack"));
            owner.log("blackchannel.chat.ids_hack", bearing(online, player), player.getServer().getTickCount());
        }
    }

    private static void tickStrikes(MinecraftServer server, NetData data, int tick) {
        var it = data.strikes.iterator();
        while (it.hasNext()) {
            NetData.Strike strike = it.next();
            strike.ticksLeft--;
            ServerLevel level = levelOf(server, strike.dim);
            if (strike.ticksLeft > 0) {
                if (strike.ticksLeft % 20 == 0) warnStrike(server, strike);
                if (level != null && strike.ticksLeft < 300 && strike.ticksLeft % 10 == 0) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, strike.x + 0.5, strike.y + 1.2, strike.z + 0.5, 6, 0.2, 0.4, 0.2, 0.01);
                }
                if (level != null && (strike.ticksLeft % 100 == 0 || (strike.ticksLeft < 200 && strike.ticksLeft % 20 == 0))) {
                    level.playSound(null, strike.x, strike.y, strike.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.3f, 0.55f);
                }
                continue;
            }
            it.remove();
            data.setDirty();
            if (level == null) continue;
            BlockPos aim = new BlockPos(strike.x, strike.y, strike.z);
            if (SignalEnv.jammed(level, aim) || SignalEnv.faraday(level, aim)) {
                announceAbort(server, strike);
                continue;
            }
            float power = Config.SWAT_POWER.get().floatValue();
            if (power > 0f) {
                level.getChunk(strike.x >> 4, strike.z >> 4);
                level.explode(null, strike.x + 0.5, strike.y + 0.5, strike.z + 0.5, power, Config.SWAT_FIRE.get(),
                        Config.SWAT_BREAKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getUUID().equals(strike.attacker) || player.getUUID().equals(strike.target) || near(player, strike, 64)) {
                    player.sendSystemMessage(Component.translatable("blackchannel.chat.strike_impact", strike.x, strike.y, strike.z));
                }
            }
        }
    }

    private static void warnStrike(MinecraftServer server, NetData.Strike strike) {
        int sec = Math.max(0, strike.ticksLeft / 20);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean target = player.getUUID().equals(strike.target);
            boolean close = near(player, strike, 48);
            if (!target && !close) continue;
            player.displayClientMessage(Component.translatable(target ? "blackchannel.hud.strike" : "blackchannel.chat.strike_near", sec, strike.x, strike.z), true);
        }
    }

    private static void announceAbort(MinecraftServer server, NetData.Strike strike) {
        ServerPlayer a = server.getPlayerList().getPlayer(strike.attacker);
        ServerPlayer t = server.getPlayerList().getPlayer(strike.target);
        if (a != null) a.sendSystemMessage(Component.translatable("blackchannel.chat.strike_abort"));
        if (t != null) t.sendSystemMessage(Component.translatable("blackchannel.chat.strike_abort"));
    }

    private static int abortNear(NetData data, String dim, BlockPos pos, int radius) {
        int n = 0;
        long r2 = (long) radius * radius;
        var it = data.strikes.iterator();
        while (it.hasNext()) {
            NetData.Strike strike = it.next();
            long dx = strike.x - pos.getX();
            long dy = strike.y - pos.getY();
            long dz = strike.z - pos.getZ();
            if (dim.equals(strike.dim) && dx * dx + dy * dy + dz * dz <= r2) {
                it.remove();
                n++;
            }
        }
        return n;
    }

    private static void tickRumor(MinecraftServer server, NetData data, int tick) {
        if (!Config.RUMOR_ENABLED.get()) return;
        if (data.nextRumorTick == 0) data.nextRumorTick = tick + 20 * 60 * 8;
        if (tick < data.nextRumorTick) return;
        data.nextRumorTick = tick + 20L * 60 * 8;
        ServerPlayer pick = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SignalEnv.transmitting(player, data.profile(player.getUUID()))) pick = player;
        }
        if (pick == null) return;
        int x = pick.blockPosition().getX() + pick.getRandom().nextInt(401) - 200;
        int z = pick.blockPosition().getZ() + pick.getRandom().nextInt(401) - 200;
        NetData.ChatLine line = new NetData.ChatLine();
        line.channel = "darknet";
        line.from = "relay";
        line.text = "unconfirmed " + x + " " + z;
        line.tick = tick;
        line.dim = Str.dim(pick.level());
        data.addChat(line);
    }

    private static void addIntel(NetData.Profile profile, String handle, String dim, int x, int y, int z, int err, int tick, boolean stale) {
        NetData.Intel intel = new NetData.Intel();
        intel.id = profile.intelSeq++;
        intel.handle = handle;
        intel.dim = dim;
        intel.x = x;
        intel.y = y;
        intel.z = z;
        intel.err = err;
        intel.tick = tick;
        intel.stale = stale;
        profile.intel.add(intel);
        while (profile.intel.size() > 12) profile.intel.remove(0);
    }

    private static void remember(NetData.Profile self, NetData.Profile target) {
        self.deanons.put(target.handle, target.lastName.isEmpty() ? "?" : target.lastName);
        while (self.deanons.size() > 24) {
            String first = self.deanons.keySet().iterator().next();
            self.deanons.remove(first);
        }
    }

    static void alert(ServerPlayer target, ServerPlayer attacker, String key) {
        String dir = bearing(target, attacker);
        target.sendSystemMessage(Component.translatable(key, dir));
        target.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.8f, 0.6f);
        NetData.Profile profile = NetData.get(target).profile(target.getUUID());
        profile.toast(key, dir);
        profile.log(key, dir, target.getServer().getTickCount());
    }

    public static String bearing(ServerPlayer from, ServerPlayer to) {
        if (from == null || to == null) return "?";
        if (!from.level().dimension().equals(to.level().dimension())) return "other";
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int idx = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return names[idx];
    }

    static boolean gate(ServerPlayer player, NetData.Profile profile, boolean laptop, boolean uplink) {
        if (player.isSpectator()) {
            result(player, profile, "blackchannel.result.no_device");
            return false;
        }
        if (laptop && !Devices.hasLaptop(player) && !free(player)) {
            result(player, profile, "blackchannel.result.no_device");
            return false;
        }
        if (!free(player) && Devices.battery(player) <= 0) {
            result(player, profile, "blackchannel.result.no_battery");
            return false;
        }
        if (profile.airplane) {
            result(player, profile, "blackchannel.result.airplane");
            return false;
        }
        if (SignalEnv.emp(profile, player.getServer())) {
            result(player, profile, "blackchannel.result.emp");
            return false;
        }
        if (SignalEnv.jammed(player.serverLevel(), player.blockPosition())) {
            result(player, profile, "blackchannel.result.jammed");
            return false;
        }
        if (SignalEnv.faraday(player.serverLevel(), player.blockPosition())) {
            result(player, profile, "blackchannel.result.faraday");
            return false;
        }
        if (uplink && !free(player) && SignalEnv.link(player, profile).mode == SignalEnv.Link.Mode.NONE) {
            result(player, profile, "blackchannel.result.no_uplink");
            return false;
        }
        return true;
    }

    private static boolean radio(ServerPlayer player, NetData.Profile profile, boolean uplink) {
        if (profile.airplane) {
            result(player, profile, "blackchannel.result.airplane");
            return false;
        }
        if (SignalEnv.emp(profile, player.getServer())) {
            result(player, profile, "blackchannel.result.emp");
            return false;
        }
        if (!Devices.hasDevice(player) || Devices.battery(player) <= 0) {
            result(player, profile, "blackchannel.result.no_battery");
            return false;
        }
        if (SignalEnv.jammed(player.serverLevel(), player.blockPosition()) || SignalEnv.faraday(player.serverLevel(), player.blockPosition())) {
            result(player, profile, "blackchannel.result.jammed");
            return false;
        }
        if (!SignalEnv.transmitting(player, profile)) {
            result(player, profile, "blackchannel.result.no_carrier");
            return false;
        }
        if (uplink && SignalEnv.link(player, profile).mode == SignalEnv.Link.Mode.NONE && !free(player)) {
            result(player, profile, "blackchannel.result.no_uplink");
            return false;
        }
        return true;
    }

    private static boolean chatRoom(ServerPlayer player, NetData.Profile profile) {
        if ("local".equals(profile.channel)) return true;
        SignalEnv.Link link = SignalEnv.link(player, profile);
        if (link.mode == SignalEnv.Link.Mode.NONE && !free(player)) {
            result(player, profile, "blackchannel.result.no_uplink");
            return false;
        }
        if (link.mode == SignalEnv.Link.Mode.SAT && !free(player)) {
            if (profile.satCharges <= 0) {
                result(player, profile, "blackchannel.result.no_uplink");
                return false;
            }
            profile.satCharges--;
        }
        return true;
    }

    private static boolean rate(ServerPlayer player, NetData.Profile profile) {
        int now = player.getServer().getTickCount();
        if (now - profile.chatWindow > 200) {
            profile.chatWindow = now;
            profile.chatCount = 0;
        }
        if (profile.chatCount >= 6 && !free(player)) {
            result(player, profile, "blackchannel.result.muted");
            return false;
        }
        profile.chatCount++;
        return true;
    }

    static boolean cd(ServerPlayer player, NetData.Profile profile, int ready) {
        if (free(player)) return true;
        int now = player.getServer().getTickCount();
        if (now < ready) {
            result(player, profile, "blackchannel.result.cooldown", String.valueOf((ready - now + 19) / 20));
            return false;
        }
        return true;
    }

    static boolean pay(ServerPlayer player, NetData.Profile profile, int cost) {
        if (free(player)) return true;
        if (profile.crypto < cost) {
            result(player, profile, "blackchannel.result.no_crypto", String.valueOf(cost));
            return false;
        }
        profile.crypto -= cost;
        return true;
    }

    private static boolean takeOptional(ServerPlayer player, net.minecraft.world.item.Item item) {
        if (free(player)) return Devices.has(player, item);
        if (Devices.count(player, item) <= 0) return false;
        Devices.consume(player, item, 1);
        return true;
    }

    private static boolean free(ServerPlayer player) {
        return player.getAbilities().instabuild;
    }

    private static boolean locks(String action) {
        return !Actions.AIR.equals(action) && !Actions.DND.equals(action) && !Actions.KILL.equals(action) && !Actions.TEAM.equals(action);
    }

    private static boolean near(ServerPlayer player, NetData.Strike strike, int radius) {
        if (!Str.dim(player.level()).equals(strike.dim)) return false;
        long dx = player.blockPosition().getX() - strike.x;
        long dz = player.blockPosition().getZ() - strike.z;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static boolean hasSample(ServerPlayer player, String handle) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModContent.SAMPLE.get()) && handle.equals(Devices.sampleHandle(stack))) return true;
        }
        return false;
    }

    private static ItemStack findSample(ServerPlayer player, String handle) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModContent.SAMPLE.get())) continue;
            if (handle == null || handle.equals(Devices.sampleHandle(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void shrink(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild && stack != null) stack.shrink(1);
    }

    private static NetData data(ServerPlayer player) {
        return NetData.get(player);
    }

    private static NetData.ChatLine baseLine(ServerPlayer player, NetData.Profile profile, String text) {
        NetData.ChatLine line = new NetData.ChatLine();
        line.from = profile.handle;
        line.text = text;
        line.tick = player.getServer().getTickCount();
        line.encrypted = profile.vpn > 0;
        line.dim = Str.dim(player.level());
        line.x = player.blockPosition().getX();
        line.y = player.blockPosition().getY();
        line.z = player.blockPosition().getZ();
        return line;
    }

    private static String dmId(String a, String b) {
        if (a.compareTo(b) <= 0) return "dm:" + a + ":" + b;
        return "dm:" + b + ":" + a;
    }

    private static NetData.Listing newListing(NetData data, ServerPlayer player, NetData.Profile profile, int price) {
        NetData.Listing listing = new NetData.Listing();
        listing.id = data.listingSeq++;
        listing.seller = player.getUUID();
        listing.sellerHandle = profile.handle;
        listing.price = price;
        return listing;
    }

    private static void sellBuff(ServerPlayer player, NetData.Profile profile, String arg) {
        int price;
        String label;
        if ("vpn".equals(arg)) {
            if (profile.vpn <= 0) {
                result(player, profile, "blackchannel.result.no_buff");
                return;
            }
            boolean deep = profile.vpn >= 2;
            price = halfCatalog(deep ? "deep" : "vpn");
            label = deep ? "VPN2" : "VPN";
        } else if ("ids".equals(arg)) {
            if (!profile.ids) {
                result(player, profile, "blackchannel.result.no_buff");
                return;
            }
            price = halfCatalog("ids");
            label = "IDS";
        } else if ("harden".equals(arg)) {
            if (!profile.hardened) {
                result(player, profile, "blackchannel.result.no_buff");
                return;
            }
            price = halfCatalog("harden");
            label = "EMP";
        } else {
            result(player, profile, "blackchannel.result.no_buff");
            return;
        }
        if (profile.crypto + price > 5000) {
            result(player, profile, "blackchannel.result.wallet_full");
            return;
        }
        if ("vpn".equals(arg)) profile.vpn = 0;
        else if ("ids".equals(arg)) profile.ids = false;
        else profile.hardened = false;
        profile.crypto += price;
        result(player, profile, "blackchannel.result.sold_buff", label, String.valueOf(price));
    }

    private static int halfCatalog(String id) {
        Catalog.Entry entry = Catalog.byId(id);
        return entry == null ? 0 : entry.price() / 2;
    }

    private static int clampPrice(int price) {
        return Math.max(1, Math.min(5000, price));
    }

    private static BlockPos rackPos(ServerPlayer player) {
        if (player.containerMenu instanceof Menus.RackMenu menu) return menu.pos;
        return null;
    }

    private static String scopeFor(String action) {
        if (Actions.SAY.equals(action) || Actions.DM.equals(action) || Actions.CHAN.equals(action)) return "chat";
        if (Actions.AIR.equals(action) || Actions.DND.equals(action) || Actions.TEAM.equals(action) || Actions.RADAR.equals(action)) return "gear";
        if (Actions.BUY.equals(action) || Actions.BUY_LIST.equals(action)) return "market";
        if (Actions.SELL.equals(action)) return "sell";
        if (Actions.SELL_BUFF.equals(action)) return "gear";
        if (Actions.OPTIC.equals(action)) return "area";
        if (Actions.PEEK.equals(action)) return "bag";
        if (Actions.HACK.equals(action) || Actions.KILL.equals(action)) return "rack";
        return "ops";
    }

    static void result(ServerPlayer player, NetData.Profile profile, String key, String... args) {
        profile.resultScope = SCOPE.get();
        profile.result(key, args);
        ModNetwork.syncFull(player);
    }
}
