package fr.heralisz.utils.train;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Voie pleine : bloc invisible qui ne sert qu'à buter contre la voie dessinée (ballast et rails de VoieRendu ne sont
 * pas des blocs : sur un pont, on pouvait se cacher dedans). Même principe que le plancher des ponts de corde
 * (PontBlock) : rien à dessiner, rien à viser (pas de contour), seulement une collision dont le dessus est à
 * (HAUTEUR + 1) / 16 du bloc. Posé et retiré par le staff avec /train voie remplir | vider (jamais à la place d'un
 * bloc existant), sans objet, incassable en survie ; les balles de TaCZ s'y arrêtent comme sur le ballast.
 */
public class VoiePleineBlock extends Block {
    public static final IntegerProperty HAUTEUR = IntegerProperty.create("hauteur", 0, 15);
    private static final VoxelShape[] FORMES = new VoxelShape[16];

    static {
        for (int n = 0; n < 16; n++) FORMES[n] = Block.box(0, 0, 0, 16, n + 1, 16);
    }

    public VoiePleineBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(HAUTEUR, 15));
    }

    /** Incassable en survie, sans butin, sons de gravier, rien ne pousse ni n'apparaît dessus. */
    public static BlockBehaviour.Properties proprietes() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0f, 3600000.0f).noLootTable()
                .sound(SoundType.GRAVEL).noOcclusion().pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)
                .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    /** État dont le dessus est à h blocs du bas du bloc (arrondi au seizième, entre 1/16 et 1). */
    public BlockState hauteur(double h) {
        int n = (int) Math.round(h * 16) - 1;
        return defaultBlockState().setValue(HAUTEUR, Math.max(0, Math.min(15, n)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(HAUTEUR);
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
        return FORMES[s.getValue(HAUTEUR)];
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
