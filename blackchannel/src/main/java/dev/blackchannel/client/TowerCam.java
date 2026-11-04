package dev.blackchannel.client;

import dev.blackchannel.logic.Actions;
import dev.blackchannel.net.ModNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;

/** Client-only render camera. The real player entity never leaves its body. */
public final class TowerCam {
    private static boolean active;
    private static boolean exitSent;
    private static Entity camera;
    private static CameraType previousView = CameraType.FIRST_PERSON;
    private static double x, y, z;
    private static double bodyX, bodyY, bodyZ;
    private static boolean bodyOnGround;
    private static boolean previousNoPhysics;
    private static int ticksLeft;
    private static List<ModNetwork.CamTarget> targets = List.of();

    private TowerCam() {}

    /** Applies the server-authorized view or restores the local player camera. */
    public static void apply(boolean on, int bx, int by, int bz, float yaw, float pitch, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (!on) {
            resetLocal();
            return;
        }
        if (mc.level == null || mc.player == null) return;

        CameraType currentView = active ? previousView : mc.options.getCameraType();
        resetLocal();
        Entity proxy = EntityType.MARKER.create(mc.level);
        if (proxy == null) proxy = EntityType.ARMOR_STAND.create(mc.level);
        x = bx + 0.5;
        y = by + 0.5;
        z = bz + 0.5;
        ticksLeft = Math.max(1, ticks);
        previousView = currentView;
        bodyX = mc.player.getX();
        bodyY = mc.player.getY();
        bodyZ = mc.player.getZ();
        bodyOnGround = mc.player.onGround();
        previousNoPhysics = mc.player.noPhysics;
        targets = List.of();
        active = true;
        exitSent = false;
        if (proxy == null) {
            camera = mc.player;
            requestExit();
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        proxy.moveTo(x, y, z, yaw, pitch);
        proxy.setOldPosAndRot();
        camera = proxy;
        mc.setScreen(null);
        mc.setCameraEntity(proxy);
        mc.mouseHandler.grabMouse();
    }

    public static void tick(TickEvent.ClientTickEvent event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || camera == null || camera.level() != mc.level) {
            resetLocal();
            return;
        }
        freezeBody(mc);
        if (event.phase != TickEvent.Phase.START) return;
        if (exitSent) return;
        if (mc.screen != null || --ticksLeft <= 0) {
            requestExit();
            return;
        }
        if (mc.options.keyAttack.consumeClick() || mc.options.keyUse.consumeClick()) {
            requestExit();
            return;
        }

        // Vanilla mouse input rotates LocalPlayer. Mirror only those angles onto the local proxy.
        camera.moveTo(x, y, z, mc.player.getYRot(), mc.player.getXRot());
        camera.setOldPosAndRot();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
    }

    private static void freezeBody(Minecraft mc) {
        if (mc.player == null) return;
        mc.player.noPhysics = true;
        mc.player.setPos(bodyX, bodyY, bodyZ);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setOnGround(bodyOnGround);
        mc.player.fallDistance = 0f;
    }

    /** Opening any screen, including Escape, exits instead of leaving a stuck remote view. */
    public static void screen(ScreenEvent.Opening event) {
        if (active && event.getNewScreen() != null) requestExit();
    }

    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetLocal();
    }

    public static void applyTargets(List<ModNetwork.CamTarget> updated) {
        if (active) targets = updated == null ? List.of() : List.copyOf(updated);
    }

    /** Movement keys never move the body while the player is watching the feed. */
    public static void input(MovementInputUpdateEvent event) {
        if (!active) return;
        var input = event.getInput();
        input.forwardImpulse = 0f;
        input.leftImpulse = 0f;
        input.up = false;
        input.down = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    public static void hud(GuiGraphics g) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        g.fill(0, 0, w, 18, 0xB0080D0F);
        g.fill(0, h - 18, w, h, 0xB0080D0F);
        Ui.scanlines(g, 0, 18, w, Math.max(0, h - 36), 0x10000000);
        drawTargets(g, mc, w, h);
        String live = Component.translatable("blackchannel.tower.cam_hud", x, z).getString();
        g.drawString(mc.font, live, 8, 6, Ui.GREEN, false);
        String exit = Component.translatable(exitSent ? "blackchannel.tower.cam_closing" : "blackchannel.tower.cam_exit").getString();
        g.drawString(mc.font, exit, w - mc.font.width(exit) - 8, 6, Ui.MUTED, false);
        String time = String.format("%d:%02d", Math.max(0, ticksLeft / 20) / 60, Math.max(0, ticksLeft / 20) % 60);
        g.drawString(mc.font, time, 8, h - 13, Ui.AMBER, false);
    }

    private static void drawTargets(GuiGraphics g, Minecraft mc, int w, int h) {
        if (mc.player == null || targets.isEmpty()) return;
        double horizontalScale = w / (2.0 * Math.tan(Math.toRadians(35.0)));
        double verticalScale = h / (2.0 * Math.tan(Math.toRadians(25.0)));
        for (ModNetwork.CamTarget target : targets) {
            double dx = target.x() - x;
            double dy = target.y() - y;
            double dz = target.z() - z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double distance = Math.sqrt(horizontal * horizontal + dy * dy);
            if (distance < 0.5 || distance > 64.0) continue;
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            float yawDelta = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
            float pitchDelta = targetPitch - mc.player.getXRot();
            if (Math.abs(yawDelta) > 48f || Math.abs(pitchDelta) > 32f) continue;
            int sx = (int) Math.round(w / 2.0 + Math.tan(Math.toRadians(yawDelta)) * horizontalScale);
            int sy = (int) Math.round(h / 2.0 + Math.tan(Math.toRadians(pitchDelta)) * verticalScale);
            if (sx < 8 || sx >= w - 8 || sy < 24 || sy >= h - 24) continue;
            int color = target.kind() == 1 ? Ui.GREEN : target.kind() == 2 ? Ui.AMBER : Ui.RED;
            g.fill(sx - 5, sy - 5, sx - 1, sy - 4, color);
            g.fill(sx + 1, sy - 5, sx + 5, sy - 4, color);
            g.fill(sx - 5, sy + 4, sx - 1, sy + 5, color);
            g.fill(sx + 1, sy + 4, sx + 5, sy + 5, color);
            String label = target.label().isEmpty() ? Math.round(distance) + "m"
                    : target.label() + " · " + Math.round(distance) + "m";
            g.drawString(mc.font, label, sx - mc.font.width(label) / 2, sy + 8, color, false);
        }
    }

    private static void requestExit() {
        if (!active || exitSent) return;
        exitSent = true;
        if (ModNetwork.CHANNEL != null) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPkt(Actions.TOWER_CAM, ""));
        }
    }

    private static void resetLocal() {
        Minecraft mc = Minecraft.getInstance();
        boolean wasActive = active || camera != null;
        active = false;
        exitSent = false;
        camera = null;
        targets = List.of();
        ticksLeft = 0;
        if (wasActive && mc.player != null) {
            mc.player.noPhysics = previousNoPhysics;
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.setCameraEntity(mc.player);
        }
        if (wasActive) mc.options.setCameraType(previousView);
    }
}
