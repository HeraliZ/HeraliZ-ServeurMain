package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.scan.ScanManager;
import com.Spartan.serveur.GardienServeur;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 *
 * Clé du Gardien (audit du 02/11, point 2) : un launcher 1.16.0 ou plus récent fait signer par le site, à la place de
 * l'empreinte des mods, {@link GardienPreuve#engagement} (empreinte des mods + clé publique du Gardien). Le jeton est
 * accepté sous ses deux formes ; la seconde PROUVE la clé publique annoncée par le jeu (GardienServeur.lier).
 */
public final class LauncherGate {
    private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");
    /** Empreinte d'un dossier mods vide (aucune ligne). */
    private static final String SHA256_VIDE = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final Map<UUID, String> STATUS = new ConcurrentHashMap<>();
    /** Jeton accepté (ou non vérifié faute de secret) ET dossier mods officiel ou vide : pour la validation du jeu. */
    private static final Set<UUID> CONFORMES = ConcurrentHashMap.newKeySet();
    private static boolean warnedNoSecret = false;

    private LauncherGate() {
    }

    public static String describe(UUID uuid) {
        return "launcher : " + statut(uuid);
    }

    /** État du jeton en clair : « jeton valide, mods conformes », la raison d'un refus, ou « rien reçu ». */
    public static String statut(UUID uuid) {
        return STATUS.getOrDefault(uuid, "rien reçu");
    }

    /** Jeton du launcher accepté et dossier mods officiel (validation du jeu). */
    public static boolean conforme(UUID uuid) {
        return CONFORMES.contains(uuid);
    }

    public static void clear(UUID uuid) {
        STATUS.remove(uuid);
        CONFORMES.remove(uuid);
    }

    public static void onToken(ServerPlayer p, String token, String clientModsHash, String cleGardien) {
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
        String tokenProblem = checkToken(p, token, name, hash, cleGardien == null ? "" : cleGardien.trim());
        if (tokenProblem == null) {
            PorteJoueurs.jetonAccepte(p.getUUID());
        }
        if (hashProblem == null && tokenProblem == null) {
            CONFORMES.add(p.getUUID());
            return;
        }
        String problem = hashProblem != null ? hashProblem : tokenProblem;
        STATUS.put(p.getUUID(), problem);
        CheckManager.flag("Launcher", p, problem);
        if (tokenProblem != null && SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get()) {
            AlertManager.info(p.server, "Launcher", name + " refusé : " + tokenProblem, AlertManager.COLOR_INFO);
            refuser(p);
            return;
        }
        // Mode strict : un dossier mods qui n'est ni vide (launcher) ni le dossier officiel est refusé. Jamais une empreinte
        // illisible (antivirus) : rien de certain.
        if (hashProblem != null && hashProblem.startsWith("dossier mods")) {
            ConformiteStricte.refuser(p, hashProblem);
        }
    }

    /** Refus de connexion : jeu lancé hors du launcher HeraliZ (CurseForge, autre launcher, jeton expiré). */
    public static void refuser(ServerPlayer p) {
        p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.launcher",
                "Connexion refusée : lancez le jeu depuis le launcher HeraliZ.\n"
                        + "(Le jeu lancé autrement, ou depuis plus de 6 heures, n'est pas accepté.)\n\n"
                        + "Télécharger le launcher (Windows) :\n%s\nMac et Linux : page Launcher de heraliz.fr",
                SpartanConfig.lienLauncher()));
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
        // contrôle d'intégrité. Le modpack d'avant la dernière mise en ligne reste accepté.
        if (allowed.isEmpty() && officiel != null && !officiel.isEmpty() && !ScanManager.official().knowsFolder(hash)
                && !SHA256_VIDE.equals(hash)) {
            return "dossier mods différent du modpack officiel (mod ajouté, retiré ou modifié)";
        }
        return null;
    }

    /** Jeton du launcher (si un secret est configuré), et clé du Gardien qu'il engage. */
    private static String checkToken(ServerPlayer p, String token, String name, String hash, String cleGardien) {
        String secret = SpartanConfig.secret(SpartanConfig.LAUNCHER_SECRET);
        boolean cleLisible = GardienPreuve.cle(cleGardien) != null;
        if (secret == null || secret.isBlank()) {
            STATUS.put(p.getUUID(), "jeton non vérifié (launcherSecret vide)");
            // serveur d'essai : la clé annoncée par le jeu est crue sans preuve
            GardienServeur.lier(p, cleLisible ? GardienJuge.Lien.NON_VERIFIE : GardienJuge.Lien.SANS_JETON, cleGardien);
            if (SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get()) {
                // Fermé par défaut (audit A324) : un secret effacé par une mise en ligne à la main ne doit pas laisser
                // entrer n'importe quel lanceur. Pour un serveur d'essai sans site : tokenRequired = false.
                if (!warnedNoSecret) {
                    warnedNoSecret = true;
                    Spartan.LOGGER.error("[Spartan] launcher.tokenRequired=true mais launcherSecret est vide : "
                            + "TOUTES les connexions sont refusées. Renseignez le secret (mise en ligne GitHub).");
                }
                return "serveur mal réglé (secret du launcher absent) : connexions refusées";
            }
            return null;
        }
        LaunchToken.Result r = LaunchToken.verify(secret, token, name, System.currentTimeMillis() / 1000L);
        if (!r.valid()) {
            GardienServeur.lier(p, GardienJuge.Lien.SANS_JETON, "");
            return "jeton du launcher refusé : " + r.reason();
        }
        // empreinte signée par le site : celle des mods (launcher 1.15.x ou plus ancien), ou l'engagement des mods ET de
        // la clé du Gardien (1.16.0 ou plus récent). Dans les deux cas, les mods chargés doivent être ceux du lancement.
        boolean ancien = !hash.isEmpty() && r.modsHash().equalsIgnoreCase(hash);
        boolean prouve = !hash.isEmpty() && cleLisible && r.modsHash().equalsIgnoreCase(GardienPreuve.engagement(hash, cleGardien));
        if (!ancien && !prouve) {
            GardienServeur.lier(p, GardienJuge.Lien.SANS_JETON, "");
            return "les mods chargés ne sont pas ceux vérifiés par le launcher (mod ajouté ou modifié après le lancement)";
        }
        GardienServeur.lier(p, prouve ? GardienJuge.Lien.PROUVE : GardienJuge.Lien.ANCIEN, cleGardien);
        STATUS.put(p.getUUID(), prouve ? "jeton valide, mods conformes, Gardien lié au jeton"
                : "jeton valide, mods conformes (ancien launcher : Gardien non prouvé)");
        return null;
    }
}
