package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.FormesDecoGiant;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Decor cuit de The Giant avec une partie animee par bloc-entite ({@link DecoAnimeBE}, seulement sur la case maitresse
 * {@code part = 0}) : levier du piege electrique ({@link #LEVIER}) et porte de confinement de N-07 ({@link #PORTE}). Le
 * corps fixe reste cuit ; le bloc-entite ne dessine que la poignee, les battants et les voyants, et seulement de pres.
 * <p>Porte : tant qu'elle est fermee, les battants ajoutent leur collision (boite x -25..25, y 0.5..54.5, z -3.3..3.5 px du
 * prop) aux cases du bati ; forme dynamique lue sur le bloc-entite de la case maitresse.
 */
public class BlocDecoAnime extends BlocDecoGiant implements EntityBlock {
    public static final int LEVIER = 0, PORTE = 1;
    public final int genre;
    /** Porte : boites des battants fermes par case et par face (null si la case n'en a pas). */
    @Nullable
    private final VoxelShape[][] battants;

    public static BlocDecoAnime creer(String id, Properties p, int genre) {
        FormesDecoGiant.Bloc b = preparer(id);
        return new BlocDecoAnime(p, b, genre);
    }

    private BlocDecoAnime(Properties p, FormesDecoGiant.Bloc b, int genre) {
        super(p, b);
        this.genre = genre;
        this.battants = genre == PORTE ? battants(b) : null;
    }

    /** Boite des battants (pixels du prop : x -25..25, y 0.5..54.5, z -3.3..3.5) decoupee par case. */
    private static VoxelShape[][] battants(FormesDecoGiant.Bloc b) {
        int w = b.largeur(), h = b.hauteur(), d = b.profondeur();
        VoxelShape[][] f = new VoxelShape[Math.max(1, b.parts())][];
        for (int pt = 0; pt < b.parts(); pt++) {
            int a = pt % w, bb = (pt / w) % h, c = pt / (w * h);
            double cx0 = a * 16 - w * 8, cy0 = bb * 16, cz0 = c * 16 - d * 8;
            double x0 = Math.max(-25, cx0) - cx0, x1 = Math.min(25, cx0 + 16) - cx0;
            double y0 = Math.max(0.5, cy0) - cy0, y1 = Math.min(54.5, cy0 + 16) - cy0;
            double z0 = Math.max(-3.3, cz0) - cz0, z1 = Math.min(3.5, cz0 + 16) - cz0;
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) continue;
            VoxelShape[] parFace = {Shapes.empty(), Shapes.empty(), Shapes.empty(), Shapes.empty()};
            FormesDecoGiant.ajouter(parFace, x0, y0, z0, x1, y1, z1);
            f[pt] = parFace;
        }
        return f;
    }

    /** Case maitresse (part 0) du prop dont {@code pos} est une case. */
    public BlockPos maitresse(BlockState s, BlockPos pos) {
        int pt = s.getValue(part);
        if (pt == 0) return pos;
        FormesDecoGiant.Bloc b = donnees;
        int w = b.largeur(), h = b.hauteur();
        int a = pt % w, bb = (pt / w) % h, c = pt / (w * h);
        Direction f = s.getValue(FACING);
        net.minecraft.world.phys.Vec3 r = tourner(a, c, f);
        return pos.offset(-(int) Math.round(r.x), -bb, -(int) Math.round(r.z));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape base = super.getShape(s, l, p, c);
        if (battants == null) return base;
        int pt = s.getValue(part);
        if (pt >= battants.length || battants[pt] == null) return base;
        if (l.getBlockEntity(maitresse(s, p)) instanceof DecoAnimeBE be && be.ouverte()) return base;
        return Shapes.or(base, battants[pt][s.getValue(FACING).get2DDataValue()]);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(part) == 0 ? new DecoAnimeBE(pos, state) : null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) ZombiesApi.interagir(sp);
        return InteractionResult.CONSUME;
    }
}
