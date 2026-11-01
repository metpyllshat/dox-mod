package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Catalog;
import dev.blackchannel.logic.Teams;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Телефон: прибор, а не окно. Сверху статус-бар (сеть, часы, батарея), под ним
 * шапка с позывным и состоянием эфира, полоса ШУМ и четыре вкладки —
 * ЭФИР, КАРТА, СЕТЬ, РЫНОК. Внизу поле ввода и строка вкладок.
 * <p>
 * Каждая зона знает свои границы: содержимое режется по высоте, поэтому строки
 * не наезжают друг на друга независимо от того, сколько пришло с сервера.
 */
public class PhoneScreen extends AbstractContainerScreen<Menus.PhoneMenu> {
    private static final int W = 248;
    private static final int H = 228;
    /** Верх зоны содержимого вкладки. */
    private static final int TOP = 68;
    /** Низ зоны содержимого вкладки. */
    private static final int BOTTOM = 184;
    private static final int TABS_Y = 188;
    private static final int TABS_H = 16;
    private static final int INPUT_Y = 208;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private final List<Component> hoverLines = new ArrayList<>();
    private EditBox input;
    private int tab;
    private int marketTab;
    private int marketPage;
    private int listPick = -1;
    private String shopPick = "";
    private ItemStack hoverStack = ItemStack.EMPTY;

