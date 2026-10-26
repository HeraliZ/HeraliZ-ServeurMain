package fr.heraliz.airstrike.strike;

import fr.heraliz.airstrike.entity.SupplyCrateEntity;
import fr.heraliz.airstrike.AirstrikeConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link Strike}: chooses the approach, the altitude and computes every release so that the objects land
 * exactly where they should.
 */
public final class StrikePlanner {
    /** Gravity and horizontal drag of the free-fall stores (blocks/tick^2, 1/tick). */
    public static final double GRAVITY = 0.05;
    public static final double DRAG_BOMB = 0.012;
    public static final double DRAG_TANK = 0.022;
    public static final double DRAG_BOMBLET = 0.06;
    public static final double BULLET_SPEED = 40.0;

    /** Supply crate kinematics - declared in {@code SupplyCrateEntity} (common: the crate falls on both sides). */
    public static final int CHUTE_DELAY = SupplyCrateEntity.CHUTE_DELAY;
    public static final double CRATE_GRAVITY = SupplyCrateEntity.CRATE_GRAVITY;
    public static final double CRATE_AIR_DRAG = SupplyCrateEntity.CRATE_AIR_DRAG;
    public static final double CHUTE_DRAG_H = SupplyCrateEntity.CHUTE_DRAG_H;
    public static final double CHUTE_SINK = SupplyCrateEntity.CHUTE_SINK;

    private StrikePlanner() {
    }

