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
public final class ReceptionServeur {
    private static final Pattern SERIAL = Pattern.compile("^[A-Za-z0-9-]{4,128}$");

    private ReceptionServeur() {
    }

    static void recevoir(ModMessages.Recu r) {
        ServerPlayer player = r.joueur();
        if (player == null || player.hasDisconnected()) {
            return;
        }
        Object p = r.paquet();
        // Avant la vérification (jeton + /login), seul ce qui sert à se faire vérifier est traité tout de suite ; le reste
        // attend la vérification (sinon un jeu lancé sous le pseudo d'un autre pouvait lui faire porter de fausses
        // alertes, une fausse capture ou le PC d'un banni, 24/10). Rapports et morceaux de capture ou de contrôle non
        // demandés : jetés.
        if (!com.Spartan.integrity.PorteJoueurs.estVerifie(player.getUUID())
                && !(p instanceof LaunchTokenPacket || (p instanceof ClientReportPacket c0 && c0.type().equals("capture")))) {
            if (p instanceof TestPacket t) {
                BanManager.BanRecord ban = BanManager.findActiveBySerial(t.message().trim());
                if (ban != null) {
                    player.connection.disconnect(BanManager.kickMessage(ban)); // un PC banni ne joue pas, vérifié ou non
                    return;
                }
            }
            // paquets d'arrivée (envoyés une seule fois, avant le /login) : traités après la vérification ; les appuis de
            // touches d'avant ne comptent pas
            if (p instanceof TestPacket || p instanceof ModsPacket || p instanceof ResourcePackPacket || p instanceof BuildPacket
                    || (p instanceof ClientReportPacket c1 && !c1.type().equals("touche"))) {
                java.util.List<Object> l = ATTENTE.computeIfAbsent(player.getUUID(), u -> new java.util.ArrayList<>());
                if (l.size() < 16) l.add(p);
            }
            return;
        }
        traiter(player, p);
    }

    private static void traiter(ServerPlayer player, Object p) {
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
    /** Paquets d'arrivée (matériel, mods, packs) reçus avant la vérification : traités une fois le joueur vérifié. */
    private static final java.util.Map<java.util.UUID, java.util.List<Object>> ATTENTE = new java.util.concurrent.ConcurrentHashMap<>();

    /** À chaque tick (rien à faire le plus souvent) : paquets en attente des joueurs maintenant vérifiés (fil du serveur). */
    public static void tick(net.minecraft.server.MinecraftServer srv) {
        if (ATTENTE.isEmpty()) return;
        for (var it = ATTENTE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            if (p == null || p.hasDisconnected()) {
                it.remove();
            } else if (com.Spartan.integrity.PorteJoueurs.estVerifie(p.getUUID())) {
                it.remove();
                for (Object paquet : en.getValue()) traiter(p, paquet);
            }
        }
    }

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
        if (type.equals("gardien")) {
            GardienServeur.recevoir(player, detail);
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

    /**
     * Appuis signalés par joueur. Une alerte à chaque appui (choix du staff), au plus 2 par seconde ; au-delà de
     * {@link #TOUCHE_MAX_FENETRE} alertes en 10 min, les appuis sont comptés et résumés en une alerte par minute : un
     * jeu modifié qui enverrait des appuis sans fin ne peut ni noyer le salon du staff ni prendre la place des
     * alertes des autres joueurs.
     */
    private static final java.util.Map<java.util.UUID, Touches> TOUCHES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TOUCHE_ECART_MS = 500;
    private static final long TOUCHE_FENETRE_MS = 10 * 60_000L;
    private static final int TOUCHE_MAX_FENETRE = 10;
    private static final long TOUCHE_RESUME_MS = 60_000L;

    private static final class Touches {
        long dernier;
        long debutFenetre;
        int alertes;
        int caches;
        long dernierResume;
    }

    public static void oublierTouches(java.util.UUID id) {
        TOUCHES.remove(id);
    }

    /** Touche d'ouverture des menus de triche (² ou Maj droite). */
    private static void toucheSuspecte(ServerPlayer player, String detail) {
        if (!SpartanConfig.CHECK_CLIENT_INTEGRITY.get()) {
            return;
        }
        String nom = detail.equals("²") ? "²" : detail.equals("Maj droite") ? "Maj droite" : null;
        if (nom == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Touches t = TOUCHES.computeIfAbsent(player.getUUID(), u -> new Touches());
        if (now - t.dernier < TOUCHE_ECART_MS) {
            return;
        }
        t.dernier = now;
        if (now - t.debutFenetre > TOUCHE_FENETRE_MS) {
            t.debutFenetre = now;
            t.alertes = 0;
        }
        if (t.alertes >= TOUCHE_MAX_FENETRE) {
            t.caches++;
            if (now - t.dernierResume >= TOUCHE_RESUME_MS) {
                t.dernierResume = now;
                CheckManager.flagEachTime("TricheMenu", player, t.caches + " appui(s) de plus sur ² ou Maj droite"
                        + " (alertes regroupées : plus de " + TOUCHE_MAX_FENETRE + " en 10 min)");
                t.caches = 0;
            }
            return;
        }
        t.alertes++;
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
