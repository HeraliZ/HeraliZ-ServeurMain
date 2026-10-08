package fr.heralisz.bases.client.gui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Bouton sobre (copie du kit des clans) : texte en capitales espacees, filet fin qui prend la couleur d'accent au
 * survol. Styles : contour (defaut), principal (teinte d'accent), lien (texte seul). Une icone optionnelle precede le
 * libelle. Son de clic maison (ui_clic). Le libelle est lu une fois (chaine gardee) : rien n'est cree a l'image.
 */
public class HzButton extends AbstractButton {
    public enum Look {OUTLINE, PRIMARY, LINK}

    private final Runnable action;
    private int accent;
    private Look look = Look.OUTLINE;
    private boolean danger;
    private int icone = -1;
    private final String libelle;

    public HzButton(int x, int y, int w, int h, Component label, int accent, Runnable action) {
        super(x, y, w, h, label);
        this.accent = accent & 0xFFFFFF;
        this.action = action;
        this.libelle = label.getString();
    }

    public HzButton look(Look look) {
        this.look = look;
        return this;
    }

    public HzButton accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    /** Icone de la planche (index de {@link Icons}) dessinee avant le libelle. */
    public HzButton icone(int index) {
        this.icone = index;
        return this;
    }

    /** Lien ou contour en rouge (actions dangereuses). */
    public HzButton danger() {
        this.danger = true;
        this.accent = Ui.DANGER;
        return this;
    }

    public HzButton actif(boolean a) {
        this.active = a;
        return this;
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
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHovered;
        Font font = Minecraft.getInstance().font;
        if (look == Look.LINK) {
            int base = danger ? Ui.DANGER : Ui.MUTED;
            int c = !active ? Ui.argb(Ui.FAINT, 255) : hover ? Ui.argb(danger ? Ui.DANGER : Ui.TEXT, 255) : Ui.argb(base, 255);
            int tx = x;
            if (icone >= 0) {
                Icons.draw(g, icone, x, y + (h - 8) / 2, 8, c & 0xFFFFFF, c >>> 24);
                tx += 11;
            }
            Ui.spaced(g, font, libelle, Ui.SMALL, tx, y + (h - 6) / 2f, c, 0.8f);
            float tw = Ui.spacedWidth(font, libelle, Ui.SMALL, 0.8f);
            if (hover) g.fill(tx, y + h - 1, tx + (int) tw, y + h, Ui.argb(accent, 160));
            return;
        }
        int fill, edge, text;
        if (!active) {
            fill = Ui.argb(0xFFFFFF, 4);
            edge = Ui.argb(0xFFFFFF, 20);
            text = Ui.argb(0xFFFFFF, 70);
        } else if (look == Look.PRIMARY) {
            fill = Ui.argb(accent, hover ? 70 : 34);
            edge = Ui.argb(Ui.mix(accent, 0xFFFFFF, hover ? 0.3f : 0f), hover ? 255 : 170);
            text = 0xFFFFFFFF;
        } else {
            fill = Ui.argb(0xFFFFFF, hover ? 18 : 7);
            edge = hover ? Ui.argb(accent, 230) : Ui.argb(0xFFFFFF, 46);
            text = hover ? 0xFFFFFFFF : Ui.argb(Ui.TEXT, 230);
        }
        Ui.soft(g, x, y, w, h, fill);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, edge);
        float lw = Ui.spacedWidth(font, libelle, Ui.BOLD, 1.2f);
        float cx = x + w / 2f;
        if (icone >= 0) {
            int ix = Math.round(cx - (lw + 13) / 2f);
            Icons.draw(g, icone, ix, y + (h - 9) / 2, 9, text & 0xFFFFFF, text >>> 24);
            cx += 6.5f;
        }
        Ui.spacedCentered(g, font, libelle, Ui.BOLD, cx, y + (h - 8) / 2f, text, 1.2f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
