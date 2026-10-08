package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.game.race.RaceGame;
import fr.heraliz.casino.game.race.RaceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import static fr.heraliz.casino.block.MachinesServeur.RNG;

/**
 * Partie SERVEUR de {@link HorseRaceBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new HorseRaceBlockEntity(...) » côté serveur.
 */
public class HorseRaceBlockEntityServeur extends HorseRaceBlockEntity {
   public HorseRaceBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    // ---- serveur
    private double[] chances;

    /** Nouvelle course : forme des chevaux et cotes. */
    private void prepare() {
        chances = RaceGame.randomChances(RNG);
        odds = RaceGame.odds(chances, rules().rtp);
        raceSeed = RNG.nextLong();
        winner = -1;
    }

    @Override
    protected int bettingTicks() {
        return rules().bettingSeconds * 20;
    }

    @Override
    protected boolean validSpot(int spot) {
        return spot >= 0 && spot < RaceRules.HORSES;
    }

    @Override
    protected void onStart(ServerLevel level) {
        if (chances == null) prepare();
        raceTicks = rules().raceSeconds * 20;
        winner = RaceGame.pick(chances, RNG);
    }

    @Override
    protected boolean runningDone(long now) {
        return now >= phaseStart + raceTicks + 30;
    }

    @Override
    protected long payoutFor(Bet bet) {
        if (winner < 0 || bet.spot != winner) return 0;
        return (long) Math.floor(bet.amount * odds[winner]);
    }

    @Override
    protected void onSettled(ServerLevel level) {
        int[] h = new int[Math.min(10, history.length + 1)];
        h[0] = winner;
        System.arraycopy(history, 0, h, 1, h.length - 1);
        history = h;
    }

    @Override
    public void tickServer(ServerLevel level) {
        if (chances == null && phase == IDLE) {
            prepare();
            sync();
        }
        int before = phase;
        super.tickServer(level);
        if (before == RESULT && phase == IDLE) {
            prepare();
            sync();
        }
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        if (chances != null) {
            long[] c = new long[chances.length];
            for (int i = 0; i < c.length; i++) c[i] = Double.doubleToLongBits(chances[i]);
            tag.putLongArray("Chances", c);
        }
    }

    @Override
    protected void readServer(CompoundTag tag) {
        long[] c = tag.getLongArray("Chances");
        if (c.length == RaceRules.HORSES) {
            chances = new double[c.length];
            for (int i = 0; i < c.length; i++) chances[i] = Double.longBitsToDouble(c[i]);
        }
    }
}
