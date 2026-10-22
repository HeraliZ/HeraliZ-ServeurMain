package com.Spartan.serveur;

import com.Spartan.Networks.BuildPacket;
import com.Spartan.Networks.CaptureChunkPacket;
import com.Spartan.Networks.ChallengeResponsePacket;
import com.Spartan.Networks.ClientReportPacket;
import com.Spartan.Networks.LaunchTokenPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ModsPacket;
import com.Spartan.Networks.ResourcePackPacket;
import com.Spartan.Networks.ScanChunkPacket;
import com.Spartan.Networks.TestPacket;
import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.ban.BanManager;
import com.Spartan.capture.CaptureManager;
import com.Spartan.ban.PlayerRegistry;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.integrity.ChallengeManager;
import com.Spartan.integrity.LauncherGate;
import com.Spartan.scan.ScanManager;
import com.Spartan.util.HardwareId;
import com.Spartan.util.PacketGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Traitement, sur le thread du serveur, des paquets envoyés par le client (branché sur
 * {@link ModMessages#RECEPTION_SERVEUR} par {@link PartieServeur}). Le code commun ne fait que décoder.
 */
final class ReceptionServeur {
    private static final Pattern SERIAL = Pattern.compile("^[A-Za-z0-9-]{4,128}$");

    private ReceptionServeur() {
    }

    static void recevoir(ModMessages.Recu r) {
        ServerPlayer player = r.joueur();
        if (player == null || player.hasDisconnected()) {
            return;
        }
        Object p = r.paquet();
        if (p instanceof TestPacket t) {
            materiel(player, t);
        } else if (p instanceof BuildPacket b) {
            if (PacketGuard.firstTime(player.getUUID(), "build")) {
                ChallengeManager.onBuild(player, b.buildId());
            }
        } else if (p instanceof ChallengeResponsePacket c) {
            ChallengeManager.onResponse(player, c.nonce(), c.answer());
        } else if (p instanceof ClientReportPacket c) {
            signalement(player, c);
        } else if (p instanceof LaunchTokenPacket l) {
            if (PacketGuard.firstTime(player.getUUID(), "launch")) {
                LauncherGate.onToken(player, l.token(), l.modsHash());
            }
        } else if (p instanceof ModsPacket m) {
            mods(player, m);
        } else if (p instanceof ResourcePackPacket rp) {
            packs(player, rp);
        } else if (p instanceof ScanChunkPacket s) {
            ScanManager.onChunk(player, s.scanId(), s.index(), s.total(), s.data());
        } else if (p instanceof CaptureChunkPacket c) {
            CaptureManager.onChunk(player, c.captureId(), c.index(), c.total(), c.data());
        }
    }

    /**
     * Identifiant matériel du client (UUID carte mère ou HWID).
     * <ul>
     *    <li>longueur et caractères validés (UUID / hexadécimal uniquement) ;</li>
     *    <li>« unknown » (lecture matérielle impossible) n'est JAMAIS enregistré ni banni : sinon bannir une seule de ces
     *    valeurs bannirait tous les joueurs concernés ;</li>
     *    <li>chaque serial n'est traité qu'une fois par connexion, avec un plafond ;</li>
     *    <li>PC banni : expulsion ; si le compte est différent du compte banni, c'est probablement un compte
     *    secondaire : alerte au staff.</li>
     * </ul>
     */
    private static void materiel(ServerPlayer player, TestPacket t) {
        String serial = t.message().trim();
        String playerName = player.getName().getString();
        if (serial.isEmpty()) {
            // Le mod client envoie toujours une valeur : un vide indique un client altéré.
            if (SpartanConfig.SILENT_CLIENT.get()) {
                CheckManager.flag("ClientIntegrity", player, "identifiant matériel vide : client modifié ?");
            } else {
                player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.identifiant",
                        "[Spartan] Identifiant client manquant."));
                Spartan.LOGGER.info("[Spartan] {} expulsé : identifiant vide.", playerName);
            }
            return;
        }
        if (!SERIAL.matcher(serial).matches() || HardwareId.isPlaceholder(serial)) {
            return; // format invalide, lecture impossible ou identifiant générique : ignoré
        }
        if (!PacketGuard.acceptSerial(player.getUUID(), serial)) {
            return;
        }
        PlayerRegistry.recordSerial(player.getUUID(), playerName, serial);
        BanManager.BanRecord ban = BanManager.findActiveBySerial(serial);
        if (ban == null) {
            return;
        }
        boolean otherAccount = ban.uuid == null || !ban.uuid.equals(player.getUUID().toString());
        if (otherAccount) {
            AlertManager.info(player.server, "Compte secondaire",
                    playerName + " se connecte depuis le PC de " + ban.name
                            + " (banni : " + ban.reason + ", " + ban.describeExpiry() + "). Expulsé. Pour le laisser jouer : /sunban "
                            + playerName + ".",
                    AlertManager.COLOR_BAN);
        }
        player.connection.disconnect(BanManager.kickMessage(ban));
    }

    /**
     * Le client signale une trace suspecte dans son propre jeu (agent Java, threads anonymes, JVM sans protections,
     * shaderpack). C'est le serveur qui décide. Best-effort : un client modifié peut ne rien signaler.
     */
    private static void signalement(ServerPlayer player, ClientReportPacket c) {
        String type = c.type();
        String detail = c.detail();
        if (type.equals("capture")) {
            CaptureManager.accord(player, detail.equals("oui"));
            return;
        }
        if (!SpartanConfig.CHECK_CLIENT_INTEGRITY.get()) {
            return;
        }
        if (type.equals("touche")) {
            toucheSuspecte(player, detail);
            return;
        }
        if (!PacketGuard.allowReport(player.getUUID(), type + ":" + detail)) {
            return;
        }
        String safeDetail = detail.replace('\n', ' ').replace('\r', ' ');
        String lower = safeDetail.toLowerCase(Locale.ROOT);
        // Agents et threads anonymes : le contrôle d'intégrité les relève aussi, avec l'empreinte de
        // l'agent, le code exécuté et la preuve complète. Pas de seconde alerte en double.
        if (SpartanConfig.SCAN_ENABLED.get() && (type.equals("agent") || type.equals("thread"))) {
            return;
        }
        switch (type) {
            case "agent" -> {
                List<String> allowed = SpartanConfig.getAllowedJavaAgents();
                if (allowed.stream().anyMatch(lower::contains)) {
                    return;
                }
                CheckManager.flag("ClientIntegrity", player, "agent Java non autorisé : " + safeDetail);
            }
            case "thread" -> CheckManager.flag("ClientIntegrity", player, "threads anonymes : " + safeDetail);
            case "jvm" -> {
                if (SpartanConfig.REQUIRE_HARDENED_JVM.get()) {
                    CheckManager.flag("JvmHardening", player,
                            "protections anti-injection manquantes (" + safeDetail + ") : lancez le jeu via le launcher officiel");
                    if (SpartanConfig.mayKickForClientSignal()) {
                        player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.protections",
                                "Lancez le jeu depuis le launcher officiel (protections anti-triche requises)."));
                    }
                }
            }
            case "shader" -> {
                if (SpartanConfig.BLOCK_SHADERS.get() && !SpartanConfig.getAllowedShaderpacks().contains(lower)) {
                    CheckManager.flag("Shaders", player, "shaderpack actif non autorisé : " + safeDetail);
                    if (SpartanConfig.mayKickForClientSignal()) {
                        player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.shaders",
                                "Les shaders sont interdits sur ce serveur. Désactivez votre shaderpack."));
                    }
                }
            }
            default -> {
                // type inconnu : ignoré
            }
        }
    }

    /** Dernier appui signalé par joueur : un client modifié ne peut pas inonder le salon d'alertes. */
    private static final java.util.Map<java.util.UUID, Long> DERNIERE_TOUCHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TOUCHE_ECART_MS = 500;

    /** Touche d'ouverture des menus de triche (² ou Maj droite) : une alerte à chaque appui (au plus 2 par seconde). */
    private static void toucheSuspecte(ServerPlayer player, String detail) {
        if (!SpartanConfig.CHECK_CLIENT_INTEGRITY.get()) {
            return;
        }
        String nom = detail.equals("²") ? "²" : detail.equals("Maj droite") ? "Maj droite" : null;
        if (nom == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long avant = DERNIERE_TOUCHE.put(player.getUUID(), now);
        if (avant != null && now - avant < TOUCHE_ECART_MS) {
            return;
        }
        CheckManager.flagEachTime("TricheMenu", player, "touche « " + nom + " » pressée en jeu alors qu'aucune action"
                + " n'y est attachée : touche d'ouverture des menus de clients de triche (indice)");
        // Capture de l'écran au moment de l'appui (seulement si le joueur l'a autorisée dans le launcher).
        CaptureManager.demander(player, "touche « " + nom + " » pressée");
    }

    /** Liste des mods du client, comparée à la liste noire (une seule fois par connexion). */
    private static void mods(ServerPlayer player, ModsPacket m) {
        if (!PacketGuard.firstTime(player.getUUID(), "mods")) {
            return;
        }
        List<String> blacklist = SpartanConfig.getBlacklistedMods();
        List<String> found = m.mods().stream()
                .map(x -> x.toLowerCase(Locale.ROOT))
                .filter(blacklist::contains)
                .distinct()
                .collect(Collectors.toList());
        if (found.isEmpty()) {
            return;
        }
        AlertManager.dispatch(player, "ModScan", 1, "Mods interdits : " + String.join(", ", found), null);
        if (SpartanConfig.mayKickForClientSignal()) {
            player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.mod_interdit",
                    "[Spartan] Mod interdit détecté : %s", String.join(", ", found)));
        }
    }

    /** Packs de ressources actifs (journalisés) : un pack ajouté par le joueur (« file/… ») est signalé. */
    private static void packs(ServerPlayer player, ResourcePackPacket rp) {
        if (!PacketGuard.firstTime(player.getUUID(), "resourcepacks")) {
            return;
        }
        List<String> resourcePacks = rp.resourcePacks();
        Spartan.LOGGER.info("[Spartan] Packs de ressources de {} : {}", player.getName().getString(),
                String.join(", ", resourcePacks));
        if (!SpartanConfig.BLOCK_RESOURCE_PACKS.get()) {
            return;
        }
        // Les packs du jeu (vanilla, mod_resources) et ceux imposés par les mods (heraliz_interface) ne comptent pas.
        List<String> allowed = SpartanConfig.getAllowedResourcePacks();
        List<String> custom = new ArrayList<>();
        for (String pack : resourcePacks) {
            String id = pack.toLowerCase(Locale.ROOT);
            if (id.startsWith("file/") && !allowed.contains(id)) {
                custom.add(pack.substring(5));
            }
        }
        if (!custom.isEmpty()) {
            CheckManager.flag("ResourcePack", player, "pack(s) de texture non autorisé(s) : " + String.join(", ", custom));
            if (SpartanConfig.mayKickForClientSignal()) {
                player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.packs",
                        "Les packs de ressources personnalisés sont interdits. Retirez-les (dossier resourcepacks)."));
            }
        }
    }
}
