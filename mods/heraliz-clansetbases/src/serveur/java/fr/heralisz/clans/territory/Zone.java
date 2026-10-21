package fr.heralisz.clans.territory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Definition d'une zone capturable (config/heralisz_clans/zones.json) : identifiant, nom affiche, centre (x, z),
 * rayon en blocs et position du bloc drapeau (null tant qu'un op ne l'a pas pose avec /territoire drapeau).
 * L'etat de jeu (proprietaire, progression...) est dans {@link EtatTerritoire} (SavedData du monde).
 * <p>Le nom est borne a {@link #NOM_MAX} caracteres des la construction : il voyage dans des paquets a taille fixe
 * (HUD, panneau) et une valeur trop longue y leverait une exception sur le thread serveur.
 * <p>Geometrie : un cylindre. Horizontalement, distance au centre &lt;= rayon ; verticalement, a moins de
 * {@link #demiHauteur()} blocs de la hauteur de reference (le drapeau, sinon le sol au centre).
 */
public final class Zone {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{2,24}");
    /** Longueur maximale du nom affiche (les paquets acceptent 48). */
    public static final int NOM_MAX = 32;
    /** Demi-hauteur minimale de la zone autour de sa reference (drapeau, sinon surface au centre). */
    public static final int DEMI_HAUTEUR = 40;
    /** Bornes d'une demi-hauteur reglee a la main dans zones.json. */
    public static final int DEMI_HAUTEUR_MIN = 8, DEMI_HAUTEUR_MAX = 512;
    /** Bornes de la valeur d'une zone (niveau de loot de la ville : T1 a T7). */
    public static final int VALEUR_MIN = 1, VALEUR_MAX = 7;

    public final String id;
    public final String nom;
    public int x, z, rayon;
    @Nullable
    public BlockPos drapeau;
    /** Demi-hauteur reglee a la main (cle « demiHauteur » de zones.json) ; null = valeur par defaut. */
    @Nullable
    public Integer demiHauteurFixe;
    /**
     * Centre regle a la main (cle « centreFixe » de zones.json) : /territoire drapeau ne le deplace pas. Par defaut
     * (faux), le centre suit le drapeau a chaque pose : la zone est le cercle autour de son drapeau.
     */
    public boolean centreFixe;
    /**
     * Valeur de la zone (cle « valeur » de zones.json, 1 a 7) : le niveau de loot de la ville (Tcherno T3-T4 = 3 ou 4,
     * aerodrome T6 = 6). Plus elle vaut, plus la tenir rapporte d'XP et de points de territoire au clan (le revenu en
     * herals, lui, est le meme pour toutes les zones).
     */
    public int valeur = VALEUR_MIN;
    /** Hauteur de la surface au centre, calee par le moteur quand la zone n'a pas de drapeau (null = inconnue). */
    @Nullable
    private Integer hauteurSurface;

    public Zone(String id, String nom, int x, int z, int rayon, @Nullable BlockPos drapeau) {
        this.id = id;
        String n = nettoyerNom(nom);
        this.nom = n.isEmpty() ? id.toUpperCase(Locale.ROOT) : n;
        this.x = x;
        this.z = z;
        this.rayon = Math.max(5, rayon);
        this.drapeau = drapeau;
    }

    /** Identifiant valide : 2 a 24 caracteres [a-z0-9_] (passe en minuscules par l'appelant). */
    public static boolean idValide(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static String normaliserId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    /** Nom nettoye (caracteres de controle retires, espaces reduits) et coupe a {@link #NOM_MAX}. */
    public static String nettoyerNom(@Nullable String nom) {
        String n = Clan.nettoyer(nom);
        return n.length() > NOM_MAX ? n.substring(0, NOM_MAX).trim() : n;
    }

    /** Vrai si le nom tel que saisi est acceptable (non vide apres nettoyage, pas plus long que {@link #NOM_MAX}). */
    public static boolean nomValide(@Nullable String nom) {
        String n = Clan.nettoyer(nom);
        return !n.isEmpty() && n.length() <= NOM_MAX;
    }

    /** Distance au carre (2D) entre le centre (milieu du bloc x, z) et un point. */
    public double distance2(double px, double pz) {
        double dx = px - (x + 0.5), dz = pz - (z + 0.5);
        return dx * dx + dz * dz;
    }

    /** Distance horizontale entre le centre et un point. */
    public double distance(double px, double pz) {
        return Math.sqrt(distance2(px, pz));
    }

    /** Distance horizontale au centre du bloc drapeau (infinie sans drapeau). */
    public double distanceDrapeau(double px, double pz) {
        if (drapeau == null) return Double.MAX_VALUE;
        double dx = px - (drapeau.getX() + 0.5), dz = pz - (drapeau.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Le point est dans le cercle de capture du drapeau (ClansConfig.RAYON_CAPTURE, ecart de hauteur HAUTEUR_CAPTURE). */
    public boolean auDrapeau(double px, double py, double pz) {
        if (drapeau == null) return false;
        return Math.abs(py - drapeau.getY()) <= fr.heralisz.clans.ClansConfig.HAUTEUR_CAPTURE.get()
                && distanceDrapeau(px, pz) <= fr.heralisz.clans.ClansConfig.RAYON_CAPTURE.get();
    }

    /**
     * Demi-hauteur du cylindre : celle de zones.json si elle est reglee, sinon max(40, rayon / 4) (une ville de
     * rayon 400 a du relief : 100 blocs ; une petite zone garde 40). Empeche de compter un joueur cache loin sous
     * terre ou perche tres haut, sans exclure les collines ni les immeubles.
     */
    public int demiHauteur() {
        if (demiHauteurFixe != null) return demiHauteurFixe;
        return Math.max(DEMI_HAUTEUR, rayon / 4);
    }

    /**
     * Hauteur de reference pour le test vertical : celle du drapeau s'il est pose, sinon la surface au centre
     * (calee par {@link #calerHauteur}) ; null tant qu'aucune des deux n'est connue.
     */
    @Nullable
    public Integer hauteurRef() {
        return drapeau != null ? drapeau.getY() : hauteurSurface;
    }

    /**
     * Sans drapeau, memorise la hauteur de la surface au centre (chunk charge au besoin : appele au demarrage, a la
     * creation, au rechargement et quand un drapeau est retire, jamais a chaque tick).
     */
    public void calerHauteur(ServerLevel level) {
        if (drapeau != null) return;
        hauteurSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    /** Le point est a moins de rayon du centre (2D, bord compris). */
    public boolean contientHorizontal(double px, double pz) {
        return distance2(px, pz) <= (double) rayon * rayon;
    }

    /**
     * Le point est dans la zone : a moins de rayon du centre (2D) et a moins de {@link #demiHauteur()} blocs de la
     * hauteur de reference (drapeau, sinon surface au centre). Sans aucune reference connue, le test est 2D seulement.
     */
    public boolean contient(double px, double py, double pz) {
        if (!contientHorizontal(px, pz)) return false;
        Integer ref = hauteurRef();
        return ref == null || Math.abs(py - ref) < demiHauteur();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("x", x);
        o.addProperty("z", z);
        o.addProperty("rayon", rayon);
        if (drapeau == null) {
            o.add("drapeau", com.google.gson.JsonNull.INSTANCE);
        } else {
            JsonArray a = new JsonArray();
            a.add(drapeau.getX());
            a.add(drapeau.getY());
            a.add(drapeau.getZ());
            o.add("drapeau", a);
        }
        if (demiHauteurFixe != null) o.addProperty("demiHauteur", demiHauteurFixe);
        if (centreFixe) o.addProperty("centreFixe", true);
        o.addProperty("valeur", valeur);
        return o;
    }

    /** Null si l'entree est inutilisable (id invalide). Un nom trop long est coupe (journal dans Zones). */
    @Nullable
    public static Zone fromJson(JsonObject o) {
        String id = normaliserId(Json.str(o, "id", ""));
        if (!idValide(id)) return null;
        BlockPos d = null;
        JsonArray a = Json.arr(o, "drapeau");
        if (a.size() == 3) {
            try {
                d = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        Zone zo = new Zone(id, Json.str(o, "nom", id.toUpperCase(Locale.ROOT)), Json.integer(o, "x", 0), Json.integer(o, "z", 0),
                Json.integer(o, "rayon", 100), d);
        if (o.has("demiHauteur")) {
            int h = Json.integer(o, "demiHauteur", 0);
            if (h > 0) zo.demiHauteurFixe = Math.max(DEMI_HAUTEUR_MIN, Math.min(DEMI_HAUTEUR_MAX, h));
        }
        zo.centreFixe = Json.bool(o, "centreFixe", false);
        zo.valeur = Math.max(VALEUR_MIN, Math.min(VALEUR_MAX, Json.integer(o, "valeur", VALEUR_MIN)));
        return zo;
    }
}
