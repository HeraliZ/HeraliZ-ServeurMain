package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.config.SpartanConfig;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Velocity (anti-recul / AntiKB) : le recul d'un coup, d'une balle ou d'une explosion
 * est imposé par le serveur, mais c'est le client qui l'applique. Grâce aux
 * transactions, on sait quand il l'a forcément appliqué ; s'il n'apparaît dans aucune
 * de ses positions alors que rien ne le bloquait (mur, plafond, entité collée), il a
 * été ignoré. Un recul isolé peut être mal jugé : on signale à partir de 4 ignorés sur
 * les 8 derniers jugés. Signalement seul, sans renvoi (catégorie à part).
 */
public class VelocityCheck extends MovementCheck {
    private static final int WINDOW = 8;
    private static final int THRESHOLD = 4;

    private final Map<UUID, ArrayDeque<Boolean>> history = new ConcurrentHashMap<>();

    public VelocityCheck() {
        super("Velocity", "velocity");
    }

    @Override
    public void onVelocity(ServerPlayer p, VelocityOutcome o) {
        if (!SpartanConfig.CHECK_VELOCITY.get()) {
            return;
        }
        ArrayDeque<Boolean> h = history.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        h.addLast(o.ignored());
        while (h.size() > WINDOW) {
            h.removeFirst();
        }
        long ignored = h.stream().filter(b -> b).count();
        if (o.ignored() && ignored >= THRESHOLD) {
            flag(p, String.format("ignore les reculs : %d des %d derniers (dernier : %s)", ignored, h.size(),
                    describe(o)));
            h.clear();
        }
    }

    private static String describe(VelocityOutcome o) {
        StringBuilder b = new StringBuilder();
        if (o.expectedH() > 0) {
            b.append(String.format("recul de %.2f bloc/tick, déplacement dans son sens %.2f", o.expectedH(), o.along()));
        }
        if (o.expectedUp() > 0) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(String.format("poussée verticale %.2f, montée %.2f", o.expectedUp(), Math.max(0, o.rise())));
        }
        return b.toString();
    }

    @Override
    public void clear(UUID uuid) {
        history.remove(uuid);
    }
}
