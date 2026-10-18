package fr.heraliz.crac.network;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import fr.heraliz.crac.build.net.BuildPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    /** Réception côté client des paquets serveur -> client (ReceptionClient, ReceptionClientBuild) : branchée par la partie client. */
    /** Paquet client -> serveur reçu, avec son contexte (expéditeur, fil). */
    public record Recu(Object paquet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
    }

    /** Réception côté serveur (<Paquet>Traitement.traiter) : branchée par la partie serveur. */
    public static final fr.heraliz.crac.pont.Crochet<Recu> RECEPTION_SERVEUR = new fr.heraliz.crac.pont.Crochet<>();

    /** Appelé par le handle de chaque paquet client -> serveur ; sans partie serveur, le paquet est ignoré. */
    public static void recevoirServeur(Object paquet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        if (RECEPTION_SERVEUR.branche()) {
            RECEPTION_SERVEUR.accept(new Recu(paquet, ctx));
        } else {
            ctx.get().setPacketHandled(true);
        }
    }

    public static final fr.heraliz.crac.pont.Crochet<Object> RECEPTION_CLIENT = new fr.heraliz.crac.pont.Crochet<>();

    private static final String PROTOCOL = "20";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("crac", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private NetworkHandler() {
    }

    /**
     * Every message is bound to one direction: Forge then refuses (and disconnects) a packet travelling the wrong way
     * before decoding it, so a client cannot feed S2C packets to the server.
     */
    public static void register() {
        int id = 0;
        toServer(id++, C2SRequestNearbyClaims.class, C2SRequestNearbyClaims::encode, C2SRequestNearbyClaims::decode, C2SRequestNearbyClaims::handle);
        toClient(id++, S2CNearbyClaims.class, S2CNearbyClaims::encode, S2CNearbyClaims::decode, S2CNearbyClaims::handle);
        toClient(id++, S2COpenGroupCreate.class, S2COpenGroupCreate::encode, S2COpenGroupCreate::decode, S2COpenGroupCreate::handle);
        toServer(id++, C2SCreateGroup.class, C2SCreateGroup::encode, C2SCreateGroup::decode, C2SCreateGroup::handle);
        toClient(id++, S2COpenGroupManage.class, S2COpenGroupManage::encode, S2COpenGroupManage::decode, S2COpenGroupManage::handle);
        toClient(id++, S2COnlinePlayers.class, S2COnlinePlayers::encode, S2COnlinePlayers::decode, S2COnlinePlayers::handle);
        toServer(id++, C2SGroupAction.class, C2SGroupAction::encode, C2SGroupAction::decode, C2SGroupAction::handle);
        toClient(id++, S2COpenInvites.class, S2COpenInvites::encode, S2COpenInvites::decode, S2COpenInvites::handle);
        toServer(id++, C2SInviteResponse.class, C2SInviteResponse::encode, C2SInviteResponse::decode, C2SInviteResponse::handle);
        toClient(id++, S2CRaidBlock.class, S2CRaidBlock::encode, S2CRaidBlock::decode, S2CRaidBlock::handle);
        toServer(id++, C2SPickup.class, C2SPickup::encode, C2SPickup::decode, C2SPickup::handle);
        toServer(id++, C2SPlacePlacable.class, C2SPlacePlacable::encode, C2SPlacePlacable::decode, C2SPlacePlacable::handle);
        toServer(id++, C2SAdminLogin.class, C2SAdminLogin::encode, C2SAdminLogin::decode, C2SAdminLogin::handle);
        toServer(id++, C2SAdminAction.class, C2SAdminAction::encode, C2SAdminAction::decode, C2SAdminAction::handle);
        toClient(id++, S2CAdminData.class, S2CAdminData::encode, S2CAdminData::decode, S2CAdminData::handle);
        toServer(id++, C2SAdminConfig.class, C2SAdminConfig::encode, C2SAdminConfig::decode, C2SAdminConfig::handle);
        toServer(id++, C2SAdminPlacable.class, C2SAdminPlacable::encode, C2SAdminPlacable::decode, C2SAdminPlacable::handle);
        toClient(id++, S2CPlacableSet.class, S2CPlacableSet::encode, S2CPlacableSet::decode, S2CPlacableSet::handle);
        id = BuildPackets.register(CHANNEL, id);
        toClient(id++, S2CConfigResync.class, S2CConfigResync::encode, S2CConfigResync::decode, S2CConfigResync::handle);
        // monde des bases (1.5.0) : carrières, joueurs protégés « nouveau joueur »
        toClient(id++, S2CCarrieres.class, S2CCarrieres::encode, S2CCarrieres::decode, S2CCarrieres::handle);
        toClient(id++, S2CProteges.class, S2CProteges::encode, S2CProteges::decode, S2CProteges::handle);
        // tutoriel des bases guidé (1.8.0) : pièces à poser, fin en cinéma
        toClient(id++, S2CTuto.class, S2CTuto::encode, S2CTuto::decode, S2CTuto::handle);
        toClient(id++, S2CTutoFin.class, S2CTutoFin::encode, S2CTutoFin::decode, S2CTutoFin::handle);
        // accès donnés dans l'armoire à outils (1.10.0)
        toServer(id++, C2SAccesBase.class, C2SAccesBase::encode, C2SAccesBase::decode, C2SAccesBase::handle);
        toClient(id++, S2CAccesBase.class, S2CAccesBase::encode, S2CAccesBase::decode, S2CAccesBase::handle);
    }

    private static <M> void toServer(
        int id, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
        BiConsumer<M, Supplier<NetworkEvent.Context>> handler
    ) {
        CHANNEL.registerMessage(id, type, encoder, decoder, handler, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static <M> void toClient(
        int id, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
        BiConsumer<M, Supplier<NetworkEvent.Context>> handler
    ) {
        CHANNEL.registerMessage(id, type, encoder, decoder, handler, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
