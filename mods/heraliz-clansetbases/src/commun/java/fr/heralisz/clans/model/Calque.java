package fr.heralisz.clans.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

/**
 * Un calque de la banniere v2 : une forme du catalogue ({@link Formes}) posee sur le canevas virtuel de 64 x 96
 * unites. Immuable ; le constructeur borne tout (sauf l'id de forme, verifie par {@link #valide()} et filtre par
 * {@link Banniere}).
 *
 * @param forme          id de forme 0..63 (voir {@link Formes})
 * @param cx             centre horizontal, unites, borne a [-32, 96]
 * @param cy             centre vertical, unites, borne a [-48, 144]
 * @param w              largeur de la boite de la forme, unites, [2, 160]
 * @param h              hauteur de la boite, unites, [2, 160]
 * @param rot            rotation en degres, ramenee a 0..359
 * @param couleur        couleur RGB 24 bits
 * @param contour        epaisseur du contour en demi-unites, 0..4 (0 = sans contour)
 * @param couleurContour couleur RGB du contour
 * @param miroirX        miroir horizontal
 * @param miroirY        miroir vertical
 */
public record Calque(int forme, int cx, int cy, int w, int h, int rot, int couleur, int contour, int couleurContour,
                     boolean miroirX, boolean miroirY) {
    public static final int CX_MIN = -32, CX_MAX = 96;
    public static final int CY_MIN = -48, CY_MAX = 144;
    public static final int TAILLE_MIN = 2, TAILLE_MAX = 160;
    public static final int CONTOUR_MAX = 4;
    /** Couleur de contour par defaut (noir, comme la reference tools/clans/formes_art.py). */
    public static final int CONTOUR_DEFAUT = 0x000000;
    /** Couleur par defaut d'un calque dont le JSON ne la donne pas. */
    public static final int COULEUR_DEFAUT = 0xFFFFFF;

    public Calque {
        cx = borne(cx, CX_MIN, CX_MAX);
        cy = borne(cy, CY_MIN, CY_MAX);
        w = borne(w, TAILLE_MIN, TAILLE_MAX);
        h = borne(h, TAILLE_MIN, TAILLE_MAX);
        rot = Math.floorMod(rot, 360);
        couleur &= 0xFFFFFF;
        contour = borne(contour, 0, CONTOUR_MAX);
        couleurContour &= 0xFFFFFF;
    }

    /** Calque simple : forme a sa taille par defaut, centree en (cx, cy), sans rotation ni contour. */
    public static Calque de(int forme, int cx, int cy, int couleur) {
        return new Calque(forme, cx, cy, Formes.largeurDefaut(forme), Formes.hauteurDefaut(forme), 0, couleur, 0, CONTOUR_DEFAUT,
                false, false);
    }

    /** Calque sans rotation ni contour, taille donnee. */
    public static Calque de(int forme, int cx, int cy, int w, int h, int couleur) {
        return new Calque(forme, cx, cy, w, h, 0, couleur, 0, CONTOUR_DEFAUT, false, false);
    }

    /** Vrai si la forme existe dans le catalogue (sinon le calque est ignore par la banniere). */
    public boolean valide() {
        return Formes.existe(forme);
    }

    public boolean estEmbleme() {
        return Formes.estEmbleme(forme);
    }

    /**
     * Contour le plus epais que le rendu sait dessiner pour une boite w x h : l'atlas SDF ne connait la distance que
     * jusqu'a 16 px de tuile du bord (spec §3), et un contour de c demi-unites en demande c x 112 / min(w, h). Au-dela,
     * le trait serait tronque (3 et 4 rendraient comme 2 sur un petit carre) : on borne donc a min(w, h) / 7 (8 unites :
     * 1 ; 16 : 2 ; 21 : 3 ; 28 et plus : 4).
     */
    public static int contourMax(int w, int h) {
        return Math.min(CONTOUR_MAX, Math.max(0, Math.min(w, h)) / 7);
    }

    /** Epaisseur reellement dessinee (et gardee par {@link Banniere#valider()}) : {@code contour} borne par la taille. */
    public int contourEffectif() {
        return Math.min(contour, contourMax(w, h));
    }

    // ------------------------------------------------------------------ modifications (copies)
    public Calque avecForme(int f) {
        return new Calque(f, cx, cy, w, h, rot, couleur, contour, couleurContour, miroirX, miroirY);
    }

    public Calque avecCentre(int ncx, int ncy) {
        return new Calque(forme, ncx, ncy, w, h, rot, couleur, contour, couleurContour, miroirX, miroirY);
    }

    /** Deplace le centre de (dx, dy) unites. */
    public Calque avecDecalage(int dx, int dy) {
        return avecCentre(cx + dx, cy + dy);
    }

    public Calque avecTaille(int nw, int nh) {
        return new Calque(forme, cx, cy, nw, nh, rot, couleur, contour, couleurContour, miroirX, miroirY);
    }

    public Calque avecRotation(int r) {
        return new Calque(forme, cx, cy, w, h, r, couleur, contour, couleurContour, miroirX, miroirY);
    }

    public Calque avecCouleur(int c) {
        return new Calque(forme, cx, cy, w, h, rot, c, contour, couleurContour, miroirX, miroirY);
    }

    public Calque avecContour(int epaisseur) {
        return new Calque(forme, cx, cy, w, h, rot, couleur, epaisseur, couleurContour, miroirX, miroirY);
    }

    public Calque avecCouleurContour(int c) {
        return new Calque(forme, cx, cy, w, h, rot, couleur, contour, c, miroirX, miroirY);
    }

    public Calque avecMiroirX(boolean m) {
        return new Calque(forme, cx, cy, w, h, rot, couleur, contour, couleurContour, m, miroirY);
    }

    public Calque avecMiroirY(boolean m) {
        return new Calque(forme, cx, cy, w, h, rot, couleur, contour, couleurContour, miroirX, m);
    }

    // ------------------------------------------------------------------ geometrie
    /**
     * Vrai si la boite tournee du calque touche le canevas 64 x 96 (un calque entierement hors toile est invisible et
     * retire par {@link Banniere#valider()}).
     */
    public boolean toucheToile() {
        double a = Math.toRadians(rot), c = Math.abs(Math.cos(a)), s = Math.abs(Math.sin(a));
        double ex = (w * c + h * s) / 2.0, ey = (w * s + h * c) / 2.0;
        return cx + ex > 0 && cx - ex < Banniere.LARGEUR && cy + ey > 0 && cy - ey < Banniere.HAUTEUR;
    }

    // ------------------------------------------------------------------ empreinte
    /** Ajoute l'empreinte du calque a une cle (hexadecimal compact). */
    void empreinte(StringBuilder sb) {
        sb.append(Integer.toHexString(forme)).append(',').append(cx).append(',').append(cy).append(',').append(w).append(',')
                .append(h).append(',').append(rot).append(',').append(Integer.toHexString(couleur)).append(',').append(contour)
                .append(',').append(Integer.toHexString(couleurContour)).append(',').append((miroirX ? 1 : 0) | (miroirY ? 2 : 0));
    }

    // ------------------------------------------------------------------ JSON
    /** JSON : la forme est ecrite par sa cle (robuste si l'ordre du catalogue change). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("forme", Formes.cle(forme));
        o.addProperty("cx", cx);
        o.addProperty("cy", cy);
        o.addProperty("w", w);
        o.addProperty("h", h);
        o.addProperty("rot", rot);
        o.addProperty("couleur", Json.couleur(couleur));
        o.addProperty("contour", contour);
        o.addProperty("couleurContour", Json.couleur(couleurContour));
        o.addProperty("miroirX", miroirX);
        o.addProperty("miroirY", miroirY);
        return o;
    }

    /**
     * Lecture tolerante : forme par cle ou par index (absente = 0) ; null si la forme est inconnue (calque ignore).
     * Champs absents : cx 32, cy 38, w/h = taille par defaut de la forme, rot 0, couleur #FFFFFF, contour 0, couleur de
     * contour #000000, miroirs faux (memes valeurs que la reference Python).
     */
    @Nullable
    public static Calque fromJson(JsonObject o) {
        int f = o.has("forme") ? formeJson(o.get("forme")) : 0;
        if (!Formes.existe(f)) return null;
        int w = Json.integer(o, "w", Formes.largeurDefaut(f)), h = Json.integer(o, "h", Formes.hauteurDefaut(f));
        return new Calque(f, Json.integer(o, "cx", Banniere.LARGEUR / 2), Json.integer(o, "cy", 38), w, h, Json.integer(o, "rot", 0),
                Json.couleur(o, "couleur", COULEUR_DEFAUT), Json.integer(o, "contour", 0), Json.couleur(o, "couleurContour", CONTOUR_DEFAUT),
                Json.bool(o, "miroirX", false), Json.bool(o, "miroirY", false));
    }

    /** Forme ecrite par cle ("loup") ou par index (0) ; -1 si illisible. */
    static int formeJson(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return -1;
        if (e.getAsJsonPrimitive().isNumber()) {
            try {
                return e.getAsInt();
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        String s = e.getAsString().trim();
        int id = Formes.id(s);
        if (id >= 0) return id;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ reseau
    /**
     * Ecriture compacte : varint forme, 5 shorts (cx, cy, w, h, rot), int couleur, int couleur de contour, un octet
     * de drapeaux (bit 0 = miroir X, bit 1 = miroir Y, bits 2..4 = epaisseur du contour).
     */
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(forme);
        b.writeShort(cx);
        b.writeShort(cy);
        b.writeShort(w);
        b.writeShort(h);
        b.writeShort(rot);
        b.writeInt(couleur);
        b.writeInt(couleurContour);
        b.writeByte((miroirX ? 1 : 0) | (miroirY ? 2 : 0) | (contour << 2));
    }

    /** Lecture : tout est reborne par le constructeur (forme non verifiee ici, voir {@link #valide()}). */
    public static Calque read(FriendlyByteBuf b) {
        int f = b.readVarInt();
        int cx = b.readShort(), cy = b.readShort(), w = b.readShort(), h = b.readShort(), rot = b.readShort();
        int couleur = b.readInt(), couleurContour = b.readInt();
        int d = b.readUnsignedByte();
        return new Calque(f, cx, cy, w, h, rot, couleur, (d >> 2) & 7, couleurContour, (d & 1) != 0, (d & 2) != 0);
    }

    private static int borne(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
