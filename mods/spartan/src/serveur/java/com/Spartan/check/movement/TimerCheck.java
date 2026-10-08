package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.model.ClockBound;
import com.Spartan.check.movement.model.TimerBalance;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Timer : jeu accéléré côté client (plus de 20 ticks par seconde).
 *
 * Avec les transactions (pings), la cadence est bornée par l'horloge du serveur
 * (ClockBound) : aucune avance possible au-delà du rattrapage vanilla après un gel,
 * quel que soit le lag. Sans réponses aux pings, repli sur le crédit de temps réel
 * (TimerBalance).
 */
public class TimerCheck extends MovementCheck {
    /** Sans réponse à nos pings depuis autant, on revient au crédit de temps. */
    private static final long CLOCK_STALE_NANOS = 5_000_000_000L;

    private static final class State {
        final TimerBalance balance = new TimerBalance();
        final ClockBound clock = new ClockBound();
        long lastPong;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public TimerCheck() {
        super("Timer");
    }

    @Override
    public void onPong(ServerPlayer p, long sentNanos, long nanos) {
        State st = states.computeIfAbsent(p.getUUID(), k -> new State());
        st.clock.onPong(sentNanos);
        st.lastPong = nanos;
    }

    @Override
    public void onMovePacket(ServerPlayer p, MovePacket m) {
        if (!SpartanConfig.CHECK_TIMER.get()) {
            return;
        }
        State st = states.computeIfAbsent(p.getUUID(), k -> new State());
        boolean balanceFlag = st.balance.onPacket(m.nanos(), m.charged());
        if (!m.charged()) {
            return;
        }
        double ahead = st.clock.onPacket(m.nanos());
        if (st.lastPong != 0 && m.nanos() - st.lastPong < CLOCK_STALE_NANOS) {
            if (ahead > ClockBound.SLACK_TICKS) {
                flag(p, String.format("cadence de jeu accélérée : %.0f ticks d'avance sur l'horloge du serveur "
                        + "(mesurée par les pings)", ahead));
                st.clock.reset();
            }
        } else if (balanceFlag) {
            flag(p, "cadence de jeu accélérée : plus de 0,5 s d'avance sur le temps réel");
        }
    }

    /** La cadence ne dépend pas des sauts de position : pas de remise à zéro pendant la grâce. */
    @Override
    public void onGrace(UUID uuid) {
    }

    @Override
    public void clear(UUID uuid) {
        states.remove(uuid);
    }
}
