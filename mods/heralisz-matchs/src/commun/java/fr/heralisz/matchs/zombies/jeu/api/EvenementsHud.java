package fr.heralisz.matchs.zombies.jeu.api;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Evenements ponctuels du mode zombies cote <b>client</b>, a destination du HUD et de la musique ({@code zombies/client}) :
 * le module client s'enregistre par {@link HudZombies#ecouteur(EvenementsHud)} (dans son initialisation client). L'etat
 * permanent (manche, points, atouts, bonus actifs, invite d'achat, objectif, ambiance musicale, materiaux) se lit dans
 * {@link HudZombies}. Fil client (rendu), aucune methode ne doit bloquer. Toutes facultatives.
 */
public interface EvenementsHud {
    /** Gain (delta &gt; 0) ou depense (delta &lt; 0) de points d'un joueur (« +60 » flottant pres de son score). */
    default void points(UUID joueur, int delta, MotifPoints motif) {
    }

    /** Debut ({@code debut} vrai) ou fin d'une manche ; {@code speciale} = manche des Rodeurs. */
    default void manche(int manche, boolean speciale, boolean debut) {
    }

    /** Bonus ramasse par {@code parNom} (effet immediat ou minuterie, voir {@link HudZombies#bonusSecondes}). */
    default void bonus(TypeBonus type, String parNom) {
    }

    /**
     * Atout bu par un joueur. Si {@code joueur} est le joueur local : animation de boisson en 1re personne (bouteille
     * portee a la bouche ~1,5 s), l'effet est deja applique par le serveur.
     */
    default void atout(UUID joueur, TypeAtout type) {
    }

    /** Changement d'etat d'un joueur : {@link HudZombies#DEBOUT} (releve), {@link HudZombies#A_TERRE}, {@link HudZombies#MORT}. */
    default void etatJoueur(UUID joueur, String nom, int etat) {
    }

    /** Achat refuse ou action impossible (texte deja traduit cote serveur sous forme de cle). */
    default void refus(Component raison) {
    }

    /** Achat reussi (porte ouverte, arme, munitions, atout...), pour un petit retour visuel. */
    default void achat(Component quoi, int cout) {
    }

    /** Le courant vient d'etre retabli. */
    default void courant() {
    }

    // ------------------------------------------------------------------ v2 (ZOMBIES_V2_SPEC)
    /**
     * Moment musical (voir {@link MomentMusique}) : le client lance, enchaine ou coupe ses pistes. {@code manche} = manche
     * courante (0 avant la premiere). Aussi rejoue a l'arrivee dans la partie (etat courant).
     */
    default void musique(MomentMusique moment, int manche) {
    }

    /**
     * Objectif courant (en haut a gauche, discret) ; {@code nouveau} vrai quand l'objectif precedent vient d'etre rempli
     * (petite coche puis nouveau texte) ; texte vide = plus d'objectif affiche. Aussi lisible dans {@link HudZombies#objectif()}.
     */
    default void objectif(Component texte, boolean nouveau) {
    }

    /** Lore d'intro (2-3 lignes, debut de partie) : texte qui s'ecrit lettre a lettre en haut a gauche (sous l'objectif), puis s'efface. */
    default void intro(List<Component> lignes) {
    }

    /** Un joueur reprend son arme amelioree au Pack-a-Punch ({@code arme} = nom « ★ ... »). Voir aussi la version avec niveau. */
    default void packAPunch(UUID joueur, Component arme) {
    }

    /**
     * Un joueur reprend son arme amelioree au Pack-a-Punch, au niveau {@code niveau} (1..{@link ZombiesApi#PAP_NIVEAU_MAX}) ;
     * {@code arme} = nom avec autant d'etoiles que de niveaux (« ★★ M1911 »). C'est celle-ci que le jeu appelle ; par
     * defaut elle renvoie a {@link #packAPunch(UUID, Component)}.
     */
    default void packAPunch(UUID joueur, Component arme, int niveau) {
        packAPunch(joueur, arme);
    }

    /** Un joueur fabrique un objet a l'etabli ({@code objet} = nom de la recette). */
    default void fabrication(UUID joueur, Component objet) {
    }

    /** Materiaux de recuperation ramasses (delta &gt; 0) ou depenses (delta &lt; 0) par un joueur. */
    default void materiaux(UUID joueur, int delta) {
    }

    /** Easter egg : etape {@code etape} (1..total) trouvee ; {@code termine} vrai a la derniere (recompense annoncee). */
    default void easterEgg(int etape, int total, boolean termine) {
    }

    /**
     * La roulette de la boite s'arrete pour le joueur local : {@code arme} = nom de l'arme seul (deja dans la couleur de la
     * rarete), la rarete a part (libelle {@link Rarete#cle()}).
     */
    /** v3 (The Giant) : le joueur local vient d'etre teleporte (eclair bleu-blanc). */
    default void teleport() {
    }

    default void boite(Rarete rarete, Component arme) {
    }
}
