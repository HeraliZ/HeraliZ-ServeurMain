package fr.heraliz.crac.registry;

import fr.heraliz.crac.menu.ClaimUpkeepMenu;
import fr.heraliz.crac.menu.CoffreMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "crac");
    public static final RegistryObject<MenuType<ClaimUpkeepMenu>> CLAIM_UPKEEP = MENUS.register(
        "claim_upkeep", () -> IForgeMenuType.create(ClaimUpkeepMenu::new)
    );

    /** Coffres de plus de 9 colonnes (caisse d'armes, coffre-fort). */
    public static final RegistryObject<MenuType<CoffreMenu>> COFFRE = MENUS.register("coffre", () -> IForgeMenuType.create(CoffreMenu::new));

    private ModMenus() {
    }
}
