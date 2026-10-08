package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base des machines de 2 blocs de large centrees sur une case (Pack-a-Punch, etabli) : la case maitresse
 * ({@link #PARTIE} 0, bloc-entite) est au centre, les cases voisines a gauche et a droite (vu de face) n'occupent que leur
 * moitie interieure, ce qui donne une emprise de 2 blocs exactement centree sur la position de la carte ; les cases du
 * dessus (3, 4, 5) font de meme. {@link #FACING} = vers la salle. Formes calculees une fois par orientation.
 * Clic droit sur n'importe quelle case = interaction du jeu.
 */
public abstract class BlocLarge extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 0 centre bas (maitre), 1 gauche bas, 2 droite bas, 3 centre haut, 4 gauche haut, 5 droite haut. */
    public static final IntegerProperty PARTIE = IntegerProperty.create("partie", 0, 5);

    /** Formes [partie][orientation 2D]. */
    private final VoxelShape[][] formes = new VoxelShape[6][4];

    /**
     * @param hauteurBas  hauteur (px) des cases du bas
     * @param hauteurHaut hauteur (px) des cases du haut (0 = sans collision)
     * @param z0          debut de l'emprise en profondeur (px, 0 = fond de la case) pour une machine tournee vers le sud
     */
    protected BlocLarge(Properties p, double hauteurBas, double hauteurHaut, double z0, double z1) {
        super(p);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int k = d.get2DDataValue();
            formes[0][k] = tourner(0, 16, z0, z1, hauteurBas, d);
            formes[1][k] = tourner(8, 16, z0, z1, hauteurBas, d);
            formes[2][k] = tourner(0, 8, z0, z1, hauteurBas, d);
            formes[3][k] = hauteurHaut <= 0 ? Shapes.empty() : tourner(0, 16, z0, z1, hauteurHaut, d);
            formes[4][k] = hauteurHaut <= 0 ? Shapes.empty() : tourner(8, 16, z0, z1, hauteurHaut, d);
            formes[5][k] = hauteurHaut <= 0 ? Shapes.empty() : tourner(0, 8, z0, z1, hauteurHaut, d);
        }
    }

    /**
     * Boite definie pour une machine tournee vers le sud (x0..x1 dans la case, z0..z1), tournee comme le modele
     * ({@code ps.mulPose(YP(-facing.toYRot()))}) autour du centre de la case.
     */
    static VoxelShape tourner(double x0, double x1, double z0, double z1, double h, Direction f) {
        double a = Math.toRadians(-f.toYRot());
        double c = Math.cos(a), s = Math.sin(a);
        double minX = 16, minZ = 16, maxX = 0, maxZ = 0;
        for (double x : new double[]{x0, x1}) {
            for (double z : new double[]{z0, z1}) {
                double cx = x - 8, cz = z - 8;
                double wx = cx * c + cz * s + 8, wz = -cx * s + cz * c + 8;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return Block.box(Mth.clamp(Math.round(minX * 1000) / 1000.0, 0, 16), 0, Mth.clamp(Math.round(minZ * 1000) / 1000.0, 0, 16),
                Mth.clamp(Math.round(maxX * 1000) / 1000.0, 0, 16), h, Mth.clamp(Math.round(maxZ * 1000) / 1000.0, 0, 16));
    }

    /** Position de la case d'une partie a partir de la case maitresse. */
    public static BlockPos position(BlockPos maitre, Direction facing, int partie) {
        BlockPos p = maitre;
        if (partie >= 3) p = p.above();
        int lat = partie % 3;
        if (lat == 1) p = p.relative(facing.getClockWise());
        else if (lat == 2) p = p.relative(facing.getCounterClockWise());
        return p;
    }

    /** Case maitresse a partir d'une case quelconque. */
    public static BlockPos maitre(BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        int partie = state.getValue(PARTIE);
        BlockPos p = partie >= 3 ? pos.below() : pos;
        int lat = partie % 3;
        if (lat == 1) p = p.relative(f.getCounterClockWise());
        else if (lat == 2) p = p.relative(f.getClockWise());
        return p;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PARTIE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PARTIE) == 0 ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return formes[state.getValue(PARTIE)][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
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
