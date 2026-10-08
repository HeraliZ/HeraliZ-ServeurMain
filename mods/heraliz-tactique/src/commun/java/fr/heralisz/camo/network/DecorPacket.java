package fr.heralisz.camo.network;

import fr.heraliz.tactique.pont.Crochet;
import fr.heralisz.camo.decor.DecorData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Client -> serveur : personnaliser l'arme tenue (stickers, pendentif, StatTrack). Tout est revérifié ici : objet présent dans
 * l'emplacement, limites, valeurs de pose bornées.
 */
public record DecorPacket(int action, int slot, int index, @Nullable DecorData.Placement at) {
   /** Fusion (heraliz_tactique) : traitement côté serveur (code d'origine), branché par fr.heralisz.camo.serveur.PartieServeur. */
   public static final Crochet<CamoNetwork.Recu<DecorPacket>> RECEPTION_SERVEUR = new Crochet<>();

   public static final int ADD_STICKER = 0, MOVE_STICKER = 1, SCRAPE_STICKER = 2, SET_CHARM = 3, MOVE_CHARM = 4, REMOVE_CHARM = 5,
      SET_STATTRACK = 6, MOVE_STATTRACK = 7;

   public static void encode(DecorPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.action);
      buf.writeVarInt(p.slot);
      buf.writeVarInt(p.index);
      buf.writeBoolean(p.at != null);
      if (p.at != null) {
         DecorData.Placement a = p.at;
         buf.writeUtf(a.bone(), 64);
         for (float f : new float[] {a.px(), a.py(), a.pz(), a.nx(), a.ny(), a.nz(), a.ux(), a.uy(), a.uz(), a.scale(), a.rot()}) buf.writeFloat(f);
      }
   }

   public static DecorPacket decode(FriendlyByteBuf buf) {
      int action = buf.readVarInt(), slot = buf.readVarInt(), index = buf.readVarInt();
      DecorData.Placement at = null;
      if (buf.readBoolean()) {
         String bone = buf.readUtf(64);
         float[] v = new float[11];
         for (int i = 0; i < 11; i++) v[i] = buf.readFloat();
         at = new DecorData.Placement(bone, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10]);
      }
      return new DecorPacket(action, slot, index, at);
   }

   public static void handle(DecorPacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> RECEPTION_SERVEUR.accept(new CamoNetwork.Recu<>(p, c.getSender())));
      c.setPacketHandled(true);
   }
}
