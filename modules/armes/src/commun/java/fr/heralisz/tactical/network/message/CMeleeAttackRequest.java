package fr.heralisz.tactical.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import fr.heralisz.tactical.HeraliszTactical;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import fr.heralisz.tactical.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent.Context;

public record CMeleeAttackRequest(MeleeAction action, int actionCount, int[] entityIds) {
   /**
    * HeraliZ (21/11, validation) : un paquet qui vise trop de cibles (seul un jeu modifié en envoie) n'est noté au journal
    * qu'une fois toutes les {@value #JOURNAL_MS} ms par joueur, avec le nombre de paquets semblables ignorés depuis la ligne
    * d'avant (avant : une ligne par paquet, le journal pouvait grossir sans fin). Fil du serveur seulement ; un joueur parti
    * est oublié tout seul (clé faible).
    */
   private static final long JOURNAL_MS = 30_000L;
   private static final java.util.Map<ServerPlayer, long[]> JOURNAL = new java.util.WeakHashMap<>();

   public CMeleeAttackRequest(MeleeAction action, int actionCount, List<Entity> entities) {
      this(action, actionCount, toList(entities));
   }

   private static int[] toList(List<Entity> entities) {
      return entities.stream().mapToInt(Entity::getId).limit(((Integer)ServerConfig.MELEE_MAX_TARGET_PER_PACKET.get()).intValue()).toArray();
   }

   public static void encode(CMeleeAttackRequest message, FriendlyByteBuf buf) {
      buf.writeEnum(message.action);
      buf.writeVarInt(message.actionCount);
      buf.writeVarIntArray(message.entityIds);
   }

   public static CMeleeAttackRequest decode(FriendlyByteBuf buf) {
      MeleeAction action = (MeleeAction)buf.readEnum(MeleeAction.class);
      int actionCount = buf.readVarInt();
      int[] ids = buf.readVarIntArray();
      return new CMeleeAttackRequest(action, actionCount, ids);
   }

   public static void handle(CMeleeAttackRequest message, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      if (context.getDirection().getReceptionSide().isServer()) {
         context.enqueueWork(
            () -> {
               ServerPlayer player = context.getSender();
               if (player != null) {
                  if (message.entityIds.length > (Integer)ServerConfig.MELEE_MAX_TARGET_PER_PACKET.get()) {
                     long maintenant = System.currentTimeMillis();
                     long[] note = JOURNAL.computeIfAbsent(player, p -> new long[2]);
                     if (maintenant - note[0] >= JOURNAL_MS) {
                        HeraliszTactical.LOGGER
                           .info("Player {} tried to attack too many entities at once: {}! Ignoring.{}", player.getName().getString(), message.entityIds.length,
                              note[1] > 0L ? " (" + note[1] + " similar packet(s) ignored since the previous line)" : "");
                        note[0] = maintenant;
                        note[1] = 0L;
                     } else {
                        note[1]++;
                     }
                  } else {
                     List<Entity> entities = new ArrayList<>();

                     for (int entityId : message.entityIds()) {
                        Entity entity = player.level().getEntity(entityId);
                        if (entity != null) {
                           entities.add(entity);
                        }
                     }

                     player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(cap -> cap.postAttack(message.action, message.actionCount, entities));
                  }
               }
            }
         );
      }

      context.setPacketHandled(true);
   }
}
