package fr.heraliz.emote.client.gui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Bouton sobre (repris des caisses) : texte en capitales espacees, filet fin qui prend la couleur d'accent au survol.
 * Styles : contour (defaut), principal (teinte d'accent), pastille (petite etiquette coloree), lien (texte seul).
 * Son de clic maison (ui_clic) a la place du son vanilla.
 */
public class HzButton extends AbstractButton {
    public enum Look { OUTLINE, PRIMARY, PILL, LINK }

    private final Runnable action;
    private int accent;
    private Look look = Look.OUTLINE;
    private boolean danger;

    public HzButton(int x, int y, int w, int h, Component label, int accent, Runnable action) {
        super(x, y, w, h, label);
        this.accent = accent & 0xFFFFFF;
        this.action = action;
    }

    public HzButton look(Look look) {
        this.look = look;
        return this;
    }

    public HzButton accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    /** Lien ou contour en rouge (actions dangereuses). */
    public HzButton danger() {
        this.danger = true;
        this.accent = Ui.DANGER;
        return this;
    }

    public void setBounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        height = h;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        SonsUi.clic(0.6F);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHovered;
        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        switch (look) {
            case LINK -> {
                int base = danger ? Ui.DANGER : Ui.MUTED;
                int c = !active ? Ui.argb(Ui.FAINT, 255) : hover ? Ui.argb(danger ? Ui.DANGER : Ui.TEXT, 255) : Ui.argb(base, 255);
                Ui.spaced(g, font, label, Ui.SMALL, x, y + (h - 6) / 2f, c, 0.8f);
                float tw = Ui.spacedWidth(font, label.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 0.8f);
                if (hover) g.fill(x, y + h - 1, x + (int) tw, y + h, Ui.argb(accent, 160));
            }
            case PILL -> {
                Ui.soft(g, x, y, w, h, Ui.argb(accent, hover ? 44 : 26));
                Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(accent, hover ? 150 : 70));
                int c = Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.25f), 255);
                Ui.spacedCentered(g, font, label, Ui.SMALL, x + w / 2f, y + (h - 6) / 2f, c, 0.6f);
            }
            default -> {
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
                Ui.spacedCentered(g, font, label, Ui.BOLD, x + w / 2f, y + (h - 8) / 2f, text, 1.2f);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
