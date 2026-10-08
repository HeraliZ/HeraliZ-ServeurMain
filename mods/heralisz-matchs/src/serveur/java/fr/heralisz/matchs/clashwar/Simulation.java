package fr.heralisz.matchs.clashwar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.server.MinecraftServer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Simulation du banc d'essai (MATCHS_SPEC §6) : un tournoi factice sans joueurs, avec des clans inventes (bannieres
 * prises dans les modeles du mod clans), pour verifier le tableau (exemptions, separation des clans), le mur-ecran et
 * l'ecran {@code /clashwar tableau}. Les rencontres sont tranchees par commande ({@code /clashwar banc avancer}).
 * Reserve aux operateurs ; jamais pendant une vraie edition.
 */
public final class Simulation {
    private Simulation() {
    }

    private static final String[][] CLANS = {
            {"NOVA", "Nova", "Vortex", "Dziga", "Kali", "Orion"},
            {"RVN", "Ravens", "Corbo", "Nyx", "Sable", "Morne"},
            {"KRK", "Kraken", "Abysse", "Tako", "Remous", "Grele"},
            {"ARS", "Arsenal", "Mortier", "Obus", "Culasse", "Silex"},
            {"LYS", "Lys d'or", "Iris", "Lilas", "Azur", "Opale"},
            {"WLF", "Wolfpack", "Fenrir", "Akela", "Loupio", "Grise"},
            {"STX", "Styx", "Charon", "Lethe", "Brume", "Obole"},
            {"BLK", "Blackout", "Panne", "Ombre", "Nuit", "Suie"},
    };

    /** Cree un tournoi simule de {@code n} equipes (2 a 32) : les deux premiers clans engagent deux equipes. */
    static Tournoi creer(MinecraftServer s, int n, Format format, long graine) {
        Random rnd = new Random(graine);
        List<Tableau.EquipeT> equipes = equipesFactices(n, format, 0);
        Tournoi t = new Tournoi(s, CwEtat.edition + 1, format, System.currentTimeMillis(), true);
        t.tableau = Tableau.construire(equipes, rnd);
        t.etat = Tournoi.Etat.PAUSE;
        return t;
    }

    /**
     * {@code n} equipes factices (clans inventes a partir du rang {@code premierClan} de la liste ; les deux premiers
     * clans de la serie engagent deux equipes). Les joueurs sont des UUID inventes (jamais connectes).
     */
    static List<Tableau.EquipeT> equipesFactices(int n, Format format, int premierClan) {
        List<Banniere> bannieres = bannieres();
        List<Tableau.EquipeT> equipes = new ArrayList<>();
        int clan = 0;
        int[] parClan = new int[CLANS.length * 4];
        while (equipes.size() < n) {
            int rang = clan + premierClan;
            int c = rang % CLANS.length;
            // les deux premiers clans engagent deux equipes (separation au premier tour)
            int numero = ++parClan[clan % parClan.length];
            String[] def = CLANS[c];
            UUID clanId = UUID.nameUUIDFromBytes(("cw-sim-" + def[0] + (rang / CLANS.length)).getBytes(StandardCharsets.UTF_8));
            List<UUID> joueurs = new ArrayList<>();
            Map<UUID, String> noms = new LinkedHashMap<>();
            for (int j = 0; j < format.taille; j++) {
                String nom = def[2 + (j + numero - 1) % 4] + (numero > 1 ? String.valueOf(numero) : "");
                UUID u = UUID.nameUUIDFromBytes(("cw-sim-" + nom + rang).getBytes(StandardCharsets.UTF_8));
                joueurs.add(u);
                noms.put(u, nom);
            }
            Banniere b = bannieres.isEmpty() ? Banniere.DEFAUT : bannieres.get(c % bannieres.size());
            String tag = rang >= CLANS.length ? def[0].substring(0, 2) + (rang / CLANS.length) : def[0];
            equipes.add(new Tableau.EquipeT(equipes.size(), clanId, tag, def[1], numero, b.accent(), b, joueurs, noms));
            if (!(clan < 2 && numero < 2)) clan++;
        }
        return equipes;
    }

    /** Tranche les {@code n} prochaines rencontres jouables (gagnant et compteurs au hasard). Rend le nombre tranche. */
    static int avancer(Tournoi t, int n) {
        Tableau tab = t.tableau;
        if (tab == null) return 0;
        int fait = 0;
        while (fait < n && !tab.termine()) {
            Tableau.Rencontre r = prochaine(tab);
            if (r == null) break;
            int taille = t.format.taille;
            r.totalA = r.totalB = taille;
            boolean aGagne = t.rnd.nextBoolean();
            int restants = 1 + t.rnd.nextInt(taille);
            r.vivantsA = aGagne ? restants : 0;
            r.vivantsB = aGagne ? 0 : restants;
            t.resultatSimule(r, aGagne ? r.a : r.b);
            fait++;
        }
        return fait;
    }

    /** Met la prochaine rencontre jouable « en direct » (compteurs partiels). */
    static boolean direct(Tournoi t) {
        Tableau tab = t.tableau;
        if (tab == null) return false;
        Tableau.Rencontre r = prochaine(tab);
        if (r == null) return false;
        int taille = t.format.taille;
        r.statut = Tableau.Statut.EN_COURS;
        r.totalA = r.totalB = taille;
        r.vivantsA = Math.max(1, taille - 1);
        r.vivantsB = Math.max(1, taille / 2);
        t.diffuserRencontre(r);
        Tournois.marquerVue();
        return true;
    }

    private static Tableau.Rencontre prochaine(Tableau tab) {
        int tour = tab.tourCourant();
        if (tour >= tab.nbTours()) return null;
        for (Tableau.Rencontre r : tab.tour(tour)) if (r.statut == Tableau.Statut.EN_COURS) return r;
        for (Tableau.Rencontre r : tab.tour(tour)) if (r.jouable()) return r;
        return null;
    }

    /** Les 16 modeles de banniere du mod clans, dans l'ordre du banc : BannieresBanc (commun, lu aussi par le client). */
    public static List<Banniere> bannieres() {
        return BannieresBanc.bannieres();
    }
}
