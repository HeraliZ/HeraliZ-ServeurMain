package fr.heralisz.tags.client.gui.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.client.render.AtlasTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

/**
 * Charte des interfaces du mod (reprise des caisses, D-030) : police DM Sans (assets/heralisz_tags/font, licence OFL),
 * couleurs sobres, capitales espacees, filets fins, verre translucide, infobulles maison. Jamais de cadre vanilla.
 */
public final class Ui {
    private Ui() {
    }

    /** DM Sans Medium (texte courant), Bold (titres, boutons), Medium petit corps (legendes). */
    public static final ResourceLocation FONT = HeraliszTags.id("ui"), BOLD = HeraliszTags.id("ui_bold"), SMALL = HeraliszTags.id("ui_small");

    public static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, DANGER = 0xFF6B6B, OK = 0x5FD08A;
    /** Couleur d'accent neutre du mod. */
    public static final int ACCENT = 0x6FA8FF;
    /** Couleur d'accent courante (modifiable, ex. couleur du clan). */
    public static int accent = ACCENT;
    /** Fond des panneaux de verre. */
    public static final int VERRE = 0x0D0F14;
    /** PNG de l'atlas des tags (ecrit par l'agent art) ; la texture affichee est AtlasTexture (mipmaps). */
    public static final ResourceLocation ATLAS = HeraliszTags.id("textures/tags/atlas.png");
    public static final int ATLAS_PX = 2048, CASE_PX = 256;

    private static Boolean atlasPresent;

    // ------------------------------------------------------------------ texte
    public static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    public static MutableComponent tr(ResourceLocation font, String key, Object... args) {
        return Component.translatable(key, args).withStyle(st -> st.withFont(font));
    }

    /** Le composant dans une police (ses morceaux sans police propre en heritent). */
    public static MutableComponent in(Component c, ResourceLocation font) {
        return Component.empty().withStyle(st -> st.withFont(font)).append(c);
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static boolean french() {
        return fr.heralisz.tags.util.Langue.francais();
    }

    // ------------------------------------------------------------------ capitales espacees
    public static float spacedWidth(Font font, String s, ResourceLocation f, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            w += font.width(lit(ch, f)) + spacing;
        }
        return Math.max(0, w - spacing);
    }

