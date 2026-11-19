package fr.heraliz.clashwar.tournoi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reglages du ClashWar (MATCHS_SPEC §2.1, §2.6) : fichier {@code <serveur>/heraliz_clashwar/clashwar.json}, cree avec les
 * valeurs par defaut au premier demarrage (ecriture atomique .tmp + ATOMIC_MOVE), relu au demarrage et par
 * {@code /recharger clashwar}. Les champs absents ou invalides gardent leur defaut (lecture tolerante).
 * <p>Fil serveur. Valeurs publiques en lecture seule par convention.
 */
public final class CwConfig {
    /** Jours de l'evenement (defaut mercredi et dimanche). */
    public List<DayOfWeek> jours = List.of(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);
    /** Heure de depart (fin des inscriptions). */
    public int heure = 20, minute = 30;
    /**
     * Fuseau ("Europe/Paris"). 18/11 (retour du propriétaire : « le message du ClashWar dit 23h16 alors qu'il est 00h16 ») :
     * vide = heure française (avant : celle de la machine, en UTC sur le serveur : une heure de retard l'hiver, deux l'été).
     */
    public String fuseau = "";
    /** Rotation des formats d'une edition a l'autre. */
    public List<Format> rotation = List.of(Format.DEUX_DEUX, Format.TROIS_TROIS, Format.QUATRE_QUATRE, Format.UN_UN);
    /** Format impose (null = rotation). */
    @Nullable
    public Format formatFixe;
    /** Equipes au minimum pour lancer le tableau, au maximum inscrites. */
    public int minEquipes = 2, maxEquipes = 32;
    /** Annonce a H-15 min, ouverture des inscriptions a H-10 min. */
    public int annonceMinutes = 15, inscriptionMinutes = 10;
    /** Durees du deroule (secondes). */
    public int choixSecondes = 10, preparationSecondes = 10, combatSecondes = 240, mortSubiteSecondes = 60,
            limiteSecondes = 90, pauseToursSecondes = 20, retourSecondes = 6, finSecondes = 20, attenteArene = 90;
    /** Manches gagnantes d'un match de recherche et destruction (3 = au meilleur des 5). */
    public int manchesGagnantes = 3;
    /** Degats par seconde hors de la zone pendant la mort subite (ancien mode, plus utilise depuis la 0.12.0). */
    public float degatsZone = 2f;
    /** Limites par equipe : classe de kit -> nombre maximum. */
    public Map<String, Integer> limites = new LinkedHashMap<>(Map.of("sniper", 1, "pompe", 1));
    /** Kits proposes (ids de data/heraliz_clashwar/kits) et kit donne a qui n'a rien choisi. */
    public List<String> kits = List.of("clashwar_assaut", "clashwar_smg", "clashwar_pompe", "clashwar_sniper", "clashwar_tactique");
    public String kitDefaut = "clashwar_assaut";
    /** Jetons verses a la banque du clan. */
    public long recompenseVainqueur = 5000, recompenseFinaliste = 2000, recompenseTroisieme = 1000, recompenseDemi = 750;
    /**
     * Clans differents au moins pour qu'une edition rapporte des points de saison (pas de tournoi entre deux clans
     * complices) ; 02/11 (audit) : sorti du code ({@code Recompenses}). L'onglet Saison de HeraliZ Clans en affiche une
     * copie ({@code SaisonVue}) : changer les deux ensemble.
     */
    public int saisonClansMin = 4;
    /**
     * Primes et cles (19/11, decision du proprietaire, audit de l'economie n° 1 : un clan seul touchait les primes en jouant
     * contre lui-meme) : versees seulement si au moins tant de clans DIFFERENTS ont une equipe dans le tableau, et une seule
     * prime par clan (sa meilleure place ; les places suivantes du meme clan n'ont rien). Les points de saison gardent leur
     * propre minimum ({@link #saisonClansMin}).
     */
    public int recompensesClansMin = 3;
    /** Cle de caisse d'event donnee a chaque joueur de l'equipe gagnante (id d'objet, vide = aucune). */
    public String cleEvent = "heralizcrates:event_key";
    public int clesParJoueur = 1;
    /** Carte de la salle d'attente. */
    public String salle = "lobby_clashwar";
    /**
     * Coffre (19/11, audit des lenteurs C3) : depots ecrits sur le fil des coffres (faux : sur le fil serveur, comme avant),
     * operations du Coffre (depots, confirmations, retours) au plus par tick, et temps au plus par tick (ms ; au moins une
     * operation par tick).
     */
    public boolean coffresEnFond = true;
    public int coffresParTick = 4, coffresBudgetMs = 6;
    /** Teleportations du ClashWar : arrivee prechargee avant (faux : tout de suite, comme avant le 19/11). */
    public boolean prechargerArrivees = true;
    /** Arenes posees d'avance au demarrage du serveur (faux : seulement a l'ouverture des inscriptions, 19/11, audit C8). */
    public boolean arenesAuDemarrage = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static CwConfig courant = new CwConfig();

