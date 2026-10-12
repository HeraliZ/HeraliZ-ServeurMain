package fr.heraliz.tactique.allonge;

import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.pont.Crochet;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal réseau de HeraliZ Tactique pour « s'allonger » (heraliz_tactique:allonge). Le joueur et le serveur doivent avoir
 * la même version : un joueur sans ce canal (ancien jar) est refusé à la connexion par Forge, avec un message clair.
 */
public final class ReseauAllonge {
   private static final String VERSION = "1";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "allonge"), () -> VERSION, VERSION::equals, VERSION::equals);

   /** Refus du serveur reçu chez le joueur : branché par la partie client (AllongeClient). */
   public static final Crochet<PaquetRefusAllonge> REFUS_CLIENT = new Crochet<>();

   private ReseauAllonge() {
   }

   /** Enregistre les paquets (constructeur du @Mod porteur). */
   public static void init() {
      CANAL.registerMessage(0, PaquetRefusAllonge.class, PaquetRefusAllonge::encode, PaquetRefusAllonge::decode,
         PaquetRefusAllonge::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }
}
