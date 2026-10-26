package fr.heralisz.economie.api;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Une ligne du journal d'un compte.
 *
 * @param quand horodatage (ms, System.currentTimeMillis)
 * @param delta variation du solde (positif = credit, negatif = debit)
 * @param apres solde juste apres la transaction
 * @param motif chaine libre prefixee (« clan:banque », « quete:… », « admin », « paiement », « ravitaillement »…)
 * @param autre l'autre joueur concerne (paiement, transfert), null sinon
 */
public record Transaction(long quand, long delta, long apres, String motif, @Nullable UUID autre) {
    /** Longueur maximale conservee pour un motif (le reste est coupe). */
    public static final int MOTIF_MAX = 64;

    public Transaction {
        if (motif == null) motif = "";
        if (motif.length() > MOTIF_MAX) motif = motif.substring(0, MOTIF_MAX);
    }

    /** Prefixe du motif (avant le premier « : »), en minuscules : « clan », « quete », « admin »… */
    public String prefixe() {
        int i = motif.indexOf(':');
        return (i < 0 ? motif : motif.substring(0, i)).trim().toLowerCase(java.util.Locale.ROOT);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("quand", quand);
        o.addProperty("delta", delta);
        o.addProperty("apres", apres);
        o.addProperty("motif", motif);
        if (autre != null) o.addProperty("autre", autre.toString());
        return o;
    }

    @Nullable
    public static Transaction fromJson(JsonObject o) {
        try {
            UUID autre = o.has("autre") && !o.get("autre").isJsonNull() ? UUID.fromString(o.get("autre").getAsString()) : null;
            return new Transaction(o.get("quand").getAsLong(), o.get("delta").getAsLong(), o.get("apres").getAsLong(),
                    o.has("motif") ? o.get("motif").getAsString() : "", autre);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
