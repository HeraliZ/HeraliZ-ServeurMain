package fr.heralisz.clans.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Membre d'un clan (etat serveur). dernierNom : dernier pseudo connu (affiche hors ligne) ; vuLe : derniere
 * deconnexion (ms epoch) ; permsJoueur : reglages individuels (AUTORISE / REFUSE seulement : une permission absente est
 * HERITE, comme son grade). Les reglages partent avec le membre (depart, exclusion) et sont ignores pour le chef.
 */
public final class Membre {
    public final UUID uuid;
    public String dernierNom;
    public Grade grade;
    public long rejointLe;
    public long vuLe;
    public Stats stats;
    /** Reglages individuels (jamais HERITE dans la table). */
    public final EnumMap<Perm, Perm.Etat> permsJoueur = new EnumMap<>(Perm.class);

    public Membre(UUID uuid, String dernierNom, Grade grade, long rejointLe, long vuLe, Stats stats) {
        this.uuid = uuid;
        this.dernierNom = dernierNom == null ? "?" : dernierNom;
        this.grade = grade == null ? Grade.RECRUE : grade;
        this.rejointLe = rejointLe;
        this.vuLe = vuLe;
        this.stats = stats == null ? new Stats() : stats;
    }

    /** Reglage individuel d'une permission (HERITE s'il n'y en a pas). */
    public Perm.Etat etatPerm(Perm p) {
        Perm.Etat e = permsJoueur.get(p);
        return e == null ? Perm.Etat.HERITE : e;
    }

    /** Fixe le reglage individuel (HERITE = retire) ; vrai si quelque chose a change. */
    public boolean definirPerm(Perm p, Perm.Etat e) {
        if (p == null || e == null) return false;
        if (e == Perm.Etat.HERITE) return permsJoueur.remove(p) != null;
        return permsJoueur.put(p, e) != e;
    }

    /** Reglages sous forme de masques (snapshot). */
    public ReglagesPerms reglages() {
        return permsJoueur.isEmpty() ? ReglagesPerms.AUCUN : ReglagesPerms.de(permsJoueur);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("nom", dernierNom);
        o.addProperty("grade", grade.name());
        o.addProperty("rejointLe", rejointLe);
        o.addProperty("vuLe", vuLe);
        o.add("stats", stats.toJson());
        if (!permsJoueur.isEmpty()) {
            JsonObject pj = new JsonObject();
            for (Map.Entry<Perm, Perm.Etat> e : permsJoueur.entrySet()) pj.addProperty(e.getKey().name(), e.getValue().name());
            o.add("permsJoueur", pj);
        }
        return o;
    }

    /** Null si l'uuid manque (entree ignoree). Reglages : permission ou etat inconnus ignores, HERITE ignore. */
    public static Membre fromJson(JsonObject o) {
        UUID id = Json.uuid(o, "uuid");
        if (id == null) return null;
        Membre m = new Membre(id, Json.str(o, "nom", "?"), Json.enumOf(o, "grade", Grade.class, Grade.RECRUE),
                Json.lng(o, "rejointLe", 0), Json.lng(o, "vuLe", 0), Stats.fromJson(Json.obj(o, "stats")));
        for (Map.Entry<String, JsonElement> e : Json.obj(o, "permsJoueur").entrySet()) {
            Perm p = Perm.parNom(e.getKey());
            Perm.Etat etat = e.getValue().isJsonPrimitive() ? Perm.Etat.parNom(e.getValue().getAsString()) : null;
            if (p != null && etat != null && etat != Perm.Etat.HERITE) m.permsJoueur.put(p, etat);
        }
        return m;
    }
}
