package com.Spartan.esp;

import com.Spartan.Spartan;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Coéquipiers : jamais cachés l'un à l'autre (marqueurs d'équipe, minimap de clan…).
 *
 * - équipes vanilla (scoreboard) : automatique ;
 * - mod de clan : il déclare son test d'alliance, soit par IMC
 *   (InterModComms.sendTo("spartan", "ally_predicate", () -> (BiPredicate&lt;ServerPlayer, ServerPlayer&gt;) ...)),
 *   soit directement avec {@link #register}.
 */
public final class Allies {
    private static final List<BiPredicate<ServerPlayer, ServerPlayer>> PREDICATES = new CopyOnWriteArrayList<>();
    private static boolean warned;

    private Allies() {
    }

    /** Ajoute un test d'alliance (appelé sur le thread serveur, doit être rapide). */
    public static void register(BiPredicate<ServerPlayer, ServerPlayer> predicate) {
        PREDICATES.add(predicate);
        Spartan.LOGGER.info("[Spartan] Anti-ESP : test d'alliance ajouté ({}).", predicate.getClass().getName());
    }

    static boolean allied(ServerPlayer a, ServerPlayer b) {
        if (a.isAlliedTo(b)) {
            return true;
        }
        for (BiPredicate<ServerPlayer, ServerPlayer> p : PREDICATES) {
            try {
                if (p.test(a, b)) {
                    return true;
                }
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    Spartan.LOGGER.warn("[Spartan] Anti-ESP : le test d'alliance {} a échoué : {}", p, t.toString());
                }
            }
        }
        return false;
    }
}
