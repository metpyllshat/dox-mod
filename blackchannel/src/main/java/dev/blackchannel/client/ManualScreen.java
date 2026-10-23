package dev.blackchannel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ManualScreen extends Screen {
    private static final int PAGES = 10;
    private final List<Ui.Hit> hits = new ArrayList<>();
    private int page;

    public ManualScreen() {
        super(Component.translatable("screen.blackchannel.manual"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        int w = 280;
        int h = 200;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        Ui.panel(g, x, y, w, h);
        String title = Component.translatable("manual.blackchannel." + page + ".t").getString();
        String body = Component.translatable("manual.blackchannel." + page + ".b").getString();
        g.drawString(font, Ui.trim(font, title, w - 16), x + 8, y + 8, Ui.EDGE, false);
        int ly = y + 24;
        for (String line : Ui.wrap(font, body, w - 16)) {
            if (ly > y + h - 28) break;
            g.drawString(font, line, x + 8, ly, Ui.TEXT, false);
            ly += 10;
        }
        Ui.button(g, font, hits, x + 8, y + h - 18, 40, 12, "<", false, "prev", "");
        Ui.button(g, font, hits, x + w - 48, y + h - 18, 40, 12, ">", false, "next", "");
        g.drawString(font, (page + 1) + " / " + PAGES, x + w / 2 - 12, y + h - 15, Ui.MUTED, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if ("prev".equals(hit.action())) page = (page + PAGES - 1) % PAGES;
            if ("next".equals(hit.action())) page = (page + 1) % PAGES;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
