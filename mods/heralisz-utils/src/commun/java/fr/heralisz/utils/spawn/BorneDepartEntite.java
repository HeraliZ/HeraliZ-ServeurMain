package fr.heralisz.utils.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Borne de départ : ne garde rien, sert seulement à dessiner l'hologramme au-dessus de la table chez le joueur
 * (BorneHologramme : globe de la carte qui tourne, épingle rouge qui saute d'un point à l'autre).
 */
public class BorneDepartEntite extends BlockEntity {
    public BorneDepartEntite(BlockPos p, BlockState s) {
        super(Depart.BORNE_BE.get(), p, s);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.5, 0, 0.5).expandTowards(0, 1.5, 0);
    }
}
