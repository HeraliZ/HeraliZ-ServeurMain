package com.mrcrayfish.backpacked.network.message;

import com.mrcrayfish.backpacked.pont.Crochet;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MessageOpenCustomisation extends PlayMessage<MessageOpenCustomisation> {
   /** Reception cote client : branchee par la partie client (network.play.ClientPlayHandler). */
   public static final Crochet<MessageOpenCustomisation> RECEPTION_CLIENT = new Crochet<>();
   private Map<ResourceLocation, Component> map;

   public MessageOpenCustomisation() {
   }

   public MessageOpenCustomisation(Map<ResourceLocation, Component> map) {
      this.map = map;
   }

   public void encode(MessageOpenCustomisation message, FriendlyByteBuf buffer) {
      buffer.writeInt(message.map.size());
      message.map.forEach((location, formattedProgress) -> {
         buffer.writeResourceLocation(location);
         buffer.writeComponent(formattedProgress);
      });
   }

   public MessageOpenCustomisation decode(FriendlyByteBuf buffer) {
      Map<ResourceLocation, Component> map = new HashMap<>();
      int size = buffer.readInt();

      for (int i = 0; i < size; i++) {
         ResourceLocation id = buffer.readResourceLocation();
         Component formattedProgress = buffer.readComponent();
         map.put(id, formattedProgress);
      }

      return new MessageOpenCustomisation(map);
   }

   public void handle(MessageOpenCustomisation message, MessageContext context) {
      context.execute(() -> RECEPTION_CLIENT.accept(message));
      context.setHandled(true);
   }

   public Map<ResourceLocation, Component> progressMap() {
      return this.map;
   }
}
