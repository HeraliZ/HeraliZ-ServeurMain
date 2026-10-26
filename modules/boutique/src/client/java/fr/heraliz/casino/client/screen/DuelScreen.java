package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.DuelBlockEntity;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.duel.DuelRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Ecran commun des duels entre joueurs : lancer un defi (mise), l'accepter, l'annuler ; le jeu lui-meme est dessine
 * par chaque duel. Le gagnant touche les deux mises moins la commission du casino.
 */
public abstract class DuelScreen<T extends DuelBlockEntity> extends MachineScreen<T> {
    protected DuelScreen(T be) {
        super(be);
    }

    protected abstract DuelRules rules();

    /** Boutons du jeu pour un joueur du duel. */
    protected abstract void gameDock(Dock d, int side);

    /** Etat du jeu en cours. */
    protected abstract Component gameStatus(int side);

    /** Dessin du jeu (au centre de l'ecran, sans fond). */
    protected abstract void renderGame(GuiGraphics g, int mx, int my, float partial);

    /** Ligne du pot sous les noms des joueurs. */
    protected boolean showPot() {
        return true;
    }

    protected int mySide() {
        return be.sideOf(me());
    }

    @Override
    protected void init() {
        super.init();
        if (mySide() != 0 && be.phase == DuelBlockEntity.PLAYING) send(DuelBlockEntity.ACTION_SYNC);
    }

    @Override
    protected int layoutKey() {
        return be.phase * 10 + mySide();
    }

    @Override
    protected void dock(Dock d) {
        int side = mySide();
        if (be.phase == DuelBlockEntity.PLAYING && side != 0) {
            gameDock(d, side);
            return;
        }
        if (be.phase == DuelBlockEntity.OPEN) {
            if (side == 1) {
                d.button(Component.translatable("screen.heralizcasino.duel.cancel"), UiAtlas.DARK, 100, () -> {
                    send(DuelBlockEntity.ACTION_CANCEL);
                    click();
                });
            } else {
                d.add(new UiButton(170, 20, Component.empty(), UiAtlas.GOLD, b -> primary())
                        .label(() -> Component.translatable("screen.heralizcasino.duel.accept", Chips.format(be.stake)))
                        .when(() -> balance() >= be.stake)
                        .tip(() -> Component.translatable("screen.heralizcasino.duel.accept_tip", be.nameA, Chips.format(rules().prize(be.stake)))));
            }
            return;
        }
        if (be.phase == DuelBlockEntity.PLAYING) return;
        d.bet();
        d.button(Component.translatable("screen.heralizcasino.duel.create"), UiAtlas.GOLD, 84, this::primary).when(() -> be.free(now()))
                .tip(() -> Component.translatable("screen.heralizcasino.duel.create_tip"));
    }

    @Override
    protected void primary() {
        if (be.phase == DuelBlockEntity.OPEN && mySide() != 1) {
            if (balance() < be.stake) {
                checkBet(be.stake);
                return;
            }
            send(DuelBlockEntity.ACTION_JOIN);
            chipSound();
            return;
        }
        if (be.free(now()) && checkBet(bet)) {
            send(DuelBlockEntity.ACTION_CREATE, bet);
            chipSound();
        }
    }

    @Override
    protected int cameraMode() {
        return be.phase == DuelBlockEntity.PLAYING ? MachineDesign.CAM_PLAY : MachineDesign.CAM_MENU;
    }

    @Override
    protected boolean watchDone() {
        return be.phase == DuelBlockEntity.IDLE || (be.phase == DuelBlockEntity.DONE && now() >= be.phaseStart + DuelBlockEntity.SHOW_TICKS);
    }

    protected int secondsLeft() {
        return (int) Math.max(0, Math.ceil((be.deadline - partialNow()) / 20f));
    }

    @Override
    protected Component status() {
        int side = mySide();
        switch (be.phase) {
            case DuelBlockEntity.OPEN -> {
                if (side == 1) return Component.translatable("screen.heralizcasino.duel.waiting", secondsLeft());
                return Component.translatable("screen.heralizcasino.duel.challenge", be.nameA, Chips.format(be.stake));
            }
            case DuelBlockEntity.PLAYING -> {
                return gameStatus(side);
            }
            case DuelBlockEntity.DONE -> {
                if (be.winner == DuelBlockEntity.VOID) return Component.translatable("screen.heralizcasino.duel.void");
                int w = be.winner;
                if (w == side) return Component.translatable("screen.heralizcasino.duel.you_win", Chips.format(be.prize));
                if (side != 0) return Component.translatable("screen.heralizcasino.duel.you_lose", be.name(w));
                return Component.translatable("screen.heralizcasino.duel.winner", be.name(w), Chips.format(be.prize));
            }
            default -> {
                return Component.translatable("screen.heralizcasino.duel.idle");
            }
        }
    }

    @Override
    protected int statusColor() {
        if (be.phase == DuelBlockEntity.DONE) {
            int side = mySide();
            if (be.winner == side) return GOLD;
            return side != 0 && be.winner != DuelBlockEntity.VOID ? RED : CREAM;
        }
        if (be.phase == DuelBlockEntity.OPEN && mySide() != 1) return GOLD;
        return CREAM;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        // panneau « i » ouvert : il occupe le coin en haut a gauche, rien ne s'y superpose (noms, pot, score, flottes)
        if ((be.phase == DuelBlockEntity.PLAYING || be.phase == DuelBlockEntity.DONE) && !infoOuvert()) {
            // joueurs et pot dans le coin en haut a gauche (la scene 3D reste degagee)
            int x = 10, y = 10;
            float wa = UiKit.font().width(be.nameA), wvs = UiKit.font().width(" VS ");
            UiKit.outlined(g, Component.literal(be.nameA), x, y, mySide() == 1 ? GOLD : CREAM, 1f, -1);
            UiKit.outlined(g, Component.literal(" VS "), x + wa, y, GREY, 1f, -1);
            UiKit.outlined(g, Component.literal(be.nameB), x + wa + wvs, y, mySide() == 2 ? GOLD : CREAM, 1f, -1);
            if (showPot()) {
                UiKit.outlined(g, Component.translatable("screen.heralizcasino.duel.pot", Chips.format(rules().prize(be.stake)),
                        fr.heraliz.casino.client.screen.kit.Ui.decimale(rules().commission * 100)), x, y + 12, GREY, 0.75f, -1);
            }
            renderGame(g, mx, my, partial);
        }
    }

    @Override
    protected List<Component> infoExtra() {
        DuelRules r = rules();
        return List.of(Component.translatable("screen.heralizcasino.duel.info_pot", fr.heraliz.casino.client.screen.kit.Ui.decimale(r.commission * 100)),
                Component.translatable("screen.heralizcasino.duel.info_limits", Chips.format(r.minBet), Chips.format(r.maxBet)));
    }

    protected static DuelRules cfg(boolean rps) {
        return rps ? CasinoConfig.get().pierreFeuilleCiseaux : CasinoConfig.get().batailleNavale;
    }
}
