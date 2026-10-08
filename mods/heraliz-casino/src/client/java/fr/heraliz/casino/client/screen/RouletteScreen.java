package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.RoundBlockEntity;
import fr.heraliz.casino.block.entity.RouletteBlockEntity;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.roulette.Roulette;
import fr.heraliz.casino.game.roulette.RouletteLayout;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Roulette : on mise en cliquant directement sur le tapis 3D ; pendant le lancer, la camera plonge sur le cylindre.
 */
public class RouletteScreen extends RoundMachineScreen<RouletteBlockEntity> {
    public RouletteScreen(RouletteBlockEntity be) {
        super(be);
    }

    @Override
    protected void dock(Dock d) {
        d.bet();
        cancelButton(d);
    }

    @Override
    protected int cameraMode() {
        if (be.phase == RoundBlockEntity.RUNNING) return MachineDesign.CAM_PLAY;
        // resultat : on voit d'abord la bille dans sa case, puis le tapis (dolly, gains)
        if (be.phase == RoundBlockEntity.RESULT && partialNow() - be.phaseStart < 50) return MachineDesign.CAM_RESULT;
        return MachineDesign.CAM_MENU;
    }

    @Override
    protected boolean hotspotEnabled(int id) {
        return be.acceptingBets() && Roulette.valid(id, be.zeros);
    }

    @Override
    protected void onHotspot(int id) {
        placeBet(id);
    }

    @Override
    protected int hotspotColor(int id) {
        if (id <= Roulette.DOUBLE_ZERO) return Roulette.isRed(id) ? 0xFF6A6A : id == 0 || id == Roulette.DOUBLE_ZERO ? 0x6AFF9A : 0xFFFFFF;
        return 0xFFD36A;
    }

    @Override
    protected Component hotspotTip(int id) {
        String label = RouletteLayout.label(id);
        Component name = id <= Roulette.DOUBLE_ZERO ? Component.translatable("screen.heralizcasino.roulette.number", label)
                : Component.translatable("screen.heralizcasino.roulette.spot." + label.toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_').replace(':', '_'));
        Component c = Component.translatable("screen.heralizcasino.roulette.tip", name, Roulette.returnMultiplier(id) - 1);
        long mine = myBetOn(id);
        return mine > 0 ? c.copy().append(Component.translatable("screen.heralizcasino.your_bet_suffix", Chips.format(mine))) : c;
    }

    @Override
    protected Component idleText() {
        return Component.translatable("screen.heralizcasino.roulette.idle");
    }

    @Override
    protected Component runningText() {
        return Component.translatable("screen.heralizcasino.roulette.spinning");
    }

    @Override
    protected Component resultText() {
        if (be.number < 0) return Component.empty();
        int n = be.number;
        String color = n == 0 || n == Roulette.DOUBLE_ZERO ? "green" : Roulette.isRed(n) ? "red" : "black";
        return Component.translatable("screen.heralizcasino.roulette.result", Roulette.label(n), Component.translatable("screen.heralizcasino.roulette." + color));
    }

    @Override
    protected List<Component> infoExtra() {
        return List.of(Component.translatable("screen.heralizcasino.roulette.info_plein"),
                Component.translatable("screen.heralizcasino.roulette.info_douzaine"),
                Component.translatable(be.zeros == 1 ? "screen.heralizcasino.roulette.info_zero1" : "screen.heralizcasino.roulette.info_zero2"));
    }
}
