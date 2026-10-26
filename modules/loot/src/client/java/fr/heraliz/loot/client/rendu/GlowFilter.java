package fr.heraliz.loot.client.rendu;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.client.rendu.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Optimisation : pour la passe lumineuse, on ne redessine que les faces dont la zone du calque lumineux
 * contient reellement de la lumiere (l'atlas est analyse par cases de 32 px).
 */
public final class GlowFilter {
    private GlowFilter() {
    }

    private static final int CELLS = 32;
    private static final Map<ResourceLocation, boolean[]> MASKS = new HashMap<>();
    private static final Map<ResourceLocation, Map<Mesh, Mesh>> CACHE = new HashMap<>();

    public static Mesh glowOnly(Mesh mesh, ResourceLocation glowTexture) {
        if (!mesh.cacheable) return mesh;
        Map<Mesh, Mesh> cache = CACHE.computeIfAbsent(glowTexture, k -> new IdentityHashMap<>());
        Mesh out = cache.get(mesh);
        if (out == null) {
            boolean[] mask = MASKS.computeIfAbsent(glowTexture, GlowFilter::scan);
            out = mask == null ? mesh : mesh.filter((u, v) -> lit(mask, u, v));
            cache.put(mesh, out);
        }
        return out;
    }

    private static boolean lit(boolean[] mask, float u, float v) {
        int x = Math.max(0, Math.min(CELLS - 1, (int) (u * CELLS)));
        int y = Math.max(0, Math.min(CELLS - 1, (int) (v * CELLS)));
        // la case et ses voisines (une face peut deborder de sa case centrale)
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < CELLS && yy < CELLS && mask[yy * CELLS + xx]) return true;
            }
        }
        return false;
    }

    private static boolean[] scan(ResourceLocation tex) {
        boolean[] mask = new boolean[CELLS * CELLS];
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(tex); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            for (int cy = 0; cy < CELLS; cy++) {
                for (int cx = 0; cx < CELLS; cx++) {
                    int x0 = cx * w / CELLS, x1 = (cx + 1) * w / CELLS, y0 = cy * h / CELLS, y1 = (cy + 1) * h / CELLS;
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
                    mask[cy * CELLS + cx] = lit;
                }
            }
        } catch (Exception e) {
            HeralizLoot.LOG.warn("[HeraliZ Loot] Analyse du calque lumineux impossible pour {}", tex, e);
            return null;
        }
        return mask;
    }

    public static void clear() {
        MASKS.clear();
        CACHE.clear();
    }
}
