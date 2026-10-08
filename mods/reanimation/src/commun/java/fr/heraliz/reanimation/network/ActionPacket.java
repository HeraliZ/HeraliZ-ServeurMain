package fr.heraliz.reanimation.network;

import fr.heraliz.reanimation.pont.Crochet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class ActionPacket {
   public static final int GIVE_UP = 0;
   public static final int CANCEL = 1;
   public static final int EXECUTE = 2;
   public static final int SELECT = 3;
   public static final int UNLOCKS = 4;
   public final int action;
   public final int target;

   public ActionPacket(int action) {
      this(action, -1);
   }

   public ActionPacket(int action, int target) {
      this.action = action;
      this.target = target;
   }

   public ActionPacket(FriendlyByteBuf buf) {
      this.action = buf.readByte();
      this.target = buf.readVarInt();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.action);
      buf.writeVarInt(this.target);
   }

   /** Réception sur le serveur : branché par PartieServeur (ActionPacketServeur::traiter). Neutre sans partie serveur. */
   public static final Crochet<ActionPacket.Recu> RECEPTION_SERVEUR = new Crochet<>();

   /** Paquet reçu par le serveur, avec son expéditeur (null si absent). */
   public record Recu(@Nullable ServerPlayer player, ActionPacket paquet) {
   }

   public void handle(Supplier<Context> ctx) {
      RECEPTION_SERVEUR.accept(new ActionPacket.Recu(ctx.get().getSender(), this));
      ctx.get().setPacketHandled(true);
   }
}
