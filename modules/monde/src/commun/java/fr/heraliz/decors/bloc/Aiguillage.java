package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Aiguillage extends Deco.Oriente {
    public static final EnumProperty<Aiguillage.Cote> COTE = EnumProperty.create("cote", Aiguillage.Cote.class);

    public Aiguillage(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, false, Effet.AUCUN);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COTE, Aiguillage.Cote.DROITE));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(COTE);
    }

    public static enum Cote implements StringRepresentable {
        GAUCHE("gauche"),
        DROITE("droite");

        private final String n;

        private Cote(String n) {
            this.n = n;
        }

        @Override
        public String getSerializedName() {
            return this.n;
        }
    }
}
