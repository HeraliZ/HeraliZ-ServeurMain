package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.CrashBlockEntity;
import fr.heraliz.casino.block.entity.RoundBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.crash.CrashGame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * La Fusee : avant le decollage, une mise (et un encaissement automatique optionnel) ; en vol, un seul gros bouton
 * ENCAISSER. En bas, les derniers points d'explosion.
 */
public class CrashScreen extends RoundMachineScreen<CrashBlockEntity> {
    private static final long[] AUTO = {0, 150, 200, 300, 500, 1000};
    private static int autoIndex = 0;

    public CrashScreen(CrashBlockEntity be) {
        super(be);
    }

    private long inPlay() {
        UUID me = me();
        return me == null ? 0 : be.inPlay(me);
    }

    private boolean canCash() {
        return be.phase == RoundBlockEntity.RUNNING && !be.crashed && inPlay() > 0;
    }

    @Override
    protected int layoutKey() {
        return canCash() ? 1 : 0;
    }

    /** Le bouton d'encaissement reste toujours sous la main, meme quand l'ecran de la fusee est en grand. */
    @Override
    protected boolean keepDock() {
        return true;
    }

    @Override
    protected void dock(Dock d) {
        if (canCash()) {
            d.add(new UiButton(170, 20, Component.empty(), UiAtlas.GREEN, b -> primary()).label(this::cashLabel).scale(1.1f));
            return;
        }
        d.bet();
        d.add(new UiButton(64, 18, Component.empty(), UiAtlas.DARK, b -> {
            autoIndex = (autoIndex + 1) % AUTO.length;
            click();
        }).label(this::autoLabel).scale(0.85f).tip(() -> Component.translatable("screen.heralizcasino.crash.auto_tip")));
        d.button(Component.translatable("screen.heralizcasino.crash.bet"), UiAtlas.GOLD, 70, this::primary).when(() -> be.acceptingBets());
        cancelButton(d);
    }

    private Component autoLabel() {
        long a = AUTO[autoIndex];
        return a == 0 ? Component.translatable("screen.heralizcasino.crash.auto_off")
                : Component.translatable("screen.heralizcasino.crash.auto_at", String.format(Locale.ROOT, "%.1f", a / 100.0));
    }

    private double liveMultiplier() {
        if (be.crashed) return be.crashShown / 100.0;
        if (be.phase != RoundBlockEntity.RUNNING) return 1.0;
        return CrashGame.multiplier(be.growth, Math.max(0, partialNow() - be.phaseStart));
    }

    private Component cashLabel() {
        double m = liveMultiplier();
        long gain = (long) Math.floor(inPlay() * Math.floor(m * 100) / 100.0);
        return Component.translatable("screen.heralizcasino.crash.cashout", Chips.format(gain), String.format(Locale.ROOT, "%.2f", m));
    }

    @Override
    protected void primary() {
        if (canCash()) {
            send(CrashBlockEntity.ACTION_CASHOUT);
            return;
        }
        placeBet((int) AUTO[autoIndex]);
    }

    @Override
    protected Component idleText() {
        return Component.translatable("screen.heralizcasino.crash.idle");
    }

    @Override
    protected Component runningText() {
        long cashed = me() == null ? 0 : be.cashedOut(me());
        if (be.crashed) return Component.translatable("screen.heralizcasino.crash.boom", String.format(Locale.ROOT, "%.2f", be.crashShown / 100.0));
        if (cashed > 0) return Component.translatable("screen.heralizcasino.crash.cashed", String.format(Locale.ROOT, "%.2f", cashed / 100.0));
        return Component.translatable("screen.heralizcasino.crash.flying", String.format(Locale.ROOT, "%.2f", liveMultiplier()));
    }

    @Override
    protected Component resultText() {
        return Component.translatable("screen.heralizcasino.crash.boom", String.format(Locale.ROOT, "%.2f", be.crashShown / 100.0));
    }

    @Override
    protected int statusColor() {
        if (be.phase == RoundBlockEntity.RUNNING && !be.crashed) return liveMultiplier() >= 2 ? GREEN : CREAM;
        if (be.crashed || be.phase == RoundBlockEntity.RESULT) return RED;
        return super.statusColor();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        // derniers points d'explosion, au-dessus de la ligne d'etat
        int n = Math.min(10, be.history.length);
        if (n == 0) return;
        int[] w = new int[n];
        int total = 0;
        for (int k = 0; k < n; k++) {
            w[k] = font.width(String.format(Locale.ROOT, "x%.2f", be.history[k] / 100.0)) + 8;
            total += w[k] + 3;
        }
        float x = width / 2f - total / 2f, y = dockY - 44;
        for (int k = 0; k < n; k++) {
            long h = be.history[k];
            UiKit.pill(g, x, y, w[k], 13);
            UiKit.text(g, Component.literal(String.format(Locale.ROOT, "x%.2f", h / 100.0)), x + w[k] / 2f, y + 3, h >= 200 ? GREEN : RED, 1f, 0, true);
            x += w[k] + 3;
        }
    }

    @Override
    protected List<Component> infoExtra() {
        return List.of(Component.translatable("screen.heralizcasino.crash.info_auto"));
    }
}
