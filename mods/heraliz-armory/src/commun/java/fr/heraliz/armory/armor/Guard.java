package fr.heraliz.armory.armor;

/**
 * Protection d'un objet porte (0.9.0) : pieces de l'Armurerie (PieceDef, combinaisons comprises) et objets repris de
 * HeralisZ Armor (GearDef : ghillies, brassards, masques a gaz, objets event). Tous passent par le meme calcul
 * ({@link Protection}) et le meme plafond commun (75 %, radiation 100 %).
 */
public interface Guard {
    /** % contre les balles et projectiles. */
    int bullet();

    /** % contre les coups des joueurs. */
    int melee();

    /** % contre les attaques des creatures (zombies...). */
    int bite();

    /** % contre les explosions. */
    float explosion();

    /** % contre l'irradiation (100 au total = immunite). */
    int radiation();

    /** Filtre les gaz : les effets de la config (poison par defaut) ne s'appliquent pas. */
    boolean gas();

    /** Piece de tete : seule a compter sur un tir a la tete, un quart sur les autres coups. */
    boolean protectsHead();

    /** Couvre tout le corps (combinaison) : compte en entier sur la tete comme sur le corps. */
    default boolean wholeBody() {
        return false;
    }

    /** Immunite au gel (degats de gel annules, le givre ne prend pas). */
    default boolean cold() {
        return false;
    }

    /** Camouflage : les zombies ne reperent plus le porteur (effet geckolib:antizombie). */
    default boolean camouflage() {
        return false;
    }
}
