package dev.blackchannel.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blackchannel.BlackChannelMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Client flood overlay: jumping text, a slow wash, and popup images. Not a strobe. */
public final class FloodHud {
    private static final Map<String, Shot> shots = new LinkedHashMap<>();
    private static String imageKey = "";
    private static long folderStamp;
    private static int frame;

    private FloodHud() {}

    public static void install(List<String> names, List<byte[]> pngs) {
        if (names == null || pngs == null) return;
        int n = Math.min(names.size(), pngs.size());
        for (int i = 0; i < n; i++) {
            put("pkt:" + safe(names.get(i)), pngs.get(i));
        }
    }

    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.bomb <= 0 || ClientState.airplane) return;
        frame++;
        if (frame % 100 == 0) reloadFolder();
        String key = String.join("|", ClientState.floodImages);
        if (!key.equals(imageKey)) {
            imageKey = key;
            loadResources(mc);
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int wash = (frame / 15) % 3;
        int color = wash == 0 ? 0x281018 : wash == 1 ? 0x102818 : 0x181018;
        g.fill(0, 0, w, h, 0x30000000 | color);
        for (int i = 0; i < 6; i++) {
            int y = (frame * 3 + i * 37) % Math.max(1, h);
            g.fill(0, y, w, y + 1, 0x553DFF9A);
        }
        int lines = ClientState.dnd ? 3 : 6;
        int step = Math.max(1, frame / 10);
        for (int i = 0; i < lines; i++) {
            String text = line(step + i * 3);
            boolean cursed = i == lines - 1;
            Component component = cursed
                    ? Component.literal(text).withStyle(Style.EMPTY.withObfuscated(true).withColor(0xFF5555))
                    : Component.literal(text);
            int x = 8 + Math.floorMod(step * 17 + i * 53, Math.max(1, w - 80));
            int y = 18 + Math.floorMod(step * 29 + i * 41, Math.max(1, h - 24));
            g.drawString(mc.font, component, x, y, cursed ? Ui.RED : (i % 2 == 0 ? Ui.AMBER : Ui.TEXT), true);
        }
        List<Shot> show = new ArrayList<>(shots.values());
        int pics = Math.min(ClientState.dnd ? 1 : 2, show.size());
        for (int i = 0; i < pics; i++) {
            Shot shot = show.get(Math.floorMod(step + i, show.size()));
            int dw = Math.min(96, Math.max(32, shot.w));
            int dh = Math.min(96, Math.max(32, shot.h));
            int x = 12 + Math.floorMod(step * 23 + i * 90, Math.max(1, w - dw - 8));
            int y = 28 + Math.floorMod(step * 19 + i * 70, Math.max(1, h - dh - 8));
            g.blit(shot.id, x, y, 0, 0f, 0f, dw, dh, shot.w, shot.h);
        }
    }

    private static String line(int index) {
        if (!ClientState.floodLines.isEmpty()) {
            return ClientState.floodLines.get(Math.floorMod(index, ClientState.floodLines.size()));
        }
        return Component.translatable("blackchannel.spam." + (1 + Math.floorMod(index, 6))).getString();
    }

    private static void loadResources(Minecraft mc) {
        shots.entrySet().removeIf(e -> e.getKey().startsWith("res:"));
        for (String raw : ClientState.floodImages) {
            ResourceLocation loc = ResourceLocation.tryParse(raw);
            if (loc == null) continue;
            try {
                var resource = mc.getResourceManager().getResource(loc);
                if (resource.isEmpty()) continue;
                try (InputStream in = resource.get().open()) {
                    put("res:" + loc, NativeImage.read(in));
                }
            } catch (Exception ignored) {
                // missing pack texture is skipped, not a purple square
            }
        }
    }

    private static void reloadFolder() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("blackchannel").resolve("flood");
        if (!Files.isDirectory(dir)) return;
        try {
            long stamp = Files.getLastModifiedTime(dir).toMillis();
            if (stamp == folderStamp) return;
            folderStamp = stamp;
            shots.entrySet().removeIf(e -> e.getKey().startsWith("file:"));
            try (var stream = Files.list(dir)) {
                List<Path> files = stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().limit(FloodLimit.MAX).toList();
                for (Path file : files) {
                    if (Files.size(file) > FloodLimit.BYTES) continue;
                    put("file:" + file.getFileName(), Files.readAllBytes(file));
                }
            }
        } catch (Exception e) {
            BlackChannelMod.LOGGER.warn("Local flood images failed: {}", e.toString());
        }
    }

    private static void put(String key, byte[] png) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            put(key, NativeImage.read(in));
        } catch (Exception ignored) {
        }
    }

    private static void put(String key, NativeImage image) {
        if (image == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            image.close();
            return;
        }
        int w = Math.max(1, image.getWidth());
        int h = Math.max(1, image.getHeight());
        Shot old = shots.remove(key);
        if (old != null) mc.getTextureManager().release(old.id);
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation id = new ResourceLocation(BlackChannelMod.MODID, "flood/dyn/" + safe(key));
        mc.getTextureManager().register(id, texture);
        shots.put(key, new Shot(id, w, h));
    }

    private static String safe(String raw) {
        String s = raw == null ? "x" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (s.length() > 48) s = s.substring(0, 48);
        if (s.isEmpty()) s = "x";
        return s;
    }

    private record Shot(ResourceLocation id, int w, int h) {}

    private static final class FloodLimit {
        static final int MAX = 4;
        static final int BYTES = 48_000;
    }
}
