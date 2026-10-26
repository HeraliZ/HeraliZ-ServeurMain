package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.cards.BlackjackTable;
import fr.heraliz.casino.game.cards.Cards;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * Table de blackjack : un joueur a la fois contre le croupier (sabot infini).
 * La carte cachee du croupier n'est envoyee aux clients qu'au moment ou elle est retournee.
 */
public class BlackjackBlockEntity extends MachineBlockEntity {
    public static final int ACTION_DEAL = 0, ACTION_HIT = 1, ACTION_STAND = 2, ACTION_DOUBLE = 3, ACTION_SPLIT = 4;
    /** Affichage du resultat avant de liberer la table (ticks). */
    public static final int SHOW_END = 70;

    public final BlackjackTable table = new BlackjackTable(new fr.heraliz.casino.game.cards.BlackjackRules());
    public UUID player;
    public String playerName = "";
    public long doneTick;

    public BlackjackBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.BLACKJACK, pos, state);
        copyRules();
    }

    public boolean free(long now) {
        return table.phase == BlackjackTable.IDLE || (table.phase == BlackjackTable.DONE && now >= doneTick + SHOW_END);
    }

    /** Cartes distribuees : on reste a la table jusqu'au reglement. */
    @Override
    public boolean locks(@javax.annotation.Nullable UUID p) {
        return p != null && p.equals(player) && (table.phase == BlackjackTable.PLAYER || table.phase == BlackjackTable.DEALER);
    }

    void copyRules() {
        var r = CasinoConfig.get().blackjack;
        var t = table.rules();
        t.blackjackPays = r.blackjackPays;
        t.dealerHitsSoft17 = r.dealerHitsSoft17;
        t.allowDouble = r.allowDouble;
        t.allowSplit = r.allowSplit;
        t.decisionSeconds = r.decisionSeconds;
        t.minBet = r.minBet;
        t.maxBet = r.maxBet;
    }

    // =================================================================== NBT
    @Override
    protected void writeSync(CompoundTag tag) {
        writeTable(tag, true);
    }

    /** masked : carte cachee remplacee par HIDDEN tant qu'elle n'est pas retournee (donnees clients). */
    void writeTable(CompoundTag tag, boolean masked) {
        BlackjackTable t = table;
        tag.putInt("Phase", t.phase);
        putUuid(tag, "Player", player);
        tag.putString("PlayerName", playerName);
        tag.putInt("HandCount", t.handCount);
        tag.putInt("Active", t.active);
        for (int h = 0; h < 2; h++) {
            tag.putIntArray("Hand" + h, java.util.Arrays.copyOf(t.hands[h], t.handSize[h]));
            tag.putLongArray("HandT" + h, java.util.Arrays.copyOf(t.handTicks[h], t.handSize[h]));
            tag.putLong("Bet" + h, t.bets[h]);
            tag.putBoolean("Dbl" + h, t.doubled[h]);
            tag.putInt("Res" + h, t.results[h]);
            tag.putLong("Pay" + h, t.payouts[h]);
        }
        int[] d = java.util.Arrays.copyOf(t.dealer, t.dealerSize);
        long now = now();
        if (masked && d.length > 1 && now < t.revealTick) d[1] = Cards.HIDDEN;
        tag.putIntArray("Dealer", d);
        tag.putLongArray("DealerT", java.util.Arrays.copyOf(t.dealerTicks, t.dealerSize));
        tag.putLong("Reveal", t.revealTick);
        tag.putLong("Settle", t.settleTick);
        tag.putLong("Busy", t.busyUntil);
        tag.putLong("Deadline", t.deadline);
        tag.putBoolean("Paid", t.paid);
        tag.putLong("DoneTick", doneTick);
        // resultats uniquement une fois regles (pas de fuite avant l'animation)
        if (masked && now < t.settleTick) {
            tag.putInt("Res0", 0);
            tag.putInt("Res1", 0);
            tag.putLong("Pay0", 0);
            tag.putLong("Pay1", 0);
        }
    }

    @Override
    protected void readSync(CompoundTag tag) {
        BlackjackTable t = table;
        t.phase = tag.getInt("Phase");
        player = getUuid(tag, "Player");
        playerName = tag.getString("PlayerName");
        t.handCount = Math.max(1, Math.min(2, tag.getInt("HandCount")));
        t.active = Math.max(0, Math.min(1, tag.getInt("Active")));
        for (int h = 0; h < 2; h++) {
            int[] c = tag.getIntArray("Hand" + h);
            long[] ct = tag.getLongArray("HandT" + h);
            t.handSize[h] = Math.min(BlackjackTable.MAX, c.length);
            System.arraycopy(c, 0, t.hands[h], 0, t.handSize[h]);
            System.arraycopy(ct, 0, t.handTicks[h], 0, Math.min(ct.length, t.handSize[h]));
            t.bets[h] = tag.getLong("Bet" + h);
            t.doubled[h] = tag.getBoolean("Dbl" + h);
            t.results[h] = tag.getInt("Res" + h);
            t.payouts[h] = tag.getLong("Pay" + h);
        }
        int[] d = tag.getIntArray("Dealer");
        long[] dt = tag.getLongArray("DealerT");
        t.dealerSize = Math.min(BlackjackTable.MAX, d.length);
        System.arraycopy(d, 0, t.dealer, 0, t.dealerSize);
        System.arraycopy(dt, 0, t.dealerTicks, 0, Math.min(dt.length, t.dealerSize));
        t.revealTick = tag.contains("Reveal") ? tag.getLong("Reveal") : Long.MAX_VALUE;
        t.settleTick = tag.contains("Settle") ? tag.getLong("Settle") : Long.MAX_VALUE;
        t.busyUntil = tag.getLong("Busy");
        t.deadline = tag.getLong("Deadline");
        t.paid = tag.getBoolean("Paid");
        doneTick = tag.getLong("DoneTick");
    }
}
