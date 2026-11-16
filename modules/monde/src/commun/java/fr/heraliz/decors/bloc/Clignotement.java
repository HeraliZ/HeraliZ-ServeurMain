package fr.heraliz.decors.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Lampes rouges qui clignotent (1.30.3, repris du jar Décors de la nouvelle carte) : toutes sur la même horloge, allumées
 * 5 s puis éteintes 1 s (cycle de {@link #CYCLE} ticks, dont {@link #NOIR} dans le noir). L'état {@link #ALLUME} est changé
 * par le serveur aux bons moments (tick programmé) ; au chargement d'un morceau de carte, la partie serveur relance les
 * lampes qui y sont posées ({@code serveur/ClignotementServeur}).
 */
public final class Clignotement {
    public static final BooleanProperty ALLUME = BooleanProperty.create("allume");
    public static final int CYCLE = 120;
    public static final int NOIR = 20;

    private Clignotement() {}

    public static boolean allumeA(long t) {
        return Math.floorMod(t, CYCLE) < CYCLE - NOIR;
    }

    private static int attente(long t) {
        int m = (int) Math.floorMod(t, (long) CYCLE);
        return m < CYCLE - NOIR ? CYCLE - NOIR - m : CYCLE - m;
    }

    public static boolean clignote(Block b) {
        return b instanceof LampeBunker || b instanceof LampeFixe || b instanceof Gyrophare;
    }

    public static void demarrer(Level l, BlockPos p, Block b) {
        if (!l.isClientSide) {
            l.scheduleTick(p, b, 1);
        }
    }

    public static void basculer(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        long t = l.getGameTime();
        boolean a = allumeA(t);
        if (s.getValue(ALLUME) != a) {
            l.setBlock(p, s.setValue(ALLUME, a), 2);
        }
        l.scheduleTick(p, s.getBlock(), attente(t));
    }

    public static void relancer(ServerLevel l, BlockPos p, Block b, RandomSource r) {
        if (!l.getBlockTicks().hasScheduledTick(p, b)) {
            l.scheduleTick(p, b, 1);
        }
    }

    public static int lumiere(BlockState s) {
        return s.getValue(ALLUME) ? 15 : 0;
    }
}
