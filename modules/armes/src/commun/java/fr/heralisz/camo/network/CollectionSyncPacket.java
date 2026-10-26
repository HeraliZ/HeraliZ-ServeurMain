package fr.heralisz.camo.network;

import fr.heralisz.camo.CollectionVue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Serveur -> client : la collection de camos à vie du joueur (ids possédés) et les délais restants après une mort (ms).
 * Envoyé à la connexion et à chaque changement ; lu par l'onglet Skin (CollectionVue).
 */
public record CollectionSyncPacket(List<String> ids, Map<String, Long> recharges) {
   private static final int MAX = 512;

   public static void encode(CollectionSyncPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.ids.size());
      for (String id : p.ids) buf.writeUtf(id, 64);
      buf.writeVarInt(p.recharges.size());
      p.recharges.forEach((id, ms) -> {
         buf.writeUtf(id, 64);
         buf.writeVarLong(ms);
      });
   }

   public static CollectionSyncPacket decode(FriendlyByteBuf buf) {
      int n = Math.min(buf.readVarInt(), MAX);
      List<String> ids = new ArrayList<>(n);
      for (int i = 0; i < n; i++) ids.add(buf.readUtf(64));
      int m = Math.min(buf.readVarInt(), MAX);
      Map<String, Long> recharges = new HashMap<>();
      for (int i = 0; i < m; i++) recharges.put(buf.readUtf(64), buf.readVarLong());
      return new CollectionSyncPacket(ids, recharges);
   }

   public static void handle(CollectionSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> CollectionVue.recevoir(p.ids, p.recharges));
      c.setPacketHandled(true);
   }
}
