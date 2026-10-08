package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.game.duel.Rps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import static fr.heraliz.casino.block.MachinesServeur.rnd;

/**
 * Partie SERVEUR de {@link RpsBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new RpsBlockEntity(...) » côté serveur.
 */
public class RpsBlockEntityServeur extends RpsBlockEntity {
   public RpsBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }

    private static final int MAX_ROUNDS = 15;

    // ---- serveur (secret jusqu'a la revelation)
    private int choiceA = Rps.NONE, choiceB = Rps.NONE;
    private int voids;

    @Override
    protected void resetGame() {
        step = CHOOSE;
        round = 0;
        scoreA = scoreB = 0;
        chosenA = chosenB = false;
        shownA = shownB = Rps.NONE;
        choiceA = choiceB = Rps.NONE;
        roundWinner = 0;
        voids = 0;
    }

    @Override
    protected void startGame(ServerLevel level, long now) {
        resetGame();
        nextRound(now);
    }

    private void nextRound(long now) {
        round++;
        step = CHOOSE;
        chosenA = chosenB = false;
        shownA = shownB = Rps.NONE;
        choiceA = choiceB = Rps.NONE;
        deadline = now + rules().turnSeconds * 20L;
    }

    @Override
    protected void handleGame(ServerPlayer p, int side, int action, long[] args) {
        if (action != ACTION_CHOOSE || args.length < 1 || step != CHOOSE) return;
        int c = (int) args[0];
        if (c < Rps.ROCK || c > Rps.SCISSORS) return;
        if (side == 1 && !chosenA) {
            choiceA = c;
            chosenA = true;
        } else if (side == 2 && !chosenB) {
            choiceB = c;
            chosenB = true;
        } else {
            return;
        }
        sendPrivate(side);
        if (chosenA && chosenB) reveal(now());
        sync();
    }

    private void reveal(long now) {
        shownA = choiceA;
        shownB = choiceB;
        if (choiceA == Rps.NONE && choiceB == Rps.NONE) {
            roundWinner = 0;
            voids++;
        } else {
            roundWinner = Rps.winner(choiceA, choiceB);
            if (roundWinner == 1) scoreA++;
            if (roundWinner == 2) scoreB++;
        }
        step = REVEAL;
        revealTick = now;
    }

    @Override
    protected void tickGame(ServerLevel level, long now) {
        if (step == CHOOSE && now >= deadline) {
            reveal(now);
            sync();
        } else if (step == REVEAL && now >= revealTick + REVEAL_TICKS) {
            int need = rules().winsNeeded;
            if (scoreA >= need) finish(level, WIN_A);
            else if (scoreB >= need) finish(level, WIN_B);
            else if (voids >= 3) finish(level, VOID);
            else if (round >= MAX_ROUNDS) finish(level, scoreA > scoreB ? WIN_A : scoreB > scoreA ? WIN_B : VOID);
            else {
                nextRound(now);
                sync();
                sendPrivate(1);
                sendPrivate(2);
            }
        }
    }

    /** Le bot reflechit un peu (1 a 3 s) puis joue au hasard. */
    @Override
    protected void tickBot(ServerLevel level, long now, int side) {
        if (step != CHOOSE || (side == 1 ? chosenA : chosenB)) return;
        long start = deadline - rules().turnSeconds * 20L;
        if (now < start + 20 + (round * 13L) % 40) return;
        botAct(side, ACTION_CHOOSE, Rps.ROCK + rnd(3));
    }

    @Override
    protected long[] privateData(int side) {
        return new long[]{side == 1 ? choiceA : choiceB, round};
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        tag.putInt("ChoiceA", choiceA);
        tag.putInt("ChoiceB", choiceB);
        tag.putInt("Voids", voids);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        if (!tag.contains("ChoiceA")) return;
        choiceA = tag.getInt("ChoiceA");
        choiceB = tag.getInt("ChoiceB");
        voids = tag.getInt("Voids");
    }
}
