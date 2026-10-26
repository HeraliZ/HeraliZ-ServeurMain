package fr.heraliz.interfaces.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/**
 * Reglage a plusieurs crans dessine comme {@link Bascule} (meme fond, meme contour au survol, meme place) : libelle en
 * petites capitales a gauche, a droite le cran en toutes lettres et des barres de niveau qui montent (1, 2 ou 3 barres
 * pleines, comme un signal ; la barre pleine glisse en douceur). Un clic passe au cran suivant (apres le dernier : le
 * premier). Le cran est relu a chaque image ({@code etat}). Trop etroit : le nom du cran disparait, les barres restent.
 * {@link #opacite} / {@link #decalageY} : animation d'ouverture de l'ecran.
 */
public class Choix extends AbstractButton {
    private static final int BARRE_L = 3, BARRE_ECART = 2, BARRE_H0 = 4, BARRE_PAS = 2;
    private final String libelle;
    private final String[] noms;
    private final IntSupplier etat;
    private final Runnable action;
    private final int accent;
    private float survol;
    private final float[] plein;
    private boolean pret;
    private final Anim.Horloge horloge = new Anim.Horloge();
    public float opacite = 1f, decalageY = 0f;

    /** {@code noms} : nom de chaque cran ; {@code etat} : cran courant (0 a noms.length - 1) ; {@code action} : cran suivant. */
    public Choix(int x, int y, int w, int h, Component libelle, String[] noms, int accent, IntSupplier etat, Runnable action) {
        super(x, y, w, h, libelle);
        this.libelle = libelle.getString();
        this.noms = noms.clone();
        this.accent = accent & 0xFFFFFF;
        this.etat = etat;
        this.action = action;
        this.plein = new float[noms.length];
        this.dispositions = new Disposition[noms.length];
    }

    /** Cran courant, borne aux crans connus. */
    public int cran() {
        int c = etat.getAsInt();
        return c < 0 ? 0 : Math.min(c, noms.length - 1);
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
        int c = cran();
        boolean hover = active && isHoveredOrFocused();
        float dt = horloge.dt();
        survol = Anim.vers(survol, hover ? 1f : 0f, dt, 16f);
        for (int i = 0; i < plein.length; i++) {
            float cible = i <= c ? 1f : 0f;
            plein[i] = pret ? Anim.vers(plein[i], cible, dt, 18f) : cible;
        }
        pret = true;
        float s = survol;
        int x = getX(), w = getWidth(), h = getHeight();
        int y = Math.round(getY() + decalageY);
        Font font = Minecraft.getInstance().font;
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, a((int) (5 + 10 * s))));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(Ui.mix(0xFFFFFF, accent, s), a((int) (24 + 80 * s))));

        // barres de niveau a droite, posees sur une meme ligne de base
        int n = plein.length;
        int largeur = n * BARRE_L + (n - 1) * BARRE_ECART;
        int hMax = BARRE_H0 + (n - 1) * BARRE_PAS;
        int bx = x + w - 8 - largeur, base = y + (h + hMax) / 2;
        for (int i = 0; i < n; i++) {
            int bh = BARRE_H0 + i * BARRE_PAS;
            int couleur = Ui.mix(0x4A4F59, Ui.OK, plein[i]);
            g.fill(bx + i * (BARRE_L + BARRE_ECART), base - bh, bx + i * (BARRE_L + BARRE_ECART) + BARRE_L, base,
                    Ui.argb(couleur, a((int) (110 + 120 * plein[i]))));
        }

        // nom du cran en toutes lettres, s'il reste la place a cote du libelle
        Disposition d = disposition(font, c, bx - 5 - x - 8);
        float ty = y + (h - 6) / 2f;
        if (d.etat() != null) {
            Ui.spacedRight(g, font, d.etat(), Ui.SMALL, bx - 5, ty, Ui.argb(Ui.mix(Ui.OK, 0xFFFFFF, 0.2f), a(255)), 0.6f);
        }
        int texte = Ui.argb(Ui.mix(Ui.mix(Ui.TEXT, Ui.MUTED, 0.25f), 0xFFFFFF, s), a(255));
        Ui.spaced(g, font, d.libelle(), Ui.SMALL, x + 8, ty, texte, 0.8f);
    }

    /** Ce qui tient dans la largeur : libelle (coupe au besoin) et nom du cran (null s'il ne tient pas). */
    private record Disposition(String libelle, String etat) {
    }

    /** Calculee une fois par cran (la largeur du widget ne change pas : l'ecran le recree a chaque init). */
    private final Disposition[] dispositions;

    private Disposition disposition(Font font, int c, float place) {
        if (dispositions[c] != null) return dispositions[c];
        String nom = noms[c];
        float le = Ui.spacedWidth(font, nom, Ui.SMALL, 0.6f), ll = Ui.spacedWidth(font, libelle, Ui.SMALL, 0.8f);
        Disposition d;
        if (ll + 8 + le <= place) d = new Disposition(libelle, nom);
        else d = new Disposition(couper(font, libelle, place), null);
        return dispositions[c] = d;
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
        out.add(NarratedElementType.TITLE, Component.literal(libelle + " : " + noms[cran()]));
        if (active) {
            out.add(NarratedElementType.USAGE, isFocused() ? Component.translatable("narration.button.usage.focused")
                    : Component.translatable("narration.button.usage.hovered"));
        }
    }
}
