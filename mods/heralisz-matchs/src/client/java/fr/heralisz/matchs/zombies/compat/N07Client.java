package fr.heralisz.matchs.zombies.compat;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraftforge.fml.ModList;

/**
 * Cote CLIENT : une cinematique du boss N-07 est-elle a l'ecran ? (dependance douce : sans {@code heralisz_n07}, toujours
 * faux, aucune classe du boss n'est chargee). Le HUD BO6 se cache alors ; le mod du boss coupe deja tous les calques
 * Forge pendant ses scenes (API.md §7).
 */
public final class N07Client {
    private N07Client() {
    }

    private static final boolean PRESENT = ModList.get() != null && ModList.get().isLoaded("heralisz_n07");
    private static boolean casse;

    /** Vrai pendant une scene, une cinematique du boss ou un rituel du terminal. */
    public static boolean cinematique() {
        if (!PRESENT || casse) return false;
        try {
            return Acces.enCours();
        } catch (LinkageError | RuntimeException ex) {
            // API du boss differente : on n'essaie plus (le HUD reste affiche)
            casse = true;
            HeraliszMatchs.LOG.warn("[zombies] heralisz_n07 : CinematiqueN07 illisible ({}), HUD jamais cache", ex.toString());
            return false;
        }
    }

    /** Seule classe qui touche le mod du boss : chargee au premier appel, si le mod est la. */
    private static final class Acces {
        static boolean enCours() {
            return fr.heralisz.n07.api.client.CinematiqueN07.enCours();
        }
    }
}
