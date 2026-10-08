package com.mrcrayfish.backpacked.network.message;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import net.minecraft.network.FriendlyByteBuf;

public class MessageRequestCustomisation extends PlayMessage<MessageRequestCustomisation> {
   /** Reception cote serveur : branchee par la partie serveur (network.play.ServerPlayHandler). */
   public static volatile BiConsumer<MessageRequestCustomisation, ServerPlayer> RECEPTION_SERVEUR = (message, player) -> {
   };
   public void encode(MessageRequestCustomisation message, FriendlyByteBuf buffer) {
   }

   public MessageRequestCustomisation decode(FriendlyByteBuf buffer) {
      return new MessageRequestCustomisation();
   }

   public void handle(MessageRequestCustomisation message, MessageContext context) {
      context.execute(() -> RECEPTION_SERVEUR.accept(message, context.getPlayer()));
      context.setHandled(true);
   }
}
