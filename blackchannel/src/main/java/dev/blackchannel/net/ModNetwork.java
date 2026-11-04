package dev.blackchannel.net;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.logic.Ops;
import dev.blackchannel.logic.Sync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTO = "3";
    public static SimpleChannel CHANNEL;
    private static int id;

    private ModNetwork() {}

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(BlackChannelMod.MODID, "main"), () -> PROTO, PROTO::equals, PROTO::equals);
        id = 0;
        CHANNEL.registerMessage(id++, ActionPkt.class, ActionPkt::encode, ActionPkt::decode, ActionPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SyncPkt.class, SyncPkt::encode, SyncPkt::decode, SyncPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StatusPkt.class, StatusPkt::encode, StatusPkt::decode, StatusPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenPkt.class, OpenPkt::encode, OpenPkt::decode, OpenPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, FloodPkt.class, FloodPkt::encode, FloodPkt::decode, FloodPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CamPkt.class, CamPkt::encode, CamPkt::decode, CamPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CamTargetsPkt.class, CamTargetsPkt::encode, CamTargetsPkt::decode, CamTargetsPkt::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void syncFull(ServerPlayer player) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPkt(Sync.full(player)));
    }

    public static void syncStatus(ServerPlayer player) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StatusPkt(Sync.status(player)));
    }

    public static void sendFlood(ServerPlayer player) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FloodPkt(dev.blackchannel.logic.Flood.images()));
    }

    /** Обзор вышки: включить или выключить на клиенте. */
    public static void sendCam(ServerPlayer player, boolean on, int x, int y, int z, float yaw, float pitch, int ticks) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CamPkt(on, x, y, z, yaw, pitch, ticks));
    }

    public static void sendCamTargets(ServerPlayer player, List<CamTarget> targets) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CamTargetsPkt(targets));
    }

    public static void open(ServerPlayer player, String screen) {
        if (CHANNEL == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenPkt(screen));
    }

    /** Сервер -> клиент: обзор вышки включён или выключен. */
    public static final class CamPkt {
        public final boolean on;
        public final int x, y, z, ticks;
        public final float yaw, pitch;

        public CamPkt(boolean on, int x, int y, int z, float yaw, float pitch, int ticks) {
            this.on = on;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.ticks = ticks;
        }

        public static void encode(CamPkt msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.on);
            buf.writeBlockPos(new net.minecraft.core.BlockPos(msg.x, msg.y, msg.z));
            buf.writeFloat(msg.yaw);
            buf.writeFloat(msg.pitch);
            buf.writeVarInt(Math.max(0, msg.ticks));
        }

        public static CamPkt decode(FriendlyByteBuf buf) {
            boolean on = buf.readBoolean();
            net.minecraft.core.BlockPos pos = buf.readBlockPos();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            int ticks = Math.min(20 * 60 * 10, Math.max(0, buf.readVarInt()));
            return new CamPkt(on, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch, ticks);
        }

        public static void handle(CamPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> dev.blackchannel.client.TowerCam.apply(
                        msg.on, msg.x, msg.y, msg.z, msg.yaw, msg.pitch, msg.ticks));
            }
            context.setPacketHandled(true);
        }
    }

    public record CamTarget(float x, float y, float z, String label, int kind) {}

    /** Lightweight CCTV overlay; never injects remote entities into vanilla client tracking. */
    public static final class CamTargetsPkt {
        public final List<CamTarget> targets;

        public CamTargetsPkt(List<CamTarget> targets) {
            this.targets = targets == null ? List.of() : List.copyOf(targets);
        }

        public static void encode(CamTargetsPkt msg, FriendlyByteBuf buf) {
            int count = Math.min(32, msg.targets.size());
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                CamTarget target = msg.targets.get(i);
                buf.writeFloat(target.x());
                buf.writeFloat(target.y());
                buf.writeFloat(target.z());
                buf.writeUtf(target.label(), 32);
                buf.writeByte(Math.max(0, Math.min(2, target.kind())));
            }
        }

        public static CamTargetsPkt decode(FriendlyByteBuf buf) {
            int count = Math.min(32, Math.max(0, buf.readVarInt()));
            List<CamTarget> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                targets.add(new CamTarget(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readUtf(32), buf.readUnsignedByte()));
            }
            return new CamTargetsPkt(targets);
        }

        public static void handle(CamTargetsPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> dev.blackchannel.client.TowerCam.applyTargets(msg.targets));
            }
            context.setPacketHandled(true);
        }
    }

    public static final class ActionPkt {
        public final String action;
        public final String arg;

        public ActionPkt(String action, String arg) {
            this.action = action == null ? "" : action;
            this.arg = arg == null ? "" : arg;
        }

        public static void encode(ActionPkt msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.action, 32);
            buf.writeUtf(msg.arg, 180);
        }

        public static ActionPkt decode(FriendlyByteBuf buf) {
            return new ActionPkt(buf.readUtf(32), buf.readUtf(180));
        }

        public static void handle(ActionPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            ServerPlayer sender = context.getDirection() == NetworkDirection.PLAY_TO_SERVER ? context.getSender() : null;
            context.enqueueWork(() -> {
                if (sender != null) Ops.action(sender, msg.action, msg.arg);
            });
            context.setPacketHandled(true);
        }
    }

    public static final class SyncPkt {
        public final CompoundTag tag;

        public SyncPkt(CompoundTag tag) {
            this.tag = tag == null ? new CompoundTag() : tag;
        }

        public static void encode(SyncPkt msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.tag);
        }

        public static SyncPkt decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new SyncPkt(tag == null ? new CompoundTag() : tag);
        }

        public static void handle(SyncPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> dev.blackchannel.client.ClientState.applyFull(msg.tag));
            }
            context.setPacketHandled(true);
        }
    }

    public static final class StatusPkt {
        public final CompoundTag tag;

        public StatusPkt(CompoundTag tag) {
            this.tag = tag == null ? new CompoundTag() : tag;
        }

        public static void encode(StatusPkt msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.tag);
        }

        public static StatusPkt decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new StatusPkt(tag == null ? new CompoundTag() : tag);
        }

        public static void handle(StatusPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> dev.blackchannel.client.ClientState.applyStatus(msg.tag));
            }
            context.setPacketHandled(true);
        }
    }

    public static final class FloodPkt {
        public final List<String> names;
        public final List<byte[]> pngs;

        public FloodPkt(List<dev.blackchannel.logic.Flood.NamedPng> images) {
            names = new ArrayList<>();
            pngs = new ArrayList<>();
            if (images == null) return;
            for (dev.blackchannel.logic.Flood.NamedPng image : images) {
                if (image == null || image.bytes() == null || image.bytes().length == 0 || image.bytes().length > 48_000) continue;
                names.add(image.name() == null ? "flood.png" : image.name());
                pngs.add(image.bytes());
                if (names.size() >= 4) break;
            }
        }

        private FloodPkt(List<String> names, List<byte[]> pngs) {
            this.names = names;
            this.pngs = pngs;
        }

        public static void encode(FloodPkt msg, FriendlyByteBuf buf) {
            int n = Math.min(msg.names.size(), msg.pngs.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeUtf(msg.names.get(i), 64);
                buf.writeByteArray(msg.pngs.get(i));
            }
        }

        public static FloodPkt decode(FriendlyByteBuf buf) {
            int n = Math.min(4, buf.readVarInt());
            List<String> names = new ArrayList<>();
            List<byte[]> pngs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                names.add(buf.readUtf(64));
                pngs.add(buf.readByteArray(48_000));
            }
            return new FloodPkt(names, pngs);
        }

        public static void handle(FloodPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> dev.blackchannel.client.FloodHud.install(msg.names, msg.pngs));
            }
            context.setPacketHandled(true);
        }
    }

    public static final class OpenPkt {
        public final String id;

        public OpenPkt(String id) {
            this.id = id == null ? "" : id;
        }

        public static void encode(OpenPkt msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.id, 32);
        }

        public static OpenPkt decode(FriendlyByteBuf buf) {
            return new OpenPkt(buf.readUtf(32));
        }

        public static void handle(OpenPkt msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> dev.blackchannel.client.ClientHooks.open(msg.id));
            }
            context.setPacketHandled(true);
        }
    }
}
