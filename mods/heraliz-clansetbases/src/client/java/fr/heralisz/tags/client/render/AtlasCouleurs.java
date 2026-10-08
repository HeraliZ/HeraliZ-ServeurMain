package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.client.gui.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

/**
 * Couleur dominante de chaque case de l'atlas des tags, calculee une fois (paresseusement) a partir de l'image, et
 * oubliee au rechargement des ressources. Sert au brouillard de peinture. Moyenne des pixels opaques (alpha &gt; 96)
 * echantillonnes tous les 4 px, saturee legerement pour que la peinture reste lisible.
 */
public final class AtlasCouleurs {
    private AtlasCouleurs() {
    }

    private static int[] couleurs;

    /** Couleur RGB dominante de la case, ou -1 si l'atlas n'est pas lisible / la case est vide. */
    public static int couleur(int cas) {
        int[] c = couleurs;
        if (c == null) c = couleurs = calculer();
        return cas < 0 || cas >= c.length ? -1 : c[cas];
    }

    public static void oublier() {
        couleurs = null;
    }

    private static int[] calculer() {
        int[] out = new int[TagCatalog.ATLAS_CASES];
        Arrays.fill(out, -1);
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Ui.ATLAS);
            if (res.isEmpty()) return out;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                int cw = w / TagCatalog.ATLAS_COLONNES, ch = h / TagCatalog.ATLAS_COLONNES;
                if (cw <= 0 || ch <= 0) return out;
                int pas = Math.max(1, cw / 64);
                for (int cas = 0; cas < out.length; cas++) {
                    int cx = (cas % TagCatalog.ATLAS_COLONNES) * cw, cy = (cas / TagCatalog.ATLAS_COLONNES) * ch;
                    long r = 0, g = 0, b = 0, n = 0;
                    for (int y = cy; y < cy + ch && y < h; y += pas) {
                        for (int x = cx; x < cx + cw && x < w; x += pas) {
                            int abgr = img.getPixelRGBA(x, y);
                            int a = (abgr >>> 24) & 255;
                            if (a < 96) continue;
                            r += abgr & 255;
                            g += (abgr >> 8) & 255;
                            b += (abgr >> 16) & 255;
                            n++;
                        }
                    }
                    if (n < 8) continue;
                    int rr = (int) (r / n), gg = (int) (g / n), bb = (int) (b / n);
                    // legere saturation : on ecarte chaque composante de la moyenne grise
                    int moy = (rr + gg + bb) / 3;
                    rr = borne(moy + (rr - moy) * 3 / 2);
                    gg = borne(moy + (gg - moy) * 3 / 2);
                    bb = borne(moy + (bb - moy) * 3 / 2);
                    // une peinture trop sombre ne se voit pas : plancher de luminosite
                    int lum = (rr * 299 + gg * 587 + bb * 114) / 1000;
                    if (lum < 70) {
                        int add = 70 - lum;
                        rr = borne(rr + add);
                        gg = borne(gg + add);
                        bb = borne(bb + add);
                    }
                    out[cas] = (rr << 16) | (gg << 8) | bb;
                }
            }
        } catch (Exception e) {
            HeraliszTags.LOGGER.warn("[HeraliZ Tags] couleurs de l'atlas non calculees", e);
        }
        return out;
    }

    private static int borne(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