    /** Reglages courants (defauts tant que rien n'est charge). */
    public static CwConfig get() {
        return courant;
    }

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    public ZoneId zone() {
        if (fuseau != null && !fuseau.isBlank()) {
            try {
                return ZoneId.of(fuseau.trim());
            } catch (RuntimeException ex) {
                HeralizClashWar.LOG.warn("[clashwar] fuseau inconnu {}", fuseau);
            }
        }
        return PARIS;
    }

    /** Limite de la classe pour une equipe (Integer.MAX_VALUE = aucune). */
    public int limite(String classe) {
        Integer l = limites.get(classe);
        return l == null ? Integer.MAX_VALUE : Math.max(0, l);
    }

    // ------------------------------------------------------------------ fichiers
    /** Dossier des donnees du mod : {@code <serveur>/heraliz_clashwar}. */
    public static Path dossier(MinecraftServer s) {
        return s.getServerDirectory().toPath().resolve("heraliz_clashwar");
    }

    /** Relit (ou cree) le fichier de reglages. */
    public static void charger(MinecraftServer s) {
        Path f = dossier(s).resolve("clashwar.json");
        CwConfig c = new CwConfig();
        if (Files.isRegularFile(f)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                c.lire(o);
            } catch (IOException | RuntimeException ex) {
                HeralizClashWar.LOG.error("[clashwar] {} illisible, reglages par defaut", f, ex);
            }
        } else {
            ecrireAtomique(f, GSON.toJson(c.toJson()));
        }
        courant = c;
        // 19/11 : reglages du moteur (Coffre etale, arrivees prechargees, arenes posees a l'ouverture des inscriptions)
        fr.heraliz.clashwar.moteur.coffre.Coffres.regler(c.coffresEnFond, c.coffresParTick, c.coffresBudgetMs);
        fr.heraliz.clashwar.moteur.Arrivees.regler(c.prechargerArrivees);
        fr.heraliz.clashwar.moteur.arenes.ArenesService.regler(c.arenesAuDemarrage);
        HeralizClashWar.LOG.info("[clashwar] reglages : jours {} a {}:{} ({}), rotation {}, format fixe {}", c.jours, c.heure,
                String.format(Locale.ROOT, "%02d", c.minute), c.zone(), c.rotation, c.formatFixe);
        HeralizClashWar.LOG.info("[clashwar] reglages : primes des {} clans, coffre {} ({} par tick, {} ms), arrivees prechargees {}, arenes au demarrage {}",
                c.recompensesClansMin, c.coffresEnFond ? "en fond" : "sur le fil serveur", c.coffresParTick, c.coffresBudgetMs,
                c.prechargerArrivees, c.arenesAuDemarrage);
    }

    private void lire(JsonObject o) {
        if (o.has("jours") && o.get("jours").isJsonArray()) {
            List<DayOfWeek> l = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("jours")) {
                DayOfWeek d = jour(e.getAsString());
                if (d != null && !l.contains(d)) l.add(d);
            }
            if (!l.isEmpty()) jours = List.copyOf(l);
        }
        if (o.has("heure")) {
            String[] hm = o.get("heure").getAsString().split(":");
            try {
                heure = Math.max(0, Math.min(23, Integer.parseInt(hm[0].trim())));
                minute = hm.length > 1 ? Math.max(0, Math.min(59, Integer.parseInt(hm[1].trim()))) : 0;
            } catch (NumberFormatException ex) {
                HeralizClashWar.LOG.warn("[clashwar] heure invalide {}", o.get("heure"));
            }
        }
        fuseau = chaine(o, "fuseau", fuseau);
        if (o.has("rotation") && o.get("rotation").isJsonArray()) {
            List<Format> l = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("rotation")) {
                Format f = Format.parNom(e.getAsString());
                if (f != null) l.add(f);
            }
            if (!l.isEmpty()) rotation = List.copyOf(l);
        }
        formatFixe = Format.parNom(chaine(o, "format_fixe", ""));
        minEquipes = Math.max(2, entier(o, "min_equipes", minEquipes));
        maxEquipes = Math.max(minEquipes, Math.min(64, entier(o, "max_equipes", maxEquipes)));
        annonceMinutes = Math.max(0, entier(o, "annonce_minutes", annonceMinutes));
        inscriptionMinutes = Math.max(1, entier(o, "inscription_minutes", inscriptionMinutes));
        choixSecondes = Math.max(3, entier(o, "choix_secondes", choixSecondes));
        preparationSecondes = Math.max(3, entier(o, "preparation_secondes", preparationSecondes));
        combatSecondes = Math.max(30, entier(o, "combat_secondes", combatSecondes));
        mortSubiteSecondes = Math.max(10, entier(o, "mort_subite_secondes", mortSubiteSecondes));
        limiteSecondes = Math.max(10, entier(o, "limite_secondes", limiteSecondes));
        pauseToursSecondes = Math.max(5, entier(o, "pause_tours_secondes", pauseToursSecondes));
        retourSecondes = Math.max(2, entier(o, "retour_secondes", retourSecondes));
        finSecondes = Math.max(3, entier(o, "fin_secondes", finSecondes));
        attenteArene = Math.max(10, entier(o, "attente_arene_secondes", attenteArene));
        manchesGagnantes = Math.max(1, Math.min(5, entier(o, "manches_gagnantes", manchesGagnantes)));
        if (o.has("degats_zone")) degatsZone = Math.max(0.5f, o.get("degats_zone").getAsFloat());
        if (o.has("limites") && o.get("limites").isJsonObject()) {
            limites = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("limites").entrySet()) {
                limites.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsInt());
            }
        }
        if (o.has("kits") && o.get("kits").isJsonArray()) {
            List<String> l = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("kits")) l.add(e.getAsString());
            if (!l.isEmpty()) kits = List.copyOf(l);
        }
        kitDefaut = chaine(o, "kit_defaut", kitDefaut);
        if (o.has("recompenses") && o.get("recompenses").isJsonObject()) {
            JsonObject r = o.getAsJsonObject("recompenses");
            recompenseVainqueur = Math.max(0, r.has("vainqueur") ? r.get("vainqueur").getAsLong() : recompenseVainqueur);
            recompenseFinaliste = Math.max(0, r.has("finaliste") ? r.get("finaliste").getAsLong() : recompenseFinaliste);
            recompenseTroisieme = Math.max(0, r.has("troisieme") ? r.get("troisieme").getAsLong() : recompenseTroisieme);
            recompenseDemi = Math.max(0, r.has("demi_finaliste") ? r.get("demi_finaliste").getAsLong() : recompenseDemi);
            cleEvent = chaine(r, "cle_event", cleEvent);
            clesParJoueur = Math.max(0, r.has("cles_par_joueur") ? r.get("cles_par_joueur").getAsInt() : clesParJoueur);
        }
        saisonClansMin = Math.max(1, entier(o, "saison_clans_min", saisonClansMin));
        recompensesClansMin = Math.max(1, entier(o, "recompenses_clans_min", recompensesClansMin));
        salle = chaine(o, "salle", salle);
        coffresEnFond = booleen(o, "coffres_en_fond", coffresEnFond);
        coffresParTick = Math.max(1, Math.min(64, entier(o, "coffres_par_tick", coffresParTick)));
        coffresBudgetMs = Math.max(1, Math.min(40, entier(o, "coffres_budget_ms", coffresBudgetMs)));
        prechargerArrivees = booleen(o, "precharger_arrivees", prechargerArrivees);
        arenesAuDemarrage = booleen(o, "arenes_au_demarrage", arenesAuDemarrage);
    }

    private JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", AIDE);
        JsonArray j = new JsonArray();
        for (DayOfWeek d : jours) j.add(d.name());
        o.add("jours", j);
        o.addProperty("heure", String.format(Locale.ROOT, "%02d:%02d", heure, minute));
        o.addProperty("fuseau", fuseau);
        JsonArray r = new JsonArray();
        for (Format f : rotation) r.add(f.nom);
        o.add("rotation", r);
        o.addProperty("format_fixe", formatFixe == null ? "" : formatFixe.nom);
        o.addProperty("min_equipes", minEquipes);
        o.addProperty("max_equipes", maxEquipes);
        o.addProperty("annonce_minutes", annonceMinutes);
        o.addProperty("inscription_minutes", inscriptionMinutes);
        o.addProperty("choix_secondes", choixSecondes);
        o.addProperty("preparation_secondes", preparationSecondes);
        o.addProperty("combat_secondes", combatSecondes);
        o.addProperty("mort_subite_secondes", mortSubiteSecondes);
        o.addProperty("limite_secondes", limiteSecondes);
        o.addProperty("pause_tours_secondes", pauseToursSecondes);
        o.addProperty("retour_secondes", retourSecondes);
        o.addProperty("fin_secondes", finSecondes);
        o.addProperty("attente_arene_secondes", attenteArene);
        o.addProperty("manches_gagnantes", manchesGagnantes);
        o.addProperty("degats_zone", degatsZone);
        JsonObject l = new JsonObject();
        limites.forEach(l::addProperty);
        o.add("limites", l);
        JsonArray k = new JsonArray();
        kits.forEach(k::add);
        o.add("kits", k);
        o.addProperty("kit_defaut", kitDefaut);
        JsonObject rec = new JsonObject();
        rec.addProperty("vainqueur", recompenseVainqueur);
        rec.addProperty("finaliste", recompenseFinaliste);
        rec.addProperty("troisieme", recompenseTroisieme);
        rec.addProperty("demi_finaliste", recompenseDemi);
        rec.addProperty("cle_event", cleEvent);
        rec.addProperty("cles_par_joueur", clesParJoueur);
        o.add("recompenses", rec);
        o.addProperty("saison_clans_min", saisonClansMin);
        o.addProperty("recompenses_clans_min", recompensesClansMin);
        o.addProperty("salle", salle);
        o.addProperty("coffres_en_fond", coffresEnFond);
        o.addProperty("coffres_par_tick", coffresParTick);
        o.addProperty("coffres_budget_ms", coffresBudgetMs);
        o.addProperty("precharger_arrivees", prechargerArrivees);
        o.addProperty("arenes_au_demarrage", arenesAuDemarrage);
        return o;
    }

    /** Texte d'aide en tete du fichier (le JSON n'a pas de commentaires). */
    static final String AIDE = "ClashWar HeraliZ : jours (MONDAY..SUNDAY ou lundi..dimanche), heure HH:MM de depart, "
            + "rotation des formats (1v1, 2v2, 3v3, 4v4), format_fixe vide = rotation ; /recharger clashwar pour relire. "
            + "recompenses_clans_min : primes et cles versees seulement si au moins tant de clans differents ont une equipe dans le "
            + "tableau, une seule prime par clan (sa meilleure place) ; saison_clans_min : meme regle pour les points de saison. "
            + "coffres_en_fond : depots du Coffre ecrits en tache de fond (false : sur le fil du serveur, comme avant) ; "
            + "coffres_par_tick et coffres_budget_ms : depots et retours du Coffre au plus par tick, et temps au plus par tick (ms). "
            + "precharger_arrivees : les teleportations du ClashWar attendent que l'arrivee soit chargee (3 s au plus ; false : tout "
            + "de suite, comme avant). arenes_au_demarrage : arenes posees d'avance des le demarrage du serveur (false : seulement a "
            + "l'ouverture des inscriptions).";

    // ------------------------------------------------------------------ aides
    @Nullable
    private static DayOfWeek jour(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        String[] fr = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};
        for (int i = 0; i < fr.length; i++) if (fr[i].equals(t)) return DayOfWeek.of(i + 1);
        try {
            return DayOfWeek.valueOf(t.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            HeralizClashWar.LOG.warn("[clashwar] jour inconnu {}", s);
            return null;
        }
    }

    private static String chaine(JsonObject o, String cle, String defaut) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsString() : defaut;
    }

    private static int entier(JsonObject o, String cle, int defaut) {
        try {
            return o.has(cle) ? o.get(cle).getAsInt() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    private static boolean booleen(JsonObject o, String cle, boolean defaut) {
        try {
            return o.has(cle) ? o.get(cle).getAsBoolean() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    /** Ecrit le texte dans le fichier de facon atomique (.tmp puis ATOMIC_MOVE). Rend faux en cas d'erreur (journalisee). */
    public static boolean ecrireAtomique(Path f, String texte) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, texte, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            HeralizClashWar.LOG.error("[clashwar] ecriture de {} impossible", f, ex);
            return false;
        }
    }

    /** Gson partage (sortie lisible). */
    static Gson gson() {
        return GSON;
    }
}
