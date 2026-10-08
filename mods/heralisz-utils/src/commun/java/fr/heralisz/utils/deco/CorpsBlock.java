package fr.heralisz.utils.deco;

import fr.heralisz.utils.HeraliszUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Corps d'un décor 3D (phare, moulin, radar, drapeaux, roue à aubes, mât d'antenne, château d'eau, grillage...) : bloc
 * invisible dont la collision est la forme exacte du modèle dans cette case (voxels de 2 pixels fusionnés en boîtes).
 * tools/gen_corps.py découpe les modèles et écrit la table data/heralisz_utils/corps_formes.txt (une forme par ligne :
 * son, puis boîtes) ; le générateur de la map pose corps[forme=N] autour du bloc porteur du décor. Remplace les blocs
 * barrière : rien à viser au survol (pas de contour), on bute et les balles s'arrêtent sur le modèle lui-même (TaCZ
 * tire contre la forme de collision), pas sur des cubes invisibles. Incassable, sans objet, peut être dans l'eau.
 */
public class CorpsBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[] FORMES;
    private static final SoundType[] SONS;
    public static final IntegerProperty FORME;

    static {
        List<VoxelShape> formes = new ArrayList<>();
        List<SoundType> sons = new ArrayList<>();
        try (InputStream in = CorpsBlock.class.getResourceAsStream("/data/heralisz_utils/corps_formes.txt")) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            double res = 8;
            String ligne;
            while ((ligne = r.readLine()) != null) {
                if (ligne.isBlank() || ligne.startsWith("#")) {
                    continue;
                }
                String[] t = ligne.trim().split(" ");
                if (t[0].equals("res")) {
                    res = Double.parseDouble(t[1]);
                    continue;
                }
                sons.add(switch (t[0]) {
                    case "bois" -> SoundType.WOOD;
                    case "pierre" -> SoundType.STONE;
                    default -> SoundType.METAL;
                });
                VoxelShape f = Shapes.empty();
                for (int i = 1; i < t.length; i++) {
                    String[] b = t[i].split(",");
                    f = Shapes.joinUnoptimized(f, Shapes.box(Integer.parseInt(b[0]) / res, Integer.parseInt(b[1]) / res,
                            Integer.parseInt(b[2]) / res, Integer.parseInt(b[3]) / res, Integer.parseInt(b[4]) / res,
                            Integer.parseInt(b[5]) / res), BooleanOp.OR);
                }
                formes.add(f.optimize());
            }
        } catch (Exception e) {
            HeraliszUtils.LOG.error("Table des corps de décor illisible (data/heralisz_utils/corps_formes.txt)", e);
        }
        while (formes.size() < 2) {                               // une propriété entière a au moins deux valeurs
            formes.add(Shapes.block());
            sons.add(SoundType.METAL);
        }
        FORMES = formes.toArray(new VoxelShape[0]);
        SONS = sons.toArray(new SoundType[0]);
        FORME = IntegerProperty.create("forme", 0, FORMES.length - 1);
    }

    public CorpsBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FORME, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FORME, WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    /** Rien à viser : pas de contour au survol, le décor se regarde comme un objet. */
    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMES[s.getValue(FORME)];
    }

    /** La caméra (troisième personne) bute aussi sur le modèle. */
    @Override
    public VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMES[s.getValue(FORME)];
    }

    @Override
    public SoundType getSoundType(BlockState s, LevelReader l, BlockPos p, Entity e) {
        return SONS[s.getValue(FORME)];
    }

    @Override
    public FluidState getFluidState(BlockState s) {
        return s.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(s);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState voisin, LevelAccessor l, BlockPos p, BlockPos pv) {
        if (s.getValue(WATERLOGGED)) {
            l.scheduleTick(p, Fluids.WATER, Fluids.WATER.getTickDelay(l));
        }
        return s;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return !s.getValue(WATERLOGGED);
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0f;
    }

    /** Pas de particules de pas ni de chute (le bloc est invisible : on marche sur le modèle). */
    @Override
    public boolean addLandingEffects(BlockState s, ServerLevel l, BlockPos p, BlockState s2, LivingEntity e, int n) {
        return true;
    }

    @Override
    public boolean addRunningEffects(BlockState s, Level l, BlockPos p, Entity e) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }
}
