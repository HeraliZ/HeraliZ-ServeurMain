package fr.heraliz.quetes.serveur;

import com.google.gson.JsonParser;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les dialogues des PNJ (serveur). Chaque page envoyée au joueur garde, côté serveur, ce que fait chacune de ses
 * réponses : le joueur ne renvoie que le numéro de la réponse choisie, et rien ne se fait s'il s'est éloigné du PNJ.
 * <p>
 * Déroulé : présentation (la 1re fois) → accueil selon la réputation ; réponses possibles : rendre une quête, donner ou
 * montrer ce qu'il demande, la prochaine quête de sa suite, son tableau de contrats, au revoir.
 */
final class Dialogues {
    private Dialogues() {
    }

    private record Rep(Texte libelle, int style, Runnable action) {
    }

    private record Session(int entite, List<Runnable> actions) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final long CHANGER_MS = 120_000L;

    static void oublier(UUID u) {
        SESSIONS.remove(u);
    }

    static void vider() {
        SESSIONS.clear();
    }

    private static void page(ServerPlayer p, Pnj ent, Defs.Pnj def, Texte texte, List<Rep> reps) {
        List<Runnable> actions = new ArrayList<>();
        List<Reseau.Option> options = new ArrayList<>();
        for (Rep r : reps) {
            options.add(new Reseau.Option(actions.size(), r.libelle(), r.style()));
            actions.add(r.action());
        }
        SESSIONS.put(p.getUUID(), new Session(ent.getId(), actions));
        Reseau.envoyer(p, new Reseau.Dialogue(ent.getId(), def.nom.vide() ? ent.nom() : def.nom, def.role, texte, options));
    }

    private static void fermer(ServerPlayer p) {
        SESSIONS.remove(p.getUUID());
        Reseau.envoyer(p, new Reseau.Dialogue(-1, Texte.VIDE, Texte.VIDE, Texte.VIDE, List.of()));
    }

