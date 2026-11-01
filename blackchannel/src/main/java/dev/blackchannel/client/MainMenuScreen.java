package dev.blackchannel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Главное меню: не список кнопок, а экран другой игры — затёртый ночной пейзаж,
 * терминал слева, сводка канала справа, крупный заголовок и широкие кнопки.
 * <p>
 * Всё, кроме фона, нарисовано кодом: ни одной иконки, поэтому меню не расползётся
 * на любом масштабе интерфейса. Ниже кнопок — бегущая строка помех и версия.
 */
public class MainMenuScreen extends Screen {
    /** true — значит мы сами открываем ванильный экран и хук не должен вмешиваться. */
    public static boolean bypass;

    private static final ResourceLocation BACKDROP = new ResourceLocation("blackchannel", "textures/gui/menu_bg.png");
    private static final int BUTTON_W = 340;
    private static final int BUTTON_H = 26;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private final List<String> terminal = new ArrayList<>();
    private final long born = System.currentTimeMillis();
    private final Random random = new Random();
    private long lastLine;

    public MainMenuScreen() {
        super(Component.translatable("screen.blackchannel.menu.title"));
    }

    @Override
    protected void init() {
        bypass = false;
        hits.clear();
        terminal.clear();
        terminal.add("> scan_user");
        terminal.add("> search_history");
        terminal.add("> leak_data");
        lastLine = System.currentTimeMillis() - 900;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.mouse(mx, my);
        hits.clear();
        int w = this.width;
        int h = this.height;
        long now = System.currentTimeMillis();
        float fade = Math.min(1f, (now - born) / 600f);

        // Фон: картинка на весь экран, поверх — затемнение, чтобы текст читался
        g.blit(BACKDROP, 0, 0, 0, 0f, 0f, w, h, w, h);
        g.fillGradient(0, 0, w, h, 0xC805080A, 0xE605080A);
        g.fill(0, 0, w, h, 0x2A1E3A22);
        Ui.scanlines(g, 0, 0, w, h, 0x30000000);

        glitch(g, w, h, now);
        terminal(g, w, h, now);
        status(g, w, h, now);
        title(g, w, h, now, fade);
        buttons(g, w, h, mx, my);
        footer(g, w, h, now);

        String hint = Ui.t("screen.blackchannel.menu.admin_hint");
        g.drawString(this.font, hint, w / 2 - this.font.width(hint) / 2, h - 34, 0x66A8B0A6, false);
        Ui.tips(g, this.font, hits, mx, my);
    }

    // ---------- фон и помехи ----------

    private void glitch(GuiGraphics g, int w, int h, long now) {
        for (int i = 0; i < 9; i++) {
            int bandY = (int) ((now / 14 + i * 137) % (h + 60)) - 30;
            int shade = (i % 4 == 0) ? 0x3300FF6A : 0x1AFFFFFF;
            g.fill(0, bandY, w, bandY + (i % 3 == 0 ? 2 : 1), shade);
        }
        // Редкий сдвиг всей картинки: экран «дёргается»
        if ((now / 90) % 23 == 0) {
            int stripY = (int) ((now / 3) % h);
            g.fillGradient(0, stripY, w, Math.min(h, stripY + 10), 0x402EFF9A, 0x002EFF9A);
        }
    }

    // ---------- текст терминала слева ----------

    private void terminal(GuiGraphics g, int w, int h, long now) {
        // На узком окне терминал уступает место кнопкам
        int bw = Math.min(BUTTON_W, w - 40);
        if (w / 2 - bw / 2 - 12 < 120) return;
        String[] script = {
                "> scan_user", "> search_history", "> leak_data", "> deanonymize",
                "> upload_to_archive", "> complete", "> target:...deanon",
                "> status: success", "> proxy: rotated", "> channel: 21.47",
                "> caches: sealed", "> uplink: standby"
        };
        if (now - lastLine > 900) {
            lastLine = now;
            if (terminal.size() < script.length) {
                String next = script[terminal.size()];
                // Иногда строка «срывается» в мусор: так экран выглядит живым
                terminal.add(random.nextInt(6) == 0 ? next + "  " + noise(6) : next);
            } else {
                terminal.remove(0);
                terminal.add(script[random.nextInt(script.length)]);
            }
        }
        int x = 12;
        int y = h - 12 - terminal.size() * 11;
        for (int i = 0; i < terminal.size(); i++) {
            int color = i == terminal.size() - 1 ? 0xFF9BE8A6 : 0x66A8C0A4;
            g.drawString(this.font, terminal.get(i), x, y + i * 11, color, false);
        }
    }

