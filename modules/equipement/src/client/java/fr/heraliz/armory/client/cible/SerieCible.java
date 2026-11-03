package fr.heraliz.armory.client.cible;

import fr.heraliz.armory.cible.CibleEssai;
import fr.heraliz.armory.network.PaquetsCible;

/**
 * Série de coups d'un joueur sur une cible d'essai (03/11), tenue chez lui seul (le serveur n'en garde rien) : les plombs
 * d'un même tir de fusil à pompe sont réunis en un tir ; une série s'arrête après {@link #FIN_MS} sans coup, ou quand la
 * cible ou sa tenue change. « Mort au tir n° N » : premier tir où le total atteint les 20 PV d'un joueur.
 */
final class SerieCible {
    private SerieCible() {}

    /** Fin d'une série : 4 s sans coup. */
    static final long FIN_MS = 4000L;

    static boolean active;
    static int entite = -1;
    static String tenue = "";
    static boolean casque;
    static long debutMs, dernierMs;
    /** Tirs de la série, total reçu, total avant armure, tir où les 20 PV sont atteints (0 : pas encore). */
    static int tirs;
    static float recu, arme;
    static int mortAu;
    /** Tir en cours (plombs d'une décharge) : sa clé, ses plombs, ses dégâts, plafond atteint. */
    static long tirCle = -1L;
    static int plombs;
    static float tirRecu, tirArme;
    static boolean tirPlafond;
    /** Dernier coup reçu. */
    static PaquetsCible.Touche derniere;

    /** Ajoute un coup ; rend vrai s'il commence un nouveau tir. */
    static boolean ajouter(PaquetsCible.Touche t, long maintenant) {
        if (active && (t.entite() != entite || !t.tenue().equals(tenue) || t.casque() != casque || maintenant - dernierMs > FIN_MS)) {
            active = false;
        }
        if (!active) {
            active = true;
            entite = t.entite();
            tenue = t.tenue();
            casque = t.casque();
            debutMs = maintenant;
            tirs = 0;
            recu = arme = 0.0F;
            mortAu = 0;
            tirCle = -1L;
        }
        boolean nouveau = t.tir() < 0 || t.tir() != tirCle || tirs == 0;
        if (nouveau) {
            tirs++;
            tirCle = t.tir();
            plombs = 0;
            tirRecu = tirArme = 0.0F;
            tirPlafond = false;
        }
        if (!t.plafond()) plombs++;
        tirPlafond |= t.plafond();
        tirRecu += t.recu();
        tirArme += t.arme();
        recu += t.recu();
        arme += t.arme();
        if (mortAu == 0 && recu >= CibleEssai.PV_JOUEUR) mortAu = tirs;
        dernierMs = maintenant;
        derniere = t;
        return nouveau;
    }

    /** Tirs qu'il faudrait pour tuer un joueur avec des tirs comme le dernier (0 : le dernier n'a rien fait). */
    static int tirsPourTuer() {
        return tirRecu <= 0.0F ? 0 : (int) Math.ceil(CibleEssai.PV_JOUEUR / tirRecu);
    }
}
