package fr.heraliz.crac.registry;

import fr.heraliz.crac.menu.ClaimUpkeepMenu;
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

    private ModMenus() {
    }
}