    public PhoneScreen(Menus.PhoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelY = 10000;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(this.font, this.leftPos + 9, this.topPos + INPUT_Y + 3, W - 76, 10, Component.empty());
        input.setMaxLength(160);
        input.setBordered(false);
        input.setTextColor(Ui.TEXT);
        input.setHint(Component.translatable("screen.blackchannel.send_hint"));
        addRenderableWidget(input);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {}

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.mouse(mx, my);
        renderBackground(g);
        hits.clear();
        hoverStack = ItemStack.EMPTY;
        hoverLines.clear();
        boolean typing = tab == 0;
        if (input != null) {
            input.visible = typing;
            if (!typing && input.isFocused()) input.setFocused(false);
        }
        int x = this.leftPos;
        int y = this.topPos;
        Ui.panel(g, x, y, W, H);
        drawStatus(g, x, y);
        drawHead(g, x, y);
        drawNoise(g, x, y);
        switch (tab) {
            case 0 -> drawFeed(g, x, y);
            case 1 -> drawMap(g, x, y);
            case 2 -> drawNet(g, x, y);
            default -> drawMarket(g, x, y, mx, my);
        }
        drawInput(g, x, y);
        drawTabs(g, x, y);
        for (var renderable : renderables) renderable.render(g, mx, my, pt);
        if (!hoverStack.isEmpty() && !hoverLines.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : hoverLines) lines.addAll(this.font.split(line, 200));
            if (!lines.isEmpty()) g.renderTooltip(this.font, lines, mx, my);
        } else Ui.tips(g, this.font, hits, mx, my);
    }

    // ---------- шапка ----------

    private void drawStatus(GuiGraphics g, int x, int y) {
        int bars = Math.max(0, Math.min(4, ClientState.signal));
        for (int i = 0; i < 4; i++) {
            int barH = 4 + i * 3;
            g.fill(x + 8 + i * 5, y + 16 - barH, x + 11 + i * 5, y + 16, i < bars ? Ui.GREEN : 0xFF2A3238);
        }
        String clock = String.format("%02d:%02d", LocalTime.now().getHour(), LocalTime.now().getMinute());
        g.drawString(this.font, clock, x + W / 2 - this.font.width(clock) / 2, y + 5, Ui.TEXT, false);
        int charge = Math.max(0, Math.min(100, ClientState.battery));
        String pct = charge + "%";
        g.drawString(this.font, pct, x + W - 46 - this.font.width(pct), y + 5, Ui.MUTED, false);
        int bx = x + W - 42;
        g.fill(bx, y + 5, bx + 30, y + 16, 0xFF000000);
        g.fill(bx + 1, y + 6, bx + 29, y + 15, 0xFF12181C);
        int fill = Math.round(28 * charge / 100f);
        if (fill > 0) g.fill(bx + 1, y + 6, bx + 1 + fill, y + 15, charge > 50 ? Ui.GREEN : charge > 20 ? Ui.AMBER : Ui.RED);
        g.fill(bx + 30, y + 8, bx + 32, y + 13, 0xFF2A3238);
        Ui.divider(g, x + 6, y + 19, W - 12);
    }

    private void drawHead(GuiGraphics g, int x, int y) {
        String title = switch (tab) {
            case 0 -> Ui.t("screen.blackchannel.tab.feed");
            case 1 -> Ui.t("screen.blackchannel.tab.map");
            case 2 -> Ui.t("screen.blackchannel.tab.net");
            default -> Ui.t("screen.blackchannel.tab.market_short");
        };
        g.drawString(this.font, title, x + 8, y + 24, Ui.TEXT, false);
        String coins = "#" + ClientState.crypto;
        g.drawString(this.font, coins, x + W - 8 - this.font.width(coins), y + 24, Ui.AMBER, false);
        String handle = ClientState.handle == null || ClientState.handle.isEmpty() ? "—" : ClientState.handle;
        String crew = ClientState.crew == null || ClientState.crew.isEmpty() ? Ui.t("screen.blackchannel.no_crew") : Teams.nameOf(ClientState.crew);
        String who = handle + " · " + crew;
        String state = linkState();
        int stateW = this.font.width(state);
        g.drawString(this.font, Ui.trim(this.font, who, W - 24 - stateW), x + 8, y + 35, Ui.MUTED, false);
        g.drawString(this.font, state, x + W - 8 - stateW, y + 35, linkColor(), false);
    }

    private String linkState() {
        if (ClientState.emp > 0) return "ЭМИ";
        if (ClientState.airplane) return Ui.t("screen.blackchannel.state.air");
        if (ClientState.jammed) return Ui.t("screen.blackchannel.state.jammed");
        if (ClientState.faraday) return Ui.t("screen.blackchannel.state.cage");
        if (ClientState.uplink) return ClientState.dish ? Ui.t("screen.blackchannel.state.dish") : Ui.t("screen.blackchannel.state.rack");
        return Ui.t("screen.blackchannel.state.none");
    }

    private int linkColor() {
        if (ClientState.emp > 0 || ClientState.jammed) return Ui.RED;
        if (ClientState.uplink) return Ui.GREEN;
        return Ui.MUTED;
    }

    private void drawNoise(GuiGraphics g, int x, int y) {
        int level = Math.max(0, Math.min(100, ClientState.noise));
        g.drawString(this.font, Ui.t("screen.blackchannel.noise"), x + 8, y + 46, Ui.MUTED, false);
        int bars = 14;
        int lit = (int) Math.ceil(level / 100f * bars);
        for (int i = 0; i < bars; i++) {
            int barH = 3 + i;
            int color = i < lit ? (i > 10 ? Ui.RED : i > 6 ? Ui.AMBER : Ui.GREEN) : 0xFF23282C;
            int bx = x + 34 + i * 7;
            g.fill(bx, y + 60 - barH, bx + 5, y + 60, color);
        }
        String hint = level >= 75 ? Ui.t("screen.blackchannel.noise.loud")
                : level >= 45 ? Ui.t("screen.blackchannel.noise.heard")
                : level > 10 ? Ui.t("screen.blackchannel.noise.quiet") : Ui.t("screen.blackchannel.noise.silent");
        g.drawString(this.font, hint, x + W - 8 - this.font.width(hint), y + 46,
                level >= 75 ? Ui.RED : level >= 45 ? Ui.AMBER : Ui.MUTED, false);
        Ui.divider(g, x + 6, y + 63, W - 12);
    }

    // ---------- ЭФИР ----------

    private void drawFeed(GuiGraphics g, int x, int y) {
        // Верхняя часть — радиосообщения, нижняя — пеленги и точки
        int chatTop = y + TOP;
        int chatBottom = y + 130;
        int ly = chatTop;
        String note = ClientState.resultFor("ops");
        if (!note.isEmpty()) {
            for (String line : Ui.wrap(this.font, note, W - 20)) {
                if (ly + 10 > chatTop + 22) break;
                g.drawString(this.font, line, x + 8, ly, Ui.AMBER, false);
                ly += 10;
            }
        }
        List<ClientState.ChatRow> chat = ClientState.chat;
        int room = Math.max(0, (chatBottom - ly) / 10);
        int from = Math.max(0, chat.size() - room);
        if (chat.isEmpty() && note.isEmpty()) {
            g.drawString(this.font, Ui.t("screen.blackchannel.feed.empty"), x + 8, ly, Ui.MUTED, false);
        }
        // Строка режется целиком: половина строки поверх разделителя выглядит как баг
        for (int i = from; i < chat.size() && ly + 10 <= chatBottom; i++) {
            ClientState.ChatRow row = chat.get(i);
            g.drawString(this.font, Ui.trim(this.font, row.from() + ": " + row.text(), W - 20), x + 8, ly,
                    row.enc() ? Ui.BLUE : Ui.TEXT, false);
            ly += 10;
        }
        Ui.divider(g, x + 6, y + 132, W - 12);

        // Пеленги
        int by = y + 136;
        g.drawString(this.font, Ui.t("screen.blackchannel.beams"), x + 8, by, Ui.MUTED, false);
        by += 10;
        int shown = 0;
        for (ClientState.BeamRow beam : ClientState.beams) {
            if (shown >= 2) break;
            String text = beam.handle + " " + Math.round(beam.angle) + "° ±" + Math.round(beam.alpha)
                    + (beam.samples > 1 ? " x" + beam.samples : "");
            g.drawString(this.font, Ui.trim(this.font, text, W - 20), x + 8, by, Ui.AMBER, false);
            by += 10;
            shown++;
        }
        if (shown == 0) {
            g.drawString(this.font, Ui.t("screen.blackchannel.beams.none"), x + 8, by, 0xFF4A545A, false);
            by += 10;
        }
        // Точки
        int ty = Math.max(by + 2, y + 158);
        int rows = 0;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ty > y + BOTTOM - 10 || rows >= 2) break;
            String text = Ui.t("screen.blackchannel.point") + " " + row.handle + " ±" + row.err + " · " + age(row.age);
            g.drawString(this.font, Ui.trim(this.font, text, W - 20), x + 8, ty, row.stale ? Ui.MUTED : Ui.GREEN, false);
            ty += 10;
            rows++;
        }
    }

    // ---------- КАРТА ----------

    private void drawMap(GuiGraphics g, int x, int y) {
        int cx = x + 56;
        int cy = y + 124;
        int r = 48;
        Ui.well(g, cx - r, cy - r, r * 2, r * 2);
        for (int i = -r + 24; i < r; i += 24) {
            g.fill(cx - r, cy + i, cx + r, cy + i + 1, 0xFF1D2A24);
            g.fill(cx + i, cy - r, cx + i + 1, cy + r, 0xFF1D2A24);
        }
        g.fill(cx - r + r, cy - r, cx - r + r + 1, cy + r, 0xFF2B3A32);
        g.fill(cx - r, cy, cx + r, cy + 1, 0xFF2B3A32);
        if (this.minecraft != null && this.minecraft.player != null) {
            double px = this.minecraft.player.getX();
            double pz = this.minecraft.player.getZ();
            for (ClientState.BeamRow beam : ClientState.beams) {
                Ui.ray(g, cx, cy, r, ClientState.radarR, beam.ox - (int) px, beam.oz - (int) pz, beam.angle, Ui.AMBER);
            }
            for (ClientState.Blip blip : ClientState.blips) {
                Ui.blip(g, cx, cy, r, ClientState.radarR, blip.dx(), blip.dz(), Ui.blipColor(blip.kind()));
            }
            Ui.selfArrow(g, cx, cy, this.minecraft.player.getYRot());
        }
        g.drawString(this.font, Ui.t("screen.blackchannel.north"), cx - 2, cy - r + 2, Ui.MUTED, false);
        // Ось расстояния под картой
        String range = "r " + ClientState.radarR;
        g.drawString(this.font, range, cx - this.font.width(range) / 2, cy + r + 3, 0xFF4A545A, false);

        // Правая колонка: что видно и что известно
        int rx = x + 112;
        int rw = W - 120;
        int ly = y + TOP;
        int bottom = y + BOTTOM;
        g.drawString(this.font, Ui.t("screen.blackchannel.contacts"), rx, ly, Ui.MUTED, false);
        ly += 10;
        int n = 0;
        for (ClientState.Blip blip : ClientState.blips) {
            if (n >= 4) break;
            String text = Ui.t("blackchannel.blip." + blip.kind()) + " " + blip.dist() + "м";
            g.drawString(this.font, Ui.trim(this.font, text, rw), rx, ly, Ui.blipColor(blip.kind()), false);
            ly += 10;
            n++;
        }
        if (n == 0) {
            g.drawString(this.font, Ui.t("screen.blackchannel.empty"), rx, ly, 0xFF4A545A, false);
            ly += 10;
        }
        ly += 4;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > bottom - 10) break;
            g.drawString(this.font, Ui.trim(this.font, row.handle + " ±" + row.err, rw), rx, ly,
                    row.stale ? Ui.MUTED : Ui.GREEN, false);
            ly += 10;
        }
        for (ClientState.TrackRow track : ClientState.trackers) {
            if (ly > bottom - 10) break;
            String text = (track.live() ? Ui.t("screen.blackchannel.track.live") : Ui.t("screen.blackchannel.track.was"))
                    + " " + track.x() + " " + track.z();
            g.drawString(this.font, Ui.trim(this.font, text, rw), rx, ly, track.live() ? Ui.AMBER : Ui.MUTED, false);
            ly += 10;
        }
    }

    // ---------- СЕТЬ ----------

    private void drawNet(GuiGraphics g, int x, int y) {
        int ly = y + TOP;
        int bottom = y + BOTTOM;
        Ui.button(g, this.font, hits, x + 8, ly, 84, 13, Ui.t("screen.blackchannel.btn.airplane"),
                Ui.t("screen.blackchannel.tip.airplane"), ClientState.airplane, Actions.AIR, "");
        Ui.button(g, this.font, hits, x + 96, ly, 56, 13, "DND", Ui.t("screen.blackchannel.tip.dnd"), ClientState.dnd, Actions.DND, "");
        Ui.button(g, this.font, hits, x + 156, ly, 84, 13, Ui.t("screen.blackchannel.btn.radar_hud"),
                ClientState.tip("screen.blackchannel.tip.radar_hud", ClientState.cfg("radarPhone", 52), ClientState.cfg("radarLaptop", 110),
                        ClientState.cfg("structure", 360), ClientState.cfg("dishBonus", 80)),
                ClientState.radarHud, Actions.RADAR, "");
        ly += 17;
        Ui.button(g, this.font, hits, x + 8, ly, 110, 13, Ui.t("screen.blackchannel.btn.manual"), null, false, "manual", "");
        Ui.button(g, this.font, hits, x + 122, ly, 118, 13, Ui.t("screen.blackchannel.btn.wipe_sim"),
                Ui.t("screen.blackchannel.tip.wipe"), false, Actions.WIPE, "");
        ly += 21;

        // Состояние связи и расходников — строками, без кнопок
        Ui.well(g, x + 8, ly, W - 16, 54);
        int ry = ly + 4;
        line(g, x + 12, ry, Ui.t("screen.blackchannel.net.uplink"), uplinkText(), ClientState.uplink ? Ui.GREEN : Ui.RED);
        ry += 10;
        line(g, x + 12, ry, Ui.t("screen.blackchannel.net.vpn"), vpnText(), ClientState.vpn > 0 ? Ui.GREEN : Ui.MUTED);
        ry += 10;
        line(g, x + 12, ry, Ui.t("screen.blackchannel.net.ids"), ClientState.ids ? Ui.t("screen.blackchannel.on") : Ui.t("screen.blackchannel.off"),
                ClientState.ids ? Ui.GREEN : Ui.MUTED);
        ry += 10;
        line(g, x + 12, ry, Ui.t("screen.blackchannel.net.hard"), ClientState.hard ? Ui.t("screen.blackchannel.on") : Ui.t("screen.blackchannel.off"),
                ClientState.hard ? Ui.GREEN : Ui.MUTED);
        ry += 10;
        line(g, x + 12, ry, Ui.t("screen.blackchannel.net.sat"), String.valueOf(ClientState.sat), ClientState.sat > 0 ? Ui.AMBER : Ui.MUTED);
        ly += 60;

        String result = ClientState.resultFor("chat");
        if (!result.isEmpty()) {
            for (String part : Ui.wrap(this.font, result, W - 20)) {
                if (ly > bottom - 10) break;
                g.drawString(this.font, part, x + 8, ly, Ui.AMBER, false);
                ly += 10;
            }
        } else {
            g.drawString(this.font, Ui.t("screen.blackchannel.net.hint"), x + 8, ly, 0xFF4A545A, false);
        }
    }

    private String uplinkText() {
        if (ClientState.uplink) return ClientState.dish ? Ui.t("screen.blackchannel.state.dish") : Ui.t("screen.blackchannel.state.rack");
        return Ui.t("screen.blackchannel.state.none");
    }

    private String vpnText() {
        return switch (ClientState.vpn) {
            case 0 -> Ui.t("screen.blackchannel.off");
            case 1 -> "VPN";
            case 2 -> "VPN +";
            default -> "DEEP VPN";
        };
    }

    private void line(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.drawString(this.font, label, x, y, Ui.MUTED, false);
        g.drawString(this.font, value, x + W - 24 - this.font.width(value), y, color, false);
    }

    // ---------- РЫНОК ----------

    private void drawMarket(GuiGraphics g, int x, int y, int mx, int my) {
        int ty = y + TOP;
        int w = (W - 16 - 8) / 3;
        Ui.tab(g, this.font, hits, x + 8, ty, w, 14, Ui.t("screen.blackchannel.market.shop"), marketTab == 0, "mkt", "0");
        Ui.tab(g, this.font, hits, x + 8 + w + 4, ty, w, 14, Ui.t("screen.blackchannel.market.board"), marketTab == 1, "mkt", "1");
        Ui.tab(g, this.font, hits, x + 8 + (w + 4) * 2, ty, w, 14, Ui.t("screen.blackchannel.market.auction"), marketTab == 2, "mkt", "2");
        if (marketTab == 0) drawShop(g, x, ty, mx, my);
        else drawListings(g, x, ty, marketTab == 1, mx, my);
    }

    private void drawShop(GuiGraphics g, int x, int ty, int mx, int my) {
        List<Catalog.Entry> entries = Catalog.entries();
        Catalog.Entry picked = null;
        for (int i = 0; i < 12; i++) {
            int sx = lotX(x, i % 6);
            int sy = lotY(ty, i / 6);
            if (i >= entries.size()) {
                emptyLot(g, sx, sy);
                continue;
            }
            Catalog.Entry entry = entries.get(i);
            ItemStack stack = new ItemStack(entry.item().get(), Math.max(1, entry.count()));
            boolean on = entry.id().equals(shopPick);
            if (on) picked = entry;
            if (lot(g, sx, sy, stack, entry.price(), on)) {
                hits.add(new Ui.Hit(sx, sy, 36, 38, "mpick", entry.id(), null));
                armTip(stack, entry.price(), Ui.t("screen.blackchannel.market.vendor"), null);
            }
        }
        if (picked == null) {
            marketFoot(g, x, ty, ItemStack.EMPTY, null, "", 0, null, "", 1);
            return;
        }
        ItemStack stack = new ItemStack(picked.item().get(), Math.max(1, picked.count()));
        String name = stack.getHoverName().getString();
        if (picked.count() > 1) name = name + " x" + picked.count();
        marketFoot(g, x, ty, stack, name, Ui.t("screen.blackchannel.market.vendor"), picked.price(), Actions.BUY, picked.id(), 1);
    }

    private void drawListings(GuiGraphics g, int x, int ty, boolean intelBoard, int mx, int my) {
        List<ClientState.ListingRow> rows = new ArrayList<>();
        for (ClientState.ListingRow row : ClientState.listings) {
            if (row.intel == intelBoard) rows.add(row);
        }
        if (rows.isEmpty()) {
            String empty = intelBoard ? Ui.t("screen.blackchannel.market.empty") : Ui.t("screen.blackchannel.market.empty_items");
            int ly = ty + 26;
            for (String line : Ui.wrap(this.font, empty, W - 28)) {
                if (ly > ty + 86) break;
                g.drawString(this.font, line, x + 14, ly, Ui.MUTED, false);
                ly += 10;
            }
            marketFoot(g, x, ty, ItemStack.EMPTY, "", "", 0, null, "", 1);
            return;
        }
        int pages = Math.max(1, (rows.size() + 11) / 12);
        if (marketPage >= pages) marketPage = pages - 1;
        if (marketPage < 0) marketPage = 0;
        int start = marketPage * 12;
        ClientState.ListingRow picked = null;
        for (int i = 0; i < 12; i++) {
            int sx = lotX(x, i % 6);
            int sy = lotY(ty, i / 6);
            int idx = start + i;
            if (idx >= rows.size()) {
                emptyLot(g, sx, sy);
                continue;
            }
            ClientState.ListingRow row = rows.get(idx);
            ItemStack stack = listingStack(row);
            boolean on = row.id == listPick;
            if (on) picked = row;
            if (lot(g, sx, sy, stack, row.price, on)) {
                hits.add(new Ui.Hit(sx, sy, 36, 38, "lpick", String.valueOf(row.id), null));
                armTip(stack, row.price, row.seller, null);
                if (row.intel) {
                    if (row.dim != null && !row.dim.isEmpty()) hoverLines.add(Component.literal(row.dim).withStyle(ChatFormatting.GRAY));
                    hoverLines.add(Component.literal(Ui.maskCoords(row.id)).withStyle(ChatFormatting.OBFUSCATED));
                }
            }
        }
        if (picked == null) {
            marketFoot(g, x, ty, ItemStack.EMPTY, null, "", 0, null, "", pages);
            return;
        }
        ItemStack stack = listingStack(picked);
        String name = stack.isEmpty() ? picked.desc : stack.getHoverName().getString();
        if (stack.getCount() > 1) name = name + " x" + stack.getCount();
        marketFoot(g, x, ty, stack, name, picked.seller, picked.price, Actions.BUY_LIST, String.valueOf(picked.id), pages);
    }

    private int lotX(int x, int col) {
        return x + 11 + col * 38;
    }

    private int lotY(int ty, int row) {
        return ty + 18 + row * 40;
    }

    private void emptyLot(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 36, sy + 38, 0xFF0C1210);
        g.fill(sx, sy, sx + 36, sy + 1, 0xFF17251F);
    }

    private boolean lot(GuiGraphics g, int sx, int sy, ItemStack stack, int price, boolean on) {
        boolean over = Ui.hover(sx, sy, 36, 38);
        g.fill(sx, sy, sx + 36, sy + 38, on ? Ui.ON_FILL : over ? 0xFF182120 : 0xFF101614);
        g.fill(sx, sy, sx + 36, sy + 1, on ? Ui.EDGE : over ? Ui.AMBER : Ui.EDGE_DIM);
        if (stack != null && !stack.isEmpty()) {
            g.renderItem(stack, sx + 10, sy + 4);
            g.renderItemDecorations(this.font, stack, sx + 10, sy + 4);
        } else {
            g.drawString(this.font, "?", sx + 15, sy + 12, Ui.MUTED, false);
        }
        String label = "#" + price;
        int pw = this.font.width(label);
        int color = price > ClientState.crypto ? Ui.RED : Ui.AMBER;
        g.drawString(this.font, label, sx + Math.max(2, (36 - pw) / 2), sy + 27, color, false);
        return over;
    }

    private void marketFoot(GuiGraphics g, int x, int ty, ItemStack stack, String title, String meta,
                            int price, String buyAction, String buyArg, int pages) {
        int fy = ty + 99;
        Ui.divider(g, x + 6, fy - 3, W - 12);
        if (stack == null || stack.isEmpty()) {
            String note = title == null || title.isEmpty() ? Ui.t("screen.blackchannel.market.pick") : title;
            int ly = fy + 2;
            for (String line : Ui.wrap(this.font, note, W - 24)) {
                if (ly > fy + 16) break;
                g.drawString(this.font, line, x + 10, ly, Ui.MUTED, false);
                ly += 10;
            }
        } else {
            g.renderItem(stack, x + 9, fy + 2);
            g.drawString(this.font, Ui.trim(this.font, title, 112), x + 28, fy + 1, Ui.TEXT, false);
            String lineText = (meta == null || meta.isEmpty() ? "" : meta + " ") + "#" + price;
            g.drawString(this.font, Ui.trim(this.font, lineText, 112), x + 28, fy + 11,
                    price > ClientState.crypto ? Ui.RED : Ui.AMBER, false);
            if (buyAction != null) {
                Ui.button(g, this.font, hits, x + 160, fy + 3, 78, 14, Ui.t("screen.blackchannel.market.buy"),
                        price <= ClientState.crypto, buyAction, buyArg);
            }
        }
        if (pages > 1) {
            String page = (marketPage + 1) + "/" + pages + " ·";
            g.drawString(this.font, page, x + W - 10 - this.font.width(page), fy + 11, Ui.MUTED, false);
        }
        String result = ClientState.resultFor(marketTab == 0 ? "market" : "board");
        if (!result.isEmpty()) g.drawString(this.font, Ui.trim(this.font, result, W - 20), x + 10, fy + 15, Ui.AMBER, false);
    }

    private void armTip(ItemStack stack, int price, String seller, String extra) {
        hoverStack = stack;
        hoverLines.clear();
        if (this.minecraft != null && stack != null && !stack.isEmpty()) {
            hoverLines.addAll(Screen.getTooltipFromItem(this.minecraft, stack));
        }
        hoverLines.add(Component.literal("#" + price).withStyle(ChatFormatting.GOLD));
        if (seller != null && !seller.isEmpty()) hoverLines.add(Component.literal(seller).withStyle(ChatFormatting.GRAY));
        if (extra != null && !extra.isEmpty()) hoverLines.add(Component.literal(extra).withStyle(ChatFormatting.DARK_AQUA));
    }

    private ItemStack listingStack(ClientState.ListingRow row) {
        if (row.intel) {
            ItemStack paper = new ItemStack(Items.PAPER);
            String name = Ui.t("screen.blackchannel.market.intel");
            if (row.desc != null && !row.desc.isEmpty()) name = name + " " + row.desc;
            if (row.dim != null && !row.dim.isEmpty()) name = name + " " + row.dim;
            paper.setHoverName(Component.literal(name));
            return paper;
        }
        CompoundTag nbt = row.nbt;
        if (nbt != null && nbt.contains("id")) {
            ItemStack stack = ItemStack.of(nbt);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    // ---------- ввод и вкладки ----------

    private void drawInput(GuiGraphics g, int x, int y) {
        if (tab != 0) return;
        Ui.well(g, x + 8, y + INPUT_Y, W - 66, 14);
        g.fill(x + 8, y + INPUT_Y + 13, x + W - 58, y + INPUT_Y + 14, Ui.EDGE_DIM);
        Ui.button(g, this.font, hits, x + W - 56, y + INPUT_Y, 48, 14, Ui.t("screen.blackchannel.btn.send"), false, "send", "");
    }

    private void drawTabs(GuiGraphics g, int x, int y) {
        String[] names = {
                Ui.t("screen.blackchannel.tab.feed"),
                Ui.t("screen.blackchannel.tab.map"),
                Ui.t("screen.blackchannel.tab.net"),
                Ui.t("screen.blackchannel.tab.market_short")
        };
        for (int i = 0; i < 4; i++) {
            Ui.tab(g, this.font, hits, x + 6 + i * 59, y + TABS_Y, 57, TABS_H, names[i], tab == i, "tab", String.valueOf(i));
        }
    }

    private String age(int seconds) {
        if (seconds < 60) return seconds + "с";
        return (seconds / 60) + "м" + (seconds % 60) + "с";
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
            case "mkt" -> {
                marketTab = Integer.parseInt(hit.arg());
                marketPage = 0;
            }
            case "mpick" -> shopPick = hit.arg();
            case "lpick" -> listPick = Integer.parseInt(hit.arg());
            case "manual" -> {
                if (this.minecraft != null) this.minecraft.setScreen(new ManualScreen());
            }
            case "send" -> submit();
            default -> send(hit.action(), hit.arg());
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && input != null && input.isFocused()) {
            submit();
            return true;
        }
        if (input != null && input.isFocused() && key != 256 && !typingToggle(key, scan)) {
            input.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Клавиша инвентаря закрывает телефон, даже когда курсор в поле ввода. */
    private boolean typingToggle(int key, int scan) {
        return this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scan);
    }

    private void submit() {
        if (input == null) return;
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        if (text.startsWith("@")) {
            int space = text.indexOf(' ');
            if (space > 1) {
                send(Actions.DM, text.substring(1, space) + " " + text.substring(space + 1).trim());
                input.setValue("");
                return;
            }
        }
        send(Actions.SAY, text);
        input.setValue("");
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 3) marketPage = Math.max(0, marketPage - (int) Math.signum(delta));
        return true;
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
