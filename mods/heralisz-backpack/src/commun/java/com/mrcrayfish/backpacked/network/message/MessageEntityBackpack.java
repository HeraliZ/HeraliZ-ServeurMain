package com.mrcrayfish.backpacked.network.message;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import net.minecraft.network.FriendlyByteBuf;

public class MessageEntityBackpack extends PlayMessage<MessageEntityBackpack> {
   /** Reception cote serveur : branchee par la partie serveur (network.play.ServerPlayHandler). */
   public static volatile BiConsumer<MessageEntityBackpack, ServerPlayer> RECEPTION_SERVEUR = (message, player) -> {
   };
   private int entityId;

   public MessageEntityBackpack() {
   }

   public MessageEntityBackpack(int entityId) {
      this.entityId = entityId;
   }

   public void encode(MessageEntityBackpack message, FriendlyByteBuf buffer) {
      buffer.writeInt(message.entityId);
   }

   public MessageEntityBackpack decode(FriendlyByteBuf buffer) {
      return new MessageEntityBackpack(buffer.readInt());
   }

   public void handle(MessageEntityBackpack message, MessageContext context) {
      context.execute(() -> RECEPTION_SERVEUR.accept(message, context.getPlayer()));
      context.setHandled(true);
   }

   public int getEntityId() {
      return this.entityId;
   }
}
