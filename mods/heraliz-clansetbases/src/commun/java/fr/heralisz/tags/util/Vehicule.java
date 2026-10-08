package fr.heralisz.tags.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Vehicules vus par les tags. Le train de HeraliZ (heralisz_utils) porte le joueur sur une entite invisible
 * (heralisz_utils:voyageur) : elle est reconnue par son type, sans dependre du mod du train (commun client/serveur).
 */
public final class Vehicule {
    private Vehicule() {
    }

    /** Type d'entite du voyageur du train. */
    public static final ResourceLocation VOYAGEUR_TRAIN = new ResourceLocation("heralisz_utils", "voyageur");

    /**
     * Le joueur est-il a bord du train ? On n'y tague pas : la voiture est dessinee par-dessus le monde (ses parois ne
     * sont pas des blocs), le rayon atteindrait a travers elles les blocs qui defilent dehors ; le mod du train bloque
     * deja toute action sur ces blocs a bord.
     */
    public static boolean dansLeTrain(Entity e) {
        Entity v = e == null ? null : e.getVehicle();
        return v != null && VOYAGEUR_TRAIN.equals(EntityType.getKey(v.getType()));
    }
}
