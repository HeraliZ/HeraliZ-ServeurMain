package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Liens par réflexion avec la saison des clans (HeraliZ Clans 3.6.0, {@code fr.heralisz.clans.saison.SaisonApi}) et
 * avec Réanimation ({@code fr.heraliz.reanimation.core.Downed}). La copie de Clans de {@code libs/} est plus ancienne :
 * aucun nom de la saison n'est compilé ici. Mod absent ou trop ancien : saison inactive, points 0, joueur jamais à terre.
 */
public final class SaisonPont {
    private SaisonPont() {
    }

    private static final String API = "fr.heralisz.clans.saison.SaisonApi";
    private static boolean cherche;
    @Nullable
    private static Method active, raid, perdreTout, points, rangs, palier;
    private static boolean downedCherche;
    @Nullable
    private static Method downed;

    private static void chercher() {
        if (cherche) {
            return;
        }
        cherche = true;
        try {
            Class<?> c = Class.forName(API);
            active = c.getMethod("active");
            raid = c.getMethod("raid", UUID.class, UUID.class, String.class, String.class);
            perdreTout = c.getMethod("perdreTout", UUID.class, String.class);
            points = c.getMethod("points", UUID.class);
            rangs = c.getMethod("rangs", UUID.class);
            palier = c.getMethod("palier", UUID.class, String.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            Crac.LOGGER.info("[CRac] saison des clans absente (HeraliZ Clans < 3.6.0) : cœurs sans points");
        }
    }

    @Nullable
    private static Object appel(@Nullable Method m, Object... args) {
        chercher();
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Crac.LOGGER.warn("[CRac] saison des clans : {} en erreur : {}", m.getName(), e.toString());
            return null;
        }
    }

    /** Le système de saison est là (même hors saison). */
    public static boolean present() {
        chercher();
        return active != null;
    }

    public static boolean active() {
        return Boolean.TRUE.equals(appel(active));
    }

    /** Prise du cœur : faux si ce clan a déjà été pris pendant cette Purge (rien ne change alors). */
    public static boolean raid(@Nullable UUID attaquant, UUID victime, String purge, String joueur) {
        chercher();
        return Boolean.TRUE.equals(appel(raid, attaquant, victime, purge, joueur));
    }

    public static void perdreTout(UUID clan, String motif) {
        appel(perdreTout, clan, motif);
    }

    public static long points(UUID clan) {
        chercher();
        return appel(points, clan) instanceof Long l ? l : 0L;
    }

    public static int[] rangs(UUID clan) {
        chercher();
        return appel(rangs, clan) instanceof int[] r ? r : new int[4];
    }

    /** Palier acheté d'une branche de l'arbre du clan (« base », « tp »…), 0 sans Clans 3.6.0. */
    public static int palier(@Nullable UUID clan, String branche) {
        chercher();
        if (clan == null) {
            return 0;
        }
        return appel(palier, clan, branche) instanceof Integer i ? i : 0;
    }

    /** Joueur à terre (Réanimation) ; faux sans Réanimation. */
    public static boolean aTerre(Entity e) {
        if (!downedCherche) {
            downedCherche = true;
            try {
                downed = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", Entity.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                downed = null;
            }
        }
        if (downed == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(downed.invoke(null, e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
