package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.MachinesV3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Morceau cuit d'une tres grande machine v3 (tour du mainframe, plateforme) : un modele {@code zbquads} de 4 x 4 x 4 blocs
 * au plus, rendu avec le troncon de sa propre case (jamais masque parce que la case d'origine de la machine sort de
 * l'ecran). Pas de bloc-entite : zero cout par image ; collision seulement si la case a une forme fine (0.2.3). Etat de bloc : {@code zb_piece.json}, genere par
 * {@code exporter_machines_v3.py} ({@link #PIECE} = index du morceau).
 */
public class BlocPiece extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, Math.max(1, MachinesV3.nombrePieces() - 1));

    public BlocPiece(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PIECE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PIECE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // 0.2.3 : forme fine de la case du modele si elle en a une (normalement aucune : l'export evite ces cases)
        return MachinesV3.formePiece(state.getValue(PIECE), state.getValue(FACING));
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
        return MachinesV3.formePiece(state.getValue(PIECE), state.getValue(FACING)).isEmpty();
    }
}
