package fr.heraliz.banc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayDeque;

/**
 * Rythmes réalistes du banc (01/11) : un vrai serveur de 150 joueurs ne voit pas 70 morts par minute (premier banc :
 * 345 morts en 5 min pour 85 bots, chacune suivie d'une reconnexion). {@code /bots rythmes <morts> <connexions>
 * <voyages> <combat>} : cibles PAR MINUTE POUR 150 JOUEURS (ramenées au nombre de bots), et part du temps (%) où chaque
 * zone se bat ({@link Zones}). {@code /bots rythmes off} : comme avant (combat permanent, ni connexions ni voyages).
 * <ul>
 *     <li>morts : les dégâts d'un bot à un autre bot sont multipliés par un facteur (1 au plus), revu toutes les 10 s
 *     pour tenir la cible sur la dernière minute (les tirs, les balles et les touches restent : seul le nombre de morts
 *     baisse) ;</li>
 *     <li>connexions : un bot au calme se déconnecte (sa sauvegarde est gardée) et revient 30 à 90 s plus tard là où il
 *     était, comme un joueur qui quitte et revient ;</li>
 *     <li>voyages : un bot au calme part dans une autre zone (tronçons d'arrivée demandés en tâche de fond d'abord),
 *     comme un {@code /home} ou un {@code /tpa}.</li>
 * </ul>
 * S'y ajoutent les téléportations qui suivent les morts (réapparition au camp, puis borne de départ) et les extractions.
 * Tout est compté ({@link #COMPTES}) : la mesure écrit les rythmes vrais par minute et pour 150 joueurs. Fil serveur.
 */
final class Rythmes {
    private Rythmes() {
    }

    /** Cibles par minute pour 150 joueurs ; part du temps en combat (%). Valeurs par défaut : comportement d'avant. */
    static double morts = 0, connexions = 0, voyages = 0;
    static int combat = 100;
    static boolean actif;
    /** Facteur des dégâts entre bots (1 = dégâts normaux). */
    static double facteur = 1;
    private static double facteurSomme;
    private static long facteurNb;

    static final String[] NOMS = {"morts", "reapparitions", "connexions", "deconnexions", "borne", "voyages", "extractions",
            "retours", "tutoriel", "pics", "autres"};
    static final int MORTS = 0, REAPPARITIONS = 1, CONNEXIONS = 2, DECONNEXIONS = 3, BORNE = 4, VOYAGES = 5, EXTRACTIONS = 6,
            RETOURS = 7, TUTORIEL = 8, PICS = 9, AUTRES = 10;
    /** Parmi {@link #NOMS} : ceux qui sont des téléportations. */
    static final int[] TELEPORTATIONS = {BORNE, VOYAGES, EXTRACTIONS, RETOURS, TUTORIEL, PICS, AUTRES};
    /** Comptes depuis le démarrage, dans l'ordre de {@link #NOMS}. */
    static final long[] COMPTES = new long[NOMS.length];

    private static final ArrayDeque<Integer> MORTS_RECENTES = new ArrayDeque<>();
    private static final RandomSource R = RandomSource.create();

    static void regler(double m, double c, double v, int pct) {
        morts = Math.max(0, m);
        connexions = Math.max(0, c);
        voyages = Math.max(0, v);
        combat = Math.max(0, Math.min(100, pct));
        actif = true;
        facteur = 1;
        MORTS_RECENTES.clear();
    }

    static void couper() {
        actif = false;
        morts = connexions = voyages = 0;
        combat = 100;
        facteur = 1;
    }

    static void compter(int i) {
        COMPTES[i]++;
    }

    static void mort(int tick) {
        COMPTES[MORTS]++;
        MORTS_RECENTES.addLast(tick);
    }

    /** Joueurs simulés ramenés à 150 : 1 pour 150 bots. */
    static double echelle() {
        return Math.max(1, Bots.total()) / 150.0;
    }

    static void tick(MinecraftServer srv, int t) {
        if (!actif) return;
        double e = echelle();
        if (connexions > 0 && R.nextDouble() < connexions * e / 1200.0) Bots.deconnecterAuHasard(srv);
        if (voyages > 0 && R.nextDouble() < voyages * e / 1200.0) Bots.voyageAuHasard(srv);
        if (t % 200 == 0) {
            while (!MORTS_RECENTES.isEmpty() && MORTS_RECENTES.peekFirst() < t - 1200) MORTS_RECENTES.pollFirst();
            double cible = morts * e, vu = MORTS_RECENTES.size();
            if (morts <= 0) {
                facteur = 1;
            } else if (vu > cible * 1.25 + 0.5) {
                facteur = Math.max(0.01, facteur * 0.7);
            } else if (vu < cible * 0.75) {
                facteur = Math.min(1, facteur * 1.25);
            }
            facteurSomme += facteur;
            facteurNb++;
        }
    }

    /** Dégâts d'un bot à un autre bot : multipliés par le facteur (LivingHurtEvent, en dernier). */
    static void degats(LivingHurtEvent e) {
        if (!actif || facteur >= 1 || !Bots.estBot(e.getEntity()) || !Bots.estBot(e.getSource().getEntity())) return;
        e.setAmount((float) (e.getAmount() * facteur));
    }

    /** Facteur moyen depuis le dernier appel (rapport de la mesure), remis à zéro. */
    static double facteurMoyen() {
        double f = facteurNb == 0 ? facteur : facteurSomme / facteurNb;
        facteurSomme = 0;
        facteurNb = 0;
        return f;
    }
}
