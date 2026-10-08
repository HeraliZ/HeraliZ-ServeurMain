package fr.heralisz.tags.network;

import fr.heralisz.tags.HeraliszTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau heralisz_tags:main (version "2" depuis 0.3.1 : EtatS2C porte la portee du serveur ; client et serveur
 * identiques). S→C : EtatS2C, TagS2C, TagRetireS2C, OuvrirIndexS2C. C→S : TagC2S, RoueC2S, OuvrirIndexC2S.
 */
public final class TagsNet {
    private TagsNet() {
    }

    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeraliszTags.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Packets.EtatS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.EtatS2C::write).decoder(Packets.EtatS2C::read).consumerMainThread(Packets.EtatS2C::handle).add();
        CHANNEL.messageBuilder(Packets.TagS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.TagS2C::write).decoder(Packets.TagS2C::read).consumerMainThread(Packets.TagS2C::handle).add();
        CHANNEL.messageBuilder(Packets.TagRetireS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.TagRetireS2C::write).decoder(Packets.TagRetireS2C::read).consumerMainThread(Packets.TagRetireS2C::handle).add();
        CHANNEL.messageBuilder(Packets.OuvrirIndexS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.OuvrirIndexS2C::write).decoder(Packets.OuvrirIndexS2C::read).consumerMainThread(Packets.OuvrirIndexS2C::handle).add();
        CHANNEL.messageBuilder(Packets.TagC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.TagC2S::write).decoder(Packets.TagC2S::read).consumerMainThread(Packets.TagC2S::handle).add();
        CHANNEL.messageBuilder(Packets.RoueC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.RoueC2S::write).decoder(Packets.RoueC2S::read).consumerMainThread(Packets.RoueC2S::handle).add();
        CHANNEL.messageBuilder(Packets.OuvrirIndexC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.OuvrirIndexC2S::write).decoder(Packets.OuvrirIndexC2S::read).consumerMainThread(Packets.OuvrirIndexC2S::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** A tous les joueurs du monde a moins de rayon blocs du point (PacketDistributor.NEAR). */
    public static void toNear(ServerLevel level, Vec3 point, double rayon, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(point.x, point.y, point.z, rayon, level.dimension())), msg);
    }

    public static void toAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
