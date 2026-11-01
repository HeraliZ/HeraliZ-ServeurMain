package fr.heralisz.utils.zombies;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Navigation des zombies HeraliZ (serveur, {@link ZombieSurvieServeur} ; 01/11, « pathfinding des zombies ultra opti »).
 * <p>
 * Le calcul d'un chemin (jusqu'à 560 cases parcourues) est le plus gros coût d'un zombie. L'attaque de Minecraft le refait
 * toutes les 4 à 10 ticks dès que la cible a bougé d'un bloc, même de loin, et même quand elle est hors d'atteinte (sur un
 * toit, derrière un mur ou une clôture : chaque calcul parcourt alors toutes les cases permises pour retomber sur le même
 * chemin incomplet). Ici, vers la MÊME cible, tant que le chemin en cours n'est pas fini :
 * <ul>
 * <li>sous {@code chemin_pres} blocs : comme Minecraft ;</li>
 * <li>de {@code chemin_pres} à {@value #LOIN} blocs : un calcul au plus toutes les {@code attente_moyen} ticks ;</li>
 * <li>au-delà : toutes les {@code attente_loin} ticks ;</li>
 * <li>cible hors d'atteinte (le dernier chemin n'y arrivait pas) restée au même endroit : toutes les {@code attente_bloque}
 * ticks, à toute distance, même quand le zombie est déjà au bout de ce chemin (au pied du mur).</li>
 * </ul>
 * Entre deux calculs, le zombie suit son chemin (vers l'endroit où était sa cible) et l'attaque de Minecraft le croit en
 * route. Nouvelle cible, chemin fini, rompu ou remplacé (promenade, bruit, Zombie Awareness) : calcul tout de suite, comme
 * avant. Réglages : {@code heraliz_zombies-server.toml} ; {@code ia_allegee = false} : comme Minecraft.
 */
final class NavigationZombie extends GroundPathNavigation {
    /** Au-delà, la cible est « loin » (blocs). */
    private static final double LOIN = 24.0;
    /** La cible « n'a pas bougé » : à moins de 1,5 bloc de sa place au dernier calcul. */
    private static final double IMMOBILE2 = 1.5 * 1.5;

    /** Identifiant de la créature visée par le dernier calcul (Integer.MIN_VALUE : chemin vers autre chose). */
    private int cible = Integer.MIN_VALUE;
    private int dernierCalcul;
    private double cx, cy, cz;
    /** Taille de recherche voulue par le niveau d'IA ({@link IaZombies}), rétablie après un chemin vers un bruit. */
    private float multiplicateur = 1.0F;

    NavigationZombie(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public boolean moveTo(Entity e, double vitesse) {
        Path p = getPath();
        if (e.getId() == cible && p != null && garder(e, p, isDone())) {
            setSpeedModifier(vitesse);
            CheminsZombies.garde();
            return true;
        }
        boolean ok = super.moveTo(e, vitesse);   // passe par moveTo(Path, double) : la cible y est oubliée, puis notée ici
        cible = e.getId();
        dernierCalcul = mob.tickCount;
        cx = e.getX();
        cy = e.getY();
        cz = e.getZ();
        return ok;
    }

    /** Tout chemin posé (promenade, bruit, Zombie Awareness, attaque qui démarre) : le prochain vers une cible est calculé. */
    @Override
    public boolean moveTo(@Nullable Path chemin, double vitesse) {
        cible = Integer.MIN_VALUE;
        return super.moveTo(chemin, vitesse);
    }

    /**
     * Garder le chemin en cours plutôt que d'en calculer un nouveau ? {@code fini} : le zombie est déjà au bout de son
     * chemin ; seulement s'il était incomplet (cible hors d'atteinte, restée au même endroit) : le zombie reste au pied du
     * mur comme avec Minecraft, qui refaisait pourtant le même calcul complet toutes les 4 à 10 ticks.
     */
    private boolean garder(Entity e, Path p, boolean fini) {
        if (!ZombiesConfig.IA_ALLEGEE.get()) return false;
        int depuis = mob.tickCount - dernierCalcul;
        // hors d'atteinte et la cible n'a presque pas bougé : un nouveau calcul retomberait sur le même mur
        boolean bloque = !p.canReach() && e.distanceToSqr(cx, cy, cz) < IMMOBILE2;
        if (fini) return bloque && depuis < ZombiesConfig.ATTENTE_BLOQUE.get();
        double d2 = mob.distanceToSqr(e);
        int pres = ZombiesConfig.CHEMIN_PRES.get();
        int attente = d2 > LOIN * LOIN ? ZombiesConfig.ATTENTE_LOIN.get() : d2 > (double) pres * pres ? ZombiesConfig.ATTENTE_MOYEN.get() : 0;
        if (bloque) attente = Math.max(attente, ZombiesConfig.ATTENTE_BLOQUE.get());
        return depuis < attente;
    }

    @Override
    public void setMaxVisitedNodesMultiplier(float m) {
        multiplicateur = m;
        super.setMaxVisitedNodesMultiplier(m);
    }

    /**
     * Va voir un point (bruit, {@link StimuliZombies}) avec une recherche de chemin bornée à {@code facteur} fois celle de
     * Minecraft (et jamais plus grande que celle de son niveau d'IA), puis rétablit la taille de son niveau d'IA.
     */
    boolean allerVoir(double x, double y, double z, double vitesse, float facteur) {
        float avant = multiplicateur;
        super.setMaxVisitedNodesMultiplier(Math.min(avant, facteur));
        try {
            return moveTo(x, y, z, vitesse);
        } finally {
            super.setMaxVisitedNodesMultiplier(avant);
        }
    }
}
