package fr.heralisz.clans.client.banniere;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Texture GL d'une banniere composee : envoyee directement depuis un tampon direct (octets R, G, B, A), sans garder
 * d'image en memoire cote CPU. Taille changeable (la memoire GL est re-specifiee). {@link #close()} libere l'id GL.
 */
@OnlyIn(Dist.CLIENT)
public final class TextureBanniere extends AbstractTexture {
    private final boolean lisse;
    private int largeur = -1, hauteur = -1;

    /** @param lisse filtrage lineaire (tissu 3D, image de travail a demi-resolution) ; sinon au plus proche. */
    public TextureBanniere(boolean lisse) {
        this.lisse = lisse;
    }

    @Override
    public void load(ResourceManager rm) {
    }

    public int largeur() {
        return largeur;
    }

    public int hauteur() {
        return hauteur;
    }

    /** Envoie {@code w x h} pixels (tampon direct, ordre natif, entiers ABGR) ; fil de rendu uniquement. */
    public void envoyer(ByteBuffer pixels, int w, int h) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (w != largeur || h != hauteur) {
            TextureUtil.prepareImage(getId(), w, h);
            largeur = w;
            hauteur = h;
            setFilter(lisse, false);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);   // GL_CLAMP_TO_EDGE
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        }
        bind();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                MemoryUtil.memAddress(pixels, 0));
    }

    @Override
    public void close() {
        releaseId();
        largeur = hauteur = -1;
    }
}
