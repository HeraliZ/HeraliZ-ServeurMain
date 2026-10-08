package fr.heralisz.bases.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Base d'un clan (une par clan, BASES_SPEC §2) : parcelle de la grille, salles achetees, etat de la pose, journal de
 * pillage. Persistee dans {@code <serveur>/heralisz_bases/bases.json} par {@code server.BasesStore}.
 */
public final class Base {
    public enum Etat {
        /** Bunker en cours de pose par tranches : personne n'entre. */
        POSE,
        /** Bunker pret. */
        PRETE,
        /** Parcelle en cours d'effacement (dissolution du clan ou reinitialisation) : personne n'entre. */
        EFFACEMENT
    }

    /** Journal de pillage : 20 lignes au plus, la plus recente en tete. */
    public static final int PILLAGES_MAX = 20;

    public final UUID clan;
    /** Dernier tag connu du clan (journaux, admin ; le vrai tag est lu par ClansApi). */
    public String tag;
    public int parcelle;
    public long creeLe;
    public Etat etat = Etat.POSE;
    /** Apres l'effacement : true = la base disparait (dissolution), false = elle est reposee (reinitialisation). */
    public boolean supprimerApresEffacement;
    /** Salles achetees (ids du modele ; les salles « depart » sont toujours ouvertes et n'y figurent pas). */
    public final Set<String> salles = new LinkedHashSet<>();
    public final List<Pillage> pillages = new ArrayList<>();
    /**
     * Donnees libres du module pillage (ou d'autres extensions), persistees telles quelles : jamais lues par le coeur.
     * Toujours un objet (jamais null).
     */
    public JsonObject extra = new JsonObject();

    public Base(UUID clan, String tag, int parcelle, long creeLe) {
        this.clan = clan;
        this.tag = tag == null ? "" : tag;
        this.parcelle = parcelle;
        this.creeLe = creeLe;
    }

    public boolean prete() {
        return etat == Etat.PRETE;
    }

    public boolean salleOuverte(Plan.Salle s) {
        return s.depart() || salleAchetee(s.id());
    }

    public boolean salleAchetee(String id) {
        for (String s : salles) if (s.equalsIgnoreCase(id)) return true;
        return false;
    }

    /** Ajoute une ligne en tete du journal de pillage (borne a {@link #PILLAGES_MAX}). */
    public void ajouterPillage(Pillage p) {
        pillages.add(0, p);
        while (pillages.size() > PILLAGES_MAX) pillages.remove(pillages.size() - 1);
    }

    /** Pillage en cours (le plus recent sans fin), ou null. */
    @Nullable
    public Pillage pillageEnCours() {
        for (Pillage p : pillages) if (p.issue == Pillage.Issue.EN_COURS) return p;
        return null;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("clan", clan.toString());
        o.addProperty("tag", tag);
        o.addProperty("parcelle", parcelle);
        o.addProperty("creeLe", creeLe);
        o.addProperty("etat", etat.name());
        if (supprimerApresEffacement) o.addProperty("supprimerApresEffacement", true);
        JsonArray s = new JsonArray();
        for (String x : salles) s.add(x);
        o.add("salles", s);
        JsonArray p = new JsonArray();
        for (Pillage x : pillages) p.add(x.toJson());
        o.add("pillages", p);
        o.add("extra", extra);
        return o;
    }

    @Nullable
    public static Base fromJson(JsonObject o) {
        UUID clan = Json.uuid(o, "clan");
        int parcelle = Json.i(o, "parcelle", -1);
        if (clan == null || parcelle < 0) return null;
        Base b = new Base(clan, Json.str(o, "tag", ""), parcelle, Json.lng(o, "creeLe", 0));
        try {
            b.etat = Etat.valueOf(Json.str(o, "etat", "POSE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            b.etat = Etat.POSE;
        }
        b.supprimerApresEffacement = Json.b(o, "supprimerApresEffacement", false);
        for (JsonElement e : Json.arr(o, "salles")) {
            try {
                b.salles.add(e.getAsString());
            } catch (RuntimeException ignore) {
                // entree illisible ignoree
            }
        }
        for (JsonElement e : Json.arr(o, "pillages")) {
            if (e.isJsonObject() && b.pillages.size() < PILLAGES_MAX) b.pillages.add(Pillage.fromJson(e.getAsJsonObject()));
        }
        JsonObject extra = Json.obj(o, "extra");
        if (extra != null) b.extra = extra;
        return b;
    }
}
