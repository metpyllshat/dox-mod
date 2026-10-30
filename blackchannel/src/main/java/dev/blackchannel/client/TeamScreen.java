package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Teams;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Меню выбора команды до старта матча. */
public class TeamScreen extends Screen {
    private final List<Ui.Hit> hits = new ArrayList<>();

    public TeamScreen() {
        super(Component.translatable("screen.blackchannel.team.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int colorOf(Teams.Team team) {
        return 0xFF000000 | team.rgb();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        int w = 250;
        int rowH = 16;
        int h = 54 + Teams.ALL.size() * (rowH + 2);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        Ui.panel(g, x, y, w, h);
        g.drawString(font, Ui.t("screen.blackchannel.team.title"), x + 8, y + 8, Ui.EDGE, false);
        g.drawString(font, Ui.trim(font, Ui.t("screen.blackchannel.team.note"), w - 16), x + 8, y + 22, Ui.MUTED, false);
        String current = ClientState.crew == null ? "" : ClientState.crew;
        int rowY = y + 38;
        for (Teams.Team team : Teams.ALL) {
            boolean picked = team.id().equals(current);
            int bx = x + 8;
            int bw = w - 16;
            g.fill(bx, rowY, bx + bw, rowY + rowH, picked ? 0xFF16382C : 0xFF12181C);
            g.fill(bx, rowY, bx + 4, rowY + rowH, colorOf(team));
            g.drawString(font, team.name(), bx + 10, rowY + 4, picked ? Ui.TEXT : Ui.MUTED, false);
            if (picked) {
                String mark = Ui.t("screen.blackchannel.team.current");
                g.drawString(font, mark, bx + bw - font.width(mark) - 6, rowY + 4, Ui.EDGE, false);
            }
            hits.add(new Ui.Hit(bx, rowY, bw, rowH, "team", team.id(), null));
            rowY += rowH + 2;
        }
        Ui.button(g, font, hits, x + 8, rowY + 2, w - 16, 14, Ui.t("screen.blackchannel.team.close"), false, "close", "");
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if ("close".equals(hit.action())) {
                onClose();
                return true;
            }
            if ("team".equals(hit.action())) {
                if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(Actions.TEAM, hit.arg()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
