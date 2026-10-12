package fr.heraliz.tactique.son;

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
 * Canal réseau des sons de HeraliZ Tactique (heraliz_tactique:son) : tirs silencieux lointains. Le joueur et le serveur
 * doivent avoir la même version : un joueur sans ce canal (ancien jar) est refusé à la connexion par Forge.
 */
public final class ReseauSon {
   private static final String VERSION = "1";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "son"), () -> VERSION, VERSION::equals, VERSION::equals);

   /** Tir lointain reçu chez le joueur : branché par la partie client (SonLointainClient). */
   public static final Crochet<PaquetTirLointain> RECEPTION_CLIENT = new Crochet<>();

   private ReseauSon() {
   }

   /** Enregistre les paquets (constructeur du @Mod porteur). */
   public static void init() {
      CANAL.registerMessage(0, PaquetTirLointain.class, PaquetTirLointain::encode, PaquetTirLointain::decode,
         PaquetTirLointain::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }
}
