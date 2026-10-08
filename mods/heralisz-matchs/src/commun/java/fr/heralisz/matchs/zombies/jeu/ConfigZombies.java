package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.pont.Services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Donnees de jeu du mode zombies ({@code data/heralisz_matchs/zombies/*.json}, rechargees avec {@code /reload}) :
 * <ul>
 *     <li>{@code regles.json} : garde-fou, points, couteau, portees d'achat, Pack-a-Punch, materiaux, arme de la carte,
 *     easter egg, apparitions (voir les accesseurs ; toute valeur absente garde son defaut) ;</li>
 *     <li>{@code boite.json} : table de la boite mystere par raretes ({@link TableBoite}) ;</li>
 *     <li>{@code recettes.json} : recettes des etablis ({@link Recettes}) ;</li>
 *     <li>{@code objectifs.json} : ordre de la chaine d'objectifs ({@code chaine}, {@code apres}) ;</li>
 *     <li>{@code equilibrage_armes.json} (0.2.3) : multiplicateur de degats par arme TaCZ en zombies
 *     ({@link #multiplicateurArme}), genere par {@code tools/matchs/zombies/gen_equilibrage_armes.py}.</li>
 * </ul>
 * Fichier absent ou illisible = valeurs par defaut (journal). Fil serveur (lecture) ; ecriture au rechargement.
 */
public final class ConfigZombies {
    private ConfigZombies() {
    }

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static volatile JsonObject regles = new JsonObject();
    @Nullable
    private static volatile JsonObject boite;
    @Nullable
    private static volatile JsonObject recettes;
    private static volatile JsonObject objectifs = new JsonObject();
    /** Multiplicateur de degats par arme (gunId -> x), table vide = x1 partout. */
    private static volatile Map<String, Float> equilibrage = Map.of();

    /** Branche le rechargement des donnees (appele par {@link ZombiesJeu#construire}). */
    static void rechargement(AddReloadListenerEvent e) {
        e.addListener(new SimpleJsonResourceReloadListener(GSON, "zombies") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> fichiers, ResourceManager rm, ProfilerFiller pf) {
                JsonObject r = new JsonObject(), o = new JsonObject();
                JsonObject b = null, re = null;
                Map<String, Float> eq = new java.util.HashMap<>();
                for (Map.Entry<ResourceLocation, JsonElement> f : fichiers.entrySet()) {
                    if (!HeraliszMatchs.MODID.equals(f.getKey().getNamespace()) || !f.getValue().isJsonObject()) continue;
                    switch (f.getKey().getPath()) {
                        case "regles" -> r = f.getValue().getAsJsonObject();
                        case "boite" -> b = f.getValue().getAsJsonObject();
                        case "recettes" -> re = f.getValue().getAsJsonObject();
                        case "objectifs" -> o = f.getValue().getAsJsonObject();
                        case "equilibrage_armes" -> lireEquilibrage(f.getValue().getAsJsonObject(), eq);
                        default -> HeraliszMatchs.LOG.debug("[zombies] donnees ignorees : {}", f.getKey());
                    }
                }
                regles = r;
                boite = b;
                recettes = re;
                objectifs = o;
                equilibrage = Map.copyOf(eq);
                Services.Moteur m = Services.MOTEUR;   // tables de la boîte et recettes (serveur)
                if (m != null) m.zombiesInvaliderTables();
                HeraliszMatchs.LOG.info("[zombies] donnees de jeu : regles {}, boite {}, recettes {}, objectifs {}, equilibrage {} armes", r.size() > 0,
                        b != null, re != null, o.size() > 0, eq.size());
            }
        });
    }

    /** Lit {@code armes.<gunId>.mult} (bornes 0,05 a 5) ; une entree illisible est ignoree (x1). */
    private static void lireEquilibrage(JsonObject racine, Map<String, Float> out) {
        JsonElement a = racine.get("armes");
        if (!(a instanceof JsonObject armes)) return;
        for (Map.Entry<String, JsonElement> e : armes.entrySet()) {
            try {
                JsonElement v = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject().get("mult") : e.getValue();
                if (v == null || !v.isJsonPrimitive()) continue;
                float m = v.getAsFloat();
                if (m > 0 && Math.abs(m - 1f) > 1e-4f) out.put(e.getKey(), Math.max(0.05f, Math.min(5f, m)));
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Multiplicateur de degats de l'arme en zombies (equilibrage par palier de rarete, 0.2.3), 1 si l'arme n'est pas dans
     * la table. Applique par {@code PartieZombies.surTir} avant le Pack-a-Punch. Sans allocation.
     */
    static float multiplicateurArme(@Nullable String gunId) {
        if (gunId == null) return 1f;
        Float m = equilibrage.get(gunId);
        return m == null ? 1f : m;
    }

    // ------------------------------------------------------------------ acces bruts
    /** Contenu de {@code boite.json}, ou null (table par defaut). */
    @Nullable
    static JsonObject boite() {
        return boite;
    }

    /** Contenu de {@code recettes.json}, ou null (recettes par defaut). */
    @Nullable
    static JsonObject recettes() {
        return recettes;
    }

    // ------------------------------------------------------------------ regles
    static double reel(String section, String cle, double defaut) {
        JsonObject s = section(section);
        try {
            return s != null && s.has(cle) ? s.get(cle).getAsDouble() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    static int entier(String section, String cle, int defaut) {
        return (int) Math.round(reel(section, cle, defaut));
    }

    static boolean vrai(String section, String cle, boolean defaut) {
        JsonObject s = section(section);
        try {
            return s != null && s.has(cle) ? s.get(cle).getAsBoolean() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    static String texte(String section, String cle, String defaut) {
        JsonObject s = section(section);
        try {
            return s != null && s.has(cle) && s.get(cle).isJsonPrimitive() ? s.get(cle).getAsString() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    @Nullable
    private static JsonObject section(String nom) {
        JsonElement e = regles.get(nom);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /**
     * Candidats de l'arme unique de la carte, dans l'ordre de preference ({@code regles.arme_carte}) : le premier
     * reellement installe gagne ; la carte n'est consultee que si aucun ne l'est.
     */
    static List<String> armesCarte() {
        List<String> l = new ArrayList<>();
        JsonElement e = regles.get("arme_carte");
        try {
            if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) l.add(x.getAsString());
            else if (e != null && e.isJsonPrimitive()) l.add(e.getAsString());
        } catch (RuntimeException ignored) {
        }
        if (l.isEmpty()) {
            l.add("cis:at_rpg_7_lvd");
            l.add("tacz:minigun");
        }
        return l;
    }

    /**
     * Accessoires de l'arme unique de la carte ({@code regles.arme_carte_accessoires.<gunId>} = {emplacement: id}), vide
     * si aucun. Chacun est verifie contre l'index TaCZ de l'arme au chargement de la table.
     */
    static java.util.Map<String, String> accessoiresArmeCarte(String gunId) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        JsonElement e = regles.get("arme_carte_accessoires");
        try {
            if (e != null && e.isJsonObject() && e.getAsJsonObject().get(gunId) instanceof JsonObject o) {
                for (Map.Entry<String, JsonElement> x : o.entrySet()) m.put(x.getKey().toLowerCase(java.util.Locale.ROOT), x.getValue().getAsString());
            }
        } catch (RuntimeException ignored) {
        }
        return m;
    }

    /** Un niveau de Pack-a-Punch : cout en points, multiplicateur de degats, reserve (en chargeurs, remplie). */
    record NiveauPap(int cout, double degats, int chargeurs) {
    }

    /** Niveaux par defaut (CoD Cold War) : 5 000 / 15 000 / 30 000, degats x2 / x3 / x4, reserve 8 / 10 / 12 chargeurs. */
    private static final List<NiveauPap> NIVEAUX_PAP = List.of(new NiveauPap(5000, 2.0, 8), new NiveauPap(15000, 3.0, 10),
            new NiveauPap(30000, 4.0, 12));

    /**
     * Niveaux du Pack-a-Punch ({@code pack_a_punch.niveaux : [{cout, degats, chargeurs_reserve}]}, 1 a
     * {@link fr.heralisz.matchs.zombies.jeu.api.ZombiesApi#PAP_NIVEAU_MAX}) ; un champ absent reprend la valeur par
     * defaut du niveau. Sans liste : l'ancien format (un seul jeu {@code cout}, {@code multiplicateur_degats},
     * {@code chargeurs_reserve}) donne le niveau 1, les suivants gardent leurs valeurs par defaut.
     */
    static List<NiveauPap> niveauxPap() {
        List<NiveauPap> l = new ArrayList<>();
        JsonObject s = section("pack_a_punch");
        JsonElement e = s == null ? null : s.get("niveaux");
        try {
            if (e instanceof JsonArray a) {
                for (int i = 0; i < a.size() && l.size() < fr.heralisz.matchs.zombies.jeu.api.ZombiesApi.PAP_NIVEAU_MAX; i++) {
                    if (!(a.get(i) instanceof JsonObject o)) continue;
                    NiveauPap d = NIVEAUX_PAP.get(Math.min(l.size(), NIVEAUX_PAP.size() - 1));
                    l.add(new NiveauPap(o.has("cout") ? o.get("cout").getAsInt() : d.cout(), o.has("degats") ? o.get("degats").getAsDouble() : d.degats(),
                            o.has("chargeurs_reserve") ? o.get("chargeurs_reserve").getAsInt() : d.chargeurs()));
                }
            }
        } catch (RuntimeException ex) {
            l.clear();
        }
        if (l.isEmpty()) {
            l.addAll(NIVEAUX_PAP);
            if (s != null && (s.has("cout") || s.has("multiplicateur_degats") || s.has("chargeurs_reserve"))) {
                NiveauPap d = NIVEAUX_PAP.get(0);
                l.set(0, new NiveauPap(entier("pack_a_punch", "cout", d.cout()), reel("pack_a_punch", "multiplicateur_degats", d.degats()),
                        entier("pack_a_punch", "chargeurs_reserve", d.chargeurs())));
            }
        }
        return l;
    }

    /** Vrai si la configuration donne le cout du niveau 1 (sinon la carte peut le donner). */
    static boolean coutPapConfigure() {
        JsonObject s = section("pack_a_punch");
        return s != null && (s.get("niveaux") instanceof JsonArray a && !a.isEmpty() && a.get(0) instanceof JsonObject o && o.has("cout") || s.has("cout"));
    }

    /** Camos heralisz_camo par niveau ({@code pack_a_punch.camos}) ; defaut {@code pap_1}, {@code pap_2}, {@code pap_3}. */
    static List<String> camosPap() {
        List<String> l = new ArrayList<>();
        JsonObject s = section("pack_a_punch");
        try {
            if (s != null && s.get("camos") instanceof JsonArray a) for (JsonElement x : a) l.add(x.isJsonNull() ? "" : x.getAsString());
        } catch (RuntimeException ignored) {
            l.clear();
        }
        if (l.isEmpty()) l.addAll(List.of("pap_1", "pap_2", "pap_3"));
        return l;
    }

    /** Ordre des objectifs ({@code objectifs.chaine}) ; defaut : porte, courant, pack_a_punch, mystere. */
    static List<String> chaineObjectifs() {
        List<String> l = new ArrayList<>();
        JsonElement e = objectifs.get("chaine");
        try {
            if (e instanceof JsonArray a) for (JsonElement x : a) l.add(x.getAsString());
        } catch (RuntimeException ignored) {
        }
        if (l.isEmpty()) l.addAll(List.of("porte", "courant", "pack_a_punch", "mystere"));
        return l;
    }

    /** Objectif affiche une fois la chaine terminee ({@code objectifs.apres}), vide = aucun. */
    static String objectifFinal() {
        try {
            JsonElement e = objectifs.get("apres");
            return e != null && e.isJsonPrimitive() ? e.getAsString() : "survivre";
        } catch (RuntimeException ex) {
            return "survivre";
        }
    }
}
