package fr.heralisz.tactical.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import fr.heralisz.tactical.network.message.CCancelToggleConsumableUse;
import fr.heralisz.tactical.network.message.CMeleeAttackRequest;
import fr.heralisz.tactical.network.message.CPrepareMeleeAttack;
import fr.heralisz.tactical.network.message.SCustomCoolDownMessage;
import fr.heralisz.tactical.network.message.SCustomSound;
import fr.heralisz.tactical.network.message.SMeleeAnimationSync;
import fr.heralisz.tactical.network.message.SPackSyncMessage;
import fr.heralisz.tactical.network.message.SResetMeleeSyncMessage;
import fr.heralisz.tactical.network.message.SShakeScreenMessage;
import fr.heralisz.tactical.network.message.SShieldDisable;
import fr.heralisz.tactical.network.message.SShieldShake;
import fr.heralisz.tactical.network.message.SSplashParticle;
import fr.heralisz.tactical.network.message.SStopConsumableUse;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
   // 0.4.0 (28/10) : paquet SStopConsumableUse ajouté : jars joueurs et serveur de la même fabrication
   private static final String VERSION = "0.4.0";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation("heralisz_tactical", "network"), () -> VERSION, VERSION::equals, VERSION::equals
   );
   private static final AtomicInteger ID_COUNT = new AtomicInteger(1);

   /** Sens imposé à chaque paquet : Forge refuse un paquet reçu dans le mauvais sens AVANT de le lire. */
   public static void init() {
      CHANNEL.registerMessage(ID_COUNT.getAndIncrement(), SPackSyncMessage.class, SPackSyncMessage::encode, SPackSyncMessage::decode, SPackSyncMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(),
         SCustomCoolDownMessage.class,
         SCustomCoolDownMessage::encode,
         SCustomCoolDownMessage::decode,
         SCustomCoolDownMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(ID_COUNT.getAndIncrement(), SCustomSound.class, SCustomSound::encode, SCustomSound::decode, SCustomSound::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(), CMeleeAttackRequest.class, CMeleeAttackRequest::encode, CMeleeAttackRequest::decode, CMeleeAttackRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(), CPrepareMeleeAttack.class, CPrepareMeleeAttack::encode, CPrepareMeleeAttack::decode, CPrepareMeleeAttack::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CHANNEL.registerMessage(ID_COUNT.getAndIncrement(), SShieldShake.class, SShieldShake::encode, SShieldShake::decode, SShieldShake::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(ID_COUNT.getAndIncrement(), SShieldDisable.class, SShieldDisable::encode, SShieldDisable::decode, SShieldDisable::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(), SShakeScreenMessage.class, SShakeScreenMessage::encode, SShakeScreenMessage::decode, SShakeScreenMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(ID_COUNT.getAndIncrement(), SSplashParticle.class, SSplashParticle::encode, SSplashParticle::decode, SSplashParticle::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(), SMeleeAnimationSync.class, SMeleeAnimationSync::encode, SMeleeAnimationSync::decode, SMeleeAnimationSync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(),
         SResetMeleeSyncMessage.class,
         SResetMeleeSyncMessage::encode,
         SResetMeleeSyncMessage::decode,
         SResetMeleeSyncMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(),
         CCancelToggleConsumableUse.class,
         CCancelToggleConsumableUse::encode,
         CCancelToggleConsumableUse::new,
         CCancelToggleConsumableUse::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CHANNEL.registerMessage(
         ID_COUNT.getAndIncrement(),
         SStopConsumableUse.class,
         SStopConsumableUse::encode,
         SStopConsumableUse::decode,
         SStopConsumableUse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void sendToServer(Object message) {
      CHANNEL.sendToServer(message);
   }

   public static void sendToClientPlayer(Object message, Player player) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), message);
   }

   public static void sendToTrackingEntityAndSelf(Entity centerEntity, Object message) {
      CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> centerEntity), message);
   }

   public static void sendToAllPlayers(Object message) {
      CHANNEL.send(PacketDistributor.ALL.noArg(), message);
   }

   public static void sendToTrackingEntity(Object message, Entity centerEntity) {
      CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> centerEntity), message);
   }

   public static void sendToDimension(Object message, Entity centerEntity) {
      ResourceKey<Level> dimension = centerEntity.level().dimension();
      CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), message);
   }

   public static void sendToNearbyPlayers(Object message, Level level, Vec3 position, double radius) {
      CHANNEL.send(PacketDistributor.NEAR.with(TargetPoint.p(position.x, position.y, position.z, radius, level.dimension())), message);
   }
}
