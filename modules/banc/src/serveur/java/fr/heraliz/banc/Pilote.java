package fr.heraliz.banc;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Un bot de charge et ce qui le « joue » à la place d'un vrai jeu (01/11, banc réaliste). Le bot lui-même est un
 * {@link ServerPlayer} ordinaire relié à une connexion factice ({@link Bots#creer}) : à chaque réapparition, Minecraft
 * (et Mohist) le remplacent par un nouvel objet, comme pour un vrai joueur ; le pilote, lui, reste le même et suit le
 * joueur actuel ({@link #joueur}).
 * <p>Vie d'un bot : {@code PREPARATION} (connexion AuthMe, kit) → {@code DEPART} (tronçon d'arrivée demandé en tâche de
 * fond, départ quand il est prêt) → {@code EN_JEU} → mort : {@code MORT} (écran de mort quelques secondes) →
 * réapparition par le même chemin qu'un vrai joueur → {@code CAMP} (au point de réveil du spawn, comme « Au camp ») →
 * {@code BORNE} (vraie borne de départ) → {@code EN_JEU} dans la zone où il arrive. {@code HORS_LIGNE} : déconnecté par
 * les rythmes ({@link Rythmes}), il revient plus tard là où il était.
 */
final class Pilote {
    enum Etat { PREPARATION, DEPART, EN_JEU, MORT, CAMP, BORNE, HORS_LIGNE }

    final String nom;
    final UUID id;
    final Cerveau cerveau;
    /** Lieu de départ (celui de {@code /bots lancer}) : il y revient si la borne ne marche pas. */
    final Place origine;
    /** Lieu de vie actuel. */
    Place zone;
    /** Le joueur de maintenant (change à chaque réapparition), null hors ligne. */
    @Nullable
    ServerPlayer joueur;
    /** La connexion factice de ce bot (la même pendant toute une connexion, réapparitions comprises). */
    @Nullable
    Connection canal;
    Etat etat = Etat.PREPARATION;
    /** Ticks restants dans l'état (préparation, écran de mort, camp, borne, départ). */
    int attente;
    /** Pas encore de sauvegarde : kit et départ vers sa zone à la fin de la préparation. */
    boolean neuf = true;
    /** Vit dans le tutoriel des bases : y retourne après une réapparition. */
    boolean tuto;
    /** Gardé au camp par un pic ({@link Pics}) : la borne attend le déclenchement. */
    boolean retenu;
    /** Tick de la reconnexion (hors ligne). */
    int retour;
    // départ en cours (DEPART)
    @Nullable
    Place destination;
    int raison;
    int destX, destZ, essais;
    /** Au camp : là où il va se poser avant la borne. */
    @Nullable
    Vec3 pointCamp;
    // dernier « paquet de position » (Bots.position) : comme le jeu d'un joueur, un paquet quand il bouge ou tourne la tête,
    // et au moins une fois par seconde
    double px, py, pz;
    float pLacet, pTangage;
    int depuisPosition;

    Pilote(String nom, UUID id, Place origine) {
        this.nom = nom;
        this.id = id;
        this.origine = origine;
        this.zone = origine;
        this.cerveau = new Cerveau(this);
    }

    boolean enLigne() {
        return joueur != null && !joueur.hasDisconnected();
    }

    boolean vivant() {
        return enLigne() && joueur.isAlive();
    }

    /** En jeu, vivant, et pas en combat (pour choisir qui se déconnecte ou voyage). */
    boolean calme() {
        return etat == Etat.EN_JEU && vivant() && !Zones.enCombat(zone.zone()) && !fr.heraliz.noyau.api.CombatTag.estTague(joueur);
    }
}
