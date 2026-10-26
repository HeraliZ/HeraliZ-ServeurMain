package fr.heraliz.armory.registry;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.entity.Mannequin;
import fr.heraliz.armory.menu.MannequinMenu;
import fr.heraliz.armory.pont.UsineEntite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Mannequin d'armurerie : type d'entite et menu. */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeralizArmory.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HeralizArmory.MOD_ID);

    /** Usine du mannequin : version serveur (MannequinServeur : casse, objets Curios, menu) branchee par la partie serveur. */
    public static final UsineEntite<Mannequin> MANNEQUIN_USINE = new UsineEntite<>(Mannequin::new);

    public static final RegistryObject<EntityType<Mannequin>> MANNEQUIN = ENTITIES.register("mannequin",
            () -> EntityType.Builder.<Mannequin>of(MANNEQUIN_USINE, MobCategory.MISC).sized(0.6F, 2.0F).clientTrackingRange(10)
                    .build(HeralizArmory.id("mannequin").toString()));

    public static final RegistryObject<MenuType<MannequinMenu>> MANNEQUIN_MENU = MENUS.register("mannequin",
            () -> IForgeMenuType.create(MannequinMenu::fromNetwork));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(MANNEQUIN.get(), LivingEntity.createLivingAttributes().build());
    }
}
