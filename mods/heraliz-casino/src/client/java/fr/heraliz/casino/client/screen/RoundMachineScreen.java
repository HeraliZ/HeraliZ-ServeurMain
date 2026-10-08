package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.RoundBlockEntity;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.economy.Chips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Jeux en tours (Grande Roue, Roulette, Course, Fusee) : tout le monde mise pendant le compte a rebours, puis la
 * camera suit l'action pendant le tour.
 */
public abstract class RoundMachineScreen<T extends RoundBlockEntity> extends MachineScreen<T> {
    protected RoundMachineScreen(T be) {
        super(be);
    }

    /** Bouton "Annuler mes mises" (visible pendant le compte a rebours si le joueur a mise). */
    protected UiButton cancelButton(Dock d) {
        return d.add(new UiButton(58, 18, Component.translatable("screen.heralizcasino.cancel_bets"), UiAtlas.DARK, b -> {
            send(RoundBlockEntity.ACTION_CANCEL);
            click();
        }).scale(0.85f).when(() -> be.phase == RoundBlockEntity.BETTING && myTotal() > 0));
    }

    @Override
    protected int cameraMode() {
        return switch (be.phase) {
            case RoundBlockEntity.RUNNING -> MachineDesign.CAM_PLAY;
            case RoundBlockEntity.RESULT -> MachineDesign.CAM_RESULT;
            default -> MachineDesign.CAM_MENU;
        };
    }

    protected long myTotal() {
        UUID me = me();
        return me == null ? 0 : be.totalOf(me);
    }

    protected long myBetOn(int spot) {
        UUID me = me();
        long t = 0;
        if (me == null) return 0;
        for (RoundBlockEntity.Bet b : be.bets) if (b.spot == spot && b.player.equals(me)) t += b.amount;
        return t;
    }

    protected long totalOn(int spot) {
        long t = 0;
        for (RoundBlockEntity.Bet b : be.bets) if (b.spot == spot) t += b.amount;
        return t;
    }

    /** Mise sur une case (verifications locales ; le serveur decide). */
    protected void placeBet(int spot) {
        if (!be.acceptingBets()) {
            notice(Component.translatable("message.heralizcasino.no_more_bets").withStyle(ChatFormatting.GOLD));
            return;
        }
        if (myTotal() + bet > maxBet()) {
            notice(Component.translatable("screen.heralizcasino.round_max", Chips.format(maxBet())).withStyle(ChatFormatting.RED));
            return;
        }
        if (!checkBet(bet)) return;
        send(RoundBlockEntity.ACTION_BET, spot, bet);
        chipSound();
    }

    /** Secondes restantes pour miser. */
    protected int countdown() {
        return (int) Math.ceil(Math.max(0f, (be.bettingEnd - partialNow()) / 20f));
    }

    /** Etat standard : compte a rebours, puis texte du tour et du resultat. */
    @Override
    protected Component status() {
        return switch (be.phase) {
            case RoundBlockEntity.BETTING -> myTotal() > 0
                    ? Component.translatable("screen.heralizcasino.round.betting_mine", countdown(), Chips.format(myTotal()))
                    : Component.translatable("screen.heralizcasino.round.betting", countdown());
            case RoundBlockEntity.RUNNING -> runningText();
            case RoundBlockEntity.RESULT -> resultText();
            default -> idleText();
        };
    }

    @Override
    protected int statusColor() {
        return switch (be.phase) {
            case RoundBlockEntity.BETTING -> countdown() <= 5 ? 0xFF8A6A : GREEN;
            case RoundBlockEntity.RUNNING, RoundBlockEntity.RESULT -> GOLD;
            default -> CREAM;
        };
    }

    protected abstract Component idleText();

    protected abstract Component runningText();

    protected abstract Component resultText();
}
