package fr.heraliz.reanimation.network;

import fr.heraliz.reanimation.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class UnlocksPacket {
   public final long owned;
   public final int selected;
   public final boolean open;

   public UnlocksPacket(long owned, int selected, boolean open) {
      this.owned = owned;
      this.selected = selected;
      this.open = open;
   }

   public UnlocksPacket(FriendlyByteBuf buf) {
      this(buf.readVarLong(), buf.readVarInt(), buf.readBoolean());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarLong(this.owned);
      buf.writeVarInt(this.selected);
      buf.writeBoolean(this.open);
   }

   /** Réception sur le client : branché par PartieClient (ClientUnlocks::receive). Neutre sans partie client. */
   public static final Crochet<UnlocksPacket> RECEPTION_CLIENT = new Crochet<>();

   public void handle(Supplier<Context> ctx) {
      RECEPTION_CLIENT.accept(this);
      ctx.get().setPacketHandled(true);
   }
}
