package com.Spartan.check;

import com.Spartan.config.SpartanConfig;
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
        boolean launchNeeded = !SpartanConfig.LAUNCHER_SECRET.get().isBlank();
        if (PacketGuard.hasSeen(uuid, "mods")
                && (!buildNeeded || PacketGuard.hasSeen(uuid, "build"))
                && (!launchNeeded || PacketGuard.hasSeen(uuid, "launch"))) {
            PENDING.remove(uuid);
            return;
        }
        int timeout = SpartanConfig.HANDSHAKE_TIMEOUT.get();
        // Compté en ticks du joueur : si le serveur rame, le délai s'allonge d'autant.
        if (p.tickCount > timeout * 20 && System.currentTimeMillis() - since > timeout * 1000L) {
            PENDING.remove(uuid);
            CheckManager.flag("Handshake", p,
                    "aucune info client reçue après " + timeout + " s (mod client modifié ou absent ?)");
            if (SpartanConfig.mayKickForClientSignal()) {
                p.connection.disconnect(Component.literal("[Spartan] Client non conforme."));
            }
        }
    }

    public static void clear(UUID uuid) {
        PENDING.remove(uuid);
    }
}