    /** Texte en capitales espacees ; x = bord gauche. */
    public static void spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, int argb, float spacing) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            MutableComponent c = lit(ch, f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + spacing;
        }
    }

    public static void spacedCentered(GuiGraphics g, Font font, String s, ResourceLocation f, float cx, float y, int argb, float spacing) {
        float w = spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
        spaced(g, font, s, f, cx - w / 2f, y, argb, spacing);
    }

    /** Texte en capitales espacees aligne a droite (x = bord droit). */
    public static void spacedRight(GuiGraphics g, Font font, String s, ResourceLocation f, float xRight, float y, int argb, float spacing) {
        float w = spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
        spaced(g, font, s, f, xRight - w, y, argb, spacing);
    }

    public static void centered(GuiGraphics g, Font font, Component c, float cx, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx - font.width(c) / 2f, y, 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    public static void right(GuiGraphics g, Font font, Component c, float xRight, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(xRight - font.width(c), y, 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ formes
    /** Rectangle aux coins adoucis (un pixel retire a chaque coin). */
    public static void soft(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y, x + w - 1, y + 1, argb);
        g.fill(x, y + 1, x + w, y + h - 1, argb);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, argb);
    }

    /** Contour d'un pixel aux coins adoucis, juste a l'exterieur du rectangle. */
    public static void softOutline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y - 1, x + w - 1, y, argb);
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, argb);
        g.fill(x - 1, y + 1, x, y + h - 1, argb);
        g.fill(x + w, y + 1, x + w + 1, y + h - 1, argb);
    }

    /** Panneau « verre » : fond 0x0D0F14 a ~78 %, contour 1 px blanc a 10 %, lisere haut blanc a 16 %, coins adoucis. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h) {
        soft(g, x, y, w, h, argb(VERRE, 200));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, 26));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, 40));
    }

    /** Degrade diagonal plein ecran (haut-gauche → bas-droite) : le monde reste lisible derriere. */
    public static void fondDegrade(GuiGraphics g, int x0, int y0, int x1, int y1, int argbHautGauche, int argbBasDroite) {
        int milieu = mixArgb(argbHautGauche, argbBasDroite, 0.5f);
        quad(g, x0, y0, x1, y1, argbHautGauche, milieu, argbBasDroite, milieu);
    }

    /** Quadrilatere a couleurs par sommet (haut-gauche, haut-droite, bas-droite, bas-gauche). */
    public static void quad(GuiGraphics g, int x0, int y0, int x1, int y1, int cTL, int cTR, int cBR, int cBL) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder b = tess.getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        sommet(b, m, x0, y0, cTL);
        sommet(b, m, x0, y1, cBL);
        sommet(b, m, x1, y1, cBR);
        sommet(b, m, x1, y0, cTR);
        tess.end();
        RenderSystem.disableBlend();
    }

    private static void sommet(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).endVertex();
    }

    public static int mixArgb(int a, int b, float t) {
        int alpha = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        return (alpha << 24) | mix(a, b, t);
    }

    /** Filet horizontal d'un pixel qui s'efface vers les deux bouts (ou vers un seul). */
    public static void hairline(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha, boolean fadeLeft, boolean fadeRight) {
        int w = x1 - x0;
        if (w <= 0) return;
        int steps = Math.max(1, w / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            float a = 1;
            if (fadeLeft && fadeRight) a = 1 - Math.abs(t * 2 - 1);
            else if (fadeLeft) a = t;
            else if (fadeRight) a = 1 - t;
            a = a * a * (3 - 2 * a);
            int xa = x0 + i * w / steps, xb = x0 + (i + 1) * w / steps;
            g.fill(xa, y, xb, y + 1, argb(rgb, (int) (alpha * a)));
        }
    }

    /** Barre de progression fine (h px) : piste blanche 8 %, remplissage accent. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float t, int rgb) {
        g.fill(x, y, x + w, y + h, argb(0xFFFFFF, 20));
        int fw = Math.round(w * Math.max(0, Math.min(1, t)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, argb(rgb, 230));
    }

    // ------------------------------------------------------------------ formes rondes (roue)
    /**
     * Secteur annulaire (roue) : de l'angle a0 a a1 (radians, 0 = haut, sens horaire), rayons rIn..rOut, couleur
     * interieure et exterieure (degrade radial). Dessine en bandes (QUADS) avec le shader position_color.
     */
    public static void secteur(GuiGraphics g, float cx, float cy, float rIn, float rOut, float a0, float a1, int argbIn, int argbOut) {
        int seg = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 24)));
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder b = tess.getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < seg; i++) {
            float t0 = a0 + (a1 - a0) * i / seg, t1 = a0 + (a1 - a0) * (i + 1) / seg;
            float s0 = (float) Math.sin(t0), c0 = (float) Math.cos(t0), s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            sommet(b, m, cx + s0 * rIn, cy - c0 * rIn, argbIn);
            sommet(b, m, cx + s0 * rOut, cy - c0 * rOut, argbOut);
            sommet(b, m, cx + s1 * rOut, cy - c1 * rOut, argbOut);
            sommet(b, m, cx + s1 * rIn, cy - c1 * rIn, argbIn);
        }
        tess.end();
        RenderSystem.disableBlend();
    }

    /** Disque plein (centre + bord en degrade). */
    public static void disque(GuiGraphics g, float cx, float cy, float r, int argbCentre, int argbBord) {
        int seg = 48;
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder b = tess.getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            sommet(b, m, cx, cy, argbCentre);
            sommet(b, m, cx + (float) Math.sin(t1) * r, cy - (float) Math.cos(t1) * r, argbBord);
            sommet(b, m, cx + (float) Math.sin(t0) * r, cy - (float) Math.cos(t0) * r, argbBord);
        }
        tess.end();
        RenderSystem.disableBlend();
    }

    /** Case de l'atlas dessinee a des coordonnees flottantes (roue) ; alpha 0..1. */
    public static void caseAtlasF(GuiGraphics g, int cas, float x, float y, float taille, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float s = taille / 64f;
        g.pose().scale(s, s, 1);
        caseAtlas(g, cas, 0, 0, 64, alpha);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ atlas des tags
    /** Vrai si l'atlas des tags est present dans les ressources (teste une fois, oublie au rechargement). */
    public static boolean atlasPresent() {
        if (atlasPresent == null) {
            try {
                atlasPresent = Minecraft.getInstance().getResourceManager().getResource(ATLAS).isPresent();
            } catch (Exception e) {
                atlasPresent = false;
            }
            if (!atlasPresent) HeraliszTags.LOGGER.warn("[HeraliZ Tags] atlas absent ({}) : cases atténuées dans la collection", ATLAS);
        }
        return atlasPresent;
    }

    public static void atlasOublier() {
        atlasPresent = null;
    }

    /**
     * Dessine la case « cas » de l'atlas dans un carre de taille px ; alpha 0..1 (teinte blanche). Utilise la texture
     * mipmappee ({@link AtlasTexture}) : la case de 256 px reduite a 40 px reste lisse.
     */
    public static void caseAtlas(GuiGraphics g, int cas, int x, int y, int taille, float alpha) {
        int col = cas % 8, lig = cas / 8;
        AtlasTexture.obtenir(Minecraft.getInstance());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        g.blit(AtlasTexture.LOC, x, y, taille, taille, col * CASE_PX, lig * CASE_PX, CASE_PX, CASE_PX, ATLAS_PX, ATLAS_PX);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ infobulle
    /** Infobulle sobre : fond sombre, lisere a la couleur d'accent, premiere ligne en titre. */
    public static void tooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY, int accent) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (Component l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() == 1 ? 9 : lines.size() * 10 + 1;
        int x = mouseX + 12, y = mouseY - 10;
        if (x + w + 8 > g.guiWidth()) x = mouseX - w - 14;
        y = Math.max(6, Math.min(y, g.guiHeight() - h - 6));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x - 6, y - 5, x + w + 6, y + h + 3, 0xF20B0C0F);
        g.fill(x - 6, y - 5, x + w + 6, y - 4, argb(accent, 200));
        g.fill(x - 6, y - 4, x - 5, y + h + 3, argb(0xFFFFFF, 18));
        g.fill(x + w + 5, y - 4, x + w + 6, y + h + 3, argb(0xFFFFFF, 18));
        g.fill(x - 6, y + h + 2, x + w + 6, y + h + 3, argb(0xFFFFFF, 18));
        int ly = y;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, ly, 0xFFFFFFFF, false);
            ly += i == 0 ? 12 : 10;
        }
        g.pose().popPose();
    }
}
