package fr.heralisz.clans.model;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Ligne du journal de la banque : quand (ms epoch), qui (uuid + nom affiche ; uuid null = systeme/quete),
 * montant signe (+ depot, - retrait), motif (cle courte : "depot", "retrait", "quete", "creation", "dissolution"...).
 */
public record Mouvement(long quand, @Nullable UUID qui, String nom, long montant, String motif) {
    public static final int MAX = 30;
    /** Longueurs maximales du nom affiche et du motif (reseau et stockage : coupees a la construction). */
    public static final int NOM_MAX = 48, MOTIF_MAX = 64;

    public Mouvement {
        nom = Texte.couper(nom, NOM_MAX);
        motif = Texte.couper(motif, MOTIF_MAX);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("quand", quand);
        if (qui != null) o.addProperty("qui", qui.toString());
        o.addProperty("nom", nom);
        o.addProperty("montant", montant);
        o.addProperty("motif", motif);
        return o;
    }

    public static Mouvement fromJson(JsonObject o) {
        return new Mouvement(Json.lng(o, "quand", 0), Json.uuid(o, "qui"), Json.str(o, "nom", ""), Json.lng(o, "montant", 0),
                Json.str(o, "motif", ""));
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarLong(quand);
        b.writeBoolean(qui != null);
        if (qui != null) b.writeUUID(qui);
        Texte.utf(b, nom, NOM_MAX);
        b.writeLong(montant);
        Texte.utf(b, motif, MOTIF_MAX);
    }

    public static Mouvement read(FriendlyByteBuf b) {
        long quand = b.readVarLong();
        UUID qui = b.readBoolean() ? b.readUUID() : null;
        return new Mouvement(quand, qui, b.readUtf(48), b.readLong(), b.readUtf(64));
    }
}
