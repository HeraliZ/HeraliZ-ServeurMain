package fr.heraliz.clashwar.moteur.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Metadonnees d'une carte de match, lues dans {@code data/heraliz_clashwar/cartes/<id>.json} (la structure est
 * {@code data/heraliz_clashwar/structures/<id>.nbt}). Format (MATCHS_SPEC §1.5) :
 * <pre>
 * {"id": "clashwar_2v2", "nom_fr": "Entrepot", "nom_en": "Warehouse", "type": "clashwar_2v2",
 *  "dimension": "heraliz_clashwar:arenes", "taille": [52, 24, 52], "bornes": [0, 0, 0, 51, 23, 51],
 *  "points": {"spawn_a": [[4, 1, 26, -90], ...], "spawn_b": [...], "spectateur": [[26, 12, 26]]},
 *  ... champs propres au mode ...}
 * </pre>
 * Coordonnees relatives a l'origine de la structure. {@code bornes} = blocs extremes <b>inclus</b> (l'AABB va donc de
 * (x0, y0, z0) a (x1 + 1, y1 + 1, z1 + 1)) ; absentes = toute la structure. {@code dimension} absente = zombies pour le
 * type {@code zombies}, arenes sinon. Les champs propres au mode (zones, portes, sites...) se lisent dans {@link #meta()}.
 *
 * @param id      identifiant (= nom des fichiers)
 * @param nomFr   nom affiche en francais
 * @param nomEn   nom affiche en anglais
 * @param type    un des {@code TYPE_*}
 * @param dimension dimension ou poser les instances
 * @param taille  taille de la structure en blocs
 * @param bornes  bornes jouables relatives (AABB en coordonnees de bloc, max exclusif)
 * @param points  points nommes relatifs (listes non modifiables, ordre du JSON)
 * @param meta    JSON complet (lecture seule par convention)
 */
public record Carte(String id, String nomFr, String nomEn, String type, ResourceKey<Level> dimension, Vec3i taille, AABB bornes,
                    Map<String, List<Pose>> points, JsonObject meta) {
    public static final String TYPE_LOBBY_CLASHWAR = "lobby_clashwar";
    public static final String TYPE_CLASHWAR_1V1 = "clashwar_1v1", TYPE_CLASHWAR_2V2 = "clashwar_2v2",
            TYPE_CLASHWAR_3V3 = "clashwar_3v3", TYPE_CLASHWAR_4V4 = "clashwar_4v4";
    /** Grandes cartes de recherche et destruction du ClashWar, pour tous les formats (passent avant celles du format). */
    public static final String TYPE_CLASHWAR_RD = "clashwar_rd";
    public static final String TYPE_DUEL = "duel";
    public static final String TYPE_ZOMBIES = "zombies";

    /** Points relatifs de ce nom (liste vide si absent). */
    public List<Pose> pointsRelatifs(String nom) {
        List<Pose> l = points.get(nom);
        return l == null ? List.of() : l;
    }

    /** Nom dans la langue demandee. */
    public String nom(boolean francais) {
        return francais ? nomFr : nomEn;
    }

    /**
     * Lit une carte depuis son JSON ; {@code idParDefaut} (nom du fichier) sert si {@code id} manque. Rend null (avec
     * un avertissement) si le JSON est inutilisable.
     */
    public static Carte lire(String idParDefaut, JsonObject o) {
        try {
            String id = o.has("id") ? o.get("id").getAsString() : idParDefaut;
            String nomFr = o.has("nom_fr") ? o.get("nom_fr").getAsString() : id;
            String nomEn = o.has("nom_en") ? o.get("nom_en").getAsString() : nomFr;
            String type = o.has("type") ? o.get("type").getAsString().toLowerCase(Locale.ROOT) : TYPE_DUEL;
            ResourceKey<Level> dim;
            if (o.has("dimension")) dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(o.get("dimension").getAsString()));
            else dim = HeralizClashWar.ARENES;
            Vec3i taille = Vec3i.ZERO;
            if (o.has("taille")) {
                JsonArray t = o.getAsJsonArray("taille");
                taille = new Vec3i(t.get(0).getAsInt(), t.get(1).getAsInt(), t.get(2).getAsInt());
            }
            AABB bornes;
            if (o.has("bornes")) {
                JsonArray b = o.getAsJsonArray("bornes");
                int x0 = b.get(0).getAsInt(), y0 = b.get(1).getAsInt(), z0 = b.get(2).getAsInt();
                int x1 = b.get(3).getAsInt(), y1 = b.get(4).getAsInt(), z1 = b.get(5).getAsInt();
                bornes = new AABB(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, Math.max(z0, z1) + 1);
            } else {
                bornes = new AABB(0, 0, 0, taille.getX(), taille.getY(), taille.getZ());
            }
            Map<String, List<Pose>> points = new LinkedHashMap<>();
            if (o.has("points") && o.get("points").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("points").entrySet()) {
                    java.util.ArrayList<Pose> l = new java.util.ArrayList<>();
                    JsonElement v = e.getValue();
                    if (v.isJsonArray()) {
                        JsonArray a = v.getAsJsonArray();
                        // une seule pose [x, y, z(, yaw)] ou une liste de poses [[...], [...]]
                        if (!a.isEmpty() && a.get(0).isJsonPrimitive()) {
                            Pose p = Pose.lire(a);
                            if (p != null) l.add(p);
                        } else {
                            for (JsonElement pe : a) {
                                Pose p = Pose.lire(pe);
                                if (p != null) l.add(p);
                            }
                        }
                    }
                    points.put(e.getKey(), Collections.unmodifiableList(l));
                }
            }
            return new Carte(id, nomFr, nomEn, type, dim, taille, bornes, Collections.unmodifiableMap(points), o);
        } catch (RuntimeException ex) {
            HeralizClashWar.LOG.warn("[matchs] carte {} illisible : {}", idParDefaut, ex.toString());
            return null;
        }
    }
}
