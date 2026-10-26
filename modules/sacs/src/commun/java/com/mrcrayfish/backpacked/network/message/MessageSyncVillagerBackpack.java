package com.mrcrayfish.backpacked.network.message;

import com.mrcrayfish.backpacked.pont.Crochet;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import net.minecraft.network.FriendlyByteBuf;

public class MessageSyncVillagerBackpack extends PlayMessage<MessageSyncVillagerBackpack> {
   /** Reception cote client : branchee par la partie client (network.play.ClientPlayHandler). */
   public static final Crochet<MessageSyncVillagerBackpack> RECEPTION_CLIENT = new Crochet<>();
   private int entityId;

   public MessageSyncVillagerBackpack() {
   }

   public MessageSyncVillagerBackpack(int entityId) {
      this.entityId = entityId;
   }

   public void encode(MessageSyncVillagerBackpack message, FriendlyByteBuf buffer) {
      buffer.writeInt(message.entityId);
   }

   public MessageSyncVillagerBackpack decode(FriendlyByteBuf buffer) {
      return new MessageSyncVillagerBackpack(buffer.readInt());
   }

   public void handle(MessageSyncVillagerBackpack message, MessageContext context) {
      context.execute(() -> RECEPTION_CLIENT.accept(message));
      context.setHandled(true);
   }

   public int entityId() {
      return this.entityId;
   }
}
