package fr.heralisz.clans.model;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Ce qu'on garde sur un joueur en dehors de son clan (joueurs.json) : le clan actuel, la date de son dernier depart
 * (delai avant de rejoindre un autre clan), son solde de l'economie interne, le mode chat de clan (/cc).
 */
public final class LienJoueur {
    public final UUID joueur;
    @Nullable
    public UUID clan;
    public long quitteLe;
    public long soldeInterne;
    public boolean chatClan;
    /** Dernier pseudo connu (commandes admin hors ligne, classement). */
    public String dernierNom = "";

    public LienJoueur(UUID joueur) {
        this.joueur = joueur;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("joueur", joueur.toString());
        if (clan != null) o.addProperty("clan", clan.toString());
        o.addProperty("quitteLe", quitteLe);
        o.addProperty("soldeInterne", soldeInterne);
        o.addProperty("chatClan", chatClan);
        o.addProperty("nom", dernierNom);
        return o;
    }

    public static LienJoueur fromJson(JsonObject o) {
        UUID id = Json.uuid(o, "joueur");
        if (id == null) return null;
        LienJoueur l = new LienJoueur(id);
        l.clan = Json.uuid(o, "clan");
        l.quitteLe = Json.lng(o, "quitteLe", 0);
        l.soldeInterne = Json.lng(o, "soldeInterne", 0);
        l.chatClan = Json.bool(o, "chatClan", false);
        l.dernierNom = Json.str(o, "nom", "");
        return l;
    }
}
