package fr.heralisz.tactical.client.network;

import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI;
import fr.heralisz.tactical.api.event.MeleePreAttackEvent;
import fr.heralisz.tactical.capability.CombatProperties;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import fr.heralisz.tactical.client.ClientEventsHandler;
import fr.heralisz.tactical.client.audio.ICustomSoundSupplier;
import fr.heralisz.tactical.client.resource.display.MeleeDisplayInstance;
import fr.heralisz.tactical.client.resource.display.ThrowableDisplayInstance;
import fr.heralisz.tactical.item.FlashShieldItem;
import fr.heralisz.tactical.network.message.SCustomCoolDownMessage;
import fr.heralisz.tactical.network.message.SCustomSound;
import fr.heralisz.tactical.network.message.SMeleeAnimationSync;
import fr.heralisz.tactical.network.message.SResetMeleeSyncMessage;
import fr.heralisz.tactical.network.message.SShakeScreenMessage;
import fr.heralisz.tactical.network.message.SShieldDisable;
import fr.heralisz.tactical.network.message.SShieldShake;
import fr.heralisz.tactical.network.message.SSplashParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;

/**
 * Fusion (heraliz_tactique) : traitement côté client des paquets serveur -> client du moteur. Chaque méthode est le code
 * d'origine de la méthode @OnlyIn(CLIENT) du paquet, sans changement ; les paquets (code commun) l'appellent par leur
 * crochet {@code RECEPTION_CLIENT}, branché par {@link fr.heralisz.tactical.client.PartieClient}.
 */
public final class ReceptionClient {
   private ReceptionClient() {
   }

   /** Branche les crochets des paquets (appelé pendant le constructeur du @Mod). */
   public static void brancher() {
      SCustomCoolDownMessage.RECEPTION_CLIENT.brancher(ReceptionClient::coolDown);
      SCustomSound.RECEPTION_CLIENT.brancher(ReceptionClient::son);
      SShieldDisable.RECEPTION_CLIENT.brancher(ReceptionClient::bouclierDesactive);
      SShieldShake.RECEPTION_CLIENT.brancher(ReceptionClient::bouclierSecoue);
      SSplashParticle.RECEPTION_CLIENT.brancher(ReceptionClient::eclaboussure);
      SResetMeleeSyncMessage.RECEPTION_CLIENT.brancher(ReceptionClient::remiseAZeroMelee);
      SMeleeAnimationSync.RECEPTION_CLIENT.brancher(ReceptionClient::animationMelee);
      SShakeScreenMessage.RECEPTION_CLIENT.brancher(m -> ClientEventsHandler.handleShakeClient(m.time(), m.radius(), m.amplitude(), m.position()));
   }

   // SCustomCoolDownMessage.handle (client)
   static void coolDown(SCustomCoolDownMessage message) {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         player.getCapability(CustomItemCoolDownsProvider.CAPABILITY).ifPresent(coolDownCapability -> {
            if (message.duration() == 0) {
               coolDownCapability.removeCooldown(message.id());
            } else {
               coolDownCapability.addCooldown(message.id(), message.duration());
            }
         });
      }
   }

   // SCustomSound.handle (client)
   static void son(SCustomSound message) {
      Player player = Minecraft.getInstance().player;
      ClientLevel level = Minecraft.getInstance().level;
      if (player != null && level != null) {
         ICustomSoundSupplier supplier = switch (message.type()) {
            case MELEE -> (MeleeDisplayInstance)HeraliszTacticalClientAPI.getMeleeDisplay(message.id()).orElse(null);
            case THROWABLE -> (ThrowableDisplayInstance)HeraliszTacticalClientAPI.getThrowableDisplay(message.id()).orElse(null);
            default -> null;
         };
         if (supplier != null) {
            ResourceLocation soundLocation = supplier.getSound(message.key());
            if (soundLocation != null) {
               level.playLocalSound(
                  message.pos().x,
                  message.pos().y,
                  message.pos().z,
                  SoundEvent.createVariableRangeEvent(soundLocation),
                  SoundSource.PLAYERS,
                  message.volume(),
                  message.pitch(),
                  false
               );
            }
         }
      }
   }

   // SShieldDisable.handle (client)
   static void bouclierDesactive(SShieldDisable message) {
      Player player = Minecraft.getInstance().player;
      ClientLevel level = Minecraft.getInstance().level;
      if (player != null && level != null) {
         if (player.getMainHandItem().getItem() instanceof FlashShieldItem
            && IClientItemExtensions.of(player.getMainHandItem()).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            renderer.triggerAnimation(player.getMainHandItem(), "hard_shake");
         }
      }
   }

   // SShieldShake.handle (client)
   static void bouclierSecoue(SShieldShake message) {
      Player player = Minecraft.getInstance().player;
      ClientLevel level = Minecraft.getInstance().level;
      if (player != null && level != null) {
         if (player.getMainHandItem().getItem() instanceof FlashShieldItem
            && IClientItemExtensions.of(player.getMainHandItem()).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            renderer.triggerAnimation(player.getMainHandItem(), "normal_shake");
         }
      }
   }

   // SSplashParticle.handle (client)
   static void eclaboussure(SSplashParticle message) {
      Player player = Minecraft.getInstance().player;
      ClientLevel level = Minecraft.getInstance().level;
      ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
      if (player != null && level != null) {
         int color = message.color();
         RandomSource randomsource = level.random;
         float r = (color >> 16 & 0xFF) / 255.0F;
         float g = (color >> 8 & 0xFF) / 255.0F;
         float b = (color & 0xFF) / 255.0F;
         Vec3 vec3 = Vec3.atBottomCenterOf(message.blockPos());
         ParticleOptions particleoptions = ParticleTypes.EFFECT;

         for (int k2 = 0; k2 < 100; k2++) {
            double d13 = randomsource.nextDouble() * 4.0;
            double d19 = randomsource.nextDouble() * Math.PI * 2.0;
            double d25 = Math.cos(d19) * d13;
            double d30 = 0.01 + randomsource.nextDouble() * 0.5;
            double d31 = Math.sin(d19) * d13;
            Particle particle1 = particleEngine.createParticle(particleoptions, vec3.x + d25 * 0.1, vec3.y + 0.3, vec3.z + d31 * 0.1, d25, d30, d31);
            if (particle1 != null) {
               float f2 = 0.75F + randomsource.nextFloat() * 0.25F;
               particle1.setColor(r * f2, g * f2, b * f2);
               particle1.setPower((float)d13);
            }
         }
      }
   }

   // SResetMeleeSyncMessage.handleClient
   static void remiseAZeroMelee(SResetMeleeSyncMessage message) {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(CombatProperties::resetMeleeSync);
      }
   }

   // SMeleeAnimationSync.onClientReceive
   static void animationMelee(SMeleeAnimationSync msg) {
      MinecraftForge.EVENT_BUS.post(new MeleePreAttackEvent(msg.playerId(), msg.state(), msg.actionCount(), msg.animationId()));
   }
}
