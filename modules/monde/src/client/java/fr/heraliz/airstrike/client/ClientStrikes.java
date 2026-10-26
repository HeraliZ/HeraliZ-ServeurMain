package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.client.sound.FollowSound;
import fr.heraliz.airstrike.client.sound.PlaneSound;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModSounds;
import fr.heraliz.airstrike.strike.AircraftData;
import fr.heraliz.airstrike.strike.Drop;
import fr.heraliz.airstrike.strike.FlightPath;
import fr.heraliz.airstrike.strike.OrdnanceKind;
import fr.heraliz.airstrike.strike.PlaneType;
import fr.heraliz.airstrike.strike.Strike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Strikes known by the client: drives their sounds and particle trails. Rendering is in StrikeRenderer. */
public final class ClientStrikes {
    private static final List<ClientStrike> STRIKES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientStrikes() {
    }

    public static final class ClientStrike {
        public final Strike strike;
        public final boolean[] whistled;
        public final double fireStart, fireEnd;
        /** Cannon bursts {start, end} in strike time (a gun run makes two passes). */
        public final List<double[]> bursts = new ArrayList<>();
        /** Strike time each hardpoint lets go of its store (+inf if never). */
        public final double[] releaseAt = new double[8];
        /** Decoy flare salvos fired on the way out of each attack (strike time of the salvo). */
        public final List<Double> flareSalvos = new ArrayList<>();
        boolean engineStarted;
        int brrrts;
        int flareSounds;
        /** Development: freezes the replay at a given strike time. */
        public Double frozenTime;

        ClientStrike(Strike strike) {
            this.strike = strike;
            this.whistled = new boolean[strike.drops.size()];
            List<Double> shots = new ArrayList<>();
            for (Drop d : strike.drops) {
                if (d.kind == OrdnanceKind.BULLET) shots.add(d.t0);
            }
            Collections.sort(shots);
            for (double t : shots) {
                double[] last = bursts.isEmpty() ? null : bursts.get(bursts.size() - 1);
                if (last == null || t - last[1] > 15) bursts.add(new double[]{t, t});
                else last[1] = t;
            }
            this.fireStart = bursts.isEmpty() ? -1 : bursts.get(0)[0];
            this.fireEnd = bursts.isEmpty() ? -1 : bursts.get(bursts.size() - 1)[1];
            java.util.Arrays.fill(releaseAt, Double.POSITIVE_INFINITY);
            double lastStore = -1;
            for (Drop d : strike.drops) {
                if (d.hardpoint >= 0 && d.hardpoint < releaseAt.length) releaseAt[d.hardpoint] = Math.min(releaseAt[d.hardpoint], d.t0);
                if (d.kind != OrdnanceKind.BULLET) lastStore = Math.max(lastStore, d.t0);
            }
            if (flaresEnabled()) {
                List<Double> exits = new ArrayList<>();
                if (lastStore >= 0) exits.add(lastStore + (strike.type.plane == PlaneType.CARGO ? 30 : 8));
                for (double[] b : bursts) exits.add(b[1] + 6);
                for (double e : exits) {
                    if (e + 14 >= strike.path.duration) continue;
                    flareSalvos.add(e);
                    flareSalvos.add(e + 14);
                }
            }
        }

        private static boolean flaresEnabled() {
            try {
                return AirstrikeConfig.FLARES.get();
            } catch (IllegalStateException e) {
                return true;
            }
        }

        public double time(float partialTick) {
            if (frozenTime != null) return frozenTime;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return -1e9;
            return mc.level.getGameTime() + partialTick - strike.startTick;
        }

        public boolean finished() {
            return time(0) > strike.endTime() + 20;
        }

        public boolean isMine() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && strike.callerEntityId == mc.player.getId();
        }

