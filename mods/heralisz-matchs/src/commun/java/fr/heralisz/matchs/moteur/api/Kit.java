package fr.heralisz.matchs.moteur.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classe (kit) de match, immuable (MATCHS_SPEC §1.7, §2.5, §3). Kits fixes lus dans
 * {@code data/heralisz_matchs/kits/<id>.json} ({@link Kits}) ; les classes personnalisees des duels construisent un
 * Kit a la volee (meme record, jamais de camo ni de skin). Format JSON :
 * <pre>
 * {"id": "assaut", "nom_fr": "Assaut", "nom_en": "Assault", "classe": "assaut", "modes": ["clashwar"],
 *  "principale": {"gun": "tacz:ak47", "accessoires": {"scope": "tacz:sight_xxx"}, "mode_tir": "AUTO", "reserve": 180},
 *  "secondaire": {"gun": "tacz:glock_17", "reserve": 60},
 *  "melee": "heralisz_tactical:couteau",
 *  "soins": [{"id": "heralisz_tactical:pansement", "nombre": 2}, {"id": "heralisz_tactical:poche_sang", "nombre": 1}],
 *  "grenades": [{"id": "heralisz_tactical:fumigene", "nombre": 1}],
 *  "armure": {"kit": "kit_siberie"}        (ou {"pieces": ["heraliz_armory:siberie_head", ...]}),
 *  "jauges": {"portee": 0.7, "cadence": 0.6, "mobilite": 0.5}}
 * </pre>
 * Les ids sont verifies a la distribution ({@link Equipement}) : un id absent est ignore avec un avertissement.
 * Tenue : {@code armure.kit} = tenue complete de HeraliZ Armurerie ({@code kit_<ensemble>} ou {@code <ensemble>}, voir
 * {@code moteur.compat.Armures}) ; une tenue inconnue (ancien kit heralisz_armor) donne la tenue par defaut, Siberie.
 *
 * @param id           identifiant (= nom du fichier pour les kits fixes, "perso:<uuid>:<n>" pour les classes de duel)
 * @param nomFr        nom affiche en francais
 * @param nomEn        nom affiche en anglais
 * @param classe       categorie pour les limites et l'icone : {@code CLASSE_*} (minuscules)
 * @param modes        modes ou le kit est propose ("clashwar", "duel", "zombies"), vide = tous
 * @param principale   arme principale, ou null
 * @param secondaire   arme secondaire, ou null
 * @param melee        id d'objet de melee, ou null
 * @param soins        soins (id + nombre)
 * @param grenades     grenades letales et tactiques (id + nombre)
 * @param armureKit    tenue de HeraliZ Armurerie ({@code kit_<ensemble>}), ou null
 * @param armurePieces ids des pieces d'armure (si pas de kit)
 * @param portee       jauge 0..1 de l'ecran de choix
 * @param cadence      jauge 0..1
 * @param mobilite     jauge 0..1
 */
