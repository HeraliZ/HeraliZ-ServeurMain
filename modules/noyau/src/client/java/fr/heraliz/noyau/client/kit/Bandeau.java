package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Le bandeau commun des messages à l'écran (18/11, demande du propriétaire : « un affichage toujours le même, bien propre,
 * un peu plus petit » ; maquette validée {@code maquettes/notifications/}, outil {@code modules/noyau/outils/apercu_notifications.py}) :
 * verre sombre, filet d'un pixel d'écran, trait de couleur d'une unité à gauche, titre en DM Sans gras 6 sur une ligne,
 * puis le texte en DM Sans 5,5 (4 lignes au plus), et au besoin une barre fine. Ni icône, ni capitales espacées.
 *
 * <p>La mise en page (textes mesurés, coupés, abrégés) se fait quand on pose le texte ({@link #poser}, {@link #ligne}) ;
 * le dessin n'alloue rien. Dessin en deux temps pour qu'une pile de bandeaux n'envoie qu'un lot de formes puis un lot de
 * textes : {@link #formes} puis {@link #textes} (au tampon, sans le vider), ou {@link #dessiner} pour un bandeau seul.
 * Géométrie de la maquette, en unités d'interface : titre seul 10 de haut ; titre et texte 17, puis 6,5 par ligne.
 */
public final class Bandeau {
    /** Couleurs des textes : texte, atténué, très atténué. */
    public static final int TEXTE = 0xECEEF2, ATTENUE = 0xA9AFBA, FAIBLE = 0x6E7480;
    /** Couleurs du trait : réussite, hérals, information, attention, danger, neutre. */
    public static final int OK = 0x5FD08A, OR = 0xE8C36A, INFO = 0x6FA8FF, ALERTE = 0xE8B34A, DANGER = 0xE3201B, NEUTRE = 0x8F95A0;
    /** Marge intérieure et trait de couleur. */
    public static final float MARGE = 4f, TRAIT = 1f;
    /** Largeur d'un texte au plus, par défaut (au-delà : coupé en lignes ; titre abrégé par « … »). */
    public static final float TEXTE_MAX = 160f;
    public static final int LIGNES_MAX = 4;
    private static final int OPACITE_VERRE = 205, OPACITE_BORD = 20, OPACITE_PISTE = 30;

    private final TexteHud titre = new TexteHud(PoliceHud.BANDEAU_TITRE);
    private final TexteHud[] lignes = new TexteHud[LIGNES_MAX];
    private final int[] couleurs = new int[LIGNES_MAX];
    private int n;
    private int accent = NEUTRE;
    private float barre = -1f;
    private int couleurBarre = OK;
    private float max = TEXTE_MAX;

    public Bandeau() {
        for (int i = 0; i < LIGNES_MAX; i++) lignes[i] = new TexteHud(PoliceHud.BANDEAU_TEXTE);
    }

    // ------------------------------------------------------------------ mise en page
    /** Largeur d'un texte au plus (titre et lignes), avant de poser les textes. */
    public Bandeau largeurMax(float largeur) {
        max = Math.max(40f, largeur);
        return this;
    }

    /** Titre (abrégé s'il est trop long) et texte coupé en lignes atténuées ; trait de couleur {@code accent} ; pas de barre. */
    public Bandeau poser(String titre, @Nullable String texte, int accent) {
        titre(titre);
        this.accent = accent;
        barre = -1f;
        n = 0;
        if (texte != null && !texte.isBlank()) {
            List<FormattedText> l = splitter().splitLines(texte.trim(), (int) max, style(PoliceHud.BANDEAU_TEXTE));
            for (int i = 0; i < l.size() && n < LIGNES_MAX; i++) {
                String s = l.get(i).getString().trim();
                // plus de lignes que la place : la dernière finit par « … »
                if (n == LIGNES_MAX - 1 && i < l.size() - 1) s = abreger(s + " …", PoliceHud.BANDEAU_TEXTE, max);
                lignes[n].texte(s);
                couleurs[n++] = ATTENUE;
            }
        }
        return this;
    }

    /** Change le titre seul (abrégé s'il est trop long). */
    public Bandeau titre(String s) {
        titre.texte(abreger(s == null ? "" : s, PoliceHud.BANDEAU_TITRE, max));
        return this;
    }

    /** Ligne {@code i} du texte (0 à 3), une seule ligne abrégée, couleur {@code rgb} ; les lignes d'après sont retirées. */
    public Bandeau ligne(int i, String s, int rgb) {
        int k = Math.max(0, Math.min(LIGNES_MAX - 1, i));
        lignes[k].texte(abreger(s == null ? "" : s, PoliceHud.BANDEAU_TEXTE, max));
        couleurs[k] = rgb;
        n = k + 1;
        return this;
    }

    /** Garde les {@code nombre} premières lignes du texte (0 : titre seul). */
    public Bandeau lignes(int nombre) {
        n = Math.max(0, Math.min(n, nombre));
        return this;
    }

    public Bandeau accent(int rgb) {
        accent = rgb;
        return this;
    }

    /** Barre fine sous le texte, remplie à {@code ratio} (0..1) ; ratio négatif : pas de barre. */
    public Bandeau barre(float ratio, int rgb) {
        barre = ratio;
        couleurBarre = rgb;
        return this;
    }

    public float largeur() {
        float l = titre.largeur();
        for (int i = 0; i < n; i++) l = Math.max(l, lignes[i].largeur());
        return l + TRAIT + 2 * MARGE;
    }

    public float hauteur() {
        float h = n == 0 ? 10f : 17f + (n - 1) * 6.5f;
        return barre >= 0f ? h + 3.5f : h;
    }

    private float yBarre(float y) {
        return n == 0 ? y + 9f : y + 12f + (n - 1) * 6.5f + 4f;
    }

    // ------------------------------------------------------------------ dessin
    /** Fond, filet, trait et barre, au tampon (sans le vider). */
    public void formes(GuiGraphics g, float x, float y, float w, float opacite) {
        if (opacite <= 0.01f) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        float px = Formes.pixel(), h = hauteur();
        Formes.rect(vc, m, x, y, x + w, y + h, Palette.argb(Palette.VERRE, Math.round(OPACITE_VERRE * opacite)));
        Formes.bord(vc, m, x, y, w, h, px, Palette.argb(0xFFFFFF, Math.round(OPACITE_BORD * opacite)));
        Formes.rect(vc, m, x, y, x + TRAIT, y + h, Palette.argb(accent, opacite));
        if (barre >= 0f) {
            float by = yBarre(y), bx0 = x + TRAIT + MARGE, bx1 = x + w - MARGE;
            Formes.rect(vc, m, bx0, by, bx1, by + 0.75f, Palette.argb(0xFFFFFF, Math.round(OPACITE_PISTE * opacite)));
            Formes.rect(vc, m, bx0, by, bx0 + (bx1 - bx0) * Math.min(1f, barre), by + 0.75f, Palette.argb(couleurBarre, opacite));
        }
    }

    /** Titre et lignes, au tampon (sans le vider). */
    public void textes(GuiGraphics g, float x, float y, float opacite) {
        if (opacite <= 0.01f) return;
        float xi = x + TRAIT + MARGE;
        titre.ajouter(g, xi, y + (n == 0 ? 5f : 5.5f), Palette.argb(TEXTE, opacite), TexteHud.GAUCHE, true);
        for (int i = 0; i < n; i++) {
            lignes[i].ajouter(g, xi, y + 12f + i * 6.5f, Palette.argb(couleurs[i], opacite), TexteHud.GAUCHE, true);
        }
    }

    /** Un bandeau seul : formes, puis textes. */
    public void dessiner(GuiGraphics g, float x, float y, float w, float opacite) {
        formes(g, x, y, w, opacite);
        g.flush();
        textes(g, x, y, opacite);
        g.flush();
    }

    // ------------------------------------------------------------------ textes
    private static StringSplitter splitter() {
        return Minecraft.getInstance().font.getSplitter();
    }

    private static Style style(PoliceHud police) {
        return Style.EMPTY.withFont(police.id);
    }

    /** Le texte s'il tient dans {@code max}, sinon son début suivi de « … ». */
    static String abreger(String s, PoliceHud police, float max) {
        StringSplitter sp = splitter();
        Style st = style(police);
        if (sp.stringWidth(FormattedText.of(s, st)) <= max) return s;
        float points = sp.stringWidth(FormattedText.of("…", st));
        return sp.plainHeadByWidth(s, (int) Math.max(0, max - points), st).stripTrailing() + "…";
    }
}
