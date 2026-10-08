package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;

/**
 * Reparation d'une arme avec un kit (serveur). Le kit rend une part de l'etat sans depasser son plafond ; l'etat maximal
 * de l'arme baisse un peu a chaque reparation (reglable, 0 = jamais), sans descendre sous le plancher. Le kit perd une
 * utilisation (il disparait a la derniere).
 * <p>Jar fusionne (docs/METHODE_CLIENT_SERVEUR.md §5) : ici, le calcul et l'affichage lus des deux cotes (ecran des
 * reglages, infobulle, HUD) ; la reparation elle-meme ({@code appliquer}) est dans {@code ReparationServeur} (partie
 * serveur).
 */
public final class Reparation {
    private Reparation() {
    }

    public enum Resultat { REPAREE, PAS_UNE_ARME, NE_S_USE_PAS, DEJA_AU_PLAFOND, RUINEE_REFUSEE, EXEMPTEE }

    /** Resultat d'une reparation : nouvel etat et nouvel etat maximal (milliemes), ou la raison du refus. */
    public record Calcul(int etat, int max, @javax.annotation.Nullable Resultat refus) {
    }

    /**
     * Calcul seul (sans toucher a l'arme) : l'etat maximal baisse d'abord (sans passer sous le plancher), puis l'etat
     * monte du gain du kit sans depasser ni le plafond du kit ni le nouvel etat maximal. Refus si l'arme est ruinee et
     * que le kit ne le permet pas, ou si la reparation ne ferait pas monter l'etat.
     */
    public static Calcul calculer(int etat, int max, int niveau, Regles r) {
        if (etat <= 0 && !r.kitRuinee[niveau]) return new Calcul(etat, max, Resultat.RUINEE_REFUSEE);
        int plafond = Math.round(r.kitPlafond[niveau] * 10f);
        int nouveauMax = max;
        if (r.perteMaxParReparation > 0) {
            nouveauMax = Math.max(Math.round(r.maxMinimum * 10f), max - Math.round(r.perteMaxParReparation * 10f));
            nouveauMax = Math.min(max, nouveauMax);
        }
        int cible = Math.min(Math.min(plafond, nouveauMax), etat + Math.round(r.kitGain[niveau] * 10f));
        if (cible <= etat) return new Calcul(etat, max, Resultat.DEJA_AU_PLAFOND);
        return new Calcul(cible, nouveauMax, null);
    }

    /** « 72 % » (au pour cent pres ; une arme pas tout a fait ruinee n'affiche jamais 0 %). */
    public static String pct(int milliemes) {
        int v = Math.round(milliemes / 10f);
        if (v == 0 && milliemes > 0) v = 1;
        if (v == 100 && milliemes < 1000) v = 99;
        return v + " %";
    }
}
