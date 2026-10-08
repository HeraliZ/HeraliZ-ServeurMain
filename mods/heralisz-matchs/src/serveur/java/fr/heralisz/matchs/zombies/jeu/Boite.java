package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Pose;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Boite mystere v2 (ZOMBIES_V3_SPEC §2 et §6, D-048) : 950 points. Temps du storyboard valide (20 ticks = 1 s) :
 * ouverture du couvercle en {@link #OUVERTURE_TICKS}, roulette jusqu'a {@link #ROULETTE_TICKS} (ItemDisplay de la vraie
 * arme qui monte de 12,5 a 25 px en changeant toutes les 3, puis 5, puis 8 ticks, contour de la couleur de rarete),
 * arme offerte {@link #OFFRE_TICKS} a l'acheteur seul (touche F) en redescendant doucement vers la paille, fermeture puis
 * boite de nouveau achetable {@link #REPOS_TICKS} plus tard. Tirage par raretes ({@link TableBoite}) sans les armes deja
 * possedees ; a l'arret, nom et rarete au-dessus de l'arme.
 * <p>Nounours (regles CoD, {@link ReglesBoite}) : jamais aux 3 premiers tirages de l'emplacement, puis 15 / 30 / 50 % ;
 * 8e tirage force a l'emplacement de depart. L'acheteur est rembourse, la boite part ({@link #DEPART_TICKS}) et arrive
 * ({@link #ARRIVEE_TICKS}) a un autre emplacement dont la zone est OUVERTE ; sans autre emplacement ouvert, pas de
 * nounours. Le premier emplacement est pris dans une zone ouverte (zone de depart d'abord) ; s'il n'y en a aucun, la
 * boite reste cachee ({@link #emplacement} = -1) jusqu'a l'ouverture d'une zone qui en a un ({@link #apparaitre}).
 * Etat serveur ; la boite visible est le bloc-entite du contenu ({@link ZombiesDecor#boite}), anime cote client.
 */
final class Boite {
    enum Etat { FERMEE, ROULETTE, OUVERTE, DEPART, ARRIVEE }

    static final int PRIX = 950;
    /** Memes temps que le rendu (BoiteBE) : ouverture, fin de la roulette, offre, fermeture + attente, depart, arrivee. */
    static final int OUVERTURE_TICKS = 10, ROULETTE_TICKS = 100, OFFRE_TICKS = 200, REPOS_TICKS = 8 + 40, DEPART_TICKS = 100, ARRIVEE_TICKS = 30;
    /** Arme : depart dans la paille, hauteur d'offre (blocs au-dessus du bas de l'emplacement : 12,5 et 25 px). */
    private static final double Y_ARME0 = 12.5 / 16, Y_ARME1 = 25.0 / 16;

    private final PartieZombies partie;
    private final TableBoite table;
    /** Emplacement courant (index de {@code plan.boites()}), -1 = cachee (aucun emplacement dans une zone ouverte). */
    int emplacement;
    Etat etat = Etat.FERMEE;
    int ticks;
    /** Ticks avant que la boite refermee soit de nouveau achetable. */
    private int repos;
    @Nullable
    UUID acheteur;
    /** Tirage propose (null hors offre). */
    @Nullable
    TableBoite.Tirage tirage;
    /** Rarete imposee au prochain tirage (banc d'essai, recompense), null = tirage normal. */
    @Nullable
    Rarete forcee;
    /** Tirages faits a l'emplacement courant. */
    int tirages;
    /** Vrai tant que la boite n'a pas quitte son premier emplacement (8e tirage = nounours). */
    private boolean depart = true;
    @Nullable
    private Display.ItemDisplay affichage;
    @Nullable
    private Display.TextDisplay etiquette;
    private double baseY;

    Boite(PartieZombies partie, TableBoite table, int emplacement) {
        this.partie = partie;
        this.table = table;
        this.emplacement = emplacement;
    }

    /** Arme proposee (id), ou null. */
    @Nullable
    String gun() {
        return tirage == null ? null : tirage.gunId();
    }

    /** Pose de l'emplacement courant (null si cachee ou si la carte n'a aucun emplacement). */
    @Nullable
    Pose pose() {
        var l = partie.plan().boites();
        return emplacement < 0 || emplacement >= l.size() ? null : l.get(emplacement);
    }

    /** Centre de la boite (pour la distance d'interaction). */
    @Nullable
    Vec3 centre() {
        Pose p = pose();
        return p == null ? null : new Vec3(p.x(), p.y() + 0.5, p.z());
    }

    boolean cachee() {
        return emplacement < 0;
    }

    boolean achetable() {
        return etat == Etat.FERMEE && repos <= 0 && pose() != null && !table.vide();
    }

    boolean offerteA(UUID joueur) {
        return etat == Etat.OUVERTE && joueur.equals(acheteur) && tirage != null;
    }

    /** Lance la roulette (points deja debites par la partie). */
    void acheter(ServerPlayer joueur) {
        Pose p = pose();
        if (p == null) return;
        etat = Etat.ROULETTE;
        ticks = 0;
        acheteur = joueur.getUUID();
        tirage = null;
        tirages++;
        baseY = p.y();
        // l'arme apparait a la fin de l'ouverture du couvercle (voir tick)
        partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.ROULETTE);
        partie.son(ZombiesApi.Sons.BOITE_ROULETTE, p.x(), p.y() + 1, p.z(), 1.2f);
    }

    /** L'acheteur prend l'arme proposee. */
    void prendre(ServerPlayer joueur) {
        if (!offerteA(joueur.getUUID())) return;
        TableBoite.Tirage t = tirage;
        fermer();
        partie.donnerTirage(joueur, t);
        Vec3 c = centre();
        if (c != null) partie.son(ZombiesApi.Sons.ARME_PRISE, c.x, c.y, c.z, 1f);
    }

    void tick() {
        Pose p = pose();
        if (p == null) return;
        switch (etat) {
            case FERMEE -> {
                if (repos > 0) repos--;
            }
            case ROULETTE -> {
                ticks++;
                if (ticks == OUVERTURE_TICKS - 2 && affichage == null) {
                    TableBoite.Tirage t = table.auHasard(partie.alea());
                    affichage = Affichages.objet(partie.niveau(), p.x(), baseY + Y_ARME0, p.z(), t == null ? ItemStack.EMPTY : partie.pileApercu(t), 0.9f);
                    if (t != null) Affichages.lueur(affichage, t.rarete().couleur);
                }
                if (ticks > OUVERTURE_TICKS) {
                    // defilement : toutes les 3 ticks, puis 5, puis 8 (ralentit avant l'arret)
                    int pas = ticks < 65 ? 3 : (ticks < 85 ? 5 : 8);
                    if (ticks % pas == 0) {
                        TableBoite.Tirage t = table.auHasard(partie.alea());
                        if (t != null) {
                            Affichages.changer(affichage, partie.pileApercu(t));
                            Affichages.lueur(affichage, t.rarete().couleur);
                        }
                    }
                }
                double k = Math.max(0, ticks - OUVERTURE_TICKS) / (double) (ROULETTE_TICKS - OUVERTURE_TICKS);
                monter(p, Y_ARME0 + (Y_ARME1 - Y_ARME0) * Math.min(1, k));
                if (ticks >= ROULETTE_TICKS) finRoulette(p);
            }
            case OUVERTE -> {
                ticks++;
                // descente lente sur toute l'offre, jusqu'a la paille
                double y = Y_ARME1 - (Y_ARME1 - Y_ARME0) * Math.min(1, ticks / (double) OFFRE_TICKS);
                monter(p, y);
                if (etiquette != null) Affichages.placer(etiquette, p.x(), baseY + y + 0.75, p.z());
                if (ticks >= OFFRE_TICKS) fermer();
            }
            case DEPART -> {
                ticks++;
                if (ticks >= DEPART_TICKS) deplacer();
            }
            case ARRIVEE -> {
                ticks++;
                if (ticks >= ARRIVEE_TICKS) {
                    etat = Etat.FERMEE;
                    ticks = 0;
                    repos = 0;
                    partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.FERMEE);
                }
            }
        }
    }

    private void monter(Pose p, double y) {
        if (affichage != null && !affichage.isRemoved()) affichage.setPos(p.x(), baseY + y, p.z());
    }

    private void finRoulette(Pose p) {
        ticks = 0;
        if (forcee == null && autreEmplacement() >= 0 && ReglesBoite.nounours(tirages, depart, partie.alea().nextFloat())) {
            // nounours : remboursement et depart (le nounours est dessine par le rendu de la boite)
            etat = Etat.DEPART;
            Affichages.retirer(affichage);
            affichage = null;
            if (acheteur != null) partie.rembourser(acheteur, PRIX);
            partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.DEPART);
            partie.son(ZombiesApi.Sons.BOITE_PELUCHE, p.x(), p.y() + 1, p.z(), 1.2f);
            partie.messageTous("zombies.boite.depart");
            HeraliszMatchs.LOG.info("[zombies] boite : nounours au tirage {} de l'emplacement {}{}", tirages, emplacement, depart ? " (depart)" : "");
            return;
        }
        Rarete f = forcee;
        forcee = null;
        tirage = f != null ? table.tirerDans(f, partie.alea(), partie.armesDe(acheteur)) : null;
        if (tirage == null) tirage = table.tirer(partie.alea(), partie.armesDe(acheteur));
        if (tirage == null) {
            if (acheteur != null) partie.rembourser(acheteur, PRIX);
            fermer();
            return;
        }
        etat = Etat.OUVERTE;
        if (affichage == null) affichage = Affichages.objet(partie.niveau(), p.x(), baseY + Y_ARME1, p.z(), ItemStack.EMPTY, 0.9f);
        ItemStack apercu = partie.pileApercu(tirage);
        Affichages.changer(affichage, apercu);
        Affichages.lueur(affichage, tirage.rarete().couleur);
        Component nom = partie.nomTirage(tirage);
        etiquette = Affichages.texte(partie.niveau(), p.x(), baseY + Y_ARME1 + 0.75, p.z(), nom, 0.8f);
        partie.decorBoiteTirage(emplacement, tirage.rarete());
        partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.OUVERTE);
        partie.annonceTirage(acheteur, tirage);
        HeraliszMatchs.LOG.info("[zombies] boite : {} tire {} {} {}", partie.nomJoueur(acheteur), tirage.rarete(), tirage.gunId(), tirage.accessoires());
    }

    /** Un autre emplacement dans une zone ouverte, tire au hasard, ou -1. */
    private int autreEmplacement() {
        int n = partie.plan().boites().size(), k = 0;
        for (int i = 0; i < n; i++) if (i != emplacement && partie.emplacementBoiteOuvert(i)) k++;
        if (k == 0) return -1;
        int r = partie.alea().nextInt(k);
        for (int i = 0; i < n; i++) {
            if (i == emplacement || !partie.emplacementBoiteOuvert(i)) continue;
            if (r-- == 0) return i;
        }
        return -1;
    }

    /** Fin du depart : l'ancien emplacement devient vide (nounours sur ses bidons), la boite arrive ailleurs. */
    private void deplacer() {
        Affichages.retirer(affichage);
        affichage = null;
        int nouveau = autreEmplacement();
        partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.ABSENTE);
        if (nouveau >= 0) {
            emplacement = nouveau;
            depart = false;
        }
        acheteur = null;
        tirage = null;
        arriver();
    }

    /** La boite arrive a l'emplacement courant (faisceau, chute, poussiere) ; achetable a la fin de l'arrivee. */
    private void arriver() {
        tirages = 0;
        etat = Etat.ARRIVEE;
        ticks = 0;
        repos = 0;
        partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.ARRIVEE);
        Pose p = pose();
        if (p != null) partie.son(ZombiesApi.Sons.BOITE_ARRIVEE, p.x(), p.y() + 1, p.z(), 1.5f);
    }

    /**
     * Boite cachee (aucun emplacement dans une zone ouverte au depart) : une zone vient de s'ouvrir, la boite apparait a
     * un emplacement ouvert s'il y en a un (premier emplacement de la partie : la regle du 8e tirage s'y applique).
     */
    void apparaitre() {
        if (emplacement >= 0) return;
        int e = autreEmplacement();
        if (e < 0) return;
        emplacement = e;
        depart = true;
        arriver();
    }

    /** Referme la boite (offre expiree ou arme prise) ; de nouveau achetable apres la fermeture et l'attente. */
    void fermer() {
        Affichages.retirer(affichage);
        Affichages.retirer(etiquette);
        affichage = null;
        etiquette = null;
        etat = Etat.FERMEE;
        acheteur = null;
        tirage = null;
        ticks = 0;
        repos = REPOS_TICKS;
        partie.decorBoite(emplacement, ZombiesDecor.EtatBoite.FERMEE);
    }

    /** Fin de partie : retire les affichages. */
    void nettoyer() {
        Affichages.retirer(affichage);
        Affichages.retirer(etiquette);
        affichage = null;
        etiquette = null;
    }
}
