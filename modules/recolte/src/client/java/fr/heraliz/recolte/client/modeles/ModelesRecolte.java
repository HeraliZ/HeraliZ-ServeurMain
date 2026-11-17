package fr.heraliz.recolte.client.modeles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.recolte.HeralizRecolte;
import fr.heraliz.recolte.entites.Arbre;
import fr.heraliz.recolte.entites.Gisement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Les 17 modèles cuits du mod ({@code assets/heraliz_recolte/modeles/*.json}, A_FAIRE §4), lus une fois dans les
 * ressources du jar joueur et relus avec elles (F3 + T, pack de ressources) : lecture hors du fil de rendu, échange
 * d'un bloc au fil de rendu. Un modèle absent ou illisible est signalé dans le journal et n'est pas dessiné.
 * <p>Textures : la clé {@code texture} des trois outils vise {@code textures/entity/<nom>.png}, qui n'existe pas (leurs
 * textures sont dans {@code textures/item/modele/}, A_FAIRE §4) : une texture {@code textures/entity/} absente est
 * cherchée là, sans retoucher le fichier écrit par l'outil.</p>
 */
public final class ModelesRecolte extends SimplePreparableReloadListener<ModelesRecolte.Lot> {
    public static final ModelesRecolte INSTANCE = new ModelesRecolte();
    /** Outils (identifiants des objets = noms des modèles). */
    public static final String[] OUTILS = {"hache", "pioche", "hache_pioche"};

    private static final String DOSSIER_ENTITES = "textures/entity/", DOSSIER_OUTILS = "textures/item/modele/";

    /** Modèles en service (remplacés d'un bloc à chaque chargement des ressources). */
    private static volatile Lot lot = Lot.VIDE;

    /** Gisements par type puis étape, arbres par espèce, outils par identifiant. */
    record Lot(ModeleCuit[][] gisements, ModeleArbre[] arbres, Map<String, ModeleCuit> outils) {
        static final Lot VIDE = new Lot(new ModeleCuit[Gisement.TYPES.length][Gisement.ETAPES],
                new ModeleArbre[Arbre.ESPECES.length], Map.of());
    }

    private ModelesRecolte() {
    }

    /** Modèle d'un gisement (type 0 à 2, étape 0 à 3) ; null tant qu'il n'est pas lu. */
    @Nullable
    public static ModeleCuit gisement(int type, int etape) {
        ModeleCuit[][] g = lot.gisements();
        return type >= 0 && type < g.length && etape >= 0 && etape < g[type].length ? g[type][etape] : null;
    }

    /** Arbre d'une espèce (0 bouleau, 1 épicéa) ; null tant qu'il n'est pas lu. */
    @Nullable
    public static ModeleArbre arbre(int espece) {
        ModeleArbre[] a = lot.arbres();
        return espece >= 0 && espece < a.length ? a[espece] : null;
    }

    /** Modèle en cubes d'un outil ({@code hache}, {@code pioche}, {@code hache_pioche}) ; null tant qu'il n'est pas lu. */
    @Nullable
    public static ModeleCuit outil(@Nullable String id) {
        return id == null ? null : lot.outils().get(id);
    }

    @Override
    protected Lot prepare(ResourceManager ressources, ProfilerFiller profil) {
        ModeleCuit[][] gisements = new ModeleCuit[Gisement.TYPES.length][Gisement.ETAPES];
        for (int t = 0; t < Gisement.TYPES.length; t++) {
            for (int e = 0; e < Gisement.ETAPES; e++) {
                gisements[t][e] = lire(ressources, "gisement_" + Gisement.TYPES[t] + "_" + (e + 1));
            }
        }
        ModeleArbre[] arbres = new ModeleArbre[Arbre.ESPECES.length];
        for (int i = 0; i < Arbre.ESPECES.length; i++) {
            ModeleCuit m = lire(ressources, "arbre_" + Arbre.ESPECES[i]);
            arbres[i] = m == null ? null : new ModeleArbre(m);
        }
        Map<String, ModeleCuit> outils = new LinkedHashMap<>();
        for (String id : OUTILS) {
            ModeleCuit m = lire(ressources, id);
            if (m != null) {
                outils.put(id, m);
            }
        }
        return new Lot(gisements, arbres, Map.copyOf(outils));
    }

    @Override
    protected void apply(Lot lu, ResourceManager ressources, ProfilerFiller profil) {
        lot = lu;
    }

    @Nullable
    private static ModeleCuit lire(ResourceManager ressources, String nom) {
        ResourceLocation ici = new ResourceLocation(HeralizRecolte.MODID, "modeles/" + nom + ".json");
        Optional<Resource> r = ressources.getResource(ici);
        if (r.isEmpty()) {
            HeralizRecolte.LOG.warn("[heraliz_recolte] modèle cuit absent : {}", ici);
            return null;
        }
        try (Reader lecteur = r.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(lecteur).getAsJsonObject();
            ResourceLocation texture = texture(ressources, json, nom);
            Map<String, float[]> groupes = new LinkedHashMap<>();
            JsonObject gs = json.getAsJsonObject("groupes");
            int ignores = 0;
            for (Map.Entry<String, JsonElement> g : gs.entrySet()) {
                JsonArray quads = g.getValue().getAsJsonArray();
                float[] q = new float[quads.size() * ModeleCuit.PAR_QUAD];
                int o = 0;
                for (JsonElement quad : quads) {
                    JsonArray v = quad.getAsJsonArray();
                    if (v.size() != ModeleCuit.PAR_QUAD) {
                        ignores++;
                        continue;
                    }
                    for (int i = 0; i < ModeleCuit.PAR_QUAD; i++) {
                        q[o++] = v.get(i).getAsFloat();
                    }
                }
                groupes.put(g.getKey(), o == q.length ? q : java.util.Arrays.copyOf(q, o));
            }
            if (ignores > 0) {
                HeralizRecolte.LOG.warn("[heraliz_recolte] {} : {} quadrilatère(s) mal formé(s) ignoré(s)", ici, ignores);
            }
            Map<String, float[]> pivots = new LinkedHashMap<>();
            JsonObject ps = json.has("pivots") && json.get("pivots").isJsonObject() ? json.getAsJsonObject("pivots") : null;
            if (ps != null) {
                for (Map.Entry<String, JsonElement> p : ps.entrySet()) {
                    JsonArray a = p.getValue().getAsJsonArray();
                    if (a.size() >= 3) {
                        pivots.put(p.getKey(), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
                    }
                }
            }
            return new ModeleCuit(nom, texture, groupes, pivots);
        } catch (Exception e) {
            HeralizRecolte.LOG.warn("[heraliz_recolte] modèle cuit illisible : {} ({})", ici, e.toString());
            return null;
        }
    }

    /**
     * Texture du modèle (clé {@code texture}) ; une texture de {@code textures/entity/} absente est cherchée dans
     * {@code textures/item/modele/} (outils, A_FAIRE §4).
     */
    private static ResourceLocation texture(ResourceManager ressources, JsonObject json, String nom) {
        ResourceLocation t = json.has("texture") ? ResourceLocation.tryParse(json.get("texture").getAsString()) : null;
        if (t == null) {
            t = new ResourceLocation(HeralizRecolte.MODID, DOSSIER_ENTITES + nom + ".png");
        }
        if (ressources.getResource(t).isEmpty() && t.getPath().startsWith(DOSSIER_ENTITES)) {
            ResourceLocation outil = new ResourceLocation(t.getNamespace(),
                    DOSSIER_OUTILS + t.getPath().substring(DOSSIER_ENTITES.length()));
            if (ressources.getResource(outil).isPresent()) {
                return outil;
            }
            HeralizRecolte.LOG.warn("[heraliz_recolte] texture absente pour le modèle {} : {}", nom, t);
        }
        return t;
    }
}
