package dev.blackchannel.client;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

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
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::screen);
    }

    public static void screen(ScreenEvent.Render.Post event) {
        FloodHud.draw(event.getGuiGraphics());
    }

    public static void open(String id) {
        if ("manual".equals(id)) Minecraft.getInstance().setScreen(new ManualScreen());
    }

    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        if (mc.screen == null) FloodHud.draw(g);
        if (mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
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
                int y = (glitch * 17 + i * 23) % Math.max(1, h);
                g.fill(0, y, w, y + 1, 0x663DFF9A);
            }
            String dead = Component.translatable("blackchannel.hud.emp").getString();
            g.drawString(mc.font, dead, w / 2 - mc.font.width(dead) / 2, 28, Ui.EDGE, false);
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
            if (mc.player != null) Ui.selfArrow(g, cx, cy, mc.player.getYRot());
        }
        // Match HUD (справа)
        if (ClientState.matchActive) drawMatchHud(g, mc, w, h);
    }

    private static void drawMatchHud(GuiGraphics g, Minecraft mc, int w, int h) {
        int x = w - 160;
        int y = 4;
        int bg = 0xD007110C;
        g.fill(x - 4, y - 2, x + 158, y + 100, bg);
        // Время
        int minutes = ClientState.matchTimeLeftSeconds / 60;
        int seconds = ClientState.matchTimeLeftSeconds % 60;
        String time = String.format("%d:%02d", minutes, seconds);
        String timeLabel = ClientState.matchFinalActive
                ? Component.translatable("blackchannel.hud.final").getString() + " " + time
                : time;
        g.drawString(mc.font, timeLabel, x + 2, y + 2, Ui.AMBER, false);
        // Живые игроки
        g.drawString(mc.font, Component.translatable("blackchannel.hud.alive", ClientState.aliveCount).getString(), x + 2, y + 14, Ui.TEXT, false);
        // Статус игрока
        String status = ClientState.selfStatus;
        if (status != null && !status.isEmpty()) {
            String statusText;
            int statusColor = Ui.TEXT;
            switch (status) {
                case "downed" -> {
                    statusText = Component.translatable("blackchannel.hud.downed", ClientState.selfDownedSeconds).getString();
                    statusColor = Ui.RED;
                }
                case "dead" -> {
                    statusText = Component.translatable("blackchannel.hud.dead").getString();
                    statusColor = 0x555555;
                }
                case "final" -> {
                    statusText = Component.translatable("blackchannel.hud.final_state").getString();
                    statusColor = Ui.AMBER;
                }
                default -> {
                    statusText = Component.translatable("blackchannel.hud.alive_state").getString();
                    statusColor = Ui.TEXT;
                }
            }
            g.drawString(mc.font, statusText, x + 2, y + 26, statusColor, false);
        }
        // Команды
        int ly = y + 40;
        g.fill(x - 4, ly - 2, x + 158, ly + 1, Ui.EDGE);
        net.minecraft.ChatFormatting[] CF = net.minecraft.ChatFormatting.values();
        for (Map.Entry<String, ClientState.TeamScore> entry : ClientState.teamScores.entrySet()) {
            ClientState.TeamScore ts = entry.getValue();
            String line = ts.code + " - " + ts.score + " " +
                    Component.translatable("blackchannel.hud.pts").getString() +
                    " (" + ts.alive + "/" + ts.total + ")";
            int color = 0xFFFFFF;
            int cfIdx = ts.colorIdx % 26;
            if (cfIdx >= 0 && cfIdx < CF.length) {
                Integer c = CF[cfIdx].getColor();
                if (c != null) color = c;
            }
            g.drawString(mc.font, line, x + 2, ly, color, false);
            ly += 12;
            if (ly > h - 40) break;
        }
    }
}
