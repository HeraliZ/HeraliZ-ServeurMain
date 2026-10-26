package com.mrcrayfish.backpacked.network.message;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import net.minecraft.network.FriendlyByteBuf;

public class MessageOpenBackpack extends PlayMessage<MessageOpenBackpack> {
   /** Reception cote serveur : branchee par la partie serveur (network.play.ServerPlayHandler). */
   public static volatile BiConsumer<MessageOpenBackpack, ServerPlayer> RECEPTION_SERVEUR = (message, player) -> {
   };
   public void encode(MessageOpenBackpack message, FriendlyByteBuf buffer) {
   }

   public MessageOpenBackpack decode(FriendlyByteBuf buffer) {
      return new MessageOpenBackpack();
   }

   public void handle(MessageOpenBackpack message, MessageContext context) {
      context.execute(() -> RECEPTION_SERVEUR.accept(message, context.getPlayer()));
      context.setHandled(true);
   }
}
