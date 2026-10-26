package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.MinesBlockEntity;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.mines.MinesRules;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Demineur : on clique directement sur les dalles de la table 3D. Chaque gemme augmente le gain ; on encaisse
 * quand on veut.
 */
public class MinesScreen extends MachineScreen<MinesBlockEntity> {
    private static final int[] PRESETS = {1, 3, 5, 10, 24};
    private static int mineCount = 3;

    public MinesScreen(MinesBlockEntity be) {
        super(be);
    }

    private boolean playing() {
        return be.phase == MinesBlockEntity.PLAYING && isMe(be.player);
    }

    private boolean canStart() {
        return be.free(now()) || (be.phase != MinesBlockEntity.PLAYING && isMe(be.player));
    }

    @Override
    protected int layoutKey() {
        return playing() ? 1 : 0;
    }

    @Override
    protected void dock(Dock d) {
        if (playing()) {
            d.add(new UiButton(170, 20, Component.empty(), UiAtlas.GREEN, b -> primary())
                    .label(() -> Component.translatable("screen.heralizcasino.mines.cashout", Chips.format(be.bet * be.mult100 / 100),
                            String.format(Locale.ROOT, "%.2f", be.mult100 / 100.0)))
                    .when(() -> playing() && be.revealedCount() > 0));
            return;
        }
        d.bet();
        d.add(new UiButton(14, 18, Component.literal("−"), UiAtlas.DARK, b -> {
            mineCount = Math.max(1, mineCount - 1);
            click();
        }));
        d.add(new UiButton(52, 18, Component.empty(), UiAtlas.DARK, b -> {
            int k = 0;
            while (k < PRESETS.length && PRESETS[k] <= mineCount) k++;
            mineCount = PRESETS[k % PRESETS.length];
            click();
        }).label(() -> Component.translatable("screen.heralizcasino.mines.count", mineCount)).color(0xFF7A6A).scale(0.85f)
                .tip(() -> Component.translatable("screen.heralizcasino.mines.count_tip")));
        d.add(new UiButton(14, 18, Component.literal("+"), UiAtlas.DARK, b -> {
            mineCount = Math.min(MinesRules.TILES - 1, mineCount + 1);
            click();
        }));
        d.button(Component.translatable("screen.heralizcasino.mines.start"), UiAtlas.GOLD, 64, this::primary).when(this::canStart);
    }

    @Override
    protected void primary() {
        if (playing()) {
            if (be.revealedCount() > 0) send(MinesBlockEntity.ACTION_CASHOUT);
            return;
        }
        if (!canStart() || !checkBet(bet)) return;
        send(MinesBlockEntity.ACTION_START, bet, mineCount);
        chipSound();
    }

    @Override
    protected boolean hotspotEnabled(int id) {
        return playing() && (be.revealed & (1 << id)) == 0;
    }

    @Override
    protected void onHotspot(int id) {
        send(MinesBlockEntity.ACTION_REVEAL, id);
        click();
    }

    @Override
    protected int hotspotColor(int id) {
        return 0x3DF5A0;
    }

    private String next() {
        int revealed = be.revealedCount();
        int safe = MinesRules.TILES - be.mines;
        if (revealed >= safe) return "-";
        return String.format(Locale.ROOT, "x%.2f", CasinoConfig.get().mines.multiplier(be.mines, revealed + 1));
    }

    @Override
    protected Component status() {
        if (playing()) return Component.translatable("screen.heralizcasino.mines.playing", next());
        if (be.phase == MinesBlockEntity.PLAYING) return Component.translatable("screen.heralizcasino.occupied_by", be.playerName);
        boolean mine = isMe(be.player);
        if (be.phase == MinesBlockEntity.BOOM && !be.free(now())) {
            return Component.translatable(mine ? "screen.heralizcasino.mines.boom" : "screen.heralizcasino.other_lost", be.playerName);
        }
        if (be.phase == MinesBlockEntity.CASHED && !be.free(now())) {
            return Component.translatable(mine ? "screen.heralizcasino.won" : "screen.heralizcasino.other_won", Chips.format(be.win), mult(be.mult100 / 100.0), be.playerName);
        }
        return Component.translatable("screen.heralizcasino.mines.idle");
    }

    @Override
    protected int statusColor() {
        if (playing()) return GREEN;
        if (be.phase == MinesBlockEntity.BOOM && !be.free(now())) return RED;
        if (be.phase == MinesBlockEntity.CASHED && !be.free(now())) return GOLD;
        return CREAM;
    }

    @Override
    protected List<Component> infoExtra() {
        MinesRules r = CasinoConfig.get().mines;
        int m = playing() ? be.mines : mineCount;
        StringBuilder s = new StringBuilder();
        for (int k = 1; k <= Math.min(5, MinesRules.TILES - m); k++) {
            if (k > 1) s.append("  ");
            s.append(String.format(Locale.ROOT, "x%.2f", r.multiplier(m, k)));
        }
        return List.of(Component.translatable("screen.heralizcasino.mines.info_table", m, s.toString()));
    }
}
