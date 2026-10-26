package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.HorseRaceBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.race.RaceRules;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Course de chevaux : on choisit sa mise puis son cheval (bouton, ligne du tableau ou couloir en 3D) ;
 * pendant la course, la camera suit le cheval de tete.
 */
public class RaceScreen extends RoundMachineScreen<HorseRaceBlockEntity> {
    public RaceScreen(HorseRaceBlockEntity be) {
        super(be);
    }

    @Override
    protected void dock(Dock d) {
        d.bet();
        for (int i = 0; i < RaceRules.HORSES; i++) {
            final int h = i;
            d.add(new UiButton(38, 18, Component.empty(), UiAtlas.DARK, b -> placeBet(h))
                    .label(() -> Component.literal(String.format(Locale.ROOT, "x%.1f", be.odds[h])))
                    .swatch(RaceRules.COLORS[h]).scale(0.85f).when(() -> be.acceptingBets()).tip(() -> tip(h)));
        }
        cancelButton(d);
    }

    private Component tip(int h) {
        Component c = Component.translatable("screen.heralizcasino.race.tip", h + 1, RaceRules.NAMES[h], String.format(Locale.ROOT, "%.2f", be.odds[h]));
        long mine = myBetOn(h);
        return mine > 0 ? c.copy().append(Component.translatable("screen.heralizcasino.your_bet_suffix", Chips.format(mine))) : c;
    }

    @Override
    protected boolean hotspotEnabled(int id) {
        return be.acceptingBets();
    }

    @Override
    protected void onHotspot(int id) {
        placeBet(id);
    }

    @Override
    protected Component hotspotTip(int id) {
        return tip(id);
    }

    @Override
    protected int hotspotColor(int id) {
        return RaceRules.COLORS[id];
    }

    @Override
    protected Component idleText() {
        return Component.translatable("screen.heralizcasino.race.idle");
    }

    @Override
    protected Component runningText() {
        return Component.translatable("screen.heralizcasino.race.running");
    }

    @Override
    protected Component resultText() {
        if (be.winner < 0) return Component.empty();
        int w = be.winner;
        return Component.translatable("screen.heralizcasino.race.result",
                Component.literal(RaceRules.NAMES[w]).withStyle(s -> s.withBold(true).withColor(RaceRules.COLORS[w])));
    }

    @Override
    protected List<Component> infoExtra() {
        List<Component> out = new ArrayList<>();
        for (int i = 0; i < RaceRules.HORSES; i++) {
            final int h = i;
            out.add(Component.literal((i + 1) + ". ").append(Component.literal(RaceRules.NAMES[i]).withStyle(s -> s.withColor(RaceRules.COLORS[h])))
                    .append(Component.literal(String.format(Locale.ROOT, "  x%.2f", be.odds[i]))));
        }
        return out;
    }
}
