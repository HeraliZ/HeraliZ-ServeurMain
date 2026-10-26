package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * Le Demineur : 5 x 5 dalles, des mines cachees (position secrete, serveur uniquement).
 * Chaque dalle sure fait monter le gain ; on encaisse quand on veut ; une mine fait tout perdre.
 */
public class MinesBlockEntity extends MachineBlockEntity {
    public static final int ACTION_START = 0, ACTION_REVEAL = 1, ACTION_CASHOUT = 2;
    public static final int IDLE = 0, PLAYING = 1, BOOM = 2, CASHED = 3;
    /** Affichage du plateau apres la fin avant de pouvoir rejouer (ticks). */
    public static final int SHOW_END = 50;

    // ---- synchronise
    public int phase = IDLE;
    public UUID player;
    public String playerName = "";
    public long bet;
    public int mines = 3;
    public int revealed;
    public final long[] tileTicks = new long[MinesRules.TILES];
    public int boomTile = -1;
    public long endTick;
    public long startTick;
    public long mult100 = 100;
    public long win;
    /** Mines devoilees a la fin de la partie (0 pendant la partie). */
    public int mineShown;

    public MinesBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.MINES, pos, state);
    }

    MinesRules rules() {
        return CasinoConfig.get().mines;
    }

    public boolean free(long now) {
        return phase == IDLE || (phase != PLAYING && now >= endTick + SHOW_END);
    }

    /** Mise payee : on reste jusqu'a la mine ou l'encaissement. */
    @Override
    public boolean locks(@javax.annotation.Nullable UUID p) {
        return p != null && p.equals(player) && phase == PLAYING;
    }

    public int revealedCount() {
        return Integer.bitCount(revealed);
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        tag.putInt("Phase", phase);
        putUuid(tag, "Player", player);
        tag.putString("PlayerName", playerName);
        tag.putLong("Bet", bet);
        tag.putInt("Mines", mines);
        tag.putInt("Revealed", revealed);
        tag.putLongArray("TileTicks", tileTicks);
        tag.putInt("Boom", boomTile);
        tag.putLong("EndTick", endTick);
        tag.putLong("StartTick", startTick);
        tag.putLong("Mult", mult100);
        tag.putLong("Win", win);
        tag.putInt("MineShown", mineShown);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        phase = tag.getInt("Phase");
        player = getUuid(tag, "Player");
        playerName = tag.getString("PlayerName");
        bet = tag.getLong("Bet");
        mines = Math.max(1, tag.getInt("Mines"));
        revealed = tag.getInt("Revealed");
        long[] tt = tag.getLongArray("TileTicks");
        if (tt.length == MinesRules.TILES) System.arraycopy(tt, 0, tileTicks, 0, tt.length);
        boomTile = tag.contains("Boom") ? tag.getInt("Boom") : -1;
        endTick = tag.getLong("EndTick");
        startTick = tag.getLong("StartTick");
        mult100 = Math.max(100, tag.getLong("Mult"));
        win = tag.getLong("Win");
        mineShown = tag.getInt("MineShown");
    }
}
