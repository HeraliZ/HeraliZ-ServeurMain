package fr.heralisz.matchs.clashwar;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.duels.DuelPartie;
import fr.heralisz.matchs.duels.armes.Armurerie;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Spectateur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un match du tableau du ClashWar (21/10, Matchs 0.12.0) : <b>recherche et destruction</b> au meilleur des 5 manches
 * (premier a {@link CwConfig#manchesGagnantes}), sur le moteur des duels ({@link DuelPartie} : bombes des sites A et B,
 * ordinateur d'amorcage porte par un attaquant, piratage, une vie par manche, arene remise au propre a chaque manche).
 * Cotes echanges aux manches 3 et 5. Classes : les 5 classes par defaut des duels ({@link ClassePerso#parDefaut}), les
 * memes pour tous ; ni skin ni camo. Points de la carte : {@code spawn_a} (attaquants), {@code spawn_b} (defenseurs),
 * {@code bombe_a} / {@code bombe_b} (a defaut {@code site_a} / {@code site_b}), {@code charge}, {@code spectateur}.
 * <p>Avant le jeu, le match attend son arene et l'appel ({@link #tickAppel}) : un joueur tague en combat, absent ou
 * deconnecte rate le match ; une equipe sans aucun joueur present perd par forfait. Elimines = spectateurs de leur
 * partie ; a la fin (tableau des scores des duels), tout le monde retourne en salle d'attente, kit retire. Les joueurs
 * de la salle peuvent regarder le match ({@link #regarder}). Fil serveur.
 */
public final class MatchCw extends DuelPartie {
    private final Tournoi tournoi;
    private final Tableau.Rencontre rencontre;
    private final Tableau.EquipeT ta, tb;
    private final Equipe ea, eb;
    private final Component nomTour;
    @Nullable
    private final Carte carte;
    private boolean appele;
    /** Annulation demandee par le tournoi (pas de resultat a rendre). */
    private boolean parTournoi;

    private MatchCw(Tournoi tournoi, Tableau.Rencontre rencontre, Tableau.EquipeT ta, Tableau.EquipeT tb, Component nomTour,
                    @Nullable Carte carte, Equipe ea, Equipe eb) {
        super("clashwar-" + tournoi.edition + "-t" + rencontre.tour + "-m" + rencontre.index, "clashwar", tournoi.serveur(),
                tournoi.format.nom, carte == null ? "" : carte.id(), classes(), "ClashWar", ea, eb);
        this.tournoi = tournoi;
        this.rencontre = rencontre;
        this.ta = ta;
        this.tb = tb;
        this.ea = ea;
        this.eb = eb;
        this.nomTour = nomTour;
        this.carte = carte;
        ea.clan(ta.clan);
        eb.clan(tb.clan);
        reserver();
    }

    static MatchCw creer(Tournoi tournoi, Tableau.Rencontre rencontre, Tableau.EquipeT ta, Tableau.EquipeT tb, Component nomTour) {
        List<Carte> cartes = Arenes.cartesDeType(Carte.TYPE_CLASHWAR_RD);
        if (cartes.isEmpty()) cartes = Arenes.cartesDeType(tournoi.format.typeCarte);
        Carte c = cartes.isEmpty() ? null : cartes.get(tournoi.rnd.nextInt(cartes.size()));
        if (c == null) HeraliszMatchs.LOG.warn("[clashwar] aucune carte de type {}", tournoi.format.typeCarte);
        Equipe a = new Equipe("a", ta.libelle(), ta.couleur);
        Equipe b = new Equipe("b", tb.libelle(), tb.couleur == ta.couleur ? Tournois.OR : tb.couleur);
        return new MatchCw(tournoi, rencontre, ta, tb, nomTour, c, a, b);
    }

    /** Les 5 classes imposees (classes par defaut des duels, construites sur le catalogue courant). */
    private static List<ClassePerso> classes() {
        Catalogue cat = Armurerie.catalogue();
        List<ClassePerso> l = new ArrayList<>(ClassePerso.EMPLACEMENTS);
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) l.add(ClassePerso.parDefaut(i, cat));
        return l;
    }

    private void reserver() {
        if (carte == null || instance() != null) return;
        Instance i = Arenes.reserver(carte.id());
        if (i != null) instance(i);
    }

    Tableau.Rencontre rencontre() {
        return rencontre;
    }

    /** Libelle « [A] contre [B] » (tableau, chat). */
    Component contre() {
        return Component.translatable("clashwar.annonce.contre", ta.libelle(), tb.libelle());
    }

    // ================================================================== deroule
    @Override
    public void tick() {
        if (!appele) {
            tickAppel();
            return;
        }
        super.tick();
        // score des manches en direct sur le tableau (vivants = manches gagnees, total = manches a gagner)
        if (phase() != Phase.TERMINEE && (ea.score() != rencontre.vivantsA || eb.score() != rencontre.vivantsB)) {
            rencontre.vivantsA = ea.score();
            rencontre.vivantsB = eb.score();
            tournoi.diffuserRencontre(rencontre);
        }
    }

    private void tickAppel() {
        CwConfig cfg = CwConfig.get();
        Instance i = instance();
        if (i == null) {
            if (ticksPhase() % 100 == 0) reserver();
            if (ticksPhase() > cfg.attenteArene * 20) forfaitTechnique();
        } else if (i.prete() && ticksPhase() >= delaiAppel()) {
            appeler();
        } else if (ticksPhase() > cfg.attenteArene * 20) {
            forfaitTechnique();
        }
    }

    /**
     * Ticks minimum entre le lancement du tour et l'appel : le temps de l'annonce du tour (3 s), de la finale annoncee a
     * tout le serveur (6 s).
     */
    private int delaiAppel() {
        Tableau tab = tournoi.tableau;
        return tab != null && rencontre.tour == tab.nbTours() - 1 ? 120 : 60;
    }

    /** L'arene n'a pas pu etre posee a temps : la rencontre est tranchee au hasard (signale). */
    private void forfaitTechnique() {
        HeraliszMatchs.LOG.warn("[clashwar] {} : arene {} indisponible, rencontre tranchee au hasard", id(), carte == null ? "?" : carte.id());
        Annonces.chat(serveur(), Component.translatable("clashwar.chat.arene_indisponible").withStyle(ChatFormatting.GRAY));
        terminerSansJeu(tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b, true);
    }

    /** Rencontre tranchee sans jouer : personne n'a ete rattache a ce match. */
    private void terminerSansJeu(int gagnant, boolean forfait) {
        appele = true;
        phase(Phase.TERMINEE);
        MatchApi.retirer(this);
        if (instance() != null) Arenes.liberer(instance());
        instance(null);
        tournoi.resultat(this, rencontre, gagnant, forfait);
    }

    /** Appel du match : joueurs presents rattaches au match, premiere manche (choix de classe, placement). */
    private void appeler() {
        appele = true;
        List<ServerPlayer> pa = presents(ta), pb = presents(tb);
        if (pa.isEmpty() || pb.isEmpty()) {
            int gagnant = pa.isEmpty() && pb.isEmpty() ? (tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b) : pa.isEmpty() ? rencontre.b : rencontre.a;
            terminerSansJeu(gagnant, true);
            return;
        }
        for (ServerPlayer p : pa) entrer(p, ea);
        for (ServerPlayer p : pb) entrer(p, eb);
        rencontre.vivantsA = rencontre.vivantsB = 0;
        rencontre.totalA = rencontre.totalB = CwConfig.get().manchesGagnantes;
        tournoi.diffuserRencontre(rencontre);
        tournoi.bandeauSalle();
        demarrer();
    }

    /** Joueurs de l'equipe presents en salle (pas en train de regarder un autre match), connectes et non tagues. */
    private List<ServerPlayer> presents(Tableau.EquipeT t) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : t.joueurs) {
            if (!tournoi.participant(u)) continue;
            ServerPlayer p = joueur(u);
            if (p == null) continue;
            // il regardait un autre match : ramene en salle d'abord
            if (MatchApi.partieDe(p) instanceof MatchCw autre && autre != this && autre.equipeDe(u) == null) autre.arreterRegarder(p);
            if (MatchApi.partieDe(p) != tournoi.salle) continue;
            if (CombatTag.estTague(p)) {
                Annonces.chat(p, Component.translatable("clashwar.chat.rate_combat").withStyle(ChatFormatting.RED));
                continue;
            }
            l.add(p);
        }
        return l;
    }

    private void entrer(ServerPlayer p, Equipe e) {
        inscrire(p, e);
        MatchApi.rejoindre(p, this);
        p.setCamera(p);
        p.removeAllEffects();
        p.clearFire();
        p.setHealth(p.getMaxHealth());
    }

    // ================================================================== reglages du mode
    @Override
    protected int manchesGagnantes() {
        return CwConfig.get().manchesGagnantes;
    }

    /** Cotes echanges toutes les 2 manches (3e et 5e manche). */
    @Override
    protected boolean changerDeCote(int manche) {
        return manche > 1 && (manche - 1) % 2 == 0;
    }

    @Override
    protected Component titreHud() {
        return Component.translatable("clashwar.hud.rd", nomTour, Math.max(1, manche()));
    }

    @Override
    protected boolean statistiques() {
        return false;
    }

    /** Le bandeau du ClashWar (pastilles des manches, role) remplace les annonces de manche et de fin des duels. */
    @Override
    protected boolean annoncerManche() {
        return false;
    }

    @Override
    protected boolean annoncerFin() {
        return false;
    }

    /** Le panneau de fin de match du ClashWar est envoye des la fin ({@link #surFin}) : pas d'ecran des scores des duels. */
    @Override
    protected void envoyerScores(List<ServerPlayer> joueurs) {
    }

    /** Bandeau du ClashWar a chaque envoi du bandeau du moteur (le joueur le dessine a la place de celui du moteur). */
    @Override
    protected void apresHud(List<ServerPlayer> joueurs, int secondes) {
        if (phase() == Phase.FIN || phase() == Phase.TERMINEE) return;
        int sec = bombePosee() ? -1 : secondes;
        String tour = nomTour.getString();
        for (ServerPlayer p : joueurs) {
            Equipe e = equipeDe(p.getUUID());
            int role = e == null ? CwPaquets.ROLE_AUCUN : attaquant(p.getUUID()) ? CwPaquets.ROLE_ATTAQUE : CwPaquets.ROLE_DEFENSE;
            MatchsNet.versJoueur(p, new CwPaquets.MatchHudS2C(true, tour, ta.libelle(), ea.couleur(), ea.score(), tb.libelle(), eb.couleur(),
                    eb.score(), manchesGagnantes(), Math.max(1, manche()), role, sec, false));
        }
    }

    @Override
    protected void observateurs(List<ServerPlayer> sortie) {
        for (UUID u : spectateurs) {
            if (equipeDe(u) != null) continue;
            ServerPlayer p = joueur(u);
            if (p != null && !sortie.contains(p)) sortie.add(p);
        }
    }

    /** Fin du match pour ce joueur : retour en salle d'attente (ou rendu, si l'edition n'a plus de salle). */
    @Override
    protected void renvoyer(ServerPlayer p) {
        MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
        if (tournoi.actif() && tournoi.salle != null && tournoi.participant(p.getUUID())) tournoi.placerEnSalle(p);
        else tournoi.rendreJoueur(p);
    }

    /** Deconnecte pendant le match : il garde sa place en salle (il y revient a sa reconnexion). */
    @Override
    protected void detacherParti(ServerPlayer joueur) {
        if (tournoi.actif() && tournoi.salle != null && tournoi.participant(joueur.getUUID())) MatchApi.rejoindre(joueur, tournoi.salle);
        else MatchApi.quitter(joueur.getUUID());
    }

    /** Un joueur de la salle qui regardait se deconnecte : il garde sa place en salle. */
    @Override
    public void surDeconnexion(ServerPlayer joueur) {
        if (equipeDe(joueur.getUUID()) == null) {
            Spectateur.arreter(joueur, false);
            spectateurs.remove(joueur.getUUID());
            if (tournoi.actif() && tournoi.salle != null) MatchApi.rejoindre(joueur, tournoi.salle);
            else MatchApi.quitter(joueur.getUUID());
            return;
        }
        super.surDeconnexion(joueur);
    }

    @Override
    public boolean surReconnexion(ServerPlayer joueur) {
        if (!tournoi.actif() || tournoi.salle == null || !tournoi.participant(joueur.getUUID())) return false;
        Spectateur.arreter(joueur, false);
        tournoi.placerEnSalle(joueur);
        return true;
    }

    // ================================================================== fin
    @Override
    protected void surFin(@Nullable Equipe gagnante) {
        super.surFin(gagnante);
        if (annulee()) return;
        // classement des clans : eliminations et morts du match
        int ka = 0, ma = 0, kb = 0, mb = 0;
        for (Vues.JoueurScore j : tableau().joueurs()) {
            if (j.bot()) continue;
            if ("a".equals(j.equipe())) {
                ka += j.eliminations();
                ma += j.morts();
            } else {
                kb += j.eliminations();
                mb += j.morts();
            }
        }
        CwClassement.match(ta, ka, ma, gagnante == ea);
        CwClassement.match(tb, kb, mb, gagnante == eb);
        CwClassement.sauver(serveur());
        Tournois.diffuserClassement(serveur());
        envoyerFin(gagnante);
        HeraliszMatchs.LOG.info("[clashwar] {} : {} gagne {}-{} (elims {} / {})", id(), gagnante != null ? gagnante.nom() : "?", ea.score(), eb.score(), ka, kb);
    }

    /** Panneau de fin de match (maquette 2) a chaque joueur et spectateur : issue, score, manches, joueurs, suite. */
    private void envoyerFin(@Nullable Equipe gagnante) {
        List<Integer> manches = new ArrayList<>();
        for (String m : manchesJouees()) manches.add("b".equals(m) ? 1 : 0);
        List<CwPaquets.JoueurFin> joueurs = new ArrayList<>();
        for (Vues.JoueurScore j : tableau().joueurs()) {
            if (!j.bot()) joueurs.add(new CwPaquets.JoueurFin(j.nom(), "b".equals(j.equipe()) ? 1 : 0, j.eliminations(), j.morts()));
        }
        // meilleurs d'abord dans chaque equipe
        joueurs.sort((x, y) -> x.equipe() != y.equipe() ? Integer.compare(x.equipe(), y.equipe()) : Integer.compare(y.eliminations(), x.eliminations()));
        int secondes = fr.heralisz.matchs.duels.DuelsConfig.FIN_TICKS / 20;
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : MatchApi.joueursDe(this)) {
            ServerPlayer p = joueur(u);
            if (p != null) l.add(p);
        }
        for (ServerPlayer p : l) {
            Equipe e = equipeDe(p.getUUID());
            int issue = e == null || gagnante == null ? CwPaquets.FIN_NEUTRE : e == gagnante ? CwPaquets.FIN_VICTOIRE : CwPaquets.FIN_DEFAITE;
            MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
            MatchsNet.versJoueur(p, new CwPaquets.FinMatchS2C(nomTour.getString(), issue, ta.libelle(), ea.couleur(), ea.score(), tb.libelle(),
                    eb.couleur(), eb.score(), manchesGagnantes(), manches, joueurs, suite(e, gagnante), secondes));
            p.playNotifySound(issue == CwPaquets.FIN_DEFAITE ? CwSons.DEFAITE.get() : CwSons.VICTOIRE.get(), net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
        }
    }

    /** Ligne de suite du panneau de fin : prochain match, match pour la 3e place, elimination, fin du tournoi. */
    private Component suite(@Nullable Equipe e, @Nullable Equipe gagnante) {
        Tableau tab = tournoi.tableau;
        if (tab == null || gagnante == null) return Component.translatable("clashwar.fin.suite.salle");
        int dernier = tab.nbTours() - 1;
        if (rencontre.tour == dernier) return Component.translatable("clashwar.fin.suite.podium");
        if (e == null) return Component.translatable("clashwar.fin.suite.qualifie", gagnante.nom());
        if (e != gagnante) {
            boolean demi = rencontre.tour == dernier - 1 && tab.troisieme() != null;
            return Component.translatable(demi ? "clashwar.fin.suite.troisieme" : "clashwar.fin.suite.elimine");
        }
        Tableau.Rencontre suivante = tab.tour(rencontre.tour + 1).get(rencontre.index / 2);
        int moi = e == ea ? rencontre.a : rencontre.b;
        int adverse = rencontre.index % 2 == 0 ? suivante.b : suivante.a;
        Component nom = Component.translatable("clashwar.tour." + tab.cleRencontre(suivante));
        if (adverse < 0 || adverse == moi) return Component.translatable("clashwar.fin.suite.prochain_inconnu", nom);
        return Component.translatable("clashwar.fin.suite.prochain", nom, tab.equipe(adverse).libelle());
    }

    /** Tout le monde est revenu en salle : resultat au tableau. */
    @Override
    protected void apresRendu() {
        spectateurs.clear();
        Equipe g = gagnante();
        tournoi.resultat(this, rencontre, g == eb ? rencontre.b : rencontre.a, false);
    }

    /** Annulation de l'edition : tout le monde retourne en salle (le tournoi rendra les Coffres), aucun resultat. */
    void annuler() {
        if (phase() == Phase.TERMINEE) return;
        parTournoi = true;
        if (!appele) {
            appele = true;
            phase(Phase.TERMINEE);
            MatchApi.retirer(this);
            if (instance() != null) Arenes.liberer(instance());
            instance(null);
            return;
        }
        annuler(Component.translatable("clashwar.chat.annule_match").withStyle(ChatFormatting.GRAY));
    }

    /** Annulation venue du moteur des duels (arene perdue, equipe vide avant le jeu) : rencontre tranchee au hasard. */
    @Override
    public void annuler(Component raison) {
        boolean deja = phase() == Phase.TERMINEE;
        for (UUID u : new ArrayList<>(spectateurs)) {
            ServerPlayer p = joueur(u);
            if (p != null && equipeDe(u) == null) arreterRegarder(p);
        }
        super.annuler(raison);
        if (!deja && !parTournoi && tournoi.actif()) tournoi.resultat(this, rencontre, tournoi.rnd.nextBoolean() ? rencontre.a : rencontre.b, true);
    }

    // ================================================================== spectateurs de la salle
    /** Vrai si on peut regarder ce match (en jeu, pas encore fini). */
    boolean regardable() {
        return appele && (phase() == Phase.CHOIX || phase() == Phase.COMBAT || phase() == Phase.PREPARATION);
    }

    /**
     * Un joueur de la salle (libre, pas dans ce match) regarde le match : rattache a la partie comme spectateur sans
     * equipe, camera sur un joueur vivant (fleches gauche / droite pour changer), bornes de l'arene. Il revient en salle
     * par {@code /clashwar salle}, a la fin du match, ou quand son propre match l'appelle.
     */
    boolean regarder(ServerPlayer p) {
        if (!regardable() || equipeDe(p.getUUID()) != null) return false;
        MatchApi.rejoindre(p, this);
        Spectateur.suivre(p, this);
        rafraichirHud();
        return true;
    }

    /** Fin du visionnage : retour en salle d'attente. */
    void arreterRegarder(ServerPlayer p) {
        if (equipeDe(p.getUUID()) != null) return;
        MatchsNet.versJoueur(p, CwPaquets.MatchHudS2C.AUCUN);
        Spectateur.arreter(p, false);
        spectateurs.remove(p.getUUID());
        tournoi.placerEnSalle(p);
    }

    /** Spectateurs de la salle encore la quand le match se ferme : rendus a la salle. */
    @Override
    public void fermer() {
        for (UUID u : new ArrayList<>(spectateurs)) {
            if (equipeDe(u) != null) continue;
            ServerPlayer p = joueur(u);
            if (p != null && MatchApi.partieDe(p) == this) arreterRegarder(p);
        }
        super.fermer();
    }
}
