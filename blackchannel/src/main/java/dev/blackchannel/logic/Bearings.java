package dev.blackchannel.logic;

import dev.blackchannel.Config;
import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;
import net.minecraft.server.MinecraftServer;

/**
 * Пеленг: TRACE даёт не координату, а направление с угловой погрешностью.
 * Точка появляется, когда два луча с разных мест пересекаются под приличным углом.
 */
public final class Bearings {
    /** Минимальное расстояние между точками приёма, иначе это один и тот же луч. */
    public static final int MIN_BASE = 90;
    /** Минимальный угол между лучами: на почти параллельных лучах пересечение врёт. */
    public static final double MIN_ANGLE = 18.0;

    private Bearings() {}

    /** Угол на цель в градусах, -180..180, ось X — восток, Z — юг. */
    public static double angleTo(double ox, double oz, double tx, double tz) {
        return Math.toDegrees(Math.atan2(tz - oz, tx - ox));
    }

    /** Разница углов по модулю, 0..180. */
    public static double angleBetween(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    /**
     * Множитель погрешности от размера карты: база настроена на traceMapRefBlocks,
     * дальше корень из отношения сторон. На 2500 при базе 1000 это 1.58.
     */
    public static double mapFactor(MinecraftServer server) {
        if (!Config.TRACE_SCALE_MAP.get()) return 1.0;
        int ref = Math.max(200, Config.TRACE_MAP_REF.get());
        int side = ref;
        if (server != null) {
            MatchData match = MatchData.get(server);
            if (match != null && match.size > 1) side = match.size;
        }
        return Math.sqrt(Math.max(2, side) / (double) ref);
    }

    /**
     * Ширина луча в градусах: база из конфига, погрешность VPN и размер карты.
     * Чем больше замеров с одной точки, тем уже луч.
     */
    public static double alpha(NetData.Profile target, int samples, double mapFactor) {
        double base = Config.TRACE_BEAM_DEGREES.get();
        double mul = 1.0;
        if (target != null) {
            if (target.vpn >= 2) mul *= Config.TRACE_VPN_DEEP.get();
            else if (target.vpn == 1) mul *= Config.TRACE_VPN_MUL.get();
            if (target.airplane) mul *= 1.4;
        }
        mul *= mapFactor;
        if (Config.TRACE_AVERAGE.get() && samples > 1) {
            int n = Math.min(4, samples);
            mul *= Math.pow(0.66, n - 1);
        }
        return base * mul;
    }

    /** Расхождение луча и цели по нормали на расстоянии dist. */
    public static double lateral(double dist, double alphaDeg) {
        return Math.tan(Math.toRadians(Math.max(0.0, alphaDeg))) * Math.max(0.0, dist);
    }

    /**
     * Пересечение двух лучей. Возвращает {x, z, err} или null, если лучи почти параллельны
     * или пересечение позади одного из приёмников.
     */
    public static double[] cross(NetData.Beam a, NetData.Beam b) {
        double d1x = Math.cos(Math.toRadians(a.angle));
        double d1z = Math.sin(Math.toRadians(a.angle));
        double d2x = Math.cos(Math.toRadians(b.angle));
        double d2z = Math.sin(Math.toRadians(b.angle));
        double det = d1x * (-d2z) - (-d2x) * d1z;
        if (Math.abs(det) < 1.0E-4) return null;
        double rx = b.ox - a.ox;
        double rz = b.oz - a.oz;
        double t1 = (rx * (-d2z) - (-d2x) * rz) / det;
        double t2 = (d1x * rz - d1z * rx) / det;
        if (t1 <= 0.0 || t2 <= 0.0) return null;
        double x = a.ox + d1x * t1;
        double z = a.oz + d1z * t1;
        double e1 = lateral(t1, a.alpha);
        double e2 = lateral(t2, b.alpha);
        double err = Math.hypot(e1, e2) / 2.0;
        return new double[]{x, z, Math.max(10.0, err)};
    }

    /** Два луча вообще можно пересекать: разные точки приёма и достаточный угол. */
    public static boolean usable(NetData.Beam a, NetData.Beam b) {
        if (a == null || b == null) return false;
        if (!a.handle.equals(b.handle)) return false;
        if (!a.dim.equals(b.dim)) return false;
        if (Math.hypot(a.ox - b.ox, a.oz - b.oz) < MIN_BASE) return false;
        return angleBetween(a.angle, b.angle) >= MIN_ANGLE;
    }

    /**
     * Лучший готовый замер по этому позывному: минимальная погрешность пересечения.
     * Возвращает {x, z, err} или null, если второго луча ещё нет.
     */
    public static double[] bestFix(NetData.Profile profile, NetData.Beam fresh) {
        if (profile == null || fresh == null) return null;
        double[] best = null;
        for (NetData.Beam other : profile.beams) {
            if (other == fresh || !usable(fresh, other)) continue;
            double[] hit = cross(fresh, other);
            if (hit == null) continue;
            if (best == null || hit[2] < best[2]) best = hit;
        }
        return best;
    }
}
