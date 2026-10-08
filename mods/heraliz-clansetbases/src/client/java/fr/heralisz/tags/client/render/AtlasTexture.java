package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.client.gui.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * L'atlas des tags charge avec une chaine de mipmaps (5 niveaux : 2048 → 128 px) et filtre en trilineaire
 * (GL_LINEAR_MIPMAP_LINEAR) : les traits fins des pochoirs restent nets de pres et lisses de loin au lieu de
 * scintiller (une case de 256 px couvre ~15 px a 48 blocs). Les niveaux sont calcules ici ({@link #reduire}) en alpha
 * premultiplie, couleurs en lumiere lineaire : le {@code MipmapGenerator} du jeu met a zero tout alpha &lt; 96 (la brume
 * de bombe et les bords doux disparaissaient des 3-4 blocs, decoupe nette d'autocollant) et divise la couleur par 4
 * meme quand des texels sont vides (liseré sombre au loin). Les texels transparents de l'atlas portent la couleur du
 * texel peint voisin (tools/tags/tag_art_v2.py) : pas de frange noire au filtrage. Une seule texture pour le monde et
 * l'interface (la grille de la collection reduit aussi la case 256 → 40 px).
 * <p>Enregistree a la demande dans le TextureManager sous {@link #LOC} (pas un fichier) ; au rechargement des
 * ressources (F3+T) le TextureManager la recharge lui-meme, mipmaps compris.
 */
public final class AtlasTexture extends AbstractTexture {
    /** Emplacement de la texture dans le TextureManager (le PNG source reste {@link Ui#ATLAS}). */
    public static final ResourceLocation LOC = HeraliszTags.id("tags_atlas_mipmap");
    /** Nombre de niveaux au-dela du niveau 0 (2048 → 1024, 512, 256, 128). */
    public static final int NIVEAUX = 4;

    /**
     * La texture mipmappee de l'atlas, enregistree au premier appel (thread de rendu). Si le PNG est absent le
     * TextureManager renvoie la texture manquante (l'interface se protege par {@link Ui#atlasPresent()}).
     */
    public static AbstractTexture obtenir(Minecraft mc) {
        TextureManager tm = mc.getTextureManager();
        AbstractTexture t = tm.getTexture(LOC, null);
        if (t == null) {
            tm.register(LOC, new AtlasTexture());
            t = tm.getTexture(LOC);
        }
        return t;
    }

    @Override
    public void load(ResourceManager rm) throws IOException {
        Resource res = rm.getResource(Ui.ATLAS).orElseThrow(() -> new FileNotFoundException(Ui.ATLAS.toString()));
        NativeImage image;
        try (InputStream in = res.open()) {
            image = NativeImage.read(in);
        }
        NativeImage[] niveaux = new NativeImage[NIVEAUX + 1];
        niveaux[0] = image;
        for (int l = 1; l <= NIVEAUX; l++) niveaux[l] = reduire(niveaux[l - 1]);
        if (RenderSystem.isOnRenderThread()) televerser(niveaux);
        else RenderSystem.recordRenderCall(() -> televerser(niveaux));
    }

    /** Puissance 2,2 des 256 valeurs d'un canal (sRGB approche → lineaire). */
    private static final float[] LINEAIRE = new float[256];
    /** Retour lineaire → sRGB sur 4096 pas (pas de Math.pow par texel). */
    private static final int[] SRGB = new int[4096];

    static {
        for (int i = 0; i < 256; i++) LINEAIRE[i] = (float) Math.pow(i / 255.0, 2.2);
        for (int i = 0; i < 4096; i++) SRGB[i] = (int) Math.round(Math.pow(i / 4095.0, 1 / 2.2) * 255.0);
    }

    /**
     * Niveau suivant (moitie de cote) : boite 2 × 2 en alpha premultiplie. Alpha = moyenne simple (aucun seuil : la
     * brume reste au loin) ; couleur = moyenne ponderee par l'alpha en lumiere lineaire, ou moyenne des quatre si
     * tout est transparent (couleur voisine saignee conservee, pour le filtrage du niveau suivant).
     */
    static NativeImage reduire(NativeImage src) {
        int w = Math.max(1, src.getWidth() >> 1), h = Math.max(1, src.getHeight() >> 1);
        NativeImage dst = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sa = 0, p0 = 0, p1 = 0, p2 = 0, c0 = 0, c1 = 0, c2 = 0;
                for (int k = 0; k < 4; k++) {
                    int px = src.getPixelRGBA(2 * x + (k & 1), 2 * y + (k >> 1));
                    float a = (px >>> 24) / 255f;
                    float l0 = LINEAIRE[px & 255], l1 = LINEAIRE[(px >> 8) & 255], l2 = LINEAIRE[(px >> 16) & 255];
                    sa += a;
                    p0 += l0 * a;
                    p1 += l1 * a;
                    p2 += l2 * a;
                    c0 += l0;
                    c1 += l1;
                    c2 += l2;
                }
                float r0, r1, r2;
                if (sa > 1e-4f) {
                    r0 = p0 / sa;
                    r1 = p1 / sa;
                    r2 = p2 / sa;
                } else {
                    r0 = c0 / 4;
                    r1 = c1 / 4;
                    r2 = c2 / 4;
                }
                int a = Math.round(sa / 4 * 255f);
                dst.setPixelRGBA(x, y, a << 24 | srgb(r2) << 16 | srgb(r1) << 8 | srgb(r0));
            }
        }
        return dst;
    }

    private static int srgb(float lin) {
        return SRGB[Math.min(4095, Math.max(0, Math.round(lin * 4095f)))];
    }

    /** Televerse tous les niveaux (filtre lineaire + mipmaps, bords bloques) ; les images sont fermees ensuite. */
    private void televerser(NativeImage[] niveaux) {
        int w = niveaux[0].getWidth(), h = niveaux[0].getHeight();
        TextureUtil.prepareImage(getId(), NIVEAUX, w, h);
        for (int l = 0; l < niveaux.length; l++) {
            niveaux[l].upload(l, 0, 0, 0, 0, w >> l, h >> l, true, true, true, true);
        }
        HeraliszTags.LOGGER.debug("[HeraliZ Tags] atlas {}x{} charge avec {} niveaux de mipmap", w, h, niveaux.length);
    }
}
