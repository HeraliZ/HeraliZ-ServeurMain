package fr.heralisz.tactical.client;

import fr.heralisz.tactical.client.audio.SoundHandler;
import fr.heralisz.tactical.client.gui.overlay.InteractKeyTextOverlay;
import fr.heralisz.tactical.client.input.AttackKeys;
import fr.heralisz.tactical.client.overlay.UsingProgressOverlay;
import fr.heralisz.tactical.client.particle.SmokeCloudParticle;
import fr.heralisz.tactical.client.renderer.CoolDownDecorations;
import fr.heralisz.tactical.client.renderer.entity.ThrowableEntityRenderer;
import fr.heralisz.tactical.client.tooltip.ClientConsumableTooltip;
import fr.heralisz.tactical.client.tooltip.ClientMeleeTooltip;
import fr.heralisz.tactical.client.tooltip.ClientThrowableTooltip;
import fr.heralisz.tactical.entity.EffectCloudGrenadeEntity;
import fr.heralisz.tactical.entity.GrenadeEntity;
import fr.heralisz.tactical.entity.SmokeGrenadeEntity;
import fr.heralisz.tactical.entity.StickyGrenadeEntity;
import fr.heralisz.tactical.entity.StunGrenadeEntity;
import fr.heralisz.tactical.entity.sp.SpEffectCloudEntity;
import fr.heralisz.tactical.init.ModItems;
import fr.heralisz.tactical.init.ModParticleTypes;
import fr.heralisz.tactical.inventory.tooltip.ConsumableTooltip;
import fr.heralisz.tactical.inventory.tooltip.MeleeTooltip;
import fr.heralisz.tactical.inventory.tooltip.ThrowableTooltip;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD, modid = "heralisz_tactical")
public class ClientSetupHandler {
   @SubscribeEvent
   public static void onEntityRenderers(RegisterRenderers event) {
      event.registerEntityRenderer(GrenadeEntity.TYPE, ThrowableEntityRenderer::new);
      event.registerEntityRenderer(StickyGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
      event.registerEntityRenderer(SmokeGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
      event.registerEntityRenderer(StunGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
      event.registerEntityRenderer(EffectCloudGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
      event.registerEntityRenderer(SpEffectCloudEntity.TYPE, NoopRenderer::new);
   }

   /** Armes de mêlée doubles : l'arme gauche est dessinée dans la main gauche en 3e personne (HeralisZ). */
   @SubscribeEvent
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static void onAddLayers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
      for (String skin : event.getSkins()) {
         net.minecraft.client.renderer.entity.LivingEntityRenderer renderer = event.getSkin(skin);
         if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
            player.addLayer(new fr.heralisz.tactical.client.renderer.item.DualWieldLayer(player));
         }
      }
   }

   @SubscribeEvent
   public static void registerParticles(RegisterParticleProvidersEvent event) {
      event.registerSpriteSet((ParticleType)ModParticleTypes.SMOKE_CLOUD.get(), SmokeCloudParticle::provider);
   }

   @SubscribeEvent
   public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
      event.register((ItemLike)ModItems.CONSUMABLE.get(), new CoolDownDecorations());
      event.register((ItemLike)ModItems.THROWABLE.get(), new CoolDownDecorations());
   }

   @SubscribeEvent
   public static void onRegisterTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
      event.register(ConsumableTooltip.class, ClientConsumableTooltip::new);
      event.register(ThrowableTooltip.class, ClientThrowableTooltip::new);
      event.register(MeleeTooltip.class, ClientMeleeTooltip::new);
   }

   @SubscribeEvent
   public static void registerOverlay(RegisterGuiOverlaysEvent event) {
      event.registerAboveAll("lr_using_progress", new UsingProgressOverlay());
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      MinecraftForge.EVENT_BUS.register(SoundHandler.get());
   }

   @SubscribeEvent
   public static void onClientSetup(RegisterKeyMappingsEvent event) {
      event.register(AttackKeys.NORMAL_ATTACK);
      event.register(AttackKeys.SPECIAL_ATTACK);
   }

   @SubscribeEvent
   public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
      event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "lrt_interact_key_overlay", new InteractKeyTextOverlay());
   }
}
