package com.mrcrayfish.backpacked.platform;

import com.mrcrayfish.backpacked.platform.services.IScreenHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.fml.ModList;

public class ForgeScreenHelper implements IScreenHelper {
   @Override
   public void openConfigScreen() {
      Minecraft minecraft = Minecraft.getInstance();
      ModList.get()
         .getModContainerById("backpacked")
         .ifPresent(
            container -> {
               Screen screen = container.getCustomExtension(ConfigScreenFactory.class)
                  .map(function -> (Screen)function.screenFunction().apply(minecraft, null))
                  .orElse(null);
               if (screen != null) {
                  minecraft.setScreen(screen);
               } else if (minecraft.player != null) {
                  MutableComponent modName = Component.literal("Configured");
                  modName.setStyle(
                     modName.getStyle()
                        .withColor(ChatFormatting.YELLOW)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(Action.SHOW_TEXT, Component.translatable("backpacked.chat.open_curseforge_page")))
                        .withClickEvent(
                           new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/configured")
                        )
                  );
                  Component message = Component.translatable("backpacked.chat.install_configured", new Object[]{modName});
                  minecraft.player.displayClientMessage(message, false);
               }
            }
         );
   }
}
