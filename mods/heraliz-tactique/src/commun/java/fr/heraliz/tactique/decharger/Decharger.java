package fr.heraliz.tactique.decharger;

import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.pont.Crochet;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Décharger une arme TaCZ (23/10, demande du propriétaire) : touche « Décharger l'arme » (N par défaut) en jeu (arme en
 * main), dans l'inventaire (arme sous la souris) ou dans l'Atelier d'arme (arme en main, bouton « Décharger ») : les
 * balles du chargeur et celle de la chambre reviennent dans l'inventaire, le chargeur reste sur l'arme, vide. Décidé par
 * le serveur (DechargerServeur). Canal {@code heraliz_tactique:decharger}, version 1.
 */
public final class Decharger {
   private static final String VERSION = "1";
   public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeralizTactique.MODID, "decharger"), () -> VERSION, VERSION::equals, VERSION::equals);

   /** Demande reçue par le serveur : branchée par la partie serveur (DechargerServeur). */
   public static final Crochet<Demande> DEMANDE_SERVEUR = new Crochet<>();

   private Decharger() {
   }

   public static void init() {
      CANAL.registerMessage(0, Paquet.class, Paquet::encode, Paquet::decode, Paquet::handle,
         Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   /** Joueur -> serveur (partie client seulement). */
   public static void demander(int caseInventaire) {
      CANAL.sendToServer(new Paquet(caseInventaire));
   }

   /** Joueur -> serveur : décharger l'arme de cette case de son inventaire (0 à 35, 40 = main gauche). */
   public record Paquet(int caseInventaire) {
      static void encode(Paquet p, FriendlyByteBuf buf) {
         buf.writeVarInt(p.caseInventaire);
      }

      static Paquet decode(FriendlyByteBuf buf) {
         return new Paquet(buf.readVarInt());
      }

      static void handle(Paquet p, Supplier<NetworkEvent.Context> contexte) {
         NetworkEvent.Context c = contexte.get();
         if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
               ServerPlayer joueur = c.getSender();
               if (joueur != null) DEMANDE_SERVEUR.accept(new Demande(joueur, p.caseInventaire));
            });
         }
         c.setPacketHandled(true);
      }
   }

   public record Demande(ServerPlayer joueur, int caseInventaire) {
   }
}
