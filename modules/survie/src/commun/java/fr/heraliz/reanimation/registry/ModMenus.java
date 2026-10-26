package fr.heraliz.reanimation.registry;

import fr.heraliz.reanimation.menu.SearchMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
   public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "reanimation");
   public static final RegistryObject<MenuType<SearchMenu>> SEARCH = MENUS.register("search", () -> IForgeMenuType.create(SearchMenu::new));

   private ModMenus() {
   }
}
