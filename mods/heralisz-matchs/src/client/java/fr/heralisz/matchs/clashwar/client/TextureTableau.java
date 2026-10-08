package fr.heralisz.matchs.clashwar.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.UUID;

/**
 * Texture du tableau du tournoi ({@value #TL} x {@value #TH} px, canevas {@link PeintreTableau} a l'echelle 2) : peinte
 * hors ecran (cible de rendu) <b>seulement</b> quand la vue change ({@link CwClient#version()}) et que quelqu'un la
 * regarde (mur visible ou ecran ouvert dans les 3 dernieres secondes), au debut de l'image. Le mur-ecran et l'ecran
 * {@code /clashwar tableau} n'affichent ensuite qu'un quadrilatere texture : rendu net (mipmaps), aucune allocation par
 * image. Fil de rendu.
 */
public final class TextureTableau {
    private TextureTableau() {
    }

    public static final int TL = 2048, TH = 1024;
    public static final ResourceLocation ID = HeraliszMatchs.id("dynamic/cw_tableau");

    private static TextureTarget cible;
    private static int versionDessinee = Integer.MIN_VALUE;
    private static long demandeMs;
    private static UUID moiDessine;
    private static boolean prete;

    /** Texture enregistree dont le numero GL est celui de la cible (jamais liberee par le gestionnaire). */
    private static final class Cible extends AbstractTexture {
        Cible(int id) {
            this.id = id;
        }

        @Override
        public void load(ResourceManager rm) {
        }

        @Override
        public void releaseId() {
            // la cible de rendu garde sa texture
        }
    }

    /** Signale qu'on va afficher la texture (a appeler a chaque image d'affichage) ; rend son identifiant. */
    public static ResourceLocation demander() {
        demandeMs = Util.getMillis();
        return ID;
    }

    /** Vrai des que la texture a ete peinte une fois. */
    public static boolean prete() {
        return prete;
    }

    /** Force un nouveau dessin (rechargement des ressources, changement de langue). */
    static void invalider() {
        versionDessinee = Integer.MIN_VALUE;
    }

    /** Debut d'image : repeint si la vue a change et que la texture est regardee. */
    static void avantImage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Util.getMillis() - demandeMs > 3000) return;
        UUID moi = mc.player != null ? mc.player.getUUID() : null;
        if (versionDessinee == CwClient.version() && java.util.Objects.equals(moi, moiDessine)) return;
        try {
            dessiner(mc, moi);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.error("[clashwar] dessin du tableau en erreur", ex);
            mc.getMainRenderTarget().bindWrite(true);
        }
        versionDessinee = CwClient.version();
        moiDessine = moi;
    }

    private static void dessiner(Minecraft mc, UUID moi) {
        long t0 = System.nanoTime();
        if (cible == null) {
            cible = new TextureTarget(TL, TH, true, Minecraft.ON_OSX);
            cible.setFilterMode(GL11.GL_LINEAR);
            mc.getTextureManager().register(ID, new Cible(cible.getColorTextureId()));
        }
        cible.setClearColor(0f, 0f, 0f, 0f);
        cible.clear(Minecraft.ON_OSX);
        cible.bindWrite(true);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting tri = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, TL, TH, 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack vue = RenderSystem.getModelViewStack();
        vue.pushPose();
        vue.setIdentity();
        vue.translate(0f, 0f, -11000f);
        RenderSystem.applyModelViewMatrix();
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        float echelle = TL / (float) PeintreTableau.L;
        g.pose().pushPose();
        g.pose().scale(echelle, TH / (float) PeintreTableau.H, 1f);
        PeintreTableau.peindre(g, CwClient.vue(), echelle, moi);
        g.pose().popPose();
        g.flush();
        // alpha opaque partout (les bannieres et les textes ecrivent un alpha partiel) ; profondeur videe pour que
        // le voile passe aussi sur les textes (dessines un peu en avant)
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(false, false, false, true);
        g.fill(0, 0, TL, TH, 0xFF000000);
        g.flush();
        RenderSystem.colorMask(true, true, true, true);
        vue.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, tri);
        // mipmaps : texte lisible et sans scintillement de loin
        GlStateManager._bindTexture(cible.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._bindTexture(0);
        mc.getMainRenderTarget().bindWrite(true);
        prete = true;
        HeraliszMatchs.LOG.debug("[clashwar] tableau repeint en {} us", (System.nanoTime() - t0) / 1000);
    }

    /** Dessine la texture dans un rectangle d'interface (haut de l'image en haut). */
    public static void dessinerGui(GuiGraphics g, float x0, float y0, float x1, float y1, float alpha) {
        demander();
        if (!prete) return;
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, ID);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // la cible de rendu est stockee de bas en haut : v = 1 en haut de l'image
        bb.vertex(m, x0, y0, 0f).uv(0f, 1f).endVertex();
        bb.vertex(m, x0, y1, 0f).uv(0f, 0f).endVertex();
        bb.vertex(m, x1, y1, 0f).uv(1f, 0f).endVertex();
        bb.vertex(m, x1, y0, 0f).uv(1f, 1f).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Deconnexion : la cible est gardee (meme taille), le contenu sera repeint. */
    static void vider() {
        invalider();
        prete = false;
    }
}
