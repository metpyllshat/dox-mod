package dev.blackchannel.client;

import dev.blackchannel.logic.Teams;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Админ-панель для теста: матч, игрок, отряд и чек-лист того, что надо потрогать.
 * <p>
 * Открывается командой {@code /bc admin} (второй уровень прав). Кнопки не делают
 * ничего сами — они шлют строку {@code admin.<действие>} на сервер, и уже там
 * проверяются права. Поэтому панель безопасна: без оператора она просто не откроется.
 */
public class AdminScreen extends Screen {
    private static final int W = 320;
    private static final int H = 232;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private EditBox sizeBox;
    private int tab;
    private int scroll;

    public AdminScreen() {
        super(Component.translatable("screen.blackchannel.admin.title"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - W / 2;
        int y = this.height / 2 - H / 2;
        sizeBox = new EditBox(this.font, x + 52, y + 72, 28, 10, Component.empty());
        sizeBox.setMaxLength(5);
        sizeBox.setBordered(false);
        sizeBox.setTextColor(Ui.AMBER);
        sizeBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        sizeBox.setValue("1000");
        addRenderableWidget(sizeBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.mouse(mx, my);
        renderBackground(g);
        hits.clear();
        int x = this.width / 2 - W / 2;
        int y = this.height / 2 - H / 2;
        Ui.panel(g, x, y, W, H);
        g.drawString(this.font, Ui.t("screen.blackchannel.admin.title"), x + 8, y + 7, Ui.AMBER, false);
        String tag = "OP · " + (ClientState.matchActive ? Ui.t("screen.blackchannel.admin.on") : Ui.t("screen.blackchannel.admin.off"));
        g.drawString(this.font, tag, x + W - 8 - this.font.width(tag), y + 7, ClientState.matchActive ? Ui.GREEN : Ui.MUTED, false);
        g.drawString(this.font, Ui.trim(this.font, statusLine(), W - 16), x + 8, y + 18, Ui.MUTED, false);
        String[] tabs = {
                Ui.t("screen.blackchannel.admin.tab.match"),
                Ui.t("screen.blackchannel.admin.tab.player"),
                Ui.t("screen.blackchannel.admin.tab.team"),
                Ui.t("screen.blackchannel.admin.tab.check")
        };
        int tw = (W - 16 - 9) / 4;
        for (int i = 0; i < tabs.length; i++) {
            Ui.tab(g, this.font, hits, x + 8 + i * (tw + 3), y + 30, tw, 15, tabs[i], tab == i, "tab", String.valueOf(i));
        }
        Ui.well(g, x + 6, y + 48, W - 12, H - 78);
        if (sizeBox != null) sizeBox.visible = tab == 0;
        switch (tab) {
            case 0 -> match(g, x, y);
            case 1 -> player(g, x, y);
            case 2 -> teams(g, x, y);
            default -> checklist(g, x, y);
        }
        String hint = Ui.t("screen.blackchannel.admin.hint");
        g.drawString(this.font, hint, x + 8, y + H - 22, 0xFF3E4A46, false);
        for (var renderable : renderables) {
            if (tab == 0) renderable.render(g, mx, my, pt);
        }
        Ui.tips(g, this.font, hits, mx, my);
    }

    private String statusLine() {
        if (!ClientState.matchActive) {
            return ClientState.tip("screen.blackchannel.admin.status_idle", ClientState.crypto, ClientState.handle);
        }
        int left = ClientState.matchTimeLeftSeconds;
        String time = String.format("%d:%02d", left / 60, left % 60);
        return ClientState.tip("screen.blackchannel.admin.status_live", time, ClientState.aliveCount, ClientState.noise, ClientState.crypto);
    }

    // ---------- матч ----------

    private void match(GuiGraphics g, int x, int y) {
        int ly = y + 54;
        int w = (W - 16 - 8) / 3;
        Ui.button(g, this.font, hits, x + 8, ly, w, 14, Ui.t("screen.blackchannel.admin.center"),
                Ui.t("screen.blackchannel.admin.center_tip"), false, "admin.center", "");
        Ui.button(g, this.font, hits, x + 8 + w + 4, ly, w, 14, Ui.t("screen.blackchannel.admin.start"), null,
                ClientState.matchActive, "admin.start", "");
        Ui.button(g, this.font, hits, x + 8 + (w + 4) * 2, ly, w, 14, Ui.t("screen.blackchannel.admin.stop"), null,
                false, "admin.stop", "");
        ly += 18;
        g.drawString(this.font, Ui.t("screen.blackchannel.admin.size"), x + 8, ly + 2, Ui.MUTED, false);
        Ui.button(g, this.font, hits, x + 84, ly, 40, 12, "1000", false, "admin.size", "1000");
        Ui.button(g, this.font, hits, x + 128, ly, 40, 12, "1500", false, "admin.size", "1500");
        Ui.button(g, this.font, hits, x + 172, ly, 40, 12, "2000", false, "admin.size", "2000");
        Ui.button(g, this.font, hits, x + 216, ly, 22, 12, "✓", Ui.t("screen.blackchannel.admin.size_apply"), false, "admin.sizes", "");
        ly += 18;
        Ui.button(g, this.font, hits, x + 8, ly, 150, 14, Ui.t("screen.blackchannel.admin.final"),
                Ui.t("screen.blackchannel.admin.final_tip"), false, "admin.final", "");
        Ui.button(g, this.font, hits, x + 162, ly, 150, 14, Ui.t("screen.blackchannel.admin.status"),
                null, false, "admin.status", "");
        ly += 22;
        Ui.divider(g, x + 8, ly, W - 16);
        ly += 6;
        g.drawString(this.font, Ui.t("screen.blackchannel.admin.scores"), x + 8, ly, Ui.MUTED, false);
        ly += 11;
        if (ClientState.teamScores.isEmpty()) {
            g.drawString(this.font, Ui.t("screen.blackchannel.admin.no_teams"), x + 8, ly, 0xFF3E4A46, false);
        } else {
            int i = 0;
            for (ClientState.TeamScore score : ClientState.teamScores.values()) {
                if (ly > y + H - 34) break;
                String label = Teams.nameOf(score.code) + "  " + score.score;
                String right = score.alive + "/" + score.total;
                Ui.row(g, this.font, x + 8, ly, W - 16, 11, label, right, false, Ui.TEXT);
                ly += 12;
                if (++i > 7) break;
            }
        }
    }

    // ---------- игрок ----------

    private void player(GuiGraphics g, int x, int y) {
        int ly = y + 54;
        int w = (W - 16 - 8) / 3;
        Ui.button(g, this.font, hits, x + 8, ly, w, 14, Ui.t("screen.blackchannel.admin.kit"),
                Ui.t("screen.blackchannel.admin.kit_tip"), false, "admin.kit", "");
        Ui.button(g, this.font, hits, x + 8 + w + 4, ly, w, 14, Ui.t("screen.blackchannel.admin.coins"), null, false, "admin.coins", "");
        Ui.button(g, this.font, hits, x + 8 + (w + 4) * 2, ly, w, 14, Ui.t("screen.blackchannel.admin.newhandle"), null, false, "admin.handle", "");
        ly += 18;
        Ui.button(g, this.font, hits, x + 8, ly, w, 14, Ui.t("screen.blackchannel.admin.noise"),
                Ui.t("screen.blackchannel.admin.noise_tip"), false, "admin.noise", "");
        Ui.button(g, this.font, hits, x + 8 + w + 4, ly, w, 14, Ui.t("screen.blackchannel.admin.flood"), null, false, "admin.flood", "");
        Ui.button(g, this.font, hits, x + 8 + (w + 4) * 2, ly, w, 14, Ui.t("screen.blackchannel.admin.emp"),
                Ui.t("screen.blackchannel.admin.emp_tip"), false, "admin.emp", "");
        ly += 18;
        Ui.button(g, this.font, hits, x + 8, ly, w, 14, Ui.t("screen.blackchannel.admin.tpbase"), null, false, "admin.tpbase", "");
        Ui.button(g, this.font, hits, x + 8 + w + 4, ly, w, 14, Ui.t("screen.blackchannel.admin.tpcenter"), null, false, "admin.tpcenter", "");
        Ui.button(g, this.font, hits, x + 8 + (w + 4) * 2, ly, w, 14, Ui.t("screen.blackchannel.admin.tptrace"), null, false, "admin.teleport0", "");
        ly += 18;
        Ui.button(g, this.font, hits, x + 8, ly, w, 14, Ui.t("screen.blackchannel.admin.reset"),
                Ui.t("screen.blackchannel.admin.reset_tip"), false, "admin.reset", "");
        ly += 22;
        Ui.divider(g, x + 8, ly, W - 16);
        ly += 6;
        g.drawString(this.font, Ui.t("screen.blackchannel.admin.player_state"), x + 8, ly, Ui.MUTED, false);
        ly += 11;
        String[] lines = {
                ClientState.tip("screen.blackchannel.admin.line_handle", ClientState.handle, ClientState.crypto),
                ClientState.tip("screen.blackchannel.admin.line_signal", ClientState.signal, ClientState.battery, ClientState.noise),
                ClientState.tip("screen.blackchannel.admin.line_buffs", ClientState.vpn, ClientState.ids, ClientState.hard),
                ClientState.tip("screen.blackchannel.admin.line_link", ClientState.uplink, ClientState.dish, ClientState.sat)
        };
        for (String line : lines) {
            if (ly > y + H - 30) break;
            g.drawString(this.font, Ui.trim(this.font, line, W - 16), x + 8, ly, Ui.TEXT, false);
            ly += 11;
        }
    }

    // ---------- отряд ----------

    private void teams(GuiGraphics g, int x, int y) {
        int ly = y + 54;
        int w = (W - 16 - 12) / 4;
        int i = 0;
        for (Teams.Team team : Teams.ALL) {
            int bx = x + 8 + (i % 4) * (w + 4);
            int by = ly + (i / 4) * 18;
            Ui.button(g, this.font, hits, bx, by, w, 14, team.name(), null, team.id().equals(ClientState.crew), "admin.team", team.id());
            i++;
        }
        ly += 40;
        Ui.divider(g, x + 8, ly, W - 16);
        ly += 6;
        g.drawString(this.font, Ui.t("screen.blackchannel.admin.team_note"), x + 8, ly, Ui.MUTED, false);
        ly += 12;
        for (String line : Ui.wrap(this.font, Ui.t("screen.blackchannel.admin.team_help"), W - 16)) {
            if (ly > y + H - 30) break;
            g.drawString(this.font, line, x + 8, ly, Ui.TEXT, false);
            ly += 10;
        }
    }

    // ---------- чек-лист ----------

    private void checklist(GuiGraphics g, int x, int y) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            String key = "screen.blackchannel.admin.check." + i;
            String line = Ui.t(key);
            if (line.equals(key)) break;
            lines.addAll(Ui.wrap(this.font, "□ " + line, W - 24));
        }
        int visible = Math.max(1, (H - 84) / 10);
        scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible), scroll));
        int ly = y + 54;
        for (int i = scroll; i < lines.size() && i < scroll + visible; i++) {
            g.drawString(this.font, lines.get(i), x + 10, ly, Ui.TEXT, false);
            ly += 10;
        }
        String more = (scroll + 1) + "/" + Math.max(1, lines.size());
        g.drawString(this.font, more, x + W - 8 - this.font.width(more), y + H - 34, 0xFF3E4A46, false);
    }

    // ---------- ввод ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if ("tab".equals(hit.action())) {
                tab = Integer.parseInt(hit.arg());
                scroll = 0;
                if (sizeBox != null) sizeBox.visible = tab == 0;
                return true;
            }
            if ("admin.sizes".equals(hit.action())) {
                send("admin.size", sizeBox == null ? "1000" : sizeBox.getValue());
                return true;
            }
            send(hit.action(), hit.arg());
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 3) scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
