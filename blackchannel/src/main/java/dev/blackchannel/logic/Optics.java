package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.NetData;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Degraded SIGINT pictures. The sample is taken around the radio fix, not the body.
 * A live spectator camera would ignore the error radius, so it is not offered.
 */
public final class Optics {
    public static final int GRID = 16;
    public static final int STEP = 4;

    private Optics() {}

    public static void optic(ServerPlayer player, NetData.Profile profile, String arg) {
        if (!Ops.gate(player, profile, true, true)) return;
        if (!Ops.cd(player, profile, profile.cdOptic)) return;
        NetData.Intel intel = intel(profile, arg);
        if (intel == null) {
            Ops.result(player, profile, "blackchannel.result.swat_need_intel");
            return;
        }
        if (!Ops.pay(player, profile, Config.OPTIC_COST.get())) return;
        ServerLevel level = Ops.levelOf(player.getServer(), intel.dim);
        int now = player.getServer().getTickCount();
        profile.cdOptic = now + Config.OPTIC_CD.get() * 20;
        if (level == null) {
            profile.optic = new byte[0];
            profile.opticColor = new int[0];
            profile.opticHx = -1;
            profile.opticHz = -1;
            profile.opticConf = 0;
            profile.opticBearing = "";
            Ops.result(player, profile, "blackchannel.result.optic_empty");
            ModNetwork.syncFull(player);
            return;
        }
        RandomSource random = player.getRandom();
        NetData.Profile target = NetData.get(player).byHandle(intel.handle);
        int vpn = target == null ? 0 : target.vpn;
        int shift = Math.max(0, intel.err / 8);
        int ox = intel.x;
        int oz = intel.z;
        if (shift > 0) {
            ox += random.nextInt(shift * 2 + 1) - shift;
            oz += random.nextInt(shift * 2 + 1) - shift;
        }
        int drop = (intel.stale ? 35 : 10) + vpn * 12;
        byte[] cells = new byte[GRID * GRID];
        int[] colors = new int[GRID * GRID];
        int holes = 0;
        int baseY = intel.y;
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int i = gz * GRID + gx;
                if (random.nextInt(100) < drop) {
                    holes++;
                    continue;
                }
                int x = ox + (gx - GRID / 2) * STEP;
                int z = oz + (gz - GRID / 2) * STEP;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    holes++;
                    continue;
                }
                try {
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    if (surface <= level.getMinBuildHeight()) {
                        holes++;
                        continue;
                    }
                    BlockPos top = new BlockPos(x, surface, z);
                    int rel = surface - baseY + random.nextInt(3) - 1;
                    cells[i] = (byte) Math.max(-60, Math.min(60, rel));
                    int col = chunk.getBlockState(top.below()).getMapColor(chunk, top.below()).col;
                    colors[i] = col == 0 ? 0x5B6B52 : col;
                } catch (Throwable ignored) {
                    holes++;
                }
            }
        }
        profile.optic = cells;
        profile.opticColor = colors;
        profile.opticHx = -1;
        profile.opticHz = -1;
        profile.opticBearing = "";
        int conf = Math.max(0, 100 - holes * 100 / cells.length - vpn * 15 - (intel.stale ? 20 : 0));
        ServerPlayer online = target == null ? null : player.getServer().getPlayerList().getPlayer(target.id);
        boolean live = online != null && SignalEnv.transmitting(online, target) && online.level().dimension().equals(level.dimension());
        if (live) {
            int dx = online.blockPosition().getX() - ox;
            int dz = online.blockPosition().getZ() - oz;
            int gx = dx / STEP + GRID / 2;
            int gz = dz / STEP + GRID / 2;
            if (gx >= 1 && gz >= 1 && gx < GRID - 1 && gz < GRID - 1 && random.nextInt(100) > vpn * 20) {
                profile.opticHx = gx + random.nextInt(3) - 1;
                profile.opticHz = gz + random.nextInt(3) - 1;
                conf = Math.min(100, conf + 15);
            } else {
                profile.opticBearing = lie(bearing(dx, dz), vpn, random);
                conf = Math.max(0, conf - 10);
            }
        }
        profile.opticConf = conf;
        Ops.result(player, profile, "blackchannel.result.optic_ok", String.valueOf(holes), String.valueOf(conf),
                profile.opticBearing.isEmpty() ? "-" : profile.opticBearing);
        ModNetwork.syncFull(player);
    }

    public static void peek(ServerPlayer player, NetData.Profile profile, String arg) {
        if (!Ops.gate(player, profile, true, true)) return;
        if (!Ops.cd(player, profile, profile.cdPeek)) return;
        NetData.Intel intel = intel(profile, arg);
        if (intel == null) {
            Ops.result(player, profile, "blackchannel.result.swat_need_intel");
            return;
        }
        NetData data = NetData.get(player);
        NetData.Profile target = data.byHandle(intel.handle);
        ServerPlayer online = target == null ? null : player.getServer().getPlayerList().getPlayer(target.id);
        if (online == null || !SignalEnv.transmitting(online, target) || !Str.dim(online.level()).equals(intel.dim)) {
            Ops.result(player, profile, "blackchannel.result.peek_miss");
            return;
        }
        if (!Ops.pay(player, profile, Config.PEEK_COST.get())) return;
        int now = player.getServer().getTickCount();
        profile.cdPeek = now + Config.PEEK_CD.get() * 20;
        int dist = 9999;
        if (Str.dim(online.level()).equals(intel.dim)) {
            long dx = online.blockPosition().getX() - intel.x;
            long dy = online.blockPosition().getY() - intel.y;
            long dz = online.blockPosition().getZ() - intel.z;
            dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        RandomSource random = player.getRandom();
        int hide = Math.min(41, 6 + target.vpn * 8 + dist / 16);
        boolean[] hidden = new boolean[41];
        int hiddenCount = 0;
        while (hiddenCount < hide) {
            int slot = random.nextInt(41);
            if (hidden[slot]) continue;
            hidden[slot] = true;
            hiddenCount++;
        }
        profile.peek.clear();
        for (int i = 0; i < 41; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("i", i);
            ItemStack stack = slot(online, i);
            if (hidden[i]) {
                tag.putBoolean("h", true);
                if (target.vpn > 0 && random.nextInt(5) == 0) {
                    tag.putString("id", "minecraft:paper");
                    tag.putInt("n", 1);
                    tag.putBoolean("lie", true);
                }
            } else if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()) != null) {
                int count = stack.getCount();
                if (target.vpn > 0 && count > 1) count = Math.max(1, count + random.nextInt(3) - 1);
                tag.putString("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
                tag.putInt("n", count);
            }
            profile.peek.add(tag);
        }
        if (target.ids) Ops.alert(online, player, "blackchannel.chat.ids_trace");
        Ops.result(player, profile, "blackchannel.result.peek_ok", target.handle, String.valueOf(hiddenCount), String.valueOf(dist));
        ModNetwork.syncFull(player);
    }

    private static ItemStack slot(ServerPlayer player, int index) {
        if (index < 36) return player.getInventory().getItem(index);
        if (index < 40) return player.getInventory().armor.get(index - 36);
        return player.getOffhandItem();
    }

    private static NetData.Intel intel(NetData.Profile profile, String arg) {
        int id;
        try {
            id = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        for (NetData.Intel row : profile.intel) if (row.id == id) return row;
        return null;
    }

    private static String bearing(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int idx = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return names[idx];
    }

    private static String lie(String dir, int vpn, RandomSource random) {
        if (vpn <= 0 || random.nextInt(100) >= vpn * 25) return dir;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[random.nextInt(names.length)];
    }

    public static ListTag peekTag(NetData.Profile profile) {
        ListTag list = new ListTag();
        for (CompoundTag tag : profile.peek) list.add(tag);
        return list;
    }
}
