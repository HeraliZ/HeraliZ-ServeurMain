package fr.heraliz.carte.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.carte.HeralizCarte;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Icônes de la carte : un atlas de formes blanches (textures/gui/icones.png, fait par
 * modules/monde/outils/icones_carte.py), teintées au dessin. Tout un lot de quads en un appel entre
 * {@link #debut} et {@link #fin}. L'ordre des noms est celui de l'outil.
 * <p>
 * L'atlas a trois niveaux de détail (cases de 128, 64 et 32 px, l'un sous l'autre) : chaque icône prend le plus petit
 * qui ne soit pas plus de ~1,2 fois plus petit que sa taille à l'écran, donc le filtrage linéaire ne réduit jamais plus
 * de 2 fois (pas de crénelage ni de scintillement, sans mipmaps). Le centre est calé sur les pixels de l'écran.
 */
final class Icones {
    private Icones() {
    }

    static final ResourceLocation TEXTURE = HeralizCarte.id("textures/gui/icones.png");
    private static final int COLONNES = 8, LIGNES = 3, LARGEUR = 1024, HAUTEUR = 672;
    /** Niveaux de détail : taille des cases et haut de chaque niveau dans l'atlas. */
    private static final int[] CASES = {32, 64, 128}, HAUTS = {576, 384, 0};
    static final String[] NOMS = {"pastille", "pastille_bord", "ombre", "sortie", "avion", "danger", "eclair", "drapeau",
            "usine", "ancre", "croix", "train", "etoile", "losange", "fleche", "disque",
            "anneau", "halo", "nord", "maison", "cible", "helico", "epave"};
    static final int PASTILLE = 0, PASTILLE_BORD = 1, OMBRE = 2, SORTIE = 3, DRAPEAU = 7, LOSANGE = 13, FLECHE = 14, DISQUE = 15,
            ANNEAU = 16, HALO = 17, NORD = 18, CIBLE = 20, HELICO = 21, EPAVE = 22;

    private static BufferBuilder buf;
    private static Matrix4f m;
    private static boolean ouvert;
    /** Pixels de l'écran par unité d'interface (échelle de l'interface), pour le niveau de détail et le calage. */
    private static float pixels = 3f;

    static int index(String nom) {
        for (int i = 0; i < NOMS.length; i++) if (NOMS[i].equals(nom)) return i;
        return -1;
    }

    /** Échelle de l'interface en cours (pixels de l'écran par unité). */
    static void echelle(float pixelsParUnite) {
        pixels = Math.max(1f, pixelsParUnite);
    }

    static void debut(GuiGraphics g) {
        g.flush();
        m = g.pose().last().pose();
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        ouvert = true;
    }

    static void fin() {
        if (!ouvert) return;
        ouvert = false;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableDepthTest();
    }

    /** Icône carrée centrée en (cx, cy), de demi-côté r. */
    static void icone(int i, float cx, float cy, float r, int argb) {
        tournee(i, cx, cy, r, 0f, argb);
    }

    /** Icône tournée d'un angle (radians, sens horaire à l'écran). */
    static void tournee(int i, float cx, float cy, float r, float angle, int argb) {
        if (!ouvert || i < 0 || r <= 0 || (argb >>> 24) == 0) return;
        // niveau de détail : la plus petite case assez grande pour la taille affichée
        float px = 2 * r * pixels;
        int n = CASES.length - 1;
        for (int l = 0; l < CASES.length; l++) {
            if (CASES[l] * 1.2f >= px) {
                n = l;
                break;
            }
        }
        int t = CASES[n];
        // centre calé sur les pixels de l'écran : même rendu d'une image à l'autre
        cx = Math.round(cx * pixels) / pixels;
        cy = Math.round(cy * pixels) / pixels;
        // un demi-texel de marge : pas de bavure de la case voisine au filtrage
        float u0 = ((i % COLONNES) * t + 0.5f) / LARGEUR, v0 = (HAUTS[n] + (i / COLONNES) * t + 0.5f) / HAUTEUR;
        float u1 = u0 + (t - 1f) / LARGEUR, v1 = v0 + (t - 1f) / HAUTEUR;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[][] coins = {{-r, -r, u0, v0}, {-r, r, u0, v1}, {r, r, u1, v1}, {r, -r, u1, v0}};
        int a = argb >>> 24, rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
        for (float[] k : coins) {
            float x = cx + k[0] * c - k[1] * s, y = cy + k[0] * s + k[1] * c;
            buf.vertex(m, x, y, 0).color(rr, gg, bb, a).uv(k[2], k[3]).endVertex();
        }
    }

    /**
     * Pictogramme sur sa petite pastille ronde de verre : ombre douce, fond sombre translucide, bord fin de la couleur,
     * pictogramme au trait fin. r : demi-côté (la pastille fait ~0,85 r de rayon).
     */
    static void pastille(int i, float cx, float cy, float r, int couleur, float alpha) {
        pastilleTournee(i, cx, cy, r, 0f, couleur, alpha);
    }

    /** {@link #pastille}, pictogramme tourné d'un angle (radians, sens horaire à l'écran) : l'hélicoptère suit son cap. */
    static void pastilleTournee(int i, float cx, float cy, float r, float angle, int couleur, float alpha) {
        if (alpha <= 0.01f) return;
        icone(OMBRE, cx, cy, r * 1.12f, Formes.argb(0x000000, 0.55f * alpha));
        icone(PASTILLE, cx, cy, r, Formes.argb(0x0D0F14, 0.80f * alpha));
        icone(PASTILLE_BORD, cx, cy, r, Formes.argb(couleur, 0.9f * alpha));
        tournee(i, cx, cy, r * 0.98f, angle, Formes.argb(couleur, alpha));
    }
}
