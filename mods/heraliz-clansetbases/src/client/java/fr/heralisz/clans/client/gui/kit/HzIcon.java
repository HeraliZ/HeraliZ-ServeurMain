package fr.heralisz.clans.client.gui.kit;

import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Bouton-icone (croix de fermeture, chevrons...) : une icone de la planche, attenuee au repos, blanche au survol avec
 * un leger disque de fond. Le libelle sert d'infobulle et de narration.
 */
public class HzIcon extends AbstractButton {
    private final int icone;
    private final Runnable action;
    private int couleur = Ui.MUTED, couleurSurvol = Ui.TEXT;

    public HzIcon(int x, int y, int taille, int icone, Component infobulle, Runnable action) {
        super(x, y, taille, taille, infobulle);
        this.icone = icone;
        this.action = action;
    }

    public HzIcon couleurs(int repos, int survol) {
        this.couleur = repos & 0xFFFFFF;
        this.couleurSurvol = survol & 0xFFFFFF;
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        if (Sons.UI_CLIC.isPresent()) sounds.play(SimpleSoundInstance.forUI(Sons.UI_CLIC.get(), 0.6F));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHovered;
        int t = getWidth();
        if (hover) Ui.soft(g, getX() - 2, getY() - 2, t + 4, t + 4, Ui.argb(0xFFFFFF, 18));
        Icons.draw(g, icone, getX(), getY(), t, hover ? couleurSurvol : couleur, active ? 255 : 90);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
