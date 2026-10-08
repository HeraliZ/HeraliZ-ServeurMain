package fr.heralisz.matchs.clashwar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Donnees globales du ClashWar qui survivent aux redemarrages ({@code <serveur>/heralisz_matchs/clashwar_etat.json},
 * JSON atomique) : numero de la derniere edition, rang dans la rotation des formats, dernier champion (affiche sur le
 * tableau entre deux editions), cles de caisse en attente (joueurs gagnants deconnectes a la fin). Fil serveur.
 */
public final class CwEtat {
    private CwEtat() {
    }

    /** Numero de la derniere edition lancee. */
    static int edition;
    /** Rang du prochain format dans la rotation. */
    static int rotation;
    /** Dernier champion : tag, nom du clan, numero d'equipe, banniere, edition, format ; null = aucun. */
    @Nullable
    static Champion champion;
    /** Cles de caisse a donner a la prochaine connexion (uuid -> nombre). */
    static final Map<UUID, Integer> CLES = new LinkedHashMap<>();

    /** Dernier champion connu. */
    public record Champion(String tag, String nom, int numero, int couleur, Banniere banniere, int edition, String format,
                           long quandMs) {
    }

    private static Path fichier(MinecraftServer s) {
        return CwConfig.dossier(s).resolve("clashwar_etat.json");
    }

    static void charger(MinecraftServer s) {
        edition = 0;
        rotation = 0;
        champion = null;
        CLES.clear();
        Path f = fichier(s);
        if (!Files.isRegularFile(f)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            edition = o.has("edition") ? o.get("edition").getAsInt() : 0;
            rotation = o.has("rotation") ? o.get("rotation").getAsInt() : 0;
            if (o.has("champion") && o.get("champion").isJsonObject()) {
                JsonObject c = o.getAsJsonObject("champion");
                champion = new Champion(c.get("tag").getAsString(), c.get("nom").getAsString(), c.get("numero").getAsInt(),
                        c.has("couleur") ? c.get("couleur").getAsInt() : 0x6FA8FF,
                        c.has("banniere") ? Banniere.fromJson(c.getAsJsonObject("banniere")) : Banniere.DEFAUT,
                        c.get("edition").getAsInt(), c.get("format").getAsString(), c.has("quand") ? c.get("quand").getAsLong() : 0L);
            }
            if (o.has("cles") && o.get("cles").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("cles").entrySet()) {
                    try {
                        CLES.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    } catch (IllegalArgumentException ignore) {
                        // entree corrompue : ignoree
                    }
                }
            }
        } catch (Exception ex) {
            HeraliszMatchs.LOG.error("[clashwar] {} illisible", f, ex);
        }
    }

    static void sauver(MinecraftServer s) {
        JsonObject o = new JsonObject();
        o.addProperty("edition", edition);
        o.addProperty("rotation", rotation);
        if (champion != null) {
            JsonObject c = new JsonObject();
            c.addProperty("tag", champion.tag());
            c.addProperty("nom", champion.nom());
            c.addProperty("numero", champion.numero());
            c.addProperty("couleur", champion.couleur());
            c.add("banniere", champion.banniere().toJson());
            c.addProperty("edition", champion.edition());
            c.addProperty("format", champion.format());
            c.addProperty("quand", champion.quandMs());
            o.add("champion", c);
        }
        JsonObject cles = new JsonObject();
        CLES.forEach((u, n) -> cles.addProperty(u.toString(), n));
        o.add("cles", cles);
        CwConfig.ecrireAtomique(fichier(s), CwConfig.gson().toJson(o));
    }
}
