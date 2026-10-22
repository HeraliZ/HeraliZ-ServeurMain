package fr.heraliz.emote.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.emote.HeralizEmote;
import fr.heraliz.emote.ListeEmotes;
import fr.heraliz.emote.reseau.ReseauEmotes;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Les emotes débloquées par chaque joueur (caisse des emotes, staff). Gardées hors du monde, à la racine du serveur
 * ({@code heraliz_emote/debloquees.json}) : elles survivent aux changements de carte, comme les camos à vie. Un fichier
 * illisible n'est jamais écrasé (copie {@code .illisible}, puis on repart de zéro). Thread du serveur seulement.
 */
public final class Deblocages {
    private static final Path FICHIER = Path.of("heraliz_emote", "debloquees.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random HASARD = new Random();
    private static final Map<UUID, Set<UUID>> PAR_JOUEUR = new HashMap<>();
    private static boolean lu;

    private Deblocages() {
    }

    /** Créatif et spectateur : tout est permis (staff qui essaie). */
    public static boolean tout(ServerPlayer p) {
        return p.isCreative() || p.isSpectator();
    }

    public static boolean a(ServerPlayer p, UUID emote) {
        return tout(p) || de(p.getUUID()).contains(emote);
    }

    public static Set<UUID> de(UUID joueur) {
        lire();
        return PAR_JOUEUR.getOrDefault(joueur, Set.of());
    }

    /** Vrai si l'emote vient d'être ajoutée (faux si déjà débloquée ou inconnue). */
    public static boolean debloquer(ServerPlayer p, UUID emote) {
        if (ListeEmotes.de(emote) == null) return false;
        lire();
        boolean ajout = PAR_JOUEUR.computeIfAbsent(p.getUUID(), u -> new LinkedHashSet<>()).add(emote);
        if (ajout) {
            ecrire();
            envoyer(p);
        }
        return ajout;
    }

    /** Une emote pas encore débloquée, tirée au hasard et débloquée ; null s'il les a toutes. */
    @Nullable
    public static ListeEmotes.Emote debloquerAuHasard(ServerPlayer p) {
        List<ListeEmotes.Emote> restantes = new ArrayList<>();
        Set<UUID> deja = de(p.getUUID());
        for (ListeEmotes.Emote e : ListeEmotes.toutes()) {
            if (!deja.contains(e.uuid())) restantes.add(e);
        }
        if (restantes.isEmpty()) return null;
        ListeEmotes.Emote e = restantes.get(HASARD.nextInt(restantes.size()));
        debloquer(p, e.uuid());
        return e;
    }

    public static int toutDebloquer(ServerPlayer p) {
        int n = 0;
        lire();
        Set<UUID> s = PAR_JOUEUR.computeIfAbsent(p.getUUID(), u -> new LinkedHashSet<>());
        for (ListeEmotes.Emote e : ListeEmotes.toutes()) {
            if (s.add(e.uuid())) n++;
        }
        if (n > 0) ecrire();
        envoyer(p);
        return n;
    }

    public static boolean retirer(ServerPlayer p, UUID emote) {
        lire();
        Set<UUID> s = PAR_JOUEUR.get(p.getUUID());
        boolean ok = s != null && s.remove(emote);
        if (ok) {
            ecrire();
            envoyer(p);
        }
        return ok;
    }

    public static int toutRetirer(ServerPlayer p) {
        lire();
        Set<UUID> s = PAR_JOUEUR.remove(p.getUUID());
        int n = s == null ? 0 : s.size();
        if (n > 0) ecrire();
        envoyer(p);
        return n;
    }

    /** Ce que le jeu du joueur doit griser (connexion, changement de mode de jeu, déblocage). */
    public static void envoyer(ServerPlayer p) {
        ReseauEmotes.envoyer(p, new ReseauEmotes.Deblocages(tout(p), List.copyOf(de(p.getUUID()))));
    }

    // ------------------------------------------------------------------ fichier
    private static void lire() {
        if (lu) return;
        lu = true;
        if (!Files.exists(FICHIER)) return;
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(FICHIER, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : racine.entrySet()) {
                Set<UUID> s = new LinkedHashSet<>();
                for (JsonElement u : e.getValue().getAsJsonArray()) s.add(UUID.fromString(u.getAsString()));
                PAR_JOUEUR.put(UUID.fromString(e.getKey()), s);
            }
        } catch (Exception ex) {
            HeralizEmote.LOGGER.error("[emote] {} illisible : gardé en .illisible, on repart de zéro", FICHIER, ex);
            PAR_JOUEUR.clear();
            try {
                Files.copy(FICHIER, FICHIER.resolveSibling("debloquees.json.illisible"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException io) {
                HeralizEmote.LOGGER.error("[emote] copie de {} impossible", FICHIER, io);
            }
        }
    }

    private static void ecrire() {
        JsonObject racine = new JsonObject();
        for (Map.Entry<UUID, Set<UUID>> e : PAR_JOUEUR.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            JsonArray a = new JsonArray();
            for (UUID u : e.getValue()) a.add(u.toString());
            racine.add(e.getKey().toString(), a);
        }
        try {
            Files.createDirectories(FICHIER.getParent());
            Path tmp = FICHIER.resolveSibling("debloquees.json.tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            Files.move(tmp, FICHIER, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            HeralizEmote.LOGGER.error("[emote] écriture de {} impossible", FICHIER, ex);
        }
    }
}
