package fr.heralisz.matchs.clashwar;

import fr.heralisz.clans.model.Banniere;
import fr.heralisz.matchs.HeraliszMatchs;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tableau a elimination directe du ClashWar (MATCHS_SPEC §2.3) : tetes de serie au hasard, exemptions (« byes ») pour
 * completer a la puissance de 2, deux equipes d'un meme clan separees au premier tour si possible. Les exemptions sont
 * reparties regulierement sur tout le tableau (jamais deux exemptions face a face). Logique pure, sans Minecraft :
 * le {@link Tournoi} l'alimente et la diffuse.
 */
public final class Tableau {
    /** Equipe engagee : clan, numero dans le clan, joueurs (noms gardes pour l'affichage). */
    public static final class EquipeT {
        public final int index;
        public final UUID clan;
        public final String tag, nomClan;
        public final int numero;
        public final int couleur;
        public final Banniere banniere;
        public final List<UUID> joueurs;
        public final Map<UUID, String> noms;
        /** Vrai si le clan engage plusieurs equipes (le numero est alors affiche). */
        public boolean numeroVisible;
        /** Eliminee du tournoi. */
        public boolean eliminee;

        public EquipeT(int index, UUID clan, String tag, String nomClan, int numero, int couleur, Banniere banniere,
                       List<UUID> joueurs, Map<UUID, String> noms) {
            this.index = index;
            this.clan = clan;
            this.tag = tag;
            this.nomClan = nomClan;
            this.numero = numero;
            this.couleur = couleur & 0xFFFFFF;
            this.banniere = banniere;
            this.joueurs = new ArrayList<>(joueurs);
            this.noms = new LinkedHashMap<>(noms);
        }

        /** « [NOVA] » ou « [NOVA] 2 ». */
        public String libelle() {
            return numeroVisible ? "[" + tag + "] " + numero : "[" + tag + "]";
        }

        /** Nom d'un joueur de l'equipe (ou l'UUID abrege). */
        public String nom(UUID u) {
            String n = noms.get(u);
            return n != null ? n : u.toString().substring(0, 8);
        }

        @Override
        public String toString() {
            return libelle() + joueurs.size();
        }
    }

    public enum Statut { ATTENTE, EN_COURS, TERMINEE, EXEMPT }

    /** Rencontre d'un tour : deux equipes (index dans {@link #equipes()}, -1 = pas encore connue ou exemption). */
    public static final class Rencontre {
        public final int tour, index;
        public int a = -1, b = -1, gagnant = -1;
        public Statut statut = Statut.ATTENTE;
        /** Joueurs en vie / engages pendant le match (affichage en direct). */
        public int vivantsA, totalA, vivantsB, totalB;
        /** Gagnee par forfait. */
        public boolean forfait;

        Rencontre(int tour, int index) {
            this.tour = tour;
            this.index = index;
        }

        public boolean jouable() {
            return statut == Statut.ATTENTE && a >= 0 && b >= 0;
        }

        public boolean finie() {
            return statut == Statut.TERMINEE || statut == Statut.EXEMPT;
        }

        /** Index de l'equipe perdante, ou -1. */
        public int perdant() {
            if (gagnant < 0 || statut == Statut.EXEMPT) return -1;
            return gagnant == a ? b : a;
        }
    }

    private final List<EquipeT> equipes;
    private final List<List<Rencontre>> tours = new ArrayList<>();
    private final int places;

    private Tableau(List<EquipeT> equipes, int places) {
        this.equipes = equipes;
        this.places = places;
    }

    /**
     * Construit le tableau (au moins 2 equipes) : ordre au hasard, exemptions reparties, puis echanges pour separer les
     * equipes d'un meme clan au premier tour quand c'est possible. Les exemptions sont resolues tout de suite.
     */
    public static Tableau construire(List<EquipeT> equipesEntree, Random rnd) {
        int n = equipesEntree.size();
        if (n < 2) throw new IllegalArgumentException("au moins 2 equipes");
        int p = 2;
        while (p < n) p <<= 1;
        List<EquipeT> equipes = List.copyOf(equipesEntree);
        // numero visible seulement si le clan engage plusieurs equipes
        for (EquipeT e : equipes) {
            int meme = 0;
            for (EquipeT f : equipes) if (f.clan.equals(e.clan)) meme++;
            e.numeroVisible = meme > 1;
        }
        Tableau t = new Tableau(equipes, p);
        int nbTours = Integer.numberOfTrailingZeros(p);
        for (int tour = 0; tour < nbTours; tour++) {
            int nb = p >> (tour + 1);
            List<Rencontre> l = new ArrayList<>(nb);
            for (int i = 0; i < nb; i++) l.add(new Rencontre(tour, i));
            t.tours.add(l);
        }
        int paires = p / 2, exempts = p - n;
        boolean[] exempte = new boolean[paires];
        for (int k = 0; k < exempts; k++) exempte[(int) ((k + 0.5) * paires / exempts)] = true;
        List<Integer> ordre = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ordre.add(i);
        Collections.shuffle(ordre, rnd);
        int[] places = new int[p];
        int k = 0;
        for (int i = 0; i < paires; i++) {
            places[2 * i] = ordre.get(k++);
            places[2 * i + 1] = exempte[i] ? -1 : ordre.get(k++);
        }
        t.separerClans(places);
        for (int i = 0; i < paires; i++) {
            Rencontre r = t.tours.get(0).get(i);
            r.a = places[2 * i];
            r.b = places[2 * i + 1];
            if (r.b < 0) {
                r.statut = Statut.EXEMPT;
                t.avancer(r, r.a);
            }
        }
        HeraliszMatchs.LOG.info("[clashwar] tableau : {} equipes, {} places, {} exemptions, premier tour {}", n, p, exempts, t.premierTour());
        return t;
    }

