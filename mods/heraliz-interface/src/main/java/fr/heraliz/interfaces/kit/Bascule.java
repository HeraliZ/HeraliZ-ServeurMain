package fr.heraliz.interfaces.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Reglage oui / non dessine comme une petite tuile du menu Echap (meme fond, meme contour au survol que {@link Tuile}
 * et que le choix de langue) : libelle en petites capitales a gauche, a droite l'etat en toutes lettres (« ACTIVÉ » en
 * vert, « DÉSACTIVÉ » attenue) et un interrupteur dont le curseur glisse. L'etat est relu a chaque image
 * ({@code etat}) : il suit le reglage meme s'il change ailleurs (Options de Minecraft). Trop etroit : l'etat en lettres
 * disparait, l'interrupteur reste. {@link #opacite} / {@link #decalageY} : animation d'ouverture de l'ecran.
 */
public class Bascule extends AbstractButton {
    private static final int INTER_L = 18, INTER_H = 9;
    private final String libelle, oui, non;
    private final BooleanSupplier etat;
    private final Runnable action;
    private final int accent;
    private float survol, curseur = -1;
    private final Anim.Horloge horloge = new Anim.Horloge();
    public float opacite = 1f, decalageY = 0f;

    /** {@code oui} / {@code non} : etat en toutes lettres ; {@code action} inverse le reglage. */
    public Bascule(int x, int y, int w, int h, Component libelle, String oui, String non, int accent, BooleanSupplier etat, Runnable action) {
        super(x, y, w, h, libelle);
        this.libelle = libelle.getString();
        this.oui = oui;
        this.non = non;
        this.accent = accent & 0xFFFFFF;
        this.etat = etat;
        this.action = action;
    }

    public boolean actif() {
        return etat.getAsBoolean();
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
        boolean on = actif();
        boolean hover = active && isHoveredOrFocused();
        float dt = horloge.dt();
        survol = Anim.vers(survol, hover ? 1f : 0f, dt, 16f);
        curseur = curseur < 0 ? (on ? 1f : 0f) : Anim.vers(curseur, on ? 1f : 0f, dt, 18f);
        float s = survol;
        int x = getX(), w = getWidth(), h = getHeight();
        int y = Math.round(getY() + decalageY);
        Font font = Minecraft.getInstance().font;
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, a((int) (5 + 10 * s))));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(Ui.mix(0xFFFFFF, accent, s), a((int) (24 + 80 * s))));

        // interrupteur a droite : piste verte (active) ou grise, curseur blanc qui glisse
        int ix = x + w - 8 - INTER_L, iy = y + (h - INTER_H) / 2;
        int piste = Ui.mix(0x4A4F59, Ui.OK, curseur);
        Ui.soft(g, ix, iy, INTER_L, INTER_H, Ui.argb(piste, a((int) (90 + 110 * curseur))));
        int cx = Math.round(ix + 1 + (INTER_L - INTER_H) * curseur);
        Ui.soft(g, cx, iy + 1, INTER_H - 2, INTER_H - 2, Ui.argb(Ui.mix(0xD8DBE0, 0xFFFFFF, s), a(255)));

        // etat en toutes lettres, s'il reste la place a cote du libelle
        Disposition d = disposition(font, on, ix - 5 - x - 8);
        float ty = y + (h - 6) / 2f;
        if (d.etat() != null) {
            int ce = on ? Ui.mix(Ui.OK, 0xFFFFFF, 0.2f) : Ui.MUTED;
            Ui.spacedRight(g, font, d.etat(), Ui.SMALL, ix - 5, ty, Ui.argb(ce, a(255)), 0.6f);
        }
        int texte = Ui.argb(Ui.mix(Ui.mix(Ui.TEXT, Ui.MUTED, 0.25f), 0xFFFFFF, s), a(255));
        Ui.spaced(g, font, d.libelle(), Ui.SMALL, x + 8, ty, texte, 0.8f);
    }

    /** Ce qui tient dans la largeur : libelle (coupe au besoin) et etat en lettres (null s'il ne tient pas). */
    private record Disposition(String libelle, String etat) {
    }

    /** Calculee une fois par etat (la largeur du widget ne change pas : l'ecran le recree a chaque init). */
    private final Disposition[] dispositions = new Disposition[2];

    private Disposition disposition(Font font, boolean on, float place) {
        int k = on ? 1 : 0;
        if (dispositions[k] != null) return dispositions[k];
        String texteEtat = on ? oui : non;
        float le = Ui.spacedWidth(font, texteEtat, Ui.SMALL, 0.6f), ll = Ui.spacedWidth(font, libelle, Ui.SMALL, 0.8f);
        Disposition d;
        if (ll + 8 + le <= place) d = new Disposition(libelle, texteEtat);
        else d = new Disposition(couper(font, libelle, place), null);
        return dispositions[k] = d;
    }

    /** Libelle coupe avec « … » pour tenir dans {@code max} (mesure en capitales espacees). */
    private static String couper(Font font, String s, float max) {
        if (Ui.spacedWidth(font, s, Ui.SMALL, 0.8f) <= max) return s;
        String t = s;
        while (!t.isEmpty() && Ui.spacedWidth(font, t.trim() + "…", Ui.SMALL, 0.8f) > max) {
            t = t.substring(0, t.offsetByCodePoints(t.length(), -1));
        }
        return t.trim() + "…";
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.literal(libelle + " : " + (actif() ? oui : non)));
        if (active) {
            out.add(NarratedElementType.USAGE, isFocused() ? Component.translatable("narration.button.usage.focused")
                    : Component.translatable("narration.button.usage.hovered"));
        }
    }
}
