package fr.heralisz.matchs.zombies.client.rendu;

import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Modeles « zombies v2 » ({@link ModeleZb}) charges depuis {@code assets/<espace>/zbmodeles/*.json} a chaque
 * rechargement des ressources (F3+T compris), et jeux de types de rendu par texture ({@link Tex}). Un modele absent ou
 * illisible est journalise une fois et simplement non dessine (jamais de plantage).
 */
public final class ModelesZb {
    private ModelesZb() {
    }

    private static volatile Map<String, ModeleZb> modeles = Map.of();
    private static final Map<ResourceLocation, Tex> TEX = new HashMap<>();

    /** Modele par nom de fichier (sans extension), ou null. */
    @Nullable
    public static ModeleZb get(String nom) {
        return modeles.get(nom);
    }

    public static int nombre() {
        return modeles.size();
    }

    /** Types de rendu d'une texture : base (decoupe sans elimination des faces), lueur additive, verre translucide. */
    public record Tex(RenderType base, RenderType lueur, RenderType verre) {
    }

    /** Types de rendu pour la texture {@code png} (et {@code <png>_lueur.png}) ; mis en cache. */
    public static Tex tex(ResourceLocation png) {
        Tex t = TEX.get(png);
        if (t == null) {
            String p = png.getPath();
            ResourceLocation lueur = new ResourceLocation(png.getNamespace(), p.substring(0, p.length() - 4) + "_lueur.png");
            t = new Tex(RenderType.entityCutoutNoCull(png), ZbTypesRendu.lueur(lueur), RenderType.entityTranslucent(png));
            TEX.put(png, t);
        }
        return t;
    }

    /** Modeles de bloc {@code zbquads} aussi charges comme modeles zombies (groupe unique {@code corps}). */
    private static final String[] CORPS_REDESSINES = {"zb_pap_rideau_corps"};

    /**
     * Convertit un modele de bloc {@code zbquads} (23 flottants par quad, texture de l'atlas des blocs) en modele zombies v2
     * (24 flottants, couche de base, texture lue directement) ; normale vers le haut : la lumiere est peinte dans la texture.
     */
    private static JsonObject corps(String nom, JsonObject bloc) {
        com.google.gson.JsonArray q = GsonHelper.getAsJsonArray(bloc, "quads");
        com.google.gson.JsonArray sortie = new com.google.gson.JsonArray();
        for (int i = 0; i + 23 <= q.size(); i += 23) {
            for (int k = 0; k < 20; k++) sortie.add(q.get(i + k));
            sortie.add(0);
            sortie.add(1);
            sortie.add(0);
            sortie.add(0);
        }
        String tex = GsonHelper.getAsJsonObject(bloc, "textures").get("texture").getAsString();
        String chemin = tex.substring(tex.indexOf(':') + 1);
        JsonObject g = new JsonObject();
        g.addProperty("nom", "corps");
        g.add("q", sortie);
        com.google.gson.JsonArray gs = new com.google.gson.JsonArray();
        gs.add(g);
        JsonObject o = new JsonObject();
        o.addProperty("nom", nom);
        o.addProperty("texture", "textures/" + chemin);
        o.add("groupes", gs);
        return o;
    }

    /** Chargeur branche par {@code RegisterClientReloadListenersEvent} (voir ZbClientSetup). */
    public static final class Chargeur extends SimplePreparableReloadListener<Map<String, ModeleZb>> {
        @Override
        protected Map<String, ModeleZb> prepare(ResourceManager rm, ProfilerFiller profil) {
            Map<String, ModeleZb> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : rm.listResources("zbmodeles", rl -> rl.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation rl = e.getKey();
                String nom = rl.getPath().substring("zbmodeles/".length(), rl.getPath().length() - 5);
                try (Reader r = e.getValue().openAsReader()) {
                    JsonObject o = GsonHelper.parse(r);
                    out.put(nom, ModeleZb.lire(nom, rl.getNamespace(), o));
                } catch (Exception ex) {
                    HeraliszMatchs.LOG.warn("[zombies] modele {} illisible : {}", rl, ex.toString());
                }
            }
            // corps cuits redessines par un rendu de bloc-entite (rideau du Pack-a-Punch, mis a l'echelle de l'ouverture)
            for (String nom : CORPS_REDESSINES) {
                ResourceLocation rl = HeraliszMatchs.id("models/block/" + nom + ".json");
                try {
                    var res = rm.getResource(rl);
                    if (res.isEmpty()) continue;
                    try (Reader r = res.get().openAsReader()) {
                        out.put(nom, ModeleZb.lire(nom, HeraliszMatchs.MODID, corps(nom, GsonHelper.parse(r))));
                    }
                } catch (Exception ex) {
                    HeraliszMatchs.LOG.warn("[zombies] corps {} illisible : {}", rl, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<String, ModeleZb> charges, ResourceManager rm, ProfilerFiller profil) {
            modeles = Map.copyOf(charges);
            HeraliszMatchs.LOG.info("[zombies] {} modeles zombies v2 charges", charges.size());
        }
    }
}
