package fr.heralisz.bases.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Metadonnees du bunker ({@code data/heralisz_bases/structures/bunker.json}, BASES_SPEC §2), coordonnees relatives au
 * coin de la structure. Sert cote serveur (regles, pose, arrivees) et cote client (plan vu de dessus : envoye tel quel
 * par {@code PlanS2C}). Lecture tolerante : champs absents = valeurs neutres, boites dans tous les formats de
 * {@link Boite#lire}.
 * <p>Format attendu : {@code taille [x,y,z]}, {@code salles [{id, nom_fr, nom_en, interieur [boites], cloison [boites],
 * cout, depart}]}, {@code entrees {nord|sud: {arrivee [x,y,z,yaw], porte boite, sas boite?, sortie [x,y,z]?}}},
 * {@code interieur [boites]} (parties communes toujours constructibles : hall, couloirs, ouvertures des portes), et en
 * option {@code hall}, {@code couloirs} (a la racine ou dans {@code plan} ; boites, listes ou objets nommes : noms de
 * zone du HUD), {@code coque_provisoire "id"|["id",...]}
 * (blocs du dessin remplaces par heralisz_bases:coque a la pose).
 */
public final class Plan {
    /** Salle du bunker. {@code depart} = ouverte des la creation (hall et salle 1). */
    public record Salle(String id, String nomFr, String nomEn, List<Boite> interieur, List<Boite> cloison, long cout, boolean depart) {
        /** Nom dans la langue demandee (fr par defaut). */
        public String nom(String langue) {
            return langue != null && langue.startsWith("en") && !nomEn.isEmpty() ? nomEn : nomFr;
        }
    }

    /**
     * Entree du bunker : point d'arrivee (centre du bloc si les coordonnees sont entieres), porte (ouverture 3 x 3 dans la
     * coque), sas (cube d'arrivee, optionnel) et bloc de sortie (optionnel : sinon pose derriere le point d'arrivee).
     */
    public record Entree(String id, double x, double y, double z, float yaw, @Nullable Boite porte, @Nullable Boite sas,
                         @Nullable int[] sortie) {
    }

    public final int tx, ty, tz;
    public final List<Salle> salles;
    public final Map<String, Entree> entrees;
    public final List<Boite> interieur;
    public final List<Boite> hall;
    public final List<Boite> couloirs;
    /** Ids de blocs remplaces par la coque a la pose (serveur seulement, jamais envoye). */
    public final Set<String> coqueProvisoire;

    public Plan(int tx, int ty, int tz, List<Salle> salles, Map<String, Entree> entrees, List<Boite> interieur, List<Boite> hall,
                List<Boite> couloirs, Set<String> coqueProvisoire) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.salles = Collections.unmodifiableList(salles);
        this.entrees = Collections.unmodifiableMap(entrees);
        this.interieur = Collections.unmodifiableList(interieur);
        this.hall = Collections.unmodifiableList(hall);
        this.couloirs = Collections.unmodifiableList(couloirs);
        this.coqueProvisoire = Collections.unmodifiableSet(coqueProvisoire);
    }

    @Nullable
    public Salle salle(String id) {
        if (id == null) return null;
        for (Salle s : salles) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }

    /** Index (0..) d'une salle dans le modele, -1 si inconnue. */
    public int index(String id) {
        for (int i = 0; i < salles.size(); i++) if (salles.get(i).id.equalsIgnoreCase(id)) return i;
        return -1;
    }

    /** Salles fermees a la creation, dans l'ordre du modele (salles 2 a 6). */
    public List<Salle> sallesAVendre() {
        List<Salle> l = new ArrayList<>();
        for (Salle s : salles) if (!s.depart) l.add(s);
        return l;
    }

    @Nullable
    public Entree entree(String id) {
        if (id == null) return null;
        Entree e = entrees.get(id.toLowerCase(Locale.ROOT));
        return e;
    }

    /** Premiere entree (nord si elle existe). */
    @Nullable
    public Entree entreeParDefaut() {
        Entree e = entrees.get("nord");
        if (e != null) return e;
        return entrees.isEmpty() ? null : entrees.values().iterator().next();
    }

    /**
     * Zone d'un point relatif pour le HUD : id de salle, « hall », « couloir », « sas_&lt;entree&gt; », ou null hors de
     * tout volume connu.
     */
    @Nullable
    public String zone(int x, int y, int z) {
        for (Salle s : salles) if (Boite.dans(s.interieur, x, y, z)) return s.id;
        for (Entree e : entrees.values()) if (e.sas != null && e.sas.contient(x, y, z)) return "sas_" + e.id;
        if (Boite.dans(hall, x, y, z)) return "hall";
        if (Boite.dans(couloirs, x, y, z)) return "couloir";
        for (Entree e : entrees.values()) if (e.porte != null && e.porte.contient(x, y, z)) return "couloir";
        if (Boite.dans(interieur, x, y, z)) return "hall";
        return null;
    }

    /**
     * Vrai si le point relatif est constructible pour un clan qui a ouvert {@code ouvertes} (ids de salles, depart
     * compris ou non : les salles de depart sont toujours ouvertes). Constructible = interieur d'une salle ouverte, ou
     * passage d'une cloison ouverte, ou volume « interieur » global hors des salles fermees (et de leurs cloisons).
     */
    public boolean constructible(int x, int y, int z, Set<String> ouvertes) {
        boolean dansGlobal = Boite.dans(interieur, x, y, z);
        for (int i = 0, n = salles.size(); i < n; i++) {
            Salle s = salles.get(i);
            boolean ouverte = s.depart || contientIgnoreCase(ouvertes, s.id);
            if (Boite.dans(s.interieur, x, y, z)) return ouverte;
            // cloison : murée tant que la salle est fermée ; une fois ouverte, son passage est constructible (on peut le refermer)
            if (Boite.dans(s.cloison, x, y, z)) return ouverte;
        }
        return dansGlobal;
    }

    /** Salle FERMEE (pas encore achetee) dont l'interieur ou la cloison contient le point relatif, null sinon. */
    @Nullable
    public Salle salleFermee(int x, int y, int z, Set<String> ouvertes) {
        for (int i = 0, n = salles.size(); i < n; i++) {
            Salle s = salles.get(i);
            if (s.depart || contientIgnoreCase(ouvertes, s.id)) continue;
            if (Boite.dans(s.interieur, x, y, z) || Boite.dans(s.cloison, x, y, z)) return s;
        }
        return null;
    }

    private static boolean contientIgnoreCase(Set<String> set, String id) {
        if (set.contains(id)) return true;
        for (String s : set) if (s.equalsIgnoreCase(id)) return true;
        return false;
    }

    /** Empreinte (le client ne redemande le plan que si elle change). */
    public int empreinte() {
        FriendlyByteBuf b = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            write(b);
            int h = 1;
            for (int i = 0; i < b.writerIndex(); i++) h = 31 * h + b.getByte(i);
            return h;
        } finally {
            b.release();
        }
    }

    // ------------------------------------------------------------------ JSON
    public static Plan lire(JsonObject o) {
        int tx = 0, ty = 0, tz = 0;
        JsonElement t = o.get("taille");
        if (t != null && t.isJsonArray() && t.getAsJsonArray().size() >= 3) {
            JsonArray a = t.getAsJsonArray();
            tx = a.get(0).getAsInt();
            ty = a.get(1).getAsInt();
            tz = a.get(2).getAsInt();
        } else if (t != null && t.isJsonObject()) {
            tx = Json.i(t.getAsJsonObject(), "x", 0);
            ty = Json.i(t.getAsJsonObject(), "y", 0);
            tz = Json.i(t.getAsJsonObject(), "z", 0);
        }
        List<Salle> salles = new ArrayList<>();
        int n = 0;
        for (JsonElement e : Json.arr(o, "salles")) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            n++;
            String id = Json.str(s, "id", "salle_" + n);
            String nomFr = Json.str(s, "nom_fr", Json.str(s, "nom", "Salle " + n));
            String nomEn = Json.str(s, "nom_en", nomFr.replace("Salle", "Room"));
            salles.add(new Salle(id, nomFr, nomEn, Boite.liste(s.get("interieur")), Boite.liste(s.get("cloison")),
                    Math.max(0, Json.lng(s, "cout", 0)), Json.b(s, "depart", false)));
        }
        Map<String, Entree> entrees = new LinkedHashMap<>();
        JsonElement en = o.get("entrees");
        if (en != null && en.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : en.getAsJsonObject().entrySet()) {
                Entree x = entree(e.getKey(), e.getValue());
                if (x != null) entrees.put(x.id, x);
            }
        } else if (en != null && en.isJsonArray()) {
            for (JsonElement e : en.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                Entree x = entree(Json.str(e.getAsJsonObject(), "id", "entree" + entrees.size()), e);
                if (x != null) entrees.put(x.id, x);
            }
        }
        Set<String> provisoire = new LinkedHashSet<>();
        JsonElement cp = o.get("coque_provisoire");
        if (cp != null && cp.isJsonPrimitive()) provisoire.add(cp.getAsString());
        else if (cp != null && cp.isJsonArray()) for (JsonElement e : cp.getAsJsonArray()) provisoire.add(e.getAsString());
        // hall et couloirs : a la racine, ou dans l'objet « plan » ({"hall": boite, "couloirs": {"nord": boite, ...}})
        JsonObject pl = Json.obj(o, "plan");
        JsonElement hall = o.has("hall") ? o.get("hall") : pl != null ? pl.get("hall") : null;
        JsonElement couloirs = o.has("couloirs") ? o.get("couloirs") : pl != null ? pl.get("couloirs") : null;
        return new Plan(tx, ty, tz, salles, entrees, Boite.liste(o.get("interieur")), Boite.liste(hall), Boite.liste(couloirs), provisoire);
    }

    @Nullable
    private static Entree entree(String id, JsonElement e) {
        if (!e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        JsonElement a = o.get("arrivee");
        if (a == null || !a.isJsonArray() || a.getAsJsonArray().size() < 3) return null;
        JsonArray arr = a.getAsJsonArray();
        double x = arr.get(0).getAsDouble(), y = arr.get(1).getAsDouble(), z = arr.get(2).getAsDouble();
        float yaw = arr.size() > 3 ? arr.get(3).getAsFloat() : 0f;
        // coordonnees de bloc entieres : centre du bloc
        if (x == Math.floor(x)) x += 0.5;
        if (z == Math.floor(z)) z += 0.5;
        int[] sortie = null;
        JsonElement s = o.get("sortie");
        if (s != null && s.isJsonArray() && s.getAsJsonArray().size() >= 3) {
            JsonArray sa = s.getAsJsonArray();
            sortie = new int[]{sa.get(0).getAsInt(), sa.get(1).getAsInt(), sa.get(2).getAsInt()};
        }
        return new Entree(id.toLowerCase(Locale.ROOT), x, y, z, yaw, Boite.lire(o.get("porte")), Boite.lire(o.get("sas")), sortie);
    }

    // ------------------------------------------------------------------ reseau
    private static void boites(FriendlyByteBuf b, List<Boite> l) {
        b.writeVarInt(l.size());
        for (Boite x : l) x.write(b);
    }

    private static List<Boite> boites(FriendlyByteBuf b) {
        int n = Math.min(256, b.readVarInt());
        List<Boite> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(Boite.read(b));
        return l;
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(tx);
        b.writeVarInt(ty);
        b.writeVarInt(tz);
        b.writeVarInt(salles.size());
        for (Salle s : salles) {
            b.writeUtf(s.id, 32);
            b.writeUtf(s.nomFr, 64);
            b.writeUtf(s.nomEn, 64);
            boites(b, s.interieur);
            boites(b, s.cloison);
            b.writeVarLong(s.cout);
            b.writeBoolean(s.depart);
        }
        b.writeVarInt(entrees.size());
        for (Entree e : entrees.values()) {
            b.writeUtf(e.id, 16);
            b.writeDouble(e.x);
            b.writeDouble(e.y);
            b.writeDouble(e.z);
            b.writeFloat(e.yaw);
            b.writeBoolean(e.porte != null);
            if (e.porte != null) e.porte.write(b);
            b.writeBoolean(e.sas != null);
            if (e.sas != null) e.sas.write(b);
        }
        boites(b, interieur);
        boites(b, hall);
        boites(b, couloirs);
    }

    public static Plan read(FriendlyByteBuf b) {
        int tx = b.readVarInt(), ty = b.readVarInt(), tz = b.readVarInt();
        int n = Math.min(64, b.readVarInt());
        List<Salle> salles = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = b.readUtf(32), fr = b.readUtf(64), en = b.readUtf(64);
            List<Boite> in = boites(b), cl = boites(b);
            salles.add(new Salle(id, fr, en, in, cl, b.readVarLong(), b.readBoolean()));
        }
        int m = Math.min(8, b.readVarInt());
        Map<String, Entree> entrees = new LinkedHashMap<>();
        for (int i = 0; i < m; i++) {
            String id = b.readUtf(16);
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            float yaw = b.readFloat();
            Boite porte = b.readBoolean() ? Boite.read(b) : null;
            Boite sas = b.readBoolean() ? Boite.read(b) : null;
            entrees.put(id, new Entree(id, x, y, z, yaw, porte, sas, null));
        }
        List<Boite> in = boites(b), hall = boites(b), couloirs = boites(b);
        return new Plan(tx, ty, tz, salles, entrees, in, hall, couloirs, Set.of());
    }
}
