package fr.heralisz.bases.model;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * Ligne du journal de pillage d'une base (BASES_SPEC §4) : rempli par le module pillage, persiste dans bases.json,
 * affiche par l'onglet BASE (« derniers pillages »). {@code fin} = 0 tant que le pillage est en cours.
 */
public final class Pillage {
    public enum Issue {
        /** Pillards encore dans la base. */
        EN_COURS,
        /** Fin de pillage avec au moins un pillard sorti vivant (ou renvoye a 23 h). */
        PILLEE,
        /** Tous les pillards sont morts. */
        DEFENDUE
    }

    public long debut;
    public long fin;
    @Nullable
    public UUID clanAttaquant;
    public String tagAttaquant;
    /** Nombre de pillards entres. */
    public int joueurs;
    public Issue issue;

    public Pillage(long debut, @Nullable UUID clanAttaquant, String tagAttaquant) {
        this.debut = debut;
        this.clanAttaquant = clanAttaquant;
        this.tagAttaquant = tagAttaquant == null ? "" : tagAttaquant;
        this.issue = Issue.EN_COURS;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("debut", debut);
        o.addProperty("fin", fin);
        if (clanAttaquant != null) o.addProperty("clanAttaquant", clanAttaquant.toString());
        o.addProperty("tagAttaquant", tagAttaquant);
        o.addProperty("joueurs", joueurs);
        o.addProperty("issue", issue.name());
        return o;
    }

    public static Pillage fromJson(JsonObject o) {
        Pillage p = new Pillage(Json.lng(o, "debut", 0), Json.uuid(o, "clanAttaquant"), Json.str(o, "tagAttaquant", ""));
        p.fin = Json.lng(o, "fin", 0);
        p.joueurs = Math.max(0, Json.i(o, "joueurs", 0));
        try {
            p.issue = Issue.valueOf(Json.str(o, "issue", "PILLEE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            p.issue = Issue.PILLEE;
        }
        return p;
    }

    public void write(FriendlyByteBuf b) {
        b.writeLong(debut);
        b.writeLong(fin);
        b.writeUtf(tagAttaquant, 8);
        b.writeVarInt(joueurs);
        b.writeByte(issue.ordinal());
    }

    public static Pillage read(FriendlyByteBuf b) {
        long debut = b.readLong(), fin = b.readLong();
        Pillage p = new Pillage(debut, null, b.readUtf(8));
        p.fin = fin;
        p.joueurs = b.readVarInt();
        int i = b.readByte();
        p.issue = i >= 0 && i < Issue.values().length ? Issue.values()[i] : Issue.PILLEE;
        return p;
    }
}
