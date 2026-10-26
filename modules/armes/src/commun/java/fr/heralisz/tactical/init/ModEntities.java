package fr.heralisz.tactical.init;

import fr.heraliz.tactique.pont.UsineEntite;
import fr.heralisz.tactical.entity.EffectCloudGrenadeEntity;
import fr.heralisz.tactical.entity.GrenadeEntity;
import fr.heralisz.tactical.entity.SmokeGrenadeEntity;
import fr.heralisz.tactical.entity.StickyGrenadeEntity;
import fr.heralisz.tactical.entity.StunGrenadeEntity;
import fr.heralisz.tactical.entity.sp.SpEffectCloudEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
   // Fusion (heraliz_tactique) : usines des lancers (docs/METHODE_CLIENT_SERVEUR.md §4) : version serveur XServeur dans un monde
   // serveur (branchée par fr.heralisz.tactical.serveur.PartieServeur), version commune dans le monde du client. Rangées ici,
   // et non dans les classes des entités, pour ne pas construire leurs EntityType pendant le chargement des mods.
   public static final UsineEntite<GrenadeEntity> GRENADE_USINE = new UsineEntite<>(GrenadeEntity::new);
   public static final UsineEntite<StickyGrenadeEntity> STICKY_GRENADE_USINE = new UsineEntite<>(StickyGrenadeEntity::new);
   public static final UsineEntite<SmokeGrenadeEntity> SMOKE_GRENADE_USINE = new UsineEntite<>(SmokeGrenadeEntity::new);
   public static final UsineEntite<StunGrenadeEntity> STUN_GRENADE_USINE = new UsineEntite<>(StunGrenadeEntity::new);
   public static final UsineEntite<EffectCloudGrenadeEntity> EFFECT_GRENADE_USINE = new UsineEntite<>(EffectCloudGrenadeEntity::new);
   public static final UsineEntite<SpEffectCloudEntity> EFFECT_CLOUD_USINE = new UsineEntite<>(SpEffectCloudEntity::new);
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "heralisz_tactical");
   public static RegistryObject<EntityType<GrenadeEntity>> GRENADE = ENTITY_TYPES.register("explode_grenade", () -> GrenadeEntity.TYPE);
   public static RegistryObject<EntityType<StickyGrenadeEntity>> STICKY_GRENADE = ENTITY_TYPES.register("sticky_grenade_entity", () -> StickyGrenadeEntity.TYPE);
   public static RegistryObject<EntityType<SmokeGrenadeEntity>> SMOKE_GRENADE = ENTITY_TYPES.register("smoke_grenade", () -> SmokeGrenadeEntity.TYPE);
   public static RegistryObject<EntityType<StunGrenadeEntity>> STUN_GRENADE = ENTITY_TYPES.register("stun_grenade", () -> StunGrenadeEntity.TYPE);
   public static RegistryObject<EntityType<EffectCloudGrenadeEntity>> EFFECT_GRENADE = ENTITY_TYPES.register(
      "effect_grenade", () -> EffectCloudGrenadeEntity.TYPE
   );
   public static RegistryObject<EntityType<SpEffectCloudEntity>> EFFECT_CLOUD = ENTITY_TYPES.register("sp_effect_cloud", () -> SpEffectCloudEntity.TYPE);
}