public record Kit(String id, String nomFr, String nomEn, String classe, List<String> modes, @Nullable Arme principale,
                  @Nullable Arme secondaire, @Nullable String melee, List<Objet> soins, List<Objet> grenades,
                  @Nullable String armureKit, List<String> armurePieces, float portee, float cadence, float mobilite) {
    public static final String CLASSE_ASSAUT = "assaut", CLASSE_SMG = "smg", CLASSE_POMPE = "pompe", CLASSE_SNIPER = "sniper",
            CLASSE_TACTIQUE = "tactique", CLASSE_PERSO = "perso", CLASSE_ZOMBIES = "zombies";

    public Kit {
        modes = List.copyOf(modes);
        soins = List.copyOf(soins);
        grenades = List.copyOf(grenades);
        armurePieces = List.copyOf(armurePieces);
        portee = borne(portee);
        cadence = borne(cadence);
        mobilite = borne(mobilite);
    }

    /**
     * Arme TaCZ (donnee par {@code GunItemBuilder}, munitions en reserve virtuelle {@code DummyAmmo}/{@code MaxDummyAmmo},
     * accessoires verrouilles {@code AttachmentLock}).
     *
     * @param gunId       id TaCZ de l'arme ("tacz:ak47")
     * @param accessoires type d'emplacement TaCZ en minuscules (scope, muzzle, stock, grip, laser, extended_mag) -> id
     * @param modeTir     mode de tir TaCZ (AUTO, SEMI, BURST) ou null = defaut de l'arme
     * @param reserve     munitions en reserve virtuelle (0 = chargeur seul)
     */
    public record Arme(String gunId, Map<String, String> accessoires, @Nullable String modeTir, int reserve) {
        public Arme {
            accessoires = Collections.unmodifiableMap(new LinkedHashMap<>(accessoires));
            reserve = Math.max(0, reserve);
        }

        @Nullable
        static Arme lire(@Nullable JsonElement e) {
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            if (!o.has("gun")) return null;
            Map<String, String> acc = new LinkedHashMap<>();
            if (o.has("accessoires") && o.get("accessoires").isJsonObject()) {
                for (Map.Entry<String, JsonElement> a : o.getAsJsonObject("accessoires").entrySet()) {
                    acc.put(a.getKey().toLowerCase(Locale.ROOT), a.getValue().getAsString());
                }
            }
            return new Arme(o.get("gun").getAsString(), acc, o.has("mode_tir") ? o.get("mode_tir").getAsString() : null,
                    o.has("reserve") ? o.get("reserve").getAsInt() : 0);
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("gun", gunId);
            if (!accessoires.isEmpty()) {
                JsonObject a = new JsonObject();
                accessoires.forEach(a::addProperty);
                o.add("accessoires", a);
            }
            if (modeTir != null) o.addProperty("mode_tir", modeTir);
            o.addProperty("reserve", reserve);
            return o;
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(gunId, 128);
            buf.writeVarInt(accessoires.size());
            accessoires.forEach((k, v) -> {
                buf.writeUtf(k, 32);
                buf.writeUtf(v, 128);
            });
            buf.writeUtf(modeTir == null ? "" : modeTir, 16);
            buf.writeVarInt(reserve);
        }

        static Arme read(FriendlyByteBuf buf) {
            String gun = buf.readUtf(128);
            int n = Math.min(8, buf.readVarInt());
            Map<String, String> acc = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) acc.put(buf.readUtf(32), buf.readUtf(128));
            String mode = buf.readUtf(16);
            return new Arme(gun, acc, mode.isEmpty() ? null : mode, buf.readVarInt());
        }
    }

    /** Objet simple (soin, grenade) : id de registre + nombre. */
    public record Objet(String id, int nombre) {
        public Objet {
            nombre = Math.max(1, Math.min(64, nombre));
        }
    }

    /** Nom dans la langue demandee. */
    public String nom(boolean francais) {
        return francais ? nomFr : nomEn;
    }

    /** Vrai si le kit est propose dans ce mode (liste vide = tous). */
    public boolean pourMode(String mode) {
        return modes.isEmpty() || modes.contains(mode);
    }

    // ------------------------------------------------------------------ JSON
    /** Lit un kit ; rend null (avec un avertissement) si le JSON est inutilisable. */
    @Nullable
    public static Kit lire(String idParDefaut, JsonObject o) {
        try {
            String id = o.has("id") ? o.get("id").getAsString() : idParDefaut;
            String nomFr = o.has("nom_fr") ? o.get("nom_fr").getAsString() : id;
            String nomEn = o.has("nom_en") ? o.get("nom_en").getAsString() : nomFr;
            String classe = o.has("classe") ? o.get("classe").getAsString().toLowerCase(Locale.ROOT) : CLASSE_ASSAUT;
            List<String> modes = chaines(o.get("modes"));
            String armureKit = null;
            List<String> pieces = List.of();
            if (o.has("armure") && o.get("armure").isJsonObject()) {
                JsonObject a = o.getAsJsonObject("armure");
                if (a.has("kit")) armureKit = a.get("kit").getAsString();
                pieces = chaines(a.get("pieces"));
            }
            float portee = 0.5f, cadence = 0.5f, mobilite = 0.5f;
            if (o.has("jauges") && o.get("jauges").isJsonObject()) {
                JsonObject j = o.getAsJsonObject("jauges");
                if (j.has("portee")) portee = j.get("portee").getAsFloat();
                if (j.has("cadence")) cadence = j.get("cadence").getAsFloat();
                if (j.has("mobilite")) mobilite = j.get("mobilite").getAsFloat();
            }
            return new Kit(id, nomFr, nomEn, classe, modes, Arme.lire(o.get("principale")), Arme.lire(o.get("secondaire")),
                    o.has("melee") ? o.get("melee").getAsString() : null, objets(o.get("soins")), objets(o.get("grenades")),
                    armureKit, pieces, portee, cadence, mobilite);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.warn("[matchs] kit {} illisible : {}", idParDefaut, ex.toString());
            return null;
        }
    }

    /** JSON au format de {@link #lire} (sauvegarde des classes personnalisees). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom_fr", nomFr);
        o.addProperty("nom_en", nomEn);
        o.addProperty("classe", classe);
        if (!modes.isEmpty()) {
            JsonArray m = new JsonArray();
            modes.forEach(m::add);
            o.add("modes", m);
        }
        if (principale != null) o.add("principale", principale.toJson());
        if (secondaire != null) o.add("secondaire", secondaire.toJson());
        if (melee != null) o.addProperty("melee", melee);
        o.add("soins", objetsJson(soins));
        o.add("grenades", objetsJson(grenades));
        JsonObject a = new JsonObject();
        if (armureKit != null) a.addProperty("kit", armureKit);
        if (!armurePieces.isEmpty()) {
            JsonArray p = new JsonArray();
            armurePieces.forEach(p::add);
            a.add("pieces", p);
        }
        o.add("armure", a);
        JsonObject j = new JsonObject();
        j.addProperty("portee", portee);
        j.addProperty("cadence", cadence);
        j.addProperty("mobilite", mobilite);
        o.add("jauges", j);
        return o;
    }

    // ------------------------------------------------------------------ reseau
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 96);
        buf.writeUtf(nomFr, 64);
        buf.writeUtf(nomEn, 64);
        buf.writeUtf(classe, 32);
        buf.writeVarInt(modes.size());
        for (String m : modes) buf.writeUtf(m, 32);
        buf.writeBoolean(principale != null);
        if (principale != null) principale.write(buf);
        buf.writeBoolean(secondaire != null);
        if (secondaire != null) secondaire.write(buf);
        buf.writeUtf(melee == null ? "" : melee, 128);
        ecrireObjets(buf, soins);
        ecrireObjets(buf, grenades);
        buf.writeUtf(armureKit == null ? "" : armureKit, 64);
        buf.writeVarInt(armurePieces.size());
        for (String p : armurePieces) buf.writeUtf(p, 128);
        buf.writeFloat(portee);
        buf.writeFloat(cadence);
        buf.writeFloat(mobilite);
    }

    public static Kit read(FriendlyByteBuf buf) {
        String id = buf.readUtf(96), nomFr = buf.readUtf(64), nomEn = buf.readUtf(64), classe = buf.readUtf(32);
        int nm = Math.min(8, buf.readVarInt());
        List<String> modes = new ArrayList<>(nm);
        for (int i = 0; i < nm; i++) modes.add(buf.readUtf(32));
        Arme p = buf.readBoolean() ? Arme.read(buf) : null;
        Arme s = buf.readBoolean() ? Arme.read(buf) : null;
        String melee = buf.readUtf(128);
        List<Objet> soins = lireObjets(buf), grenades = lireObjets(buf);
        String armure = buf.readUtf(64);
        int np = Math.min(8, buf.readVarInt());
        List<String> pieces = new ArrayList<>(np);
        for (int i = 0; i < np; i++) pieces.add(buf.readUtf(128));
        float portee = buf.readFloat(), cadence = buf.readFloat(), mobilite = buf.readFloat();
        return new Kit(id, nomFr, nomEn, classe, modes, p, s, melee.isEmpty() ? null : melee, soins, grenades,
                armure.isEmpty() ? null : armure, pieces, portee, cadence, mobilite);
    }

    // ------------------------------------------------------------------ aides
    private static float borne(float v) {
        return Float.isNaN(v) ? 0.5f : Math.max(0f, Math.min(1f, v));
    }

    private static List<String> chaines(@Nullable JsonElement e) {
        List<String> l = new ArrayList<>();
        if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) l.add(x.getAsString().toLowerCase(Locale.ROOT));
        return l;
    }

    private static List<Objet> objets(@Nullable JsonElement e) {
        List<Objet> l = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return l;
        for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonPrimitive()) l.add(new Objet(x.getAsString(), 1));
            else if (x.isJsonObject() && x.getAsJsonObject().has("id")) {
                JsonObject o = x.getAsJsonObject();
                l.add(new Objet(o.get("id").getAsString(), o.has("nombre") ? o.get("nombre").getAsInt() : 1));
            }
        }
        return l;
    }

    private static JsonArray objetsJson(List<Objet> l) {
        JsonArray a = new JsonArray();
        for (Objet ob : l) {
            JsonObject o = new JsonObject();
            o.addProperty("id", ob.id());
            o.addProperty("nombre", ob.nombre());
            a.add(o);
        }
        return a;
    }

    private static void ecrireObjets(FriendlyByteBuf buf, List<Objet> l) {
        buf.writeVarInt(l.size());
        for (Objet o : l) {
            buf.writeUtf(o.id(), 128);
            buf.writeVarInt(o.nombre());
        }
    }

    private static List<Objet> lireObjets(FriendlyByteBuf buf) {
        int n = Math.min(16, buf.readVarInt());
        List<Objet> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Objet(buf.readUtf(128), buf.readVarInt()));
        return l;
    }
}
