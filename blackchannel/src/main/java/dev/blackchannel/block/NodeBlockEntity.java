package dev.blackchannel.block;

import dev.blackchannel.Config;
import dev.blackchannel.data.NetData;
import dev.blackchannel.logic.Devices;
import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class NodeBlockEntity extends BlockEntity {
    public static final Map<ResourceKey<Level>, Set<BlockPos>> INDEX = new HashMap<>();

    public UUID owner;
    public int charge;
    public long rebootUntil;
    public String code = "";
    public String fakeHandle = "";
    public long decoyExpire;
    public int seconds;

    private final Energy energy = new Energy();
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public NodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.NODE.get(), pos, state);
    }

    public NodeBlock.Kind kind() {
        if (getBlockState().getBlock() instanceof NodeBlock block) return block.kind;
        return NodeBlock.Kind.RACK;
    }

    public boolean powered() {
        if (level == null) return false;
        if (rebootUntil > level.getGameTime()) return false;
        if (kind() == NodeBlock.Kind.DECOY && decoyExpire > 0 && level.getGameTime() > decoyExpire) return false;
        return charge > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NodeBlockEntity be) {
        if (level.isClientSide || level.getGameTime() % 20L != 0) return;
        be.second();
    }

    private void second() {
        if (level == null) return;
        if (rebootUntil > level.getGameTime()) return;
        if (kind() == NodeBlock.Kind.RACK && charge > 0 && owner != null && level.getServer() != null) {
            seconds++;
            if (seconds % 60 == 0) {
                NetData data = NetData.get(level.getServer());
                NetData.Profile profile = data.profile(owner);
                int gain = Config.CRYPTO_PER_MIN.get();
                if (gain > 0 && profile.crypto < 5000 && mintFree(profile)) {
                    profile.crypto = Math.min(5000, profile.crypto + gain);
                    profile.mintTick = level.getServer().getTickCount();
                    profile.mintDim = level.dimension().location().toString();
                    profile.mintX = getBlockPos().getX();
                    profile.mintY = getBlockPos().getY();
                    profile.mintZ = getBlockPos().getZ();
                    data.setDirty();
                }
            }
        }
        if (charge > 0) {
            charge--;
            setChanged();
        }
    }

    private boolean mintFree(NetData.Profile profile) {
        if (level == null || level.getServer() == null || profile.mintTick <= 0 || profile.mintDim == null || profile.mintDim.isEmpty()) return true;
        int now = level.getServer().getTickCount();
        if (now - profile.mintTick >= 20 * 60) return true;
        if (!profile.mintDim.equals(level.dimension().location().toString())) return true;
        int range = Config.RACK_MINT_RANGE.get();
        long dx = (long) profile.mintX - getBlockPos().getX();
        long dy = (long) profile.mintY - getBlockPos().getY();
        long dz = (long) profile.mintZ - getBlockPos().getZ();
        return dx * dx + dy * dy + dz * dz > (long) range * range;
    }

    public boolean tryFuel(Player player, ItemStack stack) {
        if (stack.isEmpty() || charge >= 1000) return false;
        int add = 0;
        boolean consume = false;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            add = 250;
            consume = true;
        } else if (stack.is(Items.REDSTONE)) {
            add = 80;
            consume = true;
        } else if (stack.is(Items.REDSTONE_BLOCK)) {
            add = 800;
            consume = true;
        } else if (stack.is(ModContent.CELL.get())) {
            add = 1000;
            consume = true;
        } else {
            int extracted = stack.getCapability(ForgeCapabilities.ENERGY).map(e -> e.extractEnergy(5000, false)).orElse(0);
            if (extracted <= 0) return false;
            charge = Math.min(1000, charge + extracted / 8);
            setChanged();
            return true;
        }
        charge = Math.min(1000, charge + add);
        if (consume && !player.getAbilities().instabuild) stack.shrink(1);
        setChanged();
        return true;
    }

    public boolean chargeDevice(ItemStack stack) {
        if (!Devices.isDevice(stack) || charge < 150 || Devices.battery(stack) >= 100) return false;
        stack.getOrCreateTag().putInt("Battery", 100);
        charge -= 150;
        setChanged();
        return true;
    }

    public void reboot(long until, int drainPercent) {
        rebootUntil = Math.max(rebootUntil, until);
        charge = charge * (100 - drainPercent) / 100;
        setChanged();
    }

    public static void each(Level level, Consumer<NodeBlockEntity> fn) {
        if (level == null) return;
        Set<BlockPos> set = INDEX.get(level.dimension());
        if (set == null || set.isEmpty()) return;
        for (BlockPos pos : new ArrayList<>(set)) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof NodeBlockEntity be) fn.accept(be);
            else set.remove(pos);
        }
    }

    public static void clearIndex() {
        INDEX.clear();
    }

    private void indexAdd() {
        if (level == null) return;
        INDEX.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(getBlockPos());
    }

    private void indexRemove() {
        if (level == null) return;
        Set<BlockPos> set = INDEX.get(level.dimension());
        if (set != null) set.remove(getBlockPos());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) indexAdd();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        indexRemove();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("charge", charge);
        tag.putLong("reboot", rebootUntil);
        tag.putString("code", code == null ? "" : code);
        tag.putString("fake", fakeHandle == null ? "" : fakeHandle);
        tag.putLong("dexp", decoyExpire);
        tag.putInt("seconds", seconds);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        charge = tag.getInt("charge");
        rebootUntil = tag.getLong("reboot");
        code = tag.getString("code");
        fakeHandle = tag.getString("fake");
        decoyExpire = tag.getLong("dexp");
        seconds = tag.getInt("seconds");
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    private final class Energy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int room = (1000 - charge) * 8;
            int take = Math.min(Math.max(0, maxReceive), Math.max(0, room));
            if (!simulate && take > 0) {
                charge = Math.min(1000, charge + take / 8);
                setChanged();
            }
            return take;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return charge * 8;
        }

        @Override
        public int getMaxEnergyStored() {
            return 8000;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