    public static double groundY(ServerLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return fallback;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    public static Strike plan(ServerLevel level, int id, StrikeType type, Vec3 target, Vec3 from, int callerId, long seed) {
        RandomSource rnd = RandomSource.create(seed);
        double hx = target.x - from.x, hz = target.z - from.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 2.0) {
            double a = rnd.nextDouble() * Math.PI * 2;
            hx = Math.cos(a);
            hz = Math.sin(a);
        } else {
            hx /= len;
            hz /= len;
        }
        // a little variety in the approach
        double jitter = Math.toRadians((rnd.nextDouble() - 0.5) * 16.0);
        if (type == StrikeType.SMOKE) jitter += (rnd.nextBoolean() ? 1 : -1) * Math.PI / 2;
        double c = Math.cos(jitter), s = Math.sin(jitter);
        double dx = hx * c - hz * s, dz = hx * s + hz * c;

        boolean jet = type.plane == PlaneType.ATTACK_JET;
        double speed = jet ? AirstrikeConfig.JET_SPEED.get() : AirstrikeConfig.CARGO_SPEED.get();
        double alt = jet ? AirstrikeConfig.JET_ALTITUDE.get() : AirstrikeConfig.CARGO_ALTITUDE.get();
        double approach = jet ? 390 : 330;
        double exit = jet ? 90 : 150;
        double levelDist = jet ? 240 : 260;
        double entry = jet ? 20 : 8;
        double radius = jet ? 115 : 200;
        double ground = groundY(level, target.x, target.z, target.y);
        double base = Math.max(target.y, ground);

        // terrain clearance along the whole path
        double maxGround = base;
        for (double a = -approach; a <= exit + radius; a += 8) {
            maxGround = Math.max(maxGround, groundY(level, target.x + dx * a, target.z + dz * a, base));
        }
        double cruiseY = Math.max(base + alt, maxGround + 26);
        cruiseY = Math.min(cruiseY, level.getMaxBuildHeight() + 64);
        int turnSign = rnd.nextBoolean() ? 1 : -1;
        int profile = type == StrikeType.GUN_RUN ? FlightPath.PROFILE_DIVE : FlightPath.PROFILE_LEVEL;
        double lowY = Math.max(maxGround + 12, base + 20);
        // gun run: one dive and strafe then climb away, or two passes crossing each other (config)
        boolean twoPasses = type == StrikeType.GUN_RUN && AirstrikeConfig.GUN_PASSES.get() >= 2;
        FlightPath path = twoPasses
                ? gunRunPath(level, target, dx, dz, speed, alt, base, turnSign)
                : new FlightPath(target.x, target.z, dx, dz, speed, cruiseY, lowY, approach, levelDist, exit,
                entry, radius, turnSign, profile, 215, 40);

        List<Drop> drops = new ArrayList<>();
        switch (type) {
            case BOMBING -> {
                int n = AirstrikeConfig.BOMB_COUNT.get();
                int[] order = {3, 7, 2, 6, 1, 5, 0, 4};
                double spacing = 4.6;
                double[] along = new double[n];
                int[] hps = new int[n];
                for (int i = 0; i < n; i++) {
                    along[i] = n == 1 ? 0 : -spacing * (n - 1) / 2.0 + spacing * i;
                    hps[i] = order[i];
                }
                ballisticRun(level, path, rnd, OrdnanceKind.BOMB, hps, along, DRAG_BOMB, 1.0, base, drops);
            }
            case NAPALM -> ballisticRun(level, path, rnd, OrdnanceKind.NAPALM, new int[]{1, 5, 0, 4},
                    new double[]{-13, -4, 5, 14}, DRAG_TANK, 1.5, base, drops);
            case SMOKE -> ballisticRun(level, path, rnd, OrdnanceKind.SMOKE_CAN, new int[]{2, 6, 1, 5, 0, 4},
                    new double[]{-20, -12, -4, 4, 12, 20}, DRAG_TANK, 1.0, base, drops);
            case GAS -> ballisticRun(level, path, rnd, OrdnanceKind.GAS_CAN, new int[]{1, 5, 2, 6},
                    new double[]{-9, -3, 3, 9}, DRAG_TANK, 3.0, base, drops);
            case PRECISION -> planPrecision(level, path, target, ground, drops);
            case CLUSTER -> planCluster(level, path, rnd, target, base, drops);
            case GUN_RUN -> planGunRun(level, path, rnd, target, base, drops);
            case SUPPLY -> planSupply(path, base, drops);
        }

        double tOver = path.timeAtAlong(0);
        long delay = AirstrikeConfig.STRIKE_DELAY_SECONDS.get() * 20L;
        long start = level.getGameTime() + delay - Math.round(tOver);
        return new Strike(id, type, start, path, drops, target, callerId, tOver, seed);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static Vec3 hardpoint(int hp) {
        double[] o = AircraftData.JET_HARDPOINTS[hp];
        return new Vec3(o[0], o[1], o[2]);
    }

    /** Free-fall stores released on the straight leg so that they land at the given along-track positions. */
    private static void ballisticRun(ServerLevel level, FlightPath path, RandomSource rnd, OrdnanceKind kind,
                                     int[] hps, double[] along, double drag, double lateralJitter, double fallback,
                                     List<Drop> out) {
        double rx = -path.dz, rz = path.dx;
        for (int i = 0; i < hps.length; i++) {
            Vec3 off = hardpoint(hps[i]);
            double lateral = (rnd.nextDouble() - 0.5) * 2.0 * lateralJitter;
            double sImp = along[i];
            double ix = path.ox + path.dx * sImp + rx * (off.x + lateral);
            double iz = path.oz + path.dz * sImp + rz * (off.x + lateral);
            double yGround = groundY(level, ix, iz, fallback);
            double tau = 1, tRel = 0;
            Vec3 start = Vec3.ZERO;
            for (int iter = 0; iter < 5; iter++) {
                double yRel = path.cruiseY + off.y;
                double h = Math.max(1.0, yRel - yGround);
                tau = Math.sqrt(2.0 * h / GRAVITY);
                double d = path.speed * Drop.horizontalFactor(drag, tau);
                double sCenter = sImp - d + off.z;
                tRel = path.timeAtAlong(sCenter);
                start = path.position(tRel).add(path.levelOffset(off.x + lateral, off.y, off.z));
                ix = start.x + path.dx * d;
                iz = start.z + path.dz * d;
                double ny = groundY(level, ix, iz, fallback);
                if (Math.abs(ny - yGround) < 0.25) break;
                yGround = ny;
            }
            Vec3 vel = path.straightVelocity(tRel);
            double endY = start.y + vel.y * tau - 0.5 * GRAVITY * tau * tau;
            out.add(Drop.ballistic(kind, hps[i], tRel, start, vel, GRAVITY, drag, tau, new Vec3(ix, endY, iz)));
        }
    }

    private static void planPrecision(ServerLevel level, FlightPath path, Vec3 target, double ground, List<Drop> out) {
        Vec3 aim = ground <= target.y + 1.5 ? target : new Vec3(target.x, ground, target.z);
        int hp = 1;
        Vec3 off = hardpoint(hp);
        double tRel = path.timeAtAlong(-150);
        Vec3 start = path.position(tRel).add(path.levelOffset(off.x, off.y, off.z));
        Vec3 v0 = path.straightVelocity(tRel);
        Vec3 endVel = new Vec3(path.dx * 0.8, -2.6, path.dz * 0.8);
        out.add(Drop.guided(OrdnanceKind.GBU, hp, tRel, tRel + 70, start, v0, aim, endVel));
    }

    private static void planCluster(ServerLevel level, FlightPath path, RandomSource rnd, Vec3 target, double base,
                                    List<Drop> out) {
        int hp = 1;
        Vec3 off = hardpoint(hp);
        double splitY = base + 26;
        double yRel = path.cruiseY + off.y;
        double tauS = Math.sqrt(2.0 * Math.max(1.0, yRel - splitY) / GRAVITY);
        double d = path.speed * Drop.horizontalFactor(DRAG_BOMB, tauS);
        double tRel = path.timeAtAlong(-d + off.z);
        Vec3 start = path.position(tRel).add(path.levelOffset(off.x, off.y, off.z));
        Vec3 vel = path.straightVelocity(tRel);
        Vec3 split = new Vec3(start.x + path.dx * d, start.y + vel.y * tauS - 0.5 * GRAVITY * tauS * tauS,
                start.z + path.dz * d);
        out.add(Drop.ballistic(OrdnanceKind.CLUSTER, hp, tRel, start, vel, GRAVITY, DRAG_BOMB, tauS, split));

        double tSplit = tRel + tauS;
        int n = fr.heraliz.airstrike.AirstrikeConfig.BOMBLET_COUNT.get();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 13.0 * Math.sqrt(rnd.nextDouble());
            double ix = target.x + Math.cos(a) * r + path.dx * 2.0;
            double iz = target.z + Math.sin(a) * r + path.dz * 2.0;
            double yG = groundY(level, ix, iz, base);
            double vy = rnd.nextDouble() * 0.3;
            double h = Math.max(0.5, split.y - yG);
            double tau = (vy + Math.sqrt(vy * vy + 2.0 * GRAVITY * h)) / GRAVITY;
            double f = Drop.horizontalFactor(DRAG_BOMBLET, tau);
            Vec3 v = new Vec3((ix - split.x) / f, vy, (iz - split.z) / f);
            out.add(Drop.ballistic(OrdnanceKind.BOMBLET, -1, tSplit, split, v, GRAVITY, DRAG_BOMBLET, tau,
                    new Vec3(ix, yG, iz)));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gun run: dive and strafe, climbing turn, second strafing pass crossing the first one, climb away

    private static final double GUN_APPROACH = 390, GUN_EXIT = 45, GUN_APPROACH2 = 230, GUN_EXIT2 = 480;
    private static final double GUN_TURN_RADIUS = 80, GUN_CROSS_ANGLE = 75;
    private static final double GUN_DIVE_START = 215, GUN_DIVE_END = 40;

    private static double mod2pi(double a) {
        a %= Math.PI * 2;
        return a < 0 ? a + Math.PI * 2 : a;
    }

    /**
     * Shortest turn-straight-turn path (Dubins) between two poses with the given turn radius. Headings in radians,
     * direction = (cos h, sin h), a positive turn increases the heading. Returns {turn1, angle1, straight, turn2, angle2}.
     */
    static double[] dubins(double x1, double z1, double h1, double x2, double z2, double h2, double r) {
        double[] best = null;
        double bestLen = Double.MAX_VALUE;
        for (int s1 = -1; s1 <= 1; s1 += 2) {
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                double c1x = x1 - s1 * r * Math.sin(h1), c1z = z1 + s1 * r * Math.cos(h1);
                double c2x = x2 - s2 * r * Math.sin(h2), c2z = z2 + s2 * r * Math.cos(h2);
                double vx = c2x - c1x, vz = c2z - c1z;
                double d = Math.sqrt(vx * vx + vz * vz);
                double psi = Math.atan2(vz, vx);
                double straight, phi;
                if (s1 == s2) {
                    straight = d;
                    phi = psi;
                } else {
                    if (d < 2 * r) continue;
                    straight = Math.sqrt(d * d - 4 * r * r);
                    phi = psi + Math.atan2(2 * s1 * r, straight);
                }
                double a1 = mod2pi(s1 * (phi - h1)), a2 = mod2pi(s2 * (h2 - phi));
                double len = r * (a1 + a2) + straight;
                if (len < bestLen) {
                    bestLen = len;
                    best = new double[]{s1, a1, straight, s2, a2};
                }
            }
        }
        return best;
    }

    private static FlightPath gunRunPath(ServerLevel level, Vec3 target, double dx, double dz, double speed, double alt,
                                         double base, int side) {
        double r = GUN_TURN_RADIUS;
        double h1 = Math.atan2(dz, dx);
        double h2 = h1 + side * Math.toRadians(180.0 - GUN_CROSS_ANGLE);
        double dx2 = Math.cos(h2), dz2 = Math.sin(h2);
        double ex = target.x + dx * GUN_EXIT, ez = target.z + dz * GUN_EXIT;
        double gx = target.x - dx2 * GUN_APPROACH2, gz = target.z - dz2 * GUN_APPROACH2;
        double[] turn = dubins(ex, ez, h1, gx, gz, h2, r);

        List<Double> lens = new ArrayList<>();
        List<Byte> turns = new ArrayList<>();
        lens.add(GUN_APPROACH + GUN_EXIT);
        turns.add((byte) 0);
        if (turn[1] > 1e-4) {
            lens.add(r * turn[1]);
            turns.add((byte) turn[0]);
        }
        if (turn[2] > 1e-3) {
            lens.add(turn[2]);
            turns.add((byte) 0);
        }
        if (turn[4] > 1e-4) {
            lens.add(r * turn[4]);
            turns.add((byte) turn[3]);
        }
        lens.add(GUN_APPROACH2 + GUN_EXIT2);
        turns.add((byte) 0);
        double[] segLen = new double[lens.size()];
        byte[] segTurn = new byte[lens.size()];
        for (int i = 0; i < segLen.length; i++) {
            segLen[i] = lens.get(i);
            segTurn[i] = turns.get(i);
        }
        double turnLen = r * (turn[1] + turn[4]) + turn[2];
        double pass2At = GUN_APPROACH + GUN_EXIT + turnLen + GUN_APPROACH2;

        // terrain clearance along the whole track (flat provisional altitude)
        FlightPath flat = FlightPath.track(target.x, target.z, dx, dz, speed, GUN_APPROACH, r, segLen, segTurn,
                new double[]{0}, new double[]{base}, pass2At, dx2, dz2);
        double maxGround = base;
        for (int t = 0; t <= flat.duration; t += Math.max(1, (int) (8 / speed))) {
            Vec3 p = flat.position(t);
            maxGround = Math.max(maxGround, groundY(level, p.x, p.z, base));
        }
        double top = level.getMaxBuildHeight() + 64;
        double cruiseY = Math.min(Math.max(base + alt, maxGround + 26), top);
        double turnY = Math.min(Math.max(cruiseY - 4, maxGround + 22), top);
        double lowY = Math.min(Math.max(maxGround + 12, base + 20), cruiseY);

        double k2 = GUN_APPROACH - GUN_DIVE_END;
        double k4 = pass2At - GUN_DIVE_START;
        double k3 = Math.min(GUN_APPROACH + GUN_EXIT + turnLen * 0.4, (k2 + k4) * 0.5);
        double[] keyS = {0, GUN_APPROACH - GUN_DIVE_START, k2, k3, k4, pass2At - GUN_DIVE_END, pass2At + 60, pass2At + 420};
        double[] keyY = {cruiseY + 20, cruiseY, lowY, turnY, turnY, lowY, lowY + 5, lowY + 110};
        return FlightPath.track(target.x, target.z, dx, dz, speed, GUN_APPROACH, r, segLen, segTurn, keyS, keyY,
                pass2At, dx2, dz2);
    }

    private static void planGunRun(ServerLevel level, FlightPath path, RandomSource rnd, Vec3 target, double base,
                                   List<Drop> out) {
        strafe(level, path, rnd, target, base, path.approach, path.dx, path.dz, out);
        if (path.profile == FlightPath.PROFILE_TRACK) strafe(level, path, rnd, target, base, path.pass2At, path.dx2, path.dz2, out);
    }

    /** One strafing pass flying over the target at track distance {@code over} along (dx, dz). */
    private static void strafe(ServerLevel level, FlightPath path, RandomSource rnd, Vec3 target, double base,
                               double over, double dx, double dz, List<Drop> out) {
        int rounds = AirstrikeConfig.GUN_ROUNDS.get();
        double rx = -dz, rz = dx;
        double[] gun = AircraftData.JET_GUN;
        for (int i = 0; i < rounds; i++) {
            double f = i / (double) (rounds - 1);
            double tFire = (over - 190 + 95 * f) / path.speed;
            Vec3 muzzle = path.position(tFire).add(path.frameOffset(tFire, gun[0], gun[1], gun[2]));
            double sImp = -30 + 60 * f;
            double lateral = (rnd.nextDouble() - 0.5) * 3.8;
            double ix = target.x + dx * sImp + rx * lateral;
            double iz = target.z + dz * sImp + rz * lateral;
            Vec3 impact = new Vec3(ix, groundY(level, ix, iz, base), iz);
            double travel = impact.distanceTo(muzzle) / BULLET_SPEED;
            out.add(Drop.linear(OrdnanceKind.BULLET, tFire, tFire + travel, muzzle, impact));
        }
    }

    /** Horizontal distance travelled by the supply crate before touching the ground and its fall duration. */
    public static double[] simulateCrate(double speed, double height) {
        double vh = speed, vy = 0, y = height, x = 0;
        int t = 0;
        while (y > 0 && t < 4000) {
            if (t < CHUTE_DELAY) {
                vy -= CRATE_GRAVITY;
                vy *= 0.98;
                vh *= CRATE_AIR_DRAG;
            } else {
                vh *= CHUTE_DRAG_H;
                vy += (CHUTE_SINK - vy) * 0.15;
            }
            x += vh;
            y += vy;
            t++;
        }
        return new double[]{x, t};
    }

    private static void planSupply(FlightPath path, double base, List<Drop> out) {
        double[] ramp = AircraftData.CARGO_RAMP_EXIT;
        double h = path.cruiseY + ramp[1] - base;
        double[] sim = simulateCrate(path.speed, h);
        double tRel = path.timeAtAlong(-sim[0] + ramp[2]);
        Vec3 start = path.position(tRel).add(path.levelOffset(ramp[0], ramp[1], ramp[2]));
        Vec3 land = new Vec3(start.x + path.dx * sim[0], base, start.z + path.dz * sim[0]);
        out.add(Drop.linear(OrdnanceKind.CRATE, tRel, tRel + 1, start, land));
    }
}
