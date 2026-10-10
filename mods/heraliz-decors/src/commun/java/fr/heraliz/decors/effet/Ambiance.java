package fr.heraliz.decors.effet;

import fr.heraliz.decors.Particules;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Émetteur d'ambiance invisible : brume, brume basse sur l'eau, embruns au pied d'une cascade, lucioles, poussière
 * dorée dans un rayon de soleil. Le jeu ne l'anime que près du joueur (animateTick, côté client), à faible débit.
 */
public class Ambiance extends Block {
    public enum Genre implements StringRepresentable {
        BRUME("brume"), BRUME_BASSE("brume_basse"), EMBRUNS("embruns"), LUCIOLES("lucioles"), POUSSIERE_DOREE("poussiere_doree");
        private final String n;
        Genre(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }

    public static final EnumProperty<Genre> GENRE = EnumProperty.create("genre", Genre.class);

    public Ambiance(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(GENRE, Genre.BRUME));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(GENRE);
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
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        double x = p.getX() + 0.5, y = p.getY(), z = p.getZ() + 0.5;
        switch (s.getValue(GENRE)) {
            case BRUME -> l.addParticle(Particules.BRUME.get(), x + (r.nextDouble() - 0.5) * 4, y + r.nextDouble() * 1.5,
                    z + (r.nextDouble() - 0.5) * 4, (r.nextDouble() - 0.5) * 0.008, 0.0015, (r.nextDouble() - 0.5) * 0.008);
            case BRUME_BASSE -> {
                if (r.nextInt(2) == 0) {
                    l.addParticle(Particules.BRUME_BASSE.get(), x + (r.nextDouble() - 0.5) * 6, y + 0.1 + r.nextDouble() * 0.5,
                            z + (r.nextDouble() - 0.5) * 6, (r.nextDouble() - 0.5) * 0.01, 0, 0.004 + r.nextDouble() * 0.006);
                }
            }
            case EMBRUNS -> {
                for (int i = 0; i < 3; i++) {
                    l.addParticle(Particules.EMBRUN.get(), x + (r.nextDouble() - 0.5) * 2, y + 0.2 + r.nextDouble() * 0.5,
                            z + (r.nextDouble() - 0.5) * 2, (r.nextDouble() - 0.5) * 0.08, 0.03 + r.nextDouble() * 0.05,
                            (r.nextDouble() - 0.5) * 0.08);
                }
                if (r.nextInt(2) == 0) {
                    l.addParticle(Particules.BRUME.get(), x + (r.nextDouble() - 0.5) * 3, y + 0.6, z + (r.nextDouble() - 0.5) * 3,
                            (r.nextDouble() - 0.5) * 0.01, 0.008, (r.nextDouble() - 0.5) * 0.01);
                }
                if (r.nextInt(4) == 0) {
                    l.playLocalSound(x, y, z, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT, 1.6f, 0.55f + r.nextFloat() * 0.3f, false);
                }
            }
            case LUCIOLES -> {
                if (r.nextInt(3) == 0) {
                    l.addParticle(Particules.LUCIOLE.get(), x + (r.nextDouble() - 0.5) * 8, y + r.nextDouble() * 3,
                            z + (r.nextDouble() - 0.5) * 8, 0, 0, 0);
                }
            }
            case POUSSIERE_DOREE -> {
                for (int i = 0; i < 2; i++) {
                    l.addParticle(Particules.POUSSIERE_DOREE.get(), x + (r.nextDouble() - 0.5) * 5, y + (r.nextDouble() - 0.5) * 5,
                            z + (r.nextDouble() - 0.5) * 5, (r.nextDouble() - 0.5) * 0.003, -0.002 - r.nextDouble() * 0.003,
                            (r.nextDouble() - 0.5) * 0.003);
                }
            }
        }
    }
}
