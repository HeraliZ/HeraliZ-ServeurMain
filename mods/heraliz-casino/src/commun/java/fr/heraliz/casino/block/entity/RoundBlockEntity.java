package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Jeu en tours communs (Grande Roue, Roulette, Crash, Course) : la premiere mise lance le compte a rebours,
 * tout le monde mise pendant ce temps, puis le tour est joue pour tous et les gagnants sont payes.
 */
public abstract class RoundBlockEntity extends MachineBlockEntity {
    public static final int IDLE = 0, BETTING = 1, RUNNING = 2, RESULT = 3;
    public static final int ACTION_BET = 0, ACTION_CANCEL = 1;
    /** Mises au maximum par tour (securite). */
    public static final int MAX_BETS = 400;

    /** Une mise d'un joueur sur une case. "extra" : donnee propre au jeu (ex. encaissement du Crash). */
    public static final class Bet {
        public UUID player;
        public String name;
        public int spot;
        public long amount;
        public long extra;

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("P", player);
            t.putString("N", name);
            t.putInt("S", spot);
            t.putLong("A", amount);
            t.putLong("X", extra);
            return t;
        }

        public static Bet load(CompoundTag t) {
            Bet b = new Bet();
            b.player = t.getUUID("P");
            b.name = t.getString("N");
            b.spot = t.getInt("S");
            b.amount = t.getLong("A");
            b.extra = t.getLong("X");
            return b;
        }
    }

    // ---- synchronise
    public int phase = IDLE;
    public long phaseStart;
    public long bettingEnd;
    public final List<Bet> bets = new ArrayList<>();

    protected RoundBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // =================================================================== a definir par le jeu
    protected int bettingTicks() {
        return 0;
    }

    /** Pause d'affichage du resultat. */
    protected int resultTicks() {
        return 100;
    }

    protected boolean validSpot(int spot) {
        return false;
    }

    /** Fin des mises : tirer le resultat (phase RUNNING). Version serveur de chaque jeu. */
    protected void onStart(ServerLevel level) {
    }

    /** Animation terminee ? Version serveur de chaque jeu. */
    protected boolean runningDone(long now) {
        return false;
    }

    /** Somme rendue pour une mise (0 si perdue). Version serveur de chaque jeu. */
    protected long payoutFor(Bet bet) {
        return 0;
    }

    /** Apres le paiement (historique...). */
    protected void onSettled(ServerLevel level) {
    }

    /** Autres actions du jeu. */
    protected void handleOther(ServerPlayer player, int action, long[] args) {
    }

    // =================================================================== partie serveur (docs/METHODE_CLIENT_SERVEUR.md)
    /**
     * Deroulement commun des tours (src/serveur : RoundsServeur) : mises, annulation, compte a rebours, reglement.
     * Branche par la partie serveur ; sans elle, la machine est neutre.
     */
    public interface Moteur {
        void handleAction(RoundBlockEntity be, ServerPlayer player, int action, long[] args);

        boolean placeBet(RoundBlockEntity be, ServerPlayer player, int spot, long amount);

        void cancel(RoundBlockEntity be, ServerPlayer player);

        void tickServer(RoundBlockEntity be, ServerLevel level);

        void settle(RoundBlockEntity be, ServerLevel level);

        void onRemoved(RoundBlockEntity be, ServerLevel level);
    }

    static volatile Moteur moteur;

    // =================================================================== actions
    @Override
    public void handleAction(ServerPlayer player, int action, long[] args) {
        Moteur m = moteur;
        if (m != null) m.handleAction(this, player, action, args);
    }

    public boolean acceptingBets() {
        return phase == IDLE || phase == BETTING;
    }

    public long totalOf(UUID player) {
        long t = 0;
        for (Bet b : bets) if (b.player.equals(player)) t += b.amount;
        return t;
    }

    /** Mise posee sur le tour qui se prepare ou qui se joue. */
    @Override
    public boolean engaged(@javax.annotation.Nullable UUID player) {
        return player != null && (phase == BETTING || phase == RUNNING) && totalOf(player) > 0;
    }

    protected boolean placeBet(ServerPlayer player, int spot, long amount) {
        Moteur m = moteur;
        return m != null && m.placeBet(this, player, spot, amount);
    }

    /** Annule toutes les mises du joueur pendant le compte a rebours (remboursees). */
    protected void cancel(ServerPlayer player) {
        Moteur m = moteur;
        if (m != null) m.cancel(this, player);
    }

    // =================================================================== tick
    @Override
    protected void tickServer(ServerLevel level) {
        Moteur m = moteur;
        if (m != null) m.tickServer(this, level);
    }

    /** Paie les gagnants (un seul versement et un seul message par joueur). */
    protected void settle(ServerLevel level) {
        Moteur m = moteur;
        if (m != null) m.settle(this, level);
    }

    @Override
    public void onRemoved(ServerLevel level) {
        Moteur m = moteur;
        if (m != null) m.onRemoved(this, level);
    }

    // =================================================================== NBT
    @Override
    protected void writeSync(CompoundTag tag) {
        tag.putInt("Phase", phase);
        tag.putLong("PhaseStart", phaseStart);
        tag.putLong("BettingEnd", bettingEnd);
        ListTag list = new ListTag();
        for (Bet b : bets) list.add(b.save());
        tag.put("Bets", list);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        phase = tag.getInt("Phase");
        phaseStart = tag.getLong("PhaseStart");
        bettingEnd = tag.getLong("BettingEnd");
        bets.clear();
        ListTag list = tag.getList("Bets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                bets.add(Bet.load(list.getCompound(i)));
            } catch (Exception ignored) {
            }
        }
    }
}
