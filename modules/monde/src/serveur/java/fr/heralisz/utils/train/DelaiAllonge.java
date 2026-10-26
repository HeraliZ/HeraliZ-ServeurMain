package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/**
 * Délai pour se rallonger dans le train, côté serveur : celui de HeraliZ Tactique (s'allonger au sol), lu par réflexion
 * ({@code fr.heraliz.tactique.serveur.AllongeServeur.restantTrain / releveTrain}). L'allongé du train ne passe pas par
 * TaCZ : sans ce branchement, on pouvait s'allonger et se relever sans attendre. Sans Tactique : aucun délai.
 */
final class DelaiAllonge {
    private static final String CLASSE = "fr.heraliz.tactique.serveur.AllongeServeur";
    private static Method restant, releve;
    private static boolean cherche;

    private DelaiAllonge() {}

    private static synchronized void chercher() {
        if (cherche) return;
        cherche = true;
        try {
            Class<?> c = Class.forName(CLASSE);
            restant = c.getMethod("restantTrain", ServerPlayer.class);
            releve = c.getMethod("releveTrain", ServerPlayer.class);
        } catch (Throwable t) {
            restant = null;
            releve = null;
            HeraliszUtils.LOG.info("Train : délai pour se rallonger de HeraliZ Tactique absent ({}), aucun délai dans le train", t.toString());
        }
    }

    /** Millisecondes avant que ce passager puisse s'allonger (0 : tout de suite). */
    static long restantMs(ServerPlayer p) {
        chercher();
        if (restant == null) return 0L;
        try {
            return (long) restant.invoke(null, p);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** Le passager vient de se relever : le délai repart. */
    static void releve(ServerPlayer p) {
        chercher();
        if (releve == null) return;
        try {
            releve.invoke(null, p);
        } catch (Throwable ignored) {
        }
    }
}
