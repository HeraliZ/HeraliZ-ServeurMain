package fr.heralisz.clans.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Banniere v2 d'un clan (BANNIERE_V2_SPEC §1) : un fond en degrade (couleurs A et B, style) et 0 a 12 calques de
 * formes, du fond vers le dessus, sur un canevas virtuel de 64 x 96 unites (fanion = rectangle 64 x 75 + pointe en
 * V jusqu'a (32, 96)).
 * <p>
 * Objet immuable : le constructeur borne tout (au plus {@link #MAX_CALQUES} calques, calques de forme inconnue
 * ignores, couleurs sur 24 bits, valeurs serrees dans leurs bornes). Les methodes {@code avec…} rendent des copies
 * (pour l'atelier). {@link #cle()} est une empreinte stable calculee une fois (cache des textures) ; equals/hashCode
 * portent sur le contenu.
 */
public final class Banniere {
    /** Styles de fond. UNI ajoute en dernier (ordinaux conserves) : couleur A seule. */
    public enum Degrade {
        VERTICAL, DIAGONAL, RADIAL, PARTAGE, UNI;

        public String libelle() {
            return "degrade." + name().toLowerCase();
        }

        public static Degrade parOrdinal(int i) {
            Degrade[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** Lecture reseau : la banniere (au plus 12 calques) et le nombre de calques annonce par l'emetteur. */
    public record Lecture(Banniere banniere, int calquesEnvoyes) {
    }

    /** Canevas virtuel : largeur, hauteur totale, hauteur du rectangle (sans la pointe), pas de la grille. */
    public static final int LARGEUR = 64, HAUTEUR = 96, CORPS = 75, GRILLE = 8;
    /** Nombre maximal de calques. */
    public static final int MAX_CALQUES = 12;
    /** Nombre d'emblemes (ids 0..31 du catalogue {@link Formes}). */
    public static final int EMBLEMES = Formes.EMBLEMES;
    /** Calque de migration : un ancien embleme centre (cx 32, cy 38, 44 x 44). */
    public static final int MIGRATION_CX = 32, MIGRATION_CY = 38, MIGRATION_TAILLE = 44;
    /** Fond 1F2A44 -> 6FA8FF diagonal + loup (forme 0) blanc casse. */
    public static final Banniere DEFAUT = new Banniere(0x1F2A44, 0x6FA8FF, Degrade.DIAGONAL,
            List.of(migration(0, 0xF2F4F8)));

    public final int couleurA, couleurB;
    public final Degrade degrade;
    /** Calques du fond vers le dessus (liste non modifiable, 0..12). */
    public final List<Calque> calques;
    private final String cle;
    private final int hash;

    public Banniere(int couleurA, int couleurB, @Nullable Degrade degrade, @Nullable List<Calque> calques) {
        this.couleurA = couleurA & 0xFFFFFF;
        this.couleurB = couleurB & 0xFFFFFF;
        this.degrade = degrade == null ? Degrade.VERTICAL : degrade;
        List<Calque> l = new ArrayList<>(MAX_CALQUES);
        if (calques != null) {
            for (Calque c : calques) {
                if (l.size() >= MAX_CALQUES) break;
                if (c != null && c.valide()) l.add(c);
            }
        }
        this.calques = Collections.unmodifiableList(l);
        StringBuilder sb = new StringBuilder(24 + l.size() * 40);
        sb.append("2|").append(Integer.toHexString(this.couleurA)).append(':').append(Integer.toHexString(this.couleurB)).append(':')
                .append(this.degrade.ordinal());
        for (Calque c : l) {
            sb.append('|');
            c.empreinte(sb);
        }
        this.cle = sb.toString();
        this.hash = cle.hashCode();
    }

    /** Fond seul, sans calque. */
    public static Banniere fond(int couleurA, int couleurB, Degrade degrade) {
        return new Banniere(couleurA, couleurB, degrade, List.of());
    }

    /** Calque issu d'un ancien embleme (migration v1 -> v2). */
    public static Calque migration(int embleme, int couleurEmbleme) {
        return new Calque(embleme, MIGRATION_CX, MIGRATION_CY, MIGRATION_TAILLE, MIGRATION_TAILLE, 0, couleurEmbleme, 0,
                Calque.CONTOUR_DEFAUT, false, false);
    }

    /** Banniere au format v1 (embleme + couleurs) convertie en v2 : un calque emblème centre. */
    public static Banniere depuisV1(int embleme, int couleurA, int couleurB, Degrade degrade, int couleurEmbleme) {
        int e = Math.max(0, Math.min(EMBLEMES - 1, embleme));
        return new Banniere(couleurA, couleurB, degrade, List.of(migration(e, couleurEmbleme)));
    }

    // ------------------------------------------------------------------ lecture
    public int nombreCalques() {
        return calques.size();
    }

    /** Calque d'index i (du fond vers le dessus) ; null hors bornes. */
    @Nullable
    public Calque calque(int i) {
        return i >= 0 && i < calques.size() ? calques.get(i) : null;
    }

    /** Vrai si la banniere a deja 12 calques (l'atelier grise « + AJOUTER »). */
    public boolean pleine() {
        return calques.size() >= MAX_CALQUES;
    }

    // ------------------------------------------------------------------ modifications du fond (copies)
    public Banniere avecCouleurA(int c) {
        return new Banniere(c, couleurB, degrade, calques);
    }

    public Banniere avecCouleurB(int c) {
        return new Banniere(couleurA, c, degrade, calques);
    }

    public Banniere avecDegrade(Degrade d) {
        return new Banniere(couleurA, couleurB, d, calques);
    }

    /** Change tout le fond d'un coup. */
    public Banniere avecFond(int a, int b, Degrade d) {
        return new Banniere(a, b, d, calques);
    }

    // ------------------------------------------------------------------ modifications des calques (copies)
    /** Remplace toute la pile de calques (bornee a 12). */
    public Banniere avecCalques(List<Calque> l) {
        return new Banniere(couleurA, couleurB, degrade, l);
    }

    /** Remplace le calque i ; inchange si i est hors bornes. */
    public Banniere avecCalque(int i, Calque c) {
        if (i < 0 || i >= calques.size() || c == null) return this;
        List<Calque> l = new ArrayList<>(calques);
        l.set(i, c);
        return avecCalques(l);
    }

    /** Ajoute un calque au-dessus de tous les autres ; inchange si la banniere est pleine. */
    public Banniere avecCalqueAjoute(Calque c) {
        return avecCalqueInsere(calques.size(), c);
    }

    /** Insere un calque a l'index i (0 = tout au fond, borne) ; inchange si la banniere est pleine. */
    public Banniere avecCalqueInsere(int i, Calque c) {
        if (c == null || !c.valide() || pleine()) return this;
        List<Calque> l = new ArrayList<>(calques);
        l.add(Math.max(0, Math.min(l.size(), i)), c);
        return avecCalques(l);
    }

    /** Retire le calque i ; inchange si i est hors bornes. */
    public Banniere avecCalqueRetire(int i) {
        if (i < 0 || i >= calques.size()) return this;
        List<Calque> l = new ArrayList<>(calques);
        l.remove(i);
        return avecCalques(l);
    }

    /** Deplace le calque de l'index « de » vers l'index « vers » (borne) dans la pile. */
    public Banniere avecCalqueDeplace(int de, int vers) {
        if (de < 0 || de >= calques.size()) return this;
        int v = Math.max(0, Math.min(calques.size() - 1, vers));
        if (v == de) return this;
        List<Calque> l = new ArrayList<>(calques);
        Calque c = l.remove(de);
        l.add(v, c);
        return avecCalques(l);
    }

    /** Duplique le calque i juste au-dessus de lui (meme place) ; inchange si pleine ou hors bornes. */
    public Banniere avecCalqueDuplique(int i) {
        if (i < 0 || i >= calques.size() || pleine()) return this;
        return avecCalqueInsere(i + 1, calques.get(i));
    }

    // ------------------------------------------------------------------ validation
    /**
     * Version sure de la banniere, a passer cote serveur avant tout enregistrement : bornes du constructeur (deja
     * garanties), calques entierement hors de la toile retires (invisibles) et contours ramenes a ce que le rendu sait
     * dessiner ({@link Calque#contourEffectif()}). Idempotente ; rend {@code this} si rien ne change.
     */
    public Banniere valider() {
        boolean intacte = true;
        for (Calque c : calques) {
            if (!c.toucheToile() || c.contour() != c.contourEffectif()) {
                intacte = false;
                break;
            }
        }
        if (intacte) return this;
        List<Calque> l = new ArrayList<>(calques.size());
        for (Calque c : calques) {
            if (!c.toucheToile()) continue;
            l.add(c.contour() != c.contourEffectif() ? c.avecContour(c.contourEffectif()) : c);
        }
        return new Banniere(couleurA, couleurB, degrade, l);
    }

    /**
     * Refus serveur selon le nombre de calques annonce par le client : cle « msg.banniere_trop_calques » (argument :
     * le maximum) au-dela de 12, null sinon.
     */
    @Nullable
    public static String refus(int calquesEnvoyes) {
        return calquesEnvoyes > MAX_CALQUES ? "msg.banniere_trop_calques" : null;
    }

    // ------------------------------------------------------------------ couleurs d'interface
    /**
     * Couleur d'accent du clan dans les interfaces (la couleur principale, eclaircie si elle est trop sombre pour
     * rester lisible sur nos fonds : un tag violet nuit devenait invisible).
     */
    public int accent() {
        return lisible(couleurA);
    }

    /** Eclaircit une couleur trop sombre (luminance < 0,38) vers un ton lisible, sans toucher aux couleurs deja claires. */
    public static int lisible(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        if (lum >= 0.38f) return rgb & 0xFFFFFF;
        float t = Math.min(0.75f, (0.38f - lum) * 1.9f);                     // plus c'est sombre, plus on remonte
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Empreinte stable du contenu (cache des textures), calculee une fois a la construction. */
    public String cle() {
        return cle;
    }

    // ------------------------------------------------------------------ JSON
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("couleurA", Json.couleur(couleurA));
        o.addProperty("couleurB", Json.couleur(couleurB));
        o.addProperty("degrade", degrade.name());
        JsonArray a = new JsonArray();
        for (Calque c : calques) a.add(c.toJson());
        o.add("calques", a);
        return o;
    }

    /**
     * Lecture tolerante. Format v2 : {@code calques} present. Format v1 (sans {@code calques}) : l'ancien
     * {@code embleme}/{@code couleurEmbleme} devient un calque centre (cx 32, cy 38, 44 x 44).
     */
    public static Banniere fromJson(JsonObject o) {
        int a = Json.couleur(o, "couleurA", DEFAUT.couleurA), b = Json.couleur(o, "couleurB", DEFAUT.couleurB);
        JsonElement de = o.get("degrade");
        Degrade d = de != null && de.isJsonPrimitive() && de.getAsJsonPrimitive().isNumber() ? Degrade.parOrdinal(de.getAsInt())
                : Json.enumOf(o, "degrade", Degrade.class, Degrade.VERTICAL);
        JsonElement ce = o.get("calques");
        if (ce == null || !ce.isJsonArray()) {
            return depuisV1(Json.integer(o, "embleme", 0), a, b, d, Json.couleur(o, "couleurEmbleme", 0xF2F4F8));
        }
        List<Calque> l = new ArrayList<>(MAX_CALQUES);
        for (JsonElement e : ce.getAsJsonArray()) {
            if (l.size() >= MAX_CALQUES) break;
            if (!e.isJsonObject()) continue;
            Calque c = Calque.fromJson(e.getAsJsonObject());
            if (c != null) l.add(c);
        }
        return new Banniere(a, b, d, l);
    }

    // ------------------------------------------------------------------ reseau
    /** int couleur A, int couleur B, octet style, varint nombre de calques, puis les calques ({@link Calque#write}). */
    public void write(FriendlyByteBuf b) {
        b.writeInt(couleurA);
        b.writeInt(couleurB);
        b.writeByte(degrade.ordinal());
        b.writeVarInt(calques.size());
        for (Calque c : calques) c.write(b);
    }

    public static Banniere read(FriendlyByteBuf b) {
        return lire(b).banniere();
    }

    /**
     * Lecture avec le nombre de calques annonce : jamais plus de 12 calques lus (le reste du tampon est ignore ; les
     * paquets client -> serveur portent la banniere en dernier). Calques de forme inconnue ignores.
     */
    public static Lecture lire(FriendlyByteBuf b) {
        int a = b.readInt(), bb = b.readInt();
        Degrade d = Degrade.parOrdinal(b.readUnsignedByte());
        int n = b.readVarInt();
        int lus = Math.max(0, Math.min(MAX_CALQUES, n));
        List<Calque> l = new ArrayList<>(lus);
        for (int i = 0; i < lus; i++) l.add(Calque.read(b));
        return new Lecture(new Banniere(a, bb, d, l), Math.max(0, n));
    }

    // ------------------------------------------------------------------ egalite
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Banniere x && x.hash == hash && x.couleurA == couleurA && x.couleurB == couleurB && x.degrade == degrade
                && x.calques.equals(calques);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "Banniere[" + cle + "]";
    }
}
