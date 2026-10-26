package fr.heraliz.casino.zone;

import fr.heraliz.casino.HeralizCasino;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Qui entre dans la zone VIP : les grades de la boutique de HeraliZ Grades, Rescape au moins (Rescape, Traqueur, Roi de
 * la Zone). Lu par reflexion ({@code fr.heraliz.grades.serveur.GradesApi.rangBoutique}) : sans le mod des grades, la
 * zone VIP reste fermee a tout le monde (sauf en creatif ou spectateur).
 */
final class AccesVip {
    private AccesVip() {
    }

    /** Rang minimal du grade de la boutique : 1 Rescape, 2 Traqueur, 3 Roi de la Zone. */
    static final int RANG_MIN = 1;

    private static Method rangBoutique;
    private static boolean cherche;

    private static Method perm;
    private static boolean permCherche;

    static boolean autorise(ServerPlayer p) {
        // HeraliZ Grades 2.0.0 : permission « Zones VIP » réglée par grade dans l'éditeur des grades
        if (!permCherche) {
            permCherche = true;
            try {
                perm = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("perm", java.util.UUID.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                perm = null;
            }
        }
        if (perm != null) {
            try {
                int v = (Integer) perm.invoke(null, p.getUUID(), "zones_vip");
                if (v >= 0) return v >= 1;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // règle d'avant ci-dessous
            }
        }
        Method m = methode();
        if (m == null) return false;
        try {
            return ((Integer) m.invoke(null, p.getUUID())) >= RANG_MIN;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizCasino.LOGGER.warn("[HeralizCasino] zone VIP : lecture du grade de {} impossible ({}) : entree refusee", p.getGameProfile().getName(), e.toString());
            return false;
        }
    }

    /** Le mod des grades est la (sinon la zone VIP est fermee). */
    static boolean disponible() {
        return methode() != null;
    }

    private static Method methode() {
        if (!cherche) {
            cherche = true;
            try {
                rangBoutique = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("rangBoutique", java.util.UUID.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                HeralizCasino.LOGGER.warn("[HeralizCasino] HeraliZ Grades absent : la zone VIP du casino reste fermee aux joueurs");
            }
        }
        return rangBoutique;
    }
}
