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
 * ({@code heraliz_emote/debloquees.json}) : elles survivent aux changements de carte, comme les camos à vie. Thread du
 * serveur seulement.
 * <p>Fichier illisible (audit du 02/11, point 30) : il n'est JAMAIS réécrit tant que le serveur tourne (avant, la
 * première emote débloquée écrivait une liste presque vide à sa place, malgré la copie {@code .illisible}). Copie datée à
 * côté ({@code debloquees.json.illisible-<date>}), les déblocages de la session vont dans
 * {@code debloquees.json.secours-<date>} (à reprendre à la main), et le staff connecté est prévenu (journal et message
 * aux op).</p>
 */
public final class Deblocages {
    private static final Path FICHIER = Path.of("heraliz_emote", "debloquees.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random HASARD = new Random();
    private static final Map<UUID, Set<UUID>> PAR_JOUEUR = new HashMap<>();
    private static boolean lu;
    /** Fichier illisible : les changements de cette session sont écrits ici, jamais dans le fichier principal. */
    @Nullable
    private static Path secours;

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

    /**
     * Wipe de la réouverture (05/11, HeraliZ Quêtes par réflexion : ne jamais renommer ni changer la signature) : toutes
     * les emotes débloquées des joueurs pour qui {@code epargne} est faux sont retirées, connectés ou non. Renvoie le
     * nombre de joueurs vidés ; -1 si le fichier était illisible au démarrage (rien n'est touché).
     */
    public static int wipeReouverture(java.util.function.Predicate<UUID> epargne) {
        lire();
        if (secours != null) return -1;
        int n = 0;
        for (var it = PAR_JOUEUR.entrySet().iterator(); it.hasNext(); ) {
            if (!epargne.test(it.next().getKey())) {
                it.remove();
                n++;
            }
        }
        if (n > 0) ecrire();
        HeralizEmote.LOGGER.warn("[emote] wipe de la réouverture : emotes débloquées retirées à {} joueur(s)", n);
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
            String date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            secours = FICHIER.resolveSibling("debloquees.json.secours-" + date);
            HeralizEmote.LOGGER.error("[emote] {} ILLISIBLE : il ne sera pas réécrit ; copie datée gardée à côté, déblocages de"
                    + " cette session dans {} (à reprendre à la main). Réparer le fichier puis redémarrer", FICHIER, secours.getFileName(), ex);
            PAR_JOUEUR.clear();
            try {
                Files.copy(FICHIER, FICHIER.resolveSibling("debloquees.json.illisible-" + date), StandardCopyOption.REPLACE_EXISTING);
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
        // fichier illisible au démarrage : jamais réécrit, la session va dans le fichier de secours
        Path cible = secours != null ? secours : FICHIER;
        try {
            Files.createDirectories(cible.getParent());
            Path tmp = cible.resolveSibling(cible.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            HeralizEmote.LOGGER.error("[emote] écriture de {} impossible", cible, ex);
        }
    }

    /** Un op (niveau 2) qui se connecte alors que le fichier était illisible est prévenu. */
    public static void prevenirStaff(ServerPlayer p) {
        lire();
        if (secours == null || !p.hasPermissions(2)) return;
        p.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback("heraliz_emote.fichier_illisible",
                "[Emotes] Le fichier des emotes débloquées (%s) était illisible : il n'est pas réécrit. Voir le journal du serveur.",
                FICHIER.toString()).withStyle(net.minecraft.ChatFormatting.RED));
    }
}
