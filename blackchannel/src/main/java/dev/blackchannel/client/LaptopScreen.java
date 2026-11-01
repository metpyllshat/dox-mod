package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
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
        input = new EditBox(this.font, this.leftPos + 10, this.topPos + 108, WIDTH - 20, 10, Component.empty());
        input.setMaxLength(64);
        input.setBordered(false);
        input.setTextColor(Ui.TEXT);
        input.setHint(Component.translatable("screen.blackchannel.hint.handle"));
        addRenderableWidget(input);
        priceBox = new EditBox(this.font, this.leftPos + PRICE_X + 2, this.topPos + PRICE_Y + 2, 52, 10, Component.empty());
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
        Ui.panel(g, x, y, WIDTH, HEIGHT);
        Ui.divider(g, x + 6, y + 26, WIDTH - 12);
        Ui.well(g, x + 6, y + 46, WIDTH - 12, HEIGHT - 52);
        if (page == 0) g.fill(x + 8, y + 105, x + WIDTH - 8, y + 119, 0xFF0B0F12);
        if (page == 2) {
            g.fill(x + PRICE_X, y + PRICE_Y, x + PRICE_X + 56, y + PRICE_Y + 12, 0xFF0B0F12);
            for (Slot slot : this.menu.slots) {
                if (!slot.isActive()) continue;
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0B0F12);
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
        g.drawString(this.font, handle.isEmpty() ? "—" : handle, x + 8, y + 6, Ui.EDGE, false);
        g.drawString(this.font, coins, x + WIDTH - 8 - this.font.width(coins), y + 6, Ui.AMBER, false);
        String status = (ClientState.uplink ? (ClientState.dish ? "DISH" : "RACK") : "NO UPLINK")
                + "  T" + ClientState.cdT + " D" + ClientState.cdD + " S" + ClientState.cdS + " B" + ClientState.cdB;
        int statusW = WIDTH - 16;
        if (page != 2 && !this.menu.getSlot(0).getItem().isEmpty()) {
            String held = Ui.t("screen.blackchannel.slot_held");
            int hw = this.font.width(held);
            g.drawString(this.font, held, x + WIDTH - 8 - hw, y + 16, Ui.AMBER, false);
            statusW = WIDTH - 16 - hw - 6;
        }
        g.drawString(this.font, Ui.trim(this.font, status, statusW), x + 8, y + 16, Ui.MUTED, false);
        tab(g, x + 8, y + 30, 80, Ui.t("screen.blackchannel.tab.ops"), null, 0);
        tab(g, x + 90, y + 30, 80, Ui.t("screen.blackchannel.tab.bag"), Ui.t("screen.blackchannel.bag_note"), 1);
        tab(g, x + 172, y + 30, 80, Ui.t("screen.blackchannel.tab.sell"), Ui.t("screen.blackchannel.tip.sell_item"), 2);
        if (page == 0) drawOps(g, x, y);
        else if (page == 1) drawBag(g, x, y, mx, my);
        else drawSell(g, x, y);
    }

    private void tab(GuiGraphics g, int x, int y, int w, String label, String tip, int index) {
        Ui.tab(g, this.font, hits, x, y, w, 14, label, page == index, "page", String.valueOf(index));
        if (tip != null && page == index) hits.set(hits.size() - 1, new Ui.Hit(x, y, w, 14, "page", String.valueOf(index), tip));
    }

    private void drawOps(GuiGraphics g, int x, int y) {
        int w = 118;
        Ui.button(g, this.font, hits, x + 8, y + 48, w, 13, "TRACE", tipTrace(), false, Actions.TRACE, "");
        Ui.button(g, this.font, hits, x + 134, y + 48, w, 13, "WIPE", Ui.t("screen.blackchannel.tip.wipe"), wipeArmed, Actions.WIPE, "");
        Ui.button(g, this.font, hits, x + 8, y + 64, w, 13, "BOMB", tipBomb(), false, Actions.BOMB, "");
        Ui.button(g, this.font, hits, x + 134, y + 64, w, 13, "UAV", tipUav(), false, Actions.UAV, "");
        // Результат последней операции: две строки, ниже кнопок и выше поля ввода
        paragraph(g, ClientState.resultFor("ops"), x + 8, y + 82, WIDTH - 16, Ui.AMBER, 2);
        g.drawString(this.font, Ui.t("screen.blackchannel.hint.handle"), x + 8, y + 100, 0xFF3E4A46, false);
        // Следы: направление и ширина луча, без координат
        g.drawString(this.font, Ui.t("screen.blackchannel.beams"), x + 8, y + 122, Ui.MUTED, false);
        int by = y + 133;
        int beams = 0;
        for (ClientState.BeamRow beam : ClientState.beams) {
            if (beams >= 2) break;
            String label = beam.handle + " " + Math.round(beam.angle) + "° ±" + Math.round(beam.alpha)
                    + (beam.samples > 1 ? " x" + beam.samples : "");
            g.drawString(this.font, Ui.trim(this.font, label, WIDTH - 16), x + 8, by, Ui.AMBER, false);
            by += 10;
            beams++;
        }
        if (beams == 0) g.drawString(this.font, Ui.t("screen.blackchannel.beams.none"), x + 8, by, 0xFF3E4A46, false);
        Ui.divider(g, x + 8, y + 154, WIDTH - 16);
        g.drawString(this.font, "INTEL", x + 8, y + 157, Ui.MUTED, false);
        int ly = y + 168;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > y + 203) break;
            String label = "#" + row.id + " " + row.handle + " ±" + row.err;
            String tip = row.handle + "  " + row.dim + "  " + row.x + " " + row.y + " " + row.z + "  ±" + row.err;
            Ui.row(g, this.font, x + 8, ly, WIDTH - 16, 11, label, row.stale ? "?" : null, row.id == selected, row.stale ? Ui.MUTED : Ui.GREEN);
            hits.add(new Ui.Hit(x + 8, ly, WIDTH - 16, 11, "pick", String.valueOf(row.id), tip));
            ly += 12;
        }
        if (ClientState.intel.isEmpty()) {
            g.drawString(this.font, Ui.t("screen.blackchannel.intel_none"), x + 8, ly, 0xFF3E4A46, false);
        }
    }

    private ClientState.IntelRow picked() {
        for (ClientState.IntelRow row : ClientState.intel) if (row.id == selected) return row;
        return ClientState.intel.isEmpty() ? null : ClientState.intel.get(0);
    }

    private void drawBag(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(this.font, Ui.t("screen.blackchannel.bag_title"), x + 8, y + 50, Ui.TEXT, false);
        paragraph(g, Ui.t("screen.blackchannel.bag_note"), x + 8, y + 61, 160, Ui.MUTED, 2);
        Ui.button(g, this.font, hits, x + 172, y + 50, 80, 13, Ui.t("screen.blackchannel.btn.snap"), tipPeek(), false, Actions.PEEK, "");
        for (int i = 0; i < 36; i++) {
            int col = i % 9;
            int row = i / 9;
            drawPeek(g, x + 8 + col * 18, y + 84 + row * 18, findPeek(i), mx, my);
        }
        for (int i = 0; i < 9; i++) {
            drawPeek(g, x + 8 + i * 18, y + 160, findPeek(36 + i), mx, my);
        }
        paragraph(g, ClientState.resultFor("bag"), x + 8, y + 184, WIDTH - 16, Ui.AMBER, 2);
    }

    private void drawPeek(GuiGraphics g, int sx, int sy, ClientState.PeekSlot slot, int mx, int my) {
        boolean hidden = slot != null && slot.hidden();
        boolean over = mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
        g.fill(sx, sy, sx + 16, sy + 16, hidden ? 0xFF2A1418 : 0xFF0B0F12);
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
        paragraph(g, Ui.t("screen.blackchannel.sell_note"), x + 8, y + 50, WIDTH - 16, Ui.MUTED, 2);
        g.drawString(this.font, Ui.t("screen.blackchannel.slot_label"), x + 8, y + 72, Ui.MUTED, false);
        g.drawString(this.font, Ui.t("screen.blackchannel.price_label"), x + PRICE_X, y + 72, Ui.MUTED, false);
        syncPriceFromBox();
        g.fill(x + SLIDER_X, y + SLIDER_Y, x + SLIDER_X + SLIDER_W, y + SLIDER_Y + 4, 0xFF0B0F12);
        int knob = (int) ((price - 1) / 4999f * (SLIDER_W - 4));
        g.fill(x + SLIDER_X + knob, y + SLIDER_Y - 2, x + SLIDER_X + knob + 4, y + SLIDER_Y + 6, Ui.EDGE);
        Ui.button(g, this.font, hits, x + 8, y + 104, 118, 13, Ui.t("screen.blackchannel.btn.sell_item"), Ui.t("screen.blackchannel.tip.sell_item"), false, "sell-item", "");
        Ui.button(g, this.font, hits, x + 132, y + 104, 118, 13, Ui.t("screen.blackchannel.btn.sell_intel"), Ui.t("screen.blackchannel.tip.sell_intel"), false, Actions.SELL, "");
        g.drawString(this.font, Ui.t("screen.blackchannel.inv_label"), x + 8, y + 118, Ui.MUTED, false);
        g.drawString(this.font, Ui.trim(this.font, ClientState.resultFor("sell"), WIDTH - 16), x + 8, y + 208, Ui.AMBER, false);
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
        this.menu.showSlots = page == 2;
    }

    private void syncWidgets() {
        if (input != null) {
            input.visible = page == 0;
            if (page != 0 && input.isFocused()) input.setFocused(false);
        }
        if (priceBox != null) {
            priceBox.visible = page == 2;
            if (page != 2 && priceBox.isFocused()) priceBox.setFocused(false);
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

    private String tipBomb() {
        return ClientState.tip("screen.blackchannel.tip.bomb", ClientState.cfg("bombCost", 3));
    }

    private String tipUav() {
        return ClientState.tip("screen.blackchannel.tip.uav", ClientState.cfg("uavCost", 3),
                ClientState.cfg("uavRange", 96), ClientState.cfg("uavError", 30));
    }

    private String tipPeek() {
        return ClientState.tip("screen.blackchannel.tip.peek", ClientState.cfg("peekCost", 8));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (page == 2 && mx >= this.leftPos + SLIDER_X && mx <= this.leftPos + SLIDER_X + SLIDER_W
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
            // Сканер сумки работает по выбранному следу
            if (Actions.PEEK.equals(hit.action())) arg = String.valueOf(selected);
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
