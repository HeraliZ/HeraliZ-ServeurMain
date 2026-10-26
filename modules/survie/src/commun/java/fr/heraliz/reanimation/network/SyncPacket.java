package fr.heraliz.reanimation.network;

import fr.heraliz.reanimation.pont.Crochet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class SyncPacket {
   public static final int EVENT_DOWNED = 1;
   public static final int EVENT_REVIVED = 2;
   public static final int EVENT_INTERRUPTED = 3;
   public static final int EVENT_EXECUTION = 4;
   public static final int EVENT_STRIKE = 5;
   public final int entityId;
   public final int flags;
   public final int partnerId;
   public final int bleed;
   public final int bleedMax;
   public final int revive;
   public final int reviveMax;
   public final int event;
   public final int style;
   public double anchorX;
   public double anchorY;
   public double anchorZ;
   public float anchorYaw;
   public int anchorSide = 1;

   public SyncPacket(int entityId, int flags, int partnerId, int bleed, int bleedMax, int revive, int reviveMax, int event, int style) {
      this.entityId = entityId;
      this.flags = flags;
      this.partnerId = partnerId;
      this.bleed = bleed;
      this.bleedMax = bleedMax;
      this.revive = revive;
      this.reviveMax = reviveMax;
      this.event = event;
      this.style = style;
   }

   public SyncPacket(FriendlyByteBuf buf) {
      this(
         buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte()
      );
      this.anchor(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readByte());
   }

   public SyncPacket anchor(double x, double y, double z, float yaw, int side) {
      this.anchorX = x;
      this.anchorY = y;
      this.anchorZ = z;
      this.anchorYaw = yaw;
      this.anchorSide = side < 0 ? -1 : 1;
      return this;
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.entityId);
      buf.writeVarInt(this.flags);
      buf.writeVarInt(this.partnerId);
      buf.writeVarInt(this.bleed);
      buf.writeVarInt(this.bleedMax);
      buf.writeVarInt(this.revive);
      buf.writeVarInt(this.reviveMax);
      buf.writeByte(this.event);
      buf.writeByte(this.style);
      buf.writeDouble(this.anchorX);
      buf.writeDouble(this.anchorY);
      buf.writeDouble(this.anchorZ);
      buf.writeFloat(this.anchorYaw);
      buf.writeByte(this.anchorSide);
   }

   /** Réception sur le client : branché par PartieClient (ClientDowned::receive). Neutre sans partie client. */
   public static final Crochet<SyncPacket> RECEPTION_CLIENT = new Crochet<>();

   public void handle(Supplier<Context> ctx) {
      RECEPTION_CLIENT.accept(this);
      ctx.get().setPacketHandled(true);
   }
}
