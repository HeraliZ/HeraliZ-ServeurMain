package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.game.slot.SlotEngine;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

import static fr.heraliz.casino.block.MachinesServeur.RNG;

/**
 * Partie SERVEUR de {@link SlotBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SlotBlockEntity(...) » côté serveur.
 */
public class SlotBlockEntityServeur extends SlotBlockEntity {
   public SlotBlockEntityServeur(MachineType type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   public SlotBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    // ---- serveur
    private boolean paid = true;

    /** Rouleaux lances, ou gain pas encore verse. */
    @Override
    public boolean engaged(@javax.annotation.Nullable UUID player) {
        return player != null && player.equals(spinner) && (!paid || playing(now()));
    }

    @Override
    public void handleAction(ServerPlayer player, int action, long[] args) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = now();
        switch (action) {
            case ACTION_SIT -> {
                if (seated != null && !seated.equals(player.getUUID()) && isSeated(seated)) return;
                if (seat(player, STOOL)) {
                    seated = player.getUUID();
                    sync();
                }
            }
            case ACTION_LEAVE -> {
                if (player.getUUID().equals(seated)) {
                    unseat(seated);
                    seated = null;
                    sync();
                }
            }
            case ACTION_SPIN, ACTION_BUY -> {
                if (args.length < 1) return;
                if (seated != null && !seated.equals(player.getUUID()) && isSeated(seated)) {
                    msg(player, Component.translatable("message.heralizcasino.rotten.taken").withStyle(ChatFormatting.GOLD));
                    return;
                }
                if (playing(now)) {
                    msg(player, Component.translatable("message.heralizcasino.busy").withStyle(ChatFormatting.GOLD));
                    return;
                }
                SlotEngine e = engine();
                long amount = args[0];
                int buy = action == ACTION_BUY && args.length >= 2 ? (int) args[1] : 0;
                double price = buy == 0 ? 1 : e.buyPrice(buy);
                if (price <= 0) return;
                long total = (long) Math.ceil(amount * price);
                if (amount < minBet() || amount > maxBet()) {
                    msg(player, Component.translatable("message.heralizcasino.bet_limits", Chips.format(minBet()), Chips.format(maxBet())).withStyle(ChatFormatting.RED));
                    return;
                }
                if (!ChipsServeur.take(player, total)) {
                    msg(player, Component.translatable("message.heralizcasino.not_enough", Chips.format(total), Chips.format(Chips.count(player)))
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                CasinoStats.get(sl.getServer()).wager(type, player.getUUID(), total);
                start(player, amount, total, e.play(RNG, buy), now);
            }
            default -> {
            }
        }
    }

    /** Lance la partie tiree (mise deja prise) ; le gain est verse a la fin de l'animation. */
    private void start(ServerPlayer player, long amount, long total, SlotEngine.Play play, long now) {
        seqData = play.data();
        duration = play.duration();
        bet = amount;
        cost = total;
        win = (long) Math.floor(amount * (play.totalMilli() / 1000.0));
        spinner = player.getUUID();
        spinnerName = player.getGameProfile().getName();
        spinStart = now;
        paid = false;
        sync();
    }

    /** Tests en jeu uniquement (source dev) : rejoue une partie tiree a l'avance, sans prendre de mise. */
    public void testPlay(ServerPlayer player, long amount, SlotEngine.Play play) {
        start(player, amount, amount, play, now());
    }

    @Override
    protected void tickServer(ServerLevel level) {
        long now = now();
        if (!paid && now - spinStart >= duration) {
            paid = true;
            setChanged();
            if (spinner != null) payout(level, spinner, spinnerName, win, cost);
        }
        // la place se libere si le joueur s'est leve
        if (seated != null && now % 20 == 0 && !isSeated(seated)) {
            seated = null;
            sync();
        }
    }

    @Override
    public void onRemoved(ServerLevel level) {
        if (!paid && spinner != null) {
            paid = true;
            payout(level, spinner, spinnerName, win, cost);
        }
        unseat(seated);
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        tag.putBoolean("Paid", paid);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        paid = !tag.contains("Paid") || tag.getBoolean("Paid");
    }
}
