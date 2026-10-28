package fr.heraliz.crac.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Objet tenu d'un bloc {@link Adosse.Adossable} (CRac 1.11.0) : visé sur une case de mur (où rien ne se pose), il se
 * pose dans la case libre voisine la plus proche, poussé vers la case visée (l'angle d'une pièce, le pied d'un mur).
 */
public class ObjetAdossable extends BlockItem {
    private final int hauteur;

    public ObjetAdossable(Block block, Properties props, int hauteur) {
        super(block, props);
        this.hauteur = hauteur;
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (!ctx.canPlace()) {
            BlockPos cible = ctx.getClickedPos();
            BlockPos ici = Adosse.caseLibre(ctx, cible, this.hauteur, this.getBlock().defaultBlockState());
            if (ici != null) {
                Adosse.CIBLE.set(cible);
                try {
                    return super.place(BlockPlaceContext.at(ctx, ici, ctx.getClickedFace()));
                } finally {
                    Adosse.CIBLE.remove();
                }
            }
        }
        return super.place(ctx);
    }
}
