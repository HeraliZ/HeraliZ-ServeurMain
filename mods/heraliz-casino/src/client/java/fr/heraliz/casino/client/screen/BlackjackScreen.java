package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.BlackjackBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.cards.BlackjackTable;
import fr.heraliz.casino.game.cards.Cards;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Blackjack : tout se passe sur la table en 3D (cartes, totaux, jetons) ; l'ecran ne garde que la mise puis
 * les quatre decisions.
 */
public class BlackjackScreen extends MachineScreen<BlackjackBlockEntity> {
    public BlackjackScreen(BlackjackBlockEntity be) {
        super(be);
    }

    private BlackjackTable t() {
        return be.table;
    }

    private boolean myTurn() {
        return t().phase == BlackjackTable.PLAYER && isMe(be.player);
    }

    private boolean canDeal() {
        return be.free(now()) || (t().phase == BlackjackTable.DONE && isMe(be.player));
    }

    @Override
    protected int layoutKey() {
        return myTurn() ? 1 : 0;
    }

    @Override
    protected void dock(Dock d) {
        if (myTurn()) {
            d.button(Component.translatable("screen.heralizcasino.bj.hit"), UiAtlas.GREEN, 62, () -> act(BlackjackBlockEntity.ACTION_HIT))
                    .when(() -> myTurn() && t().canAct(now()));
            d.button(Component.translatable("screen.heralizcasino.bj.stand"), UiAtlas.RED, 62, () -> act(BlackjackBlockEntity.ACTION_STAND))
                    .when(() -> myTurn() && t().canAct(now()));
            d.button(Component.translatable("screen.heralizcasino.bj.double"), UiAtlas.BLUE, 62, () -> act(BlackjackBlockEntity.ACTION_DOUBLE))
                    .when(() -> myTurn() && t().canDouble(now()) && t().bets[t().active] <= balance())
                    .tip(() -> Component.translatable("screen.heralizcasino.bj.double_tip"));
            d.button(Component.translatable("screen.heralizcasino.bj.split"), UiAtlas.PURPLE, 62, () -> act(BlackjackBlockEntity.ACTION_SPLIT))
                    .when(() -> myTurn() && t().canSplit(now()) && t().bets[0] <= balance())
                    .tip(() -> Component.translatable("screen.heralizcasino.bj.split_tip"));
            return;
        }
        d.bet();
        d.button(Component.translatable("screen.heralizcasino.bj.deal"), UiAtlas.GOLD, 96, this::primary).when(this::canDeal);
    }

    private void act(int action) {
        send(action);
        click();
    }

    @Override
    protected void primary() {
        if (myTurn()) {
            if (t().canAct(now())) act(BlackjackBlockEntity.ACTION_STAND);
            return;
        }
        if (!canDeal() || !checkBet(bet)) return;
        send(BlackjackBlockEntity.ACTION_DEAL, bet);
        chipSound();
    }

    @Override
    protected Component status() {
        long now = now();
        BlackjackTable t = t();
        boolean settled = now >= t.settleTick;
        String who = be.playerName == null ? "" : be.playerName;
        if (myTurn()) {
            int n = 0;
            int[] cards = new int[BlackjackTable.MAX];
            for (int i = 0; i < t.handSize[t.active]; i++) if (t.handTicks[t.active][i] <= now) cards[n++] = t.hands[t.active][i];
            long left = Math.max(0, t.deadline - now) / 20;
            return Component.translatable("screen.heralizcasino.bj.your_turn", n > 0 ? Cards.total(cards, n) : 0, left);
        }
        if (t.phase == BlackjackTable.PLAYER || (t.phase == BlackjackTable.DEALER && !settled)) {
            return isMe(be.player) ? Component.translatable("screen.heralizcasino.bj.dealer_plays") : Component.translatable("screen.heralizcasino.occupied_by", who);
        }
        if (settled && t.phase != BlackjackTable.IDLE) {
            long pay = t.totalPayout(), stake = t.totalBet();
            boolean mine = isMe(be.player);
            if (pay > stake) return Component.translatable(mine ? "screen.heralizcasino.won" : "screen.heralizcasino.other_won", Chips.format(pay), mult(pay / (double) Math.max(1, stake)), who);
            if (pay == stake) return Component.translatable("screen.heralizcasino.bj.push_back");
            return Component.translatable(mine ? "screen.heralizcasino.lost" : "screen.heralizcasino.other_lost", who);
        }
        return Component.translatable("screen.heralizcasino.bj.place_bet");
    }

    @Override
    protected int statusColor() {
        BlackjackTable t = t();
        if (myTurn()) return (t.deadline - now()) / 20 <= 5 ? RED : GREEN;
        if (now() >= t.settleTick && t.phase != BlackjackTable.IDLE) return t.totalPayout() > t.totalBet() ? GOLD : t.totalPayout() == t.totalBet() ? CREAM : GREY;
        return CREAM;
    }

    @Override
    protected List<Component> infoExtra() {
        BlackjackRules r = t().rules();
        return List.of(Component.translatable("screen.heralizcasino.bj.rules_pays", ratio(r.blackjackPays)),
                Component.translatable(r.dealerHitsSoft17 ? "screen.heralizcasino.bj.h17" : "screen.heralizcasino.bj.s17"),
                Component.translatable("screen.heralizcasino.bj.double_tip"),
                Component.translatable("screen.heralizcasino.bj.split_tip"));
    }

    /** 1.2 -> "6:5", 1.5 -> "3:2". */
    private static String ratio(double v) {
        for (int d = 1; d <= 10; d++) {
            double n = v * d;
            if (Math.abs(n - Math.round(n)) < 1e-6) return Math.round(n) + ":" + d;
        }
        return String.format(Locale.ROOT, "%.2f:1", v);
    }
}
