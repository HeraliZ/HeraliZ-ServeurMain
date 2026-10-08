package fr.heraliz.armory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Distance entre la camera et une entite (niveau de detail des ghillies, animations coupees de loin). Cote client
 * seulement. Reprise telle quelle de HeralisZ Armor (0.9.0).
 */
public final class Lod {
    /** Au-dela de 20 blocs, les meches ne s'animent plus : on ne le verrait pas. */
    public static final double ANIM_RANGE_SQR = 20 * 20;

    private Lod() {
    }

    public static double distSqr(Entity entity) {
        if (entity == null) {
            return 0;
        }
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
    }
}
