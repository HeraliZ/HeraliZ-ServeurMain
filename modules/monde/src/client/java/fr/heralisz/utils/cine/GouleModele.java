package fr.heralisz.utils.cine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.utils.HeraliszUtils;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Squelette des goules de la cinématique, lu dans assets/heralisz_utils/cine/goule.json (écrit par tools/gen_goule.py,
 * avec les textures : aucune coordonnée en double ici). Unités du modèle : 32 par bloc, y vers le bas, sol à y = 64 ;
 * une partie peut être dessinée plus fine (« echelle » : la tête, à 0,5). Un seul modèle cuit, partagé par toutes les
 * goules : chacune y pose ses angles juste avant de se dessiner (Goule.dessiner).
 */
@OnlyIn(Dist.CLIENT)
final class GouleModele {
    /** Une articulation : la partie cuite, sa pose de repos (pivot, angles) et son échelle. */
    record Os(ModelPart part, String parent, float px, float py, float pz, float rx, float ry, float rz, float echelle, List<String> variantes) {}

    private static GouleModele instance;
    final ModelPart racine;
    final Map<String, Os> os = new HashMap<>();
    final int largeur, hauteur;

    private GouleModele(ModelPart racine, int largeur, int hauteur) {
        this.racine = racine;
        this.largeur = largeur;
        this.hauteur = hauteur;
    }

    static GouleModele get() {
        if (instance == null) {
            instance = charger();
        }
        return instance;
    }

    /** Rechargement des ressources (le json ou les textures ont changé). */
    static void vider() {
        instance = null;
    }

    private static float f(JsonArray a, int i) {
        return a.get(i).getAsFloat();
    }

    private static GouleModele charger() {
        ResourceLocation rl = new ResourceLocation(HeraliszUtils.MODID, "cine/goule.json");
        JsonObject o;
        try (Reader in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(rl).orElseThrow().open(),
                StandardCharsets.UTF_8)) {
            o = JsonParser.parseReader(in).getAsJsonObject();
        } catch (Exception e) {
            HeraliszUtils.LOG.error("Goule de la cinématique : squelette illisible ({})", rl, e);
            o = new JsonObject();
            o.add("parts", new JsonArray());
            JsonArray t = new JsonArray();
            t.add(256);
            t.add(256);
            o.add("texture", t);
        }
        JsonArray tex = o.getAsJsonArray("texture");
        Map<String, JsonObject> parts = new HashMap<>();
        Map<String, List<String>> enfants = new HashMap<>();
        for (JsonElement el : o.getAsJsonArray("parts")) {
            JsonObject p = el.getAsJsonObject();
            String nom = p.get("nom").getAsString();
            parts.put(nom, p);
            String parent = p.get("parent").isJsonNull() ? "" : p.get("parent").getAsString();
            enfants.computeIfAbsent(parent, k -> new ArrayList<>()).add(nom);
        }
        MeshDefinition mesh = new MeshDefinition();
        for (String nom : enfants.getOrDefault("", List.of())) {
            ajouter(mesh.getRoot(), nom, parts, enfants);
        }
        ModelPart racine = LayerDefinition.create(mesh, tex.get(0).getAsInt(), tex.get(1).getAsInt()).bakeRoot();
        GouleModele m = new GouleModele(racine, tex.get(0).getAsInt(), tex.get(1).getAsInt());
        for (String nom : enfants.getOrDefault("", List.of())) {
            m.indexer(racine.getChild(nom), nom, parts, enfants);
        }
        return m;
    }

    private static void ajouter(PartDefinition parent, String nom, Map<String, JsonObject> parts, Map<String, List<String>> enfants) {
        JsonObject p = parts.get(nom);
        CubeListBuilder c = CubeListBuilder.create();
        for (JsonElement el : p.getAsJsonArray("cubes")) {
            JsonObject cu = el.getAsJsonObject();
            JsonArray uv = cu.getAsJsonArray("uv"), or = cu.getAsJsonArray("o"), s = cu.getAsJsonArray("s");
            c.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                    .addBox(f(or, 0), f(or, 1), f(or, 2), f(s, 0), f(s, 1), f(s, 2), new CubeDeformation(cu.get("g").getAsFloat()));
        }
        JsonArray pv = p.getAsJsonArray("pivot"), r = p.getAsJsonArray("rot");
        PartDefinition d = parent.addOrReplaceChild(nom, c, PartPose.offsetAndRotation(f(pv, 0), f(pv, 1), f(pv, 2), f(r, 0), f(r, 1), f(r, 2)));
        for (String e : enfants.getOrDefault(nom, List.of())) {
            ajouter(d, e, parts, enfants);
        }
    }

    private void indexer(ModelPart part, String nom, Map<String, JsonObject> parts, Map<String, List<String>> enfants) {
        JsonObject p = parts.get(nom);
        JsonArray pv = p.getAsJsonArray("pivot"), r = p.getAsJsonArray("rot");
        List<String> var = null;
        if (p.has("variantes") && p.get("variantes").isJsonArray()) {
            var = new ArrayList<>();
            for (JsonElement v : p.getAsJsonArray("variantes")) {
                var.add(v.getAsString());
            }
        }
        float ech = p.has("echelle") ? p.get("echelle").getAsFloat() : 1f;
        String parent = p.get("parent").isJsonNull() ? null : p.get("parent").getAsString();
        os.put(nom, new Os(part, parent, f(pv, 0), f(pv, 1), f(pv, 2), f(r, 0), f(r, 1), f(r, 2), ech, var));
        for (String e : enfants.getOrDefault(nom, List.of())) {
            indexer(part.getChild(e), e, parts, enfants);
        }
    }
}
