package fr.heraliz.crates.client.render;

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
 * Texture HD avec mipmaps + filtrage trilineaire/anisotrope :
 * les textures 1024px restent nettes de pres et ne scintillent pas de loin.
 */
public class HDTexture extends AbstractTexture {
    private static final Set<ResourceLocation> REGISTERED = ConcurrentHashMap.newKeySet();
    private static final int MAX_LEVELS = 4;

    private final ResourceLocation location;
    /** Image et mipmaps deja preparees pendant l'ecran de chargement (sinon decodees au premier usage). */
    private NativeImage[] preloaded;

    public HDTexture(ResourceLocation location) {
        this.location = location;
    }

    private HDTexture(ResourceLocation location, NativeImage[] preloaded) {
        this.location = location;
        this.preloaded = preloaded;
    }

    /** Enregistre (une fois) la texture aupres du TextureManager et renvoie son identifiant. */
    public static ResourceLocation get(ResourceLocation location) {
        if (REGISTERED.add(location)) {
            Minecraft.getInstance().getTextureManager().register(location, new HDTexture(location));
        }
        return location;
    }

    /** Installe une texture preparee en arriere-plan (fil de rendu : il ne reste que l'envoi a la carte graphique). */
    public static void install(ResourceLocation location, NativeImage[] mips) {
        REGISTERED.add(location);
        Minecraft.getInstance().getTextureManager().register(location, new HDTexture(location, mips));
    }

    /** Lecture de l'image et calcul des mipmaps (peut tourner hors du fil de rendu). */
    public static NativeImage[] decode(ResourceManager resourceManager, ResourceLocation location) throws IOException {
        NativeImage base;
        try (InputStream in = resourceManager.open(location)) {
            base = NativeImage.read(in);
        }
        int levels = 0;
        int min = Math.min(base.getWidth(), base.getHeight());
        while (levels < MAX_LEVELS && (min >> (levels + 1)) >= 8) {
            levels++;
        }
        NativeImage[] mips = new NativeImage[levels + 1];
        mips[0] = base;
        for (int i = 1; i <= levels; i++) {
            mips[i] = downsample(mips[i - 1]);
        }
        return mips;
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        NativeImage[] mips = preloaded != null ? preloaded : decode(resourceManager, location);
        preloaded = null;
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> upload(mips));
        } else {
            upload(mips);
        }
    }

    private void upload(NativeImage[] mips) {
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
            // pas de filtrage anisotrope disponible
        }
    }

    private static NativeImage downsample(NativeImage src) {
        int w = Math.max(1, src.getWidth() >> 1);
        int h = Math.max(1, src.getHeight() >> 1);
        NativeImage dst = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = Math.min(src.getWidth() - 1, x * 2), x1 = Math.min(src.getWidth() - 1, x * 2 + 1);
                int y0 = Math.min(src.getHeight() - 1, y * 2), y1 = Math.min(src.getHeight() - 1, y * 2 + 1);
                int a = src.getPixelRGBA(x0, y0);
                int b = src.getPixelRGBA(x1, y0);
                int c = src.getPixelRGBA(x0, y1);
                int d = src.getPixelRGBA(x1, y1);
                dst.setPixelRGBA(x, y, avg(a, b, c, d));
            }
        }
        return dst;
    }

    private static int avg(int a, int b, int c, int d) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int s = ((a >>> shift) & 255) + ((b >>> shift) & 255) + ((c >>> shift) & 255) + ((d >>> shift) & 255);
            out |= ((s + 2) >> 2) << shift;
        }
        return out;
    }
}
