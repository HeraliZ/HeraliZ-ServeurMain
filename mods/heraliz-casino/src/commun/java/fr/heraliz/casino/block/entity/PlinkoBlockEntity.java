package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.plinko.PlinkoGame;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plinko : chaque joueur lache des billes (plusieurs a la fois) ; chaque bille est une mise payee a son arrivee.
 */
public class PlinkoBlockEntity extends MachineBlockEntity {
    public static final int ACTION_DROP = 0;
    /** Intervalle entre deux billes d'une meme rafale (ticks). */
    public static final int STAGGER = 7;
    public static final int MAX_BALLS = 40;

    public static final class Ball {
        public UUID player;
        public String name;
        public long bet;
        public int path;
        public long start;
        public double multiplier;
        public boolean paid;

        public int bucket() {
            return PlinkoGame.bucket(path);
        }

        CompoundTag save(boolean full) {
            CompoundTag t = new CompoundTag();
            t.putUUID("P", player);
            t.putInt("Path", path);
            t.putLong("Start", start);
            if (full) {
                t.putString("N", name);
                t.putLong("Bet", bet);
                t.putDouble("M", multiplier);
                t.putBoolean("Paid", paid);
            }
            return t;
        }

        static Ball load(CompoundTag t) {
            Ball b = new Ball();
            b.player = t.getUUID("P");
            b.path = t.getInt("Path");
            b.start = t.getLong("Start");
            b.name = t.getString("N");
            b.bet = t.getLong("Bet");
            b.multiplier = t.getDouble("M");
            b.paid = !t.contains("Paid") || t.getBoolean("Paid");
            return b;
        }
    }

    // ---- synchronise
    public final List<Ball> balls = new ArrayList<>();
    public double[] multipliers = new PlinkoRules().multipliers;

    public PlinkoBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.PLINKO, pos, state);
    }

    PlinkoRules rules() {
        return CasinoConfig.get().plinko;
    }

    /** Une bille du joueur est encore en vol. */
    @Override
    public boolean engaged(@javax.annotation.Nullable UUID player) {
        if (player == null) return false;
        for (Ball b : balls) if (!b.paid && player.equals(b.player)) return true;
        return false;
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Ball b : balls) l.add(b.save(false));
        tag.put("Balls", l);
        long[] m = new long[multipliers.length];
        for (int i = 0; i < m.length; i++) m[i] = Math.round(multipliers[i] * 100);
        tag.putLongArray("Mult", m);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        balls.clear();
        ListTag l = tag.getList("Balls", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            try {
                balls.add(Ball.load(l.getCompound(i)));
            } catch (Exception ignored) {
            }
        }
        long[] m = tag.getLongArray("Mult");
        if (m.length == PlinkoGame.ROWS + 1) {
            multipliers = new double[m.length];
            for (int i = 0; i < m.length; i++) multipliers[i] = m[i] / 100.0;
        }
    }
}
