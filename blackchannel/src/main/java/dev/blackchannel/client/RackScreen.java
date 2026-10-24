package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class RackScreen extends AbstractContainerScreen<Menus.RackMenu> {
    private final List<Ui.Hit> hits = new ArrayList<>();

    public RackScreen(Menus.RackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 220;
        this.imageHeight = 168;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {}

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, x, y, imageWidth, imageHeight);
        g.drawString(font, Ui.t("screen.blackchannel.rack"), x + 8, y + 6, Ui.EDGE, false);
        int charge = Math.max(0, Math.min(1000, ClientState.rackCharge));
        g.drawString(font, charge + " / 1000", x + 8, y + 20, Ui.TEXT, false);
        g.fill(x + 8, y + 32, x + 212, y + 40, 0xFF0C1210);
        g.fill(x + 8, y + 32, x + 8 + (int) (204 * (charge / 1000f)), y + 40, Ui.EDGE);
        g.drawString(font, Ui.t("screen.blackchannel.rack_hint"), x + 8, y + 46, Ui.MUTED, false);
        Ui.button(g, font, hits, x + 8, y + 60, 96, 12, Ui.t("screen.blackchannel.btn.killswitch"), false, Actions.KILL, "");
        if (ClientState.rackHackable) {
            Ui.button(g, font, hits, x + 110, y + 60, 96, 12, Ui.t("screen.blackchannel.btn.hack"), false, Actions.HACK, "");
        }
        int ly = y + 80;
        for (int i = Math.max(0, ClientState.logs.size() - 6); i < ClientState.logs.size(); i++) {
            ClientState.LogRow row = ClientState.logs.get(i);
            String line = Component.translatable(row.key(), row.arg()).getString();
            g.drawString(font, Ui.trim(font, line, 200), x + 8, ly, Ui.AMBER, false);
            ly += 10;
        }
        g.drawString(font, Ui.trim(font, ClientState.resultFor("rack"), 200), x + 8, y + imageHeight - 14, Ui.TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(hit.action(), hit.arg()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
