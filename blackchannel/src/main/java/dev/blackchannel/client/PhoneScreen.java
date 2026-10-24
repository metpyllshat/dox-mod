package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.logic.Catalog;
import dev.blackchannel.menu.Menus;
import dev.blackchannel.net.ModNetwork;
import dev.blackchannel.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class PhoneScreen extends AbstractContainerScreen<Menus.PhoneMenu> {
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
        hoverStack = ItemStack.EMPTY;
        hoverLines.clear();
        if (input != null) {
            input.visible = tab != 2;
            if (tab == 2 && input.isFocused()) input.setFocused(false);
        }
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
        else if (tab == 2) drawMarket(g, x, y, mx, my);
        else drawGear(g, x, y);
        if (tab != 2) g.fill(x + 6, y + imageHeight - 22, x + imageWidth - 6, y + imageHeight - 6, 0xFF0C1210);
        for (var renderable : renderables) renderable.render(g, mx, my, pt);
        if (!hoverStack.isEmpty() && !hoverLines.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : hoverLines) lines.addAll(font.split(line, 220));
            if (!lines.isEmpty()) g.renderTooltip(font, lines, mx, my);
        } else Ui.tips(g, font, hits, mx, my);
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
        String chatNote = ClientState.resultFor("chat");
        g.drawString(font, Ui.trim(font, chatNote.isEmpty() ? Ui.t("screen.blackchannel.send_hint") : chatNote, imageWidth - 16), x + 8, y + imageHeight - 32, chatNote.isEmpty() ? Ui.MUTED : Ui.AMBER, false);
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
        if (minecraft != null && minecraft.player != null) Ui.selfArrow(g, cx, cy, minecraft.player.getYRot());
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

    private void drawMarket(GuiGraphics g, int x, int y, int mx, int my) {
        Ui.button(g, font, hits, x + 6, y + 44, 74, 14, Ui.t("screen.blackchannel.market.shop"), marketTab == 0, "mkt", "0");
        Ui.button(g, font, hits, x + 84, y + 44, 74, 14, Ui.t("screen.blackchannel.market.board"), marketTab == 1, "mkt", "1");
        Ui.button(g, font, hits, x + 162, y + 44, 78, 14, Ui.t("screen.blackchannel.market.bounty"), marketTab == 2, "mkt", "2");
        g.fill(x + 8, y + 60, x + imageWidth - 8, y + 186, 0xFF07110C);
        if (marketTab == 0) drawShop(g, x, y, mx, my);
        else if (marketTab == 1) drawBoard(g, x, y, mx, my);
        else drawBounties(g, x, y);
    }

    private void drawShop(GuiGraphics g, int x, int y, int mx, int my) {
        var entries = Catalog.entries();
        Catalog.Entry picked = null;
        for (int i = 0; i < 18; i++) {
            int sx = lotX(x, i % 6);
            int sy = lotY(y, i / 6);
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
        if (picked == null) drawMarketFoot(g, x, y, ItemStack.EMPTY, null, "", 0, null, "", 1);
        else {
            ItemStack stack = new ItemStack(picked.item().get(), Math.max(1, picked.count()));
            String name = stack.getHoverName().getString();
            if (picked.count() > 1) name = name + " x" + picked.count();
            drawMarketFoot(g, x, y, stack, name, Ui.t("screen.blackchannel.market.vendor"), picked.price(), Actions.BUY, picked.id(), 1);
        }
    }

    private void drawBoard(GuiGraphics g, int x, int y, int mx, int my) {
        var rows = ClientState.listings;
        int pages = Math.max(1, (rows.size() + 17) / 18);
        if (marketPage >= pages) marketPage = pages - 1;
        if (marketPage < 0) marketPage = 0;
        if (rows.isEmpty()) {
            int ly = y + 96;
            for (String line : Ui.wrap(font, Ui.t("screen.blackchannel.market.empty"), imageWidth - 28)) {
                g.drawString(font, line, x + 14, ly, Ui.MUTED, false);
                ly += 10;
            }
            drawMarketFoot(g, x, y, ItemStack.EMPTY, "", "", 0, null, "", 1);
            return;
        }
        int start = marketPage * 18;
        ClientState.ListingRow picked = null;
        for (int i = 0; i < 18; i++) {
            int sx = lotX(x, i % 6);
            int sy = lotY(y, i / 6);
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
                if (row.intel) armSecret(row);
            }
        }
        if (picked == null) drawMarketFoot(g, x, y, ItemStack.EMPTY, null, "", 0, null, "", pages);
        else {
            ItemStack stack = listingStack(picked);
            String name = stack.isEmpty() ? picked.desc : stack.getHoverName().getString();
            if (!picked.intel && stack.getCount() > 1) name = name + " x" + stack.getCount();
            drawMarketFoot(g, x, y, stack, name, picked.seller, picked.price, Actions.BUY_LIST, String.valueOf(picked.id), pages, picked.intel ? secret(picked) : null);
        }
    }

    private void drawBounties(GuiGraphics g, int x, int y) {
        if (ClientState.bounties.isEmpty()) {
            int ly = y + 90;
            for (String line : Ui.wrap(font, Ui.t("screen.blackchannel.market.no_bounty"), imageWidth - 28)) {
                g.drawString(font, line, x + 14, ly, Ui.MUTED, false);
                ly += 10;
            }
        }
        int ly = y + 70;
        ItemStack coin = new ItemStack(ModContent.HASH.get());
        for (ClientState.BountyRow bounty : ClientState.bounties) {
            if (ly > y + 160) break;
            g.fill(x + 14, ly, x + imageWidth - 14, ly + 28, 0xFF101614);
            g.fill(x + 14, ly, x + imageWidth - 14, ly + 1, Ui.AMBER);
            g.renderItem(coin, x + 18, ly + 6);
            g.drawString(font, Ui.trim(font, "@" + bounty.handle(), 150), x + 40, ly + 6, Ui.TEXT, false);
            g.drawString(font, "#" + bounty.reward(), x + 40, ly + 16, Ui.AMBER, false);
            ly += 32;
        }
        drawMarketFoot(g, x, y, ItemStack.EMPTY, Ui.t("screen.blackchannel.market.bounty_note"), "", 0, null, "", 1);
    }

    private int lotX(int x, int col) {
        return x + 11 + col * 38;
    }

    private int lotY(int y, int row) {
        return y + 64 + row * 40;
    }

    private void emptyLot(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 36, sy + 38, 0xFF0C1210);
        g.fill(sx, sy, sx + 36, sy + 1, 0xFF1B3A30);
    }

    private boolean lot(GuiGraphics g, int sx, int sy, ItemStack stack, int price, boolean on, int mx, int my) {
        boolean over = mx >= sx && my >= sy && mx < sx + 36 && my < sy + 38;
        g.fill(sx, sy, sx + 36, sy + 38, on ? 0xFF16382C : 0xFF101614);
        g.fill(sx, sy, sx + 36, sy + 1, on ? Ui.EDGE : over ? Ui.AMBER : Ui.EDGE_DIM);
        if (stack != null && !stack.isEmpty()) {
            g.renderItem(stack, sx + 10, sy + 3);
            g.renderItemDecorations(font, stack, sx + 10, sy + 3);
        }
        String label = "#" + price;
        int pw = font.width(label);
        int color = price > ClientState.crypto ? Ui.RED : Ui.AMBER;
        g.drawString(font, label, sx + Math.max(2, (36 - pw) / 2), sy + 26, color, false);
        return over;
    }

    private void armTip(ItemStack stack, int price, String seller, String extra) {
        hoverStack = stack == null ? ItemStack.EMPTY : stack;
        hoverLines.clear();
        if (minecraft != null && hoverStack != null && !hoverStack.isEmpty()) hoverLines.addAll(Screen.getTooltipFromItem(minecraft, hoverStack));
        hoverLines.add(Component.literal("#" + price).withStyle(ChatFormatting.GOLD));
        if (seller != null && !seller.isEmpty()) hoverLines.add(Component.literal(seller).withStyle(ChatFormatting.GRAY));
        if (extra != null && !extra.isEmpty()) hoverLines.add(Component.literal(extra).withStyle(ChatFormatting.DARK_AQUA));
    }

    private void armSecret(ClientState.ListingRow row) {
        if (row.dim != null && !row.dim.isEmpty()) hoverLines.add(Component.literal(row.dim).withStyle(ChatFormatting.GRAY));
        if (row.stale) hoverLines.add(Component.literal(Ui.t("screen.blackchannel.market.stale")).withStyle(ChatFormatting.DARK_GRAY));
        hoverLines.add(secret(row));
    }

    private Component secret(ClientState.ListingRow row) {
        return Component.literal(Ui.maskCoords(row.id)).withStyle(ChatFormatting.OBFUSCATED);
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

    private void drawMarketFoot(GuiGraphics g, int x, int y, ItemStack stack, String title, String meta, int price, String buyAction, String buyArg, int pages) {
        drawMarketFoot(g, x, y, stack, title, meta, price, buyAction, buyArg, pages, null);
    }

    private void drawMarketFoot(GuiGraphics g, int x, int y, ItemStack stack, String title, String meta, int price, String buyAction, String buyArg, int pages, Component secret) {
        int resultW = imageWidth - 16;
        if (pages > 1) {
            Ui.button(g, font, hits, x + 188, y + 186, 22, 11, "<", marketPage > 0, "mpage", "-1");
            Ui.button(g, font, hits, x + 214, y + 186, 22, 11, ">", marketPage + 1 < pages, "mpage", "1");
            resultW = 170;
        }
        g.drawString(font, Ui.trim(font, ClientState.resultFor("market"), resultW), x + 8, y + 187, Ui.AMBER, false);
        g.fill(x + 6, y + 200, x + imageWidth - 6, y + 224, 0xFF101614);
        g.fill(x + 6, y + 200, x + imageWidth - 6, y + 201, Ui.EDGE_DIM);
        if (stack == null || stack.isEmpty()) {
            String note = title == null ? Ui.t("screen.blackchannel.market.pick") : title;
            int ly = y + 206;
            for (String line : Ui.wrap(font, note, imageWidth - 24)) {
                if (ly > y + 216) break;
                g.drawString(font, line, x + 12, ly, Ui.MUTED, false);
                ly += 10;
            }
            return;
        }
        g.renderItem(stack, x + 10, y + 204);
        g.drawString(font, Ui.trim(font, title, 116), x + 30, y + 202, Ui.TEXT, false);
        int priceColor = price > ClientState.crypto ? Ui.RED : Ui.AMBER;
        if (secret != null) {
            g.drawString(font, secret, x + 30, y + 212, Ui.RED, false);
            g.drawString(font, "#" + price, x + 34 + font.width(secret.getString()), y + 212, priceColor, false);
        } else {
            String line = (meta == null || meta.isEmpty() ? "" : meta + "  ") + "#" + price;
            g.drawString(font, Ui.trim(font, line, 116), x + 30, y + 212, priceColor, false);
        }
        if (buyAction != null) {
            Ui.button(g, font, hits, x + 160, y + 204, 76, 14, Ui.t("screen.blackchannel.market.buy"), price <= ClientState.crypto, buyAction, buyArg);
        }
    }

    private void buffRow(GuiGraphics g, int x, int y, String label, boolean on, String refundKey, int fallback, String arg) {
        g.drawString(font, label, x + 8, y + 2, on ? Ui.TEXT : Ui.MUTED, false);
        if (!on) {
            g.drawString(font, Ui.t("screen.blackchannel.buff.none"), x + 52, y + 2, Ui.MUTED, false);
            return;
        }
        int refund = ClientState.cfg(refundKey, fallback);
        Ui.button(g, font, hits, x + 122, y, 110, 12, Ui.t("screen.blackchannel.btn.sell_buff") + " #" + refund, Ui.t("screen.blackchannel.tip.sell_buff"), false, Actions.SELL_BUFF, arg);
    }

    private void drawGear(GuiGraphics g, int x, int y) {
        Ui.button(g, font, hits, x + 8, y + 48, 110, 12, Ui.t("screen.blackchannel.btn.airplane"), Ui.t("screen.blackchannel.tip.airplane"), ClientState.airplane, Actions.AIR, "");
        Ui.button(g, font, hits, x + 122, y + 48, 110, 12, "DND", Ui.t("screen.blackchannel.tip.dnd"), ClientState.dnd, Actions.DND, "");
        Ui.button(g, font, hits, x + 8, y + 64, 110, 12, "CREW", Ui.t("screen.blackchannel.tip.crew"), false, Actions.CREW, input == null ? "" : input.getValue());
        Ui.button(g, font, hits, x + 122, y + 64, 110, 12, Ui.t("screen.blackchannel.btn.manual"), false, "manual", "");
        buffRow(g, x, y + 82, ClientState.vpn >= 2 ? Ui.t("screen.blackchannel.buff.vpn2") : Ui.t("screen.blackchannel.buff.vpn"), ClientState.vpn > 0, ClientState.vpn >= 2 ? "refundDeep" : "refundVpn", ClientState.vpn >= 2 ? 24 : 14, "vpn");
        buffRow(g, x, y + 96, Ui.t("screen.blackchannel.buff.ids"), ClientState.ids, "refundIds", 12, "ids");
        buffRow(g, x, y + 110, Ui.t("screen.blackchannel.buff.harden"), ClientState.hard, "refundHarden", 11, "harden");
        g.drawString(font, "SAT " + ClientState.sat + "  CREW " + ClientState.crew, x + 8, y + 126, Ui.MUTED, false);
        int ly = y + 140;
        for (ClientState.DeanonRow row : ClientState.deanons) {
            if (ly > y + 180) break;
            g.drawString(font, Ui.trim(font, row.handle() + " = " + row.name(), 220), x + 8, ly, Ui.BLUE, false);
            ly += 10;
        }
        g.drawString(font, Ui.trim(font, ClientState.resultFor("gear"), imageWidth - 16), x + 8, y + imageHeight - 36, Ui.AMBER, false);
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
        if (tab == 2 && marketTab == 1) marketPage = Math.max(0, marketPage - (int) Math.signum(delta));
        else scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    private void send(String action, String arg) {
        if (ModNetwork.CHANNEL != null) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(action, arg == null ? "" : arg));
    }
}
