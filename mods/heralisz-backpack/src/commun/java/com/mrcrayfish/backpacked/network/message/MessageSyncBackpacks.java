package com.mrcrayfish.backpacked.network.message;

import com.mrcrayfish.backpacked.Constants;
import com.mrcrayfish.backpacked.common.backpack.Backpack;
import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.HandshakeMessage;
import com.mrcrayfish.framework.api.network.message.HandshakeMessage.Acknowledge;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import net.minecraft.network.FriendlyByteBuf;

public class MessageSyncBackpacks extends HandshakeMessage<MessageSyncBackpacks> {
   private List<Backpack> backpacks;

   public MessageSyncBackpacks() {
   }

   public MessageSyncBackpacks(List<Backpack> backpacks) {
      this.backpacks = backpacks;
   }

   public void encode(MessageSyncBackpacks message, FriendlyByteBuf buffer) {
      buffer.writeVarInt(message.backpacks.size());
      message.backpacks.forEach(backpack -> backpack.write(buffer));
   }

   public MessageSyncBackpacks decode(FriendlyByteBuf buffer) {
      List<Backpack> backpacks = new ArrayList<>();
      int size = buffer.readVarInt();

      for (int i = 0; i < size; i++) {
         backpacks.add(new Backpack(buffer));
      }

      return new MessageSyncBackpacks(backpacks);
   }

   public void handle(MessageSyncBackpacks message, MessageContext context) {
      CountDownLatch latch = new CountDownLatch(1);
      context.execute(() -> {
         BackpackManager.instance().updateClientBackpacks(message.backpacks);
         latch.countDown();
      });

      try {
         latch.await();
      } catch (InterruptedException e) {
         Constants.LOG.error("Failed to update backpacks", e);
      }

      context.setHandled(true);
      context.reply(new Acknowledge());
   }
}
