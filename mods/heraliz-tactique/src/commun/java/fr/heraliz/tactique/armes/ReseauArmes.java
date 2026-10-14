package fr.heraliz.tactique.armes;

import fr.heraliz.tactique.HeralizTactique;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal réseau des réglages d'armes (heraliz_tactique:armes) : même version exigée des deux côtés. */
public final class ReseauArmes {
   private static final String VERSION = "1";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "armes"), () -> VERSION, VERSION::equals, VERSION::equals);

   private ReseauArmes() {
   }

   /** Enregistre les paquets (constructeur du @Mod porteur). */
   public static void init() {
      CANAL.registerMessage(0, PaquetDegatsArmes.class, PaquetDegatsArmes::encode, PaquetDegatsArmes::decode,
         PaquetDegatsArmes::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   /** Serveur -> un joueur. */
   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }
}
