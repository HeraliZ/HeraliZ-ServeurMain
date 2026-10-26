package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.MachinesServeur;
import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static fr.heraliz.casino.block.entity.DuelBlockEntity.ACTION_CANCEL;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.ACTION_CREATE;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.ACTION_JOIN;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.ACTION_SYNC;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.BOT;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.BOT_NAME;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.DONE;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.FORFEIT_TICKS;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.IDLE;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.OPEN;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.PLAYING;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.SHOW_TICKS;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.WIN_A;
import static fr.heraliz.casino.block.entity.DuelBlockEntity.WIN_B;

/**
 * Partie serveur des duels joueur contre joueur (Pierre-feuille-ciseaux, Bataille navale) : defi, adversaire, bot,
 * forfait des absents, fin et reglement. Corps d'origine de DuelBlockEntity, tels quels (sur « be » ; les methodes que
 * les jeux redefinissent sont appelees sur be, donc toujours la version du jeu).
 */
public final class DuelsServeur {
    private DuelsServeur() {
    }

    /** Branche le deroulement des duels (PartieServeur.init). */
    public static void installer() {
        DuelBlockEntity.moteur = new DuelBlockEntity.Moteur() {
            @Override
            public void handleAction(DuelBlockEntity be, ServerPlayer p, int action, long[] args) {
                DuelsServeur.handleAction(be, p, action, args);
            }

            @Override
            public void tickServer(DuelBlockEntity be, ServerLevel level) {
                DuelsServeur.tickServer(be, level);
            }

            @Override
            public void finish(DuelBlockEntity be, ServerLevel level, int win) {
                DuelsServeur.finish(be, level, win);
            }

            @Override
            public void onRemoved(DuelBlockEntity be, ServerLevel level) {
                DuelsServeur.onRemoved(be, level);
            }
        };
    }

    // =================================================================== verrou et forfait
    /** Toutes les secondes pendant la partie : un joueur qui a quitte la table depuis 10 s perd par forfait. */
    private static void checkPresence(DuelBlockEntity be, ServerLevel level) {
        BlockPos worldPosition = be.getBlockPos();
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY(), cz = worldPosition.getZ() + 0.5;
        for (int side = 1; side <= 2; side++) {
            if (be.isBot(side)) continue;
            ServerPlayer sp = be.player(be.player(side));
            boolean here = sp != null && sp.isAlive() && sp.level() == level && sp.distanceToSqr(cx, cy, cz) < 12 * 12;
            be.absent[side] = here ? 0 : be.absent[side] + 20;
            if (be.absent[side] >= FORFEIT_TICKS) {
                int stay = 3 - side;
                Component msg = Component.translatable("message.heralizcasino.duel.forfeit", be.name(side), be.name(stay)).withStyle(ChatFormatting.GOLD);
                for (int s = 1; s <= 2; s++) {
                    ServerPlayer o = be.player(be.player(s));
                    if (o != null) o.sendSystemMessage(msg);
                }
                be.finish(level, stay == 1 ? WIN_A : WIN_B);
                return;
            }
        }
    }

    // =================================================================== bot
    /**
     * Commande /casino bot : le bot releve le defi ouvert par un joueur, ou ouvre lui-meme un defi que le joueur
     * peut accepter. Rien n'est preleve au bot ; ses parties ne comptent pas dans les classements.
     * @return 1 le bot a rejoint, 2 le bot a ouvert un defi, 0 machine occupee
     */
    public static int botChallenge(DuelBlockEntity be, ServerLevel sl, long amount) {
        long now = be.now();
        if (be.phase == OPEN && be.botSide == 0 && be.playerA != null) {
            java.util.Arrays.fill(be.absent, 0);
            be.playerB = BOT;
            be.nameB = BOT_NAME;
            be.botSide = 2;
            be.phase = PLAYING;
            be.phaseStart = now;
            be.startGame(sl, now);
            be.sync();
            be.sendPrivate(1);
            MachineBlockEntity.Spot s = be.spot(1);
            ServerPlayer sp = be.player(be.playerA);
            if (s != null && sp != null) MachinesServeur.seat(be, sp, s);
            return 1;
        }
        if (!be.free(now)) return 0;
        be.resetGame();
        be.playerA = BOT;
        be.nameA = BOT_NAME;
        be.playerB = null;
        be.nameB = "";
        be.botSide = 1;
        be.stake = Math.max(be.minBet(), Math.min(be.maxBet(), amount));
        be.winner = 0;
        be.prize = 0;
        be.phase = OPEN;
        be.phaseStart = now;
        be.deadline = now + be.rules().waitSeconds * 20L;
        be.sync();
        return 2;
    }

