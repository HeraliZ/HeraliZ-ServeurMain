package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.scan.ScanManager;
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
    /** Empreinte d'un dossier mods vide (aucune ligne). */
    private static final String SHA256_VIDE = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
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

        // Mods différents du modpack officiel : signal du jeu du joueur, alerte seulement (détection silencieuse).
        String hashProblem = checkHash(raw, hash, hashError);
        // Jeton du launcher absent ou invalide : règle d'ACCÈS au serveur. Avec tokenRequired, refus de connexion,
        // même en détection silencieuse (on ne joue que depuis le launcher HeraliZ).
        String tokenProblem = checkToken(p, token, name, hash);
        if (tokenProblem == null) {
            PorteJoueurs.jetonAccepte(p.getUUID());
        }
        if (hashProblem == null && tokenProblem == null) {
            return;
        }
        String problem = hashProblem != null ? hashProblem : tokenProblem;
        STATUS.put(p.getUUID(), problem);
        CheckManager.flag("Launcher", p, problem);
        if (tokenProblem != null && SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get()) {
            AlertManager.info(p.server, "Launcher", name + " refusé : " + tokenProblem, AlertManager.COLOR_INFO);
            refuser(p);
        }
    }

    /** Refus de connexion : jeu lancé hors du launcher HeraliZ (CurseForge, autre launcher, jeton expiré). */
    public static void refuser(ServerPlayer p) {
        p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.launcher",
                "Connexion refusée : lancez le jeu depuis le launcher HeraliZ.\n"
                        + "(Le jeu lancé autrement, ou depuis plus de 6 heures, n'est pas accepté.)"));
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
        // Sans liste à la main : le dossier mods doit être EXACTEMENT le modpack officiel fabriqué avec le serveur
        // (mod ajouté, retiré, modifié ou renommé : le contrôle d'intégrité dira lequel).
        String officiel = ScanManager.official().empreinteDossier;
        // Dossier mods vide : mods chargés par le launcher depuis son propre dossier (Helios) ; jugés un par un par le
        // contrôle d'intégrité.
        if (allowed.isEmpty() && officiel != null && !officiel.isEmpty() && !officiel.equalsIgnoreCase(hash)
                && !SHA256_VIDE.equals(hash)) {
            return "dossier mods différent du modpack officiel (mod ajouté, retiré ou modifié)";
        }
        return null;
    }

    /** Jeton du launcher (si un secret est configuré). */
    private static String checkToken(ServerPlayer p, String token, String name, String hash) {
        String secret = SpartanConfig.secret(SpartanConfig.LAUNCHER_SECRET);
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
