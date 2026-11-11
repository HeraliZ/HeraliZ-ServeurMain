package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.world.item.ItemStack;

/**
 * Reparation d'une arme avec un kit (serveur). Le kit rend une part de l'etat sans depasser son plafond ; l'etat maximal
 * de l'arme baisse un peu a chaque reparation (reglable, 0 = jamais), sans descendre sous le plancher. Le kit perd une
 * utilisation (il disparait a la derniere).
 * <p>11/11 (demande du proprietaire) : une arme ne se repare qu'un nombre de fois fixe par SA rarete (grise 3, verte 5,
 * bleue 8, violette 12, legendaire 17 : {@code reparations_max} du fichier de reglages) ; chaque reparation en use une,
 * quel que soit le kit, et le compte est garde sur l'arme ({@link Usure#reparations}, les reparations faites avant
 * comptent). Raretes : celles de HeraliZ Loot ({@link RaretesLoot}), comme partout sur le serveur.
 * <p>Jar fusionne (docs/METHODE_CLIENT_SERVEUR.md §5) : ici, le calcul et l'affichage lus des deux cotes (ecran des
 * reglages, infobulle, HUD) ; la reparation elle-meme ({@code appliquer}) est dans {@code ReparationServeur} (partie
 * serveur).
 */
public final class Reparation {
    private Reparation() {
    }

    public enum Resultat { REPAREE, PAS_UNE_ARME, NE_S_USE_PAS, DEJA_AU_PLAFOND, RUINEE_REFUSEE, EXEMPTEE, PLUS_DE_REPARATION }

    /** Resultat d'une reparation : nouvel etat et nouvel etat maximal (milliemes), ou la raison du refus. */
    public record Calcul(int etat, int max, @javax.annotation.Nullable Resultat refus) {
    }

    /**
     * Calcul seul (sans toucher a l'arme) : refus si l'arme n'a plus de reparation ({@code restantes}), si elle est ruinee
     * et que le kit ne le permet pas, ou si la reparation ne ferait pas monter l'etat. Sinon l'etat maximal baisse d'abord
     * (sans passer sous le plancher), puis l'etat monte du gain du kit sans depasser ni le plafond du kit ni le nouvel etat
     * maximal.
     */
    public static Calcul calculer(int etat, int max, int restantes, int niveau, Regles r) {
        if (restantes <= 0) return new Calcul(etat, max, Resultat.PLUS_DE_REPARATION);
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

    // ---------------------------------------------------------------- raretes (11/11)
    /** Rang de rareté borné à la table des réglages (inconnue : Commun, la rareté par défaut du loot). */
    private static int rang(int rarete) {
        return rarete < 0 ? 0 : Math.min(rarete, Regles.NB_RARETES - 1);
    }

    /** Rareté d'une arme (0 Commun ... 5 Unique) : celle de HeraliZ Loot, Commun si elle est inconnue. */
    public static int rareteArme(ItemStack arme) {
        return rang(RaretesLoot.rang(arme));
    }

    /** Réparations possibles en tout pour une arme de cette rareté. */
    public static int maximum(int rareteArme, Regles r) {
        return r.reparationsMax[rang(rareteArme)];
    }

    /** Réparations encore possibles pour cette arme : le maximum de sa rareté moins les réparations déjà faites. */
    public static int restantes(ItemStack arme, Regles r) {
        return Math.max(0, maximum(rareteArme(arme), r) - Usure.reparations(arme));
    }

    /** « 72 % » (au pour cent pres ; une arme pas tout a fait ruinee n'affiche jamais 0 %). */
    public static String pct(int milliemes) {
        int v = Math.round(milliemes / 10f);
        if (v == 0 && milliemes > 0) v = 1;
        if (v == 100 && milliemes < 1000) v = 99;
        return v + " %";
    }
}
