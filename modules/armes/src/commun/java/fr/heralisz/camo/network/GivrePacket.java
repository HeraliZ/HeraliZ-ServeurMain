package fr.heralisz.camo.network;

import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> serveur (22/11) : le joueur commence l'inspection de l'arme qu'il tient, qui porte le camo Givre Vivant. Le
 * serveur revérifie tout (arme en main, camo, cadence) et bascule le givre (fr.heralisz.camo.GivreVivant). Aucune donnée.
 */
public record GivrePacket() {
   /** Traitement côté serveur, branché par fr.heralisz.camo.serveur.PartieServeur. */
   public static final Crochet<CamoNetwork.Recu<GivrePacket>> RECEPTION_SERVEUR = new Crochet<>();

   public static void encode(GivrePacket p, FriendlyByteBuf buf) {
   }

   public static GivrePacket decode(FriendlyByteBuf buf) {
      return new GivrePacket();
   }

   public static void handle(GivrePacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> RECEPTION_SERVEUR.accept(new CamoNetwork.Recu<>(p, c.getSender())));
      c.setPacketHandled(true);
   }
}
