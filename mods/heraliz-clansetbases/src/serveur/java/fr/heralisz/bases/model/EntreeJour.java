package fr.heralisz.bases.model;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Entree de pillage du jour d'un joueur (BASES_SPEC §4 : une entree par joueur et par jour, remise a zero a 6 h ;
 * mort = pas de retour). Remplie par le module pillage, persistee dans bases.json.
 *
 * @param jour cle du « jour de pillage » ({@code Fenetre.jour()}, ex. « 2026-10-01 » : change a l'heure de reset)
 * @param base clan dont la base a ete pillee
 * @param mort le joueur est mort pendant ce pillage
 * @param le   heure d'entree (epoch ms)
 */
public record EntreeJour(UUID joueur, String jour, @Nullable UUID base, boolean mort, long le) {
    public EntreeJour avecMort() {
        return new EntreeJour(joueur, jour, base, true, le);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("joueur", joueur.toString());
        o.addProperty("jour", jour);
        if (base != null) o.addProperty("base", base.toString());
        o.addProperty("mort", mort);
        o.addProperty("le", le);
        return o;
    }

    @Nullable
    public static EntreeJour fromJson(JsonObject o) {
        UUID j = Json.uuid(o, "joueur");
        if (j == null) return null;
        return new EntreeJour(j, Json.str(o, "jour", ""), Json.uuid(o, "base"), Json.b(o, "mort", false), Json.lng(o, "le", 0));
    }
}
