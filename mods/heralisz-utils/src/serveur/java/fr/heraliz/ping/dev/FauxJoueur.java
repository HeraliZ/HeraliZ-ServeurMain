package fr.heraliz.ping.dev;

import com.mojang.authlib.GameProfile;
import fr.heraliz.ping.net.PingNet;
import fr.heraliz.ping.server.PingService;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Banc d'essai (client de dev seulement) : un second joueur simule, connu de la liste des joueurs du serveur (les
 * clans et les matchs le voient « en ligne ») mais relie a un canal Netty en memoire. Tout ce que le serveur lui
 * envoie y reste : on compte les paquets de ping recus, donc on verifie qui recoit quoi sans second client.
 */
final class FauxJoueur {
    final ServerPlayer joueur;
    final EmbeddedChannel canal;

    private FauxJoueur(ServerPlayer joueur, EmbeddedChannel canal) {
        this.joueur = joueur;
        this.canal = canal;
    }

    static FauxJoueur creer(MinecraftServer serveur, ServerLevel niveau, String nom, double x, double y, double z) throws ReflectiveOperationException {
        UUID u = UUID.nameUUIDFromBytes(("ping-banc:" + nom).getBytes(StandardCharsets.UTF_8));
        ServerPlayer p = new ServerPlayer(serveur, niveau, new GameProfile(u, nom));
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel ch = new EmbeddedChannel(c);
        new ServerGamePacketListenerImpl(serveur, c, p);
        p.moveTo(x, y, z, 0f, 0f);
        PlayerList liste = serveur.getPlayerList();
        joueurs(liste).add(p);
        parUuid(liste).put(u, p);
        PingService.forcerPresence(u, true);
        return new FauxJoueur(p, ch);
    }

    /** Paquets de ping recus depuis le dernier appel, par index de message (0..4) ; le canal est vide ensuite. */
    int[] compter() {
        int[] n = new int[5];
        Object o;
        while ((o = canal.readOutbound()) != null) {
            if (o instanceof ClientboundCustomPayloadPacket pk && PingNet.ID.equals(pk.getIdentifier())) {
                int i = pk.getData().getUnsignedByte(pk.getData().readerIndex());
                if (i >= 0 && i < n.length) n[i]++;
            }
        }
        return n;
    }

    /** Pings (PingS2C) recus depuis le dernier comptage. */
    int pings() {
        return compter()[PingNet.I_PING_S2C];
    }

    void retirer(MinecraftServer serveur) {
        try {
            PlayerList liste = serveur.getPlayerList();
            joueurs(liste).remove(joueur);
            parUuid(liste).remove(joueur.getUUID());
        } catch (ReflectiveOperationException ex) {
            BancServeur.log("retrait du faux joueur {} impossible : {}", joueur.getGameProfile().getName(), ex.toString());
        }
        PingService.forcerPresence(joueur.getUUID(), false);
        canal.close();
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> joueurs(PlayerList l) throws ReflectiveOperationException {
        Field f = PlayerList.class.getDeclaredField("players");
        f.setAccessible(true);
        return (List<ServerPlayer>) f.get(l);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> parUuid(PlayerList l) throws ReflectiveOperationException {
        Field f = PlayerList.class.getDeclaredField("playersByUUID");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(l);
    }
}
