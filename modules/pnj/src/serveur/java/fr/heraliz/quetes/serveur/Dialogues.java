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
 * montrer ce qu'il demande, la prochaine quête de sa suite, son tableau de contrats, au revoir. Le Commandant Varga,
 * tant que le joueur n'a pas fait le tutoriel des bases, ne propose que « Faire ma base » ({@link Accueil}).
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
        if (def.id.equals(Accueil.PNJ)) Accueil.vargaParle(p);   // 28/10 : fin du guidage (traînée, surbrillance) vers Varga
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
        if (Moteur.nettoyer(p)) Moteur.envoyerSuivi(p);
        // nouveau joueur : le Commandant Varga ne propose que le tutoriel des bases (Accueil), pas encore ses missions
        // le guide (20/10) : la visite du spawn, proposée tant qu'elle n'est pas faite, et toujours possible à revoir
        if (def.id.equals(Accueil.GUIDE)) {
            boolean neuf = Accueil.visiteAFaire(p);
            page(p, ent, def, Texte.cle(neuf ? "heraliz_quetes.guide.accueil" : "heraliz_quetes.guide.accueil_connu"), List.of(
                    new Rep(Texte.cle(neuf ? "heraliz_quetes.guide.visiter" : "heraliz_quetes.guide.revisiter"), 1, () -> {
                        fermer(p);
                        Accueil.visite(p);
                    }),
                    new Rep(Texte.cle("heraliz_quetes.dialogue.revoir"), 3, () -> fermer(p))));
            return;
        }
        // nouveau joueur, base pas faite : Varga propose sa première mission OU le tutoriel des bases (20/10)
        if (def.id.equals(Accueil.PNJ) && Accueil.tutoAFaire(p) && Donnees.de(p.getUUID()).actives.isEmpty()) {
            List<Rep> choix = new ArrayList<>();
            Donnees.DJoueur j0 = Donnees.de(p.getUUID());
            Defs.Quete premiere = prochaine(j0, j0.pnj(def.id), def);
            if (premiere != null) {
                choix.add(new Rep(Texte.cle("heraliz_quetes.tuto.premiere_mission"), 1, () -> proposer(p, ent, def, premiere)));
            }
            // 22/10 : plus de tutoriel des bases chez Varga (il vient à la création d'un clan) : la fiche /base info
            choix.add(new Rep(Texte.cle("heraliz_quetes.dialogue.infos_base"), 0, () -> {
                fermer(p);
                Ponts.tutoFiche(p);
            }));
            if (!def.faq.isEmpty()) choix.add(new Rep(Texte.cle("heraliz_quetes.faq.bouton"), 0, () -> faq(p, ent, def, 0, null)));
            choix.add(new Rep(Texte.cle("heraliz_quetes.dialogue.revoir"), 3, () -> fermer(p)));
            page(p, ent, def, Texte.cle("heraliz_quetes.tuto.varga"), choix);
            return;
        }
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
                if (texte == null && !j.actives.containsKey(a.cle)) texte = q.fin;   // pas finie : récompense en attente
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
                    // récompense en attente (économie injoignable) : la quête reste à rendre, pas de texte de fin
                    reponse = derniere ? (Donnees.de(p.getUUID()).actives.containsKey(cle) ? Texte.VIDE : q2.fin) : e2.t("reponse");
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
        // 5. son commerce
        if (def.marchand != null) {
            Texte bouton = !def.marchand.bouton.vide() ? def.marchand.bouton
                    : Texte.cle(def.marchand.hdv ? "heraliz_quetes.dialogue.hdv"
                    : def.marchand.recyclage ? "heraliz_quetes.dialogue.recycler"
                    : def.marchand.troc ? "heraliz_quetes.dialogue.echanger"
                    : def.marchand.rachat ? "heraliz_quetes.dialogue.vendre" : "heraliz_quetes.dialogue.acheter");
            reps.add(new Rep(bouton, 2, () -> {
                // 1.11.0 : pas de commerce en match, en combat, à terre, au tutoriel ni sur la carte (Marchands.empechement)
                Texte refus = Marchands.empechement(p);
                if (refus != null) {
                    fermer(p);
                    Moteur.notif(p, refus, 0xFF6B6B);
                    return;
                }
                SESSIONS.remove(p.getUUID());
                Marchands.envoyer(p, ent, def, true);
            }));
        }
        // 6. Varga : la fiche technique des bases (/base info), pour qui a oublié la commande (20/10) ; plus de
        // tutoriel des bases ici (22/10 : il vient à la création d'un clan)
        if (def.id.equals(Accueil.PNJ)) {
            reps.add(new Rep(Texte.cle("heraliz_quetes.dialogue.infos_base"), 0, () -> {
                fermer(p);
                Ponts.tutoFiche(p);
            }));
        }
        // 7. ses questions fréquentes (28/10 : clé « faq » de son fichier, Varga d'abord)
        if (!def.faq.isEmpty()) reps.add(new Rep(Texte.cle("heraliz_quetes.faq.bouton"), 0, () -> faq(p, ent, def, 0, null)));
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

    /** Questions par page (touches 1 à 9 : 7 questions + « Autres questions » + « Retour »). */
    private static final int FAQ_PAGE = 7;

    /**
     * Questions fréquentes du PNJ (clé « faq » de son fichier, Quêtes 1.14.0) : la liste des questions, page par page ;
     * une question choisie = sa réponse, puis « Autre question » ou « Retour ». {@code reponse} : texte affiché en haut.
     */
    private static void faq(ServerPlayer p, Pnj ent, Defs.Pnj def, int page, Texte reponse) {
        int n = def.faq.size(), pages = (n + FAQ_PAGE - 1) / FAQ_PAGE;
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<Rep> reps = new ArrayList<>();
        for (int i = pg * FAQ_PAGE; i < Math.min(n, (pg + 1) * FAQ_PAGE); i++) {
            Defs.Faq f = def.faq.get(i);
            reps.add(new Rep(f.question(), 0, () -> page(p, ent, def, f.reponse(), List.of(
                    new Rep(Texte.cle("heraliz_quetes.faq.autre"), 1, () -> faq(p, ent, def, pg, null)),
                    new Rep(Texte.cle("heraliz_quetes.dialogue.retour"), 3, () -> accueil(p, ent, def, null))))));
        }
        if (pages > 1) reps.add(new Rep(Texte.cle("heraliz_quetes.faq.suite"), 1, () -> faq(p, ent, def, (pg + 1) % pages, null)));
        reps.add(new Rep(Texte.cle("heraliz_quetes.dialogue.retour"), 3, () -> accueil(p, ent, def, null)));
        page(p, ent, def, reponse != null ? reponse : Texte.cle("heraliz_quetes.faq.intro"), reps);
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
            // 25/10 : quête dont les points ne sont pas encore posés par le staff (insignes, salles à carte...) : sautée
            // pour l'instant au lieu de bloquer toute la suite (avant : « Pas encore » à vie, plus aucune quête) ; elle
            // revient d'elle-même dès que ses points existent (la suite est relue depuis le début)
            if (!Moteur.prete(q)) {
                if (PAS_PRETES.add(def.id + ":" + q.id)) {
                    LOG.warn("[heraliz_quetes] quête « {} » de {} sautée : ses points de quête ne sont pas tous posés (/quete baguette)",
                            q.id, def.id);
                }
                continue;
            }
            return q.repMin <= dp.rep ? q : null;
        }
        return null;
    }

    /** Quêtes déjà signalées « points manquants » dans le journal (une fois par démarrage). */
    private static final java.util.Set<String> PAS_PRETES = new java.util.HashSet<>();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

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
