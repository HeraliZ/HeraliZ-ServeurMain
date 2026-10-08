package fr.heralisz.tactical.compat.cloth;

import javax.annotation.Nullable;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import fr.heralisz.tactical.compat.cloth.client.BasicClothConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.fml.ModLoadingContext;

public class MenuIntegration {
   public static ConfigBuilder getConfigBuilder() {
      ConfigBuilder root = ConfigBuilder.create().setTitle(Component.literal("LesRaisins Tactical Settings"));
      root.setGlobalized(true);
      root.setGlobalizedExpanded(false);
      ConfigEntryBuilder entryBuilder = root.entryBuilder();
      BasicClothConfig.init(root, entryBuilder);
      return root;
   }

   public static void registerModsPage() {
      ModLoadingContext.get().registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory((client, parent) -> getConfigScreen(parent)));
   }

   public static Screen getConfigScreen(@Nullable Screen parent) {
      return getConfigBuilder().setParentScreen(parent).build();
   }
}
