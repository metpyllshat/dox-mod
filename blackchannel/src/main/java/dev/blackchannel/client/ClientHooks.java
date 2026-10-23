package dev.blackchannel.client;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BlackChannelMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientHooks {
    private static int glitch;

    private ClientHooks() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register((MenuType<Menus.PhoneMenu>) (Object) ModContent.PHONE_MENU.get(), PhoneScreen::new);
            MenuScreens.register((MenuType<Menus.LaptopMenu>) (Object) ModContent.LAPTOP_MENU.get(), LaptopScreen::new);
            MenuScreens.register((MenuType<Menus.RackMenu>) (Object) ModContent.RACK_MENU.get(), RackScreen::new);
        });
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::hud);
    }

    public static void open(String id) {
        if ("manual".equals(id)) Minecraft.getInstance().setScreen(new ManualScreen());
    }

    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        glitch++;
        if (ClientState.strikeT > 0) {
            String text = Component.translatable("blackchannel.hud.strike", ClientState.strikeT / 20, ClientState.strikeX, ClientState.strikeZ).getString();
            int tw = mc.font.width(text);
            g.fill(w / 2 - tw / 2 - 6, 4, w / 2 + tw / 2 + 6, 16, 0xC0401018);
            g.drawString(mc.font, text, w / 2 - tw / 2, 6, Ui.RED, false);
        }
        if (System.currentTimeMillis() < ClientState.toastUntil && ClientState.toastLine != null && !ClientState.toastLine.isEmpty()) {
            g.drawString(mc.font, Ui.trim(mc.font, ClientState.toastLine, w - 20), 8, 20, Ui.AMBER, false);
        }
        if (ClientState.emp > 0) {
            for (int i = 0; i < 8; i++) {
                int y = (glitch * 17 + i * 23) % Math.max(1, mc.getWindow().getGuiScaledHeight());
                g.fill(0, y, w, y + 1, 0x663DFF9A);
            }
            String dead = Component.translatable("blackchannel.hud.emp").getString();
            g.drawString(mc.font, dead, w / 2 - mc.font.width(dead) / 2, 28, Ui.EDGE, false);
        }
        if (ClientState.bomb > 0 && mc.screen == null) {
            String spam = Component.translatable("blackchannel.spam." + (1 + (glitch / 12) % 6)).getString();
            g.drawString(mc.font, spam, 6 + (glitch % 5), 40 + (glitch % 7), Ui.RED, false);
        }
        if (ClientState.radarHud && mc.screen == null && ClientState.emp <= 0) {
            int cx = w - 42;
            int cy = 42;
            g.fill(cx - 28, cy - 28, cx + 28, cy + 28, 0xA0101418);
            g.fill(cx, cy - 28, cx + 1, cy + 28, 0xFF1E4A38);
            g.fill(cx - 28, cy, cx + 28, cy + 1, 0xFF1E4A38);
            for (ClientState.Blip blip : ClientState.blips) {
                Ui.blip(g, cx, cy, 26, ClientState.radarR, blip.dx(), blip.dz(), Ui.blipColor(blip.kind()));
            }
        }
    }
}
