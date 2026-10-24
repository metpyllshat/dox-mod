package dev.blackchannel.item;

import dev.blackchannel.data.NetData;
import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Devices;
import dev.blackchannel.logic.Ops;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DeviceItem extends Item {
    public enum Kind { PHONE, LAPTOP }

    public final Kind kind;

    public DeviceItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                NetData.Profile profile = NetData.get(sp).profile(sp.getUUID());
                if (kind == Kind.PHONE) Ops.action(sp, Actions.AIR, "");
                else {
                    profile.radarHud = !profile.radarHud;
                    sp.displayClientMessage(Component.translatable(profile.radarHud ? "blackchannel.result.radar_on" : "blackchannel.result.radar_off"), true);
                    ModNetwork.syncStatus(sp);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (kind == Kind.PHONE) {
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new Menus.PhoneMenu(id, inv), Component.translatable("screen.blackchannel.phone")));
            } else {
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new Menus.LaptopMenu(id, inv), Component.translatable("screen.blackchannel.laptop")));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (kind != Kind.LAPTOP || levelMissing(player) || !(target instanceof ServerPlayer victim) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        Ops.identify(sp, victim);
        return InteractionResult.SUCCESS;
    }

    private static boolean levelMissing(Player player) {
        return player.level().isClientSide;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(Devices.battery(stack) / 100f * 13f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int battery = Devices.battery(stack);
        if (battery < 20) return 0xFF5A6A;
        if (battery < 50) return 0xFFC14A;
        return 0x3DFF9A;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(kind == Kind.PHONE ? "tooltip.blackchannel.phone" : "tooltip.blackchannel.laptop"));
        tooltip.add(Component.translatable("tooltip.blackchannel.battery", Devices.battery(stack)));
    }
}
