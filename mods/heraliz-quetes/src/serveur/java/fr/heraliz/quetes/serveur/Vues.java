package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Ce que le joueur voit de ses quêtes : le journal (/quetes) et la quête suivie (à droite de l'écran). */
final class Vues {
    private Vues() {
    }

    static Reseau.Journal journal(ServerPlayer p, boolean ouvrir) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        List<Reseau.JPnj> pnj = new ArrayList<>();
        for (Defs.Pnj def : Catalogue.tous()) {
            Donnees.DPnj dp = j.pnj.get(def.id);
            List<Reseau.JQuete> quetes = new ArrayList<>();
            for (Donnees.DActive a : j.actives.values()) {
                if (!a.pnj.equals(def.id)) continue;
                Defs.Quete q = Moteur.def(a);
                if (q != null) quetes.add(quete(j, a, q, def));
            }
            if ((dp == null || !dp.rencontre) && quetes.isEmpty()) continue;
            int rep = dp == null ? 0 : dp.rep;
            pnj.add(new Reseau.JPnj(def.id, def.nom, def.role, Defs.niveau(rep), rep, Defs.seuilSuivant(rep), quetes));
        }
        List<Texte> titres = new ArrayList<>();
        for (String t : j.titres) titres.add(Texte.json(t));
        return new Reseau.Journal(ouvrir, pnj, titres, j.finies.size() + j.contratsFinis);
    }

    private static Reseau.JQuete quete(Donnees.DJoueur j, Donnees.DActive a, Defs.Quete q, Defs.Pnj def) {
        List<Reseau.JEtape> etapes = new ArrayList<>();
        for (int i = 0; i < q.etapes.size(); i++) {
            Defs.Etape e = q.etapes.get(i);
            if (i < a.etape || a.aRendre) {
                int[] pr = {e.nombre(), e.nombre()};
                etapes.add(new Reseau.JEtape(Moteur.libelle(e), pr[0], pr[1], 2));
            } else if (i == a.etape) {
                int[] pr = Moteur.progression(a, e);
                etapes.add(new Reseau.JEtape(Moteur.libelle(e), pr[0], pr[1], 1));
            } else if (e.oui("visible")) {
                etapes.add(new Reseau.JEtape(Moteur.libelle(e), 0, Moteur.progression(a, e)[1], 0));
            } else {
                // les étapes à venir restent une surprise
                etapes.add(new Reseau.JEtape(Texte.cle("heraliz_quetes.etape.inconnue"), 0, 0, 0));
            }
        }
        if (a.aRendre) etapes.add(new Reseau.JEtape(Texte.cle("heraliz_quetes.etape.rendre", def.nom), 0, 1, 1));
        return new Reseau.JQuete(a.cle, q.titre, q.difficulte, a.suite.isEmpty(), a.cle.equals(j.suivie), a.aRendre,
                Moteur.recompense(q.recompense), q.resume.vide() ? q.demande : q.resume, etapes);
    }

    static Reseau.Suivi suivi(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        Donnees.DActive a = j.actives.get(j.suivie);
        if (a == null) return new Reseau.Suivi(Texte.VIDE, Texte.VIDE, List.of());
        Defs.Quete q = Moteur.def(a);
        Defs.Pnj def = Catalogue.pnj(a.pnj);
        if (q == null) return new Reseau.Suivi(Texte.VIDE, Texte.VIDE, List.of());
        Texte nomPnj = def == null ? Texte.brut(a.pnj) : def.nom;
        List<Reseau.SLigne> lignes = new ArrayList<>();
        boolean auSpawn = p.level().dimension().location().toString().equals(Moteur.SPAWN);
        if (a.aRendre) {
            lignes.add(new Reseau.SLigne(Texte.cle(auSpawn ? "heraliz_quetes.etape.rendre" : "heraliz_quetes.etape.rendre_spawn", nomPnj),
                    0, 0, -1, false, 1));
        } else {
            Defs.Etape e = Moteur.etape(a, q);
            if (e != null) {
                int[] pr = Moteur.progression(a, e);
                int[] dist = distance(p, a, e);
                Texte lib = Moteur.libelle(e);
                if (Moteur.auPnj(e) && !auSpawn) lib = Texte.cle("heraliz_quetes.suivi.au_spawn", lib);
                lignes.add(new Reseau.SLigne(lib, pr[0], pr[1], dist[0], dist[1] == 1, 1));
                // objet de quête demandé mais perdu : où le retrouver
                String ref = e.texte("objet_quete", "");
                if (!ref.isEmpty() && !Moteur.possede(p, a.cle, ref) && a.refPoint.containsKey(ref)) {
                    Points.Point pt = Moteur.points().point(a.refPoint.get(ref));
                    int d = pt == null || !pt.monde().equals(p.level().dimension().location().toString()) ? -1
                            : (int) Math.sqrt(pt.pos().distToCenterSqr(p.position()));
                    lignes.add(new Reseau.SLigne(Texte.cle("heraliz_quetes.suivi.perdu"), 0, 0, arrondi(d), d >= 0, 0));
                }
            }
        }
        return new Reseau.Suivi(nomPnj, q.titre, lignes);
    }

    /** [mètres, 1 si approximatif] jusqu'au but le plus proche de l'étape, [-1, 0] s'il n'y a rien à montrer. */
    private static int[] distance(ServerPlayer p, Donnees.DActive a, Defs.Etape e) {
        String m = e.marqueur();
        if (m.equals("aucun") || a.cibles.isEmpty()) return new int[]{-1, 0};
        Points pts = Moteur.points();
        String monde = p.level().dimension().location().toString();
        double best = -1;
        for (String id : a.cibles) {
            if (a.faits.contains(id)) continue;
            Points.Point pt = pts.point(id);
            if (pt == null || !pt.monde().equals(monde)) continue;
            double d = Math.sqrt(pt.pos().distToCenterSqr(p.position()));
            if (best < 0 || d < best) best = d;
        }
        if (best < 0) return new int[]{-1, 0};
        if (m.equals("exact")) return new int[]{(int) best, 0};
        return new int[]{arrondi((int) best), 1};
    }

    /** Distance floue : à 25 m près jusqu'à 200 m, puis à 100 m près. */
    private static int arrondi(int d) {
        if (d < 0) return d;
        int pas = d < 200 ? 25 : 100;
        return Math.max(pas, Math.round(d / (float) pas) * pas);
    }
}
