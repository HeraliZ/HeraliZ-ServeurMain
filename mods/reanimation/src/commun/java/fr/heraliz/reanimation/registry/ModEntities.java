package fr.heraliz.reanimation.registry;

import fr.heraliz.reanimation.entity.BotEntity;
import fr.heraliz.reanimation.pont.UsineEntite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber(modid = "reanimation", bus = Bus.MOD)
public final class ModEntities {
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "reanimation");
   /** Usine du bot d'essai : version serveur ({@code BotEntityServeur}, IA, sac, sauvegarde) branchée par la partie serveur. */
   public static final UsineEntite<BotEntity> BOT_USINE = new UsineEntite<>(BotEntity::new);
   public static final RegistryObject<EntityType<BotEntity>> BOT = ENTITIES.register(
      "bot", () -> Builder.of(BOT_USINE, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("reanimation:bot")
   );

   private ModEntities() {
   }

   @SubscribeEvent
   public static void onAttributes(EntityAttributeCreationEvent event) {
      event.put((EntityType)BOT.get(), BotEntity.createAttributes().build());
   }
}
