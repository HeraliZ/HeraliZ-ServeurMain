package fr.heraliz.quetes.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Liens avec nos autres mods, tous FACULTATIFS (réflexion : le mod de quêtes démarre sans eux) :
 * <ul>
 *    <li>hérals : {@code fr.heralisz.economie.api.EconomieApi} (clansetbases) ;</li>
 *    <li>rareté d'un objet : {@code fr.heraliz.loot.RaretesObjets.de(ItemStack)} (HeraliZ Loot) ;</li>
 *    <li>famille d'une arme TaCZ (« pompe », « fusil_556 »...) : {@code config/heraliz_tactique/armes.json} (écrit par
 *    outils/equilibrage_armes.py) ;</li>
 *    <li>usure d'une arme : étiquette {@code HzUsureEtat} de HeraliZ Utils (millièmes, absente = neuve).</li>
 * </ul>
 */
final class Ponts {
    private static final Logger LOG = LogUtils.getLogger();
    static final List<String> RARETES = List.of("commun", "peu_commun", "rare", "epique", "legendaire", "unique");

    private Ponts() {
    }

    // ------------------------------------------------------------------ hérals
    private static Class<?> eco;
    private static boolean ecoAbsente;

    private static Class<?> eco() {
        if (eco == null && !ecoAbsente) {
            try {
                eco = Class.forName("fr.heralisz.economie.api.EconomieApi");
            } catch (Throwable t) {
                ecoAbsente = true;
                LOG.warn("[heraliz_quetes] HeraliZ Économie absente : les récompenses en hérals ne seront pas versées");
            }
        }
        return eco;
    }

    static boolean economie() {
        Class<?> c = eco();
        if (c == null) return false;
        try {
            return (boolean) c.getMethod("disponible").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean deposer(UUID joueur, long montant, String motif) {
        Class<?> c = eco();
        if (c == null || montant <= 0) return false;
        try {
            if (!(boolean) c.getMethod("disponible").invoke(null)) return false;
            c.getMethod("deposer", UUID.class, long.class, String.class).invoke(null, joueur, montant, motif);
            return true;
        } catch (Throwable t) {
            LOG.error("[heraliz_quetes] versement de {} hérals à {} : {}", montant, joueur, t.toString());
            return false;
        }
    }

    static boolean retirer(UUID joueur, long montant, String motif) {
        Class<?> c = eco();
        if (c == null || montant <= 0) return false;
        try {
            if (!(boolean) c.getMethod("disponible").invoke(null)) return false;
            return (boolean) c.getMethod("retirer", UUID.class, long.class, String.class).invoke(null, joueur, montant, motif);
        } catch (Throwable t) {
            return false;
        }
    }

    /** « 1 500 » (séparateur des milliers du serveur). */
    static String nombre(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    // ------------------------------------------------------------------ raretés
    private static Method rarete;
    private static boolean rareteAbsente;

    /** Rang de rareté de l'objet (0 Commun ... 5 Unique), -1 si aucune ou si HeraliZ Loot est absent. */
    static int rarete(ItemStack s) {
        if (rareteAbsente) return -1;
        try {
            if (rarete == null) rarete = Class.forName("fr.heraliz.loot.RaretesObjets").getMethod("de", ItemStack.class);
            Object r = rarete.invoke(null, s);
            return r instanceof Enum<?> e ? e.ordinal() : -1;
        } catch (Throwable t) {
            rareteAbsente = true;
            return -1;
        }
    }

    static int rangRarete(String id) {
        return id == null ? -1 : RARETES.indexOf(id.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ armes TaCZ
    private static Map<String, String> familles;

    static void relireArmes() {
        Map<String, String> m = new HashMap<>();
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_tactique").resolve("armes.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("armes") && o.get("armes").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("armes").entrySet()) {
                        if (e.getValue().isJsonObject()) m.put(e.getKey(), Defs.chaine(e.getValue().getAsJsonObject(), "famille", ""));
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("[heraliz_quetes] lecture de {} : {}", f, ex.getMessage());
        }
        familles = m;
    }

    /** Identifiant d'arme TaCZ (« tacz:ak47 ») ou null. */
    static String gunId(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains("GunId", Tag.TAG_STRING) ? t.getString("GunId") : null;
    }

    static boolean couteau(String gunId) {
        if (gunId == null) return false;
        String p = gunId.toLowerCase(Locale.ROOT);
        return p.contains("knife") || p.contains("karambit") || p.contains("butterfly") || p.contains("bayonet") || p.endsWith(":m9");
    }

    static String famille(String gunId) {
        if (gunId == null) return "";
        if (familles == null) relireArmes();
        return familles.getOrDefault(gunId, "");
    }

    static boolean silencieux(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null || !t.contains("AttachmentMUZZLE")) return false;
        String m = t.get("AttachmentMUZZLE").toString().toLowerCase(Locale.ROOT);
        return m.contains("silencer") || m.contains("suppressor") || m.contains("silence");
    }

    /**
     * L'arme correspond-elle ? « feu » (toute arme TaCZ sauf couteaux), « melee » (mains nues, arme de mêlée, couteau),
     * « couteau », ou le début d'une famille (« pompe », « fusil », « precision », « pistolet », « smg »...).
     */
    static boolean arme(ItemStack main, String voulu) {
        if (voulu == null || voulu.isEmpty()) return true;
        String g = gunId(main);
        return switch (voulu.toLowerCase(Locale.ROOT)) {
            case "feu" -> g != null && !couteau(g);
            case "melee" -> g == null || couteau(g);
            case "couteau" -> couteau(g);
            default -> g != null && famille(g).startsWith(voulu.toLowerCase(Locale.ROOT));
        };
    }

    /** Usure de l'arme en pour cent (100 = neuve) ; 100 si ce n'est pas une arme. */
    static int usure(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null || gunId(s) == null || !t.contains("HzUsureEtat", Tag.TAG_ANY_NUMERIC)) return 100;
        return Math.max(0, Math.min(100, t.getInt("HzUsureEtat") / 10));
    }
}
