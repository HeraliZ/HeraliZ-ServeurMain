package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Petite croix (retirer une ligne ; fermer) dessinee au pixel, rouge au survol si « danger ». */
public class Croix extends AbstractWidget {
    private final Runnable action;
    private final boolean danger;

    public Croix(int x, int y, int taille, boolean danger, Runnable action) {
        super(x, y, taille, taille, Component.empty());
        this.action = action;
        this.danger = danger;
    }

    @Override
    public void onClick(double mx, double my) {
        action.run();
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        SonsUi.clic(0.6F);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHovered;
        int c = Ui.argb(hover ? (danger ? Ui.DANGER : Ui.TEXT) : Ui.MUTED, active ? 255 : 90);
        int n = 7, x = getX() + (getWidth() - n) / 2, y = getY() + (getHeight() - n) / 2;
        for (int i = 0; i < n; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            g.fill(x + n - 1 - i, y + i, x + n - i, y + i + 1, c);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
