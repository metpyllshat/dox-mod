package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Optics;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class LaptopScreen extends AbstractContainerScreen<Menus.LaptopMenu> {
    private final List<Ui.Hit> hits = new ArrayList<>();
    private EditBox input;
    private EditBox priceBox;
    private int selected = -1;
    private int page;
    private int price = 24;
    private boolean wipeArmed;
    private boolean dragPrice;

    public LaptopScreen(Menus.LaptopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 338;
        this.imageHeight = 252;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 8, topPos + 148, 160, 12, Component.empty());
        input.setMaxLength(64);
        input.setBordered(false);
        input.setTextColor(Ui.EDGE);
        addRenderableWidget(input);
        priceBox = new EditBox(font, leftPos + 250, topPos + 168, 72, 12, Component.empty());
        priceBox.setMaxLength(4);
        priceBox.setBordered(false);
        priceBox.setTextColor(Ui.AMBER);
        priceBox.setValue(String.valueOf(price));
        priceBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        addRenderableWidget(priceBox);
        setInitialFocus(input);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, x, y, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0C1210);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        super.render(g, mx, my, pt);
        drawChrome(g, mx, my);
        Ui.tips(g, font, hits, mx, my);
    }

    private void drawChrome(GuiGraphics g, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        g.drawString(font, Ui.trim(font, "TERMINAL  " + ClientState.handle + "  #" + ClientState.crypto, imageWidth - 16), x + 8, y + 6, Ui.EDGE, false);
        String net = ClientState.uplink ? (ClientState.dish ? "DISH" : "RACK") : "NO UPLINK";
        g.drawString(font, net + "  CD T" + ClientState.cdT + " D" + ClientState.cdD + " S" + ClientState.cdS + " B" + ClientState.cdB, x + 8, y + 16, Ui.MUTED, false);
        Ui.button(g, font, hits, x + 6, y + 28, 78, 12, Ui.t("screen.blackchannel.tab.ops"), null, page == 0, "page", "0");
        Ui.button(g, font, hits, x + 88, y + 28, 78, 12, Ui.t("screen.blackchannel.tab.map"), tipOptic(), page == 1, "page", "1");
        Ui.button(g, font, hits, x + 170, y + 28, 78, 12, Ui.t("screen.blackchannel.tab.bag"), tipPeek(), page == 2, "page", "2");
        Ui.button(g, font, hits, x + 252, y + 28, 78, 12, Ui.t("screen.blackchannel.tab.sell"), Ui.t("screen.blackchannel.tip.sell_item"), page == 3, "page", "3");
        if (page == 0) drawOps(g, x, y);
        else if (page == 1) drawMap(g, x, y);
        else if (page == 2) drawBag(g, x, y);
        else drawSell(g, x, y);
        g.drawString(font, "SLOT", x + Menus.LaptopMenu.SALE_X, y + Menus.LaptopMenu.SALE_Y - 10, Ui.MUTED, false);
    }

    private void drawOps(GuiGraphics g, int x, int y) {
        int by = y + 44;
        Ui.button(g, font, hits, x + 6, by, 52, 12, "TRACE", tipTrace(), false, Actions.TRACE, "");
        Ui.button(g, font, hits, x + 60, by, 58, 12, "DEANON", tipDeanon(), false, Actions.DEANON, "");
        Ui.button(g, font, hits, x + 120, by, 50, 12, "SWAT", tipSwat(), false, Actions.SWAT, "");
        Ui.button(g, font, hits, x + 172, by, 50, 12, "BOMB", tipBomb(), false, Actions.BOMB, "");
        Ui.button(g, font, hits, x + 224, by, 40, 12, "UAV", tipUav(), false, Actions.UAV, "");
        Ui.button(g, font, hits, x + 266, by, 64, 12, "WIPE", Ui.t("screen.blackchannel.tip.wipe"), wipeArmed, Actions.WIPE, "");
        g.drawString(font, Ui.trim(font, ClientState.resultLine(), imageWidth - 16), x + 8, y + 60, Ui.AMBER, false);
        int ly = y + 72;
        g.drawString(font, "INTEL", x + 8, ly, Ui.MUTED, false);
        ly += 10;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > y + 136) break;
            boolean on = row.id == selected;
            String label = "#" + row.id + " " + row.handle + " ±" + row.err;
            String tip = row.handle + "  " + row.dim + "  " + row.x + " " + row.y + " " + row.z + "  ±" + row.err;
            Ui.button(g, font, hits, x + 6, ly, 190, 11, Ui.trim(font, label, 180), tip, on, "pick", String.valueOf(row.id));
            ly += 12;
        }
        int ry = y + 72;
        g.drawString(font, "KNOWN", x + 214, ry, Ui.MUTED, false);
        ry += 10;
        for (ClientState.DeanonRow row : ClientState.deanons) {
            if (ry > y + 136) break;
            g.drawString(font, Ui.trim(font, row.handle() + "=" + row.name(), 110), x + 214, ry, Ui.BLUE, false);
            ry += 10;
        }
    }

    private void drawMap(GuiGraphics g, int x, int y) {
        g.drawString(font, Ui.trim(font, ClientState.resultLine(), 250), x + 8, y + 44, Ui.AMBER, false);
        int ox = x + 8;
        int oy = y + 56;
        byte[] cells = ClientState.optic;
        int[] colors = ClientState.opticColor;
        for (int gz = 0; gz < Optics.GRID; gz++) {
            for (int gx = 0; gx < Optics.GRID; gx++) {
                int i = gz * Optics.GRID + gx;
                int color = 0xFF07110C;
                if (i < colors.length && colors[i] != 0) color = 0xFF000000 | colors[i];
                else if (i < cells.length && cells[i] != 0) color = 0xFF000000 | (80 + cells[i]) * 0x010101;
                g.fill(ox + gx * 7, oy + gz * 7, ox + gx * 7 + 6, oy + gz * 7 + 6, color);
            }
        }
        if (ClientState.opticHx >= 0) {
            int px = ox + ClientState.opticHx * 7;
            int py = oy + ClientState.opticHz * 7;
            g.fill(px, py, px + 5, py + 5, Ui.RED);
        }
        g.drawString(font, "CONF " + ClientState.opticConf + (ClientState.opticBearing.isEmpty() ? "" : "  " + ClientState.opticBearing), x + 130, y + 56, Ui.MUTED, false);
        g.drawString(font, Ui.t("screen.blackchannel.map_note"), x + 130, y + 68, Ui.MUTED, false);
        Ui.button(g, font, hits, x + 130, y + 88, 90, 12, Ui.t("screen.blackchannel.btn.snap"), tipOptic(), false, Actions.OPTIC, "");
    }

    private void drawBag(GuiGraphics g, int x, int y) {
        g.drawString(font, Ui.trim(font, ClientState.resultLine(), imageWidth - 16), x + 8, y + 44, Ui.AMBER, false);
        for (int i = 0; i < 36; i++) {
            int col = i % 9;
            int row = i / 9;
            drawPeek(g, x + 8 + col * 18, y + 56 + row * 18, findPeek(i));
        }
        for (int i = 0; i < 5; i++) {
            drawPeek(g, x + 8 + i * 18, y + 132, findPeek(36 + i));
        }
        Ui.button(g, font, hits, x + 214, y + 56, 90, 12, Ui.t("screen.blackchannel.btn.snap"), tipPeek(), false, Actions.PEEK, "");
    }

    private void drawPeek(GuiGraphics g, int sx, int sy, ClientState.PeekSlot slot) {
        g.fill(sx, sy, sx + 16, sy + 16, slot != null && slot.hidden() ? 0xFF3A1820 : 0xFF0C1210);
        if (slot == null || slot.id() == null || slot.id().isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(slot.id());
        if (id == null) return;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return;
        ItemStack stack = new ItemStack(item, Math.max(1, slot.count()));
        g.renderItem(stack, sx, sy);
        g.renderItemDecorations(font, stack, sx, sy);
    }

    private ClientState.PeekSlot findPeek(int index) {
        for (ClientState.PeekSlot slot : ClientState.peek) if (slot.slot() == index) return slot;
        return null;
    }

    private void drawSell(GuiGraphics g, int x, int y) {
        g.drawString(font, Ui.t("screen.blackchannel.sell_note"), x + 8, y + 48, Ui.MUTED, false);
        g.drawString(font, Ui.trim(font, ClientState.resultLine(), imageWidth - 16), x + 8, y + 60, Ui.AMBER, false);
        syncPriceFromBox();
        int sx = x + 214;
        int sy = y + 184;
        g.fill(sx, sy, sx + 110, sy + 6, 0xFF0C1210);
        int knob = (int) ((price - 1) / 4999f * 106);
        g.fill(sx + knob, sy - 1, sx + knob + 4, sy + 7, Ui.EDGE);
        Ui.button(g, font, hits, x + 214, y + 200, 110, 12, "SELL ITEM", Ui.t("screen.blackchannel.tip.sell_item"), false, "sell-item", "");
        Ui.button(g, font, hits, x + 214, y + 216, 110, 12, "SELL INTEL", Ui.t("screen.blackchannel.tip.sell_intel"), false, Actions.SELL, "");
    }

    private String tipTrace() {
        return ClientState.tip("screen.blackchannel.tip.trace",
                ClientState.cfg("traceCost", 2), ClientState.cfg("traceBase", 500), ClientState.cfg("traceVpn", 50),
                ClientState.cfg("traceFail", 5), ClientState.cfg("traceFailVpn", 12), ClientState.cfg("traceFailDeep", 35),
                ClientState.cfg("traceStale", 40));
    }

    private String tipDeanon() {
        return ClientState.tip("screen.blackchannel.tip.deanon", ClientState.cfg("deanonCost", 4));
    }

    private String tipSwat() {
        return ClientState.tip("screen.blackchannel.tip.swat", ClientState.cfg("swatCost", 40),
                ClientState.cfg("swatContract", 8), ClientState.cfg("swatDelay", 50));
    }

    private String tipBomb() {
        return ClientState.tip("screen.blackchannel.tip.bomb", ClientState.cfg("bombCost", 3));
    }

    private String tipUav() {
        return ClientState.tip("screen.blackchannel.tip.uav", ClientState.cfg("uavCost", 3),
                ClientState.cfg("uavRange", 96), ClientState.cfg("uavError", 30));
    }

    private String tipOptic() {
        return ClientState.tip("screen.blackchannel.tip.optic", ClientState.cfg("opticCost", 6));
    }

    private String tipPeek() {
        return ClientState.tip("screen.blackchannel.tip.peek", ClientState.cfg("peekCost", 8));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (page == 3 && mx >= leftPos + 214 && mx <= leftPos + 324 && my >= topPos + 180 && my <= topPos + 194) {
            dragPrice = true;
            setPriceFromMouse(mx);
            return true;
        }
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if ("page".equals(hit.action())) {
                page = Integer.parseInt(hit.arg());
                return true;
            }
            if ("pick".equals(hit.action())) {
                selected = Integer.parseInt(hit.arg());
                for (ClientState.IntelRow row : ClientState.intel) {
                    if (row.id == selected) input.setValue(row.handle);
                }
                return true;
            }
            String arg = input == null ? "" : input.getValue().trim();
            if (Actions.SWAT.equals(hit.action()) || Actions.OPTIC.equals(hit.action()) || Actions.PEEK.equals(hit.action())) {
                arg = String.valueOf(selected);
            }
            if ("sell-item".equals(hit.action())) {
                send(Actions.SELL, "item " + readPrice());
                return true;
            }
            if (Actions.SELL.equals(hit.action())) arg = "intel " + selected + " " + readPrice();
            if (Actions.WIPE.equals(hit.action())) {
                if (!wipeArmed) {
                    wipeArmed = true;
                    return true;
                }
                arg = "confirm";
            }
            send(hit.action(), arg);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPrice) {
            setPriceFromMouse(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragPrice = false;
        return super.mouseReleased(mx, my, button);
    }

    private void setPriceFromMouse(double mx) {
        float t = (float) ((mx - (leftPos + 214)) / 110.0);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        price = 1 + Math.round(t * 4999);
        if (priceBox != null) priceBox.setValue(String.valueOf(price));
    }

    private void syncPriceFromBox() {
        if (priceBox == null || priceBox.getValue().isEmpty()) return;
        try {
            price = Math.max(1, Math.min(5000, Integer.parseInt(priceBox.getValue())));
        } catch (NumberFormatException ignored) {
        }
    }

    private int readPrice() {
        syncPriceFromBox();
        if (priceBox != null) priceBox.setValue(String.valueOf(price));
        return price;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && input != null && input.isFocused()) {
            send(Actions.TRACE, input.getValue().trim());
            return true;
        }
        boolean typing = (input != null && input.isFocused()) || (priceBox != null && priceBox.isFocused());
        if (typing && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) return true;
        return super.keyPressed(key, scan, mods);
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
