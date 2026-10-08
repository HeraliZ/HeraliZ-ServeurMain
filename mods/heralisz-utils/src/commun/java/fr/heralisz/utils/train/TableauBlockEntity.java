package fr.heralisz.utils.train;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Panneau de gare : retrouve sa gare (la plus proche du réseau, à moins de 400 blocs) au premier affichage. */
public class TableauBlockEntity extends BlockEntity {
    private int gare = -2;

    public TableauBlockEntity(BlockPos p, BlockState s) {
        super(Train.TABLEAU_BE.get(), p, s);
    }

    /** Indice de la gare du réseau, -1 s'il n'y en a pas assez près. */
    public int gare() {
        if (gare == -2) {
            gare = -1;
            Horaire h = Horaire.get();
            if (h != null) {
                double best = 400 * 400;
                double[] c = new double[3];
                for (int k = 0; k < h.reseau.gares.length; k++) {
                    h.reseau.point(h.reseau.gares[k].s(), c);
                    double d = worldPosition.distToCenterSqr(c[0], c[1], c[2]);
                    if (d < best) {
                        best = d;
                        gare = k;
                    }
                }
            }
        }
        return gare;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0, 0, 2.0).expandTowards(0, 3.5, 0);
    }
}
