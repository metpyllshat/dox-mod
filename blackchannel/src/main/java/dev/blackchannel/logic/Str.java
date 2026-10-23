package dev.blackchannel.logic;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class Str {
    private static final String[] A = {
            "ghost", "null", "wire", "ash", "kiln", "vapor", "mica", "rook", "salt", "phosphor",
            "drift", "keel", "oxide", "nylon", "quartz", "sable", "iris", "nimbus", "cinder", "lantern"
    };
    private static final String[] B = {
            "fox", "moth", "pike", "loom", "spike", "orchid", "vector", "cabin", "relay", "knife",
            "harbor", "static", "marble", "cipher", "thistle", "wick", "glyph", "otter", "prism", "yarrow"
    };

    private Str() {}

    public static String norm(String s) {
        if (s == null) return "";
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("@")) s = s.substring(1);
        s = s.replaceAll("[^a-z0-9_\\-]", "");
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }

    public static String clean(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(Math.min(s.length(), 180));
        for (int i = 0; i < s.length() && out.length() < 180; i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' || c == '\n' || c == '\r' || c == '%') c = ' ';
            if (c >= 32) out.append(c);
        }
        return out.toString().trim();
    }

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static String dimShort(String dim) {
        if (dim == null || dim.isEmpty()) return "?";
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    public static String rollHandle(RandomSource random) {
        return A[random.nextInt(A.length)] + "-" + B[random.nextInt(B.length)] + "-" + (10 + random.nextInt(90));
    }

    public static String crew(String s) {
        s = norm(s);
        if (s.length() > 8) s = s.substring(0, 8);
        return s;
    }
}
