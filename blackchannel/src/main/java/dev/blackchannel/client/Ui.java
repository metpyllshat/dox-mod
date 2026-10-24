package dev.blackchannel.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class Ui {
    public static final int BG = 0xF0101418;
    public static final int PANEL = 0xF0161E24;
    public static final int EDGE = 0xFF2EE6A6;
    public static final int EDGE_DIM = 0xFF1B6B52;
    public static final int TEXT = 0xFFD7FFE8;
    public static final int MUTED = 0xFF7F9A8C;
    public static final int AMBER = 0xFFFFC14A;
    public static final int RED = 0xFFFF5A6A;
    public static final int BLUE = 0xFF7EC8FF;

    private Ui() {}

    public record Hit(int x, int y, int w, int h, String action, String arg, String tip) {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE_DIM);
        g.fill(x, y, x + 1, y + h, EDGE_DIM);
        g.fill(x + w - 1, y, x + w, y + h, EDGE_DIM);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void button(GuiGraphics g, Font font, List<Hit> hits, int x, int y, int w, int h, String label, boolean on, String action, String arg) {
        button(g, font, hits, x, y, w, h, label, null, on, action, arg);
    }

    public static void button(GuiGraphics g, Font font, List<Hit> hits, int x, int y, int w, int h, String label, String tip, boolean on, String action, String arg) {
        g.fill(x, y, x + w, y + h, on ? 0xFF16382C : 0xFF12181C);
        g.fill(x, y, x + w, y + 1, on ? EDGE : EDGE_DIM);
        g.drawString(font, trim(font, label, w - 6), x + 3, y + 3, on ? TEXT : MUTED, false);
        hits.add(new Hit(x, y, w, h, action, arg, tip));
    }

    public static void tips(GuiGraphics g, Font font, List<Hit> hits, int mx, int my) {
        for (Hit hit : hits) {
            if (hit.tip() == null || hit.tip().isEmpty() || !inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            List<String> lines = wrap(font, hit.tip(), 200);
            if (lines.isEmpty()) return;
            int w = 8;
            for (String line : lines) w = Math.max(w, font.width(line) + 8);
            int h = lines.size() * 10 + 6;
            int tx = mx + 8;
            int ty = my + 8;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(tx, ty, tx + w, ty + h, 0xF0101418);
            g.fill(tx, ty, tx + w, ty + 1, EDGE);
            int ly = ty + 4;
            for (String line : lines) {
                g.drawString(font, line, tx + 4, ly, TEXT, false);
                ly += 10;
            }
            g.pose().popPose();
            return;
        }
    }

    public static String t(String key) {
        return Component.translatable(key).getString();
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

    public static void selfArrow(GuiGraphics g, int cx, int cy, float yaw) {
        double rad = Math.toRadians(yaw);
        float fx = (float) -Math.sin(rad);
        float fz = (float) Math.cos(rad);
        float sx = -fz;
        float sz = fx;
        int tipX = cx + Math.round(fx * 4);
        int tipY = cy + Math.round(fz * 4);
        int leftX = cx + Math.round(sx * 2 - fx);
        int leftY = cy + Math.round(sz * 2 - fz);
        int rightX = cx + Math.round(-sx * 2 - fx);
        int rightY = cy + Math.round(-sz * 2 - fz);
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
    }

    public static int blipColor(String kind) {
        if (kind == null) return RED;
        if (kind.startsWith("own")) return EDGE;
        return switch (kind) {
            case "friend", "beacon", "radio" -> EDGE;
            case "decoy", "dish", "radar" -> BLUE;
            case "drone", "rack" -> AMBER;
            case "cage" -> MUTED;
            default -> RED;
        };
    }
}
