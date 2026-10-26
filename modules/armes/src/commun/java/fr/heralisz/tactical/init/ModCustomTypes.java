package fr.heralisz.tactical.init;

import fr.heralisz.tactical.entity.EffectCloudGrenadeEntity;
import fr.heralisz.tactical.entity.GrenadeEntity;
import fr.heralisz.tactical.entity.SmokeGrenadeEntity;
import fr.heralisz.tactical.entity.StickyGrenadeEntity;
import fr.heralisz.tactical.entity.StunGrenadeEntity;
import fr.heralisz.tactical.item.melee.MeleeWeaponData;
import fr.heralisz.tactical.item.melee.MeleeWeaponType;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heralisz.tactical.item.throwable.ThrowableType;
import fr.heralisz.tactical.item.throwable.area.CloudType;
import fr.heralisz.tactical.item.throwable.area.EffectCloudThrowableData;
import fr.heralisz.tactical.item.throwable.explode.ExplodeThrowableData;
import fr.heralisz.tactical.item.throwable.explode.ExplodeType;
import fr.heralisz.tactical.item.throwable.explode.StickyType;
import fr.heralisz.tactical.item.throwable.flash.StunThrowableData;
import fr.heralisz.tactical.item.throwable.flash.StunType;
import fr.heralisz.tactical.item.throwable.smoke.SmokeType;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCustomTypes {
   public static final DeferredRegister<ThrowableType<?, ?>> THROWABLE_TYPES = DeferredRegister.create(ModRegistries.THROWABLE_TYPE, "heralisz_tactical");
   public static RegistryObject<ThrowableType<ExplodeThrowableData, GrenadeEntity>> EXPLODE = THROWABLE_TYPES.register("explode", () -> ExplodeType.EXPLODE);
   public static RegistryObject<ThrowableType<ExplodeThrowableData, StickyGrenadeEntity>> STICKY = THROWABLE_TYPES.register("sticky", () -> StickyType.STICKY);
   public static RegistryObject<ThrowableType<ThrowableData, SmokeGrenadeEntity>> SMOKE = THROWABLE_TYPES.register("smoke", () -> SmokeType.SMOKE);
   public static RegistryObject<ThrowableType<StunThrowableData, StunGrenadeEntity>> STUN = THROWABLE_TYPES.register("stun", () -> StunType.STUN);
   public static RegistryObject<ThrowableType<EffectCloudThrowableData, EffectCloudGrenadeEntity>> EFFECT_CLOUD = THROWABLE_TYPES.register(
      "effect_cloud", () -> CloudType.CLOUD
   );
   public static final DeferredRegister<MeleeWeaponType<?>> MELEE_WEAPON_TYPES = DeferredRegister.create(ModRegistries.MELEE_WEAPON_TYPE, "heralisz_tactical");
   public static RegistryObject<MeleeWeaponType<MeleeWeaponData>> NORMAL = MELEE_WEAPON_TYPES.register(
      "normal", () -> new MeleeWeaponType<>(jsonElement -> (MeleeWeaponData)CommonAssetsManager.GSON.fromJson(jsonElement, MeleeWeaponData.class))
   );
}
