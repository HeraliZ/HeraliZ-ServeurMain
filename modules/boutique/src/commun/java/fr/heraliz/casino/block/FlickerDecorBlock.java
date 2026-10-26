package fr.heraliz.casino.block;

import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.decor.DecorType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Lumiere qui gresille pour de bon (reglette a tubes du Trou) : le serveur fait varier la lumiere du bloc par a-coups
 * (pleine, faible, coupee), la salle entiere clignote avec elle. Le modele 3D suit l'etat du bloc et ajoute ses propres
 * parasites, plus rapides (purement visuels).
 * <ul>
 * <li>variante 0, "Gresille" : allumee, puis des rafales de claquements, parfois une coupure et le starter qui
 * reessaie ;</li>
 * <li>variante 1, "Agonise" : presque toujours eteinte, quelques eclairs de temps en temps (salle tres sombre) ;</li>
 * <li>variante 2, "Stable" : allumee, un battement de temps en temps.</li>
 * </ul>
 */
public class FlickerDecorBlock extends DecorBlock {
    /** 0 : pleine lumiere, 1 : faible, 2 : coupee. */
    public static final IntegerProperty FLICKER = IntegerProperty.create("flicker", 0, 2);

    public FlickerDecorBlock(DecorType type, Properties properties) {
        super(type, properties);
        registerDefaultState(defaultBlockState().setValue(FLICKER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLICKER);
    }

    /** Lumiere du bloc selon l'etat. */
    public static int light(BlockState s, int full) {
        if (!s.getValue(ON)) return 0;
        return switch (s.getValue(FLICKER)) {
            case 1 -> Math.max(1, full / 2 - 1);
            case 2 -> 0;
            default -> full;
        };
    }

    /** Prochain etat et delai avant le suivant (ticks). */
    record Step(int level, int delay) {
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ON)) return;
        if (!(level.getBlockEntity(pos) instanceof DecorBlockEntity be)) return;
        Step step = next(state.getValue(FLICKER), Math.floorMod(be.mode, 3), be, random);
        if (step.level() != state.getValue(FLICKER)) level.setBlock(pos, state.setValue(FLICKER, step.level()), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, Math.max(1, step.delay()));
    }

    static Step next(int cur, int mode, DecorBlockEntity be, RandomSource r) {
        switch (mode) {
            case 2 -> {
                // stable : un battement de temps en temps
                if (cur != 0) return new Step(0, 200 + r.nextInt(400));
                return r.nextInt(3) == 0 ? new Step(1, 2) : new Step(0, 200 + r.nextInt(400));
            }
            case 1 -> {
                // agonise : dans le noir, des eclairs par rafales
                if (be.flickerBurst > 0) {
                    be.flickerBurst--;
                    if (cur == 2) return new Step(r.nextInt(3) == 0 ? 1 : 0, 1 + r.nextInt(3));
                    return new Step(2, 1 + r.nextInt(5));
                }
                if (cur != 2) return new Step(2, 40 + r.nextInt(140));
                be.flickerBurst = 3 + r.nextInt(8);
                return new Step(0, 1 + r.nextInt(2));
            }
            default -> {
                // gresille : allumee, rafales de claquements, parfois une coupure puis le starter qui reessaie
                if (be.flickerBurst > 0) {
                    be.flickerBurst--;
                    if (be.flickerBurst == 0) {
                        if (r.nextInt(5) == 0) {
                            be.flickerDead = true;
                            return new Step(2, 20 + r.nextInt(60));
                        }
                        return new Step(0, 30 + r.nextInt(130));
                    }
                    int nl = cur == 0 ? (r.nextBoolean() ? 2 : 1) : (r.nextInt(3) == 0 ? 3 - cur : 0);
                    return new Step(nl, 1 + r.nextInt(4));
                }
                if (be.flickerDead) {
                    be.flickerDead = false;
                    be.flickerBurst = 3 + r.nextInt(5);
                    return new Step(0, 2);
                }
                be.flickerBurst = 4 + r.nextInt(11);
                return new Step(r.nextBoolean() ? 2 : 1, 1 + r.nextInt(3));
            }
        }
    }
}
