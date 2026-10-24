package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.pont.UsineEntite;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import fr.heraliz.airstrike.entity.SupplyCrateEntity;
import fr.heraliz.airstrike.helico.CaisseHelicoEntity;
import fr.heraliz.airstrike.helico.EpaveHelicoEntity;
import fr.heraliz.airstrike.helico.HelicoEntity;
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

    /** Hélicoptère de patrouille, morceaux de son épave, caisses qu'il transportait (24/10) : versions serveur par PartieServeur. */
    public static final UsineEntite<HelicoEntity> HELICO_USINE = new UsineEntite<>(HelicoEntity::new);
    public static final UsineEntite<EpaveHelicoEntity> HELICO_EPAVE_USINE = new UsineEntite<>(EpaveHelicoEntity::new);
    public static final UsineEntite<CaisseHelicoEntity> HELICO_CAISSE_USINE = new UsineEntite<>(CaisseHelicoEntity::new);

    /** Suivi large (limité de toute façon par la distance de vue du serveur ; au-delà : HelicoPositionPacket). */
    public static final RegistryObject<EntityType<HelicoEntity>> HELICO = ENTITIES.register("helico",
            () -> EntityType.Builder.<HelicoEntity>of(HELICO_USINE, MobCategory.MISC)
                    .sized(6.0f, 3.4f).clientTrackingRange(16).updateInterval(2).fireImmune().noSummon()
                    .build("helico"));

    public static final RegistryObject<EntityType<EpaveHelicoEntity>> HELICO_EPAVE = ENTITIES.register("helico_epave",
            () -> EntityType.Builder.<EpaveHelicoEntity>of(HELICO_EPAVE_USINE, MobCategory.MISC)
                    .sized(3.0f, 1.5f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon()
                    .build("helico_epave"));

    public static final RegistryObject<EntityType<CaisseHelicoEntity>> HELICO_CAISSE = ENTITIES.register("helico_caisse",
            () -> EntityType.Builder.<CaisseHelicoEntity>of(HELICO_CAISSE_USINE, MobCategory.MISC)
                    .sized(1.4f, 0.9f).clientTrackingRange(8).updateInterval(5).fireImmune().noSummon()
                    .build("helico_caisse"));

    private ModEntities() {
    }
}
