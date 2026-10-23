package dev.blackchannel.logic;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.Config;
import dev.blackchannel.api.BlackChannelAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class Bridges {
    public static boolean ballistixHooked;
    public static boolean ewSeen;
    public static boolean droneSeen;
    private static boolean ewFailed;
    private static boolean blastFailed;
    private static boolean curiosFailed;
    private static Method curiosInv;
    private static Method getController;
    private static final List<Watch> WATCH = new ArrayList<>();
    private static final List<BlockPos> RECENT_EMP = new ArrayList<>();
    private static final List<Long> RECENT_EMP_TIME = new ArrayList<>();

    private Bridges() {}

    public static void init(IEventBus bus) {
        ewSeen = ModList.get().isLoaded("sbw_ew_addon");
        droneSeen = ModList.get().isLoaded("superbwarfare");
        if (!ModList.get().isLoaded("ballistix")) return;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> type = (Class<? extends Event>) Class.forName("ballistix.api.event.BlastEvent");
            bus.addListener(EventPriority.NORMAL, false, type, Bridges::onBallistix);
            ballistixHooked = true;
            BlackChannelMod.LOGGER.info("Black Channel hooked Ballistix blasts");
        } catch (Throwable t) {
            BlackChannelMod.LOGGER.warn("Ballistix hook failed: {}", t.toString());
        }
    }

    public static String hookSummary() {
        return "ballistix=" + ballistixHooked + ", ew=" + ewSeen + ", drones=" + droneSeen
                + ", curios=" + ModList.get().isLoaded("curios") + ", electrodynamics=" + ModList.get().isLoaded("electrodynamics");
    }

    public static void onBallistix(Event event) {
        if (!Config.HOOK_EMP.get()) return;
        try {
            Object blast = findBlast(event);
            if (blast == null) return;
            if (!blast.getClass().getName().toLowerCase(Locale.ROOT).contains("emp")) return;
            Object world = field(blast, "world");
            Object position = field(blast, "position");
            if (!(world instanceof ServerLevel level) || !(position instanceof BlockPos pos)) return;
            if (!markEmp(pos, level.getGameTime())) return;
            BlackChannelAPI.pulse(level, pos, Config.EMP_RADIUS.get(), Config.EMP_SECONDS.get());
            BlackChannelMod.LOGGER.info("EMP pulse from Ballistix at {} {}", pos.toShortString(), level.dimension().location());
        } catch (Throwable t) {
            if (!blastFailed) {
                blastFailed = true;
                BlackChannelMod.LOGGER.warn("Ballistix EMP bridge error: {}", t.toString());
            }
        }
    }

    public static void watch(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        WATCH.add(new Watch(Str.dim(entity.level()), entity.getUUID(), 8));
    }

    public static void tickWatch(MinecraftServer server) {
        for (int i = WATCH.size() - 1; i >= 0; i--) {
            Watch watch = WATCH.get(i);
            watch.left--;
            if (watch.left > 0) continue;
            WATCH.remove(i);
            ServerLevel level = Ops.levelOf(server, watch.dim);
            if (level == null) continue;
            Entity entity = level.getEntity(watch.id);
            if (entity == null || entity.getPersistentData().getBoolean("bc_emp_handled")) continue;
            String name = entity.getClass().getName().toLowerCase(Locale.ROOT);
            boolean emp = false;
            if (name.contains("entityblast")) {
                try {
                    Method method = entity.getClass().getMethod("getBlast");
                    Object blast = method.invoke(entity);
                    emp = blast != null && blast.getClass().getName().toLowerCase(Locale.ROOT).contains("emp");
                } catch (Throwable ignored) {
                    emp = false;
                }
            } else if (name.contains("emp") && !name.contains("packet") && !name.contains("screen") && !name.contains("renderer")) {
                emp = true;
            }
            if (!emp) continue;
            entity.getPersistentData().putBoolean("bc_emp_handled", true);
            if (!markEmp(entity.blockPosition(), level.getGameTime())) continue;
            BlackChannelAPI.pulse(level, entity.blockPosition(), Config.EMP_RADIUS.get(), Config.EMP_SECONDS.get());
        }
    }

    public static void scanDroneEmp(MinecraftServer server) {
        if (!Config.HOOK_EMP.get() || !droneSeen) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                AABB box = player.getBoundingBox().inflate(28);
                for (Entity entity : level.getEntities(player, box, e -> e.getClass().getName().contains("DroneEntity"))) {
                    CompoundTag tag = entity.getPersistentData();
                    if (tag.getBoolean("bc_emp_handled")) continue;
                    if (!(tag.contains("EmpKill") || tag.contains("EmpShutdownStage") || tag.contains("EmpShutdownTick") || tag.getBoolean("EmpForced"))) {
                        continue;
                    }
                    tag.putBoolean("bc_emp_handled", true);
                    BlackChannelAPI.pulse(level, entity.blockPosition(), Config.DRONE_EMP_RADIUS.get(), Math.max(8, Config.EMP_SECONDS.get() / 2));
                }
            }
        }
    }

    public static Entity nearestDrone(ServerPlayer player, double range) {
        if (!droneSeen) return null;
        Entity best = null;
        double bestD = range * range;
        for (Entity entity : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(range),
                e -> e.getClass().getName().contains("DroneEntity"))) {
            double d = entity.distanceToSqr(player);
            if (d < bestD) {
                bestD = d;
                best = entity;
            }
        }
        return best;
    }

    public static UUID droneController(Entity drone) {
        try {
            if (getController == null) getController = drone.getClass().getMethod("getController");
            Object value = getController.invoke(drone);
            if (value instanceof UUID uuid) return uuid;
            if (value instanceof Optional<?> optional && optional.orElse(null) instanceof UUID uuid) return uuid;
        } catch (Throwable ignored) {
            getController = null;
        }
        return null;
    }

    public static void collectEwJammers(MinecraftServer server, List<SignalEnv.Sphere> into) {
        if (!ewSeen || ewFailed) {
            portable(server, into);
            return;
        }
        try {
            Class<?> type = Class.forName("com.example.sbw_ew_addon.JammerBlockEntity");
            Method active = method(type, "getActiveJammers", Level.class);
            Method radius = method(type, "getJamRadius");
            for (ServerLevel level : server.getAllLevels()) {
                Object set = active.invoke(null, level);
                if (!(set instanceof Iterable<?> positions)) continue;
                for (Object entry : positions) {
                    if (!(entry instanceof BlockPos pos)) continue;
                    var be = level.getBlockEntity(pos);
                    int r = 24;
                    if (be != null && radius != null) {
                        Object value = radius.invoke(be);
                        if (value instanceof Number number) r = Math.max(4, number.intValue());
                    }
                    into.add(new SignalEnv.Sphere(Str.dim(level), pos.getX(), pos.getY(), pos.getZ(), r));
                }
            }
        } catch (Throwable t) {
            ewFailed = true;
            BlackChannelMod.LOGGER.warn("EW jammer bridge failed: {}", t.toString());
        }
        portable(server, into);
    }

    private static void portable(MinecraftServer server, List<SignalEnv.Sphere> into) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            considerPortable(player, player.getMainHandItem(), into);
            considerPortable(player, player.getOffhandItem(), into);
            for (ItemStack stack : player.getInventory().items) considerPortable(player, stack, into);
        }
    }

    private static void considerPortable(ServerPlayer player, ItemStack stack, List<SignalEnv.Sphere> into) {
        if (stack.isEmpty() || !stack.getItem().getClass().getName().contains("PortableJammer")) return;
        CompoundTag tag = stack.getTag();
        if (tag != null && (tag.getBoolean("Active") || tag.getBoolean("active"))) {
            BlockPos pos = player.blockPosition();
            into.add(new SignalEnv.Sphere(Str.dim(player.level()), pos.getX(), pos.getY(), pos.getZ(), 20));
        }
    }

    public static List<ItemStack> curios(Player player) {
        if (curiosFailed || !ModList.get().isLoaded("curios")) return List.of();
        try {
            if (curiosInv == null) {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                curiosInv = api.getMethod("getCuriosInventory", LivingEntity.class);
            }
            Object opt = curiosInv.invoke(null, player);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) return List.of();
            Object inv = optional.get();
            Object handler = inv.getClass().getMethod("getEquippedCurios").invoke(inv);
            int n = (int) handler.getClass().getMethod("getSlots").invoke(handler);
            Method getStack = handler.getClass().getMethod("getStackInSlot", int.class);
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Object stack = getStack.invoke(handler, i);
                if (stack instanceof ItemStack item && !item.isEmpty()) out.add(item);
            }
            return out;
        } catch (Throwable t) {
            curiosFailed = true;
            return List.of();
        }
    }

    public static String itemName(ItemStack stack) {
        if (stack.isEmpty()) return "";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static boolean markEmp(BlockPos pos, long time) {
        for (int i = RECENT_EMP.size() - 1; i >= 0; i--) {
            if (time - RECENT_EMP_TIME.get(i) > 40) {
                RECENT_EMP.remove(i);
                RECENT_EMP_TIME.remove(i);
                continue;
            }
            if (RECENT_EMP.get(i).distSqr(pos) <= 4) return false;
        }
        RECENT_EMP.add(pos.immutable());
        RECENT_EMP_TIME.add(time);
        return true;
    }

    private static Object findBlast(Object event) {
        Object direct = field(event, "iExplosion");
        if (direct != null) return direct;
        Class<?> type = event.getClass();
        while (type != null && type != Object.class) {
            for (Field f : type.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(event);
                    if (value != null && value.getClass().getName().toLowerCase(Locale.ROOT).contains("blast")) return value;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object field(Object object, String name) {
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Method method(Class<?> type, String name, Class<?>... params) throws Exception {
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            Method method = type.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
        }
    }

    private static final class Watch {
        final String dim;
        final UUID id;
        int left;

        Watch(String dim, UUID id, int left) {
            this.dim = dim;
            this.id = id;
            this.left = left;
        }
    }
}
