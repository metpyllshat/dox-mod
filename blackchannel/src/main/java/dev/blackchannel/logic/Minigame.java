package dev.blackchannel.logic;

import com.mojang.brigadier.CommandDispatcher;
import dev.blackchannel.Config;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.block.LoggerBlockEntity;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;
import dev.blackchannel.data.TrapData;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Minigame {
    private static final Map<UUID, Long> PROTECT = new HashMap<>();
    private static final Set<String> STRUCTURES = Set.of(
            "chests/simple_dungeon",
            "chests/abandoned_mineshaft",
            "chests/desert_pyramid",
            "chests/jungle_temple",
            "chests/pillager_outpost",
            "chests/woodland_mansion",
            "chests/stronghold_corridor",
            "chests/stronghold_crossing",
            "chests/stronghold_library",
            "chests/bastion_bridge",
            "chests/bastion_hoglin_stable",
            "chests/bastion_other",
            "chests/bastion_treasure",
            "chests/nether_bridge",
            "chests/end_city_treasure",
            "chests/ruined_portal",
            "chests/shipwreck_map",
            "chests/shipwreck_supply",
            "chests/shipwreck_treasure",
            "chests/buried_treasure",
            "chests/underwater_ruin_big",
            "chests/underwater_ruin_small",
            "chests/ancient_city",
            "chests/ancient_city_ice_box",
            "chests/igloo_chest"
    );

    private Minigame() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minigame")
                .executes(c -> help(c.getSource()))
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("crew")
                        .executes(c -> crew(c.getSource(), ""))
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(c -> crew(c.getSource(), StringArgumentType.getString(c, "code")))))
                .then(Commands.literal("center").requires(Minigame::op)
                        .executes(c -> centerHere(c.getSource()))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer(-64, 320))
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> centerAt(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "y"),
                                                        IntegerArgumentType.getInteger(c, "z")))))))
                .then(Commands.literal("size").requires(Minigame::op)
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(256, 20000))
                                .executes(c -> size(c.getSource(), IntegerArgumentType.getInteger(c, "blocks")))))
                .then(Commands.literal("start").requires(Minigame::op).executes(c -> start(c.getSource())))
                .then(Commands.literal("stop").requires(Minigame::op).executes(c -> stop(c.getSource()))));
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        TrapData data = TrapData.get(server);
        if (data.traps.isEmpty()) return;
        boolean dirty = false;
        Iterator<TrapData.Trap> it = data.traps.iterator();
        while (it.hasNext()) {
            TrapData.Trap trap = it.next();
            ServerLevel level = Ops.levelOf(server, trap.dim);
            if (level == null) continue;
            BlockPos pos = new BlockPos(trap.x, trap.y, trap.z);
            if (!level.isLoaded(pos)) continue;
            if (!isContainer(level.getBlockState(pos).getBlock())) {
                it.remove();
                dirty = true;
                continue;
            }
            int next = scanLogger(level, pos, trap.owner, trap.charges, trap.seen, null);
            if (next != trap.charges) {
                trap.charges = next;
                dirty = true;
            }
        }
        if (dirty) data.setDirty();
    }

    public static void onServerStarted(MinecraftServer server) {
        MatchData match = MatchData.get(server);
        if (!match.active) return;
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null) return;
        applyBorder(level, match, false);
        applyKeep(level, match, false);
    }

    public static void onChunk(ServerLevel level, LevelChunk chunk) {
        MatchData match = MatchData.get(level.getServer());
        if (!match.active || !Str.dim(level).equals(match.dim)) return;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof RandomizableContainerBlockEntity box)) continue;
            if (!isContainer(be.getBlockState().getBlock())) continue;
            try {
                injectDungeon(box, level.getRandom());
            } catch (RuntimeException e) {
                BlackChannelMod.LOGGER.warn("field chest skip at {}: {}", be.getBlockPos(), e.toString());
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide()) return;
        NetData.Profile profile = NetData.get(victim).profile(victim.getUUID());
        String name = profile.lastName == null || profile.lastName.isEmpty() ? victim.getGameProfile().getName() : profile.lastName;
        ItemStack tag = new ItemStack(ModContent.TAG.get());
        CompoundTag nbt = tag.getOrCreateTag();
        nbt.putString("Handle", profile.handle == null ? "" : profile.handle);
        nbt.putString("Name", name);
        BlockPos pos = victim.blockPosition();
        nbt.putInt("X", pos.getX());
        nbt.putInt("Y", pos.getY());
        nbt.putInt("Z", pos.getZ());
        nbt.putString("Dim", Str.dim(victim.level()));
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp && sp != victim ? sp : null;
        if (killer != null && killer.level() == victim.level() && killer.distanceToSqr(victim) <= 256.0) {
            if (!killer.getInventory().add(tag)) killer.drop(tag, false);
            return;
        }
        if (victim.level() instanceof ServerLevel level) {
            level.addFreshEntity(new ItemEntity(level, victim.getX(), victim.getY() + 0.2, victim.getZ(), tag));
        }
    }

    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) return;
        MatchData match = MatchData.get(victim.getServer());
        if (match.active) {
            long now = victim.serverLevel().getGameTime();
            boolean grace = now < match.graceUntil;
            boolean wall = shielded(victim) || shielded(attacker);
            if (shielded(attacker)) PROTECT.remove(attacker.getUUID());
            if (grace || wall) {
                event.setCanceled(true);
                return;
            }
        }
        Ops.learnHit(victim, attacker);
    }

    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (!(event.getTarget() instanceof ServerPlayer)) return;
        if (!shielded(attacker)) return;
        PROTECT.remove(attacker.getUUID());
        event.setCanceled(true);
    }

    public static void onRespawn(ServerPlayer player) {
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) return;
        protect(player, player.serverLevel().getGameTime() + 160L);
        if (bedOk(player, match)) return;
        MatchData.Drop drop = match.drops.get(player.getUUID());
        ServerLevel level = Ops.levelOf(player.getServer(), match.dim);
        if (drop == null || level == null) {
            assign(player, match, true);
            return;
        }
        BlockPos feet = findSafe(level, drop.x, drop.z, player.getRandom());
        place(player, level, feet, drop.yaw);
        player.displayClientMessage(Component.translatable("blackchannel.minigame.respawn"), true);
    }

    public static void onLogin(ServerPlayer player) {
        MatchData match = MatchData.get(player.getServer());
        if (!match.active) return;
        if (!match.drops.containsKey(player.getUUID())) assign(player, match, true);
        kit(player, match);
        match.setDirty();
    }

    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isContainer(level.getBlockState(event.getPos()).getBlock())) return;
        TrapData data = TrapData.get(player.getServer());
        TrapData.Trap trap = data.at(Str.dim(level), event.getPos());
        if (trap != null) {
            int next = scanLogger(level, event.getPos(), trap.owner, trap.charges, trap.seen, player);
            if (next != trap.charges) {
                trap.charges = next;
                data.setDirty();
            }
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = event.getPos().relative(dir);
            if (!(level.getBlockEntity(neighbor) instanceof LoggerBlockEntity be)) continue;
            int next = scanLogger(level, neighbor, be.owner, be.charges, be.seen, player);
            if (next != be.charges) {
                be.charges = next;
                be.setChanged();
            }
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!isContainer(event.getState().getBlock())) return;
        TrapData.get(level.getServer()).removeAt(Str.dim(level), event.getPos());
    }

    public static boolean isContainer(Block block) {
        return block instanceof ChestBlock || block instanceof BarrelBlock;
    }

    public static void attachLogger(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        if (!(context.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = context.getClickedPos();
        TrapData data = TrapData.get(player.getServer());
        if (data.at(Str.dim(level), pos) != null) {
            player.displayClientMessage(Component.translatable("blackchannel.result.logger_full"), true);
            return;
        }
        ItemStack stack = context.getItemInHand();
        int charges = chargesOf(stack);
        if (charges <= 0) {
            player.displayClientMessage(Component.translatable("blackchannel.result.logger_dead"), true);
            return;
        }
        TrapData.Trap trap = new TrapData.Trap();
        trap.dim = Str.dim(level);
        trap.x = pos.getX();
        trap.y = pos.getY();
        trap.z = pos.getZ();
        trap.owner = player.getUUID();
        trap.charges = charges;
        data.traps.add(trap);
        data.setDirty();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("blackchannel.result.logger_set"), true);
    }

    public static boolean scanBlock(ServerLevel level, BlockPos pos, UUID owner, List<UUID> seen) {
        if (owner == null) return false;
        int radius = Config.LOGGER_RADIUS.get();
        long r2 = (long) radius * radius;
        int before = seen.size();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r2) continue;
            logOne(level, pos, owner, 1, seen, player, false);
        }
        return seen.size() != before;
    }

    public static int scanLogger(ServerLevel level, BlockPos pos, UUID owner, int charges, List<UUID> seen, ServerPlayer only) {
        if (charges <= 0 || owner == null) return charges;
        if (only != null) return logOne(level, pos, owner, charges, seen, only, true);
        int left = charges;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12.25) continue;
            left = logOne(level, pos, owner, left, seen, player, true);
            if (left <= 0) break;
        }
        return left;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.help"), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        MatchData match = MatchData.get(source.getServer());
        String crew = "-";
        if (source.getEntity() instanceof ServerPlayer player) {
            String code = NetData.get(player).profile(player.getUUID()).crew;
            crew = code == null || code.isEmpty() ? "-" : code;
        }
        long left = 0;
        ServerLevel level = Ops.levelOf(source.getServer(), match.dim);
        if (match.active && level != null) left = Math.max(0, (match.graceUntil - level.getGameTime()) / 20L);
        int gap = match.baseGap();
        String center = match.centerSet ? match.centerX + " " + match.centerY + " " + match.centerZ : "-";
        long grace = left;
        String crewShown = crew;
        String state = match.active ? "on" : "off";
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.status",
                Component.translatable("blackchannel.minigame." + state), center, match.size, gap, crewShown, grace), false);
        return 1;
    }

    private static int crew(CommandSourceStack source, String raw) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("blackchannel.minigame.need_player"));
            return 0;
        }
        NetData data = NetData.get(player);
        NetData.Profile profile = data.profile(player.getUUID());
        profile.crew = Str.crew(raw);
        data.setDirty();
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.crew_set", profile.crew.isEmpty() ? "-" : profile.crew), false);
        return 1;
    }

    private static int centerHere(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("blackchannel.minigame.need_player"));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        return setCenter(source, player.serverLevel(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static int centerAt(CommandSourceStack source, int x, int y, int z) {
        return setCenter(source, source.getLevel(), x, y, z);
    }

    private static int setCenter(CommandSourceStack source, ServerLevel level, int x, int y, int z) {
        MatchData match = MatchData.get(source.getServer());
        if (match.active) {
            source.sendFailure(Component.translatable("blackchannel.minigame.already"));
            return 0;
        }
        match.centerSet = true;
        match.centerX = x;
        match.centerY = y;
        match.centerZ = z;
        match.dim = Str.dim(level);
        match.setDirty();
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.center_set", x, y, z, Str.dimShort(match.dim)), true);
        return 1;
    }

    private static int size(CommandSourceStack source, int blocks) {
        MatchData match = MatchData.get(source.getServer());
        if (match.active) {
            source.sendFailure(Component.translatable("blackchannel.minigame.already"));
            return 0;
        }
        match.size = blocks;
        match.setDirty();
        int gap = match.baseGap();
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.size_set", blocks, gap), true);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        MatchData match = MatchData.get(server);
        if (match.active) {
            source.sendFailure(Component.translatable("blackchannel.minigame.already"));
            return 0;
        }
        if (!match.centerSet || match.size < 256) {
            source.sendFailure(Component.translatable("blackchannel.minigame.need_setup"));
            return 0;
        }
        ServerLevel level = Ops.levelOf(server, match.dim);
        if (level == null) {
            source.sendFailure(Component.translatable("blackchannel.minigame.no_level"));
            return 0;
        }
        Map<String, List<ServerPlayer>> groups = new java.util.LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String code = NetData.get(server).profile(player.getUUID()).crew;
            String key = code == null || code.isEmpty() ? "solo:" + player.getUUID() : "crew:" + code;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(player);
        }
        int gap = fitGap(match, Math.max(1, groups.size()));
        boolean cramped = groups.size() > 1 && ringFit(match, gap) < groups.size();
        match.drops.clear();
        match.anchors.clear();
        match.kitted.clear();
        PROTECT.clear();
        List<String> keys = new ArrayList<>(groups.keySet());
        List<int[]> spots = anchorsFor(match, keys.size(), gap);
        for (int i = 0; i < keys.size(); i++) {
            int[] spot = spots.get(i);
            match.anchors.put(keys.get(i), MatchData.Drop.at(spot[0], match.centerY, spot[1], 0));
            float yaw = yawToward(spot[0], spot[1], match.centerX, match.centerZ);
            for (ServerPlayer player : groups.get(keys.get(i))) {
                BlockPos feet = scatter(level, spot[0], spot[1], player.getRandom());
                MatchData.Drop drop = MatchData.Drop.at(feet.getX(), feet.getY(), feet.getZ(), yaw);
                match.drops.put(player.getUUID(), drop);
                place(player, level, feet, yaw);
                kit(player, match);
                String shown = keys.get(i).startsWith("crew:") ? keys.get(i).substring(5) : "-";
                player.sendSystemMessage(Component.translatable("blackchannel.minigame.started_you", shown, gap));
                player.sendSystemMessage(Component.translatable("blackchannel.minigame.grace", 60));
            }
        }
        applyBorder(level, match, true);
        applyKeep(level, match, true);
        match.graceUntil = level.getGameTime() + 20L * 60L;
        match.active = true;
        match.setDirty();
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.started", groups.size(), gap), true);
        if (cramped) source.sendSuccess(() -> Component.translatable("blackchannel.minigame.cramped", gap), true);
        int caches = placeCaches(level, match);
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.caches", caches), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        MatchData match = MatchData.get(source.getServer());
        if (!match.active) {
            source.sendFailure(Component.translatable("blackchannel.minigame.not_running"));
            return 0;
        }
        ServerLevel level = Ops.levelOf(source.getServer(), match.dim);
        match.active = false;
        match.graceUntil = 0;
        match.drops.clear();
        match.anchors.clear();
        match.kitted.clear();
        PROTECT.clear();
        if (level != null) {
            restoreBorder(level, match);
            restoreKeep(level, match);
        }
        match.setDirty();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("blackchannel.minigame.stopped"));
        }
        source.sendSuccess(() -> Component.translatable("blackchannel.minigame.stopped"), true);
        return 1;
    }

    private static void assign(ServerPlayer player, MatchData match, boolean teleport) {
        ServerLevel level = Ops.levelOf(player.getServer(), match.dim);
        if (level == null) return;
        String code = NetData.get(player).profile(player.getUUID()).crew;
        String key = code == null || code.isEmpty() ? "solo:" + player.getUUID() : "crew:" + code;
        MatchData.Drop anchor = match.anchors.get(key);
        if (anchor == null) {
            int[] spot = freeAnchor(level, match);
            anchor = MatchData.Drop.at(spot[0], match.centerY, spot[1], 0);
            match.anchors.put(key, anchor);
        }
        float yaw = yawToward(anchor.x, anchor.z, match.centerX, match.centerZ);
        BlockPos feet = scatter(level, anchor.x, anchor.z, player.getRandom());
        match.drops.put(player.getUUID(), MatchData.Drop.at(feet.getX(), feet.getY(), feet.getZ(), yaw));
        if (teleport) place(player, level, feet, yaw);
        match.setDirty();
    }

    private static void kit(ServerPlayer player, MatchData match) {
        if (match.kitted.containsKey(player.getUUID())) return;
        if (!Devices.has(player, ModContent.PHONE.get())) {
            ItemStack phone = new ItemStack(ModContent.PHONE.get());
            phone.getOrCreateTag().putInt("Battery", 100);
            give(player, phone);
        }
        give(player, new ItemStack(ModContent.CELL.get()));
        if (!Devices.has(player, ModContent.MANUAL.get())) give(player, new ItemStack(ModContent.MANUAL.get()));
        match.kitted.put(player.getUUID(), true);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void place(ServerPlayer player, ServerLevel level, BlockPos feet, float yaw) {
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        protect(player, level.getGameTime() + 160L);
    }

    private static void protect(ServerPlayer player, long until) {
        PROTECT.put(player.getUUID(), until);
    }

    private static boolean shielded(ServerPlayer player) {
        Long until = PROTECT.get(player.getUUID());
        if (until == null) return false;
        if (player.serverLevel().getGameTime() >= until) {
            PROTECT.remove(player.getUUID());
            return false;
        }
        return true;
    }

    private static boolean bedOk(ServerPlayer player, MatchData match) {
        BlockPos bed = player.getRespawnPosition();
        ResourceKey<Level> dim = player.getRespawnDimension();
        if (bed == null || dim == null || player.getServer() == null) return false;
        ServerLevel level = player.getServer().getLevel(dim);
        if (level == null || !(level.getBlockState(bed).getBlock() instanceof BedBlock)) return false;
        ServerLevel matchLevel = Ops.levelOf(player.getServer(), match.dim);
        if (matchLevel != null && dim.equals(matchLevel.dimension()) && !match.inside(bed)) return false;
        return true;
    }

    private static int fitGap(MatchData match, int teams) {
        int n = match.baseGap();
        int guard = 0;
        while (teams > 1 && n > 48 && guard++ < 40 && ringFit(match, n) < teams) n -= 16;
        return n;
    }

    private static int ringFit(MatchData match, int gap) {
        int margin = margin(match, gap);
        double radius = Math.max(16, match.size - 2.0 * margin) / 2.0;
        return Math.max(1, (int) Math.floor(2 * Math.PI * radius / Math.max(1, gap)));
    }

    private static int margin(MatchData match, int gap) {
        return Math.max(24, Math.min(gap / 2, Math.max(24, match.size / 6)));
    }

    private static List<int[]> anchorsFor(MatchData match, int teams, int gap) {
        List<int[]> spots = new ArrayList<>();
        if (teams <= 1) {
            spots.add(new int[]{match.centerX, match.centerZ});
            return spots;
        }
        int edge = margin(match, gap);
        if (teams == 2) {
            int dx = Math.min(gap / 2, Math.max(16, match.size / 2 - edge));
            spots.add(clamp(match, match.centerX - dx, match.centerZ, edge));
            spots.add(clamp(match, match.centerX + dx, match.centerZ, edge));
            return spots;
        }
        double radius = Math.max(16, match.size - 2.0 * edge) / 2.0;
        for (int i = 0; i < teams; i++) {
            double ang = 2 * Math.PI * i / teams;
            int x = match.centerX + (int) Math.round(Math.cos(ang) * radius);
            int z = match.centerZ + (int) Math.round(Math.sin(ang) * radius);
            spots.add(clamp(match, x, z, edge));
        }
        return spots;
    }

    private static int[] freeAnchor(ServerLevel level, MatchData match) {
        int gap = Math.max(48, match.baseGap() / 2);
        RandomSource random = level.getRandom();
        int half = Math.max(8, match.size / 2 - 16);
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = match.centerX + random.nextInt(half * 2 + 1) - half;
            int z = match.centerZ + random.nextInt(half * 2 + 1) - half;
            if (crowded(match, x, z, gap)) continue;
            return new int[]{x, z};
        }
        return new int[]{match.centerX, match.centerZ};
    }

    private static boolean crowded(MatchData match, int x, int z, int gap) {
        long need = (long) gap * gap;
        for (MatchData.Drop anchor : match.anchors.values()) {
            long dx = anchor.x - x;
            long dz = anchor.z - z;
            if (dx * dx + dz * dz < need) return true;
        }
        return false;
    }

    private static int[] clamp(MatchData match, int x, int z, int edge) {
        int half = Math.max(0, match.size / 2 - edge);
        return new int[]{
                Mth.clamp(x, match.centerX - half, match.centerX + half),
                Mth.clamp(z, match.centerZ - half, match.centerZ + half)
        };
    }

    private static BlockPos scatter(ServerLevel level, int x, int z, RandomSource random) {
        int dx = random.nextInt(17) - 8;
        int dz = random.nextInt(17) - 8;
        if (dx * dx + dz * dz < 16) {
            dx = 4 + random.nextInt(8);
            dz = random.nextInt(9) - 4;
        }
        return findSafe(level, x + dx, z + dz, random);
    }

    private static BlockPos findSafe(ServerLevel level, int x, int z, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int sx = x + (attempt == 0 ? 0 : random.nextInt(25) - 12);
            int sz = z + (attempt == 0 ? 0 : random.nextInt(25) - 12);
            level.getChunk(sx >> 4, sz >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            BlockPos feet = new BlockPos(sx, y, sz);
            if (standable(level, feet)) return feet;
        }
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, Math.max(level.getMinBuildHeight() + 1, y), z);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(ground).isEmpty()) return false;
        if (level.getBlockState(ground).is(Blocks.LAVA) || level.getBlockState(ground).is(Blocks.MAGMA_BLOCK)) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    private static float yawToward(int x, int z, int tx, int tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    private static void applyBorder(ServerLevel level, MatchData match, boolean capture) {
        WorldBorder border = level.getWorldBorder();
        if (capture && !match.borderSaved) {
            match.prevSize = border.getSize();
            match.prevCenterX = border.getCenterX();
            match.prevCenterZ = border.getCenterZ();
            match.borderSaved = true;
        }
        border.setCenter(match.centerX + 0.5, match.centerZ + 0.5);
        border.setSize(match.size);
        border.lerpSizeBetween(match.size, match.size, 0L);
        border.setWarningBlocks(16);
        border.setDamagePerBlock(0.2);
        border.setDamageSafeZone(0);
    }

    private static void restoreBorder(ServerLevel level, MatchData match) {
        if (!match.borderSaved) return;
        WorldBorder border = level.getWorldBorder();
        border.setCenter(match.prevCenterX, match.prevCenterZ);
        border.setSize(match.prevSize);
        border.lerpSizeBetween(match.prevSize, match.prevSize, 0L);
        match.borderSaved = false;
    }

    private static void applyKeep(ServerLevel level, MatchData match, boolean capture) {
        GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        if (capture && !match.keepSaved) {
            match.prevKeepInv = rule.get();
            match.keepSaved = true;
        }
        rule.set(false, level.getServer());
    }

    private static void restoreKeep(ServerLevel level, MatchData match) {
        if (!match.keepSaved) return;
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(match.prevKeepInv, level.getServer());
        match.keepSaved = false;
    }

    private static int placeCaches(ServerLevel level, MatchData match) {
        int count = Mth.clamp(match.size / 120, 8, 40);
        int avoid = Math.min(150, Math.max(32, match.size / 8));
        RandomSource random = level.getRandom();
        Set<Long> used = new HashSet<>();
        int placed = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = pickCache(level, match, avoid, random, used, i < 8);
            if (pos == null) continue;
            if (!placeChest(level, pos, random)) continue;
            placed++;
        }
        return placed;
    }

    private static BlockPos pickCache(ServerLevel level, MatchData match, int avoid, RandomSource random, Set<Long> used, boolean spread) {
        int half = Math.max(8, match.size / 2 - 8);
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = match.centerX + random.nextInt(half * 2 + 1) - half;
            int z = match.centerZ + random.nextInt(half * 2 + 1) - half;
            if (crowded(match, x, z, avoid)) continue;
            long key = ((long) (x >> 4) << 32) ^ (z >> 4 & 0xffffffffL);
            if (spread && attempt < 8 && used.contains(key)) continue;
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!match.inside(feet) || !standable(level, feet)) continue;
            used.add(key);
            return feet;
        }
        return null;
    }

    private static boolean placeChest(ServerLevel level, BlockPos feet, RandomSource random) {
        Direction facing = Direction.from2DDataValue(random.nextInt(4));
        level.setBlock(feet, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
        if (!(level.getBlockEntity(feet) instanceof ChestBlockEntity chest)) return false;
        int roll = random.nextInt(100);
        fill(chest, random, roll < 70 ? 0 : roll < 92 ? 1 : 2);
        chest.setCustomName(Component.translatable("block.blackchannel.field_cache"));
        chest.getPersistentData().putBoolean("bcLoot", true);
        chest.setChanged();
        return true;
    }

    private static void injectDungeon(RandomizableContainerBlockEntity box, RandomSource random) {
        if (box.getPersistentData().getBoolean("bcLoot")) return;
        // 1.20.1 не отдаёт id таблицы методом. Он лежит в теге, пока сундук не открыли.
        CompoundTag saved = box.saveWithoutMetadata();
        if (!saved.contains("LootTable", 8)) return;
        ResourceLocation table = ResourceLocation.tryParse(saved.getString("LootTable"));
        if (table == null || !"minecraft".equals(table.getNamespace()) || !STRUCTURES.contains(table.getPath())) return;
        box.unpackLootTable(null);
        addStacks(box, dungeonStacks(random));
        box.getPersistentData().putBoolean("bcLoot", true);
        box.setChanged();
    }

    private static void fill(ChestBlockEntity chest, RandomSource random, int tier) {
        List<ItemStack> stacks = new ArrayList<>();
        addConfigured(stacks, Config.CACHE_COMMON.get(), random);
        if (tier >= 1) addConfigured(stacks, Config.CACHE_UNCOMMON.get(), random);
        if (tier >= 2) addConfigured(stacks, Config.CACHE_RARE.get(), random);
        addStacks(chest, stacks);
    }

    private static List<ItemStack> dungeonStacks(RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        addConfigured(stacks, Config.CACHE_DUNGEON.get(), random);
        return stacks;
    }

    private static void addConfigured(List<ItemStack> into, List<? extends String> lines, RandomSource random) {
        if (lines == null) return;
        for (String line : lines) {
            ItemStack stack = fromSpec(line, random);
            if (!stack.isEmpty()) into.add(stack);
        }
    }

    private static ItemStack fromSpec(String raw, RandomSource random) {
        if (raw == null) return ItemStack.EMPTY;
        String spec = raw.trim();
        if (spec.isEmpty()) return ItemStack.EMPTY;
        int count = 1;
        int star = spec.indexOf('*');
        if (star >= 0) {
            String num = spec.substring(star + 1).trim();
            spec = spec.substring(0, star).trim();
            try {
                int dash = num.indexOf('-');
                if (dash >= 0) {
                    int min = Integer.parseInt(num.substring(0, dash).trim());
                    int max = Integer.parseInt(num.substring(dash + 1).trim());
                    if (max < min) {
                        int swap = min;
                        min = max;
                        max = swap;
                    }
                    count = min + (max == min ? 0 : random.nextInt(max - min + 1));
                } else {
                    count = Integer.parseInt(num);
                }
            } catch (NumberFormatException e) {
                return ItemStack.EMPTY;
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR || count <= 0) return ItemStack.EMPTY;
        return new ItemStack(item, Math.min(64, count));
    }

    private static void addStacks(RandomizableContainerBlockEntity box, List<ItemStack> stacks) {
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            while (slot < box.getContainerSize() && !box.getItem(slot).isEmpty()) slot++;
            if (slot >= box.getContainerSize()) return;
            box.setItem(slot, stack);
            slot++;
        }
    }

    private static int logOne(ServerLevel level, BlockPos pos, UUID owner, int charges, List<UUID> seen, ServerPlayer player, boolean consume) {
        if (player.getUUID().equals(owner) || seen.contains(player.getUUID())) return charges;
        if (consume && charges <= 0) return charges;
        NetData data = NetData.get(level.getServer());
        NetData.Profile self = data.profile(owner);
        NetData.Profile other = data.profile(player.getUUID());
        if (other.lastName == null || other.lastName.isEmpty()) other.lastName = player.getGameProfile().getName();
        if (self.crew != null && !self.crew.isEmpty() && self.crew.equals(other.crew)) return charges;
        seen.add(player.getUUID());
        int left = consume ? charges - 1 : charges;
        String shown = other.vpn > 0 ? "" : other.lastName;
        boolean fresh = Ops.noteHeard(self, other.handle, shown, false);
        data.setDirty();
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (fresh && ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.translatable(shown.isEmpty() ? "blackchannel.result.net_handle" : "blackchannel.result.net_named", other.handle, shown));
            ModNetwork.syncFull(ownerPlayer);
        }
        if (other.ids) {
            player.sendSystemMessage(Component.translatable("blackchannel.chat.ids_logger", bearing(player, pos)));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.8f, 0.6f);
        }
        return left;
    }

    private static String bearing(ServerPlayer from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.getX();
        double dz = to.getZ() + 0.5 - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    private static int chargesOf(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("Charges")) return Mth.clamp(stack.getTag().getInt("Charges"), 0, 3);
        return 3;
    }

    private static boolean op(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
