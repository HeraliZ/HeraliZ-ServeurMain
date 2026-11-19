package com.Spartan.check.movement;

import com.Spartan.Spartan;
import com.Spartan.check.movement.model.Silences;
import com.Spartan.util.ServerNet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Observateur Netty placé juste avant le gestionnaire de paquets vanilla.
 *
 * Il voit CHAQUE paquet de mouvement tel que le client l'a envoyé — y compris
 * ceux que vanilla rejette —, chaque téléportation / vitesse imposée par le
 * serveur et nos pings avec leurs réponses, dans l'ordre exact. Il ne modifie ni ne bloque rien : il recopie
 * l'essentiel dans la file du joueur et laisse passer le paquet.
 */
final class PacketObserver extends ChannelDuplexHandler {
    static final String NAME = "spartan_observer";
    private static final String VANILLA_HANDLER = "packet_handler";

    private final MoveInbox inbox;
    private final Connection connection;
    /** Trous dans l'arrivée des paquets du joueur (fil réseau de cette connexion seulement). */
    private final Silences silences = new Silences();

    private PacketObserver(MoveInbox inbox, Connection connection) {
        this.inbox = inbox;
        this.connection = connection;
    }

    /** Branche l'observateur sur la connexion du joueur. @return false si impossible. */
    static boolean install(ServerPlayer p, MoveInbox inbox) {
        Connection c = p.connection.connection;
        Channel ch = c.channel();
        if (ch == null) {
            return false;
        }
        ch.eventLoop().execute(() -> {
            ChannelPipeline pipeline = ch.pipeline();
            if (pipeline.get(VANILLA_HANDLER) == null) {
                Spartan.LOGGER.error("[Spartan] Gestionnaire réseau vanilla introuvable : mouvement non analysé");
                return;
            }
            if (pipeline.get(NAME) != null) {
                pipeline.remove(NAME); // reconnexion sur le même canal : on repart de la nouvelle file
            }
            pipeline.addBefore(VANILLA_HANDLER, NAME, new PacketObserver(inbox, c));
        });
        return true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            inbound(msg);
        } catch (Throwable t) {
            // Jamais casser la connexion à cause de l'analyse.
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            outbound(msg);
        } catch (Throwable t) {
            // idem
        }
        super.write(ctx, msg, promise);
    }

    private void inbound(Object msg) {
        long now = System.nanoTime();
        silences.paquet(now); // 19/11 : trous du réseau (cadence réelle des tirs)
        if (msg instanceof ServerboundCustomPayloadPacket p) {
            tirTacz(p, now);
            return;
        }
        if (msg instanceof ServerboundMovePlayerPacket m) {
            inbox.push(MoveInbox.Event.move(now, m.hasPosition(), m.getX(0), m.getY(0), m.getZ(0),
                    m.hasRotation(), m.getYRot(0), m.getXRot(0), m.isOnGround()));
        } else if (msg instanceof ServerboundPongPacket pong) {
            inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.PONG, now, pong.getId(), null));
        } else if (msg instanceof ServerboundAcceptTeleportationPacket a) {
            inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.ACK, now, a.getId(), null));
        } else if (msg instanceof ServerboundInteractPacket || msg instanceof ServerboundUseItemOnPacket
                || msg instanceof ServerboundUseItemPacket || msg instanceof ServerboundPlayerActionPacket
                || msg instanceof ServerboundSwingPacket) {
            if (changesUse(msg)) {
                inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.USE_CHANGE, now, 0, null));
            }
            inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.ACTION, now, 0, null));
        } else if (msg instanceof ServerboundSetCarriedItemPacket || msg instanceof ServerboundContainerClickPacket) {
            inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.USE_CHANGE, now, 0, null));
        }
    }

    /**
     * Tir de TaCZ (19/11, cadence réelle des tirs, voir TirsTacz) : message {@code ClientMessagePlayerShoot} du canal
     * {@code tacz:network} de TaCZ 1.1.8, reconnu sans rien lire de TaCZ : indice {@value #TACZ_INDICE_TIR} (premier message
     * inscrit par TaCZ, son compteur part de 1) puis l'heure du tir (8 octets) et la charge (4 octets), 13 octets en tout ;
     * aucun autre message du jeu vers le serveur de TaCZ n'a cette taille. L'heure annoncée, l'heure d'arrivée et le silence
     * du réseau qui venait de finir sont notés pour le fil du serveur. Rien n'est lu ni changé dans le paquet (lecture à
     * position fixe, l'index de lecture ne bouge pas). Un TaCZ d'une autre version : rien n'est reconnu, la cadence réelle
     * ne s'applique pas (le journal le dit, TirsTacz).
     */
    private void tirTacz(ServerboundCustomPayloadPacket p, long now) {
        if (!TACZ.equals(p.getIdentifier())) {
            return;
        }
        FriendlyByteBuf b = p.getData();
        int i = b.readerIndex();
        if (b.writerIndex() - i != TACZ_TAILLE_TIR || b.getUnsignedByte(i) != TACZ_INDICE_TIR) {
            return;
        }
        inbox.tirs.noter(b.getLong(i + 1), now, silences.recent(now, SILENCE_FENETRE));
        inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.SHOT, now, 0, null)); // à sa place parmi les visées
    }

    /** Canal réseau de TaCZ. */
    private static final ResourceLocation TACZ = new ResourceLocation("tacz", "network");
    /** Indice du message de tir dans le canal de TaCZ 1.1.8 (NetworkHandler.init, compteur à 1). */
    static final int TACZ_INDICE_TIR = 1;
    /** Indice (1) + heure du tir (long, 8) + charge (float, 4). */
    static final int TACZ_TAILLE_TIR = 13;
    /** Un silence du réseau compte pour les tirs qui arrivent jusqu'à 1,5 s après sa fin. */
    private static final long SILENCE_FENETRE = 1_500_000_000L;

    /**
     * Le client commence, relâche, échange ou lâche l'objet en main : à partir de ce paquet,
     * son ralentissement (manger, boire, bouclier…) peut avoir changé avant que le serveur
     * ne le traite.
     */
    private static boolean changesUse(Object msg) {
        if (msg instanceof ServerboundPlayerActionPacket a) {
            return switch (a.getAction()) {
                case RELEASE_USE_ITEM, DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> true;
                default -> false;
            };
        }
        return msg instanceof ServerboundUseItemPacket || msg instanceof ServerboundUseItemOnPacket;
    }

    private void outbound(Object msg) {
        if (msg instanceof ClientboundPingPacket ping) {
            if (Transactions.ours(ping.getId())) {
                inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.PING, System.nanoTime(), ping.getId(), null));
            }
        } else if (msg instanceof ClientboundPlayerPositionPacket pp) {
            // Destination ABSOLUE : lue dans l'état vanilla (le paquet peut être relatif).
            // teleport() l'écrit avant d'envoyer le paquet ; une téléportation plus récente
            // l'écraserait, mais son propre paquet suit et devient alors la référence.
            var target = connection.getPacketListener() instanceof ServerGamePacketListenerImpl l
                    ? ServerNet.pendingTeleport(l) : null;
            inbox.push(MoveInbox.Event.simple(MoveInbox.Kind.TELEPORT_SENT, System.nanoTime(), pp.getId(), target));
        } else if (msg instanceof ClientboundSetEntityMotionPacket mp) {
            // Vitesse imposée au joueur (recul, dégâts, propulsion de mod) : le client la remplace par celle-ci.
            if (connection.getPacketListener() instanceof ServerGamePacketListenerImpl l
                    && l.player != null && mp.getId() == l.player.getId()) {
                inbox.push(MoveInbox.Event.impulse(System.nanoTime(), mp.getXa() / 8000.0, mp.getYa() / 8000.0,
                        mp.getZa() / 8000.0, false));
            }
        } else if (msg instanceof ClientboundExplodePacket ep) {
            // Souffle d'une explosion : il s'ajoute à la vitesse du client.
            if (ep.getKnockbackX() != 0 || ep.getKnockbackY() != 0 || ep.getKnockbackZ() != 0) {
                inbox.push(MoveInbox.Event.impulse(System.nanoTime(), ep.getKnockbackX(), ep.getKnockbackY(),
                        ep.getKnockbackZ(), true));
            }
        }
    }
}
