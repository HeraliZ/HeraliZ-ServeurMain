package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Arbre;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Mouvement;

/**
 * Remboursement, une seule fois au demarrage, de l'ancienne amelioration « Membres » de l'arbre (achetee du 25/10 au
 * 31/10). Depuis le 31/10 (decision du proprietaire), les places de membres sont donnees par le niveau du clan seul
 * ({@link Arbre#membresMax(int)}) : la branche est retiree de l'arbre.
 * <ul>
 * <li>Points de competence : rendus d'eux-memes (la branche ne compte plus dans {@link Arbre#pointsDepenses}).</li>
 * <li>Herals : rendus a la banque du clan, palier par palier : le prix vraiment paye s'il est encore dans le journal de
 * la banque (motif {@code arbre:membres:N}), sinon le prix de base du palier sans facteur ({@link Arbre#HERALS_MEMBRES},
 * le moins qu'il ait pu couter) : jamais plus que ce qui a ete paye. Banque plafonnee comme partout.</li>
 * <li>Membres : rien n'est retire ; un clan au-dessus du maximum de son niveau garde tout le monde et n'invite plus.</li>
 * </ul>
 * Une seule fois : {@link Clan#membresARembourser} repasse a 0 dans le meme fichier que la banque (un arret avant la
 * sauvegarde relit l'ancien fichier, banque comprise : rien n'est rendu deux fois). Journal de la banque, journal du clan
 * et journal du serveur gardent la trace.
 */
final class MigrationMembres {
    private MigrationMembres() {
    }

    /** Prix paye pour le palier p, d'apres le journal de la banque, ou -1 s'il n'y est plus. */
    static long prixDuJournal(Clan c, int palier) {
        String motif = "arbre:membres:" + palier;
        for (Mouvement m : c.journalBanque) {
            if (motif.equals(m.motif()) && m.montant() < 0) return -m.montant();
        }
        return -1;
    }

    /** Rembourse les clans qui avaient achete des paliers Membres ; renvoie le nombre de clans rembourses. */
    static int migrer(ClanStore store) {
        int n = 0;
        long maintenant = System.currentTimeMillis();
        for (Clan c : store.tous()) {
            int paliers = Math.min(Arbre.PALIERS_MEMBRES, c.membresARembourser);
            if (paliers <= 0) {
                c.membresARembourser = 0;
                continue;
            }
            long total = 0;
            int points = 0;
            for (int p = 1; p <= paliers; p++) {
                long paye = prixDuJournal(c, p);
                total += paye >= 0 ? paye : Arbre.HERALS_MEMBRES[p - 1];
                points += Arbre.coutPoints(p);
            }
            long avant = c.banque;
            c.banque = ClanService.plafonnerBanque(c.banque + total);
            long rendu = c.banque - avant;
            c.membresARembourser = 0;
            if (rendu > 0) c.ajouterMouvement(new Mouvement(maintenant, null, "", rendu, "remboursement_membres"));
            c.ajouterEvenement(new Evenement(maintenant, Evenement.Genre.PERSO, "evenement.membres_rembourses",
                    String.valueOf(points), String.valueOf(rendu)));
            HeraliszClans.LOG.info("[clans] [{}] amelioration Membres retiree de l'arbre : {} palier(s), {} point(s) rendus, {} herals"
                    + " rendus a la banque ({} -> {}) ; membres {} pour {} au niveau {}", c.tag, paliers, points, rendu, avant, c.banque,
                    c.membres.size(), c.membresMax(), c.niveau);
            n++;
        }
        if (n > 0) {
            store.marquerDirty();
            HeraliszClans.LOG.info("[clans] {} clan(s) rembourse(s) de l'amelioration Membres (places donnees par le niveau)", n);
        }
        return n;
    }
}
