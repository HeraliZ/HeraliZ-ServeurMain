package com.mrcrayfish.backpacked.network.message;

import com.mrcrayfish.backpacked.pont.Crochet;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class MessageUnlockBackpack extends PlayMessage<MessageUnlockBackpack> {
   /** Reception cote client : branchee par la partie client (network.play.ClientPlayHandler). */
   public static final Crochet<MessageUnlockBackpack> RECEPTION_CLIENT = new Crochet<>();
   private ResourceLocation id;

   public MessageUnlockBackpack() {
   }

   public MessageUnlockBackpack(ResourceLocation id) {
      this.id = id;
   }

   public void encode(MessageUnlockBackpack message, FriendlyByteBuf buffer) {
      buffer.writeResourceLocation(message.id);
   }

   public MessageUnlockBackpack decode(FriendlyByteBuf buffer) {
      return new MessageUnlockBackpack(buffer.readResourceLocation());
   }

   public void handle(MessageUnlockBackpack message, MessageContext context) {
      context.execute(() -> RECEPTION_CLIENT.accept(message));
      context.setHandled(true);
   }

   public ResourceLocation cosmeticId() {
      return this.id;
   }
}
