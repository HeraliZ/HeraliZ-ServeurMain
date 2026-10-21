package fr.heralisz.matchs.clashwar.net;

import fr.heralisz.clans.model.Banniere;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Etat du ClashWar tel que le voient les clients : le mur-ecran de la salle d'attente, l'ecran {@code /clashwar tableau}
 * et le tableau d'accueil entre deux editions (prochaine date, dernier champion). Diffuse a tous les joueurs a chaque
 * changement de structure ({@code TableauS2C}) ; les compteurs en direct d'une rencontre passent par
 * {@code RencontreS2C} (patch de {@link Rencontre}). Toutes les listes lues sont bornees.
 *
 * @param edition          numero de l'edition (0 = aucune encore)
 * @param format           "3v3"
 * @param etat             etape du tournoi
 * @param finInscriptionMs heure de cloture des inscriptions (epoch ms, horloge du serveur)
 * @param prochaineMs      prochaine edition planifiee (epoch ms), 0 = inconnue
 * @param prochainFormat   format de la prochaine edition
 * @param clansInscrits    clans inscrits (phase d'inscription)
 * @param equipesInscrites equipes inscrites (phase d'inscription)
 * @param tourCourant      tour en cours (index), = nombre de tours si tout est fini
 * @param equipes          equipes engagees (index = celui des rencontres)
 * @param tours            rencontres par tour (tour 0 = premier tour)
 * @param champion         champion (edition finie) ou dernier champion connu, null = aucun
 * @param championEdition  edition du champion affiche
 */
public record VueTableau(int edition, String format, Etat etat, long finInscriptionMs, long prochaineMs, String prochainFormat,
                         int clansInscrits, int equipesInscrites, int tourCourant, List<Equipe> equipes, List<List<Rencontre>> tours,
                         @Nullable Equipe champion, int championEdition) {
    public static final int MAX_EQUIPES = 64, MAX_JOUEURS = 4;

    public enum Etat { AUCUN, INSCRIPTION, PREPARATION, EN_COURS, TERMINE, ANNULE }

    /** Vue vide (aucune edition, rien de planifie). */
    public static final VueTableau VIDE = new VueTableau(0, "", Etat.AUCUN, 0, 0, "", 0, 0, 0, List.of(), List.of(), null, 0);

    /** Equipe affichee. */
    public record Equipe(String libelle, String tag, String nomClan, int couleur, Banniere banniere, List<String> joueurs,
                         List<UUID> uuids, boolean eliminee) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(libelle, 32);
            buf.writeUtf(tag, 16);
            buf.writeUtf(nomClan, 48);
            buf.writeInt(couleur);
            banniere.write(buf);
            int n = Math.min(MAX_JOUEURS, joueurs.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeUtf(joueurs.get(i), 24);
                buf.writeUUID(i < uuids.size() ? uuids.get(i) : new UUID(0, 0));
            }
            buf.writeBoolean(eliminee);
        }

        static Equipe read(FriendlyByteBuf buf) {
            String libelle = buf.readUtf(32), tag = buf.readUtf(16), nom = buf.readUtf(48);
            int couleur = buf.readInt();
            Banniere b = Banniere.read(buf);
            int n = Math.min(MAX_JOUEURS, buf.readVarInt());
            List<String> j = new ArrayList<>(n);
            List<UUID> u = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                j.add(buf.readUtf(24));
                u.add(buf.readUUID());
            }
            return new Equipe(libelle, tag, nom, couleur, b, j, u, buf.readBoolean());
        }
    }

    /** Rencontre affichee (mutable cote client : les compteurs en direct la modifient). */
    public static final class Rencontre {
        public int a, b, gagnant, statut, vivantsA, totalA, vivantsB, totalB;
        public boolean forfait;

        public Rencontre(int a, int b, int gagnant, int statut, int vivantsA, int totalA, int vivantsB, int totalB, boolean forfait) {
            this.a = a;
            this.b = b;
            this.gagnant = gagnant;
            this.statut = statut;
            this.vivantsA = vivantsA;
            this.totalA = totalA;
            this.vivantsB = vivantsB;
            this.totalB = totalB;
            this.forfait = forfait;
        }

        /** Statuts : 0 attente, 1 en cours, 2 terminee, 3 exemption (ordinal de {@code Tableau.Statut}). */
        public boolean enCours() {
            return statut == 1;
        }

        public boolean finie() {
            return statut >= 2;
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(a + 1);
            buf.writeVarInt(b + 1);
            buf.writeVarInt(gagnant + 1);
            buf.writeByte(statut);
            buf.writeByte(vivantsA);
            buf.writeByte(totalA);
            buf.writeByte(vivantsB);
            buf.writeByte(totalB);
            buf.writeBoolean(forfait);
        }

        static Rencontre read(FriendlyByteBuf buf) {
            return new Rencontre(buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readByte(), buf.readByte(),
                    buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(edition);
        buf.writeUtf(format, 8);
        buf.writeEnum(etat);
        buf.writeLong(finInscriptionMs);
        buf.writeLong(prochaineMs);
        buf.writeUtf(prochainFormat, 8);
        buf.writeVarInt(clansInscrits);
        buf.writeVarInt(equipesInscrites);
        buf.writeVarInt(tourCourant);
        int ne = Math.min(MAX_EQUIPES, equipes.size());
        buf.writeVarInt(ne);
        for (int i = 0; i < ne; i++) equipes.get(i).write(buf);
        int nt = Math.min(7, tours.size());
        buf.writeVarInt(nt);
        for (int t = 0; t < nt; t++) {
            List<Rencontre> l = tours.get(t);
            int nr = Math.min(MAX_EQUIPES / 2, l.size());
            buf.writeVarInt(nr);
            for (int i = 0; i < nr; i++) l.get(i).write(buf);
        }
        buf.writeBoolean(champion != null);
        if (champion != null) champion.write(buf);
        buf.writeVarInt(championEdition);
    }

    public static VueTableau read(FriendlyByteBuf buf) {
        int edition = buf.readVarInt();
        String format = buf.readUtf(8);
        Etat etat = buf.readEnum(Etat.class);
        long fin = buf.readLong(), prochaine = buf.readLong();
        String prochainFormat = buf.readUtf(8);
        int clans = buf.readVarInt(), eqIns = buf.readVarInt(), tour = buf.readVarInt();
        int ne = Math.min(MAX_EQUIPES, buf.readVarInt());
        List<Equipe> equipes = new ArrayList<>(ne);
        for (int i = 0; i < ne; i++) equipes.add(Equipe.read(buf));
        int nt = Math.min(7, buf.readVarInt());
        List<List<Rencontre>> tours = new ArrayList<>(nt);
        for (int t = 0; t < nt; t++) {
            int nr = Math.min(MAX_EQUIPES / 2, buf.readVarInt());
            List<Rencontre> l = new ArrayList<>(nr);
            for (int i = 0; i < nr; i++) l.add(Rencontre.read(buf));
            tours.add(l);
        }
        Equipe champion = buf.readBoolean() ? Equipe.read(buf) : null;
        return new VueTableau(edition, format, etat, fin, prochaine, prochainFormat, clans, eqIns, tour, equipes, tours, champion,
                buf.readVarInt());
    }

    /** Rencontre (tour, index) ou null. */
    @Nullable
    public Rencontre rencontre(int tour, int index) {
        if (tour < 0 || tour >= tours.size()) return null;
        List<Rencontre> l = tours.get(tour);
        return index < 0 || index >= l.size() ? null : l.get(index);
    }

    /** Match pour la 3e place (index 1 du dernier tour), ou null. */
    @Nullable
    public Rencontre troisieme() {
        return tours.isEmpty() ? null : rencontre(tours.size() - 1, 1);
    }

    /** Cle du nom du tour (comme {@code Tableau.cleTour}) d'apres le nombre de rencontres. */
    public String cleTour(int tour) {
        if (tour < 0 || tour >= tours.size()) return "tour";
        if (tour == tours.size() - 1) return "finale";
        return switch (tours.get(tour).size()) {
            case 1 -> "finale";
            case 2 -> "demi";
            case 4 -> "quart";
            case 8 -> "huitieme";
            case 16 -> "seizieme";
            default -> "tour";
        };
    }
}
