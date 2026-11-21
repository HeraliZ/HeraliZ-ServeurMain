package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Staff caché (vanish de HeraliZ Grades, lu par réflexion : {@code GradesApi.cache} et {@code GradesApi.voitLesCaches}),
 * 21/11 : la table des tags et le profil d'un autre joueur ne disent jamais aux joueurs hors du staff qu'un caché est
 * connecté. Sans HeraliZ Grades : personne n'est caché. Fil serveur.
 */
public final class PontVanish {
    private static final String API = "fr.heraliz.grades.serveur.GradesApi";
    @Nullable
    private static Method cache, voit;
    private static boolean cherche, absent;

    private PontVanish() {
    }

    private static boolean pret() {
        if (!cherche) {
            cherche = true;
            try {
                Class<?> c = Class.forName(API);
                cache = c.getMethod("cache", UUID.class);
                voit = c.getMethod("voitLesCaches", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                absent = true;
                HeraliszClans.LOG.info("[clans] staff caché de HeraliZ Grades introuvable : personne n'est caché ({})", e.toString());
            }
        }
        return !absent;
    }

    /** Le joueur est-il un membre du staff caché ? */
    public static boolean cache(UUID joueur) {
        return pret() && vrai(cache, joueur);
    }

    /** Ce joueur voit-il le staff caché (staff connecté) ? */
    public static boolean voitLesCaches(ServerPlayer joueur) {
        return pret() && vrai(voit, joueur);
    }

    /** {@code voyant} peut-il voir {@code cible} en ligne (pas caché, lui-même, staff, ou la console : null) ? */
    public static boolean visible(@Nullable ServerPlayer voyant, UUID cible) {
        return voyant == null || voyant.getUUID().equals(cible) || !cache(cible) || voitLesCaches(voyant);
    }

    private static boolean vrai(@Nullable Method m, Object arg) {
        if (m == null) return false;
        try {
            return Boolean.TRUE.equals(m.invoke(null, arg));
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            return false; // erreur dans l'autre mod : « pas caché » cette fois
        }
    }
}
