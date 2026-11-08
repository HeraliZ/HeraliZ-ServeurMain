package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Arcade extends Deco {
    public static final EnumProperty<Arcade.Rang> RANG = EnumProperty.create("rang", Arcade.Rang.class);
    public static final EnumProperty<Arcade.Cote> COTE = EnumProperty.create("cote", Arcade.Cote.class);
    public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public Arcade(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(RANG, Arcade.Rang.BAS).setValue(COTE, Arcade.Cote.GAUCHE).setValue(AXIS, Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(RANG, COTE, AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return this.defaultBlockState().setValue(AXIS, c.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return r != Rotation.CLOCKWISE_90 && r != Rotation.COUNTERCLOCKWISE_90 ? s : s.setValue(AXIS, s.getValue(AXIS) == Axis.X ? Axis.Z : Axis.X);
    }

    public static enum Cote implements StringRepresentable {
        GAUCHE,
        DROITE;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static enum Rang implements StringRepresentable {
        BAS,
        MILIEU,
        HAUT;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
