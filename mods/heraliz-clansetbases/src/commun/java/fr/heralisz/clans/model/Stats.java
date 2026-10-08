package fr.heralisz.clans.model;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Statistiques d'un membre (cumulees depuis son arrivee dans le clan). Mutables cote serveur ; copiees dans le
 * snapshot pour le client.
 */
public final class Stats {
    public int kills, morts, zombies, boss, lootOuverts, captures, ravitaillements;
    public long tempsJeuTicks;
    public double degats;
    public long xpApporte;

    public Stats() {
    }

    public Stats(int kills, int morts, int zombies, int boss, int lootOuverts, int captures, int ravitaillements,
                 long tempsJeuTicks, double degats, long xpApporte) {
        this.kills = kills;
        this.morts = morts;
        this.zombies = zombies;
        this.boss = boss;
        this.lootOuverts = lootOuverts;
        this.captures = captures;
        this.ravitaillements = ravitaillements;
        this.tempsJeuTicks = tempsJeuTicks;
        this.degats = degats;
        this.xpApporte = xpApporte;
    }

    /** Ratio kills / morts (morts comptees au moins 1). */
    public double kd() {
        return kills / (double) Math.max(1, morts);
    }

    public Stats copie() {
        return new Stats(kills, morts, zombies, boss, lootOuverts, captures, ravitaillements, tempsJeuTicks, degats, xpApporte);
    }

    /** Additionne les stats d'un autre membre (totaux du clan). */
    public void ajouter(Stats s) {
        kills += s.kills;
        morts += s.morts;
        zombies += s.zombies;
        boss += s.boss;
        lootOuverts += s.lootOuverts;
        captures += s.captures;
        ravitaillements += s.ravitaillements;
        tempsJeuTicks += s.tempsJeuTicks;
        degats += s.degats;
        xpApporte += s.xpApporte;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("kills", kills);
        o.addProperty("morts", morts);
        o.addProperty("zombies", zombies);
        o.addProperty("boss", boss);
        o.addProperty("lootOuverts", lootOuverts);
        o.addProperty("captures", captures);
        o.addProperty("ravitaillements", ravitaillements);
        o.addProperty("tempsJeuTicks", tempsJeuTicks);
        o.addProperty("degats", degats);
        o.addProperty("xpApporte", xpApporte);
        return o;
    }

    public static Stats fromJson(JsonObject o) {
        return new Stats(Json.integer(o, "kills", 0), Json.integer(o, "morts", 0), Json.integer(o, "zombies", 0),
                Json.integer(o, "boss", 0), Json.integer(o, "lootOuverts", 0), Json.integer(o, "captures", 0),
                Json.integer(o, "ravitaillements", 0), Json.lng(o, "tempsJeuTicks", 0), Json.dbl(o, "degats", 0),
                Json.lng(o, "xpApporte", 0));
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(kills);
        b.writeVarInt(morts);
        b.writeVarInt(zombies);
        b.writeVarInt(boss);
        b.writeVarInt(lootOuverts);
        b.writeVarInt(captures);
        b.writeVarInt(ravitaillements);
        b.writeVarLong(tempsJeuTicks);
        b.writeDouble(degats);
        b.writeVarLong(xpApporte);
    }

    public static Stats read(FriendlyByteBuf b) {
        return new Stats(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarLong(), b.readDouble(), b.readVarLong());
    }
}
