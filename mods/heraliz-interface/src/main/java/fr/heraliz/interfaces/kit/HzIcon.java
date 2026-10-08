package fr.heraliz.interfaces.kit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Bouton-icone (croix de fermeture...) copie du kit des clans : une icone de la planche, attenuee au repos, blanche au
 * survol avec un leger fond.
 */
public class HzIcon extends AbstractButton {
    private final int icone;
    private final Runnable action;

    public HzIcon(int x, int y, int taille, int icone, Component infobulle, Runnable action) {
        super(x, y, taille, taille, infobulle);
        this.icone = icone;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        Sons.jouer(Sons.CLIC, 0.6f);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHovered;
        int t = getWidth();
        if (hover) Ui.soft(g, getX() - 2, getY() - 2, t + 4, t + 4, Ui.argb(0xFFFFFF, 18));
        Icons.draw(g, icone, getX(), getY(), t, hover ? Ui.TEXT : Ui.MUTED, active ? 255 : 90);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
