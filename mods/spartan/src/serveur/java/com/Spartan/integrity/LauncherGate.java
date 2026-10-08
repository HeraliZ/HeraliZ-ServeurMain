package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Porte d'entrée « launcher officiel » : vérifie le jeton de lancement et que
 * les mods réellement chargés sont ceux que le launcher a contrôlés.
 *
 * - launcherSecret vide : pas de vérification du jeton (allowedModsHashes reste
 *   appliqué sur l'empreinte déclarée par le client).
 * - tokenRequired=false : simple alerte (phase de mise en place) ; true : expulsion.
 */
public final class LauncherGate {
    private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Map<UUID, String> STATUS = new ConcurrentHashMap<>();
    private static boolean warnedNoSecret = false;

    private LauncherGate() {
    }

    public static String describe(UUID uuid) {
        return "launcher : " + STATUS.getOrDefault(uuid, "rien reçu");
    }

    public static void clear(UUID uuid) {
        STATUS.remove(uuid);
    }

    public static void onToken(ServerPlayer p, String token, String clientModsHash) {
        String name = p.getName().getString();
        String raw = clientModsHash == null ? "" : clientModsHash.toLowerCase(Locale.ROOT);
        boolean hashError = raw.equals("error");
        String hash = HEX64.matcher(raw).matches() ? raw : "";
        // On ne journalise jamais la valeur brute envoyée par le client.
        Spartan.LOGGER.info("[Spartan] Empreinte du dossier mods de {} : {}", name,
                hashError ? "illisible côté client" : hash.isEmpty() ? "invalide" : hash);

        String problem = checkHash(raw, hash, hashError);
        if (problem == null) {
            problem = checkToken(p, token, name, hash);
        }
        if (problem == null) {
            return;
        }
        STATUS.put(p.getUUID(), problem);
        CheckManager.flag("Launcher", p, problem);
        // Détection silencieuse : alerte seulement, même avec tokenRequired.
        if (SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get() && !SpartanConfig.SILENT_CLIENT.get()) {
            AlertManager.info(p.server, "Launcher", name + " expulsé : " + problem, AlertManager.COLOR_INFO);
            p.connection.disconnect(Component.literal(
                    "Connexion refusée : relancez le jeu depuis le launcher officiel du serveur,\n"
                            + "sans ajouter ni modifier de mods."));
        }
    }

    /** Empreinte invalide, illisible, ou hors des modpacks autorisés. */
    private static String checkHash(String raw, String hash, boolean hashError) {
        if (hashError) {
            return "empreinte des mods illisible côté client (fichier verrouillé par un antivirus ?)";
        }
        if (hash.isEmpty()) {
            return "empreinte des mods invalide";
        }
        List<String> allowed = SpartanConfig.getAllowedModsHashes();
        if (!allowed.isEmpty() && !allowed.contains(hash)) {
            return "dossier mods non autorisé (empreinte " + hash.substring(0, 12) + "…)";
        }
        return null;
    }

    /** Jeton du launcher (si un secret est configuré). */
    private static String checkToken(ServerPlayer p, String token, String name, String hash) {
        String secret = SpartanConfig.LAUNCHER_SECRET.get();
        if (secret == null || secret.isBlank()) {
            STATUS.put(p.getUUID(), "jeton non vérifié (launcherSecret vide)");
            if (SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get() && !warnedNoSecret) {
                warnedNoSecret = true;
                Spartan.LOGGER.error("[Spartan] launcher.tokenRequired=true mais launcherSecret est vide : "
                        + "vérification du jeton DÉSACTIVÉE. Renseignez le secret partagé avec le launcher.");
            }
            return null;
        }
        LaunchToken.Result r = LaunchToken.verify(secret, token, name, System.currentTimeMillis() / 1000L);
        if (!r.valid()) {
            return "jeton du launcher refusé : " + r.reason();
        }
        if (!r.modsHash().equalsIgnoreCase(hash)) {
            return "les mods chargés ne sont pas ceux vérifiés par le launcher (mod ajouté ou modifié après le lancement)";
        }
        STATUS.put(p.getUUID(), "jeton valide, mods conformes");
        return null;
    }
}
