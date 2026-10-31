package dev.blackchannel.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Единая точка для сообщений мода: всё, что мод пишет игроку, идёт жёлтым.
 * Цвет команды и другие стили внутри текста остаются — перекрашивается только сам текст.
 */
public final class Msg {
    private Msg() {}

    /** Жёлтый стиль поверх любого сообщения мода. */
    public static MutableComponent paint(Component message) {
        return message == null ? Component.empty() : message.copy().withStyle(ChatFormatting.YELLOW);
    }
}
