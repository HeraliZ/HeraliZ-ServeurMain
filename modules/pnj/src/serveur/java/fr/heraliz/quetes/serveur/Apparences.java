package fr.heraliz.quetes.serveur;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Apparence CaraCrea d'un PNJ (le créateur de personnage du serveur), sans dépendance au mod (réflexion) :
 * <ul>
 *    <li>écrite dans le fichier du PNJ, clé {@code "apparence"} : {@code {"skin_tone": "tan", "hair_style": "buzz",
 *    "marks": ["dirt", "scar_left"], "eye_size": 3...}} (noms des choix du créateur, ou leur numéro) ;</li>
 *    <li>ou copiée du staff avec {@code /pnj apparence <id> moi} (son personnage fait dans /perso).</li>
 * </ul>
 * Format rendu : celui que CaraCrea garde pour un joueur, {@code {Format: 1, Values: octets}} (un octet par trait, dans
 * l'ordre de {@code com.caracrea.character.Trait}) ; c'est aussi ce que portent les cadavres (heraliz_corps).
 */
final class Apparences {
    private static final Logger LOG = LogUtils.getLogger();
    /** Curseurs du créateur : 0 à 4, 2 = le milieu (valeur par défaut d'un trait non écrit). */
    private static final int MILIEU = 2;

    private Apparences() {
    }

    private static boolean absent;
    private static Object[] traits;
    private static Method count, id, options, kind, nom, optionId;

    private static boolean pret() {
        if (traits != null) return true;
        if (absent) return false;
        try {
            Class<?> trait = Class.forName("com.caracrea.character.Trait");
            Class<?> catalogue = Class.forName("com.caracrea.character.Catalog");
            traits = trait.getEnumConstants();
            count = catalogue.getMethod("count", trait);
            id = catalogue.getMethod("id", trait, int.class);
            options = catalogue.getMethod("options", trait);
            kind = trait.getMethod("kind");
            nom = trait.getMethod("key");
            optionId = Class.forName("com.caracrea.character.Option").getMethod("id");
            return true;
        } catch (Throwable t) {
            absent = true;
            traits = null;
            LOG.warn("[heraliz_quetes] CaraCrea absent ou différent ({}) : les PNJ gardent leur skin", t.toString());
            return false;
        }
    }

    /** Apparence décrite dans le fichier d'un PNJ, ou null (rien d'écrit, ou CaraCrea absent). */
    static CompoundTag depuis(JsonObject o) {
        if (o == null || o.size() == 0 || !pret()) return null;
        try {
            byte[] v = new byte[traits.length];
            for (int i = 0; i < traits.length; i++) {
                Object t = traits[i];
                String k = (String) nom.invoke(t);
                String genre = kind.invoke(t).toString();
                int n = (int) count.invoke(null, t);
                int val = genre.equals("SLIDER") ? MILIEU : 0;
                JsonElement e = o.get(k);
                if (e != null) {
                    if (genre.equals("FLAGS")) val = marques(t, e);
                    else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) val = e.getAsInt();
                    else val = valeur(t, n, e.getAsString(), k);
                }
                v[i] = (byte) Math.max(0, Math.min(Math.max(0, n - 1), val));
            }
            for (String k : o.keySet()) if (!connu(k)) LOG.warn("[heraliz_quetes] apparence : « {} » n'est pas un choix de CaraCrea", k);
            CompoundTag tag = new CompoundTag();
            tag.putInt("Format", 1);
            tag.putByteArray("Values", v);
            return tag;
        } catch (Throwable t) {
            LOG.warn("[heraliz_quetes] apparence illisible : {}", t.toString());
            return null;
        }
    }

    private static boolean connu(String k) {
        try {
            for (Object t : traits) if (nom.invoke(t).equals(k)) return true;
        } catch (Throwable ignore) {
        }
        return false;
    }

    private static int valeur(Object trait, int n, String voulu, String k) throws Exception {
        String s = voulu.toLowerCase(Locale.ROOT);
        for (int i = 0; i < n; i++) if (s.equals(id.invoke(null, trait, i))) return i;
        LOG.warn("[heraliz_quetes] apparence : « {} » n'existe pas pour {}", voulu, k);
        return 0;
    }

    /** Marques (taches de rousseur, cicatrices, saleté, sang...) : liste de noms, un bit chacun. */
    private static int marques(Object trait, JsonElement e) throws Exception {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
        List<?> l = (List<?>) options.invoke(null, trait);
        JsonArray a = e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
        if (!e.isJsonArray()) a.add(e);
        int bits = 0;
        for (JsonElement x : a) {
            String s = x.getAsString().toLowerCase(Locale.ROOT);
            for (int i = 0; i < l.size(); i++) if (s.equals(optionId.invoke(l.get(i)))) bits |= 1 << i;
        }
        return bits;
    }

    /** Personnage CaraCrea d'un joueur (fait dans /perso), ou null s'il n'en a pas. */
    static CompoundTag deJoueur(Player p) {
        CompoundTag persiste = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persiste.contains("caracrea", Tag.TAG_COMPOUND)) return null;
        CompoundTag t = persiste.getCompound("caracrea").copy();
        t.remove("Chosen");
        return t.contains("Values") ? t : null;
    }

    static boolean egales(CompoundTag a, CompoundTag b) {
        return a == null ? b == null || b.isEmpty() : a.equals(b);
    }

    /** Pour les messages : les noms des choix d'une apparence (« skin_tone=tan, hair_style=buzz... »). */
    static String decrire(CompoundTag tag) {
        if (tag == null || !pret()) return "";
        byte[] v = tag.getByteArray("Values");
        StringBuilder b = new StringBuilder();
        try {
            for (int i = 0; i < Math.min(v.length, traits.length); i++) {
                String genre = kind.invoke(traits[i]).toString();
                if (!genre.equals("OPTION")) continue;
                if (b.length() > 0) b.append(", ");
                b.append(nom.invoke(traits[i])).append('=').append(id.invoke(null, traits[i], v[i] & 0xFF));
            }
        } catch (Throwable ignore) {
        }
        return b.toString();
    }
}
