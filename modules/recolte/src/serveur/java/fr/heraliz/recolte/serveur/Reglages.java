package fr.heraliz.recolte.serveur;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.recolte.HeralizRecolte;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Barème et réglages de la récolte (A_FAIRE §6), lus dans {@code config/heraliz_recolte/recolte.json} (dans le dépôt :
 * {@code serveur/config/heraliz_recolte/recolte.json}) : au démarrage du serveur, par {@code /recharger recolte}, et tout
 * seul quand le fichier change (date relevée sur un fil à part par {@link DatesFichiers} : le fil du serveur ne touche le
 * disque que si le fichier a changé). Un fichier illisible ne casse rien : les réglages en cours restent, l'erreur va au
 * journal. Une valeur absente prend celle du barème de {@code maquettes/recolte/PROPOSITION.md}, une valeur hors bornes
 * sa borne. Lecture des réglages en cours : {@link #get()} (fil du serveur).
 * <p>La hache-pioche de survie (légendaire) « fait gagner du temps, pas plus de ressources » : elle demande moins de
 * coups ({@code coups_legendaire}), mais la matière donnée est la même ({@code matiere}), au prorata du travail fait.
 */
public final class Reglages {
    /** Les sortes de ressources, dans l'ordre : clés de « ressources » dans recolte.json, types de /recolte placer. */
    public static final int ARBRE = 0, PIERRE = 1, METAL = 2, RARE = 3;
    public static final String[] SORTES = {"arbre", "pierre", "metal", "rare"};

    // barème par défaut (PROPOSITION.md §2 et §4), dans l'ordre des sortes
    private static final int[] A_LA_FOIS = {30, 24, 10, 4};
    private static final int[] MATIERE = {30, 24, 30, 16};
    private static final int[] CHUTE = {6, 0, 0, 0};
    private static final int[] COUPS = {12, 16, 20, 24};
    private static final int[] COUPS_LEGENDAIRE = {7, 10, 12, 14};
    private static final int[] REAPPARITION_MINUTES = {90, 120, 240, 480};
    private static final int[] ECART = {50, 60, 150, 300};

    /** Sols sur lesquels une ressource peut être posée (herbe, terre, pierre, sable, neige). */
    private static final List<String> SOLS = List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
            "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:stone", "minecraft:granite",
            "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:calcite", "minecraft:sand",
            "minecraft:red_sand", "minecraft:snow_block", "minecraft:snow");
    /** Blocs naturels admis autour (en plus des sols) : terrain, végétation, arbres de la carte. */
    private static final List<String> NATURELS = List.of("#minecraft:dirt", "#minecraft:sand", "#minecraft:base_stone_overworld",
            "#minecraft:snow", "#minecraft:ice", "#minecraft:leaves", "#minecraft:logs", "#minecraft:flowers", "#minecraft:saplings",
            "#minecraft:replaceable", "#minecraft:replaceable_by_trees", "minecraft:clay", "minecraft:mud", "minecraft:mycelium",
            "minecraft:moss_carpet", "minecraft:sweet_berry_bush", "minecraft:sugar_cane", "minecraft:cactus", "minecraft:bamboo",
            "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:lily_pad", "minecraft:pumpkin", "minecraft:melon",
            "minecraft:azalea", "minecraft:flowering_azalea", "minecraft:big_dripleaf", "minecraft:small_dripleaf",
            "minecraft:pointed_dripstone", "minecraft:dripstone_block");

    /** Barème d'une sorte de ressource. */
    public record Ressource(int aLaFois, int matiere, int chute, int coups, int coupsLegendaire, int reapparitionMinutes, int ecart) {
        /** Coups pour la vider avec l'outil tenu (la hache-pioche de survie en demande moins). */
        public int coups(boolean legendaire) {
            return Math.max(1, legendaire ? coupsLegendaire : coups);
        }

        /** Matière donnée par les coups (pour un arbre, le reste est donné quand il tombe). */
        public int matiereCoups() {
            return Math.max(0, matiere - chute);
        }

        public long reapparitionMs() {
            return reapparitionMinutes * 60_000L;
        }
    }

    private final Ressource[] ressources = new Ressource[SORTES.length];
    /** Un coup compté toutes les {@code cadence_ms} au plus, par joueur. */
    public final long cadenceMs;
    /** Portée d'un coup (des yeux au point le plus proche de la ressource), plus la marge du décalage du réseau. */
    public final double portee, margePortee;
    /** Minutes où la souche d'un arbre abattu reste. */
    public final int soucheMinutes;
    /** Monde des ressources (un seul : la carte). */
    public final ResourceKey<Level> monde;
    public final int distanceJoueurs, loinDuSpawn, abordsVilles, margeVip, margeBordure, ecartAutres, ecartSol, eau, nonNaturels,
            troncsBases, attenteMaxMinutes, essais;
    /** Rectangle {x1, z1, x2, z2} où tirer les places (null : toute la bordure du monde). */
    @Nullable
    public final int[] zone;
    public final Filtre sols, naturels;

    private Reglages(JsonObject o) {
        cadenceMs = entier(o, "cadence_ms", 1200, 200, 10_000);
        portee = reel(o, "portee", 3.0, 1.0, 6.0);
        margePortee = reel(o, "marge_portee", 0.4, 0.0, 2.0);
        soucheMinutes = entier(o, "souche_minutes", 10, 0, 1440);
        JsonObject res = objet(o, "ressources");
        for (int i = 0; i < SORTES.length; i++) {
            JsonObject r = objet(res, SORTES[i]);
            int matiere = entier(r, "matiere", MATIERE[i], 0, 10_000);
            ressources[i] = new Ressource(entier(r, "a_la_fois", A_LA_FOIS[i], 0, 500), matiere,
                    i == ARBRE ? entier(r, "chute", CHUTE[i], 0, matiere) : 0,
                    entier(r, "coups", COUPS[i], 1, 1000), entier(r, "coups_legendaire", COUPS_LEGENDAIRE[i], 1, 1000),
                    entier(r, "reapparition_minutes", REAPPARITION_MINUTES[i], 0, 100_000), entier(r, "ecart", ECART[i], 0, 5000));
        }
        JsonObject a = objet(o, "apparition");
        ResourceLocation m = ResourceLocation.tryParse(texte(a, "monde", "minecraft:overworld"));
        monde = m == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, m);
        distanceJoueurs = entier(a, "distance_joueurs", 48, 0, 160);
        loinDuSpawn = entier(a, "loin_du_spawn", 200, 0, 5000);
        abordsVilles = entier(a, "abords_villes", 40, 0, 1000);
        margeVip = entier(a, "marge_vip", 16, 0, 500);
        margeBordure = entier(a, "marge_bordure", 64, 0, 5000);
        ecartAutres = entier(a, "ecart_autres", 12, 0, 500);
        ecartSol = entier(a, "ecart_sol", 1, 0, 3);
        eau = entier(a, "eau", 3, 0, 8);
        nonNaturels = entier(a, "non_naturels", 6, 0, 8);
        troncsBases = entier(a, "troncons_bases", 2, 0, 3);
        attenteMaxMinutes = entier(a, "attente_max_minutes", 120, 5, 100_000);
        essais = entier(a, "essais", 40, 1, 500);
        zone = zone(a);
        sols = new Filtre(liste(a, "sols_naturels", SOLS));
        naturels = new Filtre(liste(a, "naturels", NATURELS));
    }

    /** Barème d'une sorte ({@link #ARBRE}, {@link #PIERRE}, {@link #METAL}, {@link #RARE}). */
    public Ressource ressource(int sorte) {
        return ressources[Math.max(0, Math.min(SORTES.length - 1, sorte))];
    }

    public long attenteMaxMs() {
        return attenteMaxMinutes * 60_000L;
    }

    // ------------------------------------------------------------------ réglages en cours
    private static volatile Reglages actifs = new Reglages(new JsonObject());
    @Nullable
    private static Path fichier;
    /** Date du fichier déjà lue (0 : absent) ; date d'un fichier illisible (pas relu tant qu'il ne change pas). */
    private static long dateConnue = Long.MIN_VALUE, dateRatee = Long.MIN_VALUE;
    @Nullable
    private static String derniereErreur;

    /** Réglages en cours (barème par défaut tant que le fichier n'est pas lu). */
    public static Reglages get() {
        return actifs;
    }

    static Path fichier() {
        Path f = fichier;
        if (f == null) {
            fichier = f = FMLPaths.CONFIGDIR.get().resolve("heraliz_recolte").resolve("recolte.json");
        }
        return f;
    }

    /** Raison de la dernière lecture ratée (pour la réponse au staff). */
    @Nullable
    static String derniereErreur() {
        return derniereErreur;
    }

    /**
     * Lit le fichier maintenant (démarrage du serveur, {@code /recharger recolte}). Rend 1 s'il est lu, 0 s'il est absent
     * (barème par défaut), -1 s'il est illisible (réglages en cours gardés, raison : {@link #derniereErreur()}).
     */
    static int charger() {
        Path f = fichier();
        long date = DatesFichiers.relever(f);
        if (date == 0L) {
            actifs = new Reglages(new JsonObject());
            dateConnue = 0L;
            derniereErreur = null;
            HeralizRecolte.LOG.warn("[recolte] {} absent : bareme par defaut", f);
            return 0;
        }
        try {
            JsonElement racine = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (!racine.isJsonObject()) {
                throw new IllegalStateException("un objet JSON { ... } est attendu");
            }
            Reglages r = new Reglages(racine.getAsJsonObject());
            actifs = r;
            dateConnue = date;
            derniereErreur = null;
            HeralizRecolte.LOG.info("[recolte] reglages lus ({}) : {} arbres, {} pierre, {} metal, {} rare a la fois", f.getFileName(),
                    r.ressource(ARBRE).aLaFois(), r.ressource(PIERRE).aLaFois(), r.ressource(METAL).aLaFois(), r.ressource(RARE).aLaFois());
            return 1;
        } catch (IOException | RuntimeException e) {
            dateRatee = date;
            derniereErreur = e.getMessage() != null ? e.getMessage() : e.toString();
            HeralizRecolte.LOG.error("[recolte] {} illisible, reglages precedents gardes : {}", f, e.toString());
            return -1;
        }
    }

    /**
     * Toutes les 5 s (fil du serveur) : relit le fichier si sa date a changé (date relevée à part : aucun accès au disque
     * ici sinon). Vrai si de nouveaux réglages sont en place.
     */
    static boolean surveiller() {
        long date = DatesFichiers.date(fichier());
        if (date == dateConnue || date == dateRatee) {
            return false;
        }
        return charger() >= 0;
    }

    // ------------------------------------------------------------------ lecture du JSON
    private static JsonObject objet(JsonObject o, String cle) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static boolean nombre(JsonObject o, String cle) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static int entier(JsonObject o, String cle, int defaut, int min, int max) {
        if (!nombre(o, cle)) {
            return defaut;
        }
        long v = Math.round(o.get(cle).getAsDouble());
        return (int) Math.max(min, Math.min(max, v));
    }

    private static double reel(JsonObject o, String cle, double defaut, double min, double max) {
        if (!nombre(o, cle)) {
            return defaut;
        }
        double v = o.get(cle).getAsDouble();
        return Double.isNaN(v) ? defaut : Math.max(min, Math.min(max, v));
    }

    private static String texte(JsonObject o, String cle, String defaut) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString().trim() : defaut;
    }

    private static List<String> liste(JsonObject o, String cle, List<String> defaut) {
        JsonElement e = o.get(cle);
        if (e == null || !e.isJsonArray()) {
            return defaut;
        }
        List<String> l = new ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString()) {
                l.add(x.getAsString());
            }
        }
        return l;
    }

    @Nullable
    private static int[] zone(JsonObject o) {
        JsonElement e = o.get("zone");
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 4) {
            return null;
        }
        int[] z = new int[4];
        for (int i = 0; i < 4; i++) {
            JsonElement x = a.get(i);
            if (!x.isJsonPrimitive() || !x.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            z[i] = (int) Math.round(x.getAsDouble());
        }
        return new int[]{Math.min(z[0], z[2]), Math.min(z[1], z[3]), Math.max(z[0], z[2]), Math.max(z[1], z[3])};
    }

    /**
     * Liste de blocs du réglage : identifiants ({@code minecraft:grass_block}) et étiquettes de blocs
     * ({@code #minecraft:leaves}). Résolue à la première lecture (registres et étiquettes chargés), jamais avant.
     */
    public static final class Filtre {
        private final List<String> noms;
        @Nullable
        private Set<Block> blocs;
        private List<TagKey<Block>> etiquettes = List.of();

        Filtre(List<String> noms) {
            this.noms = List.copyOf(noms);
        }

        public boolean contient(BlockState etat) {
            Set<Block> b = blocs;
            if (b == null) {
                b = resoudre();
            }
            if (b.contains(etat.getBlock())) {
                return true;
            }
            for (TagKey<Block> t : etiquettes) {
                if (etat.is(t)) {
                    return true;
                }
            }
            return false;
        }

        private Set<Block> resoudre() {
            Set<Block> b = Collections.newSetFromMap(new IdentityHashMap<>());
            List<TagKey<Block>> t = new ArrayList<>();
            for (String n : noms) {
                String s = n.trim();
                boolean etiquette = s.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(etiquette ? s.substring(1) : s);
                if (id == null) {
                    HeralizRecolte.LOG.warn("[recolte] recolte.json : nom de bloc illisible « {} » ignore", s);
                } else if (etiquette) {
                    t.add(TagKey.create(Registries.BLOCK, id));
                } else if (ForgeRegistries.BLOCKS.containsKey(id)) {
                    b.add(ForgeRegistries.BLOCKS.getValue(id));
                } else {
                    HeralizRecolte.LOG.warn("[recolte] recolte.json : bloc inconnu « {} » ignore", s);
                }
            }
            etiquettes = List.copyOf(t);
            blocs = b;
            return b;
        }
    }
}
