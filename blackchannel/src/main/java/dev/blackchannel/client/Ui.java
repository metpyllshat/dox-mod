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

    public record Hit(int x, int y, int w, int h, String action, String arg) {}

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
        g.fill(x, y, x + w, y + h, on ? 0xFF16382C : 0xFF12181C);
        g.fill(x, y, x + w, y + 1, on ? EDGE : EDGE_DIM);
        g.drawString(font, trim(font, label, w - 6), x + 3, y + 3, on ? TEXT : MUTED, false);
        hits.add(new Hit(x, y, w, h, action, arg));
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
        return switch (kind) {
            case "friend", "beacon" -> EDGE;
            case "decoy" -> BLUE;
            case "drone" -> AMBER;
            default -> RED;
        };
    }
}
