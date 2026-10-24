package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Optics;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
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
    private static final int WIDTH = 260;
    private static final int HEIGHT = 222;
    private static final int PRICE_X = 32;
    private static final int PRICE_Y = 86;
    private static final int SLIDER_X = 100;
    private static final int SLIDER_Y = 88;
    private static final int SLIDER_W = 148;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private EditBox input;
    private EditBox priceBox;
    private int selected = -1;
    private int page;
    private int price = 24;
    private boolean wipeArmed;
    private boolean dragPrice;
    private ItemStack hoverStack = ItemStack.EMPTY;
    private boolean hoverHidden;

    public LaptopScreen(Menus.LaptopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        if (this.leftPos < 2) this.leftPos = 2;
        if (this.topPos < 2) this.topPos = 2;
        input = new EditBox(this.font, this.leftPos + 10, this.topPos + 109, WIDTH - 20, 12, Component.empty());
        input.setMaxLength(64);
        input.setBordered(false);
        input.setTextColor(Ui.TEXT);
        input.setHint(Component.translatable("screen.blackchannel.hint.handle"));
        addRenderableWidget(input);
        priceBox = new EditBox(this.font, this.leftPos + PRICE_X + 2, this.topPos + PRICE_Y + 2, 52, 12, Component.empty());
        priceBox.setMaxLength(4);
        priceBox.setBordered(false);
        priceBox.setTextColor(Ui.AMBER);
        priceBox.setValue(String.valueOf(price));
        priceBox.setHint(Component.translatable("screen.blackchannel.hint.price"));
        priceBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        addRenderableWidget(priceBox);
        setInitialFocus(input);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF101614);
        g.fill(x, y, x + WIDTH, y + 2, Ui.EDGE);
        g.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, Ui.EDGE_DIM);
        g.fill(x, y, x + 1, y + HEIGHT, Ui.EDGE_DIM);
        g.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, Ui.EDGE_DIM);
        g.fill(x + 6, y + 30, x + WIDTH - 6, y + 31, 0xFF1E4A38);
        g.fill(x + 6, y + 48, x + WIDTH - 6, y + HEIGHT - 6, 0xFF0C1210);
        if (page == 0) g.fill(x + 8, y + 106, x + WIDTH - 8, y + 120, 0xFF07110C);
        if (page == 3) {
            g.fill(x + PRICE_X, y + PRICE_Y, x + PRICE_X + 56, y + PRICE_Y + 12, 0xFF07110C);
            for (Slot slot : this.menu.slots) {
                if (!slot.isActive()) continue;
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF07110C);
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y, Ui.EDGE_DIM);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = ItemStack.EMPTY;
        hoverHidden = false;
        hits.clear();
        layoutSlots();
        syncWidgets();
        renderBackground(g);
        g.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(g, mx, my, pt);
        int x1 = Math.max(0, this.leftPos + 1);
        int y1 = Math.max(0, this.topPos + 1);
        int x2 = Math.min(this.width, this.leftPos + WIDTH - 1);
        int y2 = Math.min(this.height, this.topPos + HEIGHT - 1);
        boolean clip = x2 > x1 && y2 > y1;
        if (clip) g.enableScissor(x1, y1, x2, y2);
        drawChrome(g, mx, my);
        if (clip) g.disableScissor();
        if (!hoverStack.isEmpty()) g.renderTooltip(this.font, hoverStack, mx, my);
        else if (hoverHidden) noteTip(g, Ui.t("screen.blackchannel.peek_hidden"), mx, my);
        else Ui.tips(g, this.font, hits, mx, my);
    }

    private void drawChrome(GuiGraphics g, int mx, int my) {
        int x = this.leftPos;
        int y = this.topPos;
        String handle = Ui.trim(this.font, ClientState.handle, 150);
        String coins = "#" + ClientState.crypto;
        g.drawString(this.font, handle, x + 8, y + 7, Ui.EDGE, false);
        g.drawString(this.font, coins, x + WIDTH - 8 - this.font.width(coins), y + 7, Ui.AMBER, false);
        String status = (ClientState.uplink ? (ClientState.dish ? "DISH" : "RACK") : "NO UPLINK")
                + "  T" + ClientState.cdT + " D" + ClientState.cdD + " S" + ClientState.cdS + " B" + ClientState.cdB;
        int statusW = WIDTH - 16;
        if (page != 3 && !this.menu.getSlot(0).getItem().isEmpty()) {
            String held = Ui.t("screen.blackchannel.slot_held");
            int hw = this.font.width(held);
            g.drawString(this.font, held, x + WIDTH - 8 - hw, y + 18, Ui.AMBER, false);
            statusW = WIDTH - 16 - hw - 6;
        }
        g.drawString(this.font, Ui.trim(this.font, status, statusW), x + 8, y + 18, Ui.MUTED, false);
        tab(g, x + 8, y + 32, 58, Ui.t("screen.blackchannel.tab.ops"), null, 0);
        tab(g, x + 70, y + 32, 58, Ui.t("screen.blackchannel.tab.map"), Ui.t("screen.blackchannel.map_note"), 1);
        tab(g, x + 132, y + 32, 58, Ui.t("screen.blackchannel.tab.bag"), Ui.t("screen.blackchannel.bag_note"), 2);
        tab(g, x + 194, y + 32, 58, Ui.t("screen.blackchannel.tab.sell"), Ui.t("screen.blackchannel.tip.sell_item"), 3);
        if (page == 0) drawOps(g, x, y);
        else if (page == 1) drawArea(g, x, y);
        else if (page == 2) drawBag(g, x, y, mx, my);
        else drawSell(g, x, y);
    }

    private void tab(GuiGraphics g, int x, int y, int w, String label, String tip, int index) {
        Ui.button(g, this.font, hits, x, y, w, 14, label, tip, page == index, "page", String.valueOf(index));
    }

    private void drawOps(GuiGraphics g, int x, int y) {
        int w = 78;
        Ui.button(g, this.font, hits, x + 8, y + 52, w, 13, "TRACE", tipTrace(), false, Actions.TRACE, "");
        Ui.button(g, this.font, hits, x + 91, y + 52, w, 13, "DEANON", tipDeanon(), false, Actions.DEANON, "");
        Ui.button(g, this.font, hits, x + 174, y + 52, w, 13, "WIPE", Ui.t("screen.blackchannel.tip.wipe"), wipeArmed, Actions.WIPE, "");
        Ui.button(g, this.font, hits, x + 8, y + 68, w, 13, "SWAT", tipSwat(), false, Actions.SWAT, "");
        Ui.button(g, this.font, hits, x + 91, y + 68, w, 13, "BOMB", tipBomb(), false, Actions.BOMB, "");
        Ui.button(g, this.font, hits, x + 174, y + 68, w, 13, "UAV", tipUav(), false, Actions.UAV, "");
        paragraph(g, ClientState.resultLine(), x + 8, y + 86, WIDTH - 16, Ui.AMBER, 2);
        g.drawString(this.font, "INTEL", x + 8, y + 126, Ui.MUTED, false);
        g.drawString(this.font, "KNOWN", x + 168, y + 126, Ui.MUTED, false);
        int ly = y + 136;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > y + 200) break;
            String label = "#" + row.id + " " + row.handle + " ±" + row.err;
            String tip = row.handle + "  " + row.dim + "  " + row.x + " " + row.y + " " + row.z + "  ±" + row.err;
            Ui.button(g, this.font, hits, x + 8, ly, 152, 11, Ui.trim(this.font, label, 146), tip, row.id == selected, "pick", String.valueOf(row.id));
            ly += 12;
        }
        int ry = y + 136;
        for (ClientState.DeanonRow row : ClientState.deanons) {
            if (ry > y + 204) break;
            g.drawString(this.font, Ui.trim(this.font, row.handle() + "=" + row.name(), 84), x + 168, ry, Ui.BLUE, false);
            ry += 10;
        }
    }

    private void drawArea(GuiGraphics g, int x, int y) {
        g.drawString(this.font, Ui.t("screen.blackchannel.area_title"), x + 8, y + 52, Ui.TEXT, false);
        int next = paragraph(g, Ui.t("screen.blackchannel.map_note"), x + 8, y + 64, WIDTH - 16, Ui.MUTED, 3);
        int ox = x + 8;
        int oy = Math.max(y + 96, next + 4);
        int cell = 6;
        int grid = Optics.GRID * cell;
        g.fill(ox - 1, oy - 1, ox + grid + 1, oy + grid + 1, Ui.EDGE_DIM);
        byte[] cells = ClientState.optic;
        int[] colors = ClientState.opticColor;
        boolean any = false;
        for (int gz = 0; gz < Optics.GRID; gz++) {
            for (int gx = 0; gx < Optics.GRID; gx++) {
                int i = gz * Optics.GRID + gx;
                int color = 0xFF07110C;
                if (i < colors.length && colors[i] != 0) {
                    color = 0xFF000000 | colors[i];
                    any = true;
                } else if (i < cells.length && cells[i] != 0) {
                    color = 0xFF000000 | (80 + cells[i]) * 0x010101;
                    any = true;
                }
                g.fill(ox + gx * cell, oy + gz * cell, ox + gx * cell + cell - 1, oy + gz * cell + cell - 1, color);
            }
        }
        if (ClientState.opticHx >= 0) {
            int px = ox + ClientState.opticHx * cell;
            int py = oy + ClientState.opticHz * cell;
            g.fill(px, py, px + 3, py + 3, Ui.RED);
        }
        if (!any) g.drawString(this.font, Ui.trim(this.font, Ui.t("screen.blackchannel.no_shot"), grid - 4), ox + 2, oy + 4, Ui.MUTED, false);
        int rx = ox + grid + 8;
        int rw = x + WIDTH - 8 - rx;
        if (rw < 40) return;
        g.drawString(this.font, Ui.trim(this.font, "CONF " + ClientState.opticConf, rw), rx, oy, Ui.MUTED, false);
        if (!ClientState.opticBearing.isEmpty()) {
            paragraph(g, ClientState.opticBearing, rx, oy + 12, rw, Ui.AMBER, 2);
        }
        paragraph(g, ClientState.resultLine(), rx, oy + 32, rw, Ui.AMBER, 3);
        Ui.button(g, this.font, hits, rx, oy + 64, Math.min(90, rw), 13, Ui.t("screen.blackchannel.btn.snap"), tipOptic(), false, Actions.OPTIC, "");
    }

    private void drawBag(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(this.font, Ui.t("screen.blackchannel.bag_title"), x + 8, y + 52, Ui.TEXT, false);
        paragraph(g, Ui.t("screen.blackchannel.bag_note"), x + 8, y + 64, WIDTH - 16, Ui.MUTED, 2);
        for (int i = 0; i < 36; i++) {
            int col = i % 9;
            int row = i / 9;
            drawPeek(g, x + 8 + col * 18, y + 88 + row * 18, findPeek(i), mx, my);
        }
        for (int i = 0; i < 5; i++) {
            drawPeek(g, x + 8 + i * 18, y + 164, findPeek(36 + i), mx, my);
        }
        Ui.button(g, this.font, hits, x + 180, y + 88, 72, 13, Ui.t("screen.blackchannel.btn.snap"), tipPeek(), false, Actions.PEEK, "");
        g.drawString(this.font, Ui.trim(this.font, ClientState.resultLine(), WIDTH - 16), x + 8, y + 186, Ui.AMBER, false);
    }

    private void drawPeek(GuiGraphics g, int sx, int sy, ClientState.PeekSlot slot, int mx, int my) {
        boolean hidden = slot != null && slot.hidden();
        boolean over = mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
        g.fill(sx, sy, sx + 16, sy + 16, hidden ? 0xFF2A1418 : 0xFF07110C);
        if (over) g.fill(sx - 1, sy - 1, sx + 17, sy, Ui.EDGE);
        ItemStack stack = peekStack(slot);
        if (!stack.isEmpty()) {
            g.renderItem(stack, sx, sy);
            g.renderItemDecorations(this.font, stack, sx, sy);
        }
        if (!over) return;
        if (stack.isEmpty()) hoverHidden = hidden;
        else hoverStack = stack;
    }

    private ItemStack peekStack(ClientState.PeekSlot slot) {
        if (slot == null) return ItemStack.EMPTY;
        CompoundTag nbt = slot.nbt();
        if (nbt != null && nbt.contains("id")) {
            ItemStack stack = ItemStack.of(nbt);
            if (!stack.isEmpty()) return stack;
        }
        if (slot.id() == null || slot.id().isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(slot.id());
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, slot.count()));
    }

    private ClientState.PeekSlot findPeek(int index) {
        for (ClientState.PeekSlot slot : ClientState.peek) if (slot.slot() == index) return slot;
        return null;
    }

    private void drawSell(GuiGraphics g, int x, int y) {
        paragraph(g, Ui.t("screen.blackchannel.sell_note"), x + 8, y + 52, WIDTH - 16, Ui.MUTED, 2);
        g.drawString(this.font, Ui.t("screen.blackchannel.slot_label"), x + 8, y + 74, Ui.MUTED, false);
        g.drawString(this.font, Ui.t("screen.blackchannel.price_label"), x + PRICE_X, y + 74, Ui.MUTED, false);
        syncPriceFromBox();
        g.fill(x + SLIDER_X, y + SLIDER_Y, x + SLIDER_X + SLIDER_W, y + SLIDER_Y + 4, 0xFF07110C);
        int knob = (int) ((price - 1) / 4999f * (SLIDER_W - 4));
        g.fill(x + SLIDER_X + knob, y + SLIDER_Y - 2, x + SLIDER_X + knob + 4, y + SLIDER_Y + 6, Ui.EDGE);
        Ui.button(g, this.font, hits, x + 8, y + 104, 118, 13, Ui.t("screen.blackchannel.btn.sell_item"), Ui.t("screen.blackchannel.tip.sell_item"), false, "sell-item", "");
        Ui.button(g, this.font, hits, x + 132, y + 104, 118, 13, Ui.t("screen.blackchannel.btn.sell_intel"), Ui.t("screen.blackchannel.tip.sell_intel"), false, Actions.SELL, "");
        g.drawString(this.font, Ui.t("screen.blackchannel.inv_label"), x + 8, y + 120, Ui.MUTED, false);
        g.drawString(this.font, Ui.trim(this.font, ClientState.resultLine(), WIDTH - 16), x + 8, y + 208, Ui.AMBER, false);
    }

    private int paragraph(GuiGraphics g, String text, int x, int y, int maxW, int color, int maxLines) {
        if (text == null || text.isEmpty() || maxW < 8) return y;
        int n = 0;
        for (String line : Ui.wrap(this.font, text, maxW)) {
            if (n >= maxLines) break;
            g.drawString(this.font, Ui.trim(this.font, line, maxW), x, y, color, false);
            y += 10;
            n++;
        }
        return y;
    }

    private void noteTip(GuiGraphics g, String text, int mx, int my) {
        if (text == null || text.isEmpty()) return;
        String line = Ui.trim(this.font, text, 180);
        int w = this.font.width(line) + 8;
        int tx = Math.min(mx + 8, Math.max(2, this.width - w - 2));
        int ty = Math.min(my + 8, Math.max(2, this.height - 16));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(tx, ty, tx + w, ty + 14, 0xF0101418);
        g.fill(tx, ty, tx + w, ty + 1, Ui.EDGE);
        g.drawString(this.font, line, tx + 4, ty + 3, Ui.TEXT, false);
        g.pose().popPose();
    }

    private void layoutSlots() {
        this.menu.showSlots = page == 3;
    }

    private void syncWidgets() {
        if (input != null) {
            input.visible = page == 0;
            if (page != 0 && input.isFocused()) input.setFocused(false);
        }
        if (priceBox != null) {
            priceBox.visible = page == 3;
            if (page != 3 && priceBox.isFocused()) priceBox.setFocused(false);
        }
    }

    private String tipTrace() {
        return ClientState.tip("screen.blackchannel.tip.trace",
                ClientState.cfg("traceCost", 2), ClientState.cfg("traceBase", 500),
                mul(ClientState.cfg("traceVpnMul", 150)), mul(ClientState.cfg("traceVpnDeep", 200)),
                ClientState.cfg("traceFail", 5), ClientState.cfg("traceFailVpn", 12),
                ClientState.cfg("traceFailDeep", 35), ClientState.cfg("traceStale", 40));
    }

    private static String mul(int hundredths) {
        int v = Math.max(0, hundredths);
        int whole = v / 100;
        int frac = v % 100;
        if (frac == 0) return Integer.toString(whole);
        if (frac % 10 == 0) return whole + "." + (frac / 10);
        return whole + "." + (frac < 10 ? "0" : "") + frac;
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
        if (page == 3 && mx >= this.leftPos + SLIDER_X && mx <= this.leftPos + SLIDER_X + SLIDER_W
                && my >= this.topPos + SLIDER_Y - 4 && my <= this.topPos + SLIDER_Y + 10) {
            dragPrice = true;
            setPriceFromMouse(mx);
            return true;
        }
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if ("page".equals(hit.action())) {
                page = Integer.parseInt(hit.arg());
                layoutSlots();
                syncWidgets();
                return true;
            }
            if ("pick".equals(hit.action())) {
                selected = Integer.parseInt(hit.arg());
                for (ClientState.IntelRow row : ClientState.intel) {
                    if (row.id == selected && input != null) input.setValue(row.handle);
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
        float t = (float) ((mx - (this.leftPos + SLIDER_X)) / SLIDER_W);
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
        if (page == 0 && (key == 257 || key == 335) && input != null && input.isFocused()) {
            send(Actions.TRACE, input.getValue().trim());
            return true;
        }
        boolean typing = (input != null && input.isFocused()) || (priceBox != null && priceBox.isFocused());
        if (typing && this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scan)) return true;
        return super.keyPressed(key, scan, mods);
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
