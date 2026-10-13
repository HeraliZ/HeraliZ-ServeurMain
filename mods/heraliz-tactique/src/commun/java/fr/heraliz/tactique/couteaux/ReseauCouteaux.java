package fr.heraliz.tactique.couteaux;

import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.pont.Crochet;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal réseau des coups de couteau TaCZ (heraliz_tactique:couteaux, voir {@link CoupsCouteau}). Le joueur et le serveur
 * doivent avoir la même version : un joueur sans ce canal (ancien jar) est refusé à la connexion par Forge, avec un
 * message clair.
 */
public final class ReseauCouteaux {
   private static final String VERSION = "1";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "couteaux"), () -> VERSION, VERSION::equals, VERSION::equals);

   /** Demande de coup reçue par le serveur : branchée par la partie serveur (CouteauxServeur). */
   public static final Crochet<PaquetCoupCouteau.Demande> COUP_SERVEUR = new Crochet<>();

   private ReseauCouteaux() {
   }

   /** Enregistre les paquets (constructeur du @Mod porteur). */
   public static void init() {
      CANAL.registerMessage(0, PaquetCoupCouteau.class, PaquetCoupCouteau::encode, PaquetCoupCouteau::decode,
         PaquetCoupCouteau::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   /** Joueur -> serveur (appelé par la partie client seulement). */
   public static void envoyerAuServeur(Object paquet) {
      CANAL.sendToServer(paquet);
   }
}