    // =================================================================== actions
    static void handleAction(DuelBlockEntity be, ServerPlayer p, int action, long[] args) {
        if (!(be.getLevel() instanceof ServerLevel sl)) return;
        BlockPos worldPosition = be.getBlockPos();
        long now = be.now();
        switch (action) {
            case ACTION_CREATE -> {
                if (args.length < 1) return;
                if (!be.free(now)) {
                    MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.duel.busy").withStyle(ChatFormatting.GOLD));
                    return;
                }
                if (!MachinesServeur.takeBet(be, p, args[0])) return;
                be.resetGame();
                be.botSide = 0;
                be.playerA = p.getUUID();
                be.nameA = p.getGameProfile().getName();
                be.playerB = null;
                be.nameB = "";
                be.stake = args[0];
                be.winner = 0;
                be.prize = 0;
                be.phase = OPEN;
                be.phaseStart = now;
                be.deadline = now + be.rules().waitSeconds * 20L;
                be.sync();
                // les joueurs autour sont prevenus du defi
                Component c = Component.translatable("message.heralizcasino.duel.open", be.nameA, be.type.displayName(), Chips.format(be.stake))
                        .withStyle(ChatFormatting.GOLD);
                for (ServerPlayer o : sl.players()) {
                    if (o != p && o.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5) < 24 * 24) {
                        o.sendSystemMessage(c);
                    }
                }
            }
            case ACTION_JOIN -> {
                if (be.phase != OPEN || p.getUUID().equals(be.playerA)) return;
                if (!ChipsServeur.take(p, be.stake)) {
                    MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(be.stake), Chips.format(Chips.count(p)))
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                CasinoStats.get(sl.getServer()).wager(be.type, p.getUUID(), be.stake);
                java.util.Arrays.fill(be.absent, 0);
                be.playerB = p.getUUID();
                be.nameB = p.getGameProfile().getName();
                be.phase = PLAYING;
                be.phaseStart = now;
                be.startGame(sl, now);
                be.sync();
                be.sendPrivate(1);
                be.sendPrivate(2);
                // chacun prend sa place a la table (s'il est a portee)
                for (int side = 1; side <= 2; side++) {
                    MachineBlockEntity.Spot s = be.spot(side);
                    ServerPlayer sp = be.player(be.player(side));
                    if (s != null && sp != null) MachinesServeur.seat(be, sp, s);
                }
            }
            case ACTION_CANCEL -> {
                if (be.phase != OPEN || !p.getUUID().equals(be.playerA)) return;
                MachinesServeur.refund(be, sl, be.playerA, be.stake);
                be.phase = IDLE;
                be.playerA = null;
                be.sync();
            }
            case ACTION_SYNC -> {
                int side = be.sideOf(p.getUUID());
                if (side != 0 && be.phase == PLAYING) be.sendPrivate(side);
            }
            default -> {
                int side = be.sideOf(p.getUUID());
                if (be.phase == PLAYING && side != 0) be.handleGame(p, side, action, args);
            }
        }
    }

    // =================================================================== tick
    static void tickServer(DuelBlockEntity be, ServerLevel level) {
        long now = be.now();
        if (be.phase == OPEN && now >= be.deadline) {
            ServerPlayer p = be.player(be.playerA);
            if (p != null) MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.duel.nobody").withStyle(ChatFormatting.YELLOW));
            if (!be.isBot(1)) MachinesServeur.refund(be, level, be.playerA, be.stake);
            be.phase = IDLE;
            be.playerA = null;
            be.botSide = 0;
            be.sync();
        } else if (be.phase == PLAYING) {
            be.tickGame(level, now);
            if (be.phase == PLAYING && be.botSide != 0) be.tickBot(level, now, be.botSide);
            if (be.phase == PLAYING && now % 20 == 0 && DuelBlockEntity.forfeitEnabled) checkPresence(be, level);
        } else if (be.phase == DONE && now == be.phaseStart + SHOW_TICKS) {
            be.unseatAll();
        } else if (be.phase == DONE && now >= be.phaseStart + SHOW_TICKS + 200) {
            be.unseatAll();
            be.phase = IDLE;
            be.botSide = 0;
            be.sync();
        }
    }

    /** Fin du duel : le gagnant touche le pot moins la commission ; VOID rembourse les deux joueurs. */
    static void finish(DuelBlockEntity be, ServerLevel level, int win) {
        be.winner = win;
        if (win == WIN_A || win == WIN_B) {
            be.prize = be.rules().prize(be.stake);
            int lose = win == WIN_A ? 2 : 1;
            if (!be.isBot(win)) MachinesServeur.payout(be, level, be.player(win), be.name(win), be.prize, be.stake);
            if (!be.isBot(lose)) MachinesServeur.payout(be, level, be.player(lose), be.name(lose), 0, be.stake);
        } else {
            be.prize = 0;
            if (!be.isBot(1)) MachinesServeur.refund(be, level, be.playerA, be.stake);
            if (!be.isBot(2)) MachinesServeur.refund(be, level, be.playerB, be.stake);
        }
        be.phase = DONE;
        be.phaseStart = be.now();
        be.sync();
    }

    static void onRemoved(DuelBlockEntity be, ServerLevel level) {
        if (be.phase == OPEN && !be.isBot(1)) MachinesServeur.refund(be, level, be.playerA, be.stake);
        if (be.phase == PLAYING) {
            if (!be.isBot(1)) MachinesServeur.refund(be, level, be.playerA, be.stake);
            if (!be.isBot(2)) MachinesServeur.refund(be, level, be.playerB, be.stake);
        }
        be.unseatAll();
        be.phase = IDLE;
        be.botSide = 0;
    }
}
