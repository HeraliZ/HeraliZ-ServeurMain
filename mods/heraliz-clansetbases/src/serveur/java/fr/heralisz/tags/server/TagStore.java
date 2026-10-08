package fr.heralisz.tags.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.catalogue.TagCatalog;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage global des joueurs : &lt;dossier serveur&gt;/heralisz_tags/joueurs.json (cosmetique : survit aux wipes de map).
 * uuid → { debloques: [ids], roue: [4 ids ou null] }. Ecriture atomique (fichier temporaire puis renommage),
 * sauvegarde une seconde apres un changement de collection, toutes les 60 s si la roue a change, et a l'arret.
 * Tout se passe sur le thread serveur.
 */
public final class TagStore {
    private TagStore() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int AUTOSAVE_TICKS = 20 * 60;
    public static final int ROUE = 4;

    /** Donnees d'un joueur. La roue a toujours 4 entrees (null = vide). */
    public static final class Joueur {
        public final Set<String> debloques = new LinkedHashSet<>();
        public final String[] roue = new String[ROUE];

        /** Roue sous forme de liste ("" = vide) pour le reseau. */
        public List<String> roueListe() {
            List<String> l = new ArrayList<>(ROUE);
            for (String s : roue) l.add(s == null ? "" : s);
            return l;
        }

        /** Le tag du quartier i, ou null s'il est vide ou plus possede. */
        public String roue(int i) {
            if (i < 0 || i >= ROUE) return null;
            String s = roue[i];
            return s != null && debloques.contains(s) ? s : null;
        }
    }

    private static final Map<UUID, Joueur> JOUEURS = new ConcurrentHashMap<>();
    /** Delai avant la sauvegarde qui suit un changement de collection (ticks) : court, mais groupe les rafales. */
    private static final int DELAI_COLLECTION_TICKS = 20;
    private static Path fichier;
    private static boolean modifie;
    private static int ticks;
    /** Compte a rebours de la sauvegarde rapide (−1 = aucune en attente). */
    private static int ticksAvantSauvegarde = -1;

