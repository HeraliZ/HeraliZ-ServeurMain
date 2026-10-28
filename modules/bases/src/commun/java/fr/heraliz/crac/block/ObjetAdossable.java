package fr.heraliz.crac.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Objet tenu d'un bloc {@link Adosse.Adossable} (CRac 1.11.0) : visé sur une case de mur (où rien ne se pose), il se
 * pose dans la case libre voisine la plus proche, poussé vers la case visée (l'angle d'une pièce, le pied d'un mur).
 * <p>HeraliZ (28/10) : la place est décidée une seule fois ({@link #placeDePose}), pour la pose et pour le fantôme de pose
 * du joueur : avant, le fantôme ne montrait rien quand l'objet partait dans la case voisine.
 */
public class ObjetAdossable extends BlockItem {
    private final int hauteur;

    public ObjetAdossable(Block block, Properties props, int hauteur) {
        super(block, props);
        this.hauteur = hauteur;
    }

    /** Où l'objet se pose : le contexte de pose (la case) et la case visée vers laquelle le pousser (null : aucune). */
    public record Place(BlockPlaceContext ctx, @Nullable BlockPos cible) {
        /** {@code calcul} fait avec la case visée connue d'{@link Adosse} (choix du côté où pousser). */
        public <T> T avecCible(Supplier<T> calcul) {
            if (this.cible == null) {
                return calcul.get();
            }
            Adosse.CIBLE.set(this.cible);
            try {
                return calcul.get();
            } finally {
                Adosse.CIBLE.remove();
            }
        }

        /** L'état que le bloc prendrait (sens, poussée, descente), ou null s'il ne se pose pas. */
        @Nullable
        public BlockState etat(Block bloc) {
            return this.avecCible(() -> bloc.getStateForPlacement(this.ctx));
        }
    }

    /** La place de pose pour ce clic, ou null si rien ne se pose (case visée occupée sans case libre à côté). */
    @Nullable
    public Place placeDePose(BlockPlaceContext ctx) {
        if (ctx.canPlace()) {
            return new Place(ctx, null);
        }
        BlockPos cible = ctx.getClickedPos();
        BlockPos ici = Adosse.caseLibre(ctx, cible, this.hauteur, this.getBlock().defaultBlockState());
        return ici == null ? null : new Place(BlockPlaceContext.at(ctx, ici, ctx.getClickedFace()), cible);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Place p = this.placeDePose(ctx);
        if (p == null || p.cible() == null) {
            return super.place(ctx);
        }
        return p.avecCible(() -> super.place(p.ctx()));
    }
}
