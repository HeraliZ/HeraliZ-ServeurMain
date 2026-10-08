package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.duel.Rps;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pierre-feuille-ciseaux en duel : chaque manche, les deux joueurs choisissent en secret ; les coups sont reveles
 * ensemble. Le premier a remporter le nombre de manches voulu gagne le pot. Un joueur qui ne choisit pas a temps
 * perd la manche.
 */
public class RpsBlockEntity extends DuelBlockEntity {
    public static final int ACTION_CHOOSE = 10;
    public static final int CHOOSE = 0, REVEAL = 1;
    /** Duree de la revelation animee (voir Rps). */
    public static final int REVEAL_TICKS = Rps.REVEAL_TICKS;

    /** Places debout : A a gauche de la table (regarde +X), B a droite (regarde -X). */
    public static final Spot SPOT_A = new Spot(-12, 0, 8, 1, 0, -12, 24, false, 0f, 25);
    public static final Spot SPOT_B = new Spot(28, 0, 8, -1, 0, 28, 24, false, 0f, 25);

    // ---- synchronise
    public int step = CHOOSE;
    public int round, scoreA, scoreB;
    public boolean chosenA, chosenB;
    /** Coups reveles (Rps.NONE avant la revelation). */
    public int shownA = Rps.NONE, shownB = Rps.NONE;
    /** Resultat de la derniere manche : 0 egalite, 1 A, 2 B. */
    public int roundWinner;
    public long revealTick;

    // ---- client : mon coup (recu en prive)
    public int clientChoice = Rps.NONE;

    public RpsBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.PIERRE_FEUILLE_CISEAUX, pos, state);
    }

    @Override
    protected DuelRules rules() {
        return CasinoConfig.get().pierreFeuilleCiseaux;
    }

    @Override
    protected Spot spot(int side) {
        return side == 1 ? SPOT_A : SPOT_B;
    }

    /** Score affiche (le point de la manche n'est compte qu'a la fin de l'animation). */
    public int shownScore(int side, float sinceReveal) {
        int s = side == 1 ? scoreA : scoreB;
        if (step == REVEAL && roundWinner == side && sinceReveal < Rps.T_RESULT) s--;
        return Math.max(0, s);
    }

    @Override
    public void receivePrivate(long[] data) {
        if (data.length >= 1) clientChoice = (int) data[0];
    }

    @Override
    protected void writeGame(CompoundTag tag) {
        tag.putInt("Step", step);
        tag.putInt("Round", round);
        tag.putInt("ScoreA", scoreA);
        tag.putInt("ScoreB", scoreB);
        tag.putBoolean("ChosenA", chosenA);
        tag.putBoolean("ChosenB", chosenB);
        tag.putInt("ShownA", shownA);
        tag.putInt("ShownB", shownB);
        tag.putInt("RoundWinner", roundWinner);
        tag.putLong("RevealTick", revealTick);
    }

    @Override
    protected void readGame(CompoundTag tag) {
        int oldRound = round;
        step = tag.getInt("Step");
        round = tag.getInt("Round");
        scoreA = tag.getInt("ScoreA");
        scoreB = tag.getInt("ScoreB");
        chosenA = tag.getBoolean("ChosenA");
        chosenB = tag.getBoolean("ChosenB");
        shownA = tag.contains("ShownA") ? tag.getInt("ShownA") : Rps.NONE;
        shownB = tag.contains("ShownB") ? tag.getInt("ShownB") : Rps.NONE;
        roundWinner = tag.getInt("RoundWinner");
        revealTick = tag.getLong("RevealTick");
        if (round != oldRound) clientChoice = Rps.NONE;
    }
}
