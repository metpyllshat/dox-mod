package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class LaptopScreen extends AbstractContainerScreen<Menus.LaptopMenu> {
    private final List<Ui.Hit> hits = new ArrayList<>();
    private EditBox input;
    private int selected = -1;
    private boolean wipeArmed;

    public LaptopScreen(Menus.LaptopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 318;
        this.imageHeight = 206;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 8, topPos + imageHeight - 18, 180, 12, Component.empty());
        input.setMaxLength(64);
        input.setBordered(false);
        input.setTextColor(Ui.EDGE);
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
        g.drawString(font, Ui.trim(font, "TERMINAL  " + ClientState.handle + "  #" + ClientState.crypto, imageWidth - 16), x + 8, y + 6, Ui.EDGE, false);
        String net = ClientState.uplink ? (ClientState.dish ? "DISH" : "RACK") : "NO UPLINK";
        g.drawString(font, net + "  CD T" + ClientState.cdT + " D" + ClientState.cdD + " S" + ClientState.cdS + " B" + ClientState.cdB, x + 8, y + 16, Ui.MUTED, false);
        int by = y + 30;
        Ui.button(g, font, hits, x + 6, by, 58, 12, "TRACE", Ui.t("screen.blackchannel.tip.trace"), false, Actions.TRACE, "");
        Ui.button(g, font, hits, x + 66, by, 58, 12, "DEANON", Ui.t("screen.blackchannel.tip.deanon"), false, Actions.DEANON, "");
        Ui.button(g, font, hits, x + 126, by, 50, 12, "SWAT", Ui.t("screen.blackchannel.tip.swat"), false, Actions.SWAT, "");
        Ui.button(g, font, hits, x + 178, by, 50, 12, "BOMB", Ui.t("screen.blackchannel.tip.bomb"), false, Actions.BOMB, "");
        Ui.button(g, font, hits, x + 230, by, 40, 12, "UAV", Ui.t("screen.blackchannel.tip.uav"), false, Actions.UAV, "");
        Ui.button(g, font, hits, x + 272, by, 40, 12, "WIPE", Ui.t("screen.blackchannel.tip.wipe"), wipeArmed, Actions.WIPE, "");
        g.drawString(font, Ui.trim(font, ClientState.resultLine(), imageWidth - 16), x + 8, y + 46, Ui.AMBER, false);
        if (ClientState.ownStrikeT > 0) {
            g.drawString(font, "OUTBOUND T-" + (ClientState.ownStrikeT / 20), x + 8, y + 56, Ui.RED, false);
        }
        int ly = y + 68;
        g.drawString(font, "INTEL", x + 8, ly, Ui.MUTED, false);
        ly += 10;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > y + 160) break;
            boolean on = row.id == selected;
            String label = "#" + row.id + " " + row.handle + " ±" + row.err;
            String tip = row.handle + "  " + row.dim + "  " + row.x + " " + row.y + " " + row.z + "  ±" + row.err;
            Ui.button(g, font, hits, x + 6, ly, 200, 11, Ui.trim(font, label, 190), tip, on, "pick", String.valueOf(row.id));
            ly += 12;
        }
        int ry = y + 68;
        g.drawString(font, "KNOWN", x + 214, ry, Ui.MUTED, false);
        ry += 10;
        for (ClientState.DeanonRow row : ClientState.deanons) {
            if (ry > y + 150) break;
            g.drawString(font, Ui.trim(font, row.handle() + "=" + row.name(), 96), x + 214, ry, Ui.BLUE, false);
            ry += 10;
        }
        Ui.button(g, font, hits, x + 214, y + 140, 96, 12, "SELL INTEL", Ui.t("screen.blackchannel.tip.sell_intel"), false, Actions.SELL, "");
        Ui.button(g, font, hits, x + 214, y + 156, 96, 12, "SELL ITEM", Ui.t("screen.blackchannel.tip.sell_item"), false, "sell-item", "");
        g.fill(x + 6, y + imageHeight - 22, x + 200, y + imageHeight - 6, 0xFF0C1210);
        g.drawString(font, "handle / $price", x + 196, y + imageHeight - 16, Ui.MUTED, false);
        for (var renderable : renderables) renderable.render(g, mx, my, pt);
        Ui.tips(g, font, hits, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if ("pick".equals(hit.action())) {
                selected = Integer.parseInt(hit.arg());
                for (ClientState.IntelRow row : ClientState.intel) {
                    if (row.id == selected) input.setValue(row.handle);
                }
                return true;
            }
            String arg = input == null ? "" : input.getValue().trim();
            if (Actions.SWAT.equals(hit.action())) arg = String.valueOf(selected);
            if ("sell-item".equals(hit.action())) {
                int price = 12;
                if (arg.startsWith("$")) {
                    try { price = Integer.parseInt(arg.substring(1)); } catch (NumberFormatException ignored) {}
                }
                send(Actions.SELL, "item " + price);
                return true;
            }
            if (Actions.SELL.equals(hit.action())) {
                int price = 24;
                if (arg.startsWith("$")) {
                    try { price = Integer.parseInt(arg.substring(1)); } catch (NumberFormatException ignored) {}
                }
                arg = "intel " + selected + " " + price;
            }
            if (Actions.WIPE.equals(hit.action())) {
                if (!wipeArmed) {
                    wipeArmed = true;
                    return true;
                }
                arg = "confirm";
            }
            if (Actions.TRACE.equals(hit.action()) || Actions.DEANON.equals(hit.action()) || Actions.BOMB.equals(hit.action())) {
                // arg is the handle already in the box
            }
            send(hit.action(), arg);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && input != null && input.isFocused()) {
            send(Actions.TRACE, input.getValue().trim());
            return true;
        }
        if (input != null && input.isFocused() && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true;
        return super.keyPressed(key, scan, mods);
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
