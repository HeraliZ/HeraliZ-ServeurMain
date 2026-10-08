package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.game.duel.NavalBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;

import static fr.heraliz.casino.block.MachinesServeur.RNG;
import static fr.heraliz.casino.block.MachinesServeur.rnd;

/**
 * Partie SERVEUR de {@link NavalBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new NavalBlockEntity(...) » côté serveur.
 */
public class NavalBlockEntityServeur extends NavalBlockEntity {
   public NavalBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    // ---- serveur (secret)
    private int[] boardA = new int[NavalBoard.CELLS], boardB = new int[NavalBoard.CELLS];

    @Override
    protected void resetGame() {
        step = SETUP;
        readyA = readyB = false;
        turn = 1;
        shotsOnA = hitsOnA = sunkOnA = shotsOnB = hitsOnB = sunkOnB = 0;
        Arrays.fill(wrecksA, -1);
        Arrays.fill(wrecksB, -1);
        lastShot = -1;
        lastShotSide = 0;
    }

    @Override
    protected void startGame(ServerLevel level, long now) {
        resetGame();
        // les navires attendent sur le cote de la table ; le bot place les siens au hasard
        boardA = isBot(1) ? NavalBoard.random(RNG) : new int[NavalBoard.CELLS];
        boardB = isBot(2) ? NavalBoard.random(RNG) : new int[NavalBoard.CELLS];
        deadline = now + rules().turnSeconds * 60L;
    }

    private void startBattle(long now) {
        step = BATTLE;
        turn = 1 + rnd(2);
        deadline = now + rules().turnSeconds * 20L;
    }

    @Override
    protected void handleGame(ServerPlayer p, int side, int action, long[] args) {
        long now = now();
        switch (action) {
            case ACTION_SHUFFLE -> {
                if (step != SETUP || (side == 1 ? readyA : readyB)) return;
                if (side == 1) boardA = NavalBoard.random(RNG);
                else boardB = NavalBoard.random(RNG);
                sendPrivate(side);
            }
            case ACTION_PLACE -> {
                // navire k, proue (x, y), horizontal (1/0) ; x < 0 : le navire retourne sur le cote de la table
                if (step != SETUP || (side == 1 ? readyA : readyB) || args.length < 4) return;
                int[] b = side == 1 ? boardA : boardB;
                int k = (int) args[0];
                if (args[1] < 0) {
                    if (k >= 1 && k <= NavalBoard.SHIPS.length) NavalBoard.remove(b, k);
                } else {
                    NavalBoard.move(b, k, (int) args[1], (int) args[2], args[3] != 0);
                }
                sendPrivate(side);
            }
            case ACTION_READY -> {
                if (step != SETUP || !NavalBoard.complete(side == 1 ? boardA : boardB)) return;
                if (side == 1) readyA = true;
                else readyB = true;
                if (readyA && readyB) startBattle(now);
                sync();
            }
            case ACTION_SHOOT -> {
                // un tir a la fois : on attend l'impact du precedent
                if (step != BATTLE || side != turn || args.length < 1 || now < lastShotTick + SHOT_TICKS) return;
                int cell = (int) args[0];
                if (cell < 0 || cell >= NavalBoard.CELLS) return;
                long shots = side == 1 ? shotsOnB : shotsOnA;
                if ((shots & (1L << cell)) != 0) return;
                shoot((ServerLevel) level, side, cell, now);
            }
            default -> {
            }
        }
    }

    private void shoot(ServerLevel level, int side, int cell, long now) {
        long bit = 1L << cell;
        boolean hit;
        int[] target = side == 1 ? boardB : boardA;
        if (side == 1) {
            shotsOnB |= bit;
            hit = boardB[cell] != 0;
            if (hit) hitsOnB |= bit;
            sunkOnB = NavalBoard.sunk(boardB, hitsOnB);
        } else {
            shotsOnA |= bit;
            hit = boardA[cell] != 0;
            if (hit) hitsOnA |= bit;
            sunkOnA = NavalBoard.sunk(boardA, hitsOnA);
        }
        updateWrecks(target, side == 1 ? hitsOnB : hitsOnA, side == 1 ? wrecksB : wrecksA);
        lastShot = cell;
        lastShotSide = side;
        lastShotTick = now;
        if (hit && NavalBoard.allSunk(target, side == 1 ? hitsOnB : hitsOnA)) {
            finish(level, side == 1 ? WIN_A : WIN_B);
            return;
        }
        // touche : on rejoue ; a l'eau : au tour de l'adversaire
        if (!hit) turn = 3 - turn;
        deadline = now + SHOT_TICKS + rules().turnSeconds * 20L;
        sync();
    }

    private static void updateWrecks(int[] board, long hits, int[] wrecks) {
        for (int k = 1; k <= NavalBoard.SHIPS.length; k++) {
            long cells = NavalBoard.shipCells(board, k);
            int[] s = NavalBoard.ship(board, k);
            wrecks[k - 1] = cells != 0 && (hits & cells) == cells && s != null ? s[0] | s[1] << 4 | s[2] << 8 | s[3] << 9 : -1;
        }
    }

    @Override
    protected void tickGame(ServerLevel level, long now) {
        if (now < deadline) return;
        if (step == SETUP) {
            // temps ecoule : les navires restes sur le cote sont places au hasard
            NavalBoard.fillMissing(boardA, RNG);
            NavalBoard.fillMissing(boardB, RNG);
            readyA = readyB = true;
            startBattle(now);
            sync();
            sendPrivate(1);
            sendPrivate(2);
        } else {
            // temps ecoule : tir au hasard a la place du joueur
            long shots = turn == 1 ? shotsOnB : shotsOnA;
            int free = NavalBoard.CELLS - Long.bitCount(shots);
            if (free <= 0) return;
            int k = rnd(free);
            for (int c = 0; c < NavalBoard.CELLS; c++) {
                if ((shots & (1L << c)) != 0) continue;
                if (k-- == 0) {
                    shoot(level, turn, c, now);
                    return;
                }
            }
        }
    }

    /** Le bot valide sa flotte (placee au hasard) puis tire : chasse en damier, acheve les navires touches. */
    @Override
    protected void tickBot(ServerLevel level, long now, int side) {
        if (step == SETUP) {
            if (!(side == 1 ? readyA : readyB) && now >= phaseStart + 40) botAct(side, ACTION_READY);
            return;
        }
        if (turn != side || now < lastShotTick + SHOT_TICKS + 16 || now < deadline - rules().turnSeconds * 20L + 16) return;
        long shots = side == 1 ? shotsOnB : shotsOnA, hits = side == 1 ? hitsOnB : hitsOnA, sunk = side == 1 ? sunkOnB : sunkOnA;
        int cell = fr.heraliz.casino.game.duel.NavalBot.pick(shots, hits, sunk, RNG);
        if (cell >= 0) botAct(side, ACTION_SHOOT, cell);
    }

    @Override
    protected long[] privateData(int side) {
        int[] b = side == 1 ? boardA : boardB;
        long[] out = new long[NavalBoard.CELLS];
        for (int i = 0; i < out.length; i++) out[i] = b[i];
        return out;
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        tag.putIntArray("BoardA", boardA);
        tag.putIntArray("BoardB", boardB);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        int[] a = tag.getIntArray("BoardA"), b = tag.getIntArray("BoardB");
        if (a.length == NavalBoard.CELLS) boardA = a;
        if (b.length == NavalBoard.CELLS) boardB = b;
    }
}
