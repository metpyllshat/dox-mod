package dev.blackchannel.event;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.Config;
import dev.blackchannel.api.BlackChannelAPI;
import dev.blackchannel.block.NodeBlockEntity;
import dev.blackchannel.logic.Bridges;
import dev.blackchannel.logic.Devices;
import dev.blackchannel.logic.Minigame;
import dev.blackchannel.logic.Ops;
import dev.blackchannel.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = BlackChannelMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameEvents {
    private GameEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Ops.tick(server);
        Minigame.tick(server);
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        String name = event.getEntity().getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("entityblast") || name.contains("emp")) Bridges.watch(event.getEntity());
    }

    @SubscribeEvent
    public static void explode(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Config.HOOK_EMP.get()) return;
        Entity exploder = event.getExplosion().getExploder();
        if (exploder == null) return;
        if (!exploder.getClass().getName().toLowerCase(Locale.ROOT).contains("emp")) return;
        Vec3 pos = event.getExplosion().getPosition();
        BlackChannelAPI.pulse(level, BlockPos.containing(pos.x, pos.y, pos.z), Config.EMP_RADIUS.get(), Config.EMP_SECONDS.get());
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!event.getCrafting().is(ModContent.PHONE.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (Devices.has(player, ModContent.MANUAL.get())) return;
        player.getInventory().add(new ItemStack(ModContent.MANUAL.get()));
        player.sendSystemMessage(Component.translatable("blackchannel.chat.manual_given"));
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        NodeBlockEntity.clearIndex();
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        Minigame.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        Minigame.onHurt(event);
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        Minigame.onAttack(event);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        Minigame.onDeath(event);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Minigame.onRespawn(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Minigame.onLogin(player);
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        Minigame.onUseBlock(event);
    }

    @SubscribeEvent
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        Minigame.onInteractEntity(event);
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent event) {
        Minigame.onBreak(event);
    }

    @SubscribeEvent
    public static void placed(EntityPlaceEvent event) {
        Minigame.onPlace(event);
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        Minigame.onLivingTick(event);
    }

    @SubscribeEvent
    public static void chunk(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        Minigame.onChunk(level, chunk);
    }
}
