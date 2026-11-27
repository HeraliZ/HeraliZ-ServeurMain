package fr.heraliz.vehicules.menu;

import fr.heraliz.vehicules.HeralizVehicules;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Menus de HeraliZ Véhicules : le coffre d'un véhicule ({@code heraliz_vehicules:coffre}, 27/11). Enregistré chez les
 * joueurs ET sur le serveur (jars de la même fabrication).
 */
public final class MenusVehicules {
    private MenusVehicules() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HeralizVehicules.MODID);

    /** Coffre d'un véhicule : colonnes et lignes envoyées dans le paquet d'ouverture. */
    public static final RegistryObject<MenuType<CoffreVehiculeMenu>> COFFRE = MENUS.register("coffre",
            () -> IForgeMenuType.create(CoffreVehiculeMenu::client));

    public static void enregistrer(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