        public boolean isFiring(double t) {
            for (double[] b : bursts) {
                if (t >= b[0] - 1 && t <= b[1] + 1) return true;
            }
            return false;
        }
    }

    public static void add(Strike strike) {
        STRIKES.removeIf(s -> s.strike.id == strike.id);
        STRIKES.add(new ClientStrike(strike));
    }

    public static List<ClientStrike> all() {
        return Collections.unmodifiableList(STRIKES);
    }

    public static void clear() {
        STRIKES.clear();
    }

    /** Remaining ticks before the first impact of the local player's strike, or -1. */
    public static double myNextImpact() {
        for (ClientStrike s : STRIKES) {
            if (!s.isMine()) continue;
            double t = s.time(0);
            double first = s.strike.firstImpactTime();
            if (t < first) return first - t;
        }
        return -1;
    }

    public static ClientStrike myActive() {
        for (ClientStrike s : STRIKES) {
            if (s.isMine() && !s.finished() && s.time(0) < s.strike.endTime()) return s;
        }
        return null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            STRIKES.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Iterator<ClientStrike> it = STRIKES.iterator();
        while (it.hasNext()) {
            ClientStrike cs = it.next();
            if (cs.finished()) {
                it.remove();
                continue;
            }
            double t = cs.time(0);
            FlightPath path = cs.strike.path;
            boolean jet = cs.strike.type.plane == PlaneType.ATTACK_JET;

            if (!cs.engineStarted && t > -40 && t < path.duration) {
                cs.engineStarted = true;
                mc.getSoundManager().play(jet
                        ? new PlaneSound(cs, ModSounds.JET_ENGINE.get(), 1.0f, 1.0f)
                        : new PlaneSound(cs, ModSounds.PROP_ENGINE.get(), 1.0f, 1.0f));
            }

            for (int i = 0; i < cs.strike.drops.size(); i++) {
                Drop d = cs.strike.drops.get(i);
                if (cs.whistled[i] || t < d.t0 + 3) continue;
                cs.whistled[i] = true;
                if (t > d.t1) continue;
                if (d.kind == OrdnanceKind.BULLET || d.kind == OrdnanceKind.CRATE || d.kind == OrdnanceKind.BOMBLET) continue;
                if (cam.distanceToSqr(d.impact()) > 170 * 170) continue;
                float pitch = d.kind == OrdnanceKind.GBU ? 0.75f : d.kind == OrdnanceKind.SMOKE_CAN || d.kind == OrdnanceKind.GAS_CAN ? 1.25f : 0.9f + RANDOM.nextFloat() * 0.2f;
                final ClientStrike ref = cs;
                mc.getSoundManager().play(new FollowSound(ModSounds.BOMB_WHISTLE.get(), 1.5f, pitch, () -> {
                    double tt = ref.time(0);
                    return tt > d.t1 || ref.finished() ? null : d.position(tt);
                }));
            }

            // one BRRRT per burst, heard after the impacts (the sound travels much slower than the rounds)
            if (cs.brrrts < cs.bursts.size() && t >= cs.bursts.get(cs.brrrts)[0]) {
                double start = cs.bursts.get(cs.brrrts++)[0];
                if (t < start + 20) {
                    Vec3 p = path.position(start);
                    int delay = (int) Math.min(80, cam.distanceTo(p) / 17.0);
                    SimpleSoundInstance snd = new SimpleSoundInstance(ModSounds.GUN_BRRRT.get().getLocation(), SoundSource.NEUTRAL,
                            4.0f, 1.0f, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR, p.x, p.y, p.z, false);
                    mc.getSoundManager().playDelayed(snd, Math.max(1, delay));
                }
            }

            // decoy flares: the pops are heard with the delay of the sound
            while (cs.flareSounds < cs.flareSalvos.size() && t >= cs.flareSalvos.get(cs.flareSounds)) {
                double ts = cs.flareSalvos.get(cs.flareSounds++);
                if (t > ts + 10) continue;
                Vec3 p = path.position(ts);
                int delay = (int) Math.min(80, cam.distanceTo(p) / 17.0);
                SimpleSoundInstance snd = new SimpleSoundInstance(ModSounds.FLARES.get().getLocation(), SoundSource.NEUTRAL,
                        3.0f, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, false, 0, SoundInstance.Attenuation.LINEAR, p.x, p.y, p.z, false);
                mc.getSoundManager().playDelayed(snd, Math.max(1, delay));
            }

            if (t >= 0 && t <= path.duration) trails(mc, cs, t, jet, cam);
        }
    }

    private static final String[] JET_EXHAUSTS = {"exhaust_r", "exhaust_l"};
    private static final String[] CARGO_EXHAUSTS = {"exhaust_0", "exhaust_1", "exhaust_2", "exhaust_3"};
    private static final double[] ORI = new double[3];

    private static void trails(Minecraft mc, ClientStrike cs, double t, boolean jet, Vec3 cam) {
        FlightPath path = cs.strike.path;
        Vec3 here = path.position(t);
        double dist = here.distanceTo(cam);
        if (dist > 420) return;
        GeoModel model = jet ? ModModels.jet() : ModModels.cargo();
        String[] exhausts = jet ? JET_EXHAUSTS : CARGO_EXHAUSTS;
        double[] o = ORI;
        // up to three puffs per tick along the path give a continuous trail; fewer (and bigger) far away
        float q = FxQuality.at(dist);
        int steps = q > 0.66f ? 3 : q > 0.33f ? 2 : 1;
        float size = 1f + 0.3f * (3 - steps);
        for (int k = 0; k < steps; k++) {
            double tk = t - k / (double) steps;
            Vec3 pos = path.position(tk);
            path.orientation(tk, o);
            for (String name : exhausts) {
                Vec3 e = model.point(name);
                Vec3 p = pos.add(FlightPath.rotate(e.x / 16.0, e.y / 16.0, e.z / 16.0, o[0], o[1], o[2]));
                mc.level.addParticle(SmokeParticleOptions.of(jet ? 0x77736E : 0x55524E, (jet ? 0.8f : 1.1f) * size, 40, 0.0f, jet ? 0.16f : 0.22f),
                        p.x, p.y, p.z, 0, 0, 0);
            }
            // vapour trails at the wing tips while pulling hard
            if (jet && dist < 220 && Math.abs(o[2]) > 25) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 p = pos.add(FlightPath.rotate(side * 8.9, -0.4, 0.2, o[0], o[1], o[2]));
                    mc.level.addParticle(SmokeParticleOptions.of(0xF4F6F8, 0.4f, 26, 0.0f, 0.45f), p.x, p.y, p.z, 0, 0, 0);
                }
            }
        }
        // gun smoke: the cannon leaves a grey wake while it fires
        if (jet && dist < 300 && cs.isFiring(t) && cs.frozenTime == null) {
            double[] g = AircraftData.JET_GUN;
            Vec3 v = path.position(t + 1).subtract(here).scale(0.12);
            // spread along the distance flown this tick: a continuous wake rather than separate puffs
            for (int i = 0, n = FxQuality.count(3, q, 1); i < n; i++) {
                double tk = t - (i + RANDOM.nextDouble()) / n;
                Vec3 muzzle = path.position(tk).add(path.frameOffset(tk, g[0], g[1], g[2]));
                // puffs about two blocks wide from the start (one per block flown): they overlap into one wake
                mc.level.addParticle(SmokeParticleOptions.of(0x8E8B86, (2.4f + RANDOM.nextFloat() * 1.0f) * FxQuality.grow(q),
                                70 + RANDOM.nextInt(30), 0.02f, 0.42f),
                        muzzle.x, muzzle.y, muzzle.z, v.x + RANDOM.nextGaussian() * 0.04, v.y + RANDOM.nextGaussian() * 0.04,
                        v.z + RANDOM.nextGaussian() * 0.04);
            }
        }
    }
}
