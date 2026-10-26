package fr.heraliz.decors.bloc;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Meuble de deux blocs (1.11.0, mobilier de prison) : posé d'un coup, cassé d'un coup (casser une moitié retire l'autre).
 * Le joueur pose la première moitié là où il vise, face à lui ; la seconde va à côté :
 * <ul>
 *    <li>lit de prison : le long du mur, à droite de la première vue du joueur (moitiés « gauche » et « droite ») ;</li>
 *    <li>lit superposé : vers le mur, derrière la première (« pied » vers le joueur, « tete » au fond).</li>
 * </ul>
 * Modèles et états : outils/prison.py. Formes dessinées dos au nord, tournées comme les modèles.
 */
public class MeubleDouble extends Deco.Oriente {
    public enum Moitie implements StringRepresentable {
        GAUCHE("gauche"), DROITE("droite"), PIED("pied"), TETE("tete");

        private final String nom;

        Moitie(String nom) {
            this.nom = nom;
        }

        @Override
        public String getSerializedName() {
            return nom;
        }
    }

    public static final EnumProperty<Moitie> COTE = EnumProperty.create("cote", Moitie.class, Moitie.GAUCHE, Moitie.DROITE);
    public static final EnumProperty<Moitie> PARTIE = EnumProperty.create("partie", Moitie.class, Moitie.PIED, Moitie.TETE);

    /** Propriété en cours de construction (createBlockStateDefinition est appelé avant la fin du constructeur). */
    private static EnumProperty<Moitie> enCours;

    private final EnumProperty<Moitie> moitie;
    private final Moitie premiere, seconde;
    private final boolean leLongDuMur;

    public static MeubleDouble murale(Properties p, Function<BlockState, VoxelShape> forme) {
        enCours = COTE;
        return new MeubleDouble(p, forme, COTE, Moitie.GAUCHE, Moitie.DROITE, true);
    }

    public static MeubleDouble enLongueur(Properties p, Function<BlockState, VoxelShape> forme) {
        enCours = PARTIE;
        return new MeubleDouble(p, forme, PARTIE, Moitie.PIED, Moitie.TETE, false);
    }

    private MeubleDouble(Properties p, Function<BlockState, VoxelShape> forme, EnumProperty<Moitie> moitie, Moitie premiere,
                         Moitie seconde, boolean leLongDuMur) {
        super(p, forme, false, Effet.AUCUN);
        this.moitie = moitie;
        this.premiere = premiere;
        this.seconde = seconde;
        this.leLongDuMur = leLongDuMur;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(moitie, premiere));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(enCours);
    }

    public EnumProperty<Moitie> moitie() {
        return moitie;
    }

    /** De la première moitié vers la seconde (facing = côté vers le joueur). */
    private Direction versSeconde(Direction facing) {
        return leLongDuMur ? facing.getCounterClockWise() : facing.getOpposite();
    }

    /** De cette moitié vers l'autre. */
    private Direction versAutre(BlockState s) {
        Direction d = versSeconde(s.getValue(FACING));
        return s.getValue(moitie) == premiere ? d : d.getOpposite();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction facing = c.getHorizontalDirection().getOpposite();
        BlockPos autre = c.getClickedPos().relative(versSeconde(facing));
        Level l = c.getLevel();
        if (!l.getWorldBorder().isWithinBounds(autre) || !l.getBlockState(autre).canBeReplaced(c)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(moitie, premiere);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity qui, ItemStack objet) {
        super.setPlacedBy(l, p, s, qui, objet);
        if (!l.isClientSide) {
            l.setBlock(p.relative(versAutre(s)), s.setValue(moitie, seconde), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState voisin, LevelAccessor l, BlockPos p, BlockPos pv) {
        if (d == versAutre(s) && !(voisin.is(this) && voisin.getValue(moitie) != s.getValue(moitie))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(s, d, voisin, l, p, pv);
    }
}
