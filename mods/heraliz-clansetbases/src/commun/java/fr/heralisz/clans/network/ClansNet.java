package fr.heralisz.clans.network;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canal reseau heralisz_clans:main (version "2" depuis la banniere a calques, identique des deux cotes). Enregistrement des messages de
 * {@link Packets} et aides d'envoi.
 */
public final class ClansNet {
    private ClansNet() {
    }

    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeraliszClans.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId;

    private static <T> void msg(Class<T> type, NetworkDirection dir, BiConsumer<T, net.minecraft.network.FriendlyByteBuf> enc,
                                Function<net.minecraft.network.FriendlyByteBuf, T> dec,
                                BiConsumer<T, Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, dir).encoder(enc).decoder(dec).consumerMainThread(handler).add();
    }

    public static void register() {
        NetworkDirection s2c = NetworkDirection.PLAY_TO_CLIENT, c2s = NetworkDirection.PLAY_TO_SERVER;
        // serveur -> client
        msg(Packets.ClanSnapshotS2C.class, s2c, Packets.ClanSnapshotS2C::write, Packets.ClanSnapshotS2C::read, Packets.ClanSnapshotS2C::handle);
        msg(Packets.InvitesS2C.class, s2c, Packets.InvitesS2C::write, Packets.InvitesS2C::read, Packets.InvitesS2C::handle);
        msg(Packets.TagsS2C.class, s2c, Packets.TagsS2C::write, Packets.TagsS2C::read, Packets.TagsS2C::handle);
        msg(Packets.ClassementS2C.class, s2c, Packets.ClassementS2C::write, Packets.ClassementS2C::read, Packets.ClassementS2C::handle);
        msg(Packets.NoticeS2C.class, s2c, Packets.NoticeS2C::write, Packets.NoticeS2C::read, Packets.NoticeS2C::handle);
        msg(Packets.OuvrirS2C.class, s2c, Packets.OuvrirS2C::write, Packets.OuvrirS2C::read, Packets.OuvrirS2C::handle);
        msg(Packets.TerritoireHudS2C.class, s2c, Packets.TerritoireHudS2C::write, Packets.TerritoireHudS2C::read, Packets.TerritoireHudS2C::handle);
        msg(Packets.PanneauDrapeauS2C.class, s2c, Packets.PanneauDrapeauS2C::write, Packets.PanneauDrapeauS2C::read, Packets.PanneauDrapeauS2C::handle);
        // client -> serveur
        msg(Packets.CreerClanC2S.class, c2s, Packets.CreerClanC2S::write, Packets.CreerClanC2S::read, Packets.CreerClanC2S::handle);
        msg(Packets.InviterC2S.class, c2s, Packets.InviterC2S::write, Packets.InviterC2S::read, Packets.InviterC2S::handle);
        msg(Packets.ReponseInvitationC2S.class, c2s, Packets.ReponseInvitationC2S::write, Packets.ReponseInvitationC2S::read, Packets.ReponseInvitationC2S::handle);
        msg(Packets.ActionMembreC2S.class, c2s, Packets.ActionMembreC2S::write, Packets.ActionMembreC2S::read, Packets.ActionMembreC2S::handle);
        msg(Packets.BanqueC2S.class, c2s, Packets.BanqueC2S::write, Packets.BanqueC2S::read, Packets.BanqueC2S::handle);
        msg(Packets.ParametreC2S.class, c2s, Packets.ParametreC2S::write, Packets.ParametreC2S::read, Packets.ParametreC2S::handle);
        msg(Packets.BanniereC2S.class, c2s, Packets.BanniereC2S::write, Packets.BanniereC2S::read, Packets.BanniereC2S::handle);
        msg(Packets.PermsC2S.class, c2s, Packets.PermsC2S::write, Packets.PermsC2S::read, Packets.PermsC2S::handle);
        msg(Packets.SimpleC2S.class, c2s, Packets.SimpleC2S::write, Packets.SimpleC2S::read, Packets.SimpleC2S::handle);
        msg(Packets.RavitaillementC2S.class, c2s, Packets.RavitaillementC2S::write, Packets.RavitaillementC2S::read, Packets.RavitaillementC2S::handle);
        // --- ajouts (toujours en fin de liste : les identifiants existants ne bougent pas)
        msg(Packets.PermsJoueurC2S.class, c2s, Packets.PermsJoueurC2S::write, Packets.PermsJoueurC2S::read, Packets.PermsJoueurC2S::handle);
        // carte du monde (2.4.0) : demande quand la carte est ouverte, reponse avec les territoires (CarteApi)
        msg(Packets.CarteDemandeC2S.class, c2s, Packets.CarteDemandeC2S::write, Packets.CarteDemandeC2S::read, Packets.CarteDemandeC2S::handle);
        msg(Packets.CarteS2C.class, s2c, Packets.CarteS2C::write, Packets.CarteS2C::read, Packets.CarteS2C::handle);
        // saison des clans (3.6.0) : arbre d'ameliorations et points de saison
        msg(Packets.AmeliorerC2S.class, c2s, Packets.AmeliorerC2S::write, Packets.AmeliorerC2S::read, Packets.AmeliorerC2S::handle);
        msg(Packets.ArbreSaisonS2C.class, s2c, Packets.ArbreSaisonS2C::write, Packets.ArbreSaisonS2C::read, Packets.ArbreSaisonS2C::handle);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    /** Aux joueurs a moins de rayon blocs du point, dans ce monde. */
    public static void toNear(ServerLevel level, Vec3 pos, double rayon, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, rayon, level.dimension())), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