    // ------------------------------------------------------------------ cycle de vie
    public static void charger(MinecraftServer server) {
        Path dossier = server.getServerDirectory().toPath().resolve("heralisz_tags");
        fichier = dossier.resolve("joueurs.json");
        JOUEURS.clear();
        modifie = false;
        ticks = 0;
        ticksAvantSauvegarde = -1;
        // migration v1 → v2 : ids inconnus du catalogue (retires) ignores, comptes pour un seul message de journal
        Map<String, Integer> inconnus = new java.util.TreeMap<>();
        try {
            Files.createDirectories(dossier);
            if (Files.exists(fichier)) {
                JsonObject racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject joueurs = racine.has("joueurs") ? racine.getAsJsonObject("joueurs") : racine;
                for (Map.Entry<String, JsonElement> e : joueurs.entrySet()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(e.getKey());
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    Joueur j = new Joueur();
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (o.has("debloques")) for (JsonElement id : o.getAsJsonArray("debloques")) {
                        String s = id.getAsString();
                        if (TagCatalog.existe(s)) j.debloques.add(s);
                        else inconnus.merge(s, 1, Integer::sum);
                    }
                    if (o.has("roue")) {
                        JsonArray r = o.getAsJsonArray("roue");
                        for (int i = 0; i < ROUE && i < r.size(); i++) {
                            JsonElement el = r.get(i);
                            // quartier vide si l'id est inconnu (retire en v2) ou n'est plus possede
                            String s = el.isJsonNull() ? null : el.getAsString();
                            j.roue[i] = s != null && j.debloques.contains(s) ? s : null;
                            if (s != null && !s.isEmpty() && !TagCatalog.existe(s)) inconnus.putIfAbsent(s, 0);
                        }
                    }
                    JOUEURS.put(uuid, j);
                }
            }
            if (!inconnus.isEmpty()) {
                // le fichier sera reecrit sans eux a la prochaine sauvegarde (autosave ou arret)
                modifie = true;
                HeraliszTags.LOGGER.warn("[HeraliZ Tags] migration : {} id(s) de tag inconnu(s) du catalogue ignore(s) et retire(s) des roues (id = joueurs concernes) : {}",
                        inconnus.size(), inconnus);
            }
            HeraliszTags.LOGGER.info("[HeraliZ Tags] {} joueur(s) charge(s) depuis {}", JOUEURS.size(), fichier);
        } catch (Exception e) {
            HeraliszTags.LOGGER.error("[HeraliZ Tags] joueurs.json illisible ({}) : on repart d'un stockage vide, l'ancien fichier est garde en .bad", fichier, e);
            try {
                Files.copy(fichier, fichier.resolveSibling("joueurs.json.bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            JOUEURS.clear();
        }
    }

    /**
     * Appele a chaque tick serveur : sauvegarde toutes les 60 s si quelque chose a change (roue), et une seconde apres
     * un changement de collection (l'objet est consomme tout de suite : un plantage dans la minute ne doit pas perdre
     * le tag).
     */
    public static void tick() {
        if (fichier == null) return;
        if (ticksAvantSauvegarde >= 0 && --ticksAvantSauvegarde < 0) {
            if (modifie) sauvegarder();
            ticks = 0;
            return;
        }
        if (++ticks >= AUTOSAVE_TICKS) {
            ticks = 0;
            if (modifie) sauvegarder();
        }
    }

    /** Programme la sauvegarde rapide (changement de collection). */
    private static void sauvegarderBientot() {
        modifie = true;
        if (ticksAvantSauvegarde < 0) ticksAvantSauvegarde = DELAI_COLLECTION_TICKS;
    }

    public static void arreter() {
        if (fichier != null && modifie) sauvegarder();
        fichier = null;
    }

    /** Ecriture atomique : fichier temporaire puis renommage. */
    public static synchronized void sauvegarder() {
        if (fichier == null) return;
        JsonObject racine = new JsonObject();
        racine.addProperty("version", 1);
        JsonObject joueurs = new JsonObject();
        for (Map.Entry<UUID, Joueur> e : JOUEURS.entrySet()) {
            Joueur j = e.getValue();
            if (j.debloques.isEmpty()) continue;
            JsonObject o = new JsonObject();
            JsonArray d = new JsonArray();
            for (String s : j.debloques) d.add(s);
            o.add("debloques", d);
            JsonArray r = new JsonArray();
            for (String s : j.roue) {
                if (s == null) r.add(com.google.gson.JsonNull.INSTANCE);
                else r.add(s);
            }
            o.add("roue", r);
            joueurs.add(e.getKey().toString(), o);
        }
        racine.add("joueurs", joueurs);
        Path tmp = fichier.resolveSibling("joueurs.json.tmp");
        try {
            Files.createDirectories(fichier.getParent());
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomique) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
            modifie = false;
        } catch (IOException e) {
            HeraliszTags.LOGGER.error("[HeraliZ Tags] sauvegarde impossible : {}", fichier, e);
        }
    }

    // ------------------------------------------------------------------ acces
    /** Donnees du joueur (creees a la volee). */
    public static Joueur get(UUID uuid) {
        return JOUEURS.computeIfAbsent(uuid, u -> new Joueur());
    }

    public static boolean possede(UUID uuid, String tagId) {
        Joueur j = JOUEURS.get(uuid);
        return j != null && j.debloques.contains(tagId);
    }

    public static Set<String> debloques(UUID uuid) {
        Joueur j = JOUEURS.get(uuid);
        return j == null ? Collections.emptySet() : Collections.unmodifiableSet(j.debloques);
    }

    /** Ajoute un tag ; vrai s'il etait nouveau. Le premier tag debloque remplit le premier quartier libre de la roue. */
    public static boolean debloquer(UUID uuid, String tagId) {
        if (!TagCatalog.existe(tagId)) return false;
        Joueur j = get(uuid);
        if (!j.debloques.add(tagId)) return false;
        for (int i = 0; i < ROUE; i++) {
            if (j.roue[i] == null) {
                j.roue[i] = tagId;
                break;
            }
        }
        sauvegarderBientot();
        return true;
    }

    /** Retire un tag (et le vide de la roue) ; vrai s'il etait possede. */
    public static boolean retirer(UUID uuid, String tagId) {
        Joueur j = JOUEURS.get(uuid);
        if (j == null || !j.debloques.remove(tagId)) return false;
        for (int i = 0; i < ROUE; i++) if (tagId.equals(j.roue[i])) j.roue[i] = null;
        sauvegarderBientot();
        return true;
    }

    /** Remplace la roue : ids inconnus, doublons ou non possedes → emplacement vide. Retourne la roue effective. */
    public static List<String> roue(UUID uuid, List<String> ids) {
        Joueur j = get(uuid);
        Set<String> vus = new LinkedHashSet<>();
        for (int i = 0; i < ROUE; i++) {
            String s = i < ids.size() ? ids.get(i) : null;
            if (s == null || s.isEmpty() || !j.debloques.contains(s) || !vus.add(s)) s = null;
            j.roue[i] = s;
        }
        modifie = true;
        return j.roueListe();
    }

    /** Nombre de joueurs connus (pour /tags admin et le journal). */
    public static int nombreJoueurs() {
        return JOUEURS.size();
    }

    public static void marquerModifie() {
        modifie = true;
    }
}
