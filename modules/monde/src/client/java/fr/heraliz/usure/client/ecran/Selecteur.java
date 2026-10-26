package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Choix dans une liste avec deux fleches (clic a gauche = precedent, a droite = suivant ; molette aussi). */
public class Selecteur extends AbstractWidget {
    private final String[] options;
    private final IntSupplier choisi;
    private final IntConsumer change;

    public Selecteur(int x, int y, int w, int h, String[] options, IntSupplier choisi, IntConsumer change) {
        super(x, y, w, h, Component.empty());
        this.options = options;
        this.choisi = choisi;
        this.change = change;
    }

    @Override
    public void onClick(double mx, double my) {
        decaler(mx < getX() + getWidth() / 2.0 ? -1 : 1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!active || !isMouseOver(mx, my)) return false;
        decaler(delta > 0 ? -1 : 1);
        SonsUi.clic(0.4F);
        return true;
    }

    private void decaler(int d) {
        change.accept(Math.floorMod(choisi.getAsInt() + d, options.length));
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        SonsUi.clic(0.6F);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHovered;
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, hover ? 16 : 8));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(0xFFFFFF, hover ? 50 : 26));
        int c = Ui.argb(active ? (hover ? Ui.accent() : Ui.MUTED) : Ui.FAINT, 255);
        fleche(g, x + 4, y + h / 2, true, hover && mouseX < x + w / 2 ? Ui.argb(Ui.accent(), 255) : c);
        fleche(g, x + w - 7, y + h / 2, false, hover && mouseX >= x + w / 2 ? Ui.argb(Ui.accent(), 255) : c);
        int i = Math.max(0, Math.min(options.length - 1, choisi.getAsInt()));
        var font = Minecraft.getInstance().font;
        String t = Ui.ellipsis(font, options[i], Ui.FONT, w - 18);
        Ui.centered(g, font, Ui.lit(t, Ui.FONT), x + w / 2f, y + (h - 8) / 2f, Ui.argb(active ? Ui.TEXT : Ui.FAINT, 255));
    }

    private static void fleche(GuiGraphics g, int x, int cy, boolean gauche, int couleur) {
        for (int i = 0; i < 3; i++) {
            int px = gauche ? x + i : x + 2 - i;
            g.fill(px, cy - i - 1, px + 1, cy + i, couleur);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
