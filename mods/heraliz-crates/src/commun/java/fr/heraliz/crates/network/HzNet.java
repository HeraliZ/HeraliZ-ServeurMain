package fr.heraliz.crates.network;

import fr.heraliz.crates.HeralizCrates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau du mod : interface d'ouverture des caisses (facon CS:GO) et editeur de butin des administrateurs.
 */
public final class HzNet {
    private HzNet() {
    }

    /** 2 : interface epuree (une caisse a la fois) ; 3 (Caisses 2.22.0) : CaseScreen dit si la boutique vend la cle. */
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeralizCrates.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Packets.CaseScreen.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.CaseScreen::write).decoder(Packets.CaseScreen::read).consumerMainThread(Packets.CaseScreen::handle).add();
        CHANNEL.messageBuilder(Packets.CaseOpen.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.CaseOpen::write).decoder(Packets.CaseOpen::read).consumerMainThread(Packets.CaseOpen::handle).add();
        CHANNEL.messageBuilder(Packets.CaseResult.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.CaseResult::write).decoder(Packets.CaseResult::read).consumerMainThread(Packets.CaseResult::handle).add();
        CHANNEL.messageBuilder(Packets.CaseSkip.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.CaseSkip::write).decoder(Packets.CaseSkip::read).consumerMainThread(Packets.CaseSkip::handle).add();
        CHANNEL.messageBuilder(Packets.CaseClose.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.CaseClose::write).decoder(Packets.CaseClose::read).consumerMainThread(Packets.CaseClose::handle).add();
        CHANNEL.messageBuilder(Packets.EditorOpen.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.EditorOpen::write).decoder(Packets.EditorOpen::read).consumerMainThread(Packets.EditorOpen::handle).add();
        CHANNEL.messageBuilder(Packets.EditorSave.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.EditorSave::write).decoder(Packets.EditorSave::read).consumerMainThread(Packets.EditorSave::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
