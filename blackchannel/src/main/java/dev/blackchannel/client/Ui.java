package dev.blackchannel.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая графика интерфейсов: палитра, панель, кнопки, полоски, радар.
 * <p>
 * Экран — это прибор, а не окно Minecraft: тёмный графит, янтарные рамки,
 * костяной текст и CRT-зелёный для того, что работает. Всё рисуется без текстур,
 * поэтому масштаб интерфейса не влияет на вид.
 */
public final class Ui {
    // Палитра «прибор»: графит, кость, янтарь, CRT-зелёный.
    public static final int BG = 0xF012161A;
    public static final int PANEL = 0xF0161E24;
    public static final int DEEP = 0xFF0B0F12;
    public static final int EDGE = 0xFFE0A72E;
    public static final int EDGE_DIM = 0xFF463818;
    public static final int TEXT = 0xFFD9D2C4;
    public static final int MUTED = 0xFF7A838B;
    public static final int AMBER = 0xFFE0A72E;
    public static final int RED = 0xFFD64545;
    public static final int BLUE = 0xFF7EC8FF;
    public static final int GREEN = 0xFF7FE08A;
    public static final int ON_FILL = 0xFF2A2213;
    public static final int OFF_FILL = 0xFF101518;

    private static int mouseX = -1000;
    private static int mouseY = -1000;

    private Ui() {}

    public record Hit(int x, int y, int w, int h, String action, String arg, String tip) {}

