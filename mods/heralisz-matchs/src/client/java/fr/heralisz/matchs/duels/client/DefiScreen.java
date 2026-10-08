package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Defi recu : petit panneau 300 x 150 a l'echelle du joueur (le monde reste visible), ouvert a la reception si aucun
 * ecran n'est ouvert. « Vortex vous defie », format · mode · carte, camps en 2v2, barre des 30 s, ACCEPTER / REFUSER.
 * Echap ferme sans repondre (le defi reste en attente : {@code /duel} ou {@code /duel accepter}).
 */
public class DefiScreen extends Screen implements EcranDuels {
    private static final int W = 300, H = 150;
    private final Vues.DefiVue defi;
    private int px, py;
    private Component ligne1 = Component.empty(), ligne2 = Component.empty(), camps = Component.empty(), aide = Component.empty();
    private String titre = "";

    public DefiScreen(Vues.DefiVue d) {
        super(Component.translatable("duel.defi.titre"));
        this.defi = d;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        titre = Component.translatable("duel.defi.titre").getString();
        ligne1 = Ui.tr(Ui.BOLD, "duel.defi.vous_defie", defi.defiant());
        ligne2 = Ui.tr(Ui.SMALL, "duel.defi.details", defi.format().toUpperCase(Locale.ROOT), Component.translatable("duel.mode." + defi.mode()), defi.carte());
        camps = defi.campA().size() > 1 ? Ui.tr(Ui.FONT, "duel.defi.camps", String.join(" & ", defi.campA()), String.join(" & ", defi.campB())) : Component.empty();
        aide = Ui.tr(Ui.SMALL, "duel.defi.aide");
        addRenderableWidget(new HzIcon(px + W - 22, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        addRenderableWidget(new HzButton(px + 16, py + H - 16 - 20, 120, 20, Component.translatable("duel.ecran.accepter"), Ui.OK, () -> repondre(true))
                .look(HzButton.Look.PRIMARY));
        addRenderableWidget(new HzButton(px + 146, py + H - 16 - 20, 90, 20, Component.translatable("duel.ecran.refuser"), Ui.DANGER, () -> repondre(false)));
    }

    private void repondre(boolean accepte) {
        if (minecraft != null && minecraft.getConnection() != null) MatchsNet.versServeur(new PaquetsDuels.ReponseDefiC2S(defi.id(), accepte));
        onClose();
    }

    @Override
    public void tick() {
        if (DuelsClient.defi() == null || DuelsClient.defi().id() != defi.id()) {
            if (minecraft != null) minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fondLeger(g, width, height);
        Ui.glass(g, px, py, W, H);
        Ui.spaced(g, font, titre, Ui.BOLD, px + 16, py + 11, Ui.argb(Ui.OR, 255), 1.6f);
        Ui.hairline(g, px + 8, px + W - 8, py + 27, 0xFFFFFF, 40, true, true);
        Ui.gros(g, font, ligne1, px + 16, py + 38, 1.4f, Ui.argb(Ui.TEXT, 255));
        g.drawString(font, ligne2, px + 16, py + 58, Ui.argb(Ui.MUTED, 255), false);
        if (defi.campA().size() > 1) g.drawString(font, camps, px + 16, py + 72, Ui.argb(Ui.TEXT, 220), false);
        long reste = DuelsClient.defiResteMs();
        Ui.bar(g, px + 16, py + 90, W - 32, 2, reste / (float) DuelsConfig.DEFI_MS, reste < 8000 ? Ui.DANGER : Ui.OR);
        g.drawString(font, aide, px + 16, py + 98, Ui.argb(Ui.FAINT, 255), false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        SonsUi.fermer(0.6f);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public String journal() {
        return "panneau " + px + "," + py + " " + W + "x" + H + " accepter " + (px + 16) + "," + (py + H - 36) + " refuser " + (px + 146) + "," + (py + H - 36)
                + " defi " + defi.id();
    }
}
