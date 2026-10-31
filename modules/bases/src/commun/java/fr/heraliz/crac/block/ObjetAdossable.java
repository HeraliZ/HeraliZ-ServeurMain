package fr.heraliz.crac.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Objet tenu d'un bloc {@link Adosse.Adossable} (CRac 1.11.0) : visé sur une case de mur (où rien ne se pose), il se
 * pose dans la case libre voisine la plus proche, poussé vers la case visée (l'angle d'une pièce, le pied d'un mur).
 * <p>HeraliZ (28/10) : la place est décidée une seule fois ({@link #placeDePose}), pour la pose et pour le fantôme de pose
 * du joueur : avant, le fantôme ne montrait rien quand l'objet partait dans la case voisine.
 * <p>HeraliZ (31/10) : un objet qui sait se mettre dans la case d'un mur ({@link Adosse#DANS_MUR} : la caisse en métal) s'y
 * pose quand le joueur vise cette case (son sol, la face du mur, ou le quart d'une case libre contre ce mur, qui le
 * poussait contre lui) : il apparaît là où on vise et la case libre reste libre. Le serveur prend au mur la moitié
 * intérieure de la case ({@code StructureService.ceder}) juste avant la pose ; si la pose n'a pas lieu, le mur la reprend
 * au tick suivant ({@code StructureService.reboucher}).
 */
public class ObjetAdossable extends BlockItem {
    private final int hauteur;

    public ObjetAdossable(Block block, Properties props, int hauteur) {
        super(block, props);
        this.hauteur = hauteur;
    }

    /**
     * Où l'objet se pose : le contexte de pose (la case), la case visée vers laquelle le pousser (null : aucune) et, pour une
     * pose dans la case d'un mur, cette case ({@code dansMur}) avec la valeur de {@link Adosse#DANS_MUR}.
     */
    public record Place(BlockPlaceContext ctx, @Nullable BlockPos cible, @Nullable BlockPos dansMur, int codeMur) {
        public Place(BlockPlaceContext ctx, @Nullable BlockPos cible) {
            this(ctx, cible, null, Adosse.AUCUN);
        }

        /** La case où l'objet se pose. */
        public BlockPos pos() {
            return this.dansMur != null ? this.dansMur : this.ctx.getClickedPos();
        }

        /** {@code calcul} fait avec la case visée connue d'{@link Adosse} (choix du côté où pousser, pose dans un mur). */
        public <T> T avecCible(Supplier<T> calcul) {
            if (this.cible == null && this.dansMur == null) {
                return calcul.get();
            }
            if (this.cible != null) {
                Adosse.CIBLE.set(this.cible);
            }
            if (this.dansMur != null) {
                Adosse.DANS_MUR_POSE.set(this.codeMur);
            }
            try {
                return calcul.get();
            } finally {
                Adosse.CIBLE.remove();
                Adosse.DANS_MUR_POSE.remove();
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
        Place mur = this.dansLeMur(ctx);
        return mur != null ? mur : this.horsDuMur(ctx);
    }

    /** La place sans la case d'un mur (CRac avant le 31/10) : case visée libre, sinon case libre voisine poussée vers elle. */
    @Nullable
    private Place horsDuMur(BlockPlaceContext ctx) {
        if (ctx.canPlace()) {
            return new Place(ctx, null);
        }
        BlockPos cible = ctx.getClickedPos();
        BlockPos ici = Adosse.caseLibre(ctx, cible, this.hauteur, this.getBlock().defaultBlockState());
        return ici == null ? null : new Place(BlockPlaceContext.at(ctx, ici, ctx.getClickedFace()), cible);
    }

    /**
     * La case de mur visée, si l'objet peut s'y mettre ({@link Adosse#dansMur}) : la case de pose quand c'est une case de
     * mur (sol visé au pied du mur), sinon la case de mur voisine du quart de la case libre visée (face du mur visée, ou
     * bord de la case contre le mur : ce qui poussait l'objet contre ce mur, {@link Adosse#axe}). Jamais dans un angle.
     */
    @Nullable
    private Place dansLeMur(BlockPlaceContext ctx) {
        BlockState defaut = this.getBlock().defaultBlockState();
        if (!defaut.hasProperty(Adosse.DANS_MUR) || this.hauteur != 1) {
            return null;
        }
        Level level = ctx.getLevel();
        BlockPos ici = ctx.getClickedPos();
        BlockPos cell = null;
        if (!ctx.canPlace()) {
            cell = ici;
        } else {
            Vec3 clic = ctx.getClickLocation();
            int x = Adosse.axe(level, ici, Direction.EAST, clic.x - ici.getX() - 0.5);
            int z = Adosse.axe(level, ici, Direction.SOUTH, clic.z - ici.getZ() - 0.5);
            if ((x == 0) != (z == 0)) {
                cell = ici.offset(x, 0, z);
            }
        }
        if (cell == null) {
            return null;
        }
        BlockState etat = defaut.setValue(ClaimBlock.FACING, OrientationObjet.face(ctx));
        int w = Adosse.dansMur(level, cell, etat);
        return w < 0 ? null : new Place(ctx, null, cell.immutable(), w);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Place p = this.dansLeMur(ctx);
        if (p != null) {
            InteractionResult r = this.poserDansLeMur(ctx, p);
            if (r != null) {
                return r;
            }
        }
        p = this.horsDuMur(ctx);
        if (p == null || p.cible() == null) {
            return super.place(ctx);
        }
        Place q = p;
        return q.avecCible(() -> super.place(q.ctx()));
    }

    /**
     * Pose dans la case d'un mur. Chez le joueur : rien n'est posé (le serveur seul peut prendre la case au mur), seulement
     * le geste et le son. Au serveur : la case est prise au mur ({@code Serveur.Moteur#cederCase}), puis l'objet y est posé
     * par le chemin habituel (règles de la base, appui, personne dans la place). Null quand le serveur refuse la case (ses
     * pièces disent autre chose que le monde) : pose comme avant.
     */
    @Nullable
    private InteractionResult poserDansLeMur(BlockPlaceContext ctx, Place p) {
        Level level = ctx.getLevel();
        BlockPos pos = p.pos();
        if (level.isClientSide()) {
            SoundType son = this.getBlock().defaultBlockState().getSoundType();
            level.playSound(ctx.getPlayer(), pos, son.getPlaceSound(), SoundSource.BLOCKS, (son.getVolume() + 1.0F) / 2.0F, son.getPitch() * 0.8F);
            return InteractionResult.SUCCESS;
        }
        fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
        if (m == null || !(level instanceof ServerLevel sl)) {
            return null;
        }
        // quelqu'un dans la place (le joueur lui-même, collé au mur) : rien n'est posé, le mur n'est pas touché
        BlockState etat = p.etat(this.getBlock());
        if (etat == null || !level.isUnobstructed(etat, pos, ctx.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(ctx.getPlayer()))) {
            return InteractionResult.FAIL;
        }
        if (!m.cederCase(sl, pos)) {
            return null;
        }
        // la case est libre maintenant : le contexte de pose la vise elle-même
        BlockPlaceContext dans = BlockPlaceContext.at(ctx, pos, ctx.getClickedFace());
        return p.avecCible(() -> super.place(dans));
    }
}
