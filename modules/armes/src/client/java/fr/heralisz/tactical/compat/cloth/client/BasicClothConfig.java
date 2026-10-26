package fr.heralisz.tactical.compat.cloth.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import fr.heralisz.tactical.config.ClientConfig;
import fr.heralisz.tactical.config.CommonConfig;
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
      root.getOrCreateCategory(Component.translatable("config.heralisz_tactical.grenade"))
         .addEntry(
            entryBuilder.startBooleanToggle(
                  Component.translatable("config.heralisz_tactical.grenade.explode"), (Boolean)CommonConfig.GRENADE_EXPLOSION_BLOCK_DAMAGE.get()
               )
               .setDefaultValue(true)
               .setTooltip(new Component[]{Component.translatable("config.heralisz_tactical.grenade.explode.desc")})
               .setSaveConsumer(CommonConfig.GRENADE_EXPLOSION_BLOCK_DAMAGE::set)
               .build()
         )
         .addEntry(
            entryBuilder.startDoubleField(
                  Component.translatable("config.heralisz_tactical.grenade.screen_shake"), (Double)ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER.get()
               )
               .setDefaultValue(1.0)
               .setTooltip(new Component[]{Component.translatable("config.heralisz_tactical.grenade.screen_shake.desc")})
               .setSaveConsumer(ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER::set)
               .build()
         );
      root.getOrCreateCategory(Component.translatable("config.heralisz_tactical.melee"))
         .addEntry(
            entryBuilder.startBooleanToggle(
                  Component.translatable("config.heralisz_tactical.melee.durability"), (Boolean)CommonConfig.MELEE_ITEM_CONSUME_DURABILITY.get()
               )
               .setDefaultValue(true)
               .setTooltip(new Component[]{Component.translatable("config.heralisz_tactical.melee.durability.desc")})
               .setSaveConsumer(CommonConfig.MELEE_ITEM_CONSUME_DURABILITY::set)
               .build()
         )
         .addEntry(
            entryBuilder.startIntField(
                  Component.translatable("config.heralisz_tactical.melee.max_target"), (Integer)CommonConfig.MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD.get()
               )
               .setDefaultValue(32)
               .setMin(1)
               .setMax(512)
               .setTooltip(new Component[]{Component.translatable("config.heralisz_tactical.melee.max_target.desc")})
               .setSaveConsumer(CommonConfig.MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD::set)
               .build()
         );
   }
}
