package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.model.ExcessWindow;
import com.Spartan.check.movement.model.VitesseSoutenue;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vitesse horizontale, sur les positions réellement envoyées, contre l'enveloppe
 * physique de vanilla recalculée à chaque tick (voir HorizontalEnvelope) : vitesse
 * du joueur (effets, objets et armures de mods), sprint, sauts, glace, plafond bas,
 * reculs et explosions avec leur valeur exacte, poussée des mobs collés au joueur.
 *
 * - L'élan repart toujours de la vitesse BORNÉE : impossible de l'accumuler en
 *   trichant un peu à chaque tick (bunny-hop progressif, LongJump).
 * - Dépassements cumulés sur ~1 s de temps réel, avec une petite tolérance.
 * - Déplacement impossible en une seule position : signalé immédiatement.
 * - Vitesse soutenue (02/11, point 14) : sur ~5 s de déplacement à pied entièrement suivi, la distance parcourue
 *   contre le maximum physique SANS la marge par tick ; un speed hack réglé sous cette marge (+10 à +20 %) y dépasse.
 *   Toute interruption du suivi (véhicule, train, eau, téléportation…) repart de zéro.
 */
public class SpeedCheck extends MovementCheck {
    private final Map<UUID, ExcessWindow> windows = new ConcurrentHashMap<>();
    private final Map<UUID, VitesseSoutenue> soutenues = new ConcurrentHashMap<>();

    public SpeedCheck() {
        super("Speed");
    }

    @Override
    public void onSample(ServerPlayer p, MoveSample s) {
        if (!SpartanConfig.CHECK_SPEED.get()) {
            return;
        }
        ExcessWindow w = windows.computeIfAbsent(p.getUUID(), k -> new ExcessWindow());
        VitesseSoutenue v = soutenues.computeIfAbsent(p.getUUID(), k -> new VitesseSoutenue());
        MotionTracker.Result m = s.motion();
        if (m == null || m.exempt()) {
            w.clear();
            v.clear();
            return;
        }
        if (SpartanConfig.CHECK_SPEED_SOUTENUE.get() && !m.usedImpulse() && !m.slowed()) {
            double d = v.add(s.horizontal(), m.allowedH(), m.ticks(), SpartanConfig.SPEED_SOUTENUE_SECONDES.get() * 20,
                    SpartanConfig.SPEED_SOUTENUE_DISTANCE_MIN.get(), SpartanConfig.SPEED_SOUTENUE_RATIO.get());
            if (d > 0) {
                flag(p, String.format("vitesse soutenue : %.1f blocs parcourus en %.1f s pour un maximum physique de %.1f"
                                + " (+%d %%, seuil +%d %%)", v.constatReel(), v.constatTicks() / 20.0, v.constatPermis(),
                        Math.round(d * 100), Math.round(SpartanConfig.SPEED_SOUTENUE_RATIO.get() * 100)));
            }
        } else {
            v.clear();
        }
        if (s.horizontal() > Math.max(2.5, m.allowedH() * 4)) {
            flag(p, String.format("déplacement impossible : %.2f blocs en une position (maximum physique %.2f)",
                    s.horizontal(), m.allowedH()));
            w.clear();
            return;
        }
        // Marge par tick : un pas un peu au-dessus du maximum (saut depuis un bord, 0,31 pour 0,29)
        // n'est plus compté ; un speed hack la dépasse largement.
        double marge = SpartanConfig.SPEED_MARGIN_PER_TICK.get() * Math.max(1, s.ticks());
        double over = w.add(m.excessH() - marge, s.ticks(), SpartanConfig.SPEED_TOLERANCE.get());
        if (over > 0) {
            flag(p, String.format("%.2f bloc(s) de plus que le maximum physique en ~1 s (dernier pas : %.2f bloc, "
                    + "maximum %.2f%s)", over, s.horizontal(), m.allowedH(),
                    m.ticks() > 1 ? String.format(" sur %d ticks", m.ticks()) : ""));
        }
    }

    @Override
    public void clear(UUID uuid) {
        windows.remove(uuid);
        soutenues.remove(uuid);
    }
}
