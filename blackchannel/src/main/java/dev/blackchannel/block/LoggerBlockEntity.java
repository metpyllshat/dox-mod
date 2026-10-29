package dev.blackchannel.block;

import dev.blackchannel.logic.Minigame;
import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoggerBlockEntity extends BlockEntity {
    public UUID owner;
    public int charges = 3;
    public final List<UUID> seen = new ArrayList<>();

    public LoggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.LOGGER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LoggerBlockEntity be) {
        if (level.isClientSide || !(level instanceof ServerLevel server) || level.getGameTime() % 20L != 0) return;
        int next = Minigame.scanLogger(server, pos, be.owner, be.charges, be.seen, null);
        if (next != be.charges) {
            be.charges = next;
            be.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("charges", charges);
        ListTag seen = new ListTag();
        for (UUID id : this.seen) seen.add(StringTag.valueOf(id.toString()));
        tag.put("seen", seen);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        charges = tag.contains("charges") ? tag.getInt("charges") : 3;
        seen.clear();
        ListTag list = tag.getList("seen", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                seen.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // a broken id must not wipe the rest of the trap
            }
        }
    }
}