    /** Экран сообщает, где мышь: подсветка кнопок не требует параметров в каждом вызове. */
    public static void mouse(double x, double y) {
        mouseX = (int) x;
        mouseY = (int) y;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static boolean hover(int x, int y, int w, int h) {
        return inside(mouseX, mouseY, x, y, w, h);
    }

    // ---------- панели ----------

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        g.fillGradient(x, y, x + w, y + 14, 0x1AE0A72E, 0x00E0A72E);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_DIM);
        g.fill(x, y, x + 1, y + h, EDGE_DIM);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_DIM);
        // Уголки: читаются как скобки прибора
        bracket(g, x + 1, y + 1, 1, 1, EDGE);
        bracket(g, x + w - 2, y + 1, -1, 1, EDGE);
        bracket(g, x + 1, y + h - 2, 1, -1, EDGE_DIM);
        bracket(g, x + w - 2, y + h - 2, -1, -1, EDGE_DIM);
    }

    private static void bracket(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        g.fill(x, y, x + dx * 5 + (dx > 0 ? 1 : 0), y + 1, color);
        g.fill(x, y, x + 1, y + dy * 5 + (dy > 0 ? 1 : 0), color);
    }

    /** Вложенная панель под содержимое: чуть темнее фона. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 2 || h < 2) return;
        g.fill(x, y, x + w, y + h, DEEP);
        g.fill(x, y, x + w, y + 1, 0xFF1B2A24);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, EDGE_DIM);
    }

    /** Сканлайны CRT: тонкие тёмные полосы через каждые три пикселя. */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int line = y; line < y + h; line += 3) g.fill(x, line, x + w, line + 1, color);
    }

    // ---------- кнопки ----------

    public static void button(GuiGraphics g, Font font, List<Hit> hits, int x, int y, int w, int h,
                              String label, boolean on, String action, String arg) {
        button(g, font, hits, x, y, w, h, label, null, on, action, arg);
    }

    public static void button(GuiGraphics g, Font font, List<Hit> hits, int x, int y, int w, int h,
                              String label, String tip, boolean on, String action, String arg) {
        boolean over = hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? ON_FILL : over ? 0xFF1A2024 : OFF_FILL);
        g.fill(x, y, x + w, y + 1, on ? EDGE : over ? 0xFFA07C22 : EDGE_DIM);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
        if (on || over) g.fill(x, y + 1, x + 2, y + h - 1, on ? EDGE : 0xFF7A5F1A);
        int color = on ? AMBER : over ? TEXT : MUTED;
        g.drawString(font, trim(font, label, w - 8), x + 4, y + (h - 8) / 2, color, false);
        hits.add(new Hit(x, y, w, h, action, arg, tip));
    }

    /** То же, но текст по центру: для баров вкладок и крупных кнопок. */
    public static void tab(GuiGraphics g, Font font, List<Hit> hits, int x, int y, int w, int h,
                           String label, boolean on, String action, String arg) {
        boolean over = hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? ON_FILL : over ? 0xFF1A2024 : OFF_FILL);
        g.fill(x, y, x + w, y + (on ? 2 : 1), on ? EDGE : over ? 0xFFA07C22 : EDGE_DIM);
        String text = trim(font, label, w - 6);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, on ? AMBER : over ? TEXT : MUTED, false);
        hits.add(new Hit(x, y, w, h, action, arg, null));
    }

    /** Строка списка: подсветка, маркер выбора и обрезка текста. */
    public static void row(GuiGraphics g, Font font, int x, int y, int w, int h, String left, String right,
                           boolean on, int color) {
        boolean over = hover(x, y, w, h);
        if (on || over) g.fill(x, y, x + w, y + h, on ? ON_FILL : 0xFF161C20);
        int rightW = right == null || right.isEmpty() ? 0 : font.width(right) + 6;
        if (rightW > 0) g.drawString(font, right, x + w - rightW + 4, y + (h - 8) / 2, on ? AMBER : MUTED, false);
        g.drawString(font, trim(font, left, w - rightW - 6), x + 3, y + (h - 8) / 2, color, false);
        if (on) g.fill(x, y, x + 2, y + h, EDGE);
    }

    public static void tips(GuiGraphics g, Font font, List<Hit> hits, int mx, int my) {
        for (Hit hit : hits) {
            if (hit.tip() == null || hit.tip().isEmpty() || !inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            List<String> lines = wrap(font, hit.tip(), 190);
            if (lines.isEmpty()) return;
            int w = 8;
            for (String line : lines) w = Math.max(w, font.width(line) + 10);
            int h = lines.size() * 10 + 7;
            int tx = Math.min(mx + 8, Math.max(2, 10000));
            int ty = my + 10;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(tx, ty, tx + w, ty + h, 0xF00D1316);
            g.fill(tx, ty, tx + w, ty + 1, EDGE);
            g.fill(tx, ty, tx + 1, ty + h, EDGE_DIM);
            int ly = ty + 4;
            for (String line : lines) {
                g.drawString(font, line, tx + 5, ly, TEXT, false);
                ly += 10;
            }
            g.pose().popPose();
            return;
        }
    }

    // ---------- полоски и текст ----------

    /** Горизонтальная шкала: заполнение слева направо, деления поверх. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float value, int color) {
        g.fill(x, y, x + w, y + h, DEEP);
        int fill = Math.round(Math.max(0f, Math.min(1f, value)) * (w - 2));
        if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
        g.fill(x, y, x + w, y + 1, 0xFF1F2A26);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF05080A);
    }

    public static String t(String key) {
        return Component.translatable(key).getString();
    }

    public static String maskCoords(int id) {
        int n = id * 1103515245 + 12345;
        int x = (n & 511) - 256;
        int y = 8 + ((n >>> 9) & 63);
        int z = ((n >>> 16) & 511) - 256;
        return x + " " + y + " " + z;
    }

    public static String trim(Font font, String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        String ellipsis = "...";
        while (!text.isEmpty() && font.width(text + ellipsis) > width) text = text.substring(0, text.length() - 1);
        return text + ellipsis;
    }

    public static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                lines.add("");
                continue;
            }
            String cur = "";
            for (String word : para.split(" ")) {
                String trial = cur.isEmpty() ? word : cur + " " + word;
                if (font.width(trial) > width && !cur.isEmpty()) {
                    lines.add(cur);
                    cur = word;
                } else cur = trial;
            }
            if (!cur.isEmpty()) lines.add(cur);
        }
        return lines;
    }

    // ---------- радар ----------

    public static void selfArrow(GuiGraphics g, int cx, int cy, float yaw) {
        double rad = Math.toRadians(yaw);
        float fx = (float) -Math.sin(rad);
        float fz = (float) Math.cos(rad);
        float sx = -fz;
        float sz = fx;
        int tipX = cx + Math.round(fx * 5);
        int tipY = cy + Math.round(fz * 5);
        int leftX = cx + Math.round(sx * 2 - fx * 2);
        int leftY = cy + Math.round(sz * 2 - fz * 2);
        int rightX = cx + Math.round(-sx * 2 - fx * 2);
        int rightY = cy + Math.round(-sz * 2 - fz * 2);
        fillTri(g, tipX, tipY, leftX, leftY, rightX, rightY, EDGE);
    }

    private static void fillTri(GuiGraphics g, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        int minY = Math.min(y0, Math.min(y1, y2));
        int maxY = Math.max(y0, Math.max(y1, y2));
        for (int y = minY; y <= maxY; y++) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int[] a = span(y, x0, y0, x1, y1);
            int[] b = span(y, x1, y1, x2, y2);
            int[] c = span(y, x2, y2, x0, y0);
            for (int[] s : new int[][]{a, b, c}) {
                if (s == null) continue;
                left = Math.min(left, s[0]);
                right = Math.max(right, s[1]);
            }
            if (left <= right) g.fill(left, y, right + 1, y + 1, color);
        }
    }

    private static int[] span(int y, int x0, int y0, int x1, int y1) {
        if (y < Math.min(y0, y1) || y > Math.max(y0, y1)) return null;
        if (y0 == y1) return new int[]{Math.min(x0, x1), Math.max(x0, x1)};
        float t = (y - y0) / (float) (y1 - y0);
        int x = Math.round(x0 + (x1 - x0) * t);
        return new int[]{x, x};
    }

    public static void blip(GuiGraphics g, int cx, int cy, int radius, int range, int dx, int dz, int color) {
        float sx = dx / (float) Math.max(1, range);
        float sz = dz / (float) Math.max(1, range);
        float mag = (float) Math.sqrt(sx * sx + sz * sz);
        if (mag > 1f) {
            sx /= mag;
            sz /= mag;
        }
        int px = cx + Math.round(sx * (radius - 3));
        int py = cy + Math.round(sz * (radius - 3));
        g.fill(px - 1, py - 1, px + 2, py + 2, color);
        g.fill(px - 2, py, px + 3, py + 1, 0x55000000);
    }

    /** Луч пеленга на радаре: точка приёма в мире, направление в градусах. */
    public static void ray(GuiGraphics g, int cx, int cy, int radius, int range, int oxBlocks, int ozBlocks,
                           double angleDeg, int color) {
        double sx = oxBlocks / (double) Math.max(1, range);
        double sz = ozBlocks / (double) Math.max(1, range);
        double px = cx + sx * (radius - 2);
        double py = cy + sz * (radius - 2);
        double dx = Math.cos(Math.toRadians(angleDeg));
        double dz = Math.sin(Math.toRadians(angleDeg));
        for (int step = 0; step < 220; step++) {
            double t = step * 0.9;
            double x = px + dx * t;
            double y = py + dz * t;
            double ddx = x - cx;
            double ddy = y - cy;
            if (ddx * ddx + ddy * ddy > (radius - 1) * (radius - 1)) break;
            g.fill((int) Math.round(x), (int) Math.round(y), (int) Math.round(x) + 1, (int) Math.round(y) + 1, color);
        }
        int dotX = (int) Math.round(px);
        int dotY = (int) Math.round(py);
        double ddx = dotX - cx;
        double ddy = dotY - cy;
        if (ddx * ddx + ddy * ddy <= (radius - 1) * (radius - 1)) g.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, color);
    }

    public static int blipColor(String kind) {
        if (kind == null) return RED;
        if (kind.startsWith("own")) return EDGE;
        return switch (kind) {
            case "friend", "beacon", "radio" -> GREEN;
            case "decoy", "dish", "radar" -> BLUE;
            case "drone", "rack", "ew" -> AMBER;
            case "cage" -> MUTED;
            default -> RED;
        };
    }
}
