package fr.heraliz.airstrike.strike;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic flight path shared by the server (release points) and the clients (rendering).
 * <p>
 * The aircraft flies a straight line through the overflight point ({@code ox, oz}) along the horizontal unit
 * direction ({@code dx, dz}), then rolls into a climbing turn. Positions are expressed with the along-track
 * coordinate {@code s} (0 = above the overflight point, negative before it) and the time {@code t} in ticks since
 * the path started: {@code s = speed * t - approach}.
 */
public final class FlightPath {
    public static final int PROFILE_LEVEL = 0;
    public static final int PROFILE_DIVE = 1;
    /** Free track made of straight legs and turns with an altitude profile (gun run with two passes). */
    public static final int PROFILE_TRACK = 2;

    private static final double TURN_ROLL_IN = 45.0;
    private static final double MAX_ROLL = 58.0;
    private static final double CLIMB_SLOPE = 0.32;
    private static final double CLIMB_TRANSITION = 70.0;

    public final double ox, oz, dx, dz;
    public final double speed;
    public final double cruiseY;
    public final double lowY;
    public final double approach;
    public final double levelDist;
    public final double exit;
    public final double entryExtra;
    public final double turnRadius;
    public final int turnSign;
    public final int profile;
    /** Along-track distances used by the dive profile. */
    public final double diveStart, diveEnd;

    /** Total duration of the path in ticks. */
    public final int duration;

    // track mode: segments (0 = straight, +1 right turn, -1 left turn of radius turnRadius) and altitude keys
    private final double[] segLen;
    private final byte[] segTurn;
    private final double[] keyS, keyY;
    private double[] segX, segZ, segH;
    /** Track mode: track distance of the second overflight of the target and heading of that pass. */
    public final double pass2At, dx2, dz2;

    // turn table (one entry per tick)
    private final double[] tx, ty, tz, tyaw, tpitch, troll;

    public FlightPath(double ox, double oz, double dx, double dz, double speed, double cruiseY, double lowY,
                      double approach, double levelDist, double exit, double entryExtra, double turnRadius,
                      int turnSign, int profile, double diveStart, double diveEnd) {
        this(ox, oz, dx, dz, speed, cruiseY, lowY, approach, levelDist, exit, entryExtra, turnRadius, turnSign, profile,
                diveStart, diveEnd, null, null, null, null, -1, 0, 0);
    }

    /**
     * Track path: starts {@code approach} blocks before the target along (dx, dz) then follows the segments.
     * Altitudes are given at track distances {@code keyS} (smooth transitions between keys).
     */
    public static FlightPath track(double ox, double oz, double dx, double dz, double speed, double approach,
                                   double turnRadius, double[] segLen, byte[] segTurn, double[] keyS, double[] keyY,
                                   double pass2At, double dx2, double dz2) {
        double lowY = Double.MAX_VALUE, highY = -Double.MAX_VALUE;
        for (double y : keyY) {
            lowY = Math.min(lowY, y);
            highY = Math.max(highY, y);
        }
        return new FlightPath(ox, oz, dx, dz, speed, highY, lowY, approach, 0, 0, 0, turnRadius, 1, PROFILE_TRACK,
                0, 0, segLen, segTurn, keyS, keyY, pass2At, dx2, dz2);
    }

    private FlightPath(double ox, double oz, double dx, double dz, double speed, double cruiseY, double lowY,
                       double approach, double levelDist, double exit, double entryExtra, double turnRadius,
                       int turnSign, int profile, double diveStart, double diveEnd, double[] segLen, byte[] segTurn,
                       double[] keyS, double[] keyY, double pass2At, double dx2, double dz2) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        this.ox = ox;
        this.oz = oz;
        this.dx = dx / len;
        this.dz = dz / len;
        this.speed = speed;
        this.cruiseY = cruiseY;
        this.lowY = lowY;
        this.approach = approach;
        this.levelDist = levelDist;
        this.exit = exit;
        this.entryExtra = entryExtra;
        this.turnRadius = turnRadius;
        this.turnSign = turnSign >= 0 ? 1 : -1;
        this.profile = profile;
        this.diveStart = diveStart;
        this.diveEnd = diveEnd;
        this.segLen = segLen;
        this.segTurn = segTurn;
        this.keyS = keyS;
        this.keyY = keyY;
        this.pass2At = pass2At;
        this.dx2 = dx2;
        this.dz2 = dz2;
        double total;
        if (profile == PROFILE_TRACK) {
            total = 0;
            for (double l : segLen) total += l;
        } else {
            // fly the approach, the exit leg and ~150 degrees of turn
            total = approach + exit + turnRadius * 2.6 + 60.0;
        }
        this.duration = (int) Math.ceil(total / speed);
        int n = duration + 2;
        tx = new double[n];
        ty = new double[n];
        tz = new double[n];
        tyaw = new double[n];
        tpitch = new double[n];
        troll = new double[n];
        if (profile == PROFILE_TRACK) buildTrack();
        else buildTable();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Altitude profile along the track

