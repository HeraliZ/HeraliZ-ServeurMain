package fr.heralisz.tactical.client.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.function.Consumer;
import fr.heralisz.tactical.client.resource.display.ConsumableDisplayInstance;
import fr.heralisz.tactical.client.resource.display.MeleeDisplayInstance;
import fr.heralisz.tactical.client.resource.display.ThrowableDisplayInstance;
import fr.heralisz.tactical.client.resource.manager.ConsumableDisplayManager;
import fr.heralisz.tactical.client.resource.manager.MeleeDisplayManager;
import fr.heralisz.tactical.client.resource.manager.ThrowableDisplayManager;
import fr.heralisz.tactical.init.ModItems;
import fr.heralisz.tactical.resource.serializer.Vector3fDeserializer;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.ItemTransforms.Deserializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceLocation.Serializer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = Bus.MOD, value = Dist.CLIENT, modid = "heralisz_tactical")
public enum TacticalClientAssetsManager {
   INSTANCE;

   public static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(ResourceLocation.class, new Serializer())
      .registerTypeAdapter(ItemTransforms.class, new Deserializer())
      .registerTypeAdapter(ItemTransform.class, new net.minecraft.client.renderer.block.model.ItemTransform.Deserializer())
      .registerTypeAdapter(Vector3f.class, new Vector3fDeserializer())
      .create();
   private ThrowableDisplayManager throwableDisplay;
   private MeleeDisplayManager meleeDisplay;
   private ConsumableDisplayManager consumableDisplay;

   public void reloadAndRegister(Consumer<PreparableReloadListener> register) {
      this.consumableDisplay = new ConsumableDisplayManager(GSON);
      this.throwableDisplay = new ThrowableDisplayManager(GSON);
      this.meleeDisplay = new MeleeDisplayManager(GSON);
      register.accept(this.consumableDisplay);
      register.accept(this.throwableDisplay);
      register.accept(this.meleeDisplay);
   }

   public ConsumableDisplayInstance getConsumableDisplay(ResourceLocation id) {
      return (ConsumableDisplayInstance)this.consumableDisplay.getData(id);
   }

   public ThrowableDisplayInstance getThrowableDisplay(ResourceLocation id) {
      return (ThrowableDisplayInstance)this.throwableDisplay.getData(id);
   }

   public MeleeDisplayInstance getMeleeDisplay(ResourceLocation id) {
      return (MeleeDisplayInstance)this.meleeDisplay.getData(id);
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onClientResourceReload(RegisterClientReloadListenersEvent event) {
      INSTANCE.reloadAndRegister(event::registerReloadListener);
      event.registerReloadListener(IClientItemExtensions.of((Item)ModItems.FLASH_SHIELD.get()).getCustomRenderer());
   }
}
