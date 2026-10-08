package com.Spartan.util;

import com.Spartan.Spartan;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * État réseau vanilla du joueur, lu dans un champ privé de
 * ServerGamePacketListenerImpl par son nom SRG (fonctionne en dev et en prod).
 *
 * Si le champ est introuvable (autre version de Minecraft), les méthodes
 * renvoient « inconnu » et les détections gardent leur comportement prudent.
 */
public final class ServerNet {
    private static final Field AWAITING_POSITION = find("f_9766_"); // awaitingPositionFromClient

    private ServerNet() {
    }

    private static Field find(String srgName) {
        try {
            return ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, srgName);
        } catch (RuntimeException e) {
            Spartan.LOGGER.warn("[Spartan] Champ réseau {} introuvable, détection de mouvement dégradée : {}",
                    srgName, e.toString());
            return null;
        }
    }

    /**
     * Destination d'une téléportation serveur que le client n'a pas encore confirmée,
     * ou null. Toute téléportation vanilla ou de mod (/tp, perle, waystone, renvoi,
     * correction « moved wrongly ») passe par là ; tant qu'elle est en attente, le
     * serveur ignore les mouvements du client.
     */
    public static Vec3 pendingTeleport(ServerPlayer p) {
        return p.connection == null ? null : pendingTeleport(p.connection);
    }

    public static Vec3 pendingTeleport(ServerGamePacketListenerImpl listener) {
        if (AWAITING_POSITION == null || listener == null) {
            return null;
        }
        try {
            return (Vec3) AWAITING_POSITION.get(listener);
        } catch (IllegalAccessException | ClassCastException e) {
            return null;
        }
    }
}
