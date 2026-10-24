package fr.heralisz.utils.zombies;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Navigation des zombies HeraliZ ({@link ZombieSurvieServeur}) : le calcul de chemin vers une cible (A*, jusqu'à 560 cases
 * parcourues dans une zone de 100 blocs de côté) est le plus gros coût d'un zombie. L'attaque de Minecraft
 * (MeleeAttackGoal) le refait toutes les 4 à 10 ticks dès que la cible a bougé d'un bloc, même à 30 blocs, et même quand
 * la cible est hors d'atteinte (sur un toit, derrière un mur : chaque calcul parcourt alors les 560 cases pour rien).
 * <p>
 * Ici, vers la MÊME cible, tant que le chemin en cours n'est pas fini :
 * <ul>
 * <li>sous {@code chemin_pres} blocs : comme Minecraft (sauf cible hors d'atteinte restée sur place : {@code attente_bloque}) ;</li>
 * <li>de {@code chemin_pres} à 24 blocs : un calcul au plus toutes les {@code attente_moyen} ticks ;</li>
 * <li>au-delà : toutes les {@code attente_loin} ticks.</li>
 * </ul>
 * Entre deux calculs, le zombie suit son chemin (vers l'endroit où était la cible) et « réussit » (l'attaque de Minecraft
 * ne se croit pas bloquée). Nouvelle cible, chemin fini ou rompu : calcul tout de suite.
 */
public class NavigationZombie extends GroundPathNavigation {
    private static final double MOYEN = 24.0;

    private int cible = Integer.MIN_VALUE;
    private int dernierCalcul;
    private double cx, cy, cz;

    public NavigationZombie(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public boolean moveTo(Entity e, double vitesse) {
        Path p = getPath();
        if (e.getId() == cible && p != null && !isDone() && garder(e, p)) {
            setSpeedModifier(vitesse);
            return true;
        }
        boolean ok = super.moveTo(e, vitesse);
        cible = e.getId();
        dernierCalcul = mob.tickCount;
        cx = e.getX();
        cy = e.getY();
        cz = e.getZ();
        return ok;
    }

    /** Garder le chemin en cours plutôt que d'en calculer un nouveau ? */
    private boolean garder(Entity e, Path p) {
        int pres, moyen, loin, bloque;
        try {
            if (!ZombiesConfig.IA_ACTIVE.get()) return false;
            pres = ZombiesConfig.IA_CHEMIN_PRES.get();
            moyen = ZombiesConfig.IA_ATTENTE_MOYEN.get();
            loin = ZombiesConfig.IA_ATTENTE_LOIN.get();
            bloque = ZombiesConfig.IA_ATTENTE_BLOQUE.get();
        } catch (IllegalStateException ex) {
            return false;
        }
        int depuis = mob.tickCount - dernierCalcul;
        double d2 = mob.distanceToSqr(e);
        int attente = d2 > MOYEN * MOYEN ? loin : d2 > (double) pres * pres ? moyen : 0;
        // hors d'atteinte et la cible n'a presque pas bougé : un nouveau calcul tomberait sur le même mur
        if (!p.canReach() && e.distanceToSqr(cx, cy, cz) < 2.25) attente = Math.max(attente, bloque);
        return depuis < attente;
    }
}
