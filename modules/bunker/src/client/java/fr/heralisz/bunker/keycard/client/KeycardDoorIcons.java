package fr.heralisz.bunker.keycard.client;

import fr.heralisz.bunker.HeraliszBunker;
import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.KeycardDoors;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Icônes des portes à carte : fond de la couleur de la carte qui les ouvre. Dessiné derrière l'objet (profondeur plus
 * lointaine que le modèle), plus un liseré en bas pour rester lisible même quand le modèle remplit la case.
 */
@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KeycardDoorIcons {
   private static int argb(AccessLevel level, int alpha) {
      return switch (level) {
         case GREEN -> 0x22AA54;
         case BLUE -> 0x2066D6;
         case RED -> 0xC8222C;
      } | (alpha << 24);
   }

   @SubscribeEvent
   public static void onRegisterDecorations(RegisterItemDecorationsEvent event) {
      for (var item : ForgeRegistries.ITEMS) {
         if (!(item instanceof BlockItem blockItem)) continue;
         AccessLevel level = KeycardDoors.levelOf(blockItem.getBlock());
         if (level == null) continue;
         event.register(item, (graphics, font, stack, x, y) -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 50);                           // derrière le modèle de l'objet (z ~150)
            graphics.fill(x, y, x + 16, y + 16, argb(level, 0x70));
            graphics.pose().popPose();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);                          // liseré devant, en bas de la case
            graphics.fill(x, y + 15, x + 16, y + 16, argb(level, 0xFF));
            graphics.pose().popPose();
            return false;
         });
      }
   }

   private KeycardDoorIcons() {}
}