    private String noise(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append("░▒▓█#@%&".charAt(random.nextInt(8)));
        return sb.toString();
    }

    // ---------- сводка справа ----------

    private void status(GuiGraphics g, int w, int h, long now) {
        String[] lines = {
                "YOU ARE NOT SAFE",
                "IP: 192.168.0.1",
                "LOCATION: UNKNOWN",
                "STATUS: EXPOSED",
                "TRUST NO ONE"
        };
        int x = w - 12;
        int y = 14;
        for (int i = 0; i < lines.length; i++) {
            String line = "> " + lines[i];
            int color = i == 3 ? Ui.RED : i == 4 ? 0xFF9BE8A6 : 0xFF8FA89A;
            g.drawString(this.font, line, x - this.font.width(line), y + i * 11, color, false);
        }
        // Мигающий курсор под сводкой
        if ((now / 500) % 2 == 0) g.fill(x - 4, y + lines.length * 11 + 2, x - 2, y + lines.length * 11 + 10, 0xFF9BE8A6);
        String motto = Ui.t("screen.blackchannel.menu.motto");
        g.drawString(this.font, motto, x - this.font.width(motto), y + lines.length * 11 + 16, 0xAAE0A72E, false);
    }

    // ---------- заголовок ----------

    private void title(GuiGraphics g, int w, int h, long now, float fade) {
        int alpha = (int) (255 * fade);
        if (alpha <= 4) return;
        String title = Ui.t("screen.blackchannel.menu.title");
        // Заголовок тянется за шириной окна, но не превращается в кашу на узком экране
        float scale = Math.min(5f, Math.max(2.6f, w / 170f));
        int tw = (int) (this.font.width(title) * scale);

        g.pose().pushPose();
        g.pose().translate(w / 2f - tw / 2f, h * 0.17f, 0);
        g.pose().scale(scale, scale, 1f);
        // Тень и «двойная печать»: заголовок выглядит как штамп на металле
        this.font.drawShadow(g.pose(), title, 2, 3, 0xFF0A0D0A);
        this.font.draw(g.pose(), title, 0, 0, withAlpha(0xFFC9D2C4, alpha));
        this.font.draw(g.pose(), title, 1, 0, withAlpha(0x6640FF9A, alpha));
        g.pose().popPose();

        String sub = Ui.t("screen.blackchannel.menu.subtitle");
        int sc = Math.max(1, (int) (scale / 2));
        g.pose().pushPose();
        g.pose().translate(w / 2f - this.font.width(sub) * sc / 2f, h * 0.17f + scale * 10 + 6, 0);
        g.pose().scale(sc, sc, 1f);
        this.font.draw(g.pose(), sub, 0, 0, withAlpha(0xFF8FD8A0, alpha));
        g.pose().popPose();

        String version = Ui.t("screen.blackchannel.menu.version");
        g.drawString(this.font, version, w / 2 - this.font.width(version) / 2,
                h * 0.17f + scale * 10 + 6 + sc * 10 + 6, 0x66A8B0A6, false);
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    // ---------- кнопки ----------

    private void buttons(GuiGraphics g, int w, int h, int mx, int my) {
        int bw = Math.min(BUTTON_W, w - 40);
        int x = w / 2 - bw / 2;
        int y = (int) (h * 0.40f);
        menu(g, x, y, bw, Ui.t("screen.blackchannel.menu.single"), "single", 30);
        y += 34;
        menu(g, x, y, bw, Ui.t("screen.blackchannel.menu.multi"), "multi", 30);
        y += 34;
        if (realmsAvailable()) {
            menu(g, x, y, bw, Ui.t("screen.blackchannel.menu.realms"), "realms", 30);
            y += 40;
        } else y += 6;
        int half = (bw - 8) / 2;
        menu(g, x, y, half, Ui.t("screen.blackchannel.menu.settings"), "settings", 22);
        menu(g, x + half + 8, y, half, Ui.t("screen.blackchannel.menu.quit"), "quit", 22);
        if (mods() != null) {
            String mods = Ui.t("screen.blackchannel.menu.mods");
            int mw = this.font.width(mods) + 12;
            menu(g, x + bw - mw, y + 26, mw, mods, "mods", 14);
        }
    }

    /** Кнопка меню: широкая плашка с рамкой, при наведении — янтарная полоса слева. */
    private void menu(GuiGraphics g, int x, int y, int w, String label, String action, int h) {
        boolean over = Ui.hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, over ? 0xE02A2213 : 0xD0121518);
        g.fill(x, y, x + w, y + 1, over ? Ui.EDGE : 0xFF3A2F16);
        g.fill(x, y + h - 1, x + w, y + h, over ? 0xFF7A5F1A : 0xFF05080A);
        g.fill(x, y, x + 1, y + h, over ? Ui.EDGE : 0xFF2A2318);
        g.fill(x + w - 1, y, x + w, y + h, over ? Ui.EDGE : 0xFF2A2318);
        int color = over ? 0xFFFFD98A : Ui.TEXT;
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, color, true);
        if (over) g.fill(x + 2, y + 2, x + 5, y + h - 2, Ui.EDGE);
        hits.add(new Ui.Hit(x, y, w, h, action, "", null));
    }

    /** Список модов Forge: если класса нет в сборке, кнопка просто не появляется. */
    private Screen mods() {
        try {
            Class<?> cls = Class.forName("net.minecraftforge.client.gui.ModListScreen");
            return (Screen) cls.getConstructor(Screen.class).newInstance(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean realmsAvailable() {
        return realms(this) != null;
    }

    private static Screen realms(Screen parent) {
        try {
            Class<?> cls = Class.forName("com.mojang.realmsclient.gui.screens.RealmsMainScreen");
            return (Screen) cls.getConstructor(Screen.class).newInstance(parent);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ---------- подвал ----------

    private void footer(GuiGraphics g, int w, int h, long now) {
        String strip = "░▒▓ ░░▓▓▒▒░░▓▓ ▓▓▒▒░░▓▓▒▒ ░▒░▓▒░ ▒▓▒░▓ ";
        int stripW = this.font.width(strip);
        int offset = (int) ((now / 45) % stripW);
        g.drawString(this.font, strip, -offset, h - 16, 0x5540FF9A, false);
        g.drawString(this.font, strip, -offset + stripW, h - 16, 0x5540FF9A, false);
        g.drawString(this.font, "Minecraft 1.20.1 · Forge", 6, h - 12, 0x66A8B0A6, false);
        String rights = Ui.t("screen.blackchannel.menu.rights");
        g.drawString(this.font, rights, w / 2 - this.font.width(rights) / 2, h - 12, 0x55808C84, false);
        String warn = "Do not distribute. Trust no one.";
        g.drawString(this.font, warn, w - 6 - this.font.width(warn), h - 12, 0x55808C84, false);
    }

    // ---------- ввод ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (Ui.Hit hit : hits) {
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            open(hit.action());
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void open(String action) {
        Minecraft mc = Minecraft.getInstance();
        bypass = true;
        switch (action) {
            case "single" -> mc.setScreen(new SelectWorldScreen(this));
            case "multi" -> mc.setScreen(new JoinMultiplayerScreen(this));
            case "realms" -> {
                Screen realms = realms(this);
                if (realms != null) mc.setScreen(realms);
                else mc.setScreen(new TitleScreen());
            }
            case "settings" -> mc.setScreen(new OptionsScreen(this, mc.options));
            case "mods" -> {
                Screen mods = mods();
                if (mods != null) mc.setScreen(mods);
            }
            case "quit" -> mc.stop();
            default -> { }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
