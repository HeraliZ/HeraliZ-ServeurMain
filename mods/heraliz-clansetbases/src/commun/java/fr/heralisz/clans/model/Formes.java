package fr.heralisz.clans.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogue commun (serveur et client) des 64 formes de calque de la banniere v2. Les ids sont fixes : 0..31 =
 * emblemes (memes ids que l'ancienne planche, migration directe), 32..63 = formes geometriques. Les cles viennent
 * de la liste codee en dur ci-dessous (contrat BANNIERE_V2_SPEC §2) ; le fichier
 * {@code assets/heralisz_clans/banniere/formes.json} du jar, s'il existe, ne fait qu'affiner les reglages
 * (garder les proportions, taille par defaut). Sans fichier, des valeurs de secours raisonnables s'appliquent.
 * <p>
 * Lecture paresseuse et unique (classe porteuse), aucune allocation dans les accesseurs.
 */
public final class Formes {
    private Formes() {
    }

    /** Journal propre (n'initialise pas la classe principale du mod : le modele reste testable hors jeu). */
    private static final Logger LOG = LoggerFactory.getLogger("heralisz_clans");

    /** Nombre total de formes (ids 0..63). */
    public static final int NOMBRE = 64;
    /** Nombre d'emblemes (ids 0..31). */
    public static final int EMBLEMES = 32;
    /** Chemin du catalogue dans le jar. */
    public static final String CHEMIN = "/assets/heralisz_clans/banniere/formes.json";

    /** Liste de secours : les 64 cles dans l'ordre des ids (contrat, ne jamais reordonner). */
    private static final String[] CLES = {
            // emblemes 0..31
            "loup", "corbeau", "crane", "crane_casque", "masque_gaz", "dragon", "kraken", "serpent",
            "ours", "cerf", "aigle", "faucon", "requin", "scorpion", "araignee", "lion",
            "biohazard", "radioactif", "eclair", "flamme", "flocon", "etoile", "croix", "epees",
            "fusils", "couronne", "ancre", "bouclier", "oeil", "lune", "soleil", "poing",
            // formes 32..63
            "carre", "carre_arrondi", "cercle", "anneau", "demi_disque", "quart_disque", "triangle", "triangle_rect",
            "losange", "pentagone", "hexagone", "octogone", "etoile4", "etoile6", "croix_plus", "sautoir",
            "chevron", "fleche", "croissant", "coeur", "goutte", "ecu", "cadre", "vague",
            "dents", "griffes", "os", "croc", "pique", "trefle", "cible", "eclat"
    };

    /** Tailles de secours (largeur, hauteur en unites du canevas 64 x 96) des formes 32..63, dans l'ordre. */
    private static final int[][] TAILLES_SECOURS = {
            {24, 24}, {24, 24}, {24, 24}, {28, 28}, {32, 16}, {24, 24}, {28, 24}, {24, 24},
            {24, 32}, {28, 28}, {28, 28}, {28, 28}, {28, 28}, {28, 28}, {28, 28}, {28, 28},
            {40, 20}, {16, 32}, {28, 28}, {28, 26}, {20, 28}, {36, 44}, {48, 60}, {64, 12},
            {64, 10}, {32, 32}, {36, 12}, {12, 24}, {24, 28}, {26, 26}, {32, 32}, {28, 28}
    };

    /** Formes figuratives (hors emblemes) dont les proportions sont gardees par defaut. */
    private static final String[] RATIO_SECOURS = {"coeur", "pique", "trefle", "griffes"};

    /** Taille par defaut des emblemes (comme l'ancien embleme centre). */
    public static final int TAILLE_EMBLEME = 44;

    /** Donnees resolues (secours + fichier eventuel), construites une seule fois. */
    private static final class Catalogue {
        static final Catalogue I = new Catalogue();

        final Map<String, Integer> parCle = new HashMap<>();
        final boolean[] ratio = new boolean[NOMBRE];
        final int[] largeur = new int[NOMBRE];
        final int[] hauteur = new int[NOMBRE];
        final String[] libelles = new String[NOMBRE];

        Catalogue() {
            for (int i = 0; i < NOMBRE; i++) {
                parCle.put(CLES[i], i);
                libelles[i] = "forme." + HeraliszClans.MODID + "." + CLES[i];
                if (i < EMBLEMES) {
                    ratio[i] = true;
                    largeur[i] = hauteur[i] = TAILLE_EMBLEME;
                } else {
                    largeur[i] = TAILLES_SECOURS[i - EMBLEMES][0];
                    hauteur[i] = TAILLES_SECOURS[i - EMBLEMES][1];
                }
            }
            for (String c : RATIO_SECOURS) ratio[parCle.get(c)] = true;
            lireFichier();
        }

        /** Affine les reglages depuis formes.json (tolerant : format inconnu ou fichier absent = secours). */
        private void lireFichier() {
            try (InputStream in = Formes.class.getResourceAsStream(CHEMIN)) {
                if (in == null) {
                    LOG.info("[clans] {} absent : catalogue des formes de secours", CHEMIN);
                    return;
                }
                JsonElement racine = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                JsonArray arr = null;
                if (racine.isJsonArray()) arr = racine.getAsJsonArray();
                else if (racine.isJsonObject()) arr = Json.arr(racine.getAsJsonObject(), "formes");
                int lues = 0;
                if (arr != null) {
                    int pos = 0;
                    for (JsonElement e : arr) {
                        int indexParPosition = pos++;
                        if (!e.isJsonObject()) continue;
                        JsonObject o = e.getAsJsonObject();
                        int id = resoudre(o, indexParPosition);
                        if (id < 0) continue;
                        appliquer(o, id);
                        lues++;
                    }
                }
                LOG.info("[clans] catalogue des formes : {} entree(s) lue(s) dans {}", lues, CHEMIN);
            } catch (Exception ex) {
                LOG.warn("[clans] {} illisible ({}) : catalogue des formes de secours", CHEMIN, ex.toString());
            }
        }

        /** Id d'une entree : par cle d'abord (robuste si l'ordre change), sinon par index explicite, sinon position. */
        private int resoudre(JsonObject o, int position) {
            for (String k : new String[]{"cle", "key"}) {
                String s = Json.str(o, k, null);
                if (s != null) {
                    Integer id = parCle.get(s.toLowerCase(Locale.ROOT));
                    return id == null ? -1 : id;
                }
            }
            JsonElement idE = o.get("id");
            if (idE != null && idE.isJsonPrimitive()) {
                if (idE.getAsJsonPrimitive().isNumber()) {
                    int id = idE.getAsInt();
                    return id >= 0 && id < NOMBRE ? id : -1;
                }
                Integer id = parCle.get(idE.getAsString().toLowerCase(Locale.ROOT));
                return id == null ? -1 : id;
            }
            int idx = Json.integer(o, "index", position);
            return idx >= 0 && idx < NOMBRE ? idx : -1;
        }

        private void appliquer(JsonObject o, int id) {
            for (String k : new String[]{"garderRatio", "garder_ratio", "ratio"}) {
                JsonElement e = o.get(k);
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
                    ratio[id] = e.getAsBoolean();
                    break;
                }
            }
            int w = -1, h = -1;
            for (String k : new String[]{"tailleDefaut", "taille_defaut", "taille"}) {
                JsonElement e = o.get(k);
                if (e == null) continue;
                if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
                    w = nombre(e.getAsJsonArray().get(0));
                    h = nombre(e.getAsJsonArray().get(1));
                } else if (e.isJsonObject()) {
                    w = Json.integer(e.getAsJsonObject(), "w", -1);
                    h = Json.integer(e.getAsJsonObject(), "h", -1);
                } else if (e.isJsonPrimitive()) {
                    w = h = nombre(e);
                }
                break;
            }
            if (w < 0) w = Json.integer(o, "w", -1);
            if (h < 0) h = Json.integer(o, "h", -1);
            if (w >= Calque.TAILLE_MIN && w <= Calque.TAILLE_MAX) largeur[id] = w;
            if (h >= Calque.TAILLE_MIN && h <= Calque.TAILLE_MAX) hauteur[id] = h;
        }

        private static int nombre(JsonElement e) {
            try {
                return e != null && e.isJsonPrimitive() ? (int) Math.round(e.getAsDouble()) : -1;
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
    }

    /** Vrai si l'id designe une forme du catalogue. */
    public static boolean existe(int id) {
        return id >= 0 && id < NOMBRE;
    }

    /** Cle stable d'une forme ("loup", "carre"...) ; "carre" pour un id hors catalogue. */
    public static String cle(int id) {
        return existe(id) ? CLES[id] : CLES[EMBLEMES];
    }

    /** Id d'une cle (insensible a la casse) ; -1 si inconnue. */
    public static int id(String cle) {
        if (cle == null) return -1;
        Integer i = Catalogue.I.parCle.get(cle.toLowerCase(Locale.ROOT));
        return i == null ? -1 : i;
    }

    /** Emblemes = ids 0..31. */
    public static boolean estEmbleme(int id) {
        return id >= 0 && id < EMBLEMES;
    }

    /** Vrai si l'atelier garde les proportions de la forme quand on la redimensionne (Maj = libre). */
    public static boolean garderRatio(int id) {
        return existe(id) && Catalogue.I.ratio[id];
    }

    /** Largeur par defaut (unites du canevas). */
    public static int largeurDefaut(int id) {
        return existe(id) ? Catalogue.I.largeur[id] : 24;
    }

    /** Hauteur par defaut (unites du canevas). */
    public static int hauteurDefaut(int id) {
        return existe(id) ? Catalogue.I.hauteur[id] : 24;
    }

    /** Taille par defaut {largeur, hauteur} (nouveau tableau : a appeler a l'ajout d'un calque, pas par image). */
    public static int[] tailleDefaut(int id) {
        return new int[]{largeurDefaut(id), hauteurDefaut(id)};
    }

    /** Cle de traduction du nom de la forme : forme.heralisz_clans.&lt;cle&gt;. */
    public static String libelle(int id) {
        return existe(id) ? Catalogue.I.libelles[id] : "forme." + HeraliszClans.MODID + "." + CLES[EMBLEMES];
    }

    /** Force la lecture du catalogue (au demarrage, pour journaliser tot une erreur de fichier). */
    public static void charger() {
        if (Catalogue.I.parCle.isEmpty()) LOG.warn("[clans] catalogue des formes vide");
    }
}
