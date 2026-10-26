package fr.heralisz.utils.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Entité du mélangeur : aucune donnée, sert seulement à dessiner les pales qui tournent (côté client). */
public class MelangeurBlockEntity extends BlockEntity {
    public MelangeurBlockEntity(BlockPos p, BlockState s) {
        super(DecoBlocks.MELANGEUR_BE.get(), p, s);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 1, p.getY() - 3, p.getZ() - 1, p.getX() + 2, p.getY() + 1, p.getZ() + 2);
    }
}
