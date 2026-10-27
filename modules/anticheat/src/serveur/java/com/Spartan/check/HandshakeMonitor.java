package com.Spartan.check;

import com.Spartan.capture.CaptureManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.integrity.LauncherGate;
import com.Spartan.util.PacketGuard;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vérifie que le client a bien envoyé ses informations après la connexion.
 *
 * Le canal réseau de Spartan est obligatoire : un client honnête l'a forcément
 * et envoie sa liste de mods dès qu'il entre dans le monde. Si rien n'arrive
 * dans le délai, le mod client a probablement été modifié ou désactivé.
 */
public final class HandshakeMonitor {
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();
    /** Heure de la vérification (jeton + /login) : ses paquets en attente sont traités juste après, jamais jugés avant. */
    private static final Map<UUID, Long> VERIFIE_DEPUIS = new ConcurrentHashMap<>();

    private HandshakeMonitor() {
    }

    public static void onLogin(UUID uuid) {
        PENDING.put(uuid, System.currentTimeMillis());
    }

    public static void tick(ServerPlayer p) {
        if (!SpartanConfig.CHECK_HANDSHAKE.get()) {
            return;
        }
        UUID uuid = p.getUUID();
        Long since = PENDING.get(uuid);
        if (since == null) {
            return;
        }
        boolean buildNeeded = SpartanConfig.CHECK_CHALLENGE.get() || SpartanConfig.CHECK_BUILD.get();
        boolean launchNeeded = !SpartanConfig.secret(SpartanConfig.LAUNCHER_SECRET).isEmpty();
        boolean captureNeeded = SpartanConfig.CAPTURE_REQUIRED.get();
        // Avant la vérification (jeton + /login), la liste des mods et le numéro de version attendent (ReceptionServeur) :
        // seuls le jeton et l'accord des captures sont exigés dans le délai. Après, tout est arrivé au tick suivant.
        boolean verifie = com.Spartan.integrity.PorteJoueurs.estVerifie(uuid);
        long now = System.currentTimeMillis();
        if (verifie) {
            VERIFIE_DEPUIS.putIfAbsent(uuid, now);
        }
        if ((!verifie || PacketGuard.hasSeen(uuid, "mods"))
                && (!verifie || !buildNeeded || PacketGuard.hasSeen(uuid, "build"))
                && (!launchNeeded || PacketGuard.hasSeen(uuid, "launch"))
                && (!captureNeeded || CaptureManager.accordRecu(uuid))) {
            if (verifie) PENDING.remove(uuid);
            return;
        }
        int timeout = SpartanConfig.HANDSHAKE_TIMEOUT.get();
        // Compté en ticks du joueur : si le serveur rame, le délai s'allonge d'autant.
        if (p.tickCount > timeout * 20 && now - since > timeout * 1000L
                && (!verifie || now - VERIFIE_DEPUIS.getOrDefault(uuid, now) > 5000L)) {
            PENDING.remove(uuid);
            CheckManager.flag("Handshake", p,
                    "aucune info client reçue après " + timeout + " s (mod client modifié ou absent ?)");
            if (launchNeeded && !PacketGuard.hasSeen(uuid, "launch") && SpartanConfig.LAUNCHER_TOKEN_REQUIRED.get()) {
                LauncherGate.refuser(p); // aucun jeton reçu : règle d'accès, même en détection silencieuse
                return;
            }
            if (captureNeeded && !CaptureManager.accordRecu(uuid)) {
                // Aucune réponse sur les captures : traité comme un refus (règle d'accès, comme le jeton).
                com.Spartan.alert.AlertManager.info(p.server, "Accès", p.getName().getString()
                        + " refusé : aucune réponse sur l'accord des captures (launcher pas à jour ou autre lanceur)",
                        com.Spartan.alert.AlertManager.COLOR_INFO);
                p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.capture",
                        "Connexion refusée : acceptez les captures d'écran de l'anticheat dans le launcher HeraliZ.\n"
                                + "(Mettez le launcher à jour, puis cliquez sur « J'autorise et je joue ».)"));
                return;
            }
            if (verifie && com.Spartan.integrity.ConformiteStricte.actif()) {
                // un jeu officiel envoie toujours sa liste de mods et son numéro de version dès qu'il est vérifié
                com.Spartan.integrity.ConformiteStricte.refuser(p, "infos du jeu jamais envoyées après " + timeout + " s ("
                        + (!PacketGuard.hasSeen(uuid, "mods") ? "liste des mods" : "numéro de version de Spartan")
                        + ") : Spartan du jeu modifié ou retiré");
                return;
            }
            if (SpartanConfig.mayKickForClientSignal()) {
                p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.non_conforme", "[Spartan] Client non conforme."));
            }
        }
    }

    public static void clear(UUID uuid) {
        PENDING.remove(uuid);
        VERIFIE_DEPUIS.remove(uuid);
    }
}
