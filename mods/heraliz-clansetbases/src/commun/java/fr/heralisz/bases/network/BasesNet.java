package fr.heralisz.bases.network;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canal reseau heralisz_bases:main (version "1", identique des deux cotes). Enregistrement des messages de
 * {@link Packets} et aides d'envoi. Un nouveau message s'ajoute EN FIN de {@link #register()} (les ids suivent l'ordre)
 * et fait monter {@link #VERSION} si un message existant change de format.
 */
public final class BasesNet {
    private BasesNet() {
    }

    public static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeraliszBases.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId;

    /** Enregistre un message (utilisable par les modules pillage et interfaces pour leurs propres paquets, apres le coeur). */
    public static <T> void msg(Class<T> type, NetworkDirection dir, BiConsumer<T, FriendlyByteBuf> enc,
                               Function<FriendlyByteBuf, T> dec, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, dir).encoder(enc).decoder(dec).consumerMainThread(handler).add();
    }

    public static void register() {
        NetworkDirection s2c = NetworkDirection.PLAY_TO_CLIENT, c2s = NetworkDirection.PLAY_TO_SERVER;
        // serveur -> client
        msg(Packets.EtatBaseS2C.class, s2c, Packets.EtatBaseS2C::write, Packets.EtatBaseS2C::read, Packets.EtatBaseS2C::handle);
        msg(Packets.PlanS2C.class, s2c, Packets.PlanS2C::write, Packets.PlanS2C::read, Packets.PlanS2C::handle);
        msg(Packets.PillablesS2C.class, s2c, Packets.PillablesS2C::write, Packets.PillablesS2C::read, Packets.PillablesS2C::handle);
        msg(Packets.OuvrirS2C.class, s2c, Packets.OuvrirS2C::write, Packets.OuvrirS2C::read, Packets.OuvrirS2C::handle);
        msg(Packets.NoticeS2C.class, s2c, Packets.NoticeS2C::write, Packets.NoticeS2C::read, Packets.NoticeS2C::handle);
        msg(Packets.HudS2C.class, s2c, Packets.HudS2C::write, Packets.HudS2C::read, Packets.HudS2C::handle);
        msg(Packets.TeleportS2C.class, s2c, Packets.TeleportS2C::write, Packets.TeleportS2C::read, Packets.TeleportS2C::handle);
        // client -> serveur
        msg(Packets.ActionC2S.class, c2s, Packets.ActionC2S::write, Packets.ActionC2S::read, Packets.ActionC2S::handle);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
