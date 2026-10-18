package fr.heralisz.utils.train;

import java.lang.reflect.Method;

/**
 * Délai pour se rallonger dans le train, chez le joueur : celui de HeraliZ Tactique (s'allonger au sol), lu par réflexion
 * ({@code fr.heraliz.tactique.client.allonge.AllongeClient.allongeTrain / releveTrain}) ; le serveur applique le même
 * (DelaiAllonge). Sans Tactique : aucun délai.
 */
final class DelaiAllongeClient {
    private static final String CLASSE = "fr.heraliz.tactique.client.allonge.AllongeClient";
    private static Method permis, releve;
    private static boolean cherche;

    private DelaiAllongeClient() {}

    private static void chercher() {
        if (cherche) return;
        cherche = true;
        try {
            Class<?> c = Class.forName(CLASSE);
            permis = c.getMethod("allongeTrain");
            releve = c.getMethod("releveTrain");
        } catch (Throwable t) {
            permis = null;
            releve = null;
        }
    }

    /** Peut-on s'allonger maintenant ? Faux : « Tu pourras te rallonger dans N s » est affiché par Tactique. */
    static boolean permis() {
        chercher();
        if (permis == null) return true;
        try {
            return (boolean) permis.invoke(null);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Le passager se relève : le délai repart. */
    static void releve() {
        chercher();
        if (releve == null) return;
        try {
            releve.invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
