package dev.blackchannel.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.blackchannel.api.BlackChannelAPI;
import dev.blackchannel.data.NetData;
import dev.blackchannel.logic.Bridges;
import dev.blackchannel.logic.Devices;
import dev.blackchannel.logic.SignalEnv;
import dev.blackchannel.logic.Str;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ModCommands {
    private ModCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bc")
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("hooks").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(Bridges.hookSummary()), false);
                    return 1;
                }))
                .then(Commands.literal("pulse").requires(s -> s.hasPermission(2))
                        .executes(c -> pulse(c.getSource(), 24))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 128))
                                .executes(c -> pulse(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("givehash").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 5000))
                                        .executes(c -> give(EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "amount"), c.getSource())))))
                .then(Commands.literal("reveal").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> reveal(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("sethandle")
                        .then(Commands.argument("handle", StringArgumentType.word())
                                .executes(c -> setHandle(c.getSource(), StringArgumentType.getString(c, "handle"))))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("blackchannel.cmd.help"), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            NetData.Profile profile = NetData.get(player).profile(player.getUUID());
            SignalEnv.Link link = SignalEnv.link(player, profile);
            source.sendSuccess(() -> Component.translatable("blackchannel.cmd.status",
                    profile.handle,
                    profile.lastName,
                    profile.crypto,
                    profile.vpn,
                    Devices.battery(player),
                    SignalEnv.signalBars(player, profile),
                    link.mode.name(),
                    profile.airplane,
                    SignalEnv.emp(profile, player.getServer()),
                    SignalEnv.jammed(player.serverLevel(), player.blockPosition()),
                    SignalEnv.faraday(player.serverLevel(), player.blockPosition())), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("blackchannel.cmd.no_player"));
            return 0;
        }
    }

    private static int pulse(CommandSourceStack source, int radius) {
        if (!(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return 0;
        BlockPos pos = BlockPos.containing(source.getPosition());
        BlackChannelAPI.pulse(level, pos, radius, 12);
        source.sendSuccess(() -> Component.translatable("blackchannel.cmd.pulse", radius), true);
        return 1;
    }

    private static int give(ServerPlayer player, int amount, CommandSourceStack source) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        profile.crypto = Math.min(5000, profile.crypto + amount);
        NetData.get(player).setDirty();
        source.sendSuccess(() -> Component.translatable("blackchannel.cmd.give", player.getGameProfile().getName(), amount), true);
        return 1;
    }

    private static int setHandle(CommandSourceStack source, String raw) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            NetData data = NetData.get(player);
            String handle = Str.norm(raw);
            if (handle.length() < 3) {
                source.sendFailure(Component.translatable("blackchannel.result.bad_handle"));
                return 0;
            }
            NetData.Profile taken = data.byHandle(handle);
            NetData.Profile profile = data.profile(player.getUUID());
            if (taken != null && taken != profile) {
                source.sendFailure(Component.translatable("blackchannel.result.bad_handle"));
                return 0;
            }
            profile.handle = handle;
            data.setDirty();
            source.sendSuccess(() -> Component.translatable("blackchannel.cmd.handle", handle), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("blackchannel.cmd.no_player"));
            return 0;
        }
    }

    private static int reveal(CommandSourceStack source, ServerPlayer player) {
        NetData.Profile profile = NetData.get(player).profile(player.getUUID());
        source.sendSuccess(() -> Component.translatable("blackchannel.cmd.reveal", player.getGameProfile().getName(), profile.handle, Str.dimShort(profile.lastDim), profile.lastX, profile.lastY, profile.lastZ), false);
        return 1;
    }
}
