package fr.heraliz.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bloc principal d'un objet qui peut occuper plusieurs blocs (frigo, armoire à fusils, caisse de deux mètres, largage) :
 * les autres cellules reçoivent un bloc « partie » invisible ({@link PartieBlock}) qui prend sa forme et lui renvoie
 * les clics. Orientation : FACING (face avant), repère du modèle avant vers +Z, +X à droite du joueur qui le regarde.
 */
public interface Encombrant {
    Encombrement encombrement();

    /** Toutes les cellules libres pour poser l'objet en pos, orienté face ? */
    default boolean placeLibre(LevelReader l, BlockPos pos, Direction face, BlockPlaceContext ctx) {
        for (int[] c : encombrement().cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Encombrement.rotateCell(c[0], c[1], c[2], face);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            BlockState s = l.getBlockState(p);
            boolean ok = ctx != null ? s.canBeReplaced(ctx) : s.canBeReplaced();
            if (!ok || (l instanceof Level lv && !lv.isInWorldBounds(p))) return false;
        }
        return true;
    }

    /** Pose les parties autour du bloc principal (côté serveur, après sa pose). */
    default void poserParties(Level l, BlockPos pos, BlockState st) {
        Direction face = st.getValue(HorizontalDirectionalBlock.FACING);
        for (int[] c : encombrement().cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Encombrement.rotateCell(c[0], c[1], c[2], face);
            BlockState part = PartieBlock.pourDecalage(w[0], w[1], w[2]);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            if (part != null && l.getBlockState(p).canBeReplaced()) l.setBlock(p, part, 3);
        }
    }

    /** Retire les parties (le bloc principal vient d'être cassé ou remplacé). */
    default void retirerParties(Level l, BlockPos pos, BlockState st) {
        Direction face = st.getValue(HorizontalDirectionalBlock.FACING);
        for (int[] c : encombrement().cells) {
            if (c[0] == 0 && c[1] == 0 && c[2] == 0) continue;
            int[] w = Encombrement.rotateCell(c[0], c[1], c[2], face);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            BlockState s = l.getBlockState(p);
            if (s.getBlock() instanceof PartieBlock && PartieBlock.principal(p, s).equals(pos)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
        }
    }
}