    /** Echanges pour qu'aucune paire du premier tour n'oppose deux equipes du meme clan (si c'est possible). */
    private void separerClans(int[] places) {
        int paires = places.length / 2;
        for (int passe = 0; passe < 3; passe++) {
            boolean propre = true;
            for (int i = 0; i < paires; i++) {
                if (!conflit(places, i)) continue;
                propre = false;
                int s = 2 * i + 1;
                for (int q = 0; q < places.length; q++) {
                    if (q / 2 == i || places[q] < 0) continue;
                    int tmp = places[s];
                    places[s] = places[q];
                    places[q] = tmp;
                    if (!conflit(places, i) && !conflit(places, q / 2)) break;
                    places[q] = places[s];
                    places[s] = tmp;
                }
            }
            if (propre) return;
        }
    }

    private boolean conflit(int[] places, int paire) {
        int a = places[2 * paire], b = places[2 * paire + 1];
        return a >= 0 && b >= 0 && equipes.get(a).clan.equals(equipes.get(b).clan);
    }

    private String premierTour() {
        StringBuilder sb = new StringBuilder();
        for (Rencontre r : tours.get(0)) {
            sb.append(r.a >= 0 ? equipes.get(r.a).libelle() : "-").append(" v ").append(r.b >= 0 ? equipes.get(r.b).libelle() : "exempt").append(" ; ");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ deroule
    /** Fixe le gagnant d'une rencontre et le place dans la rencontre suivante. */
    public void avancer(Rencontre r, int gagnant) {
        r.gagnant = gagnant;
        if (r.statut != Statut.EXEMPT) r.statut = Statut.TERMINEE;
        int perdant = r.perdant();
        if (perdant >= 0) equipes.get(perdant).eliminee = true;
        if (r.tour + 1 < tours.size()) {
            Rencontre suivante = tours.get(r.tour + 1).get(r.index / 2);
            if (r.index % 2 == 0) suivante.a = gagnant;
            else suivante.b = gagnant;
        }
    }

    /** Premier tour qui a encore une rencontre non finie ({@link #nbTours()} si tout est fini). */
    public int tourCourant() {
        for (int t = 0; t < tours.size(); t++) {
            for (Rencontre r : tours.get(t)) if (!r.finie()) return t;
        }
        return tours.size();
    }

    public boolean termine() {
        return finale().finie();
    }

    /** Index de l'equipe championne, ou -1. */
    public int champion() {
        return finale().gagnant;
    }

    public Rencontre finale() {
        return tours.get(tours.size() - 1).get(0);
    }

    public int nbTours() {
        return tours.size();
    }

    public List<Rencontre> tour(int t) {
        return tours.get(t);
    }

    public List<List<Rencontre>> tours() {
        return tours;
    }

    public List<EquipeT> equipes() {
        return equipes;
    }

    public EquipeT equipe(int i) {
        return equipes.get(i);
    }

    /** Taille du tableau (puissance de 2). */
    public int places() {
        return places;
    }

    /** Rencontre ou figure l'equipe dans ce tour, ou null. */
    @Nullable
    public Rencontre rencontreDe(int tour, int equipe) {
        if (tour < 0 || tour >= tours.size()) return null;
        for (Rencontre r : tours.get(tour)) if (r.a == equipe || r.b == equipe) return r;
        return null;
    }

    /**
     * Cle du nom du tour selon le nombre de rencontres : finale, demi, quart, huitieme, seizieme, sinon tour (suffixe
     * de {@code clashwar.tour.*}).
     */
    public String cleTour(int tour) {
        int nb = places >> (tour + 1);
        return switch (nb) {
            case 1 -> "finale";
            case 2 -> "demi";
            case 4 -> "quart";
            case 8 -> "huitieme";
            case 16 -> "seizieme";
            default -> "tour";
        };
    }
}
