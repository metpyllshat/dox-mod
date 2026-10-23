package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.block.NodeBlock;
import dev.blackchannel.block.NodeBlockEntity;
import dev.blackchannel.data.NetData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SignalEnv {
    public static final List<Sphere> JAMMERS = new ArrayList<>();

    private SignalEnv() {}

    public static final class Sphere {
        public final String dim;
        public final int x, y, z, r;

        public Sphere(String dim, int x, int y, int z, int r) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
        }
    }

    public static final class Link {
        public enum Mode { NONE, RACK, DISH, SAT }
        public Mode mode = Mode.NONE;
        public BlockPos rack;
        public boolean dish;
    }

    public static final class Blip {
        public final int dx, dz, dist;
        public final String kind, label;

        public Blip(int dx, int dz, int dist, String kind, String label) {
            this.dx = dx;
            this.dz = dz;
            this.dist = dist;
            this.kind = kind;
            this.label = label;
        }
    }

    public static final class Track {
        public final String label, dim;
        public final int x, y, z;
        public final boolean live;

        public Track(String label, String dim, int x, int y, int z, boolean live) {
            this.label = label;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.live = live;
        }
    }

    public static void refreshJammers(MinecraftServer server) {
        JAMMERS.clear();
        for (ServerLevel level : server.getAllLevels()) {
            int range = Config.JAMMER_RANGE.get();
            NodeBlockEntity.each(level, be -> {
                if (be.kind() == NodeBlock.Kind.JAMMER && be.powered()) {
                    BlockPos pos = be.getBlockPos();
                    JAMMERS.add(new Sphere(Str.dim(level), pos.getX(), pos.getY(), pos.getZ(), range));
                }
            });
        }
        if (Config.HOOK_JAMMER.get()) Bridges.collectEwJammers(server, JAMMERS);
    }

    public static boolean jammed(ServerLevel level, BlockPos pos) {
        String dim = Str.dim(level);
        for (Sphere sphere : JAMMERS) {
            if (!sphere.dim.equals(dim)) continue;
            double dx = pos.getX() - sphere.x;
            double dy = pos.getY() - sphere.y;
            double dz = pos.getZ() - sphere.z;
            if (dx * dx + dy * dy + dz * dz <= (double) sphere.r * sphere.r) return true;
        }
        return false;
    }

    public static boolean faraday(ServerLevel level, BlockPos pos) {
        int range = Config.FARADAY_RANGE.get();
        long r2 = (long) range * range;
        boolean[] hit = {false};
        NodeBlockEntity.each(level, be -> {
            if (be.kind() == NodeBlock.Kind.FARADAY && be.powered() && be.getBlockPos().distSqr(pos) <= r2) hit[0] = true;
        });
        return hit[0];
    }

    public static boolean emp(NetData.Profile profile, MinecraftServer server) {
        return server != null && server.getTickCount() < profile.empUntil;
    }

    public static boolean friendly(NetData.Profile self, UUID owner, MinecraftServer server) {
        if (owner == null) return false;
        if (self.id.equals(owner)) return true;
        NetData.Profile other = NetData.get(server).profiles.get(owner);
        return other != null && !self.crew.isEmpty() && self.crew.equals(other.crew);
    }

    public static boolean transmitting(ServerPlayer player, NetData.Profile profile) {
        if (profile.airplane || emp(profile, player.getServer())) return false;
        if (!Devices.hasDevice(player) || Devices.battery(player) <= 0) return false;
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        return !jammed(level, pos) && !faraday(level, pos);
    }

    public static Link link(ServerPlayer player, NetData.Profile profile) {
        Link link = new Link();
        if (profile.airplane || emp(profile, player.getServer()) || !Devices.hasDevice(player) || Devices.battery(player) <= 0) {
            return link;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (jammed(level, pos) || faraday(level, pos)) return link;
        int uplink = Config.UPLINK_RANGE.get();
        long uplink2 = (long) uplink * uplink;
        int dishRange = Config.DISH_RANGE.get();
        long dish2 = (long) dishRange * dishRange;
        NodeBlockEntity.each(level, be -> {
            if (be.kind() != NodeBlock.Kind.RACK || !be.powered() || !friendly(profile, be.owner, player.getServer())) return;
            if (be.getBlockPos().distSqr(pos) <= uplink2) {
                link.mode = Link.Mode.RACK;
                link.rack = be.getBlockPos();
            }
        });
        if (link.mode == Link.Mode.NONE) {
            NodeBlockEntity.each(level, dish -> {
                if (dish.kind() != NodeBlock.Kind.DISH || !dish.powered() || !friendly(profile, dish.owner, player.getServer())) return;
                if (dish.getBlockPos().distSqr(pos) > dish2) return;
                NodeBlockEntity.each(level, rack -> {
                    if (rack.kind() == NodeBlock.Kind.RACK && rack.powered() && dish.owner != null && dish.owner.equals(rack.owner)
                            && rack.getBlockPos().distSqr(dish.getBlockPos()) <= 32L * 32L) {
                        link.mode = Link.Mode.DISH;
                        link.rack = rack.getBlockPos();
                        link.dish = true;
                    }
                });
            });
        }
        if (link.mode == Link.Mode.NONE && profile.satCharges > 0) link.mode = Link.Mode.SAT;
        link.dish = link.dish || dishNear(player, profile, 96);
        return link;
    }

    public static boolean dishNear(ServerPlayer player, NetData.Profile profile, int range) {
        long r2 = (long) range * range;
        boolean[] hit = {false};
        NodeBlockEntity.each(player.serverLevel(), be -> {
            if (be.kind() == NodeBlock.Kind.DISH && be.powered() && friendly(profile, be.owner, player.getServer())
                    && be.getBlockPos().distSqr(player.blockPosition()) <= r2) {
                hit[0] = true;
            }
        });
        return hit[0];
    }

    public static int radarRange(ServerPlayer player, NetData.Profile profile) {
        int range = 0;
        if (Devices.has(player, dev.blackchannel.registry.ModContent.PHONE.get())) range = Config.RADAR_PHONE.get();
        if (Devices.has(player, dev.blackchannel.registry.ModContent.LAPTOP.get())) range = Math.max(range, Config.RADAR_LAPTOP.get());
        if (dishNear(player, profile, 128)) range += 80;
        return range;
    }

    public static int signalBars(ServerPlayer player, NetData.Profile profile) {
        if (!Devices.hasDevice(player) || Devices.battery(player) <= 0 || profile.airplane || emp(profile, player.getServer())) return 0;
        if (jammed(player.serverLevel(), player.blockPosition()) || faraday(player.serverLevel(), player.blockPosition())) return 1;
        Link link = link(player, profile);
        if (link.mode == Link.Mode.NONE) return transmitting(player, profile) ? 2 : 1;
        return link.dish ? 4 : 3;
    }

    public static void applyEmp(ServerLevel level, BlockPos pos, int radius, int seconds, String source) {
        if (level == null || radius <= 0 || seconds <= 0) return;
        MinecraftServer server = level.getServer();
        int now = server.getTickCount();
        long r2 = (long) radius * radius;
        Vec3 center = Vec3.atCenterOf(pos);
        List<UUID> hit = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) > r2) continue;
            NetData data = NetData.get(server);
            NetData.Profile profile = data.profile(player.getUUID());
            int dur = profile.hardened ? Math.max(4, seconds / 2) : seconds;
            profile.empUntil = Math.max(profile.empUntil, now + dur * 20);
            profile.bombUntil = Math.min(profile.bombUntil, now);
            if (profile.hardened) Devices.setAll(player, Math.max(30, Devices.battery(player)));
            else Devices.setAll(player, 0);
            if (player.containerMenu instanceof dev.blackchannel.menu.Menus.PhoneMenu
                    || player.containerMenu instanceof dev.blackchannel.menu.Menus.LaptopMenu) {
                player.closeContainer();
            }
            player.displayClientMessage(Component.translatable("blackchannel.chat.emp"), true);
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.8f, 0.5f);
            hit.add(player.getUUID());
            data.setDirty();
        }
        long game = level.getGameTime();
        NodeBlockEntity.each(level, be -> {
            if (be.getBlockPos().distSqr(pos) > r2) return;
            int drain = 45;
            long until = game + seconds * 20L;
            if (be.owner != null) {
                NetData.Profile owner = NetData.get(server).profiles.get(be.owner);
                if (owner != null && owner.hardened) {
                    drain = 20;
                    until = game + Math.max(4, seconds / 2) * 20L;
                }
            }
            be.reboot(until, drain);
        });
        NetData data = NetData.get(server);
        Iterator<NetData.Strike> it = data.strikes.iterator();
        while (it.hasNext()) {
            NetData.Strike strike = it.next();
            boolean aim = strike.dim.equals(Str.dim(level)) && dist2(strike.x, strike.y, strike.z, pos) <= r2;
            boolean attacker = hit.contains(strike.attacker);
            if (!aim && !attacker) continue;
            it.remove();
            ServerPlayer a = server.getPlayerList().getPlayer(strike.attacker);
            ServerPlayer t = server.getPlayerList().getPlayer(strike.target);
            if (a != null) a.sendSystemMessage(Component.translatable("blackchannel.chat.strike_abort"));
            if (t != null) t.sendSystemMessage(Component.translatable("blackchannel.chat.strike_abort"));
        }
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.4f, 0.4f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 40, radius / 6.0, 0.6, radius / 6.0, 0.05);
        data.setDirty();
    }

    private static long dist2(int x, int y, int z, BlockPos pos) {
        long dx = x - pos.getX();
        long dy = y - pos.getY();
        long dz = z - pos.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static List<Blip> blips(ServerPlayer viewer, NetData.Profile self) {
        List<Blip> out = new ArrayList<>();
        int range = radarRange(viewer, self);
        if (range <= 0 || self.airplane || emp(self, viewer.getServer())) return out;
        ServerLevel level = viewer.serverLevel();
        if (jammed(level, viewer.blockPosition()) || faraday(level, viewer.blockPosition())) return out;
        long r2 = (long) range * range;
        MinecraftServer server = viewer.getServer();
        for (ServerPlayer other : level.players()) {
            if (other == viewer) continue;
            NetData.Profile op = NetData.get(server).profile(other.getUUID());
            if (!transmitting(other, op)) continue;
            double d2 = viewer.distanceToSqr(other);
            if (d2 > r2) continue;
            int dx = (int) (other.getX() - viewer.getX());
            int dz = (int) (other.getZ() - viewer.getZ());
            if (op.vpn > 0 && !self.deanons.containsKey(op.handle)) {
                int seed = (server.getTickCount() / 40) ^ op.id.hashCode();
                net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(seed);
                int noise = 18 * op.vpn;
                dx += random.nextInt(noise * 2 + 1) - noise;
                dz += random.nextInt(noise * 2 + 1) - noise;
            }
            String kind = !self.crew.isEmpty() && self.crew.equals(op.crew) ? "friend" : "player";
            String label = "?";
            if (self.deanons.containsKey(op.handle)) label = self.deanons.get(op.handle);
            else if (op.vpn <= 0 || "friend".equals(kind)) label = op.handle;
            out.add(new Blip(dx, dz, (int) Math.sqrt(d2), kind, label));
            if (out.size() >= 32) return out;
        }
        if (Config.HOOK_DRONE.get()) {
            for (net.minecraft.world.entity.Entity entity : level.getEntities(viewer, viewer.getBoundingBox().inflate(range),
                    e -> e.getClass().getName().contains("DroneEntity"))) {
                int dx = (int) (entity.getX() - viewer.getX());
                int dz = (int) (entity.getZ() - viewer.getZ());
                out.add(new Blip(dx, dz, (int) Math.sqrt(viewer.distanceToSqr(entity)), "drone", "uav"));
                if (out.size() >= 32) return out;
            }
        }
        NodeBlockEntity.each(level, be -> {
            if (!be.powered()) return;
            int dx = be.getBlockPos().getX() - viewer.blockPosition().getX();
            int dz = be.getBlockPos().getZ() - viewer.blockPosition().getZ();
            int dist = (int) Math.sqrt(be.getBlockPos().distSqr(viewer.blockPosition()));
            if (dist > range) return;
            if (be.kind() == NodeBlock.Kind.BEACON) {
                boolean own = friendly(self, be.owner, server);
                boolean crew = be.code != null && !be.code.isEmpty() && be.code.equals(self.crew);
                if (own || crew) out.add(new Blip(dx, dz, dist, "beacon", own ? "beacon" : be.code));
            } else if (be.kind() == NodeBlock.Kind.DECOY) {
                boolean own = be.owner != null && be.owner.equals(self.id);
                boolean revealed = self.ids && dist <= 24;
                String kind = own || revealed ? "decoy" : "player";
                String label = own || revealed ? "decoy" : (be.fakeHandle == null || be.fakeHandle.isEmpty() ? "?" : be.fakeHandle);
                out.add(new Blip(dx, dz, dist, kind, label));
            }
        });
        return out.size() > 32 ? out.subList(0, 32) : out;
    }

    public static List<Track> tracks(ServerPlayer viewer, NetData.Profile self) {
        List<Track> out = new ArrayList<>();
        MinecraftServer server = viewer.getServer();
        long now = server.getTickCount();
        for (NetData.Profile other : NetData.get(server).profiles.values()) {
            for (NetData.Bug bug : other.bugs) {
                if (!self.id.equals(bug.owner) || bug.expireTick < now) continue;
                ServerPlayer live = server.getPlayerList().getPlayer(other.id);
                boolean audible = live != null && transmitting(live, other);
                int x = audible ? live.blockPosition().getX() : other.lastX;
                int y = audible ? live.blockPosition().getY() : other.lastY;
                int z = audible ? live.blockPosition().getZ() : other.lastZ;
                String dim = audible ? Str.dim(live.level()) : other.lastDim;
                out.add(new Track(other.handle, dim, x, y, z, audible));
            }
        }
        for (NetData.BlockBug bug : NetData.get(server).blockBugs) {
            if (!self.id.equals(bug.owner) || bug.expireTick < now) continue;
            ServerLevel level = Ops.levelOf(server, bug.dim);
            boolean audible = level != null && !jammed(level, new BlockPos(bug.x, bug.y, bug.z)) && !faraday(level, new BlockPos(bug.x, bug.y, bug.z));
            out.add(new Track(bug.ownerHandle.isEmpty() ? "bug" : "bug", bug.dim, bug.x, bug.y, bug.z, audible));
        }
        return out;
    }
}
