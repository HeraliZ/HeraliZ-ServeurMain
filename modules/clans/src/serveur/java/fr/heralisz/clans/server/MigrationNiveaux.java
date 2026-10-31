package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Arbre;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Niveaux;

/**
 * Passage des clans d'avant le 31/10 sur la nouvelle courbe des niveaux ({@link Niveaux}), une seule fois, au demarrage.
 * <p>
 * Choix (31/10) : chaque clan garde son XP totale, comptee sur l'ancienne courbe (20 x n^1,5 x 3, celle en place depuis le
 * 28/10) ; son niveau est recalcule sur la nouvelle avec cette XP, comme pour un clan neuf qui aurait gagne autant. Rien
 * n'est retire : les paliers deja achetes dans l'arbre restent (et leurs effets : membres, base, TP...) ; si le clan a
 * depense plus de points que son nouveau niveau n'en donne, il n'a simplement plus de point libre tant que ses niveaux
 * suivants n'ont pas comble l'ecart ({@link Clan#pointsLibres()} ne descend jamais sous 0). Les membres deja au-dela du
 * maximum restent. Le niveau et l'XP d'avant sont gardes dans le clan ({@code niveauAvantCourbe}) et dans le journal du
 * serveur ; le clan en est prevenu par une ligne de son journal (onglet Apercu).
 */
final class MigrationNiveaux {
    private MigrationNiveaux() {
    }

    /** XP pour passer du niveau n au suivant sur l'ancienne courbe (28/10 : arrondi10(20 x n^1,5) x 3). */
    static long ancienPalier(int n) {
        return Math.round(20 * Math.pow(n, 1.5) / 10.0) * 10 * 3;
    }

    /** XP totale d'un clan sur l'ancienne courbe. */
    static long ancienneXpTotale(int niveau, long xp) {
        long t = Math.max(0, xp);
        for (int n = 1; n < Math.max(1, Math.min(Niveaux.MAX, niveau)); n++) t += ancienPalier(n);
        return t;
    }

    /** Recalcule les clans encore sur l'ancienne courbe ; renvoie le nombre de clans passes sur la nouvelle. */
    static int migrer(ClanStore store) {
        int n = 0;
        for (Clan c : store.tous()) {
            if (c.courbe >= Clan.COURBE_ACTUELLE) continue;
            int avant = c.niveau;
            long xpAvant = c.xp;
            long total = ancienneXpTotale(c.niveau, c.xp);
            int niveau = 1;
            long reste = total;
            while (niveau < Niveaux.MAX) {
                long besoin = Niveaux.xpPour(niveau);
                if (besoin <= 0 || reste < besoin) break;
                reste -= besoin;
                niveau++;
            }
            c.niveauAvantCourbe = avant;
            c.xpAvantCourbe = xpAvant;
            c.niveau = niveau;
            c.xp = reste;
            c.courbe = Clan.COURBE_ACTUELLE;
            c.courbeLe = System.currentTimeMillis();
            int depenses = Arbre.pointsDepenses(c.arbre);
            if (avant > 1) {
                c.ajouterEvenement(new Evenement(c.courbeLe, Evenement.Genre.PERSO, "evenement.courbe_niveaux",
                        String.valueOf(avant), String.valueOf(niveau)));
            }
            HeraliszClans.LOG.info("[clans] [{}] nouvelle courbe des niveaux : niveau {} ({} XP) -> niveau {} ({} XP), XP totale {} ;"
                            + " points de l'arbre deja depenses {} (gardes), points du nouveau niveau {}", c.tag, avant, xpAvant, niveau, reste,
                    total, depenses, Arbre.pointsGagnes(niveau));
            n++;
        }
        if (n > 0) {
            store.marquerDirty();
            HeraliszClans.LOG.info("[clans] {} clan(s) passe(s) sur la nouvelle courbe des niveaux (XP totale gardee, arbres gardes)", n);
        }
        return n;
    }
}
