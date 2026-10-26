package fr.heraliz.clashwar.tournoi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.model.Banniere;
import fr.heraliz.clashwar.HeralizClashWar;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bannières du banc d'essai de ClashWar (corps d'origine de Simulation.bannieres) : lues par le serveur et par l'aperçu client. */
public final class BannieresBanc {
    private BannieresBanc() {
    }

    /** Les 16 modeles de banniere du mod clans (assets/heralisz_clans/banniere/modeles.json), dans l'ordre. */
    public static List<Banniere> bannieres() {
        List<Banniere> l = new ArrayList<>();
        try (InputStream in = Banniere.class.getResourceAsStream("/assets/heralisz_clans/banniere/modeles.json")) {
            if (in == null) return l;
            JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : a) {
                JsonObject m = e.getAsJsonObject();
                JsonObject fond = m.getAsJsonObject("fond");
                JsonObject o = new JsonObject();
                o.add("couleurA", fond.get("a"));
                o.add("couleurB", fond.get("b"));
                o.add("degrade", fond.get("degrade"));
                o.add("calques", m.get("calques"));
                l.add(Banniere.fromJson(o));
            }
        } catch (Exception ex) {
            HeralizClashWar.LOG.warn("[clashwar] modeles de bannieres illisibles : {}", ex.toString());
        }
        // ordre plus contraste pour le banc
        if (l.size() >= 8) {
            List<Banniere> m = new ArrayList<>();
            int[] ordre = {1, 4, 7, 10, 13, 2, 5, 8, 11, 14, 3, 6, 9, 12, 15, 0};
            for (int i : ordre) if (i < l.size()) m.add(l.get(i));
            return m;
        }
        return l;
    }
}
