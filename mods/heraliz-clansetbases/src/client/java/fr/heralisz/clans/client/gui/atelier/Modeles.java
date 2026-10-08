package fr.heralisz.clans.client.gui.atelier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Les 16 modeles prets a l'emploi ({@code assets/heralisz_clans/banniere/modeles.json}, produit par
 * tools/clans/formes_art.py) : {@code [{id, nom_fr, nom_en, fond: {a, b, degrade}, calques: [...]}]}. Lus une fois
 * depuis le jar ; chaque modele devient une {@link Banniere} (memes bornes et valeurs par defaut que le JSON des clans).
 * Fichier absent ou illisible : liste vide et un avertissement unique.
 */
public final class Modeles {
    private Modeles() {
    }

    public static final String CHEMIN = "/assets/heralisz_clans/banniere/modeles.json";

    /** Un modele : identifiant, banniere, nom affiche (cle modele.heralisz_clans.&lt;id&gt;). */
    public record Modele(String id, Banniere banniere, Component nom) {
    }

    @Nullable
    private static List<Modele> liste;

    /** Tous les modeles, dans l'ordre du fichier (liste non modifiable). */
    public static List<Modele> tous() {
        if (liste == null) liste = charger();
        return liste;
    }

    /** Modele par identifiant (insensible a la casse), null s'il n'existe pas. */
    @Nullable
    public static Modele parId(String id) {
        for (Modele m : tous()) if (m.id().equalsIgnoreCase(id)) return m;
        return null;
    }

    private static List<Modele> charger() {
        List<Modele> l = new ArrayList<>();
        try (InputStream in = Modeles.class.getResourceAsStream(CHEMIN)) {
            if (in == null) {
                HeraliszClans.LOG.warn("[clans] modeles de banniere absents ({})", CHEMIN);
                return Collections.emptyList();
            }
            JsonElement racine = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            JsonArray tab = racine.isJsonArray() ? racine.getAsJsonArray()
                    : racine.isJsonObject() && racine.getAsJsonObject().has("modeles") ? racine.getAsJsonObject().getAsJsonArray("modeles") : new JsonArray();
            for (JsonElement e : tab) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                if (id.isEmpty()) continue;
                // le format des modeles ({fond: {a, b, degrade}, calques}) est ramene au JSON d'une banniere v2
                JsonObject ban = new JsonObject();
                JsonObject fond = o.has("fond") && o.get("fond").isJsonObject() ? o.getAsJsonObject("fond") : new JsonObject();
                if (fond.has("a")) ban.add("couleurA", fond.get("a"));
                if (fond.has("b")) ban.add("couleurB", fond.get("b"));
                else if (fond.has("a")) ban.add("couleurB", fond.get("a"));
                if (fond.has("degrade")) ban.add("degrade", fond.get("degrade"));
                ban.add("calques", o.has("calques") && o.get("calques").isJsonArray() ? o.getAsJsonArray("calques") : new JsonArray());
                Banniere b = Banniere.fromJson(ban);
                l.add(new Modele(id, b, Ui.tr(Ui.FONT, "modele.heralisz_clans." + id)));
            }
        } catch (Exception ex) {
            HeraliszClans.LOG.error("[clans] modeles de banniere illisibles ({})", CHEMIN, ex);
        }
        return Collections.unmodifiableList(l);
    }
}
