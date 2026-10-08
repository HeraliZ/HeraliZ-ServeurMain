package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.duel.NavalBoard;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;

/**
 * Bataille navale en duel, a une vraie table : chaque joueur est assis a un bout, un paravent au milieu cache les
 * flottes. Placement (au hasard, puis chacun deplace / tourne ses navires), puis on tire chacun son tour en cliquant
 * la grille verticale du paravent. Touche = on rejoue. Le premier qui coule toute la flotte adverse gagne le pot.
 * Temps ecoule : un tir au hasard est fait a la place du joueur. Les spectateurs ne voient que les tirs et les epaves.
 */
public class NavalBlockEntity extends DuelBlockEntity {
    public static final int ACTION_SHUFFLE = 10, ACTION_READY = 11, ACTION_SHOOT = 12, ACTION_PLACE = 13;
    public static final int SETUP = 0, BATTLE = 1;
    /** Duree du vol d'un tir (ticks) avant l'impact. */
    public static final int SHOT_TICKS = 12;

    /** Places : joueur A au bout gauche de la table (regarde +X), B au bout droit (regarde -X). */
    public static final Spot SEAT_A = new Spot(-12.5f, 0, 8, 1, 0, -12, 24, true, -0.26f, 70);
    public static final Spot SEAT_B = new Spot(28.5f, 0, 8, -1, 0, 28, 24, true, -0.26f, 70);

    // ---- synchronise (tout est public sauf l'emplacement des navires intacts)
    public int step = SETUP;
    public boolean readyA, readyB;
    /** Joueur qui tire (1 = A, 2 = B). */
    public int turn = 1;
    /** Tirs recus par la flotte de A / de B, cases touchees, cases des navires coules. */
    public long shotsOnA, hitsOnA, sunkOnA, shotsOnB, hitsOnB, sunkOnB;
    /** Epaves (navires coules) de A et de B : -1 ou x | y << 4 | horizontal << 8 | longueur << 9. */
    public final int[] wrecksA = new int[NavalBoard.SHIPS.length], wrecksB = new int[NavalBoard.SHIPS.length];
    public int lastShot = -1, lastShotSide;
    public long lastShotTick;

    // ---- client : ma flotte (recue en prive) ; placement : navire tenu en main (0 aucun), son sens, point de la table
    // sous la souris (repere du modele, NaN hors de la table), case de proue proposee (-1) et validite
    public int[] clientBoard = new int[0];
    public int clientHeld;
    public boolean clientHeldH = true, clientSnapOk;
    public float clientHoldX = Float.NaN, clientHoldZ = Float.NaN;
    public int clientSnap = -1;

    public NavalBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.BATAILLE_NAVALE, pos, state);
        Arrays.fill(wrecksA, -1);
        Arrays.fill(wrecksB, -1);
    }

    @Override
    protected DuelRules rules() {
        return CasinoConfig.get().batailleNavale;
    }

    @Override
    protected Spot spot(int side) {
        return side == 1 ? SEAT_A : SEAT_B;
    }

    /** Navires coules d'un joueur. */
    public int wrecks(int side) {
        int n = 0;
        for (int w : side == 1 ? wrecksA : wrecksB) if (w >= 0) n++;
        return n;
    }

    @Override
    public void receivePrivate(long[] data) {
        if (data.length != NavalBoard.CELLS) return;
        clientBoard = new int[NavalBoard.CELLS];
        for (int i = 0; i < data.length; i++) clientBoard[i] = (int) data[i];
    }

    @Override
    protected void writeGame(CompoundTag tag) {
        tag.putInt("Step", step);
        tag.putBoolean("ReadyA", readyA);
        tag.putBoolean("ReadyB", readyB);
        tag.putInt("Turn", turn);
        tag.putLongArray("Shots", new long[]{shotsOnA, hitsOnA, sunkOnA, shotsOnB, hitsOnB, sunkOnB});
        tag.putIntArray("WrecksA", wrecksA);
        tag.putIntArray("WrecksB", wrecksB);
        tag.putInt("LastShot", lastShot);
        tag.putInt("LastShotSide", lastShotSide);
        tag.putLong("LastShotTick", lastShotTick);
    }

    @Override
    protected void readGame(CompoundTag tag) {
        step = tag.getInt("Step");
        readyA = tag.getBoolean("ReadyA");
        readyB = tag.getBoolean("ReadyB");
        turn = tag.getInt("Turn");
        long[] s = tag.getLongArray("Shots");
        if (s.length == 6) {
            shotsOnA = s[0];
            hitsOnA = s[1];
            sunkOnA = s[2];
            shotsOnB = s[3];
            hitsOnB = s[4];
            sunkOnB = s[5];
        }
        int[] wa = tag.getIntArray("WrecksA"), wb = tag.getIntArray("WrecksB");
        if (wa.length == wrecksA.length) System.arraycopy(wa, 0, wrecksA, 0, wa.length);
        if (wb.length == wrecksB.length) System.arraycopy(wb, 0, wrecksB, 0, wb.length);
        lastShot = tag.getInt("LastShot");
        lastShotSide = tag.getInt("LastShotSide");
        lastShotTick = tag.getLong("LastShotTick");
        if (phase != PLAYING && phase != DONE) clientBoard = new int[0];
    }
}
