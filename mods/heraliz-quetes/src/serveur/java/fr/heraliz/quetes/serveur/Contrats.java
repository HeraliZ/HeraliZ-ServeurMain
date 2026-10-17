package fr.heraliz.quetes.serveur;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.heraliz.quetes.Texte;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tirage des contrats d'un PNJ : un modèle (« Tue {n} zombies à {ville.nom} ») + ses variables tirées au hasard. Les
 * variables d'un modèle :
 * <ul>
 *    <li>{@code {"min": 20, "max": 40, "pas": 5}} : un nombre ;</li>
 *    <li>{@code {"choix": ["pistolet", {"valeur": "pompe", "nom": {"fr": "fusil à pompe", "en": "shotgun"}}]}} : un choix
 *    dans une liste ; {@code {arme}} = la valeur, {@code {arme.nom}} = son nom dans la langue du texte ;</li>
 *    <li>{@code {"points": "villes"}} : un point du groupe ; {@code {ville}} = son identifiant, {@code {ville.nom}} = son
 *    nom (/quete point nom).</li>
 * </ul>
 * Un modèle dont un groupe de points est vide n'est pas tiré (le staff n'a pas encore posé ses points).
 */
final class Contrats {
    private static final Pattern VAR = Pattern.compile("\\{([a-z0-9_]+)(?:\\.([a-z0-9_]+))?}");

    private Contrats() {
    }

    private record Valeur(String valeur, Map<String, Texte> champs) {
    }

    /** Remplit le tableau jusqu'à 3 contrats (sans reprendre deux fois le même modèle s'il y a le choix). */
    static void remplir(Defs.Pnj pnj, Donnees.DPnj dp, Points pts, Random r) {
        if (pnj.contrats.isEmpty()) {
            dp.tableau.clear();
            return;
        }
        int essais = 0;
        while (dp.tableau.size() < 3 && essais++ < 30) {
            Set<String> deja = new java.util.HashSet<>();
            for (String s : dp.tableau) deja.add(modeleDe(s));
            String q = tirer(pnj, dp.rep, pts, r, deja);
            if (q == null) q = tirer(pnj, dp.rep, pts, r, Set.of());
            if (q == null) break;
            dp.tableau.add(q);
        }
    }

    static String modeleDe(String json) {
        try {
            return Defs.chaine(com.google.gson.JsonParser.parseString(json).getAsJsonObject(), "_modele", "");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /** Une quête tirée (JSON), ou null si aucun modèle n'est possible. */
    static String tirer(Defs.Pnj pnj, int rep, Points pts, Random r, Set<String> eviter) {
        List<Defs.Modele> possibles = new ArrayList<>();
        int total = 0;
        for (Defs.Modele m : pnj.contrats) {
            if (m.repMin > rep || eviter.contains(m.id)) continue;
            possibles.add(m);
            total += m.poids;
        }
        for (int essai = 0; essai < 8 && total > 0; essai++) {
            int t = r.nextInt(total);
            Defs.Modele m = null;
            for (Defs.Modele x : possibles) {
                t -= x.poids;
                if (t < 0) {
                    m = x;
                    break;
                }
            }
            if (m == null) continue;
            JsonObject q = construire(m, pts, r);
            if (q != null && faisable(q, pts)) return q.toString();
        }
        return null;
    }

    private static JsonObject construire(Defs.Modele m, Points pts, Random r) {
        Map<String, Valeur> vars = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : m.variables.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject v = e.getValue().getAsJsonObject();
            if (v.has("points")) {
                List<Points.Point> l = pts.groupe(Defs.chaine(v, "points", ""));
                if (l.isEmpty()) return null;
                Points.Point p = l.get(r.nextInt(l.size()));
                vars.put(e.getKey(), new Valeur(p.id(), Map.of("nom", Texte.fr(p.nomOuId()))));
            } else if (v.has("choix") && v.get("choix").isJsonArray()) {
                JsonArray a = v.getAsJsonArray("choix");
                if (a.size() == 0) return null;
                JsonElement c = a.get(r.nextInt(a.size()));
                if (c.isJsonObject()) {
                    Map<String, Texte> champs = new HashMap<>();
                    for (Map.Entry<String, JsonElement> f : c.getAsJsonObject().entrySet()) champs.put(f.getKey(), Texte.json(f.getValue()));
                    vars.put(e.getKey(), new Valeur(Defs.chaine(c.getAsJsonObject(), "valeur", ""), champs));
                } else vars.put(e.getKey(), new Valeur(c.getAsString(), Map.of()));
            } else {
                int min = Defs.entier(v, "min", 1), max = Math.max(min, Defs.entier(v, "max", min)), pas = Math.max(1, Defs.entier(v, "pas", 1));
                int n = min + pas * r.nextInt((max - min) / pas + 1);
                vars.put(e.getKey(), new Valeur(Integer.toString(n), Map.of()));
            }
        }
        JsonObject q = remplacer(m.quete, vars, "fr").getAsJsonObject();
        q.addProperty("_modele", m.id);
        if (!q.has("id")) q.addProperty("id", m.id);
        return q;
    }

    private static boolean faisable(JsonObject q, Points pts) {
        if (!q.has("etapes") || !q.get("etapes").isJsonArray()) return false;
        for (JsonElement e : q.getAsJsonArray("etapes")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (o.has("groupe") && pts.groupe(Defs.chaine(o, "groupe", "")).isEmpty()) return false;
            for (String id : Defs.liste(o, "point")) if (pts.point(id) == null) return false;
        }
        return true;
    }

    /** Copie du JSON avec les {variables} remplacées ; dans un texte {"fr", "en", "ru"}, chaque langue prend la sienne. */
    private static JsonElement remplacer(JsonElement e, Map<String, Valeur> vars, String langue) {
        if (e == null || e.isJsonNull()) return e;
        if (e.isJsonPrimitive()) {
            if (!e.getAsJsonPrimitive().isString()) return e;
            return new JsonPrimitive(remplacer(e.getAsString(), vars, langue));
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) a.add(remplacer(x, vars, langue));
            return a;
        }
        JsonObject o = e.getAsJsonObject(), c = new JsonObject();
        boolean texte = o.size() > 0 && o.keySet().stream().allMatch(Texte.LANGUES::contains);
        for (Map.Entry<String, JsonElement> x : o.entrySet()) {
            c.add(x.getKey(), remplacer(x.getValue(), vars, texte ? x.getKey() : langue));
        }
        return c;
    }

    private static String remplacer(String s, Map<String, Valeur> vars, String langue) {
        if (s.indexOf('{') < 0) return s;
        Matcher m = VAR.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Valeur v = vars.get(m.group(1));
            String r;
            if (v == null) r = m.group();
            else if (m.group(2) == null) r = v.valeur();
            else {
                Texte t = v.champs().get(m.group(2));
                r = t == null ? v.valeur() : t.choisir(langue);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
