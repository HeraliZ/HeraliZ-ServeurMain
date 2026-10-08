package fr.heralisz.matchs.clashwar.bloc;

import fr.heralisz.matchs.clashwar.CwRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Mur-ecran « tableau du tournoi » (MATCHS_SPEC §2.3) : un bloc maitre ({@link TableauBloc}, bloc-entite, coin bas-gauche
 * vu depuis la salle) et des blocs de bord ({@link TableauBordBloc}) qui remplissent un rectangle {@code largeur x hauteur}
 * vertical. {@code face} = direction vers laquelle l'ecran regarde (vers les spectateurs) ; la largeur s'etend vers la
 * droite du spectateur ({@code face.getCounterClockWise()}), la hauteur vers le haut. Casser n'importe quel bloc retire
 * tout le mur. Fil serveur pour la pose.
 */
public final class MurTableau {
    private MurTableau() {
    }

    /** Taille posee par l'objet (8 x 4 blocs, rapport 2:1 comme la texture). */
    public static final int LARGEUR_DEFAUT = 8, HAUTEUR_DEFAUT = 4;
    public static final int LARGEUR_MAX = 48, HAUTEUR_MAX = 24;

    /** Garde de recursion pendant un retrait. */
    private static boolean enRetrait;

    /** Bloc (i, j) du mur dont le coin bas-gauche est {@code base}. */
    public static BlockPos bloc(BlockPos base, Direction face, int i, int j) {
        return base.relative(face.getCounterClockWise(), i).above(j);
    }

    /** Vrai si tout le rectangle est remplacable (air, herbe...) ou deja occupe par un mur de tableau. */
    public static boolean libre(Level level, BlockPos base, Direction face, int largeur, int hauteur) {
        for (int i = 0; i < largeur; i++) {
            for (int j = 0; j < hauteur; j++) {
                BlockPos p = bloc(base, face, i, j);
                if (!level.isInWorldBounds(p)) return false;
                BlockState s = level.getBlockState(p);
                if (!s.canBeReplaced() && !estMur(s)) return false;
            }
        }
        return true;
    }

    /**
     * Pose un mur complet (remplace un mur existant au meme endroit). Rend faux si la place n'est pas libre ou si la
     * taille est hors limites.
     */
    public static boolean poser(Level level, BlockPos base, Direction face, int largeur, int hauteur) {
        if (largeur < 2 || hauteur < 1 || largeur > LARGEUR_MAX || hauteur > HAUTEUR_MAX) return false;
        if (face.getAxis() == Direction.Axis.Y) return false;
        if (!libre(level, base, face, largeur, hauteur)) return false;
        enRetrait = true;
        try {
            BlockState maitre = CwRegistres.TABLEAU.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face);
            BlockState bord = CwRegistres.TABLEAU_BORD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face);
            level.setBlock(base, maitre, Block.UPDATE_ALL);
            for (int i = 0; i < largeur; i++) {
                for (int j = 0; j < hauteur; j++) {
                    if (i == 0 && j == 0) continue;
                    level.setBlock(bloc(base, face, i, j), bord, Block.UPDATE_ALL);
                }
            }
        } finally {
            enRetrait = false;
        }
        if (level.getBlockEntity(base) instanceof TableauBlocEntite be) be.taille(largeur, hauteur);
        return true;
    }

    /** Pose les bords autour d'un maitre deja pose (objet pose a la main). */
    static void poserBords(Level level, BlockPos base, Direction face, int largeur, int hauteur) {
        BlockState bord = CwRegistres.TABLEAU_BORD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face);
        enRetrait = true;
        try {
            for (int i = 0; i < largeur; i++) {
                for (int j = 0; j < hauteur; j++) {
                    if (i == 0 && j == 0) continue;
                    BlockPos p = bloc(base, face, i, j);
                    if (level.getBlockState(p).canBeReplaced()) level.setBlock(p, bord, Block.UPDATE_ALL);
                }
            }
        } finally {
            enRetrait = false;
        }
    }

    /** Retire les bords d'un maitre (appele quand le maitre disparait). */
    static void retirerBords(Level level, BlockPos base, Direction face, int largeur, int hauteur) {
        if (enRetrait) return;
        enRetrait = true;
        try {
            for (int i = 0; i < largeur; i++) {
                for (int j = 0; j < hauteur; j++) {
                    if (i == 0 && j == 0) continue;
                    BlockPos p = bloc(base, face, i, j);
                    if (level.getBlockState(p).is(CwRegistres.TABLEAU_BORD.get())) level.removeBlock(p, false);
                }
            }
        } finally {
            enRetrait = false;
        }
    }

    /** Un bord a ete casse : retrouve son maitre et retire tout le mur. */
    static void depuisBord(Level level, BlockPos pos, Direction face) {
        if (enRetrait) return;
        BlockPos m = maitreDe(level, pos, face);
        if (m != null) level.removeBlock(m, false);
    }

    /** Maitre dont le rectangle couvre ce bord, ou null. */
    @Nullable
    public static BlockPos maitreDe(Level level, BlockPos pos, Direction face) {
        Direction gauche = face.getClockWise();
        for (int i = 0; i < LARGEUR_MAX; i++) {
            for (int j = 0; j < HAUTEUR_MAX; j++) {
                BlockPos c = pos.relative(gauche, i).below(j);
                BlockState s = level.getBlockState(c);
                if (!s.is(CwRegistres.TABLEAU.get()) || s.getValue(HorizontalDirectionalBlock.FACING) != face) continue;
                BlockEntity be = level.getBlockEntity(c);
                if (be instanceof TableauBlocEntite t && i < t.largeur() && j < t.hauteur()) return c;
            }
        }
        return null;
    }

    public static boolean estMur(BlockState s) {
        return s.is(CwRegistres.TABLEAU.get()) || s.is(CwRegistres.TABLEAU_BORD.get());
    }
}
