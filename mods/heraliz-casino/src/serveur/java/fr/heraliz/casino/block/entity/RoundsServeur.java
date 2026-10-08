package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.MachinesServeur;
import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static fr.heraliz.casino.block.entity.RoundBlockEntity.BETTING;
import static fr.heraliz.casino.block.entity.RoundBlockEntity.IDLE;
import static fr.heraliz.casino.block.entity.RoundBlockEntity.MAX_BETS;
import static fr.heraliz.casino.block.entity.RoundBlockEntity.RESULT;
import static fr.heraliz.casino.block.entity.RoundBlockEntity.RUNNING;

/**
 * Partie serveur des jeux en tours communs (Roulette, Crash, Course) : mises, annulation, compte a rebours, tirage
 * (onStart du jeu), reglement des gagnants. Corps d'origine de RoundBlockEntity, tels quels (sur « be » ; les
 * methodes que les jeux redefinissent sont appelees sur be, donc toujours la version du jeu).
 */
public final class RoundsServeur {
    private RoundsServeur() {
    }

    /** Branche le deroulement des tours (PartieServeur.init). */
    public static void installer() {
        RoundBlockEntity.moteur = new RoundBlockEntity.Moteur() {
            @Override
            public void handleAction(RoundBlockEntity be, ServerPlayer player, int action, long[] args) {
                RoundsServeur.handleAction(be, player, action, args);
            }

            @Override
            public boolean placeBet(RoundBlockEntity be, ServerPlayer player, int spot, long amount) {
                return RoundsServeur.placeBet(be, player, spot, amount);
            }

            @Override
            public void cancel(RoundBlockEntity be, ServerPlayer player) {
                RoundsServeur.cancel(be, player);
            }

            @Override
            public void tickServer(RoundBlockEntity be, ServerLevel level) {
                RoundsServeur.tickServer(be, level);
            }

            @Override
            public void settle(RoundBlockEntity be, ServerLevel level) {
                RoundsServeur.settle(be, level);
            }

            @Override
            public void onRemoved(RoundBlockEntity be, ServerLevel level) {
                RoundsServeur.onRemoved(be, level);
            }
        };
    }

    // =================================================================== actions
    static void handleAction(RoundBlockEntity be, ServerPlayer player, int action, long[] args) {
        if (action == RoundBlockEntity.ACTION_BET && args.length >= 2) {
            be.placeBet(player, (int) args[0], args[1]);
        } else if (action == RoundBlockEntity.ACTION_CANCEL) {
            be.cancel(player);
        } else {
            be.handleOther(player, action, args);
        }
    }

    static boolean placeBet(RoundBlockEntity be, ServerPlayer player, int spot, long amount) {
        if (!(be.getLevel() instanceof ServerLevel sl)) return false;
        if (!be.acceptingBets()) {
            MachineBlockEntity.msg(player, Component.translatable("message.heralizcasino.no_more_bets").withStyle(ChatFormatting.GOLD));
            return false;
        }
        if (!be.validSpot(spot) || amount <= 0 || be.bets.size() >= MAX_BETS) return false;
        long min = be.minBet(), max = be.maxBet();
        long mine = be.totalOf(player.getUUID());
        if (amount < min || mine + amount > max) {
            MachineBlockEntity.msg(player, Component.translatable("message.heralizcasino.round_limits", Chips.format(min), Chips.format(max), Chips.format(mine))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (!ChipsServeur.take(player, amount)) {
            MachineBlockEntity.msg(player, Component.translatable("message.heralizcasino.not_enough", Chips.format(amount), Chips.format(Chips.count(player)))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        CasinoStats.get(sl.getServer()).wager(be.type, player.getUUID(), amount);
        RoundBlockEntity.Bet merged = null;
        for (RoundBlockEntity.Bet b : be.bets) {
            if (b.player.equals(player.getUUID()) && b.spot == spot) {
                merged = b;
                break;
            }
        }
        if (merged != null) {
            merged.amount += amount;
        } else {
            RoundBlockEntity.Bet b = new RoundBlockEntity.Bet();
            b.player = player.getUUID();
            b.name = player.getGameProfile().getName();
            b.spot = spot;
            b.amount = amount;
            be.bets.add(b);
        }
        if (be.phase == IDLE) {
            be.phase = BETTING;
            be.phaseStart = be.now();
            be.bettingEnd = be.now() + be.bettingTicks();
        }
        be.sync();
        return true;
    }

    /** Annule toutes les mises du joueur pendant le compte a rebours (remboursees). */
    static void cancel(RoundBlockEntity be, ServerPlayer player) {
        if (be.phase != BETTING || !(be.getLevel() instanceof ServerLevel sl)) return;
        long total = 0;
        for (int i = be.bets.size() - 1; i >= 0; i--) {
            if (be.bets.get(i).player.equals(player.getUUID())) {
                total += be.bets.get(i).amount;
                be.bets.remove(i);
            }
        }
        if (total > 0) {
            MachinesServeur.refund(be, sl, player.getUUID(), total);
            if (be.bets.isEmpty()) be.phase = IDLE;
            be.sync();
        }
    }

    // =================================================================== tick
    static void tickServer(RoundBlockEntity be, ServerLevel level) {
        long now = be.now();
        if (be.phase == BETTING && now >= be.bettingEnd) {
            be.phase = RUNNING;
            be.phaseStart = now;
            be.onStart(level);
            be.sync();
        } else if (be.phase == RUNNING && be.runningDone(now)) {
            be.settle(level);
            be.phase = RESULT;
            be.phaseStart = now;
            be.sync();
        } else if (be.phase == RESULT && now >= be.phaseStart + be.resultTicks()) {
            be.phase = IDLE;
            be.bets.clear();
            be.sync();
        }
    }

    /** Paie les gagnants (un seul versement et un seul message par joueur). */
    static void settle(RoundBlockEntity be, ServerLevel level) {
        Map<UUID, long[]> perPlayer = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (RoundBlockEntity.Bet b : be.bets) {
            long[] a = perPlayer.computeIfAbsent(b.player, k -> new long[2]);
            a[0] += b.amount;
            a[1] += be.payoutFor(b);
            names.put(b.player, b.name);
        }
        for (Map.Entry<UUID, long[]> e : perPlayer.entrySet()) {
            MachinesServeur.payout(be, level, e.getKey(), names.get(e.getKey()), e.getValue()[1], e.getValue()[0]);
        }
        be.onSettled(level);
    }

    static void onRemoved(RoundBlockEntity be, ServerLevel level) {
        if (be.phase == BETTING) {
            for (RoundBlockEntity.Bet b : be.bets) MachinesServeur.refund(be, level, b.player, b.amount);
        } else if (be.phase == RUNNING) {
            be.settle(level);
        }
        be.bets.clear();
        be.phase = IDLE;
    }
}
