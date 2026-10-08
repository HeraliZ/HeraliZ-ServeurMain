package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.PlinkoBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.plinko.PlinkoGame;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plinko : combien de billes, a quel prix, et on lache. La camera cadre toute la planche.
 */
public class PlinkoScreen extends MachineScreen<PlinkoBlockEntity> {
    private static final int[] COUNTS = {1, 3, 5, 10};
    private static int countIndex = 0;
    private final List<UiButton> countButtons = new ArrayList<>();

    public PlinkoScreen(PlinkoBlockEntity be) {
        super(be);
    }

    @Override
    protected void dock(Dock d) {
        d.bet();
        countButtons.clear();
        for (int i = 0; i < COUNTS.length; i++) {
            final int k = i;
            countButtons.add(d.add(new UiButton(24, 18, Component.literal("×" + COUNTS[i]), UiAtlas.DARK, b -> {
                countIndex = k;
                click();
            }).scale(0.85f).tip(() -> Component.translatable("screen.heralizcasino.plinko.count_tip", COUNTS[k]))));
        }
        d.add(new UiButton(96, 18, Component.empty(), UiAtlas.GOLD, b -> primary())
                .label(() -> Component.translatable("screen.heralizcasino.plinko.drop", Chips.format(bet * COUNTS[countIndex])))
                .when(() -> myActive() < maxBalls()));
    }

    @Override
    public void tick() {
        super.tick();
        for (int i = 0; i < countButtons.size(); i++) countButtons.get(i).selected = i == countIndex;
    }

    private int maxBalls() {
        return CasinoConfig.get().plinko.maxBallsPerPlayer;
    }

    private int myActive() {
        int n = 0;
        long now = now();
        for (PlinkoBlockEntity.Ball b : be.balls) if (isMe(b.player) && now < b.start + PlinkoGame.DURATION - PlinkoGame.LAND / 2) n++;
        return n;
    }

    @Override
    protected void primary() {
        int count = COUNTS[countIndex];
        if (!checkBet(bet * count)) return;
        send(PlinkoBlockEntity.ACTION_DROP, bet, count);
        chipSound();
    }

    @Override
    protected Component status() {
        float now = partialNow();
        StringBuilder last = new StringBuilder();
        int shown = 0;
        for (int i = be.balls.size() - 1; i >= 0 && shown < 5; i--) {
            PlinkoBlockEntity.Ball b = be.balls.get(i);
            if (!isMe(b.player) || now < b.start + PlinkoGame.DURATION - PlinkoGame.LAND / 2f) continue;
            int k = PlinkoGame.bucket(b.path);
            if (k < 0 || k >= be.multipliers.length) continue;
            if (last.length() > 0) last.append("  ");
            last.append(trim(be.multipliers[k]));
            shown++;
        }
        int active = myActive();
        if (active > 0) return Component.translatable("screen.heralizcasino.plinko.active", active, maxBalls());
        if (last.length() > 0) return Component.translatable("screen.heralizcasino.plinko.last", last.toString());
        return Component.translatable("screen.heralizcasino.plinko.idle");
    }

    private static String trim(double v) {
        if (Math.abs(v - Math.round(v)) < 0.005) return "x" + Math.round(v);
        return "x" + String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    protected List<Component> infoExtra() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < be.multipliers.length; i++) {
            if (i > 0) s.append(" · ");
            s.append(trim(be.multipliers[i]).substring(1));
        }
        return List.of(Component.translatable("screen.heralizcasino.plinko.info_buckets", s.toString()));
    }
}
