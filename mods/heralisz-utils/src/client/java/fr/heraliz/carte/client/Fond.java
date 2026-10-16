package fr.heraliz.carte.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.carte.HeralizCarte;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Le fond de la carte : vue de dessus du monde, refaite par outils/carte_monde.py et rangée dans le jar joueur
 * (assets/heraliz_carte/carte/ : carte.json et des tuiles PNG de 1024 px au plus par vue). Absent tant que l'outil n'a
 * pas été lancé : l'écran montre alors un fond neutre et un message. Les tuiles se chargent à la première vue (ou à la
 * connexion), restent en mémoire graphique pendant la partie et sont libérées à la déconnexion.
 */
final class Fond {
    private Fond() {
    }

    /** Ce que dit carte.json : monde, emprise en blocs, blocs par pixel, découpage en tuiles, vues fabriquées. */
    record Info(ResourceLocation dimension, int x0, int z0, int largeur, int hauteur, int blocsParPixel, int pixelsL, int pixelsH,
                int tuile, int colonnes, int lignes, List<String> vues, String fait, @Nullable int[] bordure) {
        boolean a(String vue) {
            return vues.contains(vue);
        }
    }

    /**
     * Rectangle (x1, z1, x2, z2) de la clé « bordure » de carte.json (écrite par outils/carte_monde.py : bordure du
     * monde, ou rectangle donné) ; null si absente ou fausse.
     */
    @Nullable
    private static int[] bordure(JsonObject o) {
        if (!o.has("bordure") || !o.get("bordure").isJsonArray()) return null;
        JsonArray a = o.getAsJsonArray("bordure");
        if (a.size() != 4) return null;
        int x1 = a.get(0).getAsInt(), z1 = a.get(1).getAsInt(), x2 = a.get(2).getAsInt(), z2 = a.get(3).getAsInt();
        if (x2 <= x1 || z2 <= z1) return null;
        return new int[]{x1, z1, x2, z2};
    }

    private static final ResourceLocation JSON = HeralizCarte.id("carte/carte.json");
    @Nullable
    private static Info info;
    private static boolean lu;
    /** Tuiles déjà préparées (bord collé, sans répétition) et tuiles présentes dans les ressources. */
    private static final Set<ResourceLocation> PREPAREES = new HashSet<>();
    private static final Map<ResourceLocation, Boolean> PRESENTES = new HashMap<>();
    private static final List<ResourceLocation> CHARGEES = new ArrayList<>();

    @Nullable
    static Info info() {
        if (!lu) {
            lu = true;
            info = lire();
        }
        return info;
    }

    @Nullable
    private static Info lire() {
        try {
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(JSON);
            if (r.isEmpty()) return null;
            JsonObject o;
            try (Reader in = new InputStreamReader(r.get().open(), StandardCharsets.UTF_8)) {
                o = JsonParser.parseReader(in).getAsJsonObject();
            }
            List<String> vues = new ArrayList<>();
            JsonArray a = o.has("vues") ? o.getAsJsonArray("vues") : new JsonArray();
            a.forEach(e -> vues.add(e.getAsString()));
            ResourceLocation dim = ResourceLocation.tryParse(o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld");
            Info i = new Info(dim == null ? new ResourceLocation("minecraft", "overworld") : dim, o.get("x0").getAsInt(), o.get("z0").getAsInt(),
                    o.get("largeur").getAsInt(), o.get("hauteur").getAsInt(), Math.max(1, o.get("blocsParPixel").getAsInt()),
                    o.get("pixelsLargeur").getAsInt(), o.get("pixelsHauteur").getAsInt(), Math.max(16, o.get("tuile").getAsInt()),
                    o.get("colonnes").getAsInt(), o.get("lignes").getAsInt(), vues, o.has("fait") ? o.get("fait").getAsString() : "",
                    bordure(o));
            if (i.largeur() <= 0 || i.hauteur() <= 0 || i.colonnes() <= 0 || i.lignes() <= 0) return null;
            return i;
        } catch (Exception ex) {
            HeralizCarte.LOG.warn("[carte] carte.json illisible : fond de carte coupé ({})", ex.toString());
            return null;
        }
    }

    static ResourceLocation tuile(String vue, int i, int j) {
        return HeralizCarte.id("carte/" + vue + "_" + i + "_" + j + ".png");
    }

    private static boolean presente(ResourceLocation rl) {
        return PRESENTES.computeIfAbsent(rl, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }

    /**
     * Dessine les tuiles visibles de la vue. (ox, oy) : point de l'écran où tombe le monde (cx, cz) ; k : unités
     * d'interface par bloc. Filtrage lisse quand l'image est réduite, net (blocs bien carrés) quand on zoome.
     */
    static void dessiner(GuiGraphics g, Info f, String vue, float ox, float oy, double cx, double cz, float k, int largeur, int hauteur) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        float s = k * f.blocsParPixel();
        double echelleEcran = Minecraft.getInstance().getWindow().getGuiScale();
        boolean lisse = s * echelleEcran < 2.0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int j = 0; j < f.lignes(); j++) {
            for (int i = 0; i < f.colonnes(); i++) {
                int tw = Math.min(f.tuile(), f.pixelsL() - i * f.tuile()), th = Math.min(f.tuile(), f.pixelsH() - j * f.tuile());
                if (tw <= 0 || th <= 0) continue;
                double wx = f.x0() + (double) i * f.tuile() * f.blocsParPixel(), wz = f.z0() + (double) j * f.tuile() * f.blocsParPixel();
                float x = (float) (ox + (wx - cx) * k), y = (float) (oy + (wz - cz) * k);
                if (x > largeur || y > hauteur || x + tw * s < 0 || y + th * s < 0) continue;
                ResourceLocation rl = tuile(vue, i, j);
                if (!presente(rl)) continue;
                AbstractTexture t = tm.getTexture(rl);
                if (PREPAREES.add(rl)) {
                    CHARGEES.add(rl);
                    RenderSystem.bindTexture(t.getId());
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                }
                t.setFilter(lisse, false);
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(s, s, 1);
                g.blit(rl, 0, 0, 0f, 0f, tw, th, tw, th);
                g.pose().popPose();
            }
        }
    }

    /** Charge à l'avance les tuiles d'une vue (pas d'à-coup à la première ouverture de la carte). */
    static void precharger(String vue) {
        Info f = info();
        if (f == null || !f.a(vue)) return;
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (int j = 0; j < f.lignes(); j++) {
            for (int i = 0; i < f.colonnes(); i++) {
                ResourceLocation rl = tuile(vue, i, j);
                if (!presente(rl)) continue;
                AbstractTexture t = tm.getTexture(rl);
                if (PREPAREES.add(rl)) {
                    CHARGEES.add(rl);
                    RenderSystem.bindTexture(t.getId());
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                }
            }
        }
    }

    /** Déconnexion ou rechargement des ressources : tuiles libérées, carte.json relu à la prochaine vue. */
    static void oublier() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation rl : CHARGEES) tm.release(rl);
        CHARGEES.clear();
        PREPAREES.clear();
        PRESENTES.clear();
        lu = false;
        info = null;
    }
}
