package fr.heralisz.clans.profil;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Ce que le profil lit chez nos autres mods, par réflexion (ils dépendent de celui-ci, pas l'inverse) : grade affiché et
 * staff caché (HeraliZ Grades), quêtes de Varga et bourses de hérals (HeraliZ Quêtes), compte de capsules (HeraliZ
 * Casino). Un mod absent ou une lecture qui échoue rend « rien » (null ou -1) : l'écran cache simplement la ligne. Fil
 * serveur.
 */
final class PontsProfil {
    private PontsProfil() {
    }

    /** Une méthode statique d'un autre mod, cherchée une fois ; absente : plus jamais cherchée (une ligne au journal). */
    private static final class Appel {
        private final String classe, methode;
        private final Class<?>[] types;
        @Nullable
        private Method m;
        private boolean absent;

        Appel(String classe, String methode, Class<?>... types) {
            this.classe = classe;
            this.methode = methode;
            this.types = types;
        }

        @Nullable
        Object appeler(Object... args) {
            if (absent) return null;
            try {
                if (m == null) m = Class.forName(classe).getMethod(methode, types);
                return m.invoke(null, args);
            } catch (InvocationTargetException ex) {
                // erreur dans l'autre mod : rien cette fois, on réessaiera la prochaine
                return null;
            } catch (ReflectiveOperationException | LinkageError ex) {
                absent = true;
                HeraliszClans.LOG.info("[profils] {}.{} absent : cette partie du profil n'est pas montrée ({})", classe, methode, ex.toString());
                return null;
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private static final Appel GRADE = new Appel("fr.heraliz.grades.serveur.GradesApi", "gradeAfficheTextes", UUID.class);
    private static final Appel CACHE = new Appel("fr.heraliz.grades.serveur.GradesApi", "cache", UUID.class);
    private static final Appel QUETES = new Appel("fr.heraliz.quetes.serveur.QuetesApi", "profil", UUID.class);
    private static final Appel BOURSES = new Appel("fr.heraliz.quetes.serveur.QuetesApi", "bourses", ServerPlayer.class);
    private static final Appel CAPSULES = new Appel("fr.heraliz.casino.api.CapsulesApi", "solde", UUID.class);
    private static final Appel CAPSULES_SUR_LUI = new Appel("fr.heraliz.casino.api.CapsulesApi", "surLui", Player.class);

    /** Grade affiché : {identifiant, nom français, anglais, russe, couleur RRGGBB, famille} ; null : inconnu. */
    @Nullable
    static String[] grade(UUID joueur) {
        return GRADE.appeler(joueur) instanceof String[] s && s.length >= 6 ? s : null;
    }

    /** Staff caché (vanish) : jamais proposé dans les suggestions. */
    static boolean cache(UUID joueur) {
        return Boolean.TRUE.equals(CACHE.appeler(joueur));
    }

    /** Quêtes de Varga : {quêtes finies, titre français, anglais, russe} ; null : inconnu. */
    @Nullable
    static String[] quetes(UUID joueur) {
        return QUETES.appeler(joueur) instanceof String[] s && s.length >= 4 ? s : null;
    }

    /** Bourses de hérals sur le joueur : {hérals, nombre de bourses} ; null : inconnu. */
    @Nullable
    static long[] bourses(ServerPlayer joueur) {
        return BOURSES.appeler(joueur) instanceof long[] l && l.length >= 2 ? l : null;
    }

    /** Capsules sur le compte du casino ; -1 : inconnu. */
    static long capsulesCompte(UUID joueur) {
        return CAPSULES.appeler(joueur) instanceof Number n ? n.longValue() : -1;
    }

    /** Capsules dans l'inventaire ; -1 : inconnu. */
    static long capsulesSurLui(ServerPlayer joueur) {
        return CAPSULES_SUR_LUI.appeler(joueur) instanceof Number n ? n.longValue() : -1;
    }
}
