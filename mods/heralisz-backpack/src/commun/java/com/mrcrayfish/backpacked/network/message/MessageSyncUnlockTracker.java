package com.mrcrayfish.backpacked.network.message;

import com.mrcrayfish.backpacked.pont.Crochet;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class MessageSyncUnlockTracker extends PlayMessage<MessageSyncUnlockTracker> {
   /** Reception cote client : branchee par la partie client (network.play.ClientPlayHandler). */
   public static final Crochet<MessageSyncUnlockTracker> RECEPTION_CLIENT = new Crochet<>();
   private Set<ResourceLocation> unlockedBackpacks;

   public MessageSyncUnlockTracker() {
   }

   public MessageSyncUnlockTracker(Set<ResourceLocation> unlockedBackpacks) {
      this.unlockedBackpacks = unlockedBackpacks;
   }

   public void encode(MessageSyncUnlockTracker message, FriendlyByteBuf buffer) {
      buffer.writeInt(message.unlockedBackpacks.size());
      message.unlockedBackpacks.forEach(buffer::writeResourceLocation);
   }

   public MessageSyncUnlockTracker decode(FriendlyByteBuf buffer) {
      int size = buffer.readInt();
      Set<ResourceLocation> unlockedBackpacks = new HashSet<>();

      for (int i = 0; i < size; i++) {
         unlockedBackpacks.add(buffer.readResourceLocation());
      }

      return new MessageSyncUnlockTracker(unlockedBackpacks);
   }

   public void handle(MessageSyncUnlockTracker message, MessageContext context) {
      context.execute(() -> RECEPTION_CLIENT.accept(message));
      context.setHandled(true);
   }

   public Set<ResourceLocation> unlockedBackpacks() {
      return this.unlockedBackpacks;
   }
}
