package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.zombies.jeu.api.Allure;
import fr.heralisz.matchs.zombies.jeu.api.TypeZombie;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Suivi serveur d'une creature de la partie (etat face aux fenetres, garde-fou anti-blocage, tir a la tete). */
final class ZombieSuivi {
    enum Etat {
        /** Derriere une fenetre, en route vers elle (ou en file d'attente derriere un autre zombie). */
        DEHORS,
        /** Colle a la fenetre ({@code Fenetre.dehors()}), arrache les planches. */
        ARRACHE,
        /** Enjambe la fenetre vide : deplacement interpole par le jeu, sans physique. */
        ENJAMBE,
        /** A l'interieur : poursuit le joueur debout le plus proche. */
        DEDANS
    }

    final Mob mob;
    final TypeZombie type;
    Allure allure;
    Etat etat;
    /** Fenetre visee (index global), -1 si sorti de terre. */
    int fenetre;
    int ticksArrachage;
    /** Recharge du coup de griffe a travers la fenetre. */
    int griffeRecharge;
    /** Enjambement : ticks ecoules, depart et arrivee. */
    int ticksEnjambe;
    @Nullable
    Vec3 depart, arrivee;
    /** Garde-fou : derniere position mesuree et temps sans progres, sans ligne de vue, trop loin. */
    double dx, dy, dz;
    int ticksImmobile;
    int ticksLoin;
    int ticksSansLigne;
    /** Reduction des degats recus si les PV depassent le plafond de l'attribut (1 = aucune). */
    float facteurDegats = 1f;
    /** Dernier tir TaCZ recu : tick serveur et tete. */
    long tirTick = -1;
    boolean tirTete;
    /** Tick ou un +10 de touche a deja ete compte (balle TaCZ ou coup), et par qui. */
    long toucheTick = -1;
    @Nullable
    UUID touchePar;
    /** Dernier coup de melee recu (tick) et par qui : evite un double coup au couteau a travers une fenetre. */
    long meleeTick = -100;
    @Nullable
    UUID meleePar;
    /** Coup de couteau a travers la fenetre en attente : tick d'application (0 = aucun) et auteur. */
    long coupTick;
    @Nullable
    UUID coupPar;
    /** Retire par le jeu (garde-fou, atomique, manche passee) : ne compte pas comme un kill. */
    boolean retire;
    /** Renfort du hall du boss N-07 (acte IV) : hors manche, garde-fou propre au hall, fige pendant les cinematiques. */
    boolean salle;
    /** Escalade ou saut d'obstacle en cours (0.2.3), null sinon. */
    @Nullable
    Escalade escalade;
    /** Prochain tick ou une escalade peut etre cherchee (recherche espacee, cout faible). */
    long prochaineEscalade;
    /** Recherches de chemin ratees de suite vers la cible (chemin incomplet), pour declencher l'escalade. */
    int cheminsRates;

    /**
     * Escalade (deplacement interpole par le jeu, sans physique) : approche du pied du mur, montee le long de la face
     * a ~1,5 bloc/s, puis pas sur le rebord. Saut d'obstacle bas ({@code saut}) : un seul arc, comme l'enjambement.
     */
    static final class Escalade {
        final Vec3 depart, pied, haut, arrivee;
        final net.minecraft.core.Direction face;
        final boolean saut;
        final int ticksApproche, ticksMontee, ticksPas;
        int ticks;

        Escalade(Vec3 depart, Vec3 pied, Vec3 haut, Vec3 arrivee, net.minecraft.core.Direction face, boolean saut, int ticksApproche,
                 int ticksMontee, int ticksPas) {
            this.depart = depart;
            this.pied = pied;
            this.haut = haut;
            this.arrivee = arrivee;
            this.face = face;
            this.saut = saut;
            this.ticksApproche = ticksApproche;
            this.ticksMontee = ticksMontee;
            this.ticksPas = ticksPas;
        }

        int duree() {
            return ticksApproche + ticksMontee + ticksPas;
        }
    }

    ZombieSuivi(Mob mob, TypeZombie type, Allure allure, int fenetre) {
        this.mob = mob;
        this.type = type;
        this.allure = allure;
        this.fenetre = fenetre;
        this.etat = fenetre >= 0 ? Etat.DEHORS : Etat.DEDANS;
        this.dx = mob.getX();
        this.dy = mob.getY();
        this.dz = mob.getZ();
    }

    /** Remet les compteurs du garde-fou a zero (apres un deplacement force). */
    void reinitialiserGardeFou() {
        dx = mob.getX();
        dy = mob.getY();
        dz = mob.getZ();
        ticksImmobile = 0;
        ticksLoin = 0;
        ticksSansLigne = 0;
    }
}
