package fr.heraliz.airstrike.strike;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * One object released during a strike (bomb, canister, bomblet, cannon round...). Its trajectory is fully
 * determined by the fields below so that the server (impact time/place) and every client (rendering) agree.
 * Times are expressed in ticks since the start of the strike's flight path.
 */
public final class Drop {
    public static final byte MOTION_BALLISTIC = 0;
    public static final byte MOTION_GUIDED = 1;
    public static final byte MOTION_LINEAR = 2;

    public final OrdnanceKind kind;
    /** Hardpoint the store hangs from before release (0..7), or -1. */
    public final int hardpoint;
    public final byte motion;
    public final double t0, t1;
    public final double x0, y0, z0;
    public final double vx, vy, vz;
    public final double gravity, drag;
    public final double x1, y1, z1;
    /** Guided end tangent (blocks/tick). */
    public final double ex, ey, ez;

    public Drop(OrdnanceKind kind, int hardpoint, byte motion, double t0, double t1, double x0, double y0, double z0,
                double vx, double vy, double vz, double gravity, double drag, double x1, double y1, double z1,
                double ex, double ey, double ez) {
        this.kind = kind;
        this.hardpoint = hardpoint;
        this.motion = motion;
        this.t0 = t0;
        this.t1 = t1;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.gravity = gravity;
        this.drag = drag;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
    }

    public static Drop ballistic(OrdnanceKind kind, int hp, double t0, Vec3 start, Vec3 vel, double g, double k,
                                 double fallTicks, Vec3 impact) {
        return new Drop(kind, hp, MOTION_BALLISTIC, t0, t0 + fallTicks, start.x, start.y, start.z, vel.x, vel.y, vel.z,
                g, k, impact.x, impact.y, impact.z, 0, 0, 0);
    }

    public static Drop guided(OrdnanceKind kind, int hp, double t0, double t1, Vec3 start, Vec3 vel, Vec3 end,
                              Vec3 endVel) {
        return new Drop(kind, hp, MOTION_GUIDED, t0, t1, start.x, start.y, start.z, vel.x, vel.y, vel.z, 0, 0,
                end.x, end.y, end.z, endVel.x, endVel.y, endVel.z);
    }

    public static Drop linear(OrdnanceKind kind, double t0, double t1, Vec3 start, Vec3 end) {
        return new Drop(kind, -1, MOTION_LINEAR, t0, t1, start.x, start.y, start.z, 0, 0, 0, 0, 0,
                end.x, end.y, end.z, 0, 0, 0);
    }

    /** Horizontal travel factor of the ballistic model: (1 - e^(-k tau)) / k. */
    public static double horizontalFactor(double k, double tau) {
        return k < 1.0E-9 ? tau : (1.0 - Math.exp(-k * tau)) / k;
    }

    public Vec3 impact() {
        return new Vec3(x1, y1, z1);
    }

    /** Position at strike time t (clamped to the drop's lifetime). */
    public Vec3 position(double t) {
        double tau = Math.max(0.0, Math.min(t, t1) - t0);
        switch (motion) {
            case MOTION_GUIDED -> {
                double T = Math.max(1.0E-3, t1 - t0);
                double s = tau / T;
                double s2 = s * s, s3 = s2 * s;
                double h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
                return new Vec3(
                        h00 * x0 + h10 * T * vx + h01 * x1 + h11 * T * ex,
                        h00 * y0 + h10 * T * vy + h01 * y1 + h11 * T * ey,
                        h00 * z0 + h10 * T * vz + h01 * z1 + h11 * T * ez);
            }
            case MOTION_LINEAR -> {
                double T = Math.max(1.0E-3, t1 - t0);
                double s = tau / T;
                return new Vec3(x0 + (x1 - x0) * s, y0 + (y1 - y0) * s, z0 + (z1 - z0) * s);
            }
            default -> {
                double h = horizontalFactor(drag, tau);
                return new Vec3(x0 + vx * h, y0 + vy * tau - 0.5 * gravity * tau * tau, z0 + vz * h);
            }
        }
    }

    public Vec3 velocity(double t) {
        double e = 0.05;
        Vec3 a = position(Math.max(t0, t - e));
        Vec3 b = position(Math.min(t1, Math.max(t0 + e, t + e)));
        Vec3 d = b.subtract(a);
        return d.lengthSqr() < 1.0E-12 ? new Vec3(vx, vy - 0.01, vz) : d;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeByte(hardpoint);
        buf.writeByte(motion);
        buf.writeDouble(t0);
        buf.writeDouble(t1);
        buf.writeDouble(x0);
        buf.writeDouble(y0);
        buf.writeDouble(z0);
        buf.writeFloat((float) vx);
        buf.writeFloat((float) vy);
        buf.writeFloat((float) vz);
        buf.writeFloat((float) gravity);
        buf.writeFloat((float) drag);
        buf.writeDouble(x1);
        buf.writeDouble(y1);
        buf.writeDouble(z1);
        buf.writeFloat((float) ex);
        buf.writeFloat((float) ey);
        buf.writeFloat((float) ez);
    }

    public static Drop read(FriendlyByteBuf buf) {
        OrdnanceKind kind = OrdnanceKind.byId(buf.readByte());
        int hp = buf.readByte();
        byte motion = buf.readByte();
        double t0 = buf.readDouble(), t1 = buf.readDouble();
        double x0 = buf.readDouble(), y0 = buf.readDouble(), z0 = buf.readDouble();
        double vx = buf.readFloat(), vy = buf.readFloat(), vz = buf.readFloat();
        double g = buf.readFloat(), k = buf.readFloat();
        double x1 = buf.readDouble(), y1 = buf.readDouble(), z1 = buf.readDouble();
        double ex = buf.readFloat(), ey = buf.readFloat(), ez = buf.readFloat();
        return new Drop(kind, hp, motion, t0, t1, x0, y0, z0, vx, vy, vz, g, k, x1, y1, z1, ex, ey, ez);
    }
}
