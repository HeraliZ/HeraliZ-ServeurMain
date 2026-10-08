package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Optimisation : pour la passe lumineuse, on ne redessine que les faces dont la zone du calque
 * lumineux contient reellement de la lumiere (les tuiles entierement noires sont ignorees).
 */
public final class GlowFilter {
    private GlowFilter() {
    }

    private static final Map<ResourceLocation, boolean[]> TILE_MASKS = new HashMap<>();
    private static final Map<ResourceLocation, Map<Mesh, Mesh>> CACHE = new HashMap<>();

    /** Synchronise : appele aussi par la cuisson des caisses en arriere-plan. */
    public static synchronized Mesh glowOnly(Mesh mesh, ResourceLocation glowTexture) {
        boolean[] mask = TILE_MASKS.computeIfAbsent(glowTexture, GlowFilter::scan);
        // maillages dynamiques (reconstruits a chaque image) : filtres a chaque fois, sans cache (le plus souvent il ne
        // reste rien : tentacules, pattes... n'ont pas de zone lumineuse)
        if (!mesh.cacheable) return filter(mesh, mask);
        Map<Mesh, Mesh> cache = CACHE.computeIfAbsent(glowTexture, k -> new IdentityHashMap<>());
        Mesh out = cache.get(mesh);
        if (out == null) {
            out = filter(mesh, mask);
            cache.put(mesh, out);
        }
        return out;
    }

    private static Mesh filter(Mesh mesh, boolean[] mask) {
        return mesh.filter((u, v) -> {
            for (int i = 0; i < Mat.TILES.length; i++) {
                if (Mat.TILES[i].contains(u, v)) return mask[i];
            }
            return true;
        });
    }

    /** Pour chaque tuile de l'atlas : contient-elle au moins un pixel lumineux ? */
    private static boolean[] scan(ResourceLocation tex) {
        boolean[] mask = new boolean[Mat.TILES.length];
        java.util.Arrays.fill(mask, true);
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(tex); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            for (int i = 0; i < Mat.TILES.length; i++) {
                Mat t = Mat.TILES[i];
                int x0 = (int) (t.u0 * w), x1 = (int) (t.u1 * w), y0 = (int) (t.v0 * h), y1 = (int) (t.v1 * h);
                boolean lit = false;
                for (int y = y0; y < y1 && !lit; y++) {
                    for (int x = x0; x < x1; x++) {
                        int c = img.getPixelRGBA(x, y);
                        if ((c & 0xFF) > 6 || ((c >> 8) & 0xFF) > 6 || ((c >> 16) & 0xFF) > 6) {
                            lit = true;
                            break;
                        }
                    }
                }
                mask[i] = lit;
            }
        } catch (Exception e) {
            HeralizCrates.LOGGER.warn("[HeralizCrates] Analyse du calque lumineux impossible pour {}", tex, e);
        }
        return mask;
    }

    /** Vide les caches (rechargement des ressources). */
    public static synchronized void clear() {
        TILE_MASKS.clear();
        CACHE.clear();
    }
}
