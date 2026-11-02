package fr.heralisz.tactical.compat.cloth.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import fr.heralisz.tactical.config.ClientConfig;
import net.minecraft.network.chat.Component;

public class BasicClothConfig {
   public static void init(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
      root.getOrCreateCategory(Component.translatable("config.heralisz_tactical.effect"))
         .addEntry(
            entryBuilder.startBooleanToggle(Component.translatable("config.heralisz_tactical.effect.blackflash"), (Boolean)ClientConfig.BLACK_FLASH.get())
               .setDefaultValue(false)
               .setTooltip(new Component[]{Component.translatable("config.heralisz_tactical.effect.blackflash.desc")})
               .setSaveConsumer(ClientConfig.BLACK_FLASH::set)
               .build()
         );
      // HeraliZ (audit du 02/11, point 11) : les réglages COMMON (grenades qui cassent les blocs, usure et seuil des coups
      // de mêlée) sont des réglages du serveur seulement, plus déclarés dans le jeu des joueurs : retirés de cet écran.
      root.getOrCreateCategory(Component.translatable("config.heralisz_tactical.grenade"))
         .addEntry(
            entryBuilder.startDoubleField(
                  Component.translatable("config.heralisz_tactical.grenade.screen_shake"), (Double)ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER.get()
               )
               .setDefaultValue(1.0)
               .setTooltip(new Component[]{Component.translatable("config.heralisz_tactical.grenade.screen_shake.desc")})
               .setSaveConsumer(ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER::set)
               .build()
         );
   }
}
