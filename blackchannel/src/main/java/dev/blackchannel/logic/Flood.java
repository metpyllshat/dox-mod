package dev.blackchannel.logic;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server-side flood text, sounds, and optional PNG drops from config/blackchannel/flood. */
public final class Flood {
    public static final int MAX_IMAGES = 4;
    public static final int MAX_BYTES = 48_000;

    private Flood() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("blackchannel").resolve("flood");
    }

    public static void ensureDir() {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Path note = dir.resolve("README.txt");
            if (!Files.exists(note)) {
                Files.writeString(note, """
                        PNG до 48 КБ, не больше 4 файлов. Их увидит цель флуда.
                        Строки: floodLines. Звуки: floodSounds, например minecraft:block.note_block.bit
                        Картинки из ресурспака: floodImages, например blackchannel:textures/flood/noise.png
                        Громкость: floodSoundVolume. Интервалы: floodSoundTicks, floodChatTicks.
                        """);
            }
        } catch (IOException e) {
            BlackChannelMod.LOGGER.warn("Flood folder failed: {}", e.toString());
        }
    }

    public static List<String> lines() {
        List<String> out = clean(Config.FLOOD_LINES.get(), 24, 80);
        if (!out.isEmpty()) return out;
        return List.of("НЕИЗВЕСТНЫЙ НОМЕР", "ЛИНИЯ ЗАНЯТА", "ПОТОК ПАКЕТОВ", "SIM ЗАБЛОКИРОВАНА");
    }

    public static String line(int tick) {
        List<String> lines = lines();
        return lines.get(Math.floorMod(tick / 5, lines.size()));
    }

    public static void play(ServerPlayer player, int tick) {
        List<String> sounds = clean(Config.FLOOD_SOUNDS.get(), 12, 80);
        if (sounds.isEmpty()) return;
        String id = sounds.get(Math.floorMod(tick, sounds.size()));
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return;
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(loc);
        if (event == null) return;
        float volume = Config.FLOOD_VOLUME.get().floatValue();
        if (volume <= 0f) return;
        float pitch = 0.65f + (tick % 7) * 0.12f;
        player.playNotifySound(event, SoundSource.MASTER, volume, pitch);
    }

    public static void spam(ServerPlayer player, int tick, boolean actionBar) {
        Component line = Component.literal(line(tick));
        if (actionBar) player.displayClientMessage(Msg.paint(line), true);
        else player.sendSystemMessage(Msg.paint(line));
    }

    public static List<NamedPng> images() {
        ensureDir();
        List<NamedPng> out = new ArrayList<>();
        try (var stream = Files.list(dir())) {
            List<Path> files = stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".png") && Files.isRegularFile(p);
            }).sorted().limit(MAX_IMAGES).toList();
            for (Path file : files) {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length == 0 || bytes.length > MAX_BYTES) continue;
                out.add(new NamedPng(file.getFileName().toString(), bytes));
            }
        } catch (IOException e) {
            BlackChannelMod.LOGGER.warn("Flood images failed: {}", e.toString());
        }
        return out;
    }

    private static List<String> clean(List<? extends String> src, int max, int len) {
        List<String> out = new ArrayList<>();
        if (src == null) return out;
        for (String s : src) {
            if (s == null || s.isBlank()) continue;
            out.add(s.length() > len ? s.substring(0, len) : s);
            if (out.size() >= max) break;
        }
        return out;
    }

    public record NamedPng(String name, byte[] bytes) {}
}
