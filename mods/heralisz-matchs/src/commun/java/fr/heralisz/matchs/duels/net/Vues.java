package fr.heralisz.matchs.duels.net;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vues envoyees au client par les duels (donnees d'ecran, immuables). Chaque vue sait s'ecrire et se relire (chaines et
 * listes bornees).
 */
public final class Vues {
    private Vues() {
    }

    /** Modes de duel (identifiants du JSON de carte et des commandes). */
    public static final String MODE_RD = "recherche_destruction", MODE_MAM = "match_a_mort";
    /** Formats. */
    public static final String FORMAT_1V1 = "1v1", FORMAT_2V2 = "2v2";

    // ------------------------------------------------------------------ ecran DUEL
    /** Joueur que l'on peut defier. */
    public record Candidat(UUID id, String nom, boolean groupeDe2, boolean occupe, boolean memeGroupe) {
        void write(FriendlyByteBuf b) {
            b.writeUUID(id);
            b.writeUtf(nom, 32);
            b.writeBoolean(groupeDe2);
            b.writeBoolean(occupe);
            b.writeBoolean(memeGroupe);
        }

        static Candidat read(FriendlyByteBuf b) {
            return new Candidat(b.readUUID(), b.readUtf(32), b.readBoolean(), b.readBoolean(), b.readBoolean());
        }
    }

    /** Carte de duel proposee. */
    public record CarteVue(String id, String nomFr, String nomEn, List<String> modes, List<String> formats) {
        public String nom(boolean fr) {
            return fr ? nomFr : nomEn;
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 64);
            b.writeUtf(nomFr, 64);
            b.writeUtf(nomEn, 64);
            chaines(b, modes);
            chaines(b, formats);
        }

        static CarteVue read(FriendlyByteBuf b) {
            return new CarteVue(b.readUtf(64), b.readUtf(64), b.readUtf(64), chaines(b), chaines(b));
        }
    }

    /** Statistiques de duel d'un joueur. */
    public record StatsVue(int victoires, int defaites, int eliminations, int morts, int tetes, int parties) {
        public static final StatsVue ZERO = new StatsVue(0, 0, 0, 0, 0, 0);

        void write(FriendlyByteBuf b) {
            b.writeVarInt(victoires);
            b.writeVarInt(defaites);
            b.writeVarInt(eliminations);
            b.writeVarInt(morts);
            b.writeVarInt(tetes);
            b.writeVarInt(parties);
        }

        static StatsVue read(FriendlyByteBuf b) {
            return new StatsVue(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
    }

    /** Defi en attente (entrant ou sortant). */
    public record DefiVue(int id, String defiant, String defie, String format, String mode, String carte, long resteMs, List<String> campA,
                          List<String> campB) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeUtf(defiant, 32);
            b.writeUtf(defie, 32);
            b.writeUtf(format, 8);
            b.writeUtf(mode, 32);
            b.writeUtf(carte, 64);
            b.writeVarLong(Math.max(0, resteMs));
            chaines(b, campA);
            chaines(b, campB);
        }

        static DefiVue read(FriendlyByteBuf b) {
            return new DefiVue(b.readVarInt(), b.readUtf(32), b.readUtf(32), b.readUtf(8), b.readUtf(32), b.readUtf(64), b.readVarLong(), chaines(b), chaines(b));
        }
    }

    /** Contenu de l'ecran DUEL. */
    public record EcranDuel(List<Candidat> candidats, int tailleGroupe, List<CarteVue> cartes, StatsVue stats, @Nullable DefiVue entrant,
                            @Nullable DefiVue sortant, @Nullable UUID preselection) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(candidats.size());
            for (Candidat c : candidats) c.write(b);
            b.writeVarInt(tailleGroupe);
            b.writeVarInt(cartes.size());
            for (CarteVue c : cartes) c.write(b);
            stats.write(b);
            b.writeBoolean(entrant != null);
            if (entrant != null) entrant.write(b);
            b.writeBoolean(sortant != null);
            if (sortant != null) sortant.write(b);
            b.writeBoolean(preselection != null);
            if (preselection != null) b.writeUUID(preselection);
        }

        public static EcranDuel read(FriendlyByteBuf b) {
            int n = Math.min(512, b.readVarInt());
            List<Candidat> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(Candidat.read(b));
            int tg = b.readVarInt();
            int nc = Math.min(32, b.readVarInt());
            List<CarteVue> cartes = new ArrayList<>(nc);
            for (int i = 0; i < nc; i++) cartes.add(CarteVue.read(b));
            StatsVue s = StatsVue.read(b);
            DefiVue in = b.readBoolean() ? DefiVue.read(b) : null;
            DefiVue out = b.readBoolean() ? DefiVue.read(b) : null;
            UUID pre = b.readBoolean() ? b.readUUID() : null;
            return new EcranDuel(l, tg, cartes, s, in, out, pre);
        }
    }

    // ------------------------------------------------------------------ recherche et destruction
    /** Etats de la charge. */
    public static final int CHARGE_AUCUNE = 0, CHARGE_PORTEE = 1, CHARGE_AU_SOL = 2, CHARGE_POSEE = 3, CHARGE_DESAMORCEE = 4, CHARGE_EXPLOSEE = 5;
    /** Actions en cours du joueur qui recoit la vue. */
    public static final int ACTION_AUCUNE = 0, ACTION_POSE = 1, ACTION_DESAMORCAGE = 2;

    /**
     * Etat de la manche de recherche et destruction vu par un joueur.
     *
     * @param attaque     le joueur est dans l'equipe qui attaque
     * @param etat        {@code CHARGE_*}
     * @param porteur     nom du porteur (vide si aucun)
     * @param moiPorteur  le joueur porte la charge
     * @param site        site de pose ("A", "B", vide)
     * @param x           position de la charge (au sol ou posee)
     * @param resteTicks  minuterie restante (charge posee), decomptee par le client
     * @param action      {@code ACTION_*} du joueur
     * @param faitTicks   progression de l'action au moment de l'envoi
     * @param totalTicks  duree totale de l'action
     * @param invite      invite affichee ("" = aucune) : "poser", "desamorcer", "ramasser"
     * @param sites       centres des sites A et B (x, y, z, x, y, z) pour les reperes a l'ecran
     */
    public record RdVue(boolean attaque, int etat, String porteur, boolean moiPorteur, String site, double x, double y, double z, int resteTicks,
                        int action, int faitTicks, int totalTicks, String invite, double[] sites) {
        public static final RdVue AUCUNE = new RdVue(false, CHARGE_AUCUNE, "", false, "", 0, 0, 0, 0, ACTION_AUCUNE, 0, 1, "", new double[6]);

        public void write(FriendlyByteBuf b) {
            b.writeBoolean(attaque);
            b.writeVarInt(etat);
            b.writeUtf(porteur, 32);
            b.writeBoolean(moiPorteur);
            b.writeUtf(site, 4);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeVarInt(Math.max(0, resteTicks));
            b.writeVarInt(action);
            b.writeVarInt(Math.max(0, faitTicks));
            b.writeVarInt(Math.max(1, totalTicks));
            b.writeUtf(invite, 16);
            for (int i = 0; i < 6; i++) b.writeDouble(i < sites.length ? sites[i] : 0);
        }

        public static RdVue read(FriendlyByteBuf b) {
            boolean att = b.readBoolean();
            int etat = b.readVarInt();
            String porteur = b.readUtf(32);
            boolean moi = b.readBoolean();
            String site = b.readUtf(4);
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            int reste = b.readVarInt(), action = b.readVarInt(), fait = b.readVarInt(), total = b.readVarInt();
            String invite = b.readUtf(16);
            double[] sites = new double[6];
            for (int i = 0; i < 6; i++) sites[i] = b.readDouble();
            return new RdVue(att, etat, porteur, moi, site, x, y, z, reste, action, fait, total, invite, sites);
        }
    }

    // ------------------------------------------------------------------ tableau des scores
    /** Ligne d'equipe du tableau final. */
    public record EquipeScore(String id, String nom, int couleur, int score) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 16);
            b.writeUtf(nom, 64);
            b.writeInt(couleur);
            b.writeVarInt(score);
        }

        static EquipeScore read(FriendlyByteBuf b) {
            return new EquipeScore(b.readUtf(16), b.readUtf(64), b.readInt(), b.readVarInt());
        }
    }

    /** Ligne de joueur du tableau final. */
    public record JoueurScore(UUID id, String nom, String equipe, int eliminations, int morts, int tetes, boolean bot) {
        public double ratio() {
            return morts == 0 ? eliminations : eliminations / (double) morts;
        }

        void write(FriendlyByteBuf b) {
            b.writeUUID(id);
            b.writeUtf(nom, 32);
            b.writeUtf(equipe, 16);
            b.writeVarInt(eliminations);
            b.writeVarInt(morts);
            b.writeVarInt(tetes);
            b.writeBoolean(bot);
        }

        static JoueurScore read(FriendlyByteBuf b) {
            return new JoueurScore(b.readUUID(), b.readUtf(32), b.readUtf(16), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean());
        }
    }

    /**
     * Tableau des scores de fin de duel.
     *
     * @param mode      {@link #MODE_RD} ou {@link #MODE_MAM}
     * @param gagnante  id de l'equipe gagnante ("" = aucune : annule ou egalite)
     * @param manches   R&D : id de l'equipe gagnante de chaque manche, dans l'ordre
     * @param objectif  objectif du mode (manches ou eliminations)
     * @param dureeS    duree du duel en secondes
     */
    public record Tableau(String mode, String format, String carte, String gagnante, List<EquipeScore> equipes, List<JoueurScore> joueurs,
                          List<String> manches, int objectif, int dureeS, boolean entrainement) {
        public void write(FriendlyByteBuf b) {
            b.writeUtf(mode, 32);
            b.writeUtf(format, 8);
            b.writeUtf(carte, 64);
            b.writeUtf(gagnante, 16);
            b.writeVarInt(equipes.size());
            for (EquipeScore e : equipes) e.write(b);
            b.writeVarInt(joueurs.size());
            for (JoueurScore j : joueurs) j.write(b);
            chaines(b, manches);
            b.writeVarInt(objectif);
            b.writeVarInt(dureeS);
            b.writeBoolean(entrainement);
        }

        public static Tableau read(FriendlyByteBuf b) {
            String mode = b.readUtf(32), format = b.readUtf(8), carte = b.readUtf(64), gagnante = b.readUtf(16);
            int ne = Math.min(4, b.readVarInt());
            List<EquipeScore> eq = new ArrayList<>(ne);
            for (int i = 0; i < ne; i++) eq.add(EquipeScore.read(b));
            int nj = Math.min(16, b.readVarInt());
            List<JoueurScore> j = new ArrayList<>(nj);
            for (int i = 0; i < nj; i++) j.add(JoueurScore.read(b));
            return new Tableau(mode, format, carte, gagnante, eq, j, chaines(b), b.readVarInt(), b.readVarInt(), b.readBoolean());
        }
    }

    // ------------------------------------------------------------------ aides
    static void chaines(FriendlyByteBuf b, List<String> l) {
        int n = Math.min(32, l.size());
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeUtf(l.get(i), 64);
    }

    static List<String> chaines(FriendlyByteBuf b) {
        int n = Math.min(32, b.readVarInt());
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(b.readUtf(64));
        return l;
    }
}
