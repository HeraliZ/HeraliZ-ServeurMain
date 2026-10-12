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
 * mods/heralisz-utils/outils/icones_carte.py), teintées au dessin. Tout un lot de quads en un appel entre
 * {@link #debut} et {@link #fin}. L'ordre des noms est celui de l'outil.
 */
final class Icones {
    private Icones() {
    }

    static final ResourceLocation TEXTURE = HeralizCarte.id("textures/gui/icones.png");
    private static final int CASE = 64, COLONNES = 8, LARGEUR = 512, HAUTEUR = 192;
    static final String[] NOMS = {"cadre_fond", "cadre_bord", "sortie", "avion", "danger", "eclair", "drapeau", "usine",
            "ancre", "croix", "train", "etoile", "losange", "fleche", "disque", "anneau",
            "halo", "nord", "maison", "cible"};
    static final int CADRE_FOND = 0, CADRE_BORD = 1, SORTIE = 2, DRAPEAU = 6, LOSANGE = 12, FLECHE = 13, DISQUE = 14,
            ANNEAU = 15, HALO = 16, NORD = 17, CIBLE = 19;

    private static BufferBuilder buf;
    private static Matrix4f m;
    private static boolean ouvert;

    static int index(String nom) {
        for (int i = 0; i < NOMS.length; i++) if (NOMS[i].equals(nom)) return i;
        return -1;
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
        if (!ouvert || i < 0 || (argb >>> 24) == 0) return;
        // un demi-texel de marge : pas de bavure de la case voisine au filtrage
        float u0 = ((i % COLONNES) * CASE + 0.5f) / LARGEUR, v0 = ((i / COLONNES) * CASE + 0.5f) / HAUTEUR;
        float u1 = u0 + (CASE - 1f) / LARGEUR, v1 = v0 + (CASE - 1f) / HAUTEUR;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[][] coins = {{-r, -r, u0, v0}, {-r, r, u0, v1}, {r, r, u1, v1}, {r, -r, u1, v0}};
        int a = argb >>> 24, rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
        for (float[] k : coins) {
            float x = cx + k[0] * c - k[1] * s, y = cy + k[0] * s + k[1] * c;
            buf.vertex(m, x, y, 0).color(rr, gg, bb, a).uv(k[2], k[3]).endVertex();
        }
    }

    /** Pictogramme dans son cadre arrondi (fond sombre, bord de la couleur), comme sur la maquette. */
    static void encadree(int i, float cx, float cy, float r, int couleur, float alpha) {
        icone(CADRE_FOND, cx, cy, r, Formes.argb(0x0D0F14, 0.88f * alpha));
        icone(CADRE_BORD, cx, cy, r, Formes.argb(couleur, alpha));
        icone(i, cx, cy, r, Formes.argb(couleur, alpha));
    }
}
