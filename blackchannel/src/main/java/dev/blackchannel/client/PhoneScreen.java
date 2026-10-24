package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Catalog;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class PhoneScreen extends AbstractContainerScreen<Menus.PhoneMenu> {
    private final List<Ui.Hit> hits = new ArrayList<>();
    private EditBox input;
    private int tab;
    private int scroll;
    private String dmTarget = "";

    public PhoneScreen(Menus.PhoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 230;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 8, topPos + imageHeight - 18, imageWidth - 58, 12, Component.empty());
        input.setMaxLength(160);
        input.setBordered(false);
        input.setTextColor(Ui.TEXT);
        addRenderableWidget(input);
        setInitialFocus(input);
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
        String bars = "|".repeat(Math.max(0, Math.min(4, ClientState.signal)));
        g.drawString(font, Ui.trim(font, ClientState.handle + "  " + bars + "  " + ClientState.battery + "%  #" + ClientState.crypto, imageWidth - 12), x + 6, y + 6, Ui.TEXT, false);
        String state = ClientState.airplane ? "AIR" : ClientState.emp > 0 ? "EMP" : ClientState.jammed ? "JAM" : ClientState.faraday ? "CAGE" : ClientState.uplink ? "UP" : "MESH";
        g.drawString(font, state + (ClientState.vpn > 0 ? " VPN" + ClientState.vpn : ""), x + 6, y + 16, Ui.AMBER, false);
        Ui.button(g, font, hits, x + 6, y + 28, 56, 12, Ui.t("screen.blackchannel.tab.chat"), tab == 0, "tab", "0");
        Ui.button(g, font, hits, x + 64, y + 28, 56, 12, Ui.t("screen.blackchannel.tab.radar"), tab == 1, "tab", "1");
        Ui.button(g, font, hits, x + 122, y + 28, 58, 12, Ui.t("screen.blackchannel.tab.market"), tab == 2, "tab", "2");
        Ui.button(g, font, hits, x + 182, y + 28, 58, 12, Ui.t("screen.blackchannel.tab.gear"), tab == 3, "tab", "3");
        if (tab == 0) drawChat(g, x, y);
        else if (tab == 1) drawRadar(g, x, y);
        else if (tab == 2) drawMarket(g, x, y);
        else drawGear(g, x, y);
        g.fill(x + 6, y + imageHeight - 22, x + imageWidth - 6, y + imageHeight - 6, 0xFF0C1210);
        for (var renderable : renderables) renderable.render(g, mx, my, pt);
        Ui.tips(g, font, hits, mx, my);
    }

    private void drawChat(GuiGraphics g, int x, int y) {
        Ui.button(g, font, hits, x + 6, y + 44, 52, 12, "DARK", "darknet".equals(ClientState.channel), Actions.CHAN, "darknet");
        Ui.button(g, font, hits, x + 60, y + 44, 52, 12, "LOCAL", "local".equals(ClientState.channel), Actions.CHAN, "local");
        Ui.button(g, font, hits, x + 114, y + 44, 40, 12, "DM", "dm".equals(ClientState.channel), "dm-mode", "");
        int cy = y + 60;
        int shown = 0;
        int start = Math.max(0, ClientState.chat.size() - 8 - scroll);
        for (int i = start; i < ClientState.chat.size() && shown < 8; i++) {
            ClientState.ChatRow row = ClientState.chat.get(i);
            String line = row.from() + ": " + row.text();
            g.drawString(font, Ui.trim(font, line, imageWidth - 16), x + 8, cy, row.enc() ? Ui.BLUE : Ui.TEXT, false);
            cy += 10;
            shown++;
        }
        int hx = x + 160;
        int hy = y + 60;
        for (String contact : ClientState.contacts) {
            if (hy > y + 150) break;
            Ui.button(g, font, hits, hx, hy, 80, 11, contact, contact.equals(dmTarget), "pick-dm", contact);
            hy += 12;
        }
        g.drawString(font, Ui.t("screen.blackchannel.send_hint"), x + 8, y + imageHeight - 32, Ui.MUTED, false);
    }

    private void drawRadar(GuiGraphics g, int x, int y) {
        int cx = x + 58;
        int cy = y + 110;
        g.fill(cx - 46, cy - 46, cx + 46, cy + 46, 0xFF07110C);
        g.fill(cx, cy - 46, cx + 1, cy + 46, 0xFF1E4A38);
        g.fill(cx - 46, cy, cx + 46, cy + 1, 0xFF1E4A38);
        for (ClientState.Blip blip : ClientState.blips) {
            Ui.blip(g, cx, cy, 46, ClientState.radarR, blip.dx(), blip.dz(), Ui.blipColor(blip.kind()));
        }
        if (minecraft != null && minecraft.player != null) Ui.selfArrow(g, cx, cy, minecraft.player.getYRot(), 8);
        int ly = y + 58;
        int n = 0;
        for (ClientState.Blip blip : ClientState.blips) {
            if (n++ >= 6) break;
            String kind = Ui.t("blackchannel.blip." + blip.kind());
            g.drawString(font, Ui.trim(font, kind + " " + blip.label() + " " + blip.dist() + "m", 120), x + 112, ly, Ui.blipColor(blip.kind()), false);
            ly += 10;
        }
        ly += 4;
        for (ClientState.TrackRow track : ClientState.trackers) {
            if (ly > y + 180) break;
            String line = (track.live() ? "BUG " : "LAST ") + track.dim() + " " + track.x() + " " + track.y() + " " + track.z();
            g.drawString(font, Ui.trim(font, line, 220), x + 8, ly, track.live() ? Ui.AMBER : Ui.MUTED, false);
            ly += 10;
        }
        Ui.button(g, font, hits, x + 8, y + imageHeight - 40, 90, 12, Ui.t("screen.blackchannel.btn.radar_hud"),
                ClientState.tip("screen.blackchannel.tip.radar_hud", ClientState.cfg("radarPhone", 52), ClientState.cfg("radarLaptop", 110),
                        ClientState.cfg("structure", 360), ClientState.cfg("dishBonus", 80)),
                ClientState.radarHud, Actions.RADAR, "");
    }

    private void drawMarket(GuiGraphics g, int x, int y) {
        var entries = Catalog.entries();
        for (int i = 0; i < entries.size() && i < 16; i++) {
            Catalog.Entry entry = entries.get(i);
            int col = i / 8;
            int row = i % 8;
            String name = Ui.t("blackchannel.shop." + entry.id());
            Ui.button(g, font, hits, x + 6 + col * 118, y + 44 + row * 13, 114, 12, Ui.trim(font, name + " #" + entry.price(), 108), name + "  #" + entry.price(), false, Actions.BUY, entry.id());
        }
        int ly = y + 152;
        for (ClientState.BountyRow bounty : ClientState.bounties) {
            if (ly > y + 176) break;
            g.drawString(font, Ui.trim(font, Ui.t("blackchannel.shop.bounty") + " @" + bounty.handle() + " #" + bounty.reward(), 230), x + 8, ly, Ui.AMBER, false);
            ly += 10;
        }
        for (ClientState.ListingRow listing : ClientState.listings) {
            if (ly > y + 188) break;
            String label = "#" + listing.id + " " + (listing.intel ? listing.desc : listing.item) + " $" + listing.price;
            Ui.button(g, font, hits, x + 6, ly, 230, 11, Ui.trim(font, label, 220), false, Actions.BUY_LIST, String.valueOf(listing.id));
            ly += 12;
        }
    }

    private void drawGear(GuiGraphics g, int x, int y) {
        Ui.button(g, font, hits, x + 8, y + 48, 110, 12, Ui.t("screen.blackchannel.btn.airplane"), Ui.t("screen.blackchannel.tip.airplane"), ClientState.airplane, Actions.AIR, "");
        Ui.button(g, font, hits, x + 122, y + 48, 110, 12, "DND", Ui.t("screen.blackchannel.tip.dnd"), ClientState.dnd, Actions.DND, "");
        Ui.button(g, font, hits, x + 8, y + 64, 110, 12, "CREW", Ui.t("screen.blackchannel.tip.crew"), false, Actions.CREW, input == null ? "" : input.getValue());
        Ui.button(g, font, hits, x + 122, y + 64, 110, 12, Ui.t("screen.blackchannel.btn.manual"), false, "manual", "");
        g.drawString(font, "VPN " + ClientState.vpn + (ClientState.ids ? "  IDS" : "") + (ClientState.hard ? "  EMP+" : ""), x + 8, y + 84, Ui.TEXT, false);
        g.drawString(font, "SAT " + ClientState.sat + "  CREW " + ClientState.crew, x + 8, y + 96, Ui.MUTED, false);
        int ly = y + 112;
        for (ClientState.DeanonRow row : ClientState.deanons) {
            if (ly > y + 180) break;
            g.drawString(font, Ui.trim(font, row.handle() + " = " + row.name(), 220), x + 8, ly, Ui.BLUE, false);
            ly += 10;
        }
        g.drawString(font, Ui.trim(font, ClientState.resultLine(), imageWidth - 16), x + 8, y + imageHeight - 36, Ui.AMBER, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            onHit(hit);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void onHit(Ui.Hit hit) {
        switch (hit.action()) {
            case "tab" -> tab = Integer.parseInt(hit.arg());
            case "pick-dm" -> {
                dmTarget = hit.arg();
                send(Actions.CHAN, "dm:" + hit.arg());
            }
            case "dm-mode" -> send(Actions.CHAN, dmTarget.isEmpty() ? "dm:" : "dm:" + dmTarget);
            case "manual" -> minecraft.setScreen(new ManualScreen());
            case Actions.CREW -> send(Actions.CREW, input.getValue());
            default -> send(hit.action(), hit.arg());
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && input != null && input.isFocused()) {
            submit();
            return true;
        }
        if (typing(key, scan)) return true;
        return super.keyPressed(key, scan, mods);
    }

    private boolean typing(int key, int scan) {
        return input != null && input.isFocused() && minecraft != null && minecraft.options.keyInventory.matches(key, scan);
    }

    private void submit() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        if (text.startsWith("@")) {
            int sp = text.indexOf(' ');
            if (sp > 1) {
                send(Actions.DM, text.substring(1, sp) + " " + text.substring(sp + 1).trim());
                input.setValue("");
                return;
            }
        }
        if (tab == 3) send(Actions.CREW, text);
        else if ("dm".equals(ClientState.channel) && !dmTarget.isEmpty()) send(Actions.DM, dmTarget + " " + text);
        else send(Actions.SAY, text);
        input.setValue("");
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
