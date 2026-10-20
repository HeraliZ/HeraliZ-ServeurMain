package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.model.ExcessWindow;
import com.Spartan.check.movement.model.FallWindow;
import com.Spartan.check.movement.model.Physics;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vol / planer / flotter, sur les positions réellement envoyées par le client.
 *
 * 1. Gravité : en l'air, un joueur ne peut pas être plus haut que la trajectoire
 *    maximale de vanilla depuis son dernier appui (saut maximal au décollage,
 *    reculs et explosions avec leur valeur exacte). Planer, voler à plat, flotter
 *    au-dessus de l'eau (Jesus) : la trajectoire est dépassée en moins d'une seconde.
 * 2. Parachute / planeur (flyExemptKeywords) en l'air : la physique est celle du
 *    mod ; on exige seulement une descente minimale (flyGearMinFallPerSecond).
 * 3. Immobilité en l'air : position strictement identique pendant 3 s alors que
 *    le client continue d'envoyer des paquets.
 *
 * Les montées impossibles (re-saut en l'air, saut trop haut) relèvent de Vertical.
 */
public class FlyCheck extends MovementCheck {
    private static final long STILL_NANOS = 3_000_000_000L;
    private static final int STILL_PACKETS = 2;

    private static final class State {
        final ExcessWindow window = new ExcessWindow();
        final FallWindow gear = new FallWindow();
        long stillSince;
        int stillPackets;

        void reset() {
            window.clear();
            gear.clear();
            stillSince = 0;
            stillPackets = 0;
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FlyCheck() {
        super("Fly");
    }

    @Override
    public void onSample(ServerPlayer p, MoveSample s) {
        if (!SpartanConfig.CHECK_FLY.get()) {
            return;
        }
        State st = states.computeIfAbsent(p.getUUID(), k -> new State());
        MotionTracker.Result rm = s.motion();
        boolean roughGround = rm != null && rm.rough() && s.claimedGround();
        if (s.groundTo() || roughGround || s.special() || s.fluid() || s.climbable() || s.slowing() || s.platform() || s.piston()
                || s.slowFalling() || s.hoverGear() || s.gravity() <= 0.005) {
            st.reset();
            return;
        }
        if (s.still()) {
            if (st.stillSince == 0) {
                st.stillSince = s.nanos();
                st.stillPackets = 0;
            }
            st.stillPackets++;
            checkStill(p, st, s.nanos(), s.to().y);
        } else {
            st.stillSince = 0;
        }

        if (s.descentGear()) {
            // Parachute / planeur : physique du mod, seulement une descente minimale.
            double g = s.gravity();
            double minFall = SpartanConfig.FLY_GEAR_MIN_FALL_PER_SECOND.get() * Math.min(1.0, g / Physics.GRAVITY);
            FallWindow.Verdict v = st.gear.add(s.dy(), s.ticks(), minFall, g);
            if (v.flag()) {
                flag(p, String.format("en l'air avec parachute %.1f s sans descendre (%.2f bloc/s, minimum %.2f, y=%.1f)",
                        v.ticks() / 20.0, -v.net() * 20.0 / v.ticks(), minFall, s.to().y));
            }
            st.window.clear();
            return;
        }
        st.gear.clear();
        MotionTracker.Result m = s.motion();
        if (m == null || m.exempt()) {
            st.window.clear();
            return;
        }
        // Montées : Vertical. Ici, ce qui descend moins vite que la gravité (ou ne descend pas).
        double excess = s.dy() > 0 ? 0 : m.excessDy();
        double over = st.window.add(excess, s.ticks(), SpartanConfig.FLY_TOLERANCE.get());
        if (over > 0) {
            flag(p, String.format("descend moins vite que la gravité : %.2f bloc(s) au-dessus de la trajectoire maximale "
                    + "en ~1 s (dernier pas %.3f, maximum %.3f, y=%.1f)", over, s.dy(), m.allowedDy(), s.to().y));
        }
    }

    @Override
    public void onMovePacket(ServerPlayer p, MovePacket m) {
        // Rotation seule pendant une immobilité en l'air : le client est bien vivant.
        State st = states.get(p.getUUID());
        if (!m.hasPos() && st != null && st.stillSince != 0) {
            st.stillPackets++;
            checkStill(p, st, m.nanos(), Double.NaN);
        }
    }

    private void checkStill(ServerPlayer p, State st, long nanos, double y) {
        if (nanos - st.stillSince >= STILL_NANOS && st.stillPackets >= STILL_PACKETS) {
            flag(p, String.format("immobile en l'air depuis %.1f s alors que le client envoie des paquets%s",
                    (nanos - st.stillSince) / 1e9, Double.isNaN(y) ? "" : String.format(" (y=%.1f)", y)));
            st.stillSince = nanos;
            st.stillPackets = 0;
        }
    }

    @Override
    public void clear(UUID uuid) {
        states.remove(uuid);
    }
}