    private static double smooth(double x) {
        x = Mth.clamp(x, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double smoother(double x) {
        x = Mth.clamp(x, 0.0, 1.0);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    /** Altitude at along-track position s. */
    public double altitude(double s) {
        double y;
        if (profile == PROFILE_TRACK) {
            double S = s + approach;
            int n = keyS.length;
            if (S <= keyS[0]) return keyY[0];
            if (S >= keyS[n - 1]) return keyY[n - 1];
            int k = 0;
            while (k < n - 2 && S > keyS[k + 1]) k++;
            return keyY[k] + (keyY[k + 1] - keyY[k]) * smoother((S - keyS[k]) / Math.max(1e-6, keyS[k + 1] - keyS[k]));
        } else if (profile == PROFILE_DIVE) {
            y = cruiseY;
            if (s < -diveStart) {
                double u = (-diveStart - s) / Math.max(1.0, approach - diveStart);
                y += entryExtra * smooth(u);
            } else {
                y -= (cruiseY - lowY) * smoother((s + diveStart) / (diveStart - diveEnd));
            }
            double s0 = -diveEnd + 8.0;
            if (s > s0) y += climb(s - s0, CLIMB_SLOPE * 1.4);
        } else {
            y = cruiseY;
            if (s < -levelDist) {
                double u = (-levelDist - s) / Math.max(1.0, approach - levelDist);
                y += entryExtra * smooth(u);
            }
            double s0 = exit * 0.5;
            if (s > s0) y += climb(s - s0, CLIMB_SLOPE);
        }
        return y;
    }

    private static double climb(double d, double slope) {
        if (d <= 0) return 0;
        if (d < CLIMB_TRANSITION) return slope * d * d / (2.0 * CLIMB_TRANSITION);
        return slope * (CLIMB_TRANSITION / 2.0 + (d - CLIMB_TRANSITION));
    }

    public double alongAt(double t) {
        return speed * t - approach;
    }

    public double timeAtAlong(double s) {
        return (s + approach) / speed;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Track mode

    private static double rightX(double h) {
        return -Math.sin(h);
    }

    private static double rightZ(double h) {
        return Math.cos(h);
    }

    /** Exact pose at track distance S: x, z, heading (radians, direction = (cos h, sin h)). */
    private void trackPose(double S, double[] out) {
        int n = segLen.length;
        int i = 0;
        double start = 0;
        while (i < n - 1 && S > start + segLen[i]) {
            start += segLen[i];
            i++;
        }
        trackPoseUpTo(i, S - start, out);
    }

    /** Integral of the signed curvature between two track distances. */
    private double curvatureIntegral(double a, double b) {
        double sum = 0, start = 0;
        for (int i = 0; i < segLen.length; i++) {
            double end = start + segLen[i];
            double lo = Math.max(a, start), hi = Math.min(b, end);
            if (hi > lo && segTurn[i] != 0) sum += segTurn[i] * (hi - lo) / turnRadius;
            start = end;
        }
        return sum;
    }

    private void buildTrack() {
        int n = segLen.length;
        segX = new double[n];
        segZ = new double[n];
        segH = new double[n];
        segX[0] = ox - dx * approach;
        segZ[0] = oz - dz * approach;
        segH[0] = Math.atan2(dz, dx);
        double[] p = new double[3];
        for (int i = 1; i < n; i++) {
            // each segment starts where the previous one ends
            trackPoseUpTo(i - 1, segLen[i - 1], p);
            segX[i] = p[0];
            segZ[i] = p[1];
            segH[i] = p[2];
        }
        // bank like a real aircraft: tan(roll) = v^2 * curvature / g, curvature smoothed so the roll eases in/out
        double v = speed * 20.0;
        double window = 30.0;
        for (int i = 0; i < tx.length; i++) {
            double S = speed * i;
            trackPose(S, p);
            tx[i] = p[0];
            tz[i] = p[1];
            tyaw[i] = yawOf(Math.cos(p[2]), Math.sin(p[2]));
            double k = curvatureIntegral(S - window, S + window) / (2 * window);
            troll[i] = Mth.clamp(Math.toDegrees(Math.atan(v * v * k / 9.81)), -80.0, 80.0);
            double s = alongAt(i);
            ty[i] = altitude(s);
            tpitch[i] = -Math.toDegrees(Math.atan(altitude(s + 0.5) - altitude(s - 0.5)));
        }
        unwrapYaw();
    }

    /** Pose at distance u along segment i (segment start poses up to i must be known). */
    private void trackPoseUpTo(int i, double u, double[] out) {
        double x = segX[i], z = segZ[i], h = segH[i];
        int turn = segTurn[i];
        if (turn == 0) {
            out[0] = x + Math.cos(h) * u;
            out[1] = z + Math.sin(h) * u;
            out[2] = h;
        } else {
            double r = turnRadius;
            double cx = x + turn * r * rightX(h), cz = z + turn * r * rightZ(h);
            double h2 = h + turn * u / r;
            out[0] = cx - turn * r * rightX(h2);
            out[1] = cz - turn * r * rightZ(h2);
            out[2] = h2;
        }
    }

    private void buildTable() {
        double hx = 0, hz = 0, heading = 0;
        boolean turning = false;
        double sTurn = 0;
        for (int i = 0; i < tx.length; i++) {
            double s = alongAt(i);
            if (s <= exit) {
                tx[i] = ox + dx * s;
                tz[i] = oz + dz * s;
                tyaw[i] = yawOf(dx, dz);
                troll[i] = 0;
                hx = tx[i];
                hz = tz[i];
            } else {
                if (!turning) {
                    turning = true;
                    // integrate from the exact end of the straight leg
                    hx = ox + dx * exit;
                    hz = oz + dz * exit;
                    heading = 0;
                    sTurn = exit;
                }
                // integrate in small steps up to s
                while (sTurn < s - 1.0E-9) {
                    double step = Math.min(0.5, s - sTurn);
                    double sp = sTurn + step * 0.5 - exit;
                    double k = smooth(sp / TURN_ROLL_IN) / turnRadius;
                    double hMid = heading + k * step * 0.5;
                    double cx = Math.cos(hMid), sx = Math.sin(hMid);
                    double ddx = dx * cx - dz * sx * turnSign;
                    double ddz = dx * sx * turnSign + dz * cx;
                    hx += ddx * step;
                    hz += ddz * step;
                    heading += k * step;
                    sTurn += step;
                }
                double c = Math.cos(heading), sn = Math.sin(heading);
                double ddx = dx * c - dz * sn * turnSign;
                double ddz = dx * sn * turnSign + dz * c;
                tx[i] = hx;
                tz[i] = hz;
                tyaw[i] = yawOf(ddx, ddz);
                double k = smooth((s - exit) / TURN_ROLL_IN);
                troll[i] = MAX_ROLL * k * turnSign;
            }
            ty[i] = altitude(s);
            double slope = (altitude(s + 0.5) - altitude(s - 0.5));
            tpitch[i] = -Math.toDegrees(Math.atan(slope));
        }
        unwrapYaw();
    }

    /** Unwraps the yaw table so interpolation never spins the long way round. */
    private void unwrapYaw() {
        for (int i = 1; i < tyaw.length; i++) {
            double d = tyaw[i] - tyaw[i - 1];
            while (d > 180) {
                tyaw[i] -= 360;
                d -= 360;
            }
            while (d < -180) {
                tyaw[i] += 360;
                d += 360;
            }
        }
    }

    /** Minecraft yaw (degrees) of a horizontal direction. */
    public static double yawOf(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public Vec3 position(double t) {
        double s = alongAt(t);
        if (profile == PROFILE_TRACK) {
            if (t <= 0) return new Vec3(segX[0] + dx * speed * t, altitude(s), segZ[0] + dz * speed * t);
            int last = tx.length - 1;
            if (t >= last) {
                double h = Math.toRadians(tyaw[last]);
                double over = (t - last) * speed;
                return new Vec3(tx[last] - Math.sin(h) * over, altitude(s), tz[last] + Math.cos(h) * over);
            }
            int i = (int) Math.floor(t);
            double f = t - i;
            return new Vec3(Mth.lerp(f, tx[i], tx[i + 1]), altitude(s), Mth.lerp(f, tz[i], tz[i + 1]));
        }
        if (s <= exit || t <= 0) {
            return new Vec3(ox + dx * s, altitude(s), oz + dz * s);
        }
        int i = Mth.clamp((int) Math.floor(t), 0, tx.length - 2);
        double f = Mth.clamp(t - i, 0.0, 1.0);
        return new Vec3(Mth.lerp(f, tx[i], tx[i + 1]), altitude(s), Mth.lerp(f, tz[i], tz[i + 1]));
    }

    /** yaw, pitch (nose down positive), roll (right wing down positive), all in degrees. */
    public void orientation(double t, double[] out) {
        double tt = Mth.clamp(t, 0.0, tx.length - 1.001);
        int i = (int) Math.floor(tt);
        double f = tt - i;
        out[0] = Mth.lerp(f, tyaw[i], tyaw[i + 1]);
        double s = alongAt(t);
        double slope = (altitude(s + 0.5) - altitude(s - 0.5));
        out[1] = -Math.toDegrees(Math.atan(slope));
        out[2] = Mth.lerp(f, troll[i], troll[i + 1]);
    }

    /** Velocity (blocks/tick) while on the straight leg. */
    public Vec3 straightVelocity(double t) {
        double s = alongAt(t);
        double vy = (altitude(s + 0.25) - altitude(s - 0.25)) * 2.0 * speed;
        return new Vec3(dx * speed, vy, dz * speed);
    }

    /**
     * Converts an offset expressed in the aircraft frame (x right, y up, z back, blocks) into a world offset using
     * the full attitude at time t. Mirrors the transform used by the renderer:
     * {@code Ry(180 - yaw) * Rx(-pitch) * Rz(-roll)}.
     */
    public Vec3 frameOffset(double t, double right, double up, double back) {
        double[] o = new double[3];
        orientation(t, o);
        return rotate(right, up, back, o[0], o[1], o[2]);
    }

    public static Vec3 rotate(double x, double y, double z, double yaw, double pitch, double roll) {
        double a = Math.toRadians(-roll);
        double x1 = x * Math.cos(a) - y * Math.sin(a);
        double y1 = x * Math.sin(a) + y * Math.cos(a);
        double z1 = z;
        double b = Math.toRadians(-pitch);
        double y2 = y1 * Math.cos(b) - z1 * Math.sin(b);
        double z2 = y1 * Math.sin(b) + z1 * Math.cos(b);
        double c = Math.toRadians(180.0 - yaw);
        double x3 = x1 * Math.cos(c) + z2 * Math.sin(c);
        double z3 = -x1 * Math.sin(c) + z2 * Math.cos(c);
        return new Vec3(x3, y2, z3);
    }

    /** Converts an offset in the aircraft frame (x right, y up, z back, in blocks) at level flight. */
    public Vec3 levelOffset(double right, double up, double back) {
        double rx = -dz, rz = dx;
        return new Vec3(rx * right - dx * back, up, rz * right - dz * back);
    }

    // ------------------------------------------------------------------------------------------------------------

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(ox);
        buf.writeDouble(oz);
        buf.writeDouble(dx);
        buf.writeDouble(dz);
        buf.writeDouble(speed);
        buf.writeDouble(cruiseY);
        buf.writeDouble(lowY);
        buf.writeDouble(approach);
        buf.writeDouble(levelDist);
        buf.writeDouble(exit);
        buf.writeDouble(entryExtra);
        buf.writeDouble(turnRadius);
        buf.writeByte(turnSign);
        buf.writeByte(profile);
        buf.writeDouble(diveStart);
        buf.writeDouble(diveEnd);
        if (profile == PROFILE_TRACK) {
            buf.writeVarInt(segLen.length);
            for (int i = 0; i < segLen.length; i++) {
                buf.writeDouble(segLen[i]);
                buf.writeByte(segTurn[i]);
            }
            buf.writeVarInt(keyS.length);
            for (int i = 0; i < keyS.length; i++) {
                buf.writeDouble(keyS[i]);
                buf.writeDouble(keyY[i]);
            }
            buf.writeDouble(pass2At);
            buf.writeDouble(dx2);
            buf.writeDouble(dz2);
        }
    }

    public static FlightPath read(FriendlyByteBuf buf) {
        double ox = buf.readDouble(), oz = buf.readDouble(), dx = buf.readDouble(), dz = buf.readDouble();
        double speed = buf.readDouble(), cruiseY = buf.readDouble(), lowY = buf.readDouble();
        double approach = buf.readDouble(), levelDist = buf.readDouble(), exit = buf.readDouble();
        double entry = buf.readDouble(), radius = buf.readDouble();
        int sign = buf.readByte(), profile = buf.readByte();
        double diveStart = buf.readDouble(), diveEnd = buf.readDouble();
        if (profile == PROFILE_TRACK) {
            int n = buf.readVarInt();
            double[] len = new double[n];
            byte[] turn = new byte[n];
            for (int i = 0; i < n; i++) {
                len[i] = buf.readDouble();
                turn[i] = buf.readByte();
            }
            int m = buf.readVarInt();
            double[] ks = new double[m], ky = new double[m];
            for (int i = 0; i < m; i++) {
                ks[i] = buf.readDouble();
                ky[i] = buf.readDouble();
            }
            double p2 = buf.readDouble(), dx2 = buf.readDouble(), dz2 = buf.readDouble();
            return track(ox, oz, dx, dz, speed, approach, radius, len, turn, ks, ky, p2, dx2, dz2);
        }
        return new FlightPath(ox, oz, dx, dz, speed, cruiseY, lowY, approach, levelDist, exit, entry, radius, sign,
                profile, diveStart, diveEnd);
    }
}
