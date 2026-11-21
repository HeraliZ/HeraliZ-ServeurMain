package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.caracrea.network.CaraCreaNetwork;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Takes the voice frames out of a player's connection on its network thread, before the game sees them (players
 * whose dedicated UDP channel is blocked send their voice this way).
 *
 * <p>Forge already hands mod packets to the network thread, but Mohist (the production server) adds the
 * Bukkit rule that every custom payload waits for the server thread: the voice would then be relayed once
 * per tick, in bursts, and stop whenever the server lags. Reading the frames here keeps the relay on the
 * network threads on both servers, as designed. Every other packet goes on unchanged.
 *
 * <p>21/11 (fin de la validation) : posé devant la porte de Spartan quand elle est là ({@value #PORTE_SPARTAN}). La porte
 * retient les paquets des mods jusqu'au /login (128 au plus) ; la voix d'avant le /login y prenait des places (les
 * paquets des autres mods arrivés ensuite étaient jetés) et repartait après le /login. Maintenant la voix est lue ici
 * d'abord, et jetée tant que le joueur n'a pas fait son /login ({@link VoiceServer#relayTcp}) : comme par l'UDP.
 */
final class VoiceInbound extends ChannelInboundHandlerAdapter {
    static final String NAME = "caracrea_voice";
    /** Nom du filtre « porte des paquets » de Spartan (com.Spartan.integrity.PorteJoueurs), s'il est installé. */
    static final String PORTE_SPARTAN = "spartan_porte";
    private static boolean warned;

    /** Only the UUID: a respawn replaces the ServerPlayer object, the connection stays. */
    private final java.util.UUID player;

    private VoiceInbound(ServerPlayer player) {
        this.player = player.getUUID();
    }

    /**
     * Server thread, on login: inserts the handler just before Spartan's gate when there is one (21/11), otherwise just
     * before the game's packet handler.
     */
    static void install(ServerPlayer player) {
        try {
            Channel channel = player.connection.connection.channel();
            if (channel == null || channel.pipeline().get(NAME) != null) {
                return;
            }
            String before = channel.pipeline().get(PORTE_SPARTAN) != null ? PORTE_SPARTAN : "packet_handler";
            channel.pipeline().addBefore(before, NAME, new VoiceInbound(player));
        } catch (RuntimeException e) {
            // Unusual pipeline (a proxy plugin renamed the handler): the voice still works through Forge, tick-bound on Mohist.
            if (!warned) {
                warned = true;
                CaraCrea.LOGGER.warn("Voice chat: could not hook the connection ({}); voice frames go through the server thread", e.toString());
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ServerboundCustomPayloadPacket packet && CaraCreaNetwork.NAME.equals(packet.getName())) {
            FriendlyByteBuf data = packet.getInternalData();
            if (data != null && data.readableBytes() > 0 && data.getUnsignedByte(data.readerIndex()) == CaraCreaNetwork.VOICE_UP) {
                try {
                    FriendlyByteBuf frame = new FriendlyByteBuf(data.duplicate());
                    frame.readUnsignedByte();
                    CaraCreaNetwork.VoiceUp up = CaraCreaNetwork.VoiceUp.decode(frame);
                    VoiceServer.INBOUND.incrementAndGet();
                    VoiceServer.relayTcp(player, up.sequence(), up.flags(), up.data(), 0, up.data().length);
                } catch (RuntimeException e) {
                    // Malformed or oversized frame: dropped.
                } finally {
                    release(data);
                }
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    /** The payload was copied out of the network buffer for this packet; nobody else will read it. */
    private static void release(ByteBuf data) {
        try {
            if (data.refCnt() > 0) {
                data.release();
            }
        } catch (RuntimeException e) {
            // Already released: nothing to do.
        }
    }
}
