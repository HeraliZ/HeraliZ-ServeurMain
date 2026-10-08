package fr.heralisz.clans.model;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Invitation en attente dans un clan : l'invite, l'auteur (et son nom, pour l'affichage), l'expiration (ms epoch,
 * 5 min apres l'envoi).
 */
public record Invitation(UUID invite, UUID auteur, String auteurNom, long expireLe) {
    public static final long DUREE_MS = 5 * 60 * 1000L;

    public boolean expiree(long maintenant) {
        return maintenant >= expireLe;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("invite", invite.toString());
        o.addProperty("auteur", auteur.toString());
        o.addProperty("auteurNom", auteurNom);
        o.addProperty("expireLe", expireLe);
        return o;
    }

    /** Null si un uuid manque. */
    public static Invitation fromJson(JsonObject o) {
        UUID i = Json.uuid(o, "invite"), a = Json.uuid(o, "auteur");
        if (i == null || a == null) return null;
        return new Invitation(i, a, Json.str(o, "auteurNom", "?"), Json.lng(o, "expireLe", 0));
    }
}
