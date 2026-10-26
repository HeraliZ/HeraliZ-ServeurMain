package fr.heraliz.clashwar.tournoi.client;

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
import fr.heraliz.clashwar.HeralizClashWar;
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
 * Textures des murs-ecrans du ClashWar ({@value #TL} x {@value #TH} px, canevas 1024 x 512 a l'echelle 2) : le tableau du
 * tournoi ({@link PeintreTableau}) et, depuis le 21/10, le classement des clans ({@link PeintreClassement}, panneau du
 * spawn). Chacune est peinte hors ecran (cible de rendu) <b>seulement</b> quand ses donnees changent et que quelqu'un la
 * regarde (mur visible ou ecran ouvert dans les 3 dernieres secondes), au debut de l'image. Le mur-ecran et l'ecran
 * {@code /clashwar tableau} n'affichent ensuite qu'un quadrilatere texture : rendu net (mipmaps), aucune allocation par
 * image. Fil de rendu.
 */
public final class TextureTableau {
    private TextureTableau() {
    }

    public static final int TL = 2048, TH = 1024;
    public static final ResourceLocation ID = HeralizClashWar.id("dynamic/cw_tableau");

    /** Peintre d'une surface : canevas 1024 x 512 deja mis a l'echelle, pixels physiques par unite, joueur local. */
    interface Peintre {
        void peindre(GuiGraphics g, float pxParUnite, @javax.annotation.Nullable UUID moi);
    }

    /** Une texture de mur : cible de rendu, version des donnees peintes, derniere demande. */
    static final class Surface {
        private final ResourceLocation id;
        private final java.util.function.IntSupplier version;
        private final Peintre peintre;
        private TextureTarget cible;
        private int versionDessinee = Integer.MIN_VALUE;
        private long demandeMs;
        private UUID moiDessine;
        private boolean prete;

        Surface(ResourceLocation id, java.util.function.IntSupplier version, Peintre peintre) {
            this.id = id;
            this.version = version;
            this.peintre = peintre;
        }

        /** Signale qu'on va afficher la texture (a appeler a chaque image d'affichage) ; rend son identifiant. */
        ResourceLocation demander() {
            demandeMs = Util.getMillis();
            return id;
        }

        boolean prete() {
            return prete;
        }

        void invalider() {
            versionDessinee = Integer.MIN_VALUE;
        }

        void vider() {
            invalider();
            prete = false;
        }

        void avantImage(Minecraft mc) {
            if (mc.level == null || Util.getMillis() - demandeMs > 3000) return;
            UUID moi = mc.player != null ? mc.player.getUUID() : null;
            int v = version.getAsInt();
            if (versionDessinee == v && java.util.Objects.equals(moi, moiDessine)) return;
            try {
                dessiner(mc, moi);
            } catch (RuntimeException ex) {
                HeralizClashWar.LOG.error("[clashwar] dessin du mur {} en erreur", id, ex);
                mc.getMainRenderTarget().bindWrite(true);
            }
            versionDessinee = v;
            moiDessine = moi;
        }

        private void dessiner(Minecraft mc, UUID moi) {
            if (cible == null) {
                cible = new TextureTarget(TL, TH, true, Minecraft.ON_OSX);
                cible.setFilterMode(GL11.GL_LINEAR);
                mc.getTextureManager().register(id, new Cible(cible.getColorTextureId()));
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
            peintre.peindre(g, echelle, moi);
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
        }
    }

    /** Tableau du tournoi. */
    static final Surface TABLEAU = new Surface(ID, CwClient::version, (g, px, moi) -> PeintreTableau.peindre(g, CwClient.vue(), px, moi));
    /** Classement des clans (panneau du spawn). */
    static final Surface CLASSEMENT = new Surface(HeralizClashWar.id("dynamic/cw_classement"), CwClient::versionClassement,
            (g, px, moi) -> PeintreClassement.peindre(g, CwClient.classement()));

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

    /** Signale qu'on va afficher le tableau (a appeler a chaque image d'affichage) ; rend son identifiant. */
    public static ResourceLocation demander() {
        return TABLEAU.demander();
    }

    /** Vrai des que le tableau a ete peint une fois. */
    public static boolean prete() {
        return TABLEAU.prete();
    }

    /** Force un nouveau dessin (rechargement des ressources, changement de langue). */
    static void invalider() {
        TABLEAU.invalider();
        CLASSEMENT.invalider();
    }

    /** Debut d'image : repeint ce qui a change et qui est regarde. */
    static void avantImage() {
        Minecraft mc = Minecraft.getInstance();
        TABLEAU.avantImage(mc);
        CLASSEMENT.avantImage(mc);
    }

    /** Dessine le tableau dans un rectangle d'interface (haut de l'image en haut). */
    public static void dessinerGui(GuiGraphics g, float x0, float y0, float x1, float y1, float alpha) {
        demander();
        if (!prete()) return;
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

    /** Deconnexion : les cibles sont gardees (meme taille), le contenu sera repeint. */
    static void vider() {
        TABLEAU.vider();
        CLASSEMENT.vider();
    }
}
