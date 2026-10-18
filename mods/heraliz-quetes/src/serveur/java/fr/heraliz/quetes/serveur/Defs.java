package fr.heraliz.quetes.serveur;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heraliz.quetes.Texte;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ce que disent les fichiers de réglage {@code config/heraliz_quetes/pnj/*.json} (un fichier par PNJ, voir
 * {@code config/heraliz_quetes/LISEZMOI.md}) : le PNJ, sa suite de quêtes, ses modèles de contrats.
 */
final class Defs {
    private Defs() {
    }

    /** Seuils de réputation : Inconnu, Toléré, Allié, De confiance, Frère d'armes. */
    static final int[] SEUILS = {0, 100, 400, 1000, 2500};

    static int niveau(int rep) {
        int n = 0;
        for (int i = 0; i < SEUILS.length; i++) if (rep >= SEUILS[i]) n = i;
        return n;
    }

    static int seuilSuivant(int rep) {
        for (int s : SEUILS) if (rep < s) return s;
        return -1;
    }

    // ------------------------------------------------------------------ PNJ
    static final class Pnj {
        String id;
        Texte nom = Texte.VIDE, role = Texte.VIDE;
        String skin = "";
        boolean brasFins;
        /** Apparence CaraCrea du fichier (clé « apparence »), null s'il n'y en a pas. */
        CompoundTag apparence;
        final List<Texte> ambiance = new ArrayList<>();
        Texte presentation = Texte.VIDE, inconnu = Texte.VIDE, connu = Texte.VIDE, ami = Texte.VIDE;
        final List<Quete> suite = new ArrayList<>();
        final List<Modele> contrats = new ArrayList<>();

        Quete quete(String id) {
            for (Quete q : suite) if (q.id.equals(id)) return q;
            return null;
        }

        Texte accueil(int niveau) {
            Texte t = niveau >= 3 && !ami.vide() ? ami : connu;
            return t.vide() ? inconnu : t;
        }
    }

    // ------------------------------------------------------------------ quête
    static final class Quete {
        String id;
        Texte titre = Texte.VIDE, demande = Texte.VIDE, fin = Texte.VIDE, rappel = Texte.VIDE, resume = Texte.VIDE;
        int difficulte = 1, repMin;
        boolean sansMourir;
        final List<Etape> etapes = new ArrayList<>();
        final List<Don> donne = new ArrayList<>();
        Recompense recompense = new Recompense();
        /** Le JSON d'origine (gardé avec un contrat tiré au hasard : il survit aux rechargements). */
        JsonObject source;
    }

    /** Objet de quête donné par le PNJ quand on accepte (colis, flacon, trousses...). */
    record Don(String modele, String ref, Texte nom, Texte desc, int nombre) {
    }

    static final class Recompense {
        long herals;
        int reputation;
        Texte titre = Texte.VIDE;
        final Map<String, Integer> autres = new LinkedHashMap<>();
    }

    static final class Etape {
        String type;
        Texte libelle = Texte.VIDE;
        JsonObject p = new JsonObject();

        int entier(String cle, int defaut) {
            return Defs.entier(p, cle, defaut);
        }

        String texte(String cle, String defaut) {
            return Defs.chaine(p, cle, defaut);
        }

        boolean oui(String cle) {
            return p.has(cle) && p.get(cle).isJsonPrimitive() && p.get(cle).getAsBoolean();
        }

        Texte t(String cle) {
            return Texte.json(p.get(cle));
        }

        /** Nombre de buts (points tirés dans le groupe) ou de choses à faire. */
        int nombre() {
            return Math.max(1, entier("nombre", 1));
        }

        boolean avecPoints() {
            return p.has("groupe") || p.has("point");
        }

        /** « exact » (distance précise), « zone » (≈ à 100 m près) ou « aucun ». */
        String marqueur() {
            String def = switch (type) {
                case "endroit", "tenir" -> "exact";
                default -> "zone";
            };
            return chaine(p, "marqueur", def).toLowerCase(Locale.ROOT);
        }

        int rayon() {
            return entier("rayon", switch (type) {
                case "endroit" -> 6;
                case "tenir" -> 5;
                default -> 150;
            });
        }
    }

    // ------------------------------------------------------------------ contrats
    static final class Modele {
        String id;
        int poids = 1, repMin;
        JsonObject variables = new JsonObject();
        JsonObject quete = new JsonObject();
    }

    // ------------------------------------------------------------------ lecture
    static Pnj pnj(JsonObject o, List<String> erreurs) {
        Pnj p = new Pnj();
        p.id = chaine(o, "id", "").toLowerCase(Locale.ROOT);
        p.nom = Texte.json(o.get("nom"));
        p.role = Texte.json(o.get("role"));
        p.skin = chaine(o, "skin", "");
        p.brasFins = o.has("bras_fins") && o.get("bras_fins").getAsBoolean();
        if (o.has("apparence") && o.get("apparence").isJsonObject()) p.apparence = Apparences.depuis(o.getAsJsonObject("apparence"));
        if (o.has("ambiance") && o.get("ambiance").isJsonArray()) o.getAsJsonArray("ambiance").forEach(e -> p.ambiance.add(Texte.json(e)));
        p.presentation = Texte.json(o.get("presentation"));
        if (o.has("accueil") && o.get("accueil").isJsonObject()) {
            JsonObject a = o.getAsJsonObject("accueil");
            p.inconnu = Texte.json(a.get("inconnu"));
            p.connu = Texte.json(a.get("connu"));
            p.ami = Texte.json(a.get("ami"));
        }
        if (o.has("suite") && o.get("suite").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("suite")) {
                if (!e.isJsonObject()) continue;
                Quete q = quete(e.getAsJsonObject(), p.id, erreurs);
                if (q.id.isEmpty()) {
                    erreurs.add(p.id + " : une quête sans « id »");
                    continue;
                }
                if (p.quete(q.id) != null) erreurs.add(p.id + " : deux quêtes « " + q.id + " »");
                p.suite.add(q);
            }
        }
        if (o.has("contrats") && o.get("contrats").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("contrats")) {
                if (!e.isJsonObject()) continue;
                JsonObject c = e.getAsJsonObject();
                Modele m = new Modele();
                m.id = chaine(c, "id", "contrat" + p.contrats.size());
                m.poids = Math.max(1, entier(c, "poids", 1));
                m.repMin = entier(c, "reputation_min", 0);
                if (c.has("variables") && c.get("variables").isJsonObject()) m.variables = c.getAsJsonObject("variables");
                if (c.has("quete") && c.get("quete").isJsonObject()) m.quete = c.getAsJsonObject("quete");
                else erreurs.add(p.id + " : contrat « " + m.id + " » sans « quete »");
                p.contrats.add(m);
            }
        }
        return p;
    }

    static Quete quete(JsonObject o, String pnj, List<String> erreurs) {
        Quete q = new Quete();
        q.source = o;
        q.id = chaine(o, "id", "");
        q.titre = Texte.json(o.get("titre"));
        q.demande = Texte.json(o.get("demande"));
        q.fin = Texte.json(o.get("fin"));
        q.rappel = Texte.json(o.get("rappel"));
        q.resume = Texte.json(o.get("resume"));
        q.difficulte = Math.max(1, Math.min(4, entier(o, "difficulte", 1)));
        q.repMin = entier(o, "reputation_min", 0);
        q.sansMourir = o.has("sans_mourir") && o.get("sans_mourir").getAsBoolean();
        if (o.has("donne") && o.get("donne").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("donne")) {
                if (!e.isJsonObject()) continue;
                JsonObject d = e.getAsJsonObject();
                q.donne.add(new Don(chaine(d, "objet", "colis"), chaine(d, "ref", chaine(d, "objet", "colis")),
                        Texte.json(d.get("nom")), Texte.json(d.get("desc")), Math.max(1, entier(d, "nombre", 1))));
            }
        }
        if (o.has("etapes") && o.get("etapes").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("etapes")) {
                if (!e.isJsonObject()) continue;
                JsonObject eo = e.getAsJsonObject();
                Etape et = new Etape();
                et.type = chaine(eo, "type", "").toLowerCase(Locale.ROOT);
                et.libelle = Texte.json(eo.get("libelle"));
                et.p = eo;
                if (!TYPES.contains(et.type)) erreurs.add(pnj + " / " + q.id + " : type d'étape inconnu « " + et.type + " »");
                q.etapes.add(et);
            }
        }
        if (q.etapes.isEmpty()) erreurs.add(pnj + " / " + q.id + " : aucune étape");
        if (o.has("recompense") && o.get("recompense").isJsonObject()) {
            JsonObject r = o.getAsJsonObject("recompense");
            q.recompense.herals = Math.max(0, nombre(r.get("herals")));
            q.recompense.reputation = (int) nombre(r.get("reputation"));
            q.recompense.titre = Texte.json(r.get("titre"));
            if (r.has("reputation_autres") && r.get("reputation_autres").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : r.getAsJsonObject("reputation_autres").entrySet()) {
                    q.recompense.autres.put(e.getKey(), (int) nombre(e.getValue()));
                }
            }
        }
        return q;
    }

    static final List<String> TYPES = List.of("parler", "donner", "montrer", "payer", "tuer", "ramasser", "indice", "code",
            "deposer", "endroit", "tenir", "attendre", "survivre", "extraction", "fouiller");

    // ------------------------------------------------------------------ outils JSON
    static String chaine(JsonObject o, String cle, String defaut) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : defaut;
    }

    static int entier(JsonObject o, String cle, int defaut) {
        JsonElement e = o.get(cle);
        if (e == null || !e.isJsonPrimitive()) return defaut;
        return (int) nombre(e);
    }

    /** Un nombre, ou un petit calcul écrit en texte (« 30*6 », après remplacement des variables d'un contrat). */
    static long nombre(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return 0;
        if (e.getAsJsonPrimitive().isNumber()) return Math.round(e.getAsDouble());
        if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean() ? 1 : 0;
        return Expr.calculer(e.getAsString());
    }

    static List<String> liste(JsonObject o, String cle) {
        List<String> l = new ArrayList<>();
        JsonElement e = o.get(cle);
        if (e == null) return l;
        if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) if (x.isJsonPrimitive()) l.add(x.getAsString());
        } else if (e.isJsonPrimitive()) l.add(e.getAsString());
        return l;
    }
}
