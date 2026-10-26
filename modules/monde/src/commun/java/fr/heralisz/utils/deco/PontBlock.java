package fr.heralisz.utils.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plancher d'un pont de corde : invisible (le pont est dessiné par la tête, PontRenderer), il ne sert qu'à marcher.
 * Une plaque de 3 pixels dont le dessus est à (NIVEAU + 1) / 16 du bloc, posée par le générateur sous la courbe du
 * tablier ; sur les colonnes du bord (BORD), une rambarde d'un bloc au-dessus du plancher, sous la corde de main
 * courante (on peut l'enjamber en sautant, pas tomber en marchant). Rien à viser, rien à casser en survie.
 */
public class PontBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXE = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty NIVEAU = IntegerProperty.create("niveau", 0, 15);
    public static final EnumProperty<Bord> BORD = EnumProperty.create("bord", Bord.class);
    private static final VoxelShape[] FORMES = new VoxelShape[2 * 16 * 3];

    static {
        for (int a = 0; a < 2; a++) {
            for (int n = 0; n < 16; n++) {
                for (Bord b : Bord.values()) {
                    FORMES[(a * 16 + n) * 3 + b.ordinal()] = construire(a == 0 ? Direction.Axis.X : Direction.Axis.Z, n, b);
                }
            }
        }
    }

    public enum Bord implements StringRepresentable {
        AUCUN("aucun"), MOINS("moins"), PLUS("plus");

        private final String nom;

        Bord(String nom) {
            this.nom = nom;
        }

        @Override
        public String getSerializedName() {
            return nom;
        }
    }

    public PontBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(AXE, Direction.Axis.X).setValue(NIVEAU, 0).setValue(BORD, Bord.AUCUN));
    }

    /** Plaque (et rambarde) pour un pont orienté selon `axe` ; le côté MOINS est au nord (axe X) ou à l'ouest (axe Z). */
    private static VoxelShape construire(Direction.Axis axe, int niveau, Bord bord) {
        double top = niveau + 1, bot = Math.max(0, top - 3);
        double l0 = bord == Bord.MOINS ? 2 : 0, l1 = bord == Bord.PLUS ? 14 : 16;
        VoxelShape plaque = axe == Direction.Axis.X ? Block.box(0, bot, l0, 16, top, l1) : Block.box(l0, bot, 0, l1, top, 16);
        if (bord == Bord.AUCUN) {
            return plaque;
        }
        double r0 = bord == Bord.PLUS ? 12 : 2, r1 = bord == Bord.PLUS ? 14 : 4;
        VoxelShape rampe = axe == Direction.Axis.X ? Block.box(0, bot, r0, 16, top + 16, r1) : Block.box(r0, bot, 0, r1, top + 16, 16);
        return Shapes.or(plaque, rampe);
    }

    static VoxelShape forme(Direction.Axis axe, int niveau, Bord bord) {
        return FORMES[((axe == Direction.Axis.X ? 0 : 1) * 16 + niveau) * 3 + bord.ordinal()];
    }

    /** Dessus du plancher (en blocs, depuis le bas du bloc). */
    public static float dessus(BlockState s) {
        return (s.getValue(NIVEAU) + 1) / 16f;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(AXE, NIVEAU, BORD);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return forme(s.getValue(AXE), s.getValue(NIVEAU), s.getValue(BORD));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0f;
    }
}
