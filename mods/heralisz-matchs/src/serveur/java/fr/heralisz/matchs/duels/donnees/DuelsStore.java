package fr.heralisz.matchs.duels.donnees;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Donnees globales des duels, en JSON atomique (.tmp puis ATOMIC_MOVE) sous {@code <dossier du serveur>/heralisz_matchs/duels/} :
 * <ul>
 *     <li>{@code classes.json} : {@code {"<uuid>": {"derniere": n, "classes": [5 classes]}}} ;</li>
 *     <li>{@code stats.json} : {@code {"<uuid>": {"nom", "victoires", "defaites", "eliminations", "morts", "tetes",
 *     "manches", "parties"}}} (aucun impact sur les stats de clan).</li>
 * </ul>
 * Chargement paresseux a la premiere lecture ; ecriture differee (au plus une par fichier toutes les 5 s, et a l'arret
 * du serveur : {@link #sauverSiBesoin(boolean)}). Fil serveur.
 */
public final class DuelsStore {
    private DuelsStore() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long DELAI_MS = 5_000L;

    /** Statistiques de duel d'un joueur (modifiables par le moteur des duels uniquement). */
    public static final class Stats {
        public String nom = "";
        public int victoires, defaites, eliminations, morts, tetes, manches, parties;

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("nom", nom);
            o.addProperty("victoires", victoires);
            o.addProperty("defaites", defaites);
            o.addProperty("eliminations", eliminations);
            o.addProperty("morts", morts);
            o.addProperty("tetes", tetes);
            o.addProperty("manches", manches);
            o.addProperty("parties", parties);
            return o;
        }

        static Stats lire(JsonObject o) {
            Stats s = new Stats();
            s.nom = o.has("nom") ? o.get("nom").getAsString() : "";
            s.victoires = entier(o, "victoires");
            s.defaites = entier(o, "defaites");
            s.eliminations = entier(o, "eliminations");
            s.morts = entier(o, "morts");
            s.tetes = entier(o, "tetes");
            s.manches = entier(o, "manches");
            s.parties = entier(o, "parties");
            return s;
        }

        /** Ratio eliminations / morts (morts = 0 -> eliminations). */
        public double ratio() {
            return morts == 0 ? eliminations : eliminations / (double) morts;
        }
    }

    private static final class Fiche {
        int derniere;
        ClassePerso[] classes = new ClassePerso[ClassePerso.EMPLACEMENTS];
    }

    private static Map<UUID, Fiche> classes;
    private static Map<UUID, Stats> stats;
    private static boolean classesSales, statsSales;
    private static long derniereEcritureClasses, derniereEcritureStats;

    // ------------------------------------------------------------------ classes
    /**
     * Les 5 classes du joueur (nouvelle liste) ; un emplacement jamais rempli prend la classe par defaut du catalogue
     * ({@link ClassePerso#parDefaut}). Les classes rendues sont assainies contre le catalogue courant.
     */
    public static List<ClassePerso> classes(UUID joueur, Catalogue c) {
        Fiche f = charger().get(joueur);
        List<ClassePerso> l = new ArrayList<>(ClassePerso.EMPLACEMENTS);
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) {
            ClassePerso k = f != null ? f.classes[i] : null;
            l.add(k == null ? ClassePerso.parDefaut(i, c) : k.assainir(c));
        }
        return l;
    }

    /** Classe d'un emplacement (0..4), voir {@link #classes}. */
    public static ClassePerso classe(UUID joueur, int n, Catalogue c) {
        Fiche f = charger().get(joueur);
        int k = Math.max(0, Math.min(ClassePerso.EMPLACEMENTS - 1, n));
        ClassePerso cl = f != null ? f.classes[k] : null;
        return cl == null ? ClassePerso.parDefaut(k, c) : cl.assainir(c);
    }

    /** Enregistre une classe (deja assainie par l'appelant). */
    public static void definir(UUID joueur, int n, ClassePerso classe) {
        if (n < 0 || n >= ClassePerso.EMPLACEMENTS) return;
        charger().computeIfAbsent(joueur, u -> new Fiche()).classes[n] = classe;
        classesSales = true;
    }

    /** Dernier emplacement choisi par le joueur (classe proposee par defaut). */
    public static int derniere(UUID joueur) {
        Fiche f = charger().get(joueur);
        return f == null ? 0 : f.derniere;
    }

    public static void derniere(UUID joueur, int n) {
        if (n < 0 || n >= ClassePerso.EMPLACEMENTS) return;
        Fiche f = charger().computeIfAbsent(joueur, u -> new Fiche());
        if (f.derniere != n) {
            f.derniere = n;
            classesSales = true;
        }
    }

    // ------------------------------------------------------------------ stats
    /** Statistiques du joueur (creees si absentes, a modifier puis {@link #statsModifiees()}). */
    public static Stats stats(UUID joueur) {
        return chargerStats().computeIfAbsent(joueur, u -> new Stats());
    }

    /** Statistiques existantes, ou null. */
    @Nullable
    public static Stats statsSiConnues(UUID joueur) {
        return chargerStats().get(joueur);
    }

    /** Stats existantes par nom (insensible a la casse), ou null. */
    @Nullable
    public static Map.Entry<UUID, Stats> statsParNom(String nom) {
        for (Map.Entry<UUID, Stats> e : chargerStats().entrySet()) if (e.getValue().nom.equalsIgnoreCase(nom)) return e;
        return null;
    }

    /** Les n meilleurs joueurs (victoires puis ratio), nouvelle liste. */
    public static List<Map.Entry<UUID, Stats>> meilleurs(int n) {
        List<Map.Entry<UUID, Stats>> l = new ArrayList<>(chargerStats().entrySet());
        l.sort(Comparator.<Map.Entry<UUID, Stats>>comparingInt(e -> -e.getValue().victoires).thenComparingDouble(e -> -e.getValue().ratio()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    public static void statsModifiees() {
        statsSales = true;
    }

    // ------------------------------------------------------------------ persistance
    /** Ecrit les fichiers modifies (au plus une fois toutes les 5 s, sauf si force). */
    public static void sauverSiBesoin(boolean force) {
        long now = System.currentTimeMillis();
        if (classesSales && classes != null && (force || now - derniereEcritureClasses > DELAI_MS)) {
            JsonObject racine = new JsonObject();
            classes.forEach((u, f) -> {
                JsonObject o = new JsonObject();
                o.addProperty("derniere", f.derniere);
                JsonArray a = new JsonArray();
                for (ClassePerso c : f.classes) a.add(c == null ? new JsonObject() : c.toJson());
                o.add("classes", a);
                racine.add(u.toString(), o);
            });
            if (ecrire("classes.json", racine)) classesSales = false;
            derniereEcritureClasses = now;
        }
        if (statsSales && stats != null && (force || now - derniereEcritureStats > DELAI_MS)) {
            JsonObject racine = new JsonObject();
            stats.forEach((u, s) -> racine.add(u.toString(), s.toJson()));
            if (ecrire("stats.json", racine)) statsSales = false;
            derniereEcritureStats = now;
        }
    }

    /** Arret du serveur : ecrit puis oublie tout. */
    public static void fermer() {
        sauverSiBesoin(true);
        classes = null;
        stats = null;
    }

    @Nullable
    private static Path dossier() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getServerDirectory().toPath().resolve("heralisz_matchs").resolve("duels");
    }

    private static Map<UUID, Fiche> charger() {
        if (classes != null) return classes;
        classes = new HashMap<>();
        JsonObject racine = lire("classes.json");
        if (racine != null) {
            for (Map.Entry<String, JsonElement> e : racine.entrySet()) {
                try {
                    UUID u = UUID.fromString(e.getKey());
                    JsonObject o = e.getValue().getAsJsonObject();
                    Fiche f = new Fiche();
                    f.derniere = o.has("derniere") ? Math.max(0, Math.min(ClassePerso.EMPLACEMENTS - 1, o.get("derniere").getAsInt())) : 0;
                    if (o.has("classes") && o.get("classes").isJsonArray()) {
                        JsonArray a = o.getAsJsonArray("classes");
                        for (int i = 0; i < Math.min(a.size(), ClassePerso.EMPLACEMENTS); i++) {
                            JsonElement c = a.get(i);
                            if (c.isJsonObject() && c.getAsJsonObject().size() > 0) f.classes[i] = ClassePerso.lire(c.getAsJsonObject(), i);
                        }
                    }
                    classes.put(u, f);
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.warn("[duels] classes de {} illisibles : {}", e.getKey(), ex.toString());
                }
            }
        }
        return classes;
    }

    private static Map<UUID, Stats> chargerStats() {
        if (stats != null) return stats;
        stats = new HashMap<>();
        JsonObject racine = lire("stats.json");
        if (racine != null) {
            for (Map.Entry<String, JsonElement> e : racine.entrySet()) {
                try {
                    stats.put(UUID.fromString(e.getKey()), Stats.lire(e.getValue().getAsJsonObject()));
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.warn("[duels] stats de {} illisibles : {}", e.getKey(), ex.toString());
                }
            }
        }
        return stats;
    }

    @Nullable
    private static JsonObject lire(String nom) {
        Path d = dossier();
        if (d == null) return null;
        Path f = d.resolve(nom);
        if (!Files.isRegularFile(f)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException ex) {
            HeraliszMatchs.LOG.error("[duels] {} illisible, copie gardee en .corrompu", f, ex);
            try {
                Files.copy(f, d.resolve(nom + ".corrompu"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // la lecture a deja echoue : rien de plus a faire
            }
            return null;
        }
    }

    private static boolean ecrire(String nom, JsonObject racine) {
        Path d = dossier();
        if (d == null) return false;
        try {
            Files.createDirectories(d);
            Path f = d.resolve(nom), tmp = d.resolve(nom + ".tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ex) {
            HeraliszMatchs.LOG.error("[duels] ecriture de {} impossible", nom, ex);
            return false;
        }
    }

    private static int entier(JsonObject o, String cle) {
        return o.has(cle) ? o.get(cle).getAsInt() : 0;
    }
}
