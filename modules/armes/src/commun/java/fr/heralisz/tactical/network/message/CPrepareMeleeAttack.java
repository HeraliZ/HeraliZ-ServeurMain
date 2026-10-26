package fr.heralisz.tactical.network.message;

import java.util.function.Supplier;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import fr.heralisz.tactical.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;

public record CPrepareMeleeAttack(MeleeAction action, Vec3 origin, Vec3 direction) {
   public static void encode(CPrepareMeleeAttack message, FriendlyByteBuf buf) {
      buf.writeEnum(message.action);
      buf.writeDouble(message.origin.x);
      buf.writeDouble(message.origin.y);
      buf.writeDouble(message.origin.z);
      buf.writeDouble(message.direction.x);
      buf.writeDouble(message.direction.y);
      buf.writeDouble(message.direction.z);
   }

   public static CPrepareMeleeAttack decode(FriendlyByteBuf buf) {
      return new CPrepareMeleeAttack(
         (MeleeAction)buf.readEnum(MeleeAction.class),
         new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
         new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
      );
   }

   public static void handle(CPrepareMeleeAttack message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isServer()) {
         context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(cap -> {
                  if (cap.preAttack(message.action, message.origin, message.direction) && player.getMainHandItem().getItem() instanceof IMeleeWeapon weapon) {
                     ResourceLocation animationId = weapon.getId(player.getMainHandItem());
                     int actionCount = cap.getActionCount(message.action);
                     NetworkHandler.sendToTrackingEntityAndSelf(player, new SMeleeAnimationSync(player.getId(), message.action, actionCount, animationId));
                  }
               });
            }
         });
      }

      context.setPacketHandled(true);
   }
}
