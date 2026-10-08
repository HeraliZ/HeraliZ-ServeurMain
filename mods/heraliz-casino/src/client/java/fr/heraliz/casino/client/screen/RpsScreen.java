package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.RpsBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.duel.Rps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pierre-feuille-ciseaux : trois boutons, les coups sont reveles en meme temps (grandes icones au centre).
 */
public class RpsScreen extends DuelScreen<RpsBlockEntity> {
    private static final ItemStack[] ICONS = {new ItemStack(Items.COBBLESTONE), new ItemStack(Items.PAPER), new ItemStack(Items.SHEARS)};

    public RpsScreen(RpsBlockEntity be) {
        super(be);
    }

    @Override
    protected DuelRules rules() {
        return cfg(true);
    }

    private boolean iChose(int side) {
        return side == 1 ? be.chosenA : be.chosenB;
    }

    @Override
    protected int layoutKey() {
        return super.layoutKey() + be.step * 1000 + (be.clientChoice + 1) * 10000 + be.round * 100000;
    }

    @Override
    protected void gameDock(Dock d, int side) {
        for (int c = Rps.ROCK; c <= Rps.SCISSORS; c++) {
            final int choice = c;
            UiButton b = d.add(new UiButton(78, 20, Component.translatable("screen.heralizcasino.rps." + Rps.KEYS[c]), UiAtlas.DARK, x -> {
                send(RpsBlockEntity.ACTION_CHOOSE, choice);
                be.clientChoice = choice;
                click();
            }).icon(ICONS[c]).when(() -> be.step == RpsBlockEntity.CHOOSE && !iChose(side) && be.clientChoice == Rps.NONE));
            b.selected = be.clientChoice == c;
        }
    }

    @Override
    protected Component gameStatus(int side) {
        if (be.step == RpsBlockEntity.REVEAL) {
            if (be.roundWinner == 0) return Component.translatable("screen.heralizcasino.rps.draw");
            return Component.translatable("screen.heralizcasino.rps.round_for", be.name(be.roundWinner));
        }
        if (side != 0 && !iChose(side) && be.clientChoice == Rps.NONE) {
            return Component.translatable("screen.heralizcasino.rps.choose", be.round, secondsLeft());
        }
        int other = side == 1 ? 2 : 1;
        if (side != 0) return Component.translatable("screen.heralizcasino.rps.wait", be.name(other), secondsLeft());
        return Component.translatable("screen.heralizcasino.rps.spectate", be.round, secondsLeft());
    }

    @Override
    protected int statusColor() {
        if (be.phase == RpsBlockEntity.PLAYING && be.step == RpsBlockEntity.CHOOSE && mySide() != 0 && be.clientChoice == Rps.NONE) {
            return secondsLeft() <= 5 ? RED : GREEN;
        }
        return super.statusColor();
    }

    /** Le duel se joue dans la scene 3D (mains dans le dos, objets lances sur la table) : ici seulement le score. */
    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float partial) {
        int x = 10, y = 34;
        float since = be.step == RpsBlockEntity.REVEAL ? partialNow() - be.revealTick : 0;
        String score = be.shownScore(1, since) + " - " + be.shownScore(2, since);
        UiKit.outlined(g, Component.literal(score), x, y, GOLD, 1.4f, -1);
        UiKit.outlined(g, Component.translatable("screen.heralizcasino.rps.first_to", rules().winsNeeded), x + UiKit.font().width(score) * 1.4f + 8, y + 3, GREY, 0.7f, -1);
        int side = mySide();
        if (side != 0 && be.step == RpsBlockEntity.CHOOSE && be.clientChoice >= Rps.ROCK) {
            // mon coup, que moi seul connais avant la revelation
            Component c = Component.translatable("screen.heralizcasino.rps.your_choice_is", Component.translatable("screen.heralizcasino.rps." + Rps.KEYS[be.clientChoice]));
            g.renderItem(ICONS[be.clientChoice], x, y + 15);
            UiKit.outlined(g, c, x + 20, y + 19, CREAM, 0.9f, -1);
        }
    }
}
