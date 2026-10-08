package fr.heralisz.clans.client.gui.kit;

import fr.heralisz.clans.registry.Sons;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * « Maintenir pour confirmer » : remplace les fenetres de confirmation. Le bouton est un contour ; tant que la souris
 * reste appuyee dessus, une barre se remplit en bas (duree en ms) ; relacher avant annule ; au bout, l'action est
 * declenchee une fois et il faut relacher pour rearmer.
 */
public class Hold extends AbstractWidget {
    private final long dureeMs;
    private final Runnable action;
    private int couleur = Ui.DANGER;
    private long depuis = -1;
    private boolean declenche;

    public Hold(int x, int y, int w, int h, Component label, long dureeMs, Runnable action) {
        super(x, y, w, h, label);
        this.dureeMs = Math.max(100, dureeMs);
        this.action = action;
    }

    public Hold couleur(int rgb) {
        this.couleur = rgb & 0xFFFFFF;
        return this;
    }

    /** Progression 0..1 de l'appui en cours. */
    public float progression() {
        if (depuis < 0) return 0;
        return Math.min(1, (Util.getMillis() - depuis) / (float) dureeMs);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mx, my)) return false;
        depuis = Util.getMillis();
        declenche = false;
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
        float p = progression();
        if (p >= 1 && !declenche) {
            declenche = true;
            if (Sons.UI_CLIC.isPresent()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.UI_CLIC.get(), 0.8F));
            action.run();
        }
        boolean hover = active && isHovered;
        int fill = !active ? Ui.argb(0xFFFFFF, 4) : Ui.argb(couleur, hover ? 26 : 12);
        int edge = !active ? Ui.argb(0xFFFFFF, 20) : Ui.argb(couleur, hover ? 200 : 110);
        Ui.soft(g, x, y, w, h, fill);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, edge);
        if (p > 0) {
            Ui.soft(g, x, y, Math.max(2, Math.round(w * p)), h, Ui.argb(couleur, 60));
            g.fill(x + 1, y + h - 2, x + 1 + Math.round((w - 2) * p), y + h - 1, Ui.argb(couleur, 240));
        }
        Font font = Minecraft.getInstance().font;
        int text = !active ? Ui.argb(0xFFFFFF, 70) : Ui.argb(Ui.mix(couleur, 0xFFFFFF, hover || p > 0 ? 0.6f : 0.25f), 255);
        Ui.spacedCentered(g, font, getMessage().getString(), Ui.BOLD, x + w / 2f, y + (h - 8) / 2f, text, 1.2f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
