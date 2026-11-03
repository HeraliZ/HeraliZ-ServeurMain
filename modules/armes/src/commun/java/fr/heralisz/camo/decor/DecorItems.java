package fr.heralisz.camo.decor;

import fr.heralisz.camo.Camo;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Objets de personnalisation : stickers (dessin × finition), module StatTrack ; groupe Décorations de l'onglet HeraliZ · Tactique. */
public final class DecorItems {
   public static final List<RegistryObject<Item>> ALL = new ArrayList<>();
   public static final RegistryObject<Item> STATTRAK = reg("stattrack_module", StatTrakItem::new);

   static {
      for (DecorCatalog.Sticker s : DecorCatalog.Sticker.values()) {
         for (DecorCatalog.Finish f : DecorCatalog.Finish.values()) {
            reg("sticker_" + s.id() + (f == DecorCatalog.Finish.PAPER ? "" : "_" + f.id()), () -> new StickerItem(s, f));
         }
      }
   }

   // Fusion (heraliz_tactique) : onglet « decor » remplacé par le groupe Décorations de l'onglet unique (HeraliszCamo.remplirDecor).

   private DecorItems() {
   }

   private static RegistryObject<Item> reg(String name, java.util.function.Supplier<Item> sup) {
      RegistryObject<Item> r = HeraliszCamo.ITEMS.register(name, sup);
      ALL.add(r);
      return r;
   }

   /** Charge la classe (enregistrement des objets) depuis le constructeur du mod. */
   public static void init() {
   }

   /**
    * Infobulle des stickers et du module StatTrak (03/11, choix du propriétaire, comme les camos : « pas de description,
    * nom + rareté + une ligne simple ») : la rareté, puis « Posé depuis le menu de modification d'armes ».
    */
   private static void infobulle(List<Component> tooltip, Camo.Tier rarete) {
      tooltip.add(Component.translatable(rarete.key()).withStyle(rarete.color, ChatFormatting.BOLD));
      tooltip.add(Component.translatable("item.heralisz_camo.decor.howto").withStyle(ChatFormatting.GRAY));
   }

   /**
    * Rareté d'un sticker selon sa finition, celle de la caisse Camo (outils/caisses.py, CAISSES_BETA : papier Commun, holo
    * Rare, métallisé Épique, or Légendaire).
    */
   public static Camo.Tier rarete(DecorCatalog.Finish finition) {
      return switch (finition) {
         case PAPER -> Camo.Tier.COMMON;
         case HOLO -> Camo.Tier.RARE;
         case FOIL -> Camo.Tier.EPIC;
         case GOLD -> Camo.Tier.LEGENDARY;
      };
   }

   public static final class StickerItem extends Item {
      public final DecorCatalog.Sticker design;
      public final DecorCatalog.Finish finish;

      StickerItem(DecorCatalog.Sticker design, DecorCatalog.Finish finish) {
         super(new Properties().stacksTo(64));
         this.design = design;
         this.finish = finish;
      }

      /** Nom dans la couleur de sa rareté (03/11, comme les camos et tous les objets ; avant : couleur de la finition). */
      @Override
      public Component getName(ItemStack stack) {
         return Component.translatable("item.heralisz_camo.sticker", Component.translatable("sticker.heralisz_camo." + this.design.id()),
            Component.translatable("finish.heralisz_camo." + this.finish.id())).withStyle(rarete(this.finish).color);
      }

      @Override
      public boolean isFoil(ItemStack stack) {
         return this.finish == DecorCatalog.Finish.GOLD || this.finish == DecorCatalog.Finish.HOLO;
      }

      @Override
      public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
         infobulle(tooltip, rarete(this.finish));
      }
   }

   public static final class StatTrakItem extends Item {
      StatTrakItem() {
         super(new Properties().stacksTo(16).rarity(Rarity.EPIC));
      }

      @Override
      public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
         // Épique comme dans la caisse Camo (le nom l'est déjà : rareté Épique de Minecraft, en violet)
         infobulle(tooltip, Camo.Tier.EPIC);
      }
   }
}
