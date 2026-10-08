package fr.heralisz.tactical.network.message;

import java.util.function.Supplier;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.item.IConsumable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public class CCancelToggleConsumableUse {
   public CCancelToggleConsumableUse() {
   }

   public CCancelToggleConsumableUse(FriendlyByteBuf buf) {
   }

   public void encode(FriendlyByteBuf buf) {
   }

   public void handle(Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               ItemStack useItem = player.getUseItem();
               if (useItem.getItem() instanceof IConsumable
                  && HeraliszTacticalAPI.getConsumableIndex(useItem).map(index -> index.getData().isToggleUse()).orElse(false)) {
                  player.stopUsingItem();
               }
            }
         }
      );
      context.setPacketHandled(true);
   }
}
