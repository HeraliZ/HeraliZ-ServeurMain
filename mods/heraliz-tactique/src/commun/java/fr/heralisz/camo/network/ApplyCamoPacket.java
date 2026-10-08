package fr.heralisz.camo.network;

import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client -> serveur : appliquer le camo de l'emplacement {@code camoSlot} sur l'arme tenue en main. Tout est revérifié côté serveur. */
public record ApplyCamoPacket(int camoSlot) {
   /** Fusion (heraliz_tactique) : traitement côté serveur (code d'origine), branché par fr.heralisz.camo.serveur.PartieServeur. */
   public static final Crochet<CamoNetwork.Recu<ApplyCamoPacket>> RECEPTION_SERVEUR = new Crochet<>();

   public static void encode(ApplyCamoPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.camoSlot);
   }

   public static ApplyCamoPacket decode(FriendlyByteBuf buf) {
      return new ApplyCamoPacket(buf.readVarInt());
   }

   public static void handle(ApplyCamoPacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> RECEPTION_SERVEUR.accept(new CamoNetwork.Recu<>(p, c.getSender())));
      c.setPacketHandled(true);
   }
}
