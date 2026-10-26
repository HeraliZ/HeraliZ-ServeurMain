package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.model.ExcessWindow;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Montées impossibles en vanilla, contre l'enveloppe verticale (VerticalEnvelope) :
 * - Step : monter plus qu'une marche ou qu'un saut en une seule position ;
 * - HighJump : sauter plus haut que possible (Saut amélioré compris) ;
 * - AirJump / Spider : re-sauter en plein air, escalader un mur sans échelle.
 *
 * Reculs, explosions et rebonds (slime, lit) sont intégrés avec leur valeur
 * exacte ; échelles, eau, toiles, plateformes et modes spéciaux sont exemptés.
 */
public class VerticalCheck extends MovementCheck {
    /** Une seule montée qui dépasse autant le maximum est signalée tout de suite (marche géante). */
    private static final double INSTANT = 0.35;

    private final Map<UUID, ExcessWindow> windows = new ConcurrentHashMap<>();

    public VerticalCheck() {
        super("Vertical");
    }

    @Override
    public void onSample(ServerPlayer p, MoveSample s) {
        if (!SpartanConfig.CHECK_VERTICAL.get()) {
            return;
        }
        ExcessWindow w = windows.computeIfAbsent(p.getUUID(), k -> new ExcessWindow());
        MotionTracker.Result m = s.motion();
        if (m == null || m.exempt()) {
            w.clear();
            return;
        }
        double excess = s.dy() > 0 ? m.excessDy() : 0;
        if (excess > INSTANT) {
            flag(p, String.format("monte de %.2f bloc en une position (maximum %.2f%s)", s.dy(), m.allowedDy(),
                    s.groundFrom() ? ", depuis le sol" : ", en plein air"));
            w.clear();
            return;
        }
        double over = w.add(excess, s.ticks(), SpartanConfig.VERTICAL_TOLERANCE.get());
        if (over > 0) {
            flag(p, String.format("monte plus que la physique ne le permet : %.2f bloc(s) de trop en ~1 s "
                    + "(saut en l'air ou escalade ; dernier pas +%.2f, maximum %.2f)", over, s.dy(), m.allowedDy()));
        }
    }

    @Override
    public void clear(UUID uuid) {
        windows.remove(uuid);
    }
}
