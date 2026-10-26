package fr.heralisz.utils.train;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Voyageur côté serveur (créé par Train.VOYAGEUR_USINE dans un monde serveur) : tick serveur et point de descente
 * d'origine (Voyageurs.tickServeur / Voyageurs.sortie, désormais dans VoyageursServeur).
 */
public class VoyageurServeur extends Voyageur {
    public VoyageurServeur(EntityType<? extends Voyageur> type, Level level) {
        super(type, level);
    }

    @Override
    void tickServeur() {
        VoyageursServeur.tickServeur(this);
    }

    /** Descente par le chemin vanilla (stopRiding : téléportation d'un autre mod, commande, mort...) : sortie sûre à côté
     *  du train (VoyageursServeur.sortie), jamais la position du voyageur (dans la caisse, au-dessus de la voie). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        if (libere) return sortieImposee != null ? sortieImposee : p.position();
        return VoyageursServeur.sortie(this, p);
    }
}
