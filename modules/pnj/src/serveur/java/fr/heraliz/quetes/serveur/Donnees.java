package fr.heraliz.quetes.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Progression des joueurs, gardée HORS du monde ({@code heraliz_quetes/joueurs/<uuid>.json} à la racine du serveur) :
 * elle survit aux changements de carte, comme les clans et les hérals. Écrite à la déconnexion, toutes les 30 s si elle a
 * changé, et à l'arrêt du serveur. Un fichier illisible n'est jamais écrasé (il est mis de côté en .illisible).
 */
final class Donnees {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Ce qu'un joueur a fait avec un PNJ. */
    public static final class DPnj {
        public boolean rencontre;
        public int rep;
        /** Index de la prochaine quête de la suite. */
        public int suite;
        /** Tableau des contrats proposés (quêtes tirées, en JSON). */
        public List<String> tableau = new ArrayList<>();
        public long changerApres;
        public int contrats;
    }

    /** Une quête en cours. */
    public static final class DActive {
        public String cle;
        public String pnj;
        /** Quête de la suite (identifiant) ; vide pour un contrat. */
        public String suite = "";
        /** Contrat : la quête tirée, en JSON. */
        public String json = "";
        public int etape;
        public int progres;
        public List<String> cibles = new ArrayList<>();
        public Set<String> faits = new LinkedHashSet<>();
        public long debut, debutEtape;
        public int tenu;
        public boolean aRendre;
        /** Objet de quête ramassé : repère -> point, et quand. */
        public Map<String, String> refPoint = new HashMap<>();
        public Map<String, Long> refQuand = new HashMap<>();
        /** Objets déjà comptés (étapes « differents »). */
        public Set<String> vus = new LinkedHashSet<>();
    }

    public static final class DJoueur {
        public String nom = "";
        public Map<String, DPnj> pnj = new LinkedHashMap<>();
        public Map<String, DActive> actives = new LinkedHashMap<>();
        public Set<String> finies = new LinkedHashSet<>();
        public List<String> titres = new ArrayList<>();
        public String suivie = "";
        public int contratsFinis;

        DPnj pnj(String id) {
            return pnj.computeIfAbsent(id, k -> new DPnj());
        }
    }

    private static Path dossier;
    private static final Map<UUID, DJoueur> CACHE = new HashMap<>();
    private static final Set<UUID> SALES = new LinkedHashSet<>();

    private Donnees() {
    }

    static void demarrer(Path racineServeur) {
        dossier = racineServeur.resolve("heraliz_quetes").resolve("joueurs");
        try {
            Files.createDirectories(dossier);
        } catch (IOException ex) {
            LOG.error("[heraliz_quetes] dossier {} : {}", dossier, ex.getMessage());
        }
        CACHE.clear();
        SALES.clear();
    }

    static void arreter() {
        toutEcrire();
        CACHE.clear();
        SALES.clear();
        dossier = null;
    }

    static DJoueur de(UUID u) {
        DJoueur j = CACHE.get(u);
        if (j != null) return j;
        j = lire(u);
        CACHE.put(u, j);
        return j;
    }

    static void sale(UUID u) {
        SALES.add(u);
    }

    /** Écrit tout de suite la progression (après une quête payée : jamais payée deux fois après un arrêt brutal). */
    static void ecrireMaintenant(UUID u) {
        ecrire(u);
    }

    /** Écrit et oublie (déconnexion). */
    static void liberer(UUID u) {
        ecrire(u);
        CACHE.remove(u);
    }

    static void toutEcrire() {
        for (UUID u : new ArrayList<>(SALES)) ecrire(u);
    }

    private static Path fichier(UUID u) {
        return dossier.resolve(u + ".json");
    }

    private static DJoueur lire(UUID u) {
        if (dossier == null) return new DJoueur();
        Path f = fichier(u);
        if (!Files.exists(f)) return new DJoueur();
        try {
            DJoueur j = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), DJoueur.class);
            if (j == null) j = new DJoueur();
            if (j.pnj == null) j.pnj = new LinkedHashMap<>();
            if (j.actives == null) j.actives = new LinkedHashMap<>();
            if (j.finies == null) j.finies = new LinkedHashSet<>();
            if (j.titres == null) j.titres = new ArrayList<>();
            if (j.suivie == null) j.suivie = "";
            for (DPnj p : j.pnj.values()) if (p.tableau == null) p.tableau = new ArrayList<>();
            for (DActive a : j.actives.values()) {
                if (a.cibles == null) a.cibles = new ArrayList<>();
                if (a.faits == null) a.faits = new LinkedHashSet<>();
                if (a.refPoint == null) a.refPoint = new HashMap<>();
                if (a.refQuand == null) a.refQuand = new HashMap<>();
                if (a.vus == null) a.vus = new LinkedHashSet<>();
                if (a.suite == null) a.suite = "";
                if (a.json == null) a.json = "";
            }
            return j;
        } catch (Exception ex) {
            LOG.error("[heraliz_quetes] progression illisible {} ({}) : mise de côté", f, ex.getMessage());
            try {
                Files.move(f, f.resolveSibling(u + ".json.illisible"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // on garde le fichier tel quel
            }
            return new DJoueur();
        }
    }

    private static void ecrire(UUID u) {
        SALES.remove(u);
        DJoueur j = CACHE.get(u);
        if (j == null || dossier == null) return;
        Path f = fichier(u), tmp = dossier.resolve(u + ".json.tmp");
        try {
            Files.writeString(tmp, GSON.toJson(j), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex2) {
                LOG.error("[heraliz_quetes] écriture de {} : {}", f, ex2.getMessage());
            }
        }
    }
}
