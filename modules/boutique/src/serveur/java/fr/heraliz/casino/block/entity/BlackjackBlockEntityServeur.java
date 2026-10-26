package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.game.cards.BlackjackTable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.IntSupplier;

import static fr.heraliz.casino.block.MachinesServeur.RNG;

/**
 * Partie SERVEUR de {@link BlackjackBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new BlackjackBlockEntity(...) » côté serveur.
 */
public class BlackjackBlockEntityServeur extends BlackjackBlockEntity {
   public BlackjackBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }

    private boolean revealSent = true;

    private static final IntSupplier DRAW = () -> RNG.nextInt(52);

    @Override
    public void handleAction(ServerPlayer p, int action, long[] args) {
        if (!(level instanceof ServerLevel)) return;
        long now = now();
        if (action == ACTION_DEAL) {
            if (args.length < 1) return;
            if (!free(now)) {
                msg(p, Component.translatable("message.heralizcasino.occupied", playerName).withStyle(ChatFormatting.GOLD));
                return;
            }
            if (!takeBet(p, args[0])) return;
            player = p.getUUID();
            playerName = p.getGameProfile().getName();
            // les regles peuvent avoir change (rechargement)
            copyRules();
            table.start(args[0], now, DRAW);
            revealSent = false;
            sync();
            return;
        }
        if (!p.getUUID().equals(player) || table.phase != BlackjackTable.PLAYER) return;
        switch (action) {
            case ACTION_HIT -> table.hit(now, DRAW);
            case ACTION_STAND -> table.stand(now, DRAW);
            case ACTION_DOUBLE -> {
                if (!table.canDouble(now)) return;
                long extra = table.bets[table.active];
                if (!ChipsServeur.take(p, extra)) {
                    msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(extra), Chips.format(Chips.count(p))).withStyle(ChatFormatting.RED));
                    return;
                }
                fr.heraliz.casino.data.CasinoStats.get(((ServerLevel) level).getServer()).wager(type, p.getUUID(), extra);
                table.doubleDown(now, DRAW);
            }
            case ACTION_SPLIT -> {
                if (!table.canSplit(now)) return;
                long extra = table.bets[0];
                if (!ChipsServeur.take(p, extra)) {
                    msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(extra), Chips.format(Chips.count(p))).withStyle(ChatFormatting.RED));
                    return;
                }
                fr.heraliz.casino.data.CasinoStats.get(((ServerLevel) level).getServer()).wager(type, p.getUUID(), extra);
                table.split(now, DRAW);
            }
            default -> {
                return;
            }
        }
        sync();
    }

    @Override
    protected void tickServer(ServerLevel level) {
        long now = now();
        if (table.phase == BlackjackTable.PLAYER && now >= table.deadline && now >= table.busyUntil) {
            table.timeout(now, DRAW);
            sync();
        }
        if (!revealSent && now >= table.revealTick) {
            revealSent = true;
            sync();
        }
        if (table.phase == BlackjackTable.DEALER && now >= table.settleTick && !table.paid) {
            table.paid = true;
            table.phase = BlackjackTable.DONE;
            doneTick = now;
            if (player != null) payout(level, player, playerName, table.totalPayout(), table.totalBet());
            sync();
        }
        if (table.phase == BlackjackTable.DONE && now >= doneTick + SHOW_END + 200) {
            table.reset();
            sync();
        }
    }

    @Override
    public void onRemoved(ServerLevel level) {
        if (player == null) return;
        if (table.phase == BlackjackTable.PLAYER) {
            refund(level, player, table.totalBet());
        } else if (table.phase == BlackjackTable.DEALER && !table.paid) {
            table.paid = true;
            payout(level, player, playerName, table.totalPayout(), table.totalBet());
        }
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        writeTable(tag, false);
        tag.putBoolean("RevealSent", revealSent);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        revealSent = !tag.contains("RevealSent") || tag.getBoolean("RevealSent");
    }
}
