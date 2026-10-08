package fr.heralisz.matchs.zombies.jeu.api;

/**
 * Sorte de creature demandee a la {@link ZombieFabrique}. Les PV, la vitesse et les degats sont poses par le jeu
 * apres la creation (attributs) : la fabrique ne choisit que le modele, la texture et les animations.
 */
public enum TypeZombie {
    /** Zombie « labo » (4 variantes de texture : blouses, combinaisons, militaires...). */
    NORMAL,
    /** Creature rapide des manches speciales « Rodeurs » (equivalent des chiens de CoD). */
    RODEUR
}
