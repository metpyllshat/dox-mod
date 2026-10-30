package dev.blackchannel.logic;

import net.minecraft.ChatFormatting;

import java.util.List;

/** Фиксированный список команд. Выбор идёт через меню до старта матча. */
public final class Teams {
    public record Team(String id, String name, ChatFormatting color, int rgb) {}

    public static final List<Team> ALL = List.of(
            new Team("red", "Красные", ChatFormatting.RED, 0xE23D3D),
            new Team("blue", "Голубые", ChatFormatting.AQUA, 0x3DBBE2),
            new Team("green", "Зелёные", ChatFormatting.GREEN, 0x4CBB3D),
            new Team("yellow", "Жёлтые", ChatFormatting.YELLOW, 0xE2D63D),
            new Team("purple", "Розовые", ChatFormatting.LIGHT_PURPLE, 0xD64BE2),
            new Team("orange", "Оранжевые", ChatFormatting.GOLD, 0xE28B2E),
            new Team("white", "Белые", ChatFormatting.WHITE, 0xE8E8E8),
            new Team("gray", "Серые", ChatFormatting.GRAY, 0x8A8A8A)
    );

    private Teams() {}

    public static Team byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Team team : ALL) {
            if (team.id().equals(id)) return team;
        }
        return null;
    }

    public static boolean valid(String id) {
        return byId(id) != null;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    public static String nameOf(String id) {
        Team team = byId(id);
        return team == null ? (id == null ? "" : id) : team.name();
    }

    public static ChatFormatting colorOf(String id) {
        Team team = byId(id);
        return team == null ? ChatFormatting.WHITE : team.color();
    }
}
