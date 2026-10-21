package fr.heralisz.matchs.clashwar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classement ClashWar des clans (21/10, Matchs 0.12.0) : pour chaque clan, titres (editions gagnees), 2e et 3e places,
 * matchs joues et gagnes, eliminations et morts de ses joueurs dans les matchs du ClashWar (K/D). Fichier
 * {@code <serveur>/heralisz_matchs/clashwar_classement.json} (JSON atomique), hors du monde : il survit aux changements
 * de carte. Rang : titres, puis matchs gagnes, puis K/D. Fil serveur.
 */
public final class CwClassement {
    private CwClassement() {
    }

    /** Ligne d'un clan. Le tag et le nom sont ceux de sa derniere participation. */
    public static final class Ligne {
        public final UUID clan;
        public String tag = "", nom = "";
        public int couleur = 0x6FA8FF;
        public int titres, deuxiemes, troisiemes, matchs, matchsGagnes, eliminations, morts;

        Ligne(UUID clan) {
            this.clan = clan;
        }

        public double kd() {
            return morts == 0 ? eliminations : eliminations / (double) morts;
        }
    }

    private static final Map<UUID, Ligne> LIGNES = new LinkedHashMap<>();

    private static Path fichier(MinecraftServer s) {
        return CwConfig.dossier(s).resolve("clashwar_classement.json");
    }

    private static Ligne ligne(Tableau.EquipeT e) {
        Ligne l = LIGNES.computeIfAbsent(e.clan, Ligne::new);
        l.tag = e.tag;
        l.nom = e.nomClan;
        l.couleur = e.couleur;
        return l;
    }

    /** Fin d'un match joue (pas les forfaits) : eliminations et morts des joueurs de l'equipe. */
    static void match(Tableau.EquipeT e, int eliminations, int morts, boolean gagne) {
        Ligne l = ligne(e);
        l.matchs++;
        if (gagne) l.matchsGagnes++;
        l.eliminations += Math.max(0, eliminations);
        l.morts += Math.max(0, morts);
    }

    /** Place finale d'une equipe (1 a 4). */
    static void place(Tableau.EquipeT e, int place) {
        Ligne l = ligne(e);
        if (place == 1) l.titres++;
        else if (place == 2) l.deuxiemes++;
        else if (place == 3) l.troisiemes++;
    }

    /** Les {@code n} meilleurs clans (nouvelle liste). */
    public static List<Ligne> meilleurs(int n) {
        List<Ligne> l = new ArrayList<>(LIGNES.values());
        l.sort(Comparator.comparingInt((Ligne x) -> x.titres).thenComparingInt(x -> x.matchsGagnes).thenComparingDouble(Ligne::kd).reversed());
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    /** Rang (1 = premier) et ligne du clan, ou null s'il n'a jamais joue. */
    public static Map.Entry<Integer, Ligne> rangDe(UUID clan) {
        List<Ligne> l = meilleurs(Integer.MAX_VALUE);
        for (int i = 0; i < l.size(); i++) if (l.get(i).clan.equals(clan)) return Map.entry(i + 1, l.get(i));
        return null;
    }

    static void charger(MinecraftServer s) {
        LIGNES.clear();
        Path f = fichier(s);
        if (!Files.isRegularFile(f)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("clans") || !o.get("clans").isJsonObject()) return;
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("clans").entrySet()) {
                try {
                    JsonObject c = e.getValue().getAsJsonObject();
                    Ligne l = new Ligne(UUID.fromString(e.getKey()));
                    l.tag = c.has("tag") ? c.get("tag").getAsString() : "";
                    l.nom = c.has("nom") ? c.get("nom").getAsString() : "";
                    l.couleur = c.has("couleur") ? c.get("couleur").getAsInt() : l.couleur;
                    l.titres = entier(c, "titres");
                    l.deuxiemes = entier(c, "deuxiemes");
                    l.troisiemes = entier(c, "troisiemes");
                    l.matchs = entier(c, "matchs");
                    l.matchsGagnes = entier(c, "matchs_gagnes");
                    l.eliminations = entier(c, "eliminations");
                    l.morts = entier(c, "morts");
                    LIGNES.put(l.clan, l);
                } catch (RuntimeException ignore) {
                    // entree corrompue : ignoree
                }
            }
        } catch (Exception ex) {
            HeraliszMatchs.LOG.error("[clashwar] {} illisible", f, ex);
        }
    }

    static void sauver(MinecraftServer s) {
        JsonObject clans = new JsonObject();
        for (Ligne l : LIGNES.values()) {
            JsonObject c = new JsonObject();
            c.addProperty("tag", l.tag);
            c.addProperty("nom", l.nom);
            c.addProperty("couleur", l.couleur);
            c.addProperty("titres", l.titres);
            c.addProperty("deuxiemes", l.deuxiemes);
            c.addProperty("troisiemes", l.troisiemes);
            c.addProperty("matchs", l.matchs);
            c.addProperty("matchs_gagnes", l.matchsGagnes);
            c.addProperty("eliminations", l.eliminations);
            c.addProperty("morts", l.morts);
            clans.add(l.clan.toString(), c);
        }
        JsonObject o = new JsonObject();
        o.add("clans", clans);
        CwConfig.ecrireAtomique(fichier(s), CwConfig.gson().toJson(o));
    }

    private static int entier(JsonObject o, String cle) {
        return o.has(cle) ? Math.max(0, o.get(cle).getAsInt()) : 0;
    }
}
