package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Texture HD de la bombe et de l'ordinateur (128 à 256 texels par bloc) avec mipmaps et filtrage trilinéaire /
 * anisotrope : nette de près, sans scintillement de loin (même méthode que les conteneurs HD de HeraliZ Loot). À
 * dessiner avec les types de {@link TypesRendu} (mipmaps demandés) : une texture enregistrée autrement n'aurait pas de
 * mipmaps.
 */
final class TextureHD extends AbstractTexture {
    private static final Set<ResourceLocation> ENREGISTREES = ConcurrentHashMap.newKeySet();
    private static final int NIVEAUX_MAX = 4;

    private final ResourceLocation chemin;

    private TextureHD(ResourceLocation chemin) {
        this.chemin = chemin;
    }

    /** Enregistre (une fois) la texture auprès du gestionnaire de textures et rend son identifiant (fil de rendu). */
    static ResourceLocation get(ResourceLocation chemin) {
        if (ENREGISTREES.add(chemin)) Minecraft.getInstance().getTextureManager().register(chemin, new TextureHD(chemin));
        return chemin;
    }

    @Override
    public void load(ResourceManager ressources) throws IOException {
        NativeImage base;
        try (InputStream in = ressources.open(this.chemin)) {
            base = NativeImage.read(in);
        }
        int niveaux = 0;
        int min = Math.min(base.getWidth(), base.getHeight());
        while (niveaux < NIVEAUX_MAX && (min >> (niveaux + 1)) >= 8) niveaux++;
        NativeImage[] mips = new NativeImage[niveaux + 1];
        mips[0] = base;
        for (int i = 1; i <= niveaux; i++) mips[i] = reduire(mips[i - 1]);
        if (!RenderSystem.isOnRenderThreadOrInit()) RenderSystem.recordRenderCall(() -> envoyer(mips));
        else envoyer(mips);
    }

    private void envoyer(NativeImage[] mips) {
        TextureUtil.prepareImage(this.getId(), mips.length - 1, mips[0].getWidth(), mips[0].getHeight());
        for (int i = 0; i < mips.length; i++) {
            mips[i].upload(i, 0, 0, 0, 0, mips[i].getWidth(), mips[i].getHeight(), true, false, mips.length > 1, true);
        }
        this.setFilter(true, mips.length > 1);
        try {
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                float max = GL11.glGetFloat(0x84FF); // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, Math.min(8.0F, max)); // GL_TEXTURE_MAX_ANISOTROPY_EXT
            }
        } catch (Throwable ignored) {
            // pas de filtrage anisotrope
        }
    }

    private static NativeImage reduire(NativeImage src) {
        int w = Math.max(1, src.getWidth() >> 1);
        int h = Math.max(1, src.getHeight() >> 1);
        NativeImage dst = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = Math.min(src.getWidth() - 1, x * 2), x1 = Math.min(src.getWidth() - 1, x * 2 + 1);
                int y0 = Math.min(src.getHeight() - 1, y * 2), y1 = Math.min(src.getHeight() - 1, y * 2 + 1);
                dst.setPixelRGBA(x, y, moyenne(src.getPixelRGBA(x0, y0), src.getPixelRGBA(x1, y0), src.getPixelRGBA(x0, y1), src.getPixelRGBA(x1, y1)));
            }
        }
        return dst;
    }

    private static int moyenne(int a, int b, int c, int d) {
        int out = 0;
        for (int dec = 0; dec < 32; dec += 8) {
            int s = ((a >>> dec) & 255) + ((b >>> dec) & 255) + ((c >>> dec) & 255) + ((d >>> dec) & 255);
            out |= ((s + 2) >> 2) << dec;
        }
        return out;
    }
}
