package fr.heralisz.clans.saison;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bareme de la saison des clans (docs/SAISON_CLANS.md §4) : config/heralisz_clans/saison.json (fichier du depot qui fait
 * foi, ecrit avec les valeurs par defaut s'il manque, relu par /saison recharger). Tous les chiffres des points sont
 * ici : les autres mods n'envoient qu'une source et une quantite ({@link SaisonApi#pve}).
 */
public final class Bareme {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Points PVE par source (par unite de quantite). */
    public final Map<String, Double> pve = new LinkedHashMap<>();
    /** Sources des petites actions, plafonnees par joueur et par jour. */
    public final Set<String> plafonnees = new HashSet<>();
    public double plafondJour = 50;
    /** Type d'entite -> source PVE quand un joueur la tue (zombies). */
    public final Map<String, String> entites = new HashMap<>();
    /** Types d'entites des boss (points « boss », partages selon les degats). */
    public final Set<String> boss = new HashSet<>();
    /**
     * Rendement des boss par clan, par boss et par jour (heure de Paris) : 1er kill, 2e, puis tous les suivants
     * (25/10 : avant, 150 points a chaque reapparition, toutes les heures, pour chacun des 5 boss).
     */
    public double[] bossCoefs = {1, 0.2, 0};
    /**
     * Capture d'un drapeau (25/10, demande du propriétaire : plus la zone est forte, plus elle rapporte, avec une limite) :
     * points par point de valeur de la ville (1 a 7), coefficient de la difficulte des vagues (1, 2, 3), multiplicateur
     * d'une zone prise a un clan qui la tenait depuis au moins {@link #captureTenueMin} minutes (points PVP ; sinon PVE),
     * delais contre les echanges entre clans complices et plafond par clan et par jour.
     */
    public double captureParValeur = 3, capturePriseCoef = 2, captureTenueMin = 60, captureMemeZoneH = 12, captureMemePaireH = 48,
            capturePlafondJour = 40;
    public double[] captureDifficulte = {0.8, 1, 1.25};

    public double pvpKill = 1, pvpMort = 1;
    /** Fenetre (heures) et coefficients pour une meme victime : 1er kill, 2e, puis tous les suivants. */
    public double pvpFenetreH = 6;
    public double[] pvpCoefs = {1, 0.5, 0};
    /** Rarete minimale (0 Commun ... 5 Unique) d'une arme ou d'une protection pour que la victime compte. */
    public int pvpRareteMin = 1;
    /** Objets qui ne font jamais d'une victime une menace (ceux des /kit). */
    public final Set<String> pvpExclus = new HashSet<>(Set.of("geckolib:item_sledge_hammer"));

    public double[] places = {100, 60, 40, 20};
    public double participation = 5;
    public final Map<String, Double> multiplicateurs = new HashMap<>();
    /**
     * Événements de la carte (25/10, module heraliz_evenements, {@link SaisonApi#evenement}) : points (catégorie events)
     * par source (« largage »...) et nombre de fois récompensé par clan et par jour (absent : sans limite).
     */
    public final Map<String, Double> evenementsPoints = new LinkedHashMap<>();
    public final Map<String, Integer> evenementsParJour = new HashMap<>();

    /** Prise du coeur pendant la Purge : part perdue par le clan pris, part gagnee par l'attaquant. */
    public double raidPerduPct = 15, raidGagnePct = 10;
    /**
     * XP de clan par objet donne au clan (/clan don), par rarete 0..5 : seulement les objets de recuperation a revendre
     * (tag heraliz_composants:vente), Legendaires et Uniques ; l'objet disparait.
     */
    public long[] donXp = {0, 0, 0, 0, 60, 200};
    /** Derniers jours de la saison ou personne ne rejoint un clan. */
    public int verrouJours = 7;

    public double pointsPve(String source) {
        Double v = pve.get(source);
        return v == null ? 0 : v;
    }

    public double multiplicateur(String event) {
        Double v = multiplicateurs.get(event);
        return v == null ? 1 : v;
    }

    // ------------------------------------------------------------------ fichier
    static Bareme charger(Path dossierConfig) {
        Path f = dossierConfig.resolve("saison.json");
        Bareme b = defaut();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(dossierConfig);
                Files.writeString(f, GSON.toJson(b.toJson()), StandardCharsets.UTF_8);
                HeraliszClans.LOG.info("[saison] saison.json ecrit avec les valeurs par defaut");
                return b;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            b.lire(o);
            HeraliszClans.LOG.info("[saison] bareme charge ({} sources PVE)", b.pve.size());
        } catch (IOException | RuntimeException ex) {
            HeraliszClans.LOG.error("[saison] saison.json illisible, bareme par defaut garde", ex);
            b = defaut();
        }
        return b;
    }

    static Bareme defaut() {
        Bareme b = new Bareme();
        b.pve.put("zombie", 0.01);
        b.pve.put("zombie_mutant", 0.02);
        b.pve.put("brute", 0.5);
        b.pve.put("boss", 60.0);
        b.pve.put("salle_verte", 5.0);
        b.pve.put("salle_bleue", 15.0);
        b.pve.put("salle_rouge", 40.0);
        double[] cont = {0.05, 0.1, 0.2, 0.4, 0.8, 1.5, 5};
        for (int i = 0; i < cont.length; i++) b.pve.put("conteneur_t" + (i + 1), cont[i]);
        b.pve.put("vente_1000", 1.0);
        double[] craft = {1, 2, 4, 8, 8, 8};
        String[] rar = {"commun", "peu_commun", "rare", "epique", "legendaire", "unique"};
        for (int i = 0; i < rar.length; i++) b.pve.put("craft_" + rar[i], craft[i]);
        b.pve.put("quete_principale", 10.0);
        b.pve.put("quete_clan", 5.0);
        b.pve.put("oil_rig", 60.0);
        for (String s : new String[]{"zombie", "zombie_mutant", "brute", "vente_1000"}) b.plafonnees.add(s);
        for (int i = 1; i <= 6; i++) b.plafonnees.add("conteneur_t" + i);
        for (String r : rar) b.plafonnees.add("craft_" + r);
        b.entites.put("heralisz_utils:zombie_survie", "zombie");
        b.entites.put("minecraft:zombie", "zombie");
        b.entites.put("mutantszombies:crawler", "zombie_mutant");
        b.entites.put("mutantszombies:blister_zombie", "zombie_mutant");
        b.entites.put("mutantszombies:zombie_brute", "brute");
        for (String s : new String[]{"heralisz_mammoth:mammoth", "heralisz_mere:ruin_mother", "heralisz_n07:sujet_n07",
                "heralisz_plague:plague_bearer", "heralisz_sylvorn:sylvorn"}) b.boss.add(s);
        b.multiplicateurs.put("clashwar", 2.0);
        b.evenementsPoints.put("largage", 8.0);
        b.evenementsParJour.put("largage", 3);
        // objets d'événement à revendre (25/10) : vendus à Mila, points au clan du vendeur ; l'hélicoptère abattu ne rapporte
        // rien par lui-même, seulement sa boîte noire (choix du propriétaire)
        b.evenementsPoints.put("objet_dossier_classe", 2.0);
        b.evenementsParJour.put("objet_dossier_classe", 5);
        b.evenementsPoints.put("objet_mallette_scellee", 4.0);
        b.evenementsParJour.put("objet_mallette_scellee", 3);
        b.evenementsPoints.put("objet_disque_dur_militaire", 8.0);
        b.evenementsParJour.put("objet_disque_dur_militaire", 2);
        b.evenementsPoints.put("objet_boite_noire_helico", 12.0);
        b.evenementsParJour.put("objet_boite_noire_helico", 2);
        return b;
    }

    private void lire(JsonObject o) {
        JsonObject p = Json.obj(o, "pve");
        if (!p.entrySet().isEmpty()) {
            pve.clear();
            for (Map.Entry<String, JsonElement> e : p.entrySet()) if (e.getValue().isJsonPrimitive()) pve.put(e.getKey(), Math.max(0, e.getValue().getAsDouble()));
        }
        JsonArray pl = Json.arr(o, "plafonnees");
        if (pl.size() > 0) {
            plafonnees.clear();
            for (JsonElement e : pl) if (e.isJsonPrimitive()) plafonnees.add(e.getAsString());
        }
        plafondJour = Math.max(0, Json.dbl(o, "plafond_jour_joueur", plafondJour));
        JsonObject en = Json.obj(o, "entites");
        if (!en.entrySet().isEmpty()) {
            entites.clear();
            for (Map.Entry<String, JsonElement> e : en.entrySet()) if (e.getValue().isJsonPrimitive()) entites.put(e.getKey(), e.getValue().getAsString());
        }
        JsonArray bo = Json.arr(o, "boss");
        if (bo.size() > 0) {
            boss.clear();
            for (JsonElement e : bo) if (e.isJsonPrimitive()) boss.add(e.getAsString());
        }
        JsonArray bc = Json.arr(o, "boss_coefficients");
        if (bc.size() > 0) {
            bossCoefs = new double[bc.size()];
            for (int i = 0; i < bc.size(); i++) bossCoefs[i] = Math.max(0, bc.get(i).getAsDouble());
        }
        JsonObject ca = Json.obj(o, "capture");
        captureParValeur = Math.max(0, Json.dbl(ca, "par_valeur", captureParValeur));
        capturePriseCoef = Math.max(0, Json.dbl(ca, "prise_coef", capturePriseCoef));
        captureTenueMin = Math.max(0, Json.dbl(ca, "tenue_min_minutes", captureTenueMin));
        captureMemeZoneH = Math.max(0, Json.dbl(ca, "meme_zone_heures", captureMemeZoneH));
        captureMemePaireH = Math.max(0, Json.dbl(ca, "meme_paire_heures", captureMemePaireH));
        capturePlafondJour = Math.max(0, Json.dbl(ca, "plafond_jour_clan", capturePlafondJour));
        JsonArray cd = Json.arr(ca, "difficulte");
        if (cd.size() == 3) for (int i = 0; i < 3; i++) captureDifficulte[i] = Math.max(0, cd.get(i).getAsDouble());
        JsonObject v = Json.obj(o, "pvp");
        pvpKill = Math.max(0, Json.dbl(v, "kill", pvpKill));
        pvpMort = Math.max(0, Json.dbl(v, "mort", pvpMort));
        pvpFenetreH = Math.max(0, Json.dbl(v, "fenetre_h", pvpFenetreH));
        JsonArray co = Json.arr(v, "coefficients");
        if (co.size() > 0) {
            pvpCoefs = new double[co.size()];
            for (int i = 0; i < co.size(); i++) pvpCoefs[i] = Math.max(0, co.get(i).getAsDouble());
        }
        pvpRareteMin = Math.max(0, Math.min(5, Json.integer(v, "rarete_min", pvpRareteMin)));
        if (v.has("exclus")) {
            pvpExclus.clear();
            for (JsonElement e : Json.arr(v, "exclus")) if (e.isJsonPrimitive()) pvpExclus.add(e.getAsString());
        }
        JsonObject ev = Json.obj(o, "events");
        JsonArray pa = Json.arr(ev, "places");
        if (pa.size() > 0) {
            places = new double[pa.size()];
            for (int i = 0; i < pa.size(); i++) places[i] = Math.max(0, pa.get(i).getAsDouble());
        }
        participation = Math.max(0, Json.dbl(ev, "participation", participation));
        JsonObject mu = Json.obj(ev, "multiplicateurs");
        if (!mu.entrySet().isEmpty()) {
            multiplicateurs.clear();
            for (Map.Entry<String, JsonElement> e : mu.entrySet()) if (e.getValue().isJsonPrimitive()) multiplicateurs.put(e.getKey(), e.getValue().getAsDouble());
        }
        JsonObject ep = Json.obj(ev, "points");
        if (!ep.entrySet().isEmpty()) {
            evenementsPoints.clear();
            for (Map.Entry<String, JsonElement> e : ep.entrySet()) if (e.getValue().isJsonPrimitive()) evenementsPoints.put(e.getKey(), Math.max(0, e.getValue().getAsDouble()));
        }
        JsonObject ej = Json.obj(ev, "par_jour");
        if (!ej.entrySet().isEmpty()) {
            evenementsParJour.clear();
            for (Map.Entry<String, JsonElement> e : ej.entrySet()) if (e.getValue().isJsonPrimitive()) evenementsParJour.put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
        }
        JsonObject r = Json.obj(o, "raid");
        raidPerduPct = Math.max(0, Math.min(100, Json.dbl(r, "perdu_pct", raidPerduPct)));
        raidGagnePct = Math.max(0, Math.min(raidPerduPct, Json.dbl(r, "gagne_pct", raidGagnePct)));
        verrouJours = Math.max(0, Json.integer(o, "verrou_jours", verrouJours));
        JsonArray dx = Json.arr(o, "don_xp");
        for (int i = 0; i < Math.min(6, dx.size()); i++) donXp[i] = Math.max(0, dx.get(i).getAsLong());
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "Bareme de la saison des clans (docs/SAISON_CLANS.md). pve : points par unite (vente_1000 = pour 1 000 herals"
                + " vendus) ; plafonnees : petites actions, au plus plafond_jour_joueur points par joueur et par jour ; boss_coefficients :"
                + " part des points du boss pour le 1er kill de ce boss par le clan dans la journee, le 2e, puis tous les suivants ; capture :"
                + " capture d'un drapeau = valeur de la ville x par_valeur x difficulte des vagues (1, 2, 3), x prise_coef (points PVP) si la"
                + " zone etait tenue par un autre clan depuis tenue_min_minutes ; rien pour la meme zone et le meme clan avant meme_zone_heures,"
                + " ni pour la meme zone entre les deux memes clans avant meme_paire_heures ; au plus plafond_jour_clan par clan et par jour ;"
                + " relu par /saison recharger.");
        JsonObject p = new JsonObject();
        for (Map.Entry<String, Double> e : pve.entrySet()) p.addProperty(e.getKey(), e.getValue());
        o.add("pve", p);
        JsonArray pl = new JsonArray();
        plafonnees.stream().sorted().forEach(pl::add);
        o.add("plafonnees", pl);
        o.addProperty("plafond_jour_joueur", plafondJour);
        JsonObject en = new JsonObject();
        entites.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> en.addProperty(e.getKey(), e.getValue()));
        o.add("entites", en);
        JsonArray bo = new JsonArray();
        boss.stream().sorted().forEach(bo::add);
        o.add("boss", bo);
        JsonArray bc = new JsonArray();
        for (double c : bossCoefs) bc.add(c);
        o.add("boss_coefficients", bc);
        JsonObject ca = new JsonObject();
        ca.addProperty("par_valeur", captureParValeur);
        JsonArray cd = new JsonArray();
        for (double c : captureDifficulte) cd.add(c);
        ca.add("difficulte", cd);
        ca.addProperty("prise_coef", capturePriseCoef);
        ca.addProperty("tenue_min_minutes", captureTenueMin);
        ca.addProperty("meme_zone_heures", captureMemeZoneH);
        ca.addProperty("meme_paire_heures", captureMemePaireH);
        ca.addProperty("plafond_jour_clan", capturePlafondJour);
        o.add("capture", ca);
        JsonObject v = new JsonObject();
        v.addProperty("kill", pvpKill);
        v.addProperty("mort", pvpMort);
        v.addProperty("fenetre_h", pvpFenetreH);
        JsonArray co = new JsonArray();
        for (double c : pvpCoefs) co.add(c);
        v.add("coefficients", co);
        v.addProperty("rarete_min", pvpRareteMin);
        JsonArray ex = new JsonArray();
        pvpExclus.stream().sorted().forEach(ex::add);
        v.add("exclus", ex);
        o.add("pvp", v);
        JsonObject ev = new JsonObject();
        JsonArray pa = new JsonArray();
        for (double d : places) pa.add(d);
        ev.add("places", pa);
        ev.addProperty("participation", participation);
        JsonObject mu = new JsonObject();
        for (Map.Entry<String, Double> e : multiplicateurs.entrySet()) mu.addProperty(e.getKey(), e.getValue());
        ev.add("multiplicateurs", mu);
        JsonObject ep = new JsonObject();
        for (Map.Entry<String, Double> e : evenementsPoints.entrySet()) ep.addProperty(e.getKey(), e.getValue());
        ev.add("points", ep);
        JsonObject ej = new JsonObject();
        evenementsParJour.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> ej.addProperty(e.getKey(), e.getValue()));
        ev.add("par_jour", ej);
        o.add("events", ev);
        JsonObject r = new JsonObject();
        r.addProperty("perdu_pct", raidPerduPct);
        r.addProperty("gagne_pct", raidGagnePct);
        o.add("raid", r);
        o.addProperty("verrou_jours", verrouJours);
        JsonArray dx = new JsonArray();
        for (long x : donXp) dx.add(x);
        o.add("don_xp", dx);
        return o;
    }

    /** Pour ecrire le fichier du depot (outil). */
    public static String jsonParDefaut() {
        return GSON.toJson(defaut().toJson());
    }
}
