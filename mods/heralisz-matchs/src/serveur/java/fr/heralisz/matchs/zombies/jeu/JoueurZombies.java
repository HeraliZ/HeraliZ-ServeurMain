package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** Etat d'un joueur dans une partie de zombies (serveur). Champs mutables, acces par la partie seulement. */
final class JoueurZombies {
    /** Cible d'interaction (touche F). */
    enum Cible { AUCUNE, PORTE, FENETRE, ARME, BOITE_ACHAT, BOITE_PRENDRE, ATOUT, COURANT, REANIMER, PAP, PAP_PRENDRE, ATELIER, EASTER_EGG, GIANT }

    /** Couleurs fixes des 4 joueurs (blanc, bleu, jaune, vert). */
    static final int[] COULEURS = {0xF2F2F2, 0x5FA8FF, 0xF2C94C, 0x6FD08A};

    final UUID id;
    final String nom;
    final int couleur;
    boolean connecte = true;

    int points = 0;
    int pointsGagnes;
    int kills, tetes, couteau, reanimations, aTerreFois, planches;
    /** Planches reparees dans la manche (plafond de points). */
    int planchesManche;
    int atouts;
    int etat = HudZombies.DEBOUT;
    /** Ticks restants avant la mort, a terre. */
    int ticksATerre;
    /** Ticks avant de se relever seul (Reanimation rapide en solo, kit d'auto-reanimation), 0 = non. */
    int ticksAutoReleve;
    int relevesSolo = 3;
    /** Dernier tick ou le joueur a subi des degats (regeneration). */
    long dernierDegat;
    /** Emplacement verrouille a terre, -1 sinon ; objet remplace temporairement par le pistolet de secours. */
    int slotATerre = -1;
    ItemStack objetRemplace = ItemStack.EMPTY;
    boolean pistoletPrete;

    // v2 : etablis, coups de couteau
    /** Materiaux de recuperation. */
    int materiaux;
    /** Kits d'auto-reanimation (fabriques a l'etabli). */
    int autoReanimations;
    /** Plaques d'armure envoyees au HUD (recalculees depuis l'absorption chaque seconde). */
    int plaquesVues;
    /** Dernier coup de melee signale par le client (anti-rafale). */
    long dernierCoup = -100;
    /** v3 (The Giant) : Bowie Knife achete (couteau mortel jusqu'a la manche 10, perdu a la mort). */
    boolean bowie;
    /** v3 : zone de la carte envoyee au HUD (nom du lieu sous la boussole), vide = aucune encore. */
    String zoneVue = "";
    /** v3, acte IV : prochain coup du boss N-07 (montant remappe exact) et son tick serveur, -1 = aucun. */
    float degatsBoss;
    long degatsBossTick = -1;
    /** Dernier tick ou une touche sur le boss a rapporte +10 (une fois par tick). */
    long toucheBossTick = -1;

    // interaction
    boolean touche;
    /** Appui recu depuis le dernier tick (front montant de la touche), consomme par la partie. */
    boolean appui;
    Cible cible = Cible.AUCUNE;
    int cibleIndex = -1;
    @Nullable
    UUID cibleJoueur;
    int ticksMaintien;
    // dernier etat d'invite envoye (pour n'envoyer qu'aux changements)
    Cible inviteCible = Cible.AUCUNE;
    int inviteIndex = -2, inviteCout = -1, inviteProgression = -2;
    boolean inviteAbordable;

    JoueurZombies(UUID id, String nom, int rang) {
        this.id = id;
        this.nom = nom;
        this.couleur = COULEURS[Math.floorMod(rang, COULEURS.length)];
    }

    boolean debout() {
        return etat == HudZombies.DEBOUT;
    }

    boolean aAtout(TypeAtout a) {
        return (atouts & a.bit()) != 0;
    }

    /** Force le renvoi de l'invite au prochain calcul. */
    void inviteSale() {
        inviteIndex = -2;
        inviteProgression = -2;
    }
}
