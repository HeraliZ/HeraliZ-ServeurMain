package fr.heraliz.interfaces.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Ligne de menu (menu Echap, listes d'actions) : icone a gauche, libelle en capitales espacees, a droite une indication
 * attenuee (« ÉCHAP », adresse du lien...) ou une pastille (« BIENTÔT »), puis une petite icone (chevron, lien externe).
 * Survol anime : fond qui s'eclaircit, filet d'accent qui pousse a gauche, libelle et chevron qui glissent d'un pixel.
 * Styles : normal, principal (teinte d'accent, pour « Reprendre ») et danger (rouge, pour « Quitter »).
 * {@link #opacite} et {@link #decalageY} servent a l'animation d'ouverture de l'ecran (posees a chaque image).
 */
public class MenuLigne extends AbstractButton {
    public enum Style {NORMAL, PRINCIPAL, DANGER}

    private final Runnable action;
    private final String libelle;
    private final int icone;
    private Style style = Style.NORMAL;
    private int accent;
    @Nullable
    private String indice;
    private boolean indiceCapitales = true;
    private int iconeDroite = -1;
    @Nullable
    private String pastille;
    /** Sans cadre au repos (le libelle pose sur le fond) : seul le survol dessine le fond et le contour. */
    private boolean sobre;
    private float survol;
    private final Anim.Horloge horloge = new Anim.Horloge();
    /** Opacite d'ensemble 0..1 et decalage vertical (unites) de l'animation d'ouverture. */
    public float opacite = 1f, decalageY = 0f;

    public MenuLigne(int x, int y, int w, int h, int icone, Component libelle, int accent, Runnable action) {
        super(x, y, w, h, libelle);
        this.icone = icone;
        this.libelle = libelle.getString();
        this.accent = accent & 0xFFFFFF;
        this.action = action;
    }

    public MenuLigne style(Style s) {
        this.style = s;
        if (s == Style.DANGER) accent = Ui.DANGER_ROUGE;
        return this;
    }

    /** Texte attenue aligne a droite (raccourci, adresse...). */
    public MenuLigne indice(@Nullable String texte) {
        this.indice = texte == null || texte.isBlank() ? null : texte;
        return this;
    }

    /** Texte attenue a droite, tel quel (adresse web : la casse compte). */
    public MenuLigne indiceTexte(@Nullable String texte) {
        indice(texte);
        this.indiceCapitales = false;
        return this;
    }

    /** Petite icone tout a droite (Icons.CHEVRON, Icons.EXTERNE...). */
    public MenuLigne iconeDroite(int index) {
        this.iconeDroite = index;
        return this;
    }

    /** Pastille a droite (remplace l'indication), typiquement « BIENTÔT » sur une ligne inactive. */
    public MenuLigne pastille(@Nullable String texte) {
        this.pastille = texte;
        return this;
    }

    public MenuLigne actif(boolean a) {
        this.active = a;
        return this;
    }

    /** Ligne sans cadre au repos (menu Echap) : fond et contour n'apparaissent qu'au survol. */
    public MenuLigne sobre() {
        this.sobre = true;
        return this;
    }

    public String libelle() {
        return libelle;
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
        if (opacite <= 0.01f) return;
        boolean hover = active && isHoveredOrFocused();
        survol = Anim.vers(survol, hover ? 1f : 0f, horloge.dt(), 16f);
        float s = survol;
        int x = getX(), w = getWidth(), h = getHeight();
        int y = Math.round(getY() + decalageY);
        Font font = Minecraft.getInstance().font;

        // fond et contour
        int fond, contour;
        int repos = sobre ? 0 : 1;
        if (!active) {
            fond = Ui.argb(0xFFFFFF, a(4 * repos));
            contour = Ui.argb(0xFFFFFF, a(14 * repos));
        } else if (style == Style.PRINCIPAL) {
            fond = Ui.argb(accent, a((int) (38 + 34 * s)));
            contour = Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.25f * s), a((int) (150 + 90 * s)));
        } else if (style == Style.DANGER) {
            fond = Ui.argb(accent, a((int) (12 * repos + 26 * s)));
            contour = Ui.argb(accent, a((int) (70 * repos + 130 * s)));
        } else {
            fond = Ui.argb(0xFFFFFF, a((int) (7 * repos + 11 * s)));
            contour = Ui.argb(Ui.mix(0xFFFFFF, accent, s), a((int) (30 * repos + 80 * s)));
        }
        Ui.soft(g, x, y, w, h, fond);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, contour);
        // filet d'accent a gauche (toujours present sur la ligne principale)
        float barre = style == Style.PRINCIPAL ? 1f : s;
        int bh = Math.round((h - 6) * barre);
        if (bh > 0 && active) g.fill(x + 1, y + (h - bh) / 2, x + 3, y + (h - bh) / 2 + bh, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.2f), a(255)));

        // icone + libelle
        int couleurTexte, couleurIcone;
        if (!active) {
            couleurTexte = Ui.argb(0xFFFFFF, a(80));
            couleurIcone = Ui.argb(0xFFFFFF, a(60));
        } else if (style == Style.DANGER) {
            couleurTexte = Ui.argb(Ui.mix(Ui.mix(accent, 0xFFFFFF, 0.45f), 0xFFFFFF, s * 0.6f), a(255));
            couleurIcone = Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.25f + 0.5f * s), a(255));
        } else if (style == Style.PRINCIPAL) {
            couleurTexte = Ui.argb(0xFFFFFF, a(255));
            couleurIcone = Ui.argb(0xFFFFFF, a(255));
        } else {
            couleurTexte = Ui.argb(Ui.mix(Ui.TEXT, 0xFFFFFF, s), a((int) (230 + 25 * s)));
            couleurIcone = Ui.argb(Ui.mix(Ui.MUTED, Ui.mix(accent, 0xFFFFFF, 0.35f), s), a(255));
        }
        int taille = 10;
        int ix = x + 11;
        Icons.draw(g, icone, ix, y + (h - taille) / 2, taille, couleurIcone & 0xFFFFFF, couleurIcone >>> 24);
        float tx = ix + taille + 9 + 1.5f * s;
        Ui.spaced(g, font, libelle, Ui.BOLD, tx, y + (h - 8) / 2f, couleurTexte, 1.0f);

        // droite : pastille ou indication + icone
        int droite = x + w - 10;
        if (iconeDroite >= 0) {
            int t = 8;
            float glisse = iconeDroite == Icons.CHEVRON ? 1.5f * s : 0f;
            int ci = active ? Ui.argb(Ui.mix(Ui.FAINT, Ui.TEXT, s), a(255)) : Ui.argb(0xFFFFFF, a(40));
            Icons.draw(g, iconeDroite, Math.round(droite - t + glisse), y + (h - t) / 2, t, ci & 0xFFFFFF, ci >>> 24);
            droite -= t + 7;
        }
        if (pastille != null) {
            int pw = Ui.chipWidth(font, pastille);
            int couleur = Ui.AMBRE;
            Ui.soft(g, droite - pw, y + (h - 11) / 2, pw, 11, Ui.argb(couleur, a(30)));
            Ui.softOutline(g, droite - pw + 1, y + (h - 11) / 2 + 1, pw - 2, 9, Ui.argb(couleur, a(90)));
            Ui.spaced(g, font, pastille, Ui.SMALL, droite - pw + 5, y + (h - 11) / 2 + 2.5f, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.3f), a(255)), 0.6f);
        } else if (indice != null) {
            int ci = Ui.argb(Ui.mix(Ui.FAINT, Ui.MUTED, active ? 0.4f + 0.6f * s : 0f), a(255));
            if (indiceCapitales) Ui.spacedRight(g, font, indice, Ui.SMALL, droite, y + (h - 6) / 2f, ci, 0.6f);
            else Ui.right(g, font, Ui.lit(indice, Ui.SMALL), droite, y + (h - 7) / 2f, ci);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
