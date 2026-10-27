package fr.heralisz.clans.saison;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.LigneBareme;
import fr.heralisz.clans.model.SaisonLigne;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.server.ClanStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que montre l'onglet Saison de l'ecran du clan (25/10, maquette validee) : le classement de la saison et
 * « Gagner des points », construit depuis le bareme reel (relu par /saison recharger) : les joueurs voient toujours les
 * vrais chiffres. Une nouvelle source de points (evenement…) ajoute ici sa ligne, avec son libelle et sa condition dans
 * les trois langues du mod (docs/EVENEMENTS.md, liste de branchement).
 */
public final class SaisonVue {
    private SaisonVue() {
    }

    /** Clans differents au minimum pour qu'un ClashWar compte (Recompenses.SAISON_CLANS_MIN du ClashWar). */
    private static final int CLASHWAR_CLANS_MIN = 4;
    private static final String[] AUCUN = new String[0];

    /** L'onglet Saison pour ce joueur : top 20 et place de son clan. */
    public static Packets.SaisonS2C pour(ServerPlayer p) {
        ClanStore s = ClanStore.get();
        Clan mien = s == null ? null : s.clanDe(p.getUUID());
        List<SaisonLigne> top = new ArrayList<>();
        for (Saison.Ligne l : Saison.classement()) {
            if (top.size() >= 20) break;
            Clan c = s == null ? null : s.get(l.clan());
            if (c == null) continue;
            top.add(new SaisonLigne(l.rGeneral(), c.id, c.tag, c.nom, c.banniere, Saison.entiers(l.pve()), Saison.entiers(l.pvp()),
                    Saison.entiers(l.events()), l.rPve(), l.rPvp(), l.rEvents()));
        }
        int[] rangs = mien == null ? new int[4] : Saison.rangs(mien.id);
        long[] points = new long[3];
        if (mien != null) {
            double[] sc = Saison.scores(mien.id);
            for (int i = 0; i < 3; i++) points[i] = Saison.entiers(sc[i]);
        }
        return new Packets.SaisonS2C(Saison.active(), Saison.numero(), Saison.fin(), mien != null && Ponts.aUnCoeur(mien.id), rangs, points,
                top, bareme());
    }

    /** « Gagner des points » : seulement les sources reellement branchees aujourd'hui. */
    static List<LigneBareme> bareme() {
        Bareme b = Saison.bareme();
        List<LigneBareme> l = new ArrayList<>();
        int pve = LigneBareme.PVE, pvp = LigneBareme.PVP, ev = LigneBareme.EVENTS;
        // PVE
        l.add(ligne(pve, "salle_rouge", "un", new double[]{b.pointsPve("salle_rouge")}, "salle"));
        l.add(ligne(pve, "salles", "paire", new double[]{b.pointsPve("salle_bleue"), b.pointsPve("salle_verte")}, "salle"));
        l.add(ligne(pve, "conteneur_t7", "un", new double[]{b.pointsPve("conteneur_t7")}, "premiere_ouverture"));
        l.add(ligne(pve, "quete_principale", "un", new double[]{b.pointsPve("quete_principale")}, "une_fois"));
        l.add(new LigneBareme(pve, "quete_clan", "un", new double[]{b.pointsPve("quete_clan")}, "quetes_clan",
                new String[]{String.valueOf(ClansConfig.QUETES_PAR_JOUR.get()), String.valueOf(ClansConfig.QUETES_PAR_SEMAINE.get())}));
        l.add(new LigneBareme(pve, "capture_libre", "par_niveau", new double[]{b.captureParValeur}, "capture",
                new String[]{entier(b.capturePlafondJour)}));
        if (ModList.get().isLoaded("heraliz_boss")) {
            l.add(new LigneBareme(pve, "boss", "un", new double[]{b.pointsPve("boss")}, "boss",
                    new String[]{entier(100 * (b.bossCoefs.length > 1 ? b.bossCoefs[1] : b.bossCoefs[0]))}));
        }
        l.add(ligne(pve, "conteneurs", "plage", new double[]{b.pointsPve("conteneur_t1"), b.pointsPve("conteneur_t6")}, "fouille"));
        l.add(ligne(pve, "craft", "plage", new double[]{b.pointsPve("craft_commun"), b.pointsPve("craft_epique")}, "craft"));
        l.add(ligne(pve, "vente", "vente", new double[]{b.pointsPve("vente_1000"), 1000}, "vente"));
        l.add(new LigneBareme(pve, "zombies", "plage", new double[]{b.pointsPve("zombie"), b.pointsPve("brute")}, "plafond",
                new String[]{entier(b.plafondJour)}));
        // PVP
        l.add(ligne(pvp, "kill", "plus", new double[]{b.pvpKill}, "kill"));
        l.add(ligne(pvp, "mort", "moins", new double[]{b.pvpMort}, "mort"));
        double[] co = b.pvpCoefs;
        l.add(new LigneBareme(pvp, "meme_victime", "coefs", new double[]{co[0], co.length > 1 ? co[1] : co[0], co[co.length - 1]}, "fenetre",
                new String[]{entier(b.pvpFenetreH)}));
        // 27/10 : avec une tenue (tenue_min > 0), une zone tenue reste protegee jusqu'a redevenir libre : on ne la prend plus
        // a un clan, la ligne n'est montree que sous l'ancienne regle (tenue_min = 0)
        if (fr.heralisz.clans.territory.Territoires.tenueMs() <= 0) {
            l.add(new LigneBareme(pvp, "capture_prise", "par_niveau", new double[]{b.captureParValeur * b.capturePriseCoef}, "capture_prise",
                    new String[]{entier(b.captureTenueMin), entier(b.capturePlafondJour)}));
        }
        l.add(new LigneBareme(pvp, "coeur", "pct", new double[]{b.raidGagnePct}, "coeur", new String[]{entier(b.raidPerduPct)}));
        // events
        double m = b.multiplicateur("clashwar");
        double[] pl = b.places;
        String[] min = {String.valueOf(CLASHWAR_CLANS_MIN)};
        l.add(new LigneBareme(ev, "clashwar_12", "paire", new double[]{place(pl, 0) * m, place(pl, 1) * m}, "clashwar", min));
        l.add(new LigneBareme(ev, "clashwar_34", "paire", new double[]{place(pl, 2) * m, place(pl, 3) * m}, "clashwar", min));
        l.add(ligne(ev, "clashwar_participation", "un", new double[]{b.participation * m}, ""));
        // événements de la carte (module heraliz_evenements) : une ligne par source du barème
        for (java.util.Map.Entry<String, Double> e : b.evenementsPoints.entrySet()) {
            Integer max = b.evenementsParJour.get(e.getKey());
            l.add(new LigneBareme(ev, e.getKey(), "un", new double[]{e.getValue()}, max == null ? e.getKey() : e.getKey() + "_plafond",
                    max == null ? AUCUN : new String[]{String.valueOf(max)}));
        }
        l.add(ligne(ev, "evenements", "bientot", new double[0], "evenements"));
        return l;
    }

    private static LigneBareme ligne(int cat, String cle, String forme, double[] v, String condition) {
        return new LigneBareme(cat, cle, forme, v, condition, AUCUN);
    }

    private static double place(double[] places, int i) {
        return i < places.length ? places[i] : 0;
    }

    private static String entier(double v) {
        return String.valueOf(Math.round(v));
    }
}
