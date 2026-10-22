package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Qui a quel grade : {@code heraliz_grades/grades.json} à la racine du serveur (hors du monde : survit à un changement de
 * carte), et {@code heraliz_grades/journal.log} (chaque changement, avec qui l'a fait).
 * <p>
 * Sécurité : un fichier illisible n'est JAMAIS écrasé ; tant qu'il l'est, personne n'a de grade (que Survivant) et tout
 * changement est refusé. Écriture atomique (fichier temporaire puis remplacement). À n'utiliser que sur le fil serveur.
 */
final class RegistreGrades {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Grades enregistrés d'un joueur : au plus un du staff, au plus un de la boutique, et des grades spéciaux (Streamer…) ;
     * nom = dernier pseudo connu.
     */
    record Fiche(String nom, Grade staff, Grade boutique, Set<Grade> speciaux) {
        Fiche(String nom, Grade staff, Grade boutique) {
            this(nom, staff, boutique, Set.of());
        }

        boolean vide() {
            return staff == null && boutique == null && speciaux.isEmpty();
        }

        /** Tous ses grades enregistrés (sans Survivant). */
        List<Grade> grades() {
            List<Grade> l = new ArrayList<>();
            if (staff != null) l.add(staff);
            if (boutique != null) l.add(boutique);
            l.addAll(speciaux);
            return l;
        }

        /** Grade affiché devant le pseudo : celui qui a le plus haut rang d'affichage parmi ceux qui s'affichent. */
        Grade affiche() {
            Grade best = null;
            for (Grade g : grades()) if (g.prefixe && (best == null || g.ordre > best.ordre)) best = g;
            return best != null ? best : Grade.SURVIVANT;
        }

        boolean a(Grade g) {
            return g == Grade.SURVIVANT || staff == g || boutique == g || speciaux.contains(g);
        }

        Fiche avec(Grade g, boolean present) {
            Grade s = staff, b = boutique;
            Set<Grade> sp = new LinkedHashSet<>(speciaux);
            if (g.type == Grade.Type.STAFF) s = present ? g : (staff == g ? null : staff);
            else if (g.type == Grade.Type.BOUTIQUE) b = present ? g : (boutique == g ? null : boutique);
            else if (present) sp.add(g);
            else sp.remove(g);
            return new Fiche(nom, s, b, Set.copyOf(sp));
        }

        /** La même fiche sans ce grade (grade supprimé de l'éditeur, ou qui a changé de famille). */
        Fiche sans(Grade g) {
            Set<Grade> sp = new LinkedHashSet<>(speciaux);
            sp.remove(g);
            return new Fiche(nom, staff == g ? null : staff, boutique == g ? null : boutique, Set.copyOf(sp));
        }
    }

    enum Resultat { OK, DEJA, ABSENT, ILLISIBLE, ERREUR }

    private final Path fichier, journal;
    private final Map<UUID, Fiche> fiches = new HashMap<>();
    private boolean illisible;

    RegistreGrades(Path dossier) {
        this.fichier = dossier.resolve("grades.json");
        this.journal = dossier.resolve("journal.log");
        charger();
    }

    boolean illisible() {
        return illisible;
    }

    private void charger() {
        fiches.clear();
        illisible = false;
        if (!Files.exists(fichier)) {
            LOG.info("[grades] aucun fichier {} : personne n'a encore de grade", fichier);
            return;
        }
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject joueurs = racine.getAsJsonObject("joueurs");
            if (joueurs == null) throw new IllegalStateException("clé « joueurs » absente");
            for (Map.Entry<String, JsonElement> e : joueurs.entrySet()) {
                UUID uuid = UUID.fromString(e.getKey());
                JsonObject o = e.getValue().getAsJsonObject();
                Grade staff = lireGrade(o, "staff", Grade.Type.STAFF), boutique = lireGrade(o, "boutique", Grade.Type.BOUTIQUE);
                String nom = o.has("nom") ? o.get("nom").getAsString() : "?";
                Set<Grade> speciaux = new LinkedHashSet<>();
                if (o.has("speciaux")) {
                    for (JsonElement x : o.getAsJsonArray("speciaux")) {
                        Grade g = Grade.parId(x.getAsString());
                        if (g != null && g.type == Grade.Type.SPECIAL) speciaux.add(g);
                        else LOG.warn("[grades] {} : grade spécial « {} » inconnu, ignoré", nom, x.getAsString());
                    }
                }
                Fiche f = new Fiche(nom, staff, boutique, Set.copyOf(speciaux));
                if (!f.vide()) fiches.put(uuid, f);
            }
            LOG.info("[grades] {} joueur(s) gradé(s) chargé(s) depuis {}", fiches.size(), fichier);
        } catch (Exception ex) {
            fiches.clear();
            illisible = true;
            LOG.error("[grades] {} ILLISIBLE ({}) : personne n'a de grade et aucun changement n'est possible tant qu'il n'est pas réparé. Le fichier n'est pas touché.", fichier, ex.toString());
        }
    }

    /** Grade d'une case staff / boutique ; un grade supprimé ou d'une autre famille est ignoré (pas une erreur de fichier). */
    private static Grade lireGrade(JsonObject o, String cle, Grade.Type type) {
        if (!o.has(cle) || o.get(cle).isJsonNull()) return null;
        Grade g = Grade.parId(o.get(cle).getAsString());
        if (g == null || g.type != type) {
            LOG.warn("[grades] grade « {} » inconnu ou d'une autre famille pour {}, ignoré", o.get(cle).getAsString(), cle);
            return null;
        }
        return g;
    }

    private void sauver() throws IOException {
        JsonObject joueurs = new JsonObject();
        fiches.forEach((uuid, f) -> {
            JsonObject o = new JsonObject();
            o.addProperty("nom", f.nom());
            if (f.staff() != null) o.addProperty("staff", f.staff().id);
            if (f.boutique() != null) o.addProperty("boutique", f.boutique().id);
            if (!f.speciaux().isEmpty()) {
                com.google.gson.JsonArray sp = new com.google.gson.JsonArray();
                f.speciaux().forEach(g -> sp.add(g.id));
                o.add("speciaux", sp);
            }
            joueurs.add(uuid.toString(), o);
        });
        JsonObject racine = new JsonObject();
        racine.addProperty("version", 1);
        racine.add("joueurs", joueurs);
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling("grades.json.tmp");
        Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomiqueImpossible) {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------ lecture
    /** Fiche du joueur (null s'il n'a que Survivant, ou si le fichier est illisible). */
    Fiche fiche(UUID uuid) {
        return fiches.get(uuid);
    }

    /** Grade affiché (préfixe). */
    Grade affiche(UUID uuid) {
        Fiche f = fiches.get(uuid);
        return f == null ? Grade.SURVIVANT : f.affiche();
    }

    /** Vrai si le joueur a un grade du staff au moins égal à min (min doit être un grade du staff). */
    boolean staffAuMoins(UUID uuid, Grade min) {
        Fiche f = fiches.get(uuid);
        return f != null && f.staff() != null && f.staff().rang >= min.rang;
    }

    List<Map.Entry<UUID, Fiche>> tous() {
        List<Map.Entry<UUID, Fiche>> l = new ArrayList<>(fiches.entrySet());
        l.sort((a, b) -> {
            int c = Integer.compare(poids(b.getValue()), poids(a.getValue()));
            return c != 0 ? c : a.getValue().nom().compareToIgnoreCase(b.getValue().nom());
        });
        return l;
    }

    private static int poids(Fiche f) {
        return f.affiche().ordre;
    }

    /** Joueurs qui ont ce grade (uuid -> dernier pseudo connu). */
    List<Map.Entry<UUID, String>> membres(Grade g) {
        List<Map.Entry<UUID, String>> l = new ArrayList<>();
        for (Map.Entry<UUID, Fiche> e : tous()) if (e.getValue().a(g)) l.add(Map.entry(e.getKey(), e.getValue().nom()));
        return l;
    }

    /** Retire ce grade à tout le monde (grade supprimé ou changé de famille dans l'éditeur) ; faux si l'écriture échoue. */
    boolean retirerPartout(Grade g, String auteur) {
        if (illisible) return false;
        Map<UUID, Fiche> avant = new HashMap<>(fiches);
        boolean change = false;
        for (Map.Entry<UUID, Fiche> e : new ArrayList<>(fiches.entrySet())) {
            if (!e.getValue().grades().contains(g)) continue;
            Fiche f = e.getValue().sans(g);
            if (f.vide()) fiches.remove(e.getKey());
            else fiches.put(e.getKey(), f);
            journaliser(auteur + " | retirer (grade supprimé ou changé de famille) | " + e.getValue().nom() + " (" + e.getKey() + ") | " + g.id);
            change = true;
        }
        if (!change) return true;
        try {
            sauver();
            return true;
        } catch (IOException ex) {
            fiches.clear();
            fiches.putAll(avant);
            LOG.error("[grades] écriture de {} impossible : {}", fichier, ex.toString());
            return false;
        }
    }

    /** Tient à jour le dernier pseudo connu (pas de journal, pas d'écriture si rien ne change). */
    void noterNom(UUID uuid, String nom) {
        Fiche f = fiches.get(uuid);
        if (illisible || f == null || f.nom().equals(nom)) return;
        fiches.put(uuid, new Fiche(nom, f.staff(), f.boutique(), f.speciaux()));
        try {
            sauver();
        } catch (IOException ex) {
            fiches.put(uuid, f);
            LOG.warn("[grades] nouveau pseudo de {} non enregistré : {}", nom, ex.toString());
        }
    }

    // ------------------------------------------------------------------ changements (console seulement : voir CommandesGrades)
    Resultat changer(UUID uuid, String nom, Grade g, boolean donner, String auteur) {
        if (illisible) return Resultat.ILLISIBLE;
        if (!g.attribuable()) return Resultat.ERREUR;
        Fiche avant = fiches.get(uuid);
        Fiche base = avant != null ? new Fiche(nom, avant.staff(), avant.boutique(), avant.speciaux()) : new Fiche(nom, null, null);
        if (donner && base.a(g)) return Resultat.DEJA;
        if (!donner && !base.a(g)) return Resultat.ABSENT;
        Fiche apres = base.avec(g, donner);
        if (apres.vide()) fiches.remove(uuid);
        else fiches.put(uuid, apres);
        try {
            sauver();
        } catch (IOException ex) {
            if (avant == null) fiches.remove(uuid);
            else fiches.put(uuid, avant);
            LOG.error("[grades] écriture de {} impossible, changement annulé : {}", fichier, ex.toString());
            return Resultat.ERREUR;
        }
        Grade ancien = avant == null ? null : g.type == Grade.Type.STAFF ? avant.staff() : g.type == Grade.Type.BOUTIQUE ? avant.boutique() : null;
        String remplace = donner && ancien != null ? " (remplace " + ancien.id + ")" : "";
        journaliser(auteur + " | " + (donner ? "donner" : "retirer") + " | " + nom + " (" + uuid + ") | " + g.id + remplace);
        return Resultat.OK;
    }

    void journaliser(String ligne) {
        String l = LocalDateTime.now().format(DATE) + " | " + ligne + System.lineSeparator();
        LOG.info("[grades] {}", ligne);
        try {
            Files.createDirectories(journal.getParent());
            Files.writeString(journal, l, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            LOG.warn("[grades] journal impossible à écrire : {}", ex.toString());
        }
    }
}
