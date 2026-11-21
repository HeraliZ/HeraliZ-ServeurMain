package fr.heralisz.camo.network;

import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueur (21/11, docs/SKINS_PROTEGES.md, étape 1) : « refais tous les programmes des skins depuis la copie
 * reçue du serveur » (essai du staff, /camos dessins). Traité sur le fil du jeu, qui est celui du rendu.
 */
public record DessinsRefairePacket() {
   /** Traitement chez le joueur (fil du jeu), branché par fr.heralisz.camo.client.PartieClient. */
   public static final Crochet<DessinsRefairePacket> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(DessinsRefairePacket p, FriendlyByteBuf buf) {
   }

   public static DessinsRefairePacket decode(FriendlyByteBuf buf) {
      return new DessinsRefairePacket();
   }

   public static void handle(DessinsRefairePacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> RECEPTION_CLIENT.accept(p));
      c.setPacketHandled(true);
   }
}
