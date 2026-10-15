package fr.heralisz.bunker.salle;

import fr.heralisz.bunker.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serveur -> staff : boîtes à dessiner (sélection de la baguette, coins, salles proches). « voir » : réponse à
 * /salle voir, montrée 30 s même sans la baguette en main ; sinon : montrée tant que le staff tient la baguette.
 */
public record ContoursPacket(boolean voir, List<Boite> boites) {
   /** Genres de boîte : sélection complète, coin 1 seul, coin 2 seul, puis une salle verte, bleue, rouge. */
   public static final byte SELECTION = 0, COIN1 = 1, COIN2 = 2, SALLE_VERTE = 3, SALLE_BLEUE = 4, SALLE_ROUGE = 5;
   public static final int MAX = 64;
   /** Réception côté client (dessin des contours) : branchée par la partie client. */
   public static final Crochet<ContoursPacket> RECEPTION_CLIENT = new Crochet<>();

   public record Boite(BlockPos min, BlockPos max, byte genre) {}

   public static void encode(ContoursPacket p, FriendlyByteBuf buf) {
      buf.writeBoolean(p.voir);
      int n = Math.min(MAX, p.boites.size());
      buf.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         Boite b = p.boites.get(i);
         buf.writeBlockPos(b.min());
         buf.writeBlockPos(b.max());
         buf.writeByte(b.genre());
      }
   }

   public static ContoursPacket decode(FriendlyByteBuf buf) {
      boolean voir = buf.readBoolean();
      int n = Math.min(MAX, buf.readVarInt());
      List<Boite> l = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         l.add(new Boite(buf.readBlockPos(), buf.readBlockPos(), buf.readByte()));
      }
      return new ContoursPacket(voir, l);
   }

   public static void handle(ContoursPacket p, Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(p));
      ctx.get().setPacketHandled(true);
   }
}
