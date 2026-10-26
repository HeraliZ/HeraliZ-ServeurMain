package fr.heraliz.crac.registry;

import fr.heraliz.crac.entity.C4Entity;
import fr.heraliz.crac.entity.ExplosiveChargeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "crac");
    public static final RegistryObject<EntityType<C4Entity>> C4 = ENTITIES.register(
        "c4", () -> Builder.<C4Entity>of(C4Entity::new, MobCategory.MISC).sized(0.45F, 0.45F).clientTrackingRange(4).updateInterval(10).build("crac:c4")
    );
    public static final RegistryObject<EntityType<ExplosiveChargeEntity>> EXPLOSIVE_CHARGE = ENTITIES.register(
        "explosive_charge",
        () -> Builder.<ExplosiveChargeEntity>of(ExplosiveChargeEntity::new, MobCategory.MISC)
            .sized(0.4F, 0.4F)
            .clientTrackingRange(4)
            .updateInterval(10)
            .build("crac:explosive_charge")
    );

    private ModEntities() {
    }
}
