package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Catalog;
import dev.blackchannel.logic.Teams;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
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
 * Телефон как прибор: сверху статус-бар (сеть, время, батарея), под ним шум в эфире,
 * внизу четыре вкладки — ЭФИР, КАРТА, СЕТЬ, РЫНОК. Всё остальное живёт внутри них.
 */
public class PhoneScreen extends AbstractContainerScreen<Menus.PhoneMenu> {
    private static final int W = 248;
    private static final int H = 230;
    private static final int CONTENT_TOP = 66;
    private static final int TABS_Y = 206;
    private static final int TABS_H = 16;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private EditBox input;
    private int tab;
    private int scroll;
    private int marketTab;
    private int marketPage;
    private int listPick = -1;
    private String shopPick = "";
    private String dmTarget = "";
    private ItemStack hoverStack = ItemStack.EMPTY;
    private final List<Component> hoverLines = new ArrayList<>();

    public PhoneScreen(Menus.PhoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 8, topPos + H - 40, W - 76, 12, Component.empty());
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
        hoverStack = ItemStack.EMPTY;
        hoverLines.clear();
        boolean typingTab = tab == 0 || tab == 2;
        if (input != null) {
            input.visible = typingTab;
            if (!typingTab && input.isFocused()) input.setFocused(false);
        }
        int x = leftPos;
        int y = topPos;
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
        drawTabs(g, x, y);
        for (var renderable : renderables) renderable.render(g, mx, my, pt);
        if (!hoverStack.isEmpty() && !hoverLines.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : hoverLines) lines.addAll(font.split(line, 220));
            if (!lines.isEmpty()) g.renderTooltip(font, lines, mx, my);
        } else Ui.tips(g, font, hits, mx, my);
    }

    // ---------- шапка ----------

    private void drawStatus(GuiGraphics g, int x, int y) {
        // Сеть: четыре столбика, как на трубке
        int bars = Math.max(0, Math.min(4, ClientState.signal));
        for (int i = 0; i < 4; i++) {
            int bh = 3 + i * 3;
            int color = i < bars ? Ui.GREEN : Ui.EDGE_DIM;
            g.fill(x + 8 + i * 6, y + 16 - bh, x + 12 + i * 6, y + 15, color);
        }
        String clock = String.format("%02d:%02d", LocalTime.now().getHour(), LocalTime.now().getMinute());
        g.drawString(font, clock, x + W / 2 - font.width(clock) / 2, y + 6, Ui.TEXT, false);
        // Батарея
        g.fill(x + W - 40, y + 5, x + W - 10, y + 15, 0xFF000000);
        g.fill(x + W - 39, y + 6, x + W - 11, y + 14, Ui.EDGE_DIM);
        int charge = Math.max(0, Math.min(100, ClientState.battery));
        int fillW = (int) (26 * charge / 100f);
        g.fill(x + W - 39, y + 6, x + W - 39 + fillW, y + 14,
                charge > 50 ? Ui.GREEN : charge > 20 ? Ui.AMBER : Ui.RED);
        g.fill(x + W - 9, y + 8, x + W - 7, y + 12, Ui.EDGE_DIM);
        String pct = charge + "%";
        g.drawString(font, pct, x + W - 44 - font.width(pct), y + 6, Ui.MUTED, false);
        g.fill(x + 6, y + 19, x + W - 6, y + 20, Ui.EDGE_DIM);
    }

    private void drawHead(GuiGraphics g, int x, int y) {
        String title = switch (tab) {
            case 0 -> "ЭФИР";
            case 1 -> "КАРТА";
            case 2 -> "СЕТЬ";
            default -> "РЫНОК";
        };
        g.drawString(font, title, x + 8, y + 24, Ui.TEXT, false);
        String coins = "#" + ClientState.crypto;
        g.drawString(font, coins, x + W - 8 - font.width(coins), y + 24, Ui.AMBER, false);
        String handle = ClientState.handle == null || ClientState.handle.isEmpty() ? "—" : ClientState.handle;
        String crew = ClientState.crew == null || ClientState.crew.isEmpty() ? "-" : Teams.nameOf(ClientState.crew);
        String state = ClientState.emp > 0 ? "ЭМИ" : ClientState.airplane ? "ВНЕ ЭФИРА"
                : ClientState.jammed ? "ГЛУШИЛКА" : ClientState.faraday ? "КЛЕТКА"
                : ClientState.uplink ? (ClientState.dish ? "ТАРЕЛКА" : "СТОЙКА") : "БЕЗ СВЯЗИ";
        int stateColor = ClientState.emp > 0 || ClientState.jammed ? Ui.RED
                : ClientState.uplink ? Ui.GREEN : Ui.MUTED;
        String sub = handle + " · " + crew;
        g.drawString(font, Ui.trim(font, sub, 150), x + 8, y + 36, Ui.MUTED, false);
        g.drawString(font, state, x + W - 8 - font.width(state), y + 36, stateColor, false);
    }

    private void drawNoise(GuiGraphics g, int x, int y) {
        g.drawString(font, "ШУМ", x + 8, y + 50, Ui.MUTED, false);
        int level = Math.max(0, Math.min(100, ClientState.noise));
        int bars = 12;
        int lit = (int) Math.ceil(level / 100f * bars);
        for (int i = 0; i < bars; i++) {
            int h = 3 + i * 2;
            int color = i < lit ? (i > 8 ? Ui.RED : i > 5 ? Ui.AMBER : Ui.GREEN) : Ui.EDGE_DIM;
            g.fill(x + 36 + i * 8, y + 62 - h, x + 41 + i * 8, y + 62, color);
        }
        String hint = level >= 75 ? "СЛЫШНО ДАЛЕКО" : level >= 45 ? "СЛЫШНО" : level > 10 ? "шёпот" : "тихо";
        int color = level >= 75 ? Ui.RED : level >= 45 ? Ui.AMBER : Ui.MUTED;
        g.drawString(font, hint, x + W - 8 - font.width(hint), y + 50, color, false);
        g.fill(x + 6, y + 64, x + W - 6, y + 65, Ui.EDGE_DIM);
    }

    // ---------- ЭФИР ----------

    private void drawFeed(GuiGraphics g, int x, int y) {
        int ly = CONTENT_TOP;
        String result = ClientState.resultFor("ops");
        String gear = ClientState.resultFor("gear");
        String chatResult = ClientState.resultFor("chat");
        String line = !result.isEmpty() ? result : !chatResult.isEmpty() ? chatResult : gear;
        if (line != null && !line.isEmpty()) {
            for (String part : Ui.wrap(font, line, W - 20)) {
                if (ly > TABS_Y - 46) break;
                g.drawString(font, part, x + 10, ly, Ui.AMBER, false);
                ly += 10;
            }
            ly += 3;
        }
        // Пеленги: пока луч один, точка не появляется
        for (ClientState.BeamRow beam : ClientState.beams) {
            if (ly > TABS_Y - 34) break;
            String text = "ЛУЧ " + beam.handle + " " + Math.round(beam.angle) + "° ±" + Math.round(beam.alpha)
                    + (beam.samples > 1 ? " x" + beam.samples : "") + " · " + age(beam.age);
            g.drawString(font, Ui.trim(font, text, W - 20), x + 10, ly, Ui.AMBER, false);
            ly += 10;
        }
        // Готовые точки
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > TABS_Y - 34) break;
            String text = "ТОЧКА " + row.handle + " ±" + row.err + " · " + age(row.age);
            g.drawString(font, Ui.trim(font, text, W - 20), x + 10, ly, row.stale ? Ui.MUTED : Ui.GREEN, false);
            ly += 10;
        }
        // Последние сообщения
        int from = Math.max(0, ClientState.chat.size() - 5);
        for (int i = from; i < ClientState.chat.size(); i++) {
            if (ly > TABS_Y - 22) break;
            ClientState.ChatRow row = ClientState.chat.get(i);
            String text = row.from() + ": " + row.text();
            g.drawString(font, Ui.trim(font, text, W - 20), x + 10, ly, row.enc() ? Ui.BLUE : Ui.MUTED, false);
            ly += 10;
        }
        if (ly == CONTENT_TOP) {
            g.drawString(font, "Эфир пуст. Нажми TRACE на ноутбуке.", x + 10, ly, Ui.MUTED, false);
        }
        int cardY = TABS_Y - 40;
        g.fill(x + 6, cardY, x + W - 6, cardY + 1, Ui.EDGE_DIM);
        String drops = ClientState.matchActive ? "до финала " + clock(ClientState.matchTimeLeftSeconds) : "канал в режиме ожидания";
        g.drawString(font, Ui.trim(font, drops, W - 20), x + 10, cardY + 5, Ui.TEXT, false);
        String team = ClientState.crew == null || ClientState.crew.isEmpty() ? "вне отряда" : Teams.nameOf(ClientState.crew);
        g.drawString(font, Ui.trim(font, "отряд: " + team + " · заряд " + ClientState.sat, W - 20), x + 10, cardY + 15, Ui.MUTED, false);
        g.drawString(font, Ui.trim(font, "живых " + ClientState.aliveCount, W - 20), x + 10, cardY + 25, Ui.MUTED, false);
    }

    // ---------- КАРТА ----------

    private void drawMap(GuiGraphics g, int x, int y) {
        int cx = x + 56;
        int cy = y + CONTENT_TOP + 46;
        int r = 44;
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF0B0F12);
        for (int i = -r; i <= r; i += 22) {
            g.fill(cx - r, cy + i, cx + r, cy + i + 1, 0xFF1E2A24);
            g.fill(cx + i, cy - r, cx + i + 1, cy + r, 0xFF1E2A24);
        }
        if (minecraft != null && minecraft.player != null) {
            double px = minecraft.player.getX();
            double pz = minecraft.player.getZ();
            for (ClientState.BeamRow beam : ClientState.beams) {
                Ui.ray(g, cx, cy, r, ClientState.radarR, beam.ox - px, beam.oz - pz, beam.angle, Ui.AMBER);
            }
            for (ClientState.Blip blip : ClientState.blips) {
                Ui.blip(g, cx, cy, r, ClientState.radarR, blip.dx(), blip.dz(), Ui.blipColor(blip.kind()));
            }
            Ui.selfArrow(g, cx, cy, minecraft.player.getYRot());
        }
        g.drawString(font, "С", cx - 2, cy - r + 2, Ui.MUTED, false);
        int ly = y + CONTENT_TOP;
        int rx = x + 108;
        int rw = W - 116;
        int n = 0;
        for (ClientState.Blip blip : ClientState.blips) {
            if (n++ >= 6) break;
            String text = Ui.t("blackchannel.blip." + blip.kind()) + " " + blip.dist() + "м";
            g.drawString(font, Ui.trim(font, text, rw), rx, ly, Ui.blipColor(blip.kind()), false);
            ly += 10;
        }
        if (n == 0) {
            g.drawString(font, "пусто", rx, ly, Ui.MUTED, false);
            ly += 10;
        }
        ly += 4;
        for (ClientState.IntelRow row : ClientState.intel) {
            if (ly > TABS_Y - 26) break;
            String text = row.handle + " ±" + row.err;
            g.drawString(font, Ui.trim(font, text, rw), rx, ly, row.stale ? Ui.MUTED : Ui.GREEN, false);
            ly += 10;
        }
        for (ClientState.TrackRow track : ClientState.trackers) {
            if (ly > TABS_Y - 24) break;
            String text = (track.live() ? "ЖУК " : "БЫЛ ") + track.x() + " " + track.z();
            g.drawString(font, Ui.trim(font, text, rw), rx, ly, track.live() ? Ui.AMBER : Ui.MUTED, false);
            ly += 10;
        }
        Ui.button(g, font, hits, x + 8, TABS_Y - 18, 96, 12, Ui.t("screen.blackchannel.btn.radar_hud"),
                ClientState.tip("screen.blackchannel.tip.radar_hud", ClientState.cfg("radarPhone", 52), ClientState.cfg("radarLaptop", 110),
                        ClientState.cfg("structure", 360), ClientState.cfg("dishBonus", 80)),
                ClientState.radarHud, Actions.RADAR, "");
    }

    // ---------- СЕТЬ ----------

    private void drawNet(GuiGraphics g, int x, int y) {
        int ly = CONTENT_TOP;
        Ui.button(g, font, hits, x + 8, ly, 52, 12, "DARK", "darknet".equals(ClientState.channel), Actions.CHAN, "darknet");
        Ui.button(g, font, hits, x + 62, ly, 52, 12, "LOCAL", "local".equals(ClientState.channel), Actions.CHAN, "local");
        Ui.button(g, font, hits, x + 116, ly, 40, 12, "DM", "dm".equals(ClientState.channel), "dm-mode", "");
        Ui.button(g, font, hits, x + 158, ly, 82, 12, Ui.t("screen.blackchannel.btn.airplane"),
                Ui.t("screen.blackchannel.tip.airplane"), ClientState.airplane, Actions.AIR, "");
        ly += 14;
        Ui.button(g, font, hits, x + 8, ly, 40, 12, "DND", Ui.t("screen.blackchannel.tip.dnd"), ClientState.dnd, Actions.DND, "");
        Ui.button(g, font, hits, x + 50, ly, 100, 12, Ui.t("screen.blackchannel.btn.manual"), false, "manual", "");
        Ui.button(g, font, hits, x + 152, ly, 88, 12, Ui.t("screen.blackchannel.btn.wipe_sim"), Ui.t("screen.blackchannel.tip.wipe"), false, Actions.WIPE, "");
        ly += 16;
        int from = Math.max(0, ClientState.chat.size() - 8);
        for (int i = from; i < ClientState.chat.size(); i++) {
            if (ly > TABS_Y - 58) break;
            ClientState.ChatRow row = ClientState.chat.get(i);
            g.drawString(font, Ui.trim(font, row.from() + ": " + row.text(), W - 20), x + 10, ly, row.enc() ? Ui.BLUE : Ui.TEXT, false);
            ly += 10;
        }
        // Строка расходников: VPN, IDS, закалка
        int buffY = TABS_Y - 54;
        g.fill(x + 6, buffY - 3, x + W - 6, buffY - 2, Ui.EDGE_DIM);
        String buffs = (ClientState.vpn >= 2 ? "VPN2" : ClientState.vpn == 1 ? "VPN" : "—")
                + (ClientState.ids ? " · IDS" : "") + (ClientState.hard ? " · закалка" : "");
        g.drawString(font, Ui.trim(font, buffs, W - 20), x + 10, buffY, ClientState.vpn + (ClientState.ids ? 1 : 0) > 0 ? Ui.GREEN : Ui.MUTED, false);
        String note = ClientState.resultFor("chat");
        g.drawString(font, Ui.trim(font, note.isEmpty() ? Ui.t("screen.blackchannel.send_hint") : note, W - 20),
                x + 10, buffY + 10, note.isEmpty() ? Ui.MUTED : Ui.AMBER, false);
        // Контакты — по три в ряд
        int cy = buffY + 22;
        int i = 0;
        for (String contact : ClientState.contacts) {
            if (i >= 9) break;
            int bx = x + 8 + (i % 3) * 78;
            int by = cy + (i / 3) * 13;
            Ui.button(g, font, hits, bx, by, 74, 11, contact, contact.equals(dmTarget), "pick-dm", contact);
            i++;
        }
    }

    // ---------- РЫНОК ----------

    private void drawMarket(GuiGraphics g, int x, int y, int mx, int my) {
        int ty = y + CONTENT_TOP;
        Ui.button(g, font, hits, x + 8, ty, 74, 13, Ui.t("screen.blackchannel.market.shop"), marketTab == 0, "mkt", "0");
        Ui.button(g, font, hits, x + 86, ty, 74, 13, Ui.t("screen.blackchannel.market.board"), marketTab == 1, "mkt", "1");
        Ui.button(g, font, hits, x + 164, ty, 76, 13, Ui.t("screen.blackchannel.market.auction"), marketTab == 2, "mkt", "2");
        g.fill(x + 8, ty + 15, x + W - 8, TABS_Y - 46, 0xFF0B0F12);
        if (marketTab == 0) drawShop(g, x, ty, mx, my);
        else drawListings(g, x, ty, marketTab == 1, mx, my);
    }

    private void drawShop(GuiGraphics g, int x, int ty, int mx, int my) {
        var entries = Catalog.entries();
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
            if (lot(g, sx, sy, stack, entry.price(), on, mx, my)) {
                hits.add(new Ui.Hit(sx, sy, 36, 38, "mpick", entry.id(), null));
                armTip(stack, entry.price(), Ui.t("screen.blackchannel.market.vendor"), null);
            }
        }
        if (picked == null) {
            marketFoot(g, x, ty, ItemStack.EMPTY, null, "", 0, null, "", 1, null);
            return;
        }
        ItemStack stack = new ItemStack(picked.item().get(), Math.max(1, picked.count()));
        String name = stack.getHoverName().getString();
        if (picked.count() > 1) name = name + " x" + picked.count();
        marketFoot(g, x, ty, stack, name, Ui.t("screen.blackchannel.market.vendor"), picked.price(), Actions.BUY, picked.id(), 1, null);
    }

    private void drawListings(GuiGraphics g, int x, int ty, boolean intelBoard, int mx, int my) {
        List<ClientState.ListingRow> rows = new ArrayList<>();
        for (ClientState.ListingRow row : ClientState.listings) {
            if (row.intel == intelBoard) rows.add(row);
        }
        if (rows.isEmpty()) {
            String empty = intelBoard ? Ui.t("screen.blackchannel.market.empty") : Ui.t("screen.blackchannel.market.empty_items");
            int ly = ty + 44;
            for (String line : Ui.wrap(font, empty, W - 28)) {
                g.drawString(font, line, x + 14, ly, Ui.MUTED, false);
                ly += 10;
            }
            marketFoot(g, x, ty, ItemStack.EMPTY, "", "", 0, null, "", 1, null);
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
            if (lot(g, sx, sy, stack, row.price, on, mx, my)) {
                hits.add(new Ui.Hit(sx, sy, 36, 38, "lpick", String.valueOf(row.id), null));
                armTip(stack, row.price, row.seller, null);
                if (row.intel) {
                    if (row.dim != null && !row.dim.isEmpty()) hoverLines.add(Component.literal(row.dim).withStyle(ChatFormatting.GRAY));
                    hoverLines.add(Component.literal(Ui.maskCoords(row.id)).withStyle(ChatFormatting.OBFUSCATED));
                }
            }
        }
        if (picked == null) {
            marketFoot(g, x, ty, ItemStack.EMPTY, null, "", 0, null, "", pages, null);
            return;
        }
        ItemStack stack = listingStack(picked);
        String name = stack.isEmpty() ? picked.desc : stack.getHoverName().getString();
        if (stack.getCount() > 1) name = name + " x" + stack.getCount();
        marketFoot(g, x, ty, stack, name, picked.seller, picked.price, Actions.BUY_LIST, String.valueOf(picked.id), pages, null);
    }

    private int lotX(int x, int col) {
        return x + 11 + col * 38;
    }

    private int lotY(int ty, int row) {
        return ty + 19 + row * 40;
    }

    private void emptyLot(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 36, sy + 38, 0xFF0C1210);
        g.fill(sx, sy, sx + 36, sy + 1, 0xFF1B3A30);
    }

    private boolean lot(GuiGraphics g, int sx, int sy, ItemStack stack, int price, boolean on, int mx, int my) {
        boolean over = mx >= sx && my >= sy && mx < sx + 36 && my < sy + 38;
        g.fill(sx, sy, sx + 36, sy + 38, on ? 0xFF2A2213 : 0xFF101614);
        g.fill(sx, sy, sx + 36, sy + 1, on ? Ui.EDGE : over ? Ui.AMBER : Ui.EDGE_DIM);
        if (stack != null && !stack.isEmpty()) {
            g.renderItem(stack, sx + 10, sy + 3);
            g.renderItemDecorations(font, stack, sx + 10, sy + 3);
        } else {
            g.drawString(font, "?", sx + 15, sy + 12, Ui.MUTED, false);
        }
        String label = "#" + price;
        int pw = font.width(label);
        int color = price > ClientState.crypto ? Ui.RED : Ui.AMBER;
        g.drawString(font, label, sx + Math.max(2, (36 - pw) / 2), sy + 26, color, false);
        return over;
    }

    private void marketFoot(GuiGraphics g, int x, int ty, ItemStack stack, String title, String meta,
                            int price, String buyAction, String buyArg, int pages, Component secret) {
        int fy = TABS_Y - 44;
        g.fill(x + 6, fy, x + W - 6, fy + 1, Ui.EDGE_DIM);
        if (stack == null || stack.isEmpty()) {
            String note = title == null ? Ui.t("screen.blackchannel.market.pick") : title;
            int ly = fy + 6;
            for (String line : Ui.wrap(font, note, W - 24)) {
                if (ly > fy + 40) break;
                g.drawString(font, line, x + 12, ly, Ui.MUTED, false);
                ly += 10;
            }
        } else {
            g.renderItem(stack, x + 10, fy + 4);
            g.drawString(font, Ui.trim(font, title, 118), x + 30, fy + 3, Ui.TEXT, false);
            int priceColor = price > ClientState.crypto ? Ui.RED : Ui.AMBER;
            String line = (meta == null || meta.isEmpty() ? "" : meta + "  ") + "#" + price;
            g.drawString(font, Ui.trim(font, line, 118), x + 30, fy + 13, priceColor, false);
            if (buyAction != null) {
                Ui.button(g, font, hits, x + 160, fy + 4, 76, 14, Ui.t("screen.blackchannel.market.buy"),
                        price <= ClientState.crypto, buyAction, buyArg);
            }
        }
        if (pages > 1) {
            Ui.button(g, font, hits, x + W - 62, fy + 22, 22, 12, "<", marketPage > 0, "mpage", "-1");
            String p = (marketPage + 1) + "/" + pages;
            g.drawString(font, p, x + W - 38, fy + 25, Ui.MUTED, false);
            Ui.button(g, font, hits, x + W - 26, fy + 22, 20, 12, ">", marketPage < pages - 1, "mpage", "1");
        }
        String result = ClientState.resultFor(marketTab == 0 ? "market" : "board");
        if (!result.isEmpty()) g.drawString(font, Ui.trim(font, result, W - 20), x + 10, fy + 22, Ui.AMBER, false);
    }

    private void armTip(ItemStack stack, int price, String seller, String extra) {
        hoverStack = stack == null ? ItemStack.EMPTY : stack;
        hoverLines.clear();
        if (minecraft != null && hoverStack != null && !hoverStack.isEmpty()) hoverLines.addAll(Screen.getTooltipFromItem(minecraft, hoverStack));
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

    // ---------- вкладки и ввод ----------

    private void drawTabs(GuiGraphics g, int x, int y) {
        g.fill(x + 6, TABS_Y - 2, x + W - 6, TABS_Y - 1, Ui.EDGE_DIM);
        String[] names = {Ui.t("screen.blackchannel.tab.feed"), Ui.t("screen.blackchannel.tab.map"),
                Ui.t("screen.blackchannel.tab.net"), Ui.t("screen.blackchannel.tab.market_short")};
        for (int i = 0; i < 4; i++) {
            int bx = x + 6 + i * 60;
            Ui.button(g, font, hits, bx, y + TABS_Y, 58, TABS_H, names[i], tab == i, "tab", String.valueOf(i));
        }
    }

    private String age(int seconds) {
        if (seconds < 60) return seconds + "с";
        return (seconds / 60) + "м" + (seconds % 60) + "с";
    }

    private String clock(int totalSeconds) {
        int s = Math.max(0, totalSeconds);
        return String.format("%d:%02d", s / 60, s % 60);
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
            case "mpage" -> marketPage = Math.max(0, marketPage + Integer.parseInt(hit.arg()));
            case "mpick" -> shopPick = hit.arg();
            case "lpick" -> listPick = Integer.parseInt(hit.arg());
            case "pick-dm" -> {
                dmTarget = hit.arg();
                send(Actions.CHAN, "dm:" + hit.arg());
            }
            case "dm-mode" -> send(Actions.CHAN, dmTarget.isEmpty() ? "dm:" : "dm:" + dmTarget);
            case "manual" -> minecraft.setScreen(new ManualScreen());
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
        if ("dm".equals(ClientState.channel) && !dmTarget.isEmpty()) send(Actions.DM, dmTarget + " " + text);
        else send(Actions.SAY, text);
        input.setValue("");
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 3) marketPage = Math.max(0, marketPage - (int) Math.signum(delta));
        else scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
