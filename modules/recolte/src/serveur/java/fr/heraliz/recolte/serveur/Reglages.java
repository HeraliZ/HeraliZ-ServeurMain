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
import net.minecraft.util.RandomSource;
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
    // 18/11 (retour du propriétaire : « pas assez d'arbres et de minerais, je n'en ai croisé aucun ») : 4 fois plus, plus
    // serrés, qui reviennent 2 fois plus vite (avant : 30, 24, 10, 4 ; 90, 120, 240, 480 min ; écarts 50, 60, 150, 300)
    // 18/11 au soir (« ça donne beaucoup trop ; 3 fois plus de ressources mais qui donnent moins », pour que monter sa base
    // reste à la mesure des explosifs) : 3 fois plus (avant : 120, 90, 40, 14 ; écarts du métal et du titane 80, 160), et
    // plus de places dans la zone de neige ({@link Neige}) ; matière tirée entre deux bornes (choix du propriétaire : pierre
    // 10 à 14, métal 7 à 12, titane 4 à 8 ; arbre 10 ; avant : 30, 24, 30, 16 ; chute 6), meilleure avec l'outil légendaire
    private static final int[] A_LA_FOIS = {360, 270, 120, 42};
    private static final int[] MATIERE_MIN = {10, 10, 7, 4};
    private static final int[] MATIERE_MAX = {10, 14, 12, 8};
    private static final int[] CHUTE = {2, 0, 0, 0};
    private static final int[] COUPS = {12, 16, 20, 24};
    private static final int[] COUPS_LEGENDAIRE = {7, 10, 12, 14};
    private static final int[] REAPPARITION_MINUTES = {45, 60, 120, 240};
    private static final int[] ECART = {30, 35, 60, 90};

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

    /**
     * Barème d'une sorte de ressource. Matière : entre {@code matiereMin} et {@code matiereMax}, tirée au premier coup
     * ({@link #tirer}).
     */
    public record Ressource(int aLaFois, int matiereMin, int matiereMax, int chute, int coups, int coupsLegendaire,
                            int reapparitionMinutes, int ecart) {
        /** Coups pour la vider avec l'outil tenu (la hache-pioche de survie en demande moins). */
        public int coups(boolean legendaire) {
            return Math.max(1, legendaire ? coupsLegendaire : coups);
        }

        /**
         * Matière que donnera une ressource, tirée à son premier coup : au hasard entre les deux bornes ; avec l'outil
         * légendaire (la hache-pioche de survie), deux tirages et le meilleur gardé (18/11, choix du propriétaire : « de
         * meilleures chances avec la meilleure hache ou pioche, et plus vite »).
         */
        public int tirer(RandomSource rnd, boolean legendaire) {
            int ecartBornes = Math.max(0, matiereMax - matiereMin);
            int n = matiereMin + rnd.nextInt(ecartBornes + 1);
            return legendaire ? Math.max(n, matiereMin + rnd.nextInt(ecartBornes + 1)) : n;
        }

        /** Bûches de la chute d'un arbre qui donne {@code total} en tout (le reste vient des coups). */
        public int chute(int total) {
            return Math.min(chute, Math.max(0, total));
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
    /**
     * 18/11 : part des tirages faits autour d'un joueur sur la carte (0 à 1), dans un anneau de {@code distance_joueurs} +
     * 30 à {@code rayon_joueurs} blocs : les ressources apparaissent là où l'on joue (avant : partout dans la bordure, mer
     * comprise, à attendre qu'un joueur passe).
     */
    public final double presJoueurs;
    public final int rayonJoueurs;
    /** Rectangle {x1, z1, x2, z2} où tirer les places (null : toute la bordure du monde). */
    @Nullable
    public final int[] zone;
    /** Zone de neige : une part des places y est tirée, et ses ressources donnent plus (null : pas de zone). */
    @Nullable
    public final Neige neige;
    public final Filtre sols, naturels;

    private Reglages(JsonObject o) {
        cadenceMs = entier(o, "cadence_ms", 1200, 200, 10_000);
        portee = reel(o, "portee", 3.0, 1.0, 6.0);
        margePortee = reel(o, "marge_portee", 0.4, 0.0, 2.0);
        soucheMinutes = entier(o, "souche_minutes", 10, 0, 1440);
        JsonObject res = objet(o, "ressources");
        for (int i = 0; i < SORTES.length; i++) {
            JsonObject r = objet(res, SORTES[i]);
            int[] matiere = bornes(r, "matiere", MATIERE_MIN[i], MATIERE_MAX[i], 0, 10_000);
            ressources[i] = new Ressource(entier(r, "a_la_fois", A_LA_FOIS[i], 0, 500), matiere[0], matiere[1],
                    i == ARBRE ? entier(r, "chute", CHUTE[i], 0, matiere[0]) : 0,
                    entier(r, "coups", COUPS[i], 1, 1000), entier(r, "coups_legendaire", COUPS_LEGENDAIRE[i], 1, 1000),
                    entier(r, "reapparition_minutes", REAPPARITION_MINUTES[i], 0, 100_000), entier(r, "ecart", ECART[i], 0, 5000));
        }
        JsonObject a = objet(o, "apparition");
        ResourceLocation m = ResourceLocation.tryParse(texte(a, "monde", "minecraft:overworld"));
        monde = m == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, m);
        distanceJoueurs = entier(a, "distance_joueurs", 70, 0, 160);
        loinDuSpawn = entier(a, "loin_du_spawn", 200, 0, 5000);
        abordsVilles = entier(a, "abords_villes", 40, 0, 1000);
        margeVip = entier(a, "marge_vip", 16, 0, 500);
        margeBordure = entier(a, "marge_bordure", 64, 0, 5000);
        ecartAutres = entier(a, "ecart_autres", 12, 0, 500);
        ecartSol = entier(a, "ecart_sol", 1, 0, 3);
        eau = entier(a, "eau", 3, 0, 8);
        nonNaturels = entier(a, "non_naturels", 6, 0, 8);
        troncsBases = entier(a, "troncons_bases", 2, 0, 3);
        attenteMaxMinutes = entier(a, "attente_max_minutes", 20, 5, 100_000);
        essais = entier(a, "essais", 40, 1, 500);
        presJoueurs = reel(a, "pres_joueurs", 0.7, 0.0, 1.0);
        rayonJoueurs = entier(a, "rayon_joueurs", 450, 120, 3000);
        zone = zone(a);
        neige = Neige.lire(objet(o, "neige"));
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

    /**
     * La zone de neige (18/11, choix du propriétaire : « les arbres et les ressources doivent apparaître en plus grand
     * nombre dans la zone de neige ; c'est là qu'on va en priorité pour farmer ») : contour en points {x, z} (l'Île
     * arctique de config/crac/zones_construction.json), {@code part} des places de chaque sorte tirées dedans (0 à 1). Ses
     * ressources ne donnent pas plus qu'ailleurs (« juste plus de points d'apparition : on cherche moins, on mine plus,
     * donc plus de risque »).
     */
    public record Neige(int[] xs, int[] zs, int minX, int minZ, int maxX, int maxZ, double part) {
        /** Le point (x, z) est-il dans le contour (rectangle d'abord, puis rayon qui traverse le contour) ? */
        public boolean contient(double x, double z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                return false;
            }
            boolean dedans = false;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                if ((zs[i] > z) != (zs[j] > z) && x < (double) (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                    dedans = !dedans;
                }
            }
            return dedans;
        }

        /** Lue dans « neige » de recolte.json ; null sans contour d'au moins 3 points. */
        @Nullable
        static Neige lire(JsonObject o) {
            JsonElement e = o.get("contour");
            if (e == null || !e.isJsonArray()) {
                return null;
            }
            List<int[]> points = new ArrayList<>();
            for (JsonElement p : e.getAsJsonArray()) {
                if (p.isJsonArray() && p.getAsJsonArray().size() == 2 && p.getAsJsonArray().get(0).isJsonPrimitive()
                        && p.getAsJsonArray().get(1).isJsonPrimitive()) {
                    points.add(new int[]{(int) Math.round(p.getAsJsonArray().get(0).getAsDouble()),
                            (int) Math.round(p.getAsJsonArray().get(1).getAsDouble())});
                }
            }
            if (points.size() < 3) {
                return null;
            }
            int n = points.size();
            int[] xs = new int[n], zs = new int[n];
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                xs[i] = points.get(i)[0];
                zs[i] = points.get(i)[1];
                x0 = Math.min(x0, xs[i]);
                z0 = Math.min(z0, zs[i]);
                x1 = Math.max(x1, xs[i]);
                z1 = Math.max(z1, zs[i]);
            }
            return new Neige(xs, zs, x0, z0, x1, z1, reel(o, "part", 0.4, 0.0, 1.0));
        }
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

    /**
     * Deux bornes {min, max} : un nombre (les deux bornes égales) ou [min, max] ; absent ou illisible : les valeurs par
     * défaut ; bornes ramenées dans [bas, haut], et min jamais au-dessus de max.
     */
    private static int[] bornes(JsonObject o, String cle, int defMin, int defMax, int bas, int haut) {
        JsonElement e = o.get(cle);
        int a = defMin, b = defMax;
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            a = b = (int) Math.round(e.getAsDouble());
        } else if (e != null && e.isJsonArray() && e.getAsJsonArray().size() == 2 && e.getAsJsonArray().get(0).isJsonPrimitive()
                && e.getAsJsonArray().get(0).getAsJsonPrimitive().isNumber() && e.getAsJsonArray().get(1).isJsonPrimitive()
                && e.getAsJsonArray().get(1).getAsJsonPrimitive().isNumber()) {
            a = (int) Math.round(e.getAsJsonArray().get(0).getAsDouble());
            b = (int) Math.round(e.getAsJsonArray().get(1).getAsDouble());
        }
        a = Math.max(bas, Math.min(haut, a));
        b = Math.max(bas, Math.min(haut, b));
        return new int[]{Math.min(a, b), Math.max(a, b)};
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
