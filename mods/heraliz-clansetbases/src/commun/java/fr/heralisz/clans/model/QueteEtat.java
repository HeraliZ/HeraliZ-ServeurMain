package fr.heralisz.clans.model;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Quete tiree pour un clan et sa progression (commune au clan). Les titres du catalogue voyagent avec la quete pour
 * que le client affiche celui de sa langue sans connaitre le catalogue.
 */
public final class QueteEtat {
    /** Types de quetes du catalogue config/heralisz_clans/quetes.json. */
    public enum Type {
        KILL_ZOMBIES, KILL_JOUEURS, KILL_BOSS, LOOT_OUVERTS, TEMPS_JEU_MIN, DEGATS, CAPTURES, TENIR_ZONE_MIN,
        RAVITAILLEMENTS, DEPOTS_BANQUE, DISTANCE_M;

        public String libelle() {
            return "quete.type." + name().toLowerCase();
        }

        public static Type parOrdinal(int i) {
            Type[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    public enum Periode {
        JOUR, SEMAINE;

        public static Periode parOrdinal(int i) {
            return i == 1 ? SEMAINE : JOUR;
        }
    }

    /** Longueurs maximales de l'id et des titres (reseau et stockage : coupees a la construction). */
    public static final int ID_MAX = 64, TITRE_MAX = 96;

    public final String id;
    public final Type type;
    public final Periode periode;
    public final long objectif;
    public final int xp;
    public final long argent;
    public final String titreFr, titreEn;
    public long progression;
    public boolean terminee;
    /** Quand elle a ete terminee (ms epoch), 0 sinon. */
    public long termineeLe;

    /**
     * Anciens titres par defaut (monnaie « jetons », avant clans 0.1.3) -> nouveau nom de la monnaie, les « hérals ».
     * Couvre le quetes.json deja ecrit et les quetes deja tirees dans clans.json ; un titre ecrit a la main est garde.
     */
    public static String nouveauNom(String titre) {
        return switch (titre) {
            case "Déposer 1 500 jetons" -> "Déposer 1 500 hérals";
            case "Déposer 15 000 jetons" -> "Déposer 15 000 hérals";
            case "Deposit 1,500 tokens" -> "Deposit 1,500 herals";
            case "Deposit 15,000 tokens" -> "Deposit 15,000 herals";
            default -> titre;
        };
    }

    public QueteEtat(String id, Type type, Periode periode, long objectif, int xp, long argent, String titreFr, String titreEn,
                     long progression, boolean terminee, long termineeLe) {
        this.id = Texte.couper(id, ID_MAX);
        this.type = type;
        this.periode = periode;
        this.objectif = Math.max(1, objectif);
        this.xp = xp;
        this.argent = argent;
        this.titreFr = Texte.couper(titreFr == null ? this.id : nouveauNom(titreFr), TITRE_MAX);
        this.titreEn = Texte.couper(titreEn == null ? this.titreFr : nouveauNom(titreEn), TITRE_MAX);
        this.progression = progression;
        this.terminee = terminee;
        this.termineeLe = termineeLe;
    }

    /** Titre dans la langue demandee (fr sinon en). */
    public String titre(boolean francais) {
        return francais ? titreFr : titreEn;
    }

    public float ratio() {
        return (float) Math.min(1.0, progression / (double) objectif);
    }

    public QueteEtat copie() {
        return new QueteEtat(id, type, periode, objectif, xp, argent, titreFr, titreEn, progression, terminee, termineeLe);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", type.name());
        o.addProperty("periode", periode.name());
        o.addProperty("objectif", objectif);
        o.addProperty("xp", xp);
        o.addProperty("argent", argent);
        o.addProperty("titre_fr", titreFr);
        o.addProperty("titre_en", titreEn);
        o.addProperty("progression", progression);
        o.addProperty("terminee", terminee);
        o.addProperty("termineeLe", termineeLe);
        return o;
    }

    public static QueteEtat fromJson(JsonObject o) {
        return new QueteEtat(Json.str(o, "id", "?"), Json.enumOf(o, "type", Type.class, Type.KILL_ZOMBIES),
                Json.enumOf(o, "periode", Periode.class, Periode.JOUR), Json.lng(o, "objectif", 1), Json.integer(o, "xp", 0),
                Json.lng(o, "argent", 0), Json.str(o, "titre_fr", null), Json.str(o, "titre_en", null),
                Json.lng(o, "progression", 0), Json.bool(o, "terminee", false), Json.lng(o, "termineeLe", 0));
    }

    public void write(FriendlyByteBuf b) {
        Texte.utf(b, id, ID_MAX);
        b.writeVarInt(type.ordinal());
        b.writeVarInt(periode.ordinal());
        b.writeVarLong(objectif);
        b.writeVarInt(xp);
        b.writeVarLong(argent);
        Texte.utf(b, titreFr, TITRE_MAX);
        Texte.utf(b, titreEn, TITRE_MAX);
        b.writeVarLong(progression);
        b.writeBoolean(terminee);
        b.writeVarLong(termineeLe);
    }

    public static QueteEtat read(FriendlyByteBuf b) {
        return new QueteEtat(b.readUtf(64), Type.parOrdinal(b.readVarInt()), Periode.parOrdinal(b.readVarInt()), b.readVarLong(),
                b.readVarInt(), b.readVarLong(), b.readUtf(96), b.readUtf(96), b.readVarLong(), b.readBoolean(), b.readVarLong());
    }
}
