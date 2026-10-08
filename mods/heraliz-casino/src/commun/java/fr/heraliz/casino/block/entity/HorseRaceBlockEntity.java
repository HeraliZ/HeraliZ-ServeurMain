package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.race.RaceRules;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Course de chevaux : cotes affichees, mises sur le vainqueur pendant le compte a rebours, puis la course.
 * Les chances reelles restent sur le serveur ; le client rejoue la course a partir de la graine et du gagnant.
 */
public class HorseRaceBlockEntity extends RoundBlockEntity {
    // ---- synchronise
    public double[] odds = new double[RaceRules.HORSES];
    public long raceSeed;
    public int winner = -1;
    public int raceTicks = 240;
    public int[] history = new int[0];

    public HorseRaceBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.COURSE, pos, state);
        java.util.Arrays.fill(odds, 4.0);
    }

    RaceRules rules() {
        return CasinoConfig.get().course;
    }

    @Override
    protected int resultTicks() {
        return 100;
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        super.writeSync(tag);
        long[] o = new long[odds.length];
        for (int i = 0; i < o.length; i++) o[i] = Math.round(odds[i] * 100);
        tag.putLongArray("Odds", o);
        tag.putLong("Seed", raceSeed);
        tag.putInt("Winner", winner);
        tag.putInt("RaceTicks", raceTicks);
        tag.putIntArray("History", history);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        super.readSync(tag);
        long[] o = tag.getLongArray("Odds");
        if (o.length == RaceRules.HORSES) for (int i = 0; i < o.length; i++) odds[i] = o[i] / 100.0;
        raceSeed = tag.getLong("Seed");
        winner = tag.contains("Winner") ? tag.getInt("Winner") : -1;
        raceTicks = Math.max(60, tag.getInt("RaceTicks"));
        history = tag.getIntArray("History");
    }
}
