package fr.heraliz.interfaces.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Petit bouton secondaire (demi-largeur) : icone + libelle en petites capitales espacees, centres. Meme survol anime
 * que {@link MenuLigne} (fond, contour d'accent), en plus discret. {@link #opacite} / {@link #decalageY} : animation
 * d'ouverture de l'ecran.
 */
public class Tuile extends AbstractButton {
    private final Runnable action;
    private final String libelle;
    private final int icone;
    private final int accent;
    private float survol;
    private final Anim.Horloge horloge = new Anim.Horloge();
    public float opacite = 1f, decalageY = 0f;

    public Tuile(int x, int y, int w, int h, int icone, Component libelle, int accent, Runnable action) {
        super(x, y, w, h, libelle);
        this.icone = icone;
        this.libelle = libelle.getString();
        this.accent = accent & 0xFFFFFF;
        this.action = action;
    }

    public Tuile actif(boolean a) {
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

    private int a(int alpha) {
        return Math.round(alpha * opacite);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // sous ~6 %, les textes les plus pales passeraient sous l'opacite 4, que Minecraft dessine opaque
        if (opacite < 0.06f) return;
        boolean hover = active && isHoveredOrFocused();
        survol = Anim.vers(survol, hover ? 1f : 0f, horloge.dt(), 16f);
        float s = survol;
        int x = getX(), w = getWidth(), h = getHeight();
        int y = Math.round(getY() + decalageY);
        Font font = Minecraft.getInstance().font;
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, a(active ? (int) (5 + 10 * s) : 3)));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(Ui.mix(0xFFFFFF, accent, s), a(active ? (int) (24 + 80 * s) : 12)));
        int texte = active ? Ui.argb(Ui.mix(Ui.mix(Ui.TEXT, Ui.MUTED, 0.25f), 0xFFFFFF, s), a(255)) : Ui.argb(0xFFFFFF, a(70));
        int couleurIcone = active ? Ui.argb(Ui.mix(Ui.MUTED, Ui.mix(accent, 0xFFFFFF, 0.35f), s), a(255)) : Ui.argb(0xFFFFFF, a(50));
        int t = 8;
        float lw = Ui.spacedWidth(font, libelle, Ui.SMALL, 0.8f);
        float total = t + 6 + lw;
        float x0 = x + (w - total) / 2f;
        Icons.draw(g, icone, Math.round(x0), y + (h - t) / 2, t, couleurIcone & 0xFFFFFF, couleurIcone >>> 24);
        Ui.spaced(g, font, libelle, Ui.SMALL, Math.round(x0 + t + 6), y + (h - 6) / 2f, texte, 0.8f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