    /** Réponse choisie par le joueur. */
    static void choix(ServerPlayer p, Reseau.Choix c) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.entite() != c.entite()) return;
        if (c.option() < 0 || c.option() >= s.actions().size()) {
            SESSIONS.remove(p.getUUID());
            return;
        }
        Entity e = p.level().getEntity(s.entite());
        if (!(e instanceof Pnj) || e.distanceToSqr(p) > 8 * 8 || !p.isAlive()) {
            fermer(p);
            return;
        }
        s.actions().get(c.option()).run();
    }

    // ------------------------------------------------------------------ pages
    /** Clic droit sur un PNJ. */
    static void parler(ServerPlayer p, Pnj ent) {
        Defs.Pnj def = Catalogue.pnj(ent.pnjId());
        if (def == null) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback("heraliz_quetes.cmd.pnj_sans_fichier",
                    "Ce PNJ (%s) n'a pas de fichier dans config/heraliz_quetes/pnj/.", ent.pnjId()));
            return;
        }
        Donnees.DPnj dp = Donnees.de(p.getUUID()).pnj(def.id);
        if (!dp.rencontre) {
            page(p, ent, def, def.presentation.vide() ? def.inconnu : def.presentation, List.of(
                    new Rep(Texte.cle("heraliz_quetes.dialogue.continuer"), 0, () -> {
                        dp.rencontre = true;
                        Donnees.sale(p.getUUID());
                        accueil(p, ent, def, null);
                    })));
            return;
        }
        accueil(p, ent, def, null);
    }

    private static void accueil(ServerPlayer p, Pnj ent, Defs.Pnj def, Texte entete) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        Donnees.DPnj dp = j.pnj(def.id);
        Texte texte = entete;

        // 1. ce qui se règle en parlant : étapes « parler » à ce PNJ, quêtes à rendre
        for (Donnees.DActive a : new ArrayList<>(j.actives.values())) {
            Defs.Quete q = Moteur.def(a);
            Defs.Etape e = Moteur.etape(a, q);
            if (e != null && e.type.equals("parler") && Moteur.pnjEtape(e, a).equals(def.id)) {
                Moteur.avancer(p, a, q, true);
                if (!j.actives.containsKey(a.cle)) {
                    if (texte == null) texte = q.fin;
                    continue;
                }
                if (texte == null && !e.t("reponse").vide()) texte = e.t("reponse");
            }
            if (j.actives.containsKey(a.cle) && a.aRendre && a.pnj.equals(def.id) && q != null) {
                Moteur.terminer(p, a, q);
                if (texte == null) texte = q.fin;
            }
        }

        // 2. ce qu'il attend (donner, montrer, payer)
        List<Rep> reps = new ArrayList<>();
        for (Donnees.DActive a : j.actives.values()) {
            Defs.Quete q = Moteur.def(a);
            Defs.Etape e = Moteur.etape(a, q);
            if (e == null || !Moteur.auPnj(e) || e.type.equals("parler") || !Moteur.pnjEtape(e, a).equals(def.id)) continue;
            int[] pr = Moteur.progression(a, e);
            Texte lib = switch (e.type) {
                case "montrer" -> Texte.cle("heraliz_quetes.dialogue.montrer", Moteur.libelle(e));
                case "payer" -> Texte.cle("heraliz_quetes.dialogue.payer", Moteur.libelle(e), Texte.brut(Ponts.nombre(e.entier("herals", 0))));
                default -> e.nombre() > 1
                        ? Texte.cle("heraliz_quetes.dialogue.donner_n", Moteur.libelle(e), Texte.brut(pr[0]), Texte.brut(pr[1]))
                        : Texte.cle("heraliz_quetes.dialogue.donner", Moteur.libelle(e));
            };
            String cle = a.cle;
            reps.add(new Rep(lib, 2, () -> {
                Donnees.DActive a2 = Donnees.de(p.getUUID()).actives.get(cle);
                Defs.Quete q2 = a2 == null ? null : Moteur.def(a2);
                Defs.Etape e2 = a2 == null ? null : Moteur.etape(a2, q2);
                if (e2 == null || !Moteur.auPnj(e2)) {
                    accueil(p, ent, def, null);
                    return;
                }
                Texte reponse = null;
                if (Moteur.faireAuPnj(p, a2, q2, e2)) {
                    boolean derniere = a2.etape == q2.etapes.size() - 1;
                    Moteur.avancer(p, a2, q2, true);
                    reponse = derniere ? q2.fin : e2.t("reponse");
                    if (reponse.vide()) reponse = Texte.cle("heraliz_quetes.dialogue.merci");
                } else if (!q2.rappel.vide()) reponse = q2.rappel;
                accueil(p, ent, def, reponse);
            }));
        }

        // 3. la prochaine quête de sa suite
        Defs.Quete prochaine = prochaine(j, dp, def);
        if (prochaine != null) {
            Defs.Quete q = prochaine;
            reps.add(new Rep(Texte.cle("heraliz_quetes.dialogue.nouvelle", q.titre, Texte.brut(etoiles(q.difficulte))), 1,
                    () -> proposer(p, ent, def, q)));
        }

        // 4. ses contrats
        if (!def.contrats.isEmpty()) {
            reps.add(new Rep(Texte.cle("heraliz_quetes.dialogue.contrats"), 0, () -> tableau(p, ent, def, null)));
        }
        reps.add(new Rep(Texte.cle("heraliz_quetes.dialogue.revoir"), 3, () -> fermer(p)));

        if ((texte == null || texte.vide()) && prochaine != null && !def.besoin.isEmpty()) {
            texte = def.besoin.get(Moteur.RANDOM.nextInt(def.besoin.size()));
        }
        if ((texte == null || texte.vide()) && !def.enCours.isEmpty() && aUneQuete(j, def)) {
            texte = def.enCours.get(Moteur.RANDOM.nextInt(def.enCours.size()));
        }
        if (texte == null || texte.vide()) {
            texte = def.accueil(Defs.niveau(dp.rep));
            if ((texte == null || texte.vide()) && !def.ambiance.isEmpty()) texte = def.ambiance.get(Moteur.RANDOM.nextInt(def.ambiance.size()));
            if (texte == null) texte = Texte.VIDE;
        }
        page(p, ent, def, texte, reps);
    }

    private static boolean aUneQuete(Donnees.DJoueur j, Defs.Pnj def) {
        for (Donnees.DActive a : j.actives.values()) if (a.pnj.equals(def.id)) return true;
        return false;
    }

    /** La prochaine quête de la suite, si aucune quête de la suite n'est en cours chez ce PNJ et que la réputation suffit. */
    private static Defs.Quete prochaine(Donnees.DJoueur j, Donnees.DPnj dp, Defs.Pnj def) {
        for (Donnees.DActive a : j.actives.values()) if (a.pnj.equals(def.id) && !a.suite.isEmpty()) return null;
        // depuis le début (quêtes finies sautées) : une quête ajoutée ou déplacée dans le fichier n'est jamais perdue
        for (int i = 0; i < def.suite.size(); i++) {
            Defs.Quete q = def.suite.get(i);
            if (q.desactivee || j.finies.contains("s:" + def.id + ":" + q.id)) continue;
            return q.repMin <= dp.rep ? q : null;
        }
        return null;
    }

    private static void proposer(ServerPlayer p, Pnj ent, Defs.Pnj def, Defs.Quete q) {
        page(p, ent, def, q.demande, List.of(
                new Rep(Texte.cle("heraliz_quetes.dialogue.accepter"), 4, () -> {
                    Donnees.DJoueur j = Donnees.de(p.getUUID());
                    if (prochaine(j, j.pnj(def.id), def) != q) {
                        accueil(p, ent, def, null);
                        return;
                    }
                    if (!Moteur.prete(q)) {
                        accueil(p, ent, def, Texte.cle("heraliz_quetes.dialogue.pas_prete"));
                        return;
                    }
                    Moteur.accepter(p, def, q, null);
                    accueil(p, ent, def, Texte.cle("heraliz_quetes.dialogue.accepte"));
                }),
                new Rep(Texte.cle("heraliz_quetes.dialogue.plus_tard"), 3, () -> accueil(p, ent, def, null))));
    }

    private static void tableau(ServerPlayer p, Pnj ent, Defs.Pnj def, Texte entete) {
        Donnees.DPnj dp = Donnees.de(p.getUUID()).pnj(def.id);
        Contrats.remplir(def, dp, Moteur.points(), Moteur.RANDOM);
        Donnees.sale(p.getUUID());
        List<Rep> reps = new ArrayList<>();
        for (int i = 0; i < dp.tableau.size(); i++) {
            Defs.Quete q = lire(dp.tableau.get(i), def.id);
            if (q == null) continue;
            int n = i;
            Texte lib = q.recompense.herals > 0
                    ? Texte.cle("heraliz_quetes.dialogue.offre", q.titre, Texte.brut(etoiles(q.difficulte)), Texte.brut(Ponts.nombre(q.recompense.herals)))
                    : Texte.cle("heraliz_quetes.dialogue.offre_sans", q.titre, Texte.brut(etoiles(q.difficulte)));
            reps.add(new Rep(lib, 0, () -> offre(p, ent, def, n)));
        }
        long reste = dp.changerApres - System.currentTimeMillis();
        reps.add(new Rep(reste > 0 ? Texte.cle("heraliz_quetes.dialogue.changer_attente", Texte.brut((reste + 999) / 1000))
                : Texte.cle("heraliz_quetes.dialogue.changer"), 0, () -> {
            Donnees.DPnj d = Donnees.de(p.getUUID()).pnj(def.id);
            if (d.changerApres > System.currentTimeMillis()) {
                tableau(p, ent, def, null);
                return;
            }
            d.tableau.clear();
            d.changerApres = System.currentTimeMillis() + CHANGER_MS;
            tableau(p, ent, def, Texte.cle("heraliz_quetes.dialogue.nouveaux"));
        }));
        reps.add(new Rep(Texte.cle("heraliz_quetes.dialogue.retour"), 3, () -> accueil(p, ent, def, null)));
        page(p, ent, def, entete != null ? entete : dp.tableau.isEmpty() ? Texte.cle("heraliz_quetes.dialogue.tableau_vide")
                : Texte.cle("heraliz_quetes.dialogue.tableau"), reps);
    }

    private static void offre(ServerPlayer p, Pnj ent, Defs.Pnj def, int n) {
        Donnees.DPnj dp = Donnees.de(p.getUUID()).pnj(def.id);
        if (n >= dp.tableau.size()) {
            tableau(p, ent, def, null);
            return;
        }
        String json = dp.tableau.get(n);
        Defs.Quete q = lire(json, def.id);
        if (q == null) {
            dp.tableau.remove(n);
            tableau(p, ent, def, null);
            return;
        }
        Texte texte = q.demande;
        Texte rec = Moteur.recompense(q.recompense);
        if (!rec.vide()) texte = Texte.cle("heraliz_quetes.dialogue.offre_detail", q.demande, rec);
        page(p, ent, def, texte, List.of(
                new Rep(Texte.cle("heraliz_quetes.dialogue.accepter"), 4, () -> {
                    Donnees.DPnj d = Donnees.de(p.getUUID()).pnj(def.id);
                    int i = d.tableau.indexOf(json);
                    if (i < 0) {
                        tableau(p, ent, def, null);
                        return;
                    }
                    if (!Moteur.prete(q)) {
                        d.tableau.remove(i);
                        tableau(p, ent, def, Texte.cle("heraliz_quetes.dialogue.pas_prete"));
                        return;
                    }
                    d.tableau.remove(i);
                    Moteur.accepter(p, def, q, json);
                    accueil(p, ent, def, Texte.cle("heraliz_quetes.dialogue.accepte"));
                }),
                new Rep(Texte.cle("heraliz_quetes.dialogue.retour"), 3, () -> tableau(p, ent, def, null))));
    }

    private static Defs.Quete lire(String json, String pnj) {
        try {
            return Defs.quete(JsonParser.parseString(json).getAsJsonObject(), pnj, new ArrayList<>());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static String etoiles(int n) {
        return "★".repeat(Math.max(1, Math.min(4, n)));
    }
}
