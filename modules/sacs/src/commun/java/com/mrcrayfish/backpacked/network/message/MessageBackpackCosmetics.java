package com.mrcrayfish.backpacked.network.message;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import com.mrcrayfish.framework.api.network.MessageContext;
import com.mrcrayfish.framework.api.network.message.PlayMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class MessageBackpackCosmetics extends PlayMessage<MessageBackpackCosmetics> {
   /** Reception cote serveur : branchee par la partie serveur (network.play.ServerPlayHandler). */
   public static volatile BiConsumer<MessageBackpackCosmetics, ServerPlayer> RECEPTION_SERVEUR = (message, player) -> {
   };
   private ResourceLocation id;
   private boolean showGlint;
   private boolean showWithElytra;
   private boolean showEffects;

   public MessageBackpackCosmetics() {
   }

   public MessageBackpackCosmetics(ResourceLocation id, boolean showGlint, boolean showWithElytra, boolean showEffects) {
      this.id = id;
      this.showGlint = showGlint;
      this.showWithElytra = showWithElytra;
      this.showEffects = showEffects;
   }

   public void encode(MessageBackpackCosmetics message, FriendlyByteBuf buffer) {
      buffer.writeResourceLocation(message.id);
      buffer.writeBoolean(message.showGlint);
      buffer.writeBoolean(message.showWithElytra);
      buffer.writeBoolean(message.showEffects);
   }

   public MessageBackpackCosmetics decode(FriendlyByteBuf buffer) {
      return new MessageBackpackCosmetics(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
   }

   public void handle(MessageBackpackCosmetics message, MessageContext context) {
      context.execute(() -> RECEPTION_SERVEUR.accept(message, context.getPlayer()));
      context.setHandled(true);
   }

   public ResourceLocation getBackpackId() {
      return this.id;
   }

   public boolean isShowGlint() {
      return this.showGlint;
   }

   public boolean isShowWithElytra() {
      return this.showWithElytra;
   }

   public boolean isShowEffects() {
      return this.showEffects;
   }
}
