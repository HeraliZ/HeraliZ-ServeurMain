package fr.heraliz.ping.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.ping.HeralizPing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Canaux de pings (25/10, demande des propriétaires) : par défaut, le canal d'un joueur est son clan (comme avant) ;
 * {@code /ping canal <nom>} le met sur un canal libre, partagé par tous ceux qui ont tapé le même nom (joueurs solos qui
 * s'entendent hors clan), {@code /ping canal clan} le ramène à son clan. Un joueur sur un canal ne reçoit plus les pings
 * de son clan et son clan ne reçoit plus les siens : un ping ne va qu'aux joueurs du même canal (et du même monde, voir
 * {@link Cercles}). En match, l'équipe passe toujours avant.
 * <p>Gardé en mémoire (joueur -> canal, et l'index inverse canal -> joueurs) et sur le disque hors du monde
 * ({@code heraliz_ping/canaux.json} à la racine du serveur) : un canal survit à la déconnexion et au redémarrage.
 * L'écriture (un petit fichier, seulement quand un joueur change de canal) part sur un fil à part, dans l'ordre ; à
 * l'arrêt, on attend qu'elle soit finie. Tout le reste : fil serveur.
 */
public final class Canaux {
    private Canaux() {
    }

    /** Nom d'un canal : 2 à 16 lettres ou chiffres (rangé en minuscules). */
    public static final Pattern NOM = Pattern.compile("[a-z0-9]{2,16}");
    /** Mot réservé : revenir au canal du clan. */
    public static final String CLAN = "clan";
    /** Joueurs connectés au plus sur un canal au moment d'y entrer (un canal n'est pas un salon public). */
    public static final int MAX_EN_LIGNE = 16;

    private static final Map<UUID, String> CANAL = new HashMap<>();
    private static final Map<String, Set<UUID>> MEMBRES = new HashMap<>();
    /** Couleurs des pings d'un canal (choisie par le nom : la même pour tous ses membres). */
    private static final int[] COULEURS = {0x5FD3A8, 0xF2B84B, 0xE0779B, 0x9C8CFF, 0x5EC8F2, 0xF28C5E, 0xB7E05B, 0xE8E0D0};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Nullable
    private static Path fichier;
    @Nullable
    private static ExecutorService ecriture;

    // ------------------------------------------------------------------ lecture
    /** Canal libre du joueur, ou null (canal du clan, le défaut). */
    @Nullable
    public static String de(UUID joueur) {
        return CANAL.isEmpty() ? null : CANAL.get(joueur);
    }

    /** Nom nettoyé (minuscules) s'il est valide et pas réservé, sinon null. */
    @Nullable
    public static String nettoyer(String nom) {
        if (nom == null) return null;
        String n = nom.trim().toLowerCase(Locale.ROOT);
        return NOM.matcher(n).matches() && !CLAN.equals(n) ? n : null;
    }

    /** Ajoute à {@code out} les membres connectés du canal (l'auteur compris s'il en est). Aucune allocation. */
    static void enLigne(String canal, MinecraftServer serveur, List<ServerPlayer> out) {
        Set<UUID> m = MEMBRES.get(canal);
        if (m == null) return;
        PlayerList liste = serveur.getPlayerList();
        for (UUID u : m) {
            ServerPlayer p = liste.getPlayer(u);
            if (p != null) out.add(p);
        }
    }

    /** Nombre de membres connectés du canal. */
    public static int compterEnLigne(String canal, MinecraftServer serveur) {
        Set<UUID> m = MEMBRES.get(canal);
        if (m == null) return 0;
        int n = 0;
        PlayerList liste = serveur.getPlayerList();
        for (UUID u : m) if (liste.getPlayer(u) != null) n++;
        return n;
    }

    /** Couleur des pings du canal. */
    static int couleur(String canal) {
        return COULEURS[Math.floorMod(canal.hashCode(), COULEURS.length)];
    }

    // ------------------------------------------------------------------ écriture
    /** Met le joueur sur ce canal (nom déjà nettoyé) ; null = retour au clan. Vrai si quelque chose a changé. */
    public static boolean definir(UUID joueur, @Nullable String canal) {
        String avant = CANAL.get(joueur);
        if (canal == null ? avant == null : canal.equals(avant)) return false;
        if (avant != null) {
            Set<UUID> m = MEMBRES.get(avant);
            if (m != null && m.remove(joueur) && m.isEmpty()) MEMBRES.remove(avant);
        }
        if (canal == null) CANAL.remove(joueur);
        else {
            CANAL.put(joueur, canal);
            MEMBRES.computeIfAbsent(canal, k -> new HashSet<>()).add(joueur);
        }
        sauver();
        return true;
    }

    // ------------------------------------------------------------------ disque
    /** Démarrage du serveur : relit le fichier (absent = personne sur un canal ; abîmé = mis de côté, rien de perdu). */
    public static void charger(MinecraftServer serveur) {
        CANAL.clear();
        MEMBRES.clear();
        fichier = serveur.getServerDirectory().toPath().resolve("heraliz_ping").resolve("canaux.json");
        if (ecriture == null || ecriture.isShutdown()) {
            ecriture = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HeraliZ canaux de pings");
                t.setDaemon(true);
                return t;
            });
        }
        if (!Files.isRegularFile(fichier)) return;
        try {
            JsonElement racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8));
            JsonObject canaux = racine.isJsonObject() && racine.getAsJsonObject().has("canaux")
                    ? racine.getAsJsonObject().getAsJsonObject("canaux") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : canaux.entrySet()) {
                try {
                    UUID u = UUID.fromString(e.getKey());
                    String c = e.getValue().isJsonPrimitive() ? nettoyer(e.getValue().getAsString()) : null;
                    if (c == null) continue;
                    CANAL.put(u, c);
                    MEMBRES.computeIfAbsent(c, k -> new HashSet<>()).add(u);
                } catch (IllegalArgumentException ignore) {
                    // ligne illisible : ignorée
                }
            }
            HeralizPing.LOG.info("[ping] {} joueur(s) sur {} canal(aux) de pings", CANAL.size(), MEMBRES.size());
        } catch (Exception ex) {
            // fichier abîmé : gardé à côté (jamais écrasé par la prochaine écriture), les joueurs repartent sur leur clan
            HeralizPing.LOG.error("[ping] {} illisible, mis de côté : {}", fichier, ex.toString());
            try {
                Files.move(fichier, fichier.resolveSibling("canaux.json.illisible-" + System.currentTimeMillis()));
            } catch (IOException io) {
                HeralizPing.LOG.error("[ping] copie de {} impossible", fichier, io);
            }
        }
    }

    /** Copie l'état sur le fil serveur, l'écrit sur le fil d'écriture (fichier à côté puis mis à la place). */
    private static void sauver() {
        Path f = fichier;
        ExecutorService ex = ecriture;
        if (f == null || ex == null || ex.isShutdown()) return;
        JsonObject canaux = new JsonObject();
        for (Map.Entry<UUID, String> e : CANAL.entrySet()) canaux.addProperty(e.getKey().toString(), e.getValue());
        JsonObject racine = new JsonObject();
        racine.add("canaux", canaux);
        String texte = GSON.toJson(racine);
        ex.execute(() -> ecrire(f, texte));
    }

    private static void ecrire(Path f, String texte) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, texte, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            HeralizPing.LOG.error("[ping] écriture de {} impossible", f, e);
        }
    }

    /** Arrêt du serveur : les écritures en attente sont finies avant de tout oublier. */
    public static void arreter() {
        ExecutorService ex = ecriture;
        ecriture = null;
        if (ex != null) {
            ex.shutdown();
            try {
                if (!ex.awaitTermination(10, TimeUnit.SECONDS)) HeralizPing.LOG.warn("[ping] écriture des canaux trop longue à l'arrêt");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        CANAL.clear();
        MEMBRES.clear();
        fichier = null;
    }
}
