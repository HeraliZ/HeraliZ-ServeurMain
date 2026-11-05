package fr.heraliz.crac.item;

import fr.heraliz.crac.block.EchelleBlock;
import fr.heraliz.crac.heraliz.Txt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Objet de l'échelle de raid (03/11) : une échelle d'un étage (3 cases), posée d'un coup ; la place est décidée par
 * {@link EchelleBlock#pose} (depuis le 05/11 : n'importe quelle face debout d'une pièce de base, centrée au point visé ;
 * ou l'étage au-dessus d'une échelle). Chez le joueur : seulement le geste et le son. Au serveur, les cases tenues par la
 * moitié d'un mur droit sont d'abord laissées par le mur ({@code Serveur.Moteur#cederCase}), puis la partie du bas est posée
 * par le chemin habituel (évènement de pose : règles de la base dans BuildEventHandler), garde sa place et les deux autres la
 * suivent ({@link EchelleBlock#setPlacedBy}). Pose refusée : le mur reprend ses cases au tick suivant.
 */
public class EchelleItem extends BlockItem {
    public EchelleItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Player joueur = ctx.getPlayer();
        if (joueur == null) {
            return InteractionResult.FAIL;
        }
        BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(),
            ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()), false);
        if (ctx.replacingClickedOnBlock()) {
            hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), false);
        }
        EchelleBlock.Pose p = EchelleBlock.pose(level, hit);
        if (p == null) {
            if (level.isClientSide()) {
                joueur.displayClientMessage(Txt.red("crac.echelle.ou"), true);
            }
            return InteractionResult.FAIL;
        }
        // personne dans la place (le joueur lui-même, collé au mur) : rien n'est posé, le mur n'est pas touché
        CollisionContext qui = CollisionContext.of(joueur);
        for (int i = 0; i < EchelleBlock.HAUTEUR; i++) {
            if (!level.isUnobstructed(p.etat(this.getBlock(), i), p.partie(i), qui)) {
                if (level.isClientSide()) {
                    joueur.displayClientMessage(Txt.red("crac.echelle.gene"), true);
                }
                return InteractionResult.FAIL;
            }
        }
        if (level.isClientSide()) {
            SoundType son = this.getBlock().defaultBlockState().getSoundType();
            level.playSound(joueur, p.bas(), son.getPlaceSound(), SoundSource.BLOCKS, (son.getVolume() + 1.0F) / 2.0F,
                son.getPitch() * 0.8F);
            return InteractionResult.SUCCESS;
        }
        fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
        if (m == null || !(level instanceof ServerLevel sl)) {
            return InteractionResult.FAIL;
        }
        // cases tenues par la moitié d'un mur droit : il les laisse ; si une case est refusée, celles déjà prises sont
        // rendues au mur au tick suivant (rien n'est posé). Cases libres (air) : rien à demander.
        for (int i = 0; i < EchelleBlock.HAUTEUR; i++) {
            BlockPos c = p.partie(i);
            if (level.getBlockState(c).getBlock() instanceof fr.heraliz.crac.build.world.StructureBlock && !m.cederCase(sl, c)) {
                return InteractionResult.FAIL;
            }
        }
        EchelleBlock.POSE.set(p.etat(this.getBlock(), 0));
        EchelleBlock.POSE_PLACE.set(p);
        try {
            return super.place(BlockPlaceContext.at(ctx, p.bas(), p.facing()));
        } finally {
            EchelleBlock.POSE.remove();
            EchelleBlock.POSE_PLACE.remove();
        }
    }
}
