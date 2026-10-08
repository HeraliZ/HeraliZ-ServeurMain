package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.pont.UsineEntite;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import fr.heraliz.airstrike.entity.SupplyCrateEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AirstrikeMod.MODID);

    /** Factories (docs/METHODE_CLIENT_SERVEUR.md §4): server versions plugged in by PartieServeur. */
    public static final UsineEntite<EffectCloudEntity> EFFECT_CLOUD_USINE = new UsineEntite<>(EffectCloudEntity::new);
    public static final UsineEntite<SupplyCrateEntity> SUPPLY_CRATE_USINE = new UsineEntite<>(SupplyCrateEntity::new);

    public static final RegistryObject<EntityType<EffectCloudEntity>> EFFECT_CLOUD = ENTITIES.register("effect_cloud",
            () -> EntityType.Builder.<EffectCloudEntity>of(EFFECT_CLOUD_USINE, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(20).fireImmune().noSummon()
                    .build("effect_cloud"));

    public static final RegistryObject<EntityType<SupplyCrateEntity>> SUPPLY_CRATE = ENTITIES.register("supply_crate",
            () -> EntityType.Builder.<SupplyCrateEntity>of(SUPPLY_CRATE_USINE, MobCategory.MISC)
                    .sized(3.0f, 2.35f).clientTrackingRange(16).updateInterval(2).fireImmune()
                    .build("supply_crate"));

    private ModEntities() {
    }
}
