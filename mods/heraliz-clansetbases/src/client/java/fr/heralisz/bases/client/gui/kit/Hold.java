package fr.heralisz.bases.client.gui.kit;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * « Maintenir pour confirmer » (copie du kit des clans) : remplace les fenetres de confirmation des actions qui coutent
 * (creer la base, debloquer une salle, lancer un pillage). Tant que la souris reste appuyee dessus, une barre se
 * remplit (duree en ms) ; relacher ou quitter le bouton annule ; au bout, l'action part une fois. Au repos, le bouton
 * est teinte de sa couleur (style principal) ; un petit son monte pendant l'appui.
 */
public class Hold extends AbstractWidget {
    private final long dureeMs;
    private final Runnable action;
    private final String libelle;
    private int couleur = Ui.DANGER;
    private long depuis = -1;
    private boolean declenche;
    private float gel = -1;

    public Hold(int x, int y, int w, int h, Component label, long dureeMs, Runnable action) {
        super(x, y, w, h, label);
        this.dureeMs = Math.max(100, dureeMs);
        this.action = action;
        this.libelle = label.getString();
    }

    public Hold couleur(int rgb) {
        this.couleur = rgb & 0xFFFFFF;
        return this;
    }

    public Hold actif(boolean a) {
        this.active = a;
        return this;
    }

    /** Progression 0..1 de l'appui en cours. */
    public float progression() {
        if (depuis < 0) return 0;
        return Math.min(1, (Util.getMillis() - depuis) / (float) dureeMs);
    }

    /** Banc d'essai : fige la barre a {@code fraction} (capture a mi-course ; l'action ne part jamais), -1 = normal. */
    public void figer(float fraction) {
        gel = fraction;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mx, my)) return false;
        depuis = Util.getMillis();
        declenche = false;
        Sons.jouer(Sons.CLIC, 0.4f, 0.8f);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean was = depuis >= 0;
        depuis = -1;
        declenche = false;
        return was;
    }

    @Override
    public void playDownSound(SoundManager sounds) {
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // la souris est partie : on annule
        if (depuis >= 0 && !isMouseOver(mouseX, mouseY)) {
            depuis = -1;
            declenche = false;
        }
        float p = gel >= 0 ? gel : progression();
        if (gel < 0 && p >= 1 && !declenche) {
            declenche = true;
            Sons.jouer(Sons.CLIC, 0.8f, 1.2f);
            action.run();
        }
        boolean hover = active && isHovered;
        int fill = !active ? Ui.argb(0xFFFFFF, 4) : Ui.argb(couleur, hover ? 52 : 30);
        int edge = !active ? Ui.argb(0xFFFFFF, 20) : Ui.argb(Ui.mix(couleur, 0xFFFFFF, hover ? 0.3f : 0f), hover ? 255 : 160);
        Ui.soft(g, x, y, w, h, fill);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, edge);
        if (p > 0) {
            Ui.soft(g, x, y, Math.max(2, Math.round(w * p)), h, Ui.argb(couleur, 80));
            g.fill(x + 1, y + h - 2, x + 1 + Math.round((w - 2) * p), y + h - 1, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.4f), 255));
        }
        Font font = Minecraft.getInstance().font;
        int text = !active ? Ui.argb(0xFFFFFF, 70) : 0xFFFFFFFF;
        Ui.spacedCentered(g, font, libelle, Ui.BOLD, x + w / 2f, y + (h - 8) / 2f, text, 1.2f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
