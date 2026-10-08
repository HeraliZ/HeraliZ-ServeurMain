package fr.heralisz.matchs.zombies.client.rendu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Modele « zombies v2 » genere par script ({@code tools/matchs/zombies/*.py}, kit {@code zbkit.py}) : groupes de quads
 * textures lus dans {@code assets/heralisz_matchs/zbmodeles/<nom>.json} (voir {@link ModelesZb}). Le meme fichier sert
 * aux apercus hors jeu (rendu logiciel du kit) : ce que l'on voit sur les apercus est ce que dessine le jeu.
 * <p>Format : {@code {"v": 1, "nom", "texture": "textures/entity/...", "groupes": [{"nom", "pivot": [x, y, z],
 * "q": [x, y, z, u, v (x 4), nx, ny, nz, couche, ...]}]}} ; positions en pixels (1/16 de bloc, converties en blocs a la
 * lecture), origine au centre bas de la case, +z vers l'avant ; couches {@link #BASE} (texture decoupee),
 * {@link #LUEUR} (texture {@code _lueur}, additive, pleine lumiere) et {@link #VERRE} (translucide).
 * <p>Dessin sans allocation : les quads sont des tableaux de flottants (23 par quad), transformes avec des vecteurs
 * reutilises (fil de rendu seulement).
 */
public final class ModeleZb {
    public static final int BASE = 0, LUEUR = 1, VERRE = 2;
    /** Flottants par quad : 4 sommets (x, y, z, u, v) + normale (3). */
    public static final int PAS = 23;

    public final String nom;
    /** Texture par defaut (ex. {@code heralisz_matchs:textures/entity/zombies/v2/bouteille.png}), sans theme. */
    public final ResourceLocation texture;
    private final Groupe[] groupes;
    private final Map<String, Groupe> parNom = new HashMap<>();

    /** Groupe nomme : quads par couche, pivot (en blocs) pour les animations. */
    public static final class Groupe {
        public final String nom;
        public final float px, py, pz;
        final float[][] quads = new float[3][];

        Groupe(String nom, float px, float py, float pz) {
            this.nom = nom;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }

        /** Vrai si le groupe a des quads dans cette couche. */
        public boolean a(int couche) {
            return quads[couche] != null && quads[couche].length > 0;
        }
    }

    private ModeleZb(String nom, ResourceLocation texture, Groupe[] groupes) {
        this.nom = nom;
        this.texture = texture;
        this.groupes = groupes;
        for (Groupe g : groupes) parNom.put(g.nom, g);
    }

    /** Lecture du JSON (exception si le fichier est invalide : le chargeur la journalise). */
    public static ModeleZb lire(String nomDefaut, String espace, JsonObject o) {
        String nom = o.has("nom") ? o.get("nom").getAsString() : nomDefaut;
        String tex = o.has("texture") ? o.get("texture").getAsString() : "textures/entity/zombies/v2/" + nom;
        ResourceLocation rl = new ResourceLocation(espace, tex.endsWith(".png") ? tex : tex + ".png");
        JsonArray ga = o.getAsJsonArray("groupes");
        Groupe[] gs = new Groupe[ga.size()];
        for (int i = 0; i < ga.size(); i++) {
            JsonObject g = ga.get(i).getAsJsonObject();
            JsonArray p = g.has("pivot") ? g.getAsJsonArray("pivot") : null;
            Groupe gr = new Groupe(g.get("nom").getAsString(), p == null ? 0 : p.get(0).getAsFloat() / 16F,
                    p == null ? 0 : p.get(1).getAsFloat() / 16F, p == null ? 0 : p.get(2).getAsFloat() / 16F);
            JsonArray q = g.getAsJsonArray("q");
            int n = q.size() / 24;
            int[] compte = new int[3];
            for (int k = 0; k < n; k++) compte[couche(q, k)]++;
            for (int c = 0; c < 3; c++) gr.quads[c] = new float[compte[c] * PAS];
            int[] pos = new int[3];
            for (int k = 0; k < n; k++) {
                int c = couche(q, k);
                float[] dst = gr.quads[c];
                int o2 = pos[c];
                int base = k * 24;
                for (int s = 0; s < 4; s++) {
                    int src = base + s * 5;
                    dst[o2 + s * 5] = q.get(src).getAsFloat() / 16F;
                    dst[o2 + s * 5 + 1] = q.get(src + 1).getAsFloat() / 16F;
                    dst[o2 + s * 5 + 2] = q.get(src + 2).getAsFloat() / 16F;
                    dst[o2 + s * 5 + 3] = q.get(src + 3).getAsFloat();
                    dst[o2 + s * 5 + 4] = q.get(src + 4).getAsFloat();
                }
                dst[o2 + 20] = q.get(base + 20).getAsFloat();
                dst[o2 + 21] = q.get(base + 21).getAsFloat();
                dst[o2 + 22] = q.get(base + 22).getAsFloat();
                pos[c] += PAS;
            }
            gs[i] = gr;
        }
        return new ModeleZb(nom, rl, gs);
    }

    private static int couche(JsonArray q, int k) {
        JsonElement e = q.get(k * 24 + 23);
        int c = e.getAsInt();
        return c < 0 || c > 2 ? 0 : c;
    }

    @Nullable
    public Groupe groupe(String nom) {
        return parNom.get(nom);
    }

    public Groupe[] groupes() {
        return groupes;
    }

    // ------------------------------------------------------------------ dessin
    private static final Vector4f V = new Vector4f();
    private static final Vector3f N = new Vector3f();

    /** Dessine une couche d'un groupe ; {@code argb} module la texture (pour la lueur : intensite via le RGB). */
    public static void dessiner(@Nullable Groupe g, int couche, PoseStack ps, VertexConsumer vc, int lumiere, int overlay, int argb) {
        if (g == null) return;
        float[] q = g.quads[couche];
        if (q == null || q.length == 0) return;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
        for (int i = 0; i < q.length; i += PAS) {
            N.set(q[i + 20], q[i + 21], q[i + 22]).mul(nm);
            for (int s = 0; s < 4; s++) {
                int o = i + s * 5;
                V.set(q[o], q[o + 1], q[o + 2], 1F).mul(m);
                vc.vertex(V.x(), V.y(), V.z()).color(r, gg, b, a).uv(q[o + 3], q[o + 4]).overlayCoords(overlay).uv2(lumiere)
                        .normal(N.x(), N.y(), N.z()).endVertex();
            }
        }
    }

    /** Applique une rotation (degres) autour du pivot du groupe sur la pile (a encadrer de push/pop). */
    public static void tourner(PoseStack ps, Groupe g, com.mojang.math.Axis axe, float degres) {
        ps.translate(g.px, g.py, g.pz);
        ps.mulPose(axe.rotationDegrees(degres));
        ps.translate(-g.px, -g.py, -g.pz);
    }
}
