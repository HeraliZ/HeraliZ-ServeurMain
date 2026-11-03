package fr.heraliz.crac.item;

import fr.heraliz.crac.block.EchelleBlock;
import fr.heraliz.crac.heraliz.Txt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Objet de l'échelle de raid (03/11) : la place est décidée par {@link EchelleBlock#pose} (face d'un mur, flanc d'une
 * fondation, au-dessus d'une échelle). Chez le joueur : seulement le geste et le son ; au serveur, la case d'un mur est
 * d'abord laissée par le mur ({@code Serveur.Moteur#cederCase}), puis l'échelle est posée par le chemin habituel
 * (évènement de pose : règles de la base dans BuildEventHandler). Pose refusée : le mur reprend sa case au tick suivant.
 */
public class EchelleItem extends BlockItem {
    public EchelleItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        if (ctx.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(),
            ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()), false);
        if (ctx.replacingClickedOnBlock()) {
            hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), false);
        }
        EchelleBlock.Pose p = EchelleBlock.pose(level, ctx.getPlayer(), hit, ctx.getClickedPos());
        if (p == null) {
            if (level.isClientSide()) {
                ctx.getPlayer().displayClientMessage(Txt.red("crac.echelle.ou"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            SoundType son = this.getBlock().defaultBlockState().getSoundType();
            level.playSound(ctx.getPlayer(), p.pos(), son.getPlaceSound(), SoundSource.BLOCKS, (son.getVolume() + 1.0F) / 2.0F,
                son.getPitch() * 0.8F);
            return InteractionResult.SUCCESS;
        }
        fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
        if (p.mur() && (m == null || !(level instanceof ServerLevel sl) || !m.cederCase(sl, p.pos()))) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = p.pos();
        EchelleBlock.POSE.set(p.etat(this.getBlock()));
        try {
            return super.place(BlockPlaceContext.at(ctx, pos, p.facing()));
        } finally {
            EchelleBlock.POSE.remove();
        }
    }
}
