package dev.blackchannel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Главное меню мода: не список кнопок, а экран из другой игры.
 * Тёмный фон, помехи, зелёная развёртка, адрес и статус канала.
 * Кнопки простые: играть (ванильный экран), настройки, выход.
 */
public class MainMenuScreen extends Screen {
    /** true — значит мы сами открываем ванильный экран и хук не должен вмешиваться. */
    public static boolean bypass;

    private long born = System.currentTimeMillis();
    private Button play;

    public MainMenuScreen() {
        super(Component.translatable("screen.blackchannel.menu.title"));
    }

    @Override
    protected void init() {
        bypass = false;
        int cx = this.width / 2;
        int y = this.height / 2 + 30;
        play = Button.builder(Component.translatable("screen.blackchannel.menu.play"), b -> {
            bypass = true;
            Minecraft.getInstance().setScreen(new TitleScreen());
        }).bounds(cx - 90, y, 180, 20).build();
        addRenderableWidget(play);
        addRenderableWidget(Button.builder(Component.translatable("screen.blackchannel.menu.settings"), b -> {
            bypass = true;
            Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options));
        }).bounds(cx - 90, y + 26, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.blackchannel.menu.quit"), b -> Minecraft.getInstance().stop())
                .bounds(cx + 4, y + 26, 86, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int w = this.width;
        int h = this.height;
        long now = System.currentTimeMillis();
        // Фон и развёртка
        g.fill(0, 0, w, h, 0xFF05080A);
        for (int y = 0; y < h; y += 3) {
            g.fill(0, y, w, y + 1, 0x22000000);
        }
        // Помехи: горизонтальные полосы, которые едут
        for (int i = 0; i < 14; i++) {
            int bandY = (int) ((now / 12 + i * 97) % (h + 40)) - 20;
            int shade = (i % 3 == 0) ? 0x3300FF6A : 0x22FFFFFF;
            g.fill(0, bandY, w, bandY + 2, shade);
        }
        // Заголовок
        String title = "ЧЁРНЫЙ КАНАЛ";
        int tw = this.font.width(title) * 2;
        g.pose().pushPose();
        g.pose().scale(2f, 2f, 1f);
        g.drawString(this.font, title, (w / 2 - tw / 2) / 2, h / 2 - 80, 0xFF7FE08A, false);
        g.pose().popPose();
        String sub = "КОРЯЖМА · НОЯБРЬ · КАНАЛ ОТКРЫТ";
        g.drawString(this.font, sub, w / 2 - this.font.width(sub) / 2, h / 2 - 52, 0xFFE0A72E, false);
        String address = "частота 21.47 · дежурный на связи · шум " + (long) (12 + (now / 500) % 7) + "%";
        g.drawString(this.font, address, w / 2 - this.font.width(address) / 2, h / 2 - 40, 0xFF7F9A8C, false);
        // Рамка «экрана»
        int bx = w / 2 - 130;
        int by = h / 2 - 96;
        g.fill(bx, by, bx + 260, by + 1, 0xFF2EE6A6);
        g.fill(bx, by + 190, bx + 260, by + 191, 0xFF1B6B52);
        g.fill(bx, by, bx + 1, by + 191, 0xFF1B6B52);
        g.fill(bx + 259, by, bx + 260, by + 191, 0xFF1B6B52);
        // Бегущая строка помех внизу
        String noise = "░░▒▒▓▓▓▒▒░░ ░░▓▓▒▒░░▓▓  ▓▓▒▒░░▓▓▒▒ ";
        int width = this.font.width(noise);
        int offset = (int) ((now / 40) % width);
        g.drawString(this.font, noise, w / 2 - 120 - offset % 40, h - 34, 0xFF1B6B52, false);
        String ver = "Black Channel · Forge 1.20.1";
        g.drawString(this.font, ver, 6, h - 12, 0xFF3A4A44, false);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
