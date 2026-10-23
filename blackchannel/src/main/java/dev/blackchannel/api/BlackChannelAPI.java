package dev.blackchannel.api;

import dev.blackchannel.logic.SignalEnv;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Other mods, command blocks and datapacks can call this.
 * A pulse only shuts down in-game terminals inside the Minecraft world.
 */
public final class BlackChannelAPI {
    private BlackChannelAPI() {}

    public static void pulse(ServerLevel level, BlockPos pos, int radius, int seconds) {
        SignalEnv.applyEmp(level, pos, radius, seconds, "api");
    }
}
