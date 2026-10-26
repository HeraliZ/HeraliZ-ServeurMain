package fr.heralisz.tactical.resource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import fr.heralisz.tactical.api.collision.ITargetFilter;
import fr.heralisz.tactical.item.consumable.ConsumableData;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.item.melee.AttributeData;
import fr.heralisz.tactical.item.melee.CombatData;
import fr.heralisz.tactical.item.throwable.area.EffectCloudThrowableData;
import fr.heralisz.tactical.network.DataType;
import fr.heralisz.tactical.resource.manager.ConsumableIndexManager;
import fr.heralisz.tactical.resource.manager.MeleeIndexManager;
import fr.heralisz.tactical.resource.manager.ThrowableIndexManager;
import fr.heralisz.tactical.resource.serializer.ParticleOptionsDeserializer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceLocation.Serializer;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Index du moteur (mêlée, soins, lancers) lus dans data/<ns>/index/ par le serveur, envoyés aux joueurs (SPackSyncMessage)
 * et lus par {@link #get()} des deux côtés. Fusion (heraliz_tactique) : le chargement des données et leur envoi aux joueurs
 * (écouteurs AddReloadListenerEvent, ServerStoppedEvent, OnDatapackSyncEvent) sont côté serveur : ChargementServeur.
 */
public class CommonAssetsManager implements ICommonResourceProvider {
   public static CommonAssetsManager INSTANCE;
   public static Gson GSON = new GsonBuilder()
      .registerTypeAdapter(ResourceLocation.class, new Serializer())
      .registerTypeAdapter(ConsumableData.RemoveEffectSelector.class, new ConsumableData.RemoveEffectSelector.Deserializer())
      .registerTypeAdapter(CombatData.class, new CombatData.Deserializer())
      .registerTypeAdapter(ITargetFilter.class, new ITargetFilter.Deserializer())
      .registerTypeAdapter(AttributeData.class, new AttributeData.Deserializer())
      .registerTypeAdapter(ParticleOptions.class, new ParticleOptionsDeserializer())
      .registerTypeAdapter(EffectCloudThrowableData.EffectData.class, new EffectCloudThrowableData.EffectDataDeSerializer())
      .create();
   public ThrowableIndexManager throwableIndexManager;
   public MeleeIndexManager meleeIndexManager;
   public ConsumableIndexManager consumableIndexManager;

   CommonAssetsManager() {
   }

   public static ICommonResourceProvider get() {
      return INSTANCE == null ? CommonNetworkCache.INSTANCE : INSTANCE;
   }

   void reloadAndRegister(Consumer<PreparableReloadListener> register) {
      this.consumableIndexManager = new ConsumableIndexManager(GSON);
      this.throwableIndexManager = new ThrowableIndexManager(GSON);
      this.meleeIndexManager = new MeleeIndexManager(GSON);
      register.accept(this.consumableIndexManager);
      register.accept(this.throwableIndexManager);
      register.accept(this.meleeIndexManager);
   }

   public Map<DataType, Map<ResourceLocation, String>> toNetwork() {
      Builder<DataType, Map<ResourceLocation, String>> builder = ImmutableMap.builder();
      builder.put(DataType.CONSUMABLE_INDEX, this.consumableIndexManager.getCache());
      builder.put(DataType.THROWABLE_INDEX, this.throwableIndexManager.getCache());
      builder.put(DataType.MELEE_INDEX, this.meleeIndexManager.getCache());
      return builder.build();
   }

   @Override
   public ConsumableIndex getConsumableIndex(ResourceLocation id) {
      return (ConsumableIndex)this.consumableIndexManager.getData(id);
   }

   @Override
   public Collection<ConsumableIndex> getConsumableIndexes() {
      return this.consumableIndexManager.getAllData().values();
   }

   @Override
   public ThrowableIndex<?, ?> getThrowableIndex(ResourceLocation id) {
      return (ThrowableIndex<?, ?>)this.throwableIndexManager.getData(id);
   }

   @Override
   public Collection<ThrowableIndex<?, ?>> getThrowableIndexes() {
      return this.throwableIndexManager.getAllData().values();
   }

   @Override
   public MeleeWeaponIndex<?> getMeleeIndex(ResourceLocation id) {
      return (MeleeWeaponIndex<?>)this.meleeIndexManager.getData(id);
   }

   @Override
   public Collection<MeleeWeaponIndex<?>> getMeleeIndexes() {
      return this.meleeIndexManager.getAllData().values();
   }
}
