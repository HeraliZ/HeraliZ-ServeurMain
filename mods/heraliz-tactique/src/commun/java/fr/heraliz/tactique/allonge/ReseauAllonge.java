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
 * la même version : un joueur sans ce canal ou avec une autre version (ancien jar) est refusé à la connexion par Forge,
 * avec un message clair. Version 2 : plongeon (demande et réponse).
 */
public final class ReseauAllonge {
   private static final String VERSION = "2";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "allonge"), () -> VERSION, VERSION::equals, VERSION::equals);

   /** Refus du serveur reçu chez le joueur : branché par la partie client (AllongeClient). */
   public static final Crochet<PaquetRefusAllonge> REFUS_CLIENT = new Crochet<>();
   /** Demande de plongeon reçue par le serveur : branchée par la partie serveur (PlongeonServeur). */
   public static final Crochet<ServerPlayer> PLONGEON_SERVEUR = new Crochet<>();
   /** Réponse du serveur à une demande de plongeon, reçue chez le joueur : branchée par la partie client (AllongeClient). */
   public static final Crochet<PaquetReponsePlongeon> REPONSE_PLONGEON_CLIENT = new Crochet<>();

   private ReseauAllonge() {
   }

   /** Enregistre les paquets (constructeur du @Mod porteur). */
   public static void init() {
      CANAL.registerMessage(0, PaquetRefusAllonge.class, PaquetRefusAllonge::encode, PaquetRefusAllonge::decode,
         PaquetRefusAllonge::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CANAL.registerMessage(1, PaquetDemandePlongeon.class, PaquetDemandePlongeon::encode, PaquetDemandePlongeon::decode,
         PaquetDemandePlongeon::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CANAL.registerMessage(2, PaquetReponsePlongeon.class, PaquetReponsePlongeon::encode, PaquetReponsePlongeon::decode,
         PaquetReponsePlongeon::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void envoyer(ServerPlayer joueur, Object paquet) {
      CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), paquet);
   }

   /** Joueur -> serveur (appelé par la partie client seulement). */
   public static void envoyerAuServeur(Object paquet) {
      CANAL.sendToServer(paquet);
   }
}
