package fr.heraliz.ping.client.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Lot de dessin du HUD : tous les quads (formes, icones, lettres) d'une image sont ecrits dans UN tampon natif et
 * dessines en UN appel avec l'atlas ({@link Atlas}). Ordre de dessin = ordre d'ajout (peintre) : pas de tampon de
 * profondeur, aucune interference avec le reste de l'interface.
 * <p>Zero allocation par image : le tampon natif, le tableau de sommets (VAO + VBO) et l'entier de la texture sont crees
 * une fois ; l'envoi passe directement par OpenGL (le chemin du jeu, {@code BufferUploader}, cree a chaque dessin une
 * douzaine de chaines « Sampler0..11 » et plusieurs objets : pres d'1 Ko par appel). Fil de rendu uniquement.
 */
public final class Lot {
    private Lot() {
    }

    /** Octets par sommet (position 3 flottants, couleur 4 octets, texture 2 flottants : POSITION_COLOR_TEX). */
    private static final int SOMMET = 24;
    private static ByteBuffer octets = MemoryUtil.memAlloc(4096 * SOMMET);
    private static int vao, vbo;
    /** Shader du HUD (heraliz_ping:hud), ou celui du jeu s'il n'a pas pu etre charge. */
    @Nullable
    public static ShaderInstance shaderHud;
    private static int idTexture = -1;
    private static Integer idTextureObjet = -1;
    private static float m00, m01, m10, m11, m30, m31, m32;
    private static boolean ouvert;
    /** Quads du dernier lot (mesures). */
    public static int quads;

    private static ShaderInstance shader() {
        return shaderHud != null ? shaderHud : GameRenderer.getPositionColorTexShader();
    }

    public static void debut(Matrix4f pose) {
        m00 = pose.m00();
        m01 = pose.m01();
        m10 = pose.m10();
        m11 = pose.m11();
        m30 = pose.m30();
        m31 = pose.m31();
        m32 = pose.m32();
        octets.clear();
        ouvert = true;
        quads = 0;
    }

    public static void fin() {
        if (!ouvert) return;
        ouvert = false;
        if (quads == 0) return;
        ShaderInstance sh = shader();
        if (sh == null) return;
        int id = Minecraft.getInstance().getTextureManager().getTexture(Atlas.TEXTURE).getId();
        if (id != idTexture) {
            idTexture = id;
            idTextureObjet = id; // une seule mise en boite, au chargement de la texture
        }
        octets.flip();
        if (vao == 0) {
            vao = GlStateManager._glGenVertexArrays();
            vbo = GlStateManager._glGenBuffers();
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, octets, GL15.GL_STREAM_DRAW);
        DefaultVertexFormat.POSITION_COLOR_TEX.setupBufferState();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        int n = quads * 6;
        indices.bind(n);
        sh.setSampler("Sampler0", idTextureObjet);
        if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (sh.COLOR_MODULATOR != null) sh.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        sh.apply();
        RenderSystem.drawElements(GL11.GL_TRIANGLES, n, indices.type().asGLType);
        sh.clear();
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate(); // le jeu rebranchera son propre tableau de sommets
        octets.clear();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void sommet(float x, float y, float u, float v, int r, int g, int b, int a) {
        if (octets.remaining() < SOMMET) octets = MemoryUtil.memRealloc(octets, octets.capacity() * 2); // rare : une fois au plus
        octets.putFloat(m00 * x + m10 * y + m30).putFloat(m01 * x + m11 * y + m31).putFloat(m32)
                .put((byte) r).put((byte) g).put((byte) b).put((byte) a)
                .putFloat(u).putFloat(v);
    }

    /** Quad aligne (x0, y0) - (x1, y1) avec une portion de texture. */
    public static void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        int a = argb >>> 24;
        if (a == 0 || !ouvert) return;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        sommet(x0, y0, u0, v0, r, g, b, a);
        sommet(x0, y1, u0, v1, r, g, b, a);
        sommet(x1, y1, u1, v1, r, g, b, a);
        sommet(x1, y0, u1, v0, r, g, b, a);
        quads++;
    }

    /** Sprite entier dans le rectangle donne. */
    public static void sprite(Atlas.Sprite s, float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x1, y1, s.u0, s.v0, s.u1, s.v1, argb);
    }

    /** Sprite carre centre en (cx, cy), de demi-cote r. */
    public static void carre(Atlas.Sprite s, float cx, float cy, float r, int argb) {
        quad(cx - r, cy - r, cx + r, cy + r, s.u0, s.v0, s.u1, s.v1, argb);
    }

    /** Rectangle plein (case blanche de l'atlas). */
    public static void rect(float x0, float y0, float x1, float y1, int argb) {
        Atlas.Sprite s = Atlas.BLANC;
        quad(x0, y0, x1, y1, s.u0, s.v0, s.u1, s.v1, argb);
    }

    /** Pilule (bouts ronds) : demi-disque, rectangle, demi-disque, sans recouvrement. */
    public static void pilule(float x0, float y0, float x1, float y1, int argb) {
        float r = (y1 - y0) * 0.5f;
        if (x1 - x0 < 2 * r) x1 = x0 + 2 * r;
        Atlas.Sprite d = Atlas.DISQUE;
        float um = (d.u0 + d.u1) * 0.5f;
        quad(x0, y0, x0 + r, y1, d.u0, d.v0, um, d.v1, argb);
        rect(x0 + r, y0, x1 - r, y1, argb);
        quad(x1 - r, y0, x1, y1, um, d.v0, d.u1, d.v1, argb);
    }

    /**
     * Sprite dans le rectangle local (lx0, ly0) - (lx1, ly1) tourne autour de (cx, cy) : (cos, sin) = direction de
     * l'axe x local a l'ecran (y vers le bas, sens horaire).
     */
    public static void tourne(Atlas.Sprite s, float cx, float cy, float lx0, float ly0, float lx1, float ly1, float cos, float sin, int argb) {
        int a = argb >>> 24;
        if (a == 0 || !ouvert) return;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        sommet(cx + lx0 * cos - ly0 * sin, cy + lx0 * sin + ly0 * cos, s.u0, s.v0, r, g, b, a);
        sommet(cx + lx0 * cos - ly1 * sin, cy + lx0 * sin + ly1 * cos, s.u0, s.v1, r, g, b, a);
        sommet(cx + lx1 * cos - ly1 * sin, cy + lx1 * sin + ly1 * cos, s.u1, s.v1, r, g, b, a);
        sommet(cx + lx1 * cos - ly0 * sin, cy + lx1 * sin + ly0 * cos, s.u1, s.v0, r, g, b, a);
        quads++;
    }

    // ------------------------------------------------------------------ couleurs
    /** Couleur RGB avec une opacite 0..1. */
    public static int argb(int rgb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f + 0.5f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int melange(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
