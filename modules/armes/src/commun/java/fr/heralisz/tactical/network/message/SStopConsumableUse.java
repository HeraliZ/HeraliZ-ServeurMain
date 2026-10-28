package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * HeraliZ (28/10) : le serveur a refusé un soin que le jeu du joueur avait déjà commencé (sortie de l'objet pas encore
 * finie côté serveur, temps de recharge...). Sans ce paquet, le joueur restait « en train d'utiliser » chez lui : barre
 * de soin pleine, rien soigné ni consommé (les soins en bascule n'ont pas de fin côté joueur). Le joueur arrête alors
 * l'utilisation, la barre repart de zéro et un nouveau clic relance le soin.
 */
public record SStopConsumableUse() {
   /** Traitement côté client, branché par fr.heralisz.tactical.client.network.ReceptionClient. */
   public static final Crochet<SStopConsumableUse> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(SStopConsumableUse message, FriendlyByteBuf buf) {
   }

   public static SStopConsumableUse decode(FriendlyByteBuf buf) {
      return new SStopConsumableUse();
   }

   public static void handle(SStopConsumableUse message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(message));
      }

      context.setPacketHandled(true);
   }
}
