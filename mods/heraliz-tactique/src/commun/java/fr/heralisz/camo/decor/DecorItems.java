package fr.heralisz.camo.decor;

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

/** Objets de personnalisation : stickers (dessin × finition), pendentifs, module StatTrack ; groupe Décorations de l'onglet HeraliZ · Tactique. */
public final class DecorItems {
   public static final List<RegistryObject<Item>> ALL = new ArrayList<>();
   public static final RegistryObject<Item> STATTRAK = reg("stattrack_module", StatTrakItem::new);

   static {
      for (DecorCatalog.Sticker s : DecorCatalog.Sticker.values()) {
         for (DecorCatalog.Finish f : DecorCatalog.Finish.values()) {
            reg("sticker_" + s.id() + (f == DecorCatalog.Finish.PAPER ? "" : "_" + f.id()), () -> new StickerItem(s, f));
         }
      }
      for (DecorCatalog.Charm c : DecorCatalog.Charm.values()) {
         reg("charm_" + c.id(), () -> new CharmItem(c));
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

   private static void howto(List<Component> tooltip) {
      tooltip.add(Component.translatable("item.heralisz_camo.decor.howto").withStyle(ChatFormatting.DARK_GRAY));
   }

   public static final class StickerItem extends Item {
      public final DecorCatalog.Sticker design;
      public final DecorCatalog.Finish finish;

      StickerItem(DecorCatalog.Sticker design, DecorCatalog.Finish finish) {
         super(new Properties().stacksTo(64));
         this.design = design;
         this.finish = finish;
      }

      @Override
      public Component getName(ItemStack stack) {
         return Component.translatable("item.heralisz_camo.sticker", Component.translatable("sticker.heralisz_camo." + this.design.id()),
            Component.translatable("finish.heralisz_camo." + this.finish.id())).withStyle(this.finish.color);
      }

      @Override
      public boolean isFoil(ItemStack stack) {
         return this.finish == DecorCatalog.Finish.GOLD || this.finish == DecorCatalog.Finish.HOLO;
      }

      @Override
      public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
         tooltip.add(Component.translatable("item.heralisz_camo.sticker.desc").withStyle(ChatFormatting.GRAY));
         howto(tooltip);
      }
   }

   public static final class CharmItem extends Item {
      public final DecorCatalog.Charm design;

      CharmItem(DecorCatalog.Charm design) {
         super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
         this.design = design;
      }

      /** Mobs : icône et objet en 3D (le vrai mob) ; les répliques d'armes gardent leur icône plate. Fusion (heraliz_tactique) : le
       *  rendu (CharmItemRenderer, code client) est fourni par fr.heralisz.camo.client.PartieClient par ce crochet. */
      public static final fr.heraliz.tactique.pont.Crochet<java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions>> RENDU_CLIENT =
         new fr.heraliz.tactique.pont.Crochet<>();

      @Override
      public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
         RENDU_CLIENT.accept(consumer);
      }

      @Override
      public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
         tooltip.add(Component.translatable(this.design.kind == DecorCatalog.Charm.Kind.MOB ? "item.heralisz_camo.charm_mob.desc"
            : "item.heralisz_camo.charm.desc").withStyle(ChatFormatting.GRAY));
         howto(tooltip);
      }
   }

   public static final class StatTrakItem extends Item {
      StatTrakItem() {
         super(new Properties().stacksTo(16).rarity(Rarity.EPIC));
      }

      @Override
      public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
         tooltip.add(Component.translatable("item.heralisz_camo.stattrack.desc").withStyle(ChatFormatting.GRAY));
         howto(tooltip);
         tooltip.add(Component.translatable("item.heralisz_camo.camo.permanent").withStyle(ChatFormatting.DARK_RED));
      }
   }
}
