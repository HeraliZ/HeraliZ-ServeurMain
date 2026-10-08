package fr.heralisz.tactical.init;

import fr.heraliz.tactique.pont.Usine;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.item.ConsumableItem;
import fr.heralisz.tactical.item.DetonatorItem;
import fr.heralisz.tactical.item.FlashShieldItem;
import fr.heralisz.tactical.item.MeleeItem;
import fr.heralisz.tactical.item.ThrowableItem;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.MOD)
public class ModItems {
   // Fusion (heraliz_tactique) : les trois onglets d'origine (throwable, consumable, melee) sont remplacés par les groupes
   // Mêlée / Soins / Grenades de l'onglet unique « HeraliZ · Tactique » (voir remplirMelee/remplirSoins/remplirGrenades).
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "heralisz_tactical");
   // Fusion : objets créés par usine (docs/METHODE_CLIENT_SERVEUR.md §5.3) ; la partie serveur branche leurs versions
   // XServeur (soins, lancers, coups, bouclier, détonateur) dans fr.heralisz.tactical.serveur.PartieServeur.
   public static final Usine<Void, ConsumableItem> CONSUMABLE_USINE = Usine.simple(ConsumableItem::new);
   public static final Usine<Void, ThrowableItem> THROWABLE_USINE = Usine.simple(ThrowableItem::new);
   public static final Usine<Void, MeleeItem> MELEE_USINE = Usine.simple(MeleeItem::new);
   public static final Usine<Void, FlashShieldItem> FLASH_SHIELD_USINE = Usine.simple(FlashShieldItem::new);
   public static final Usine<Void, DetonatorItem> DETONATOR_USINE = Usine.simple(DetonatorItem::new);
   public static RegistryObject<ConsumableItem> CONSUMABLE = ITEMS.register("consumable", CONSUMABLE_USINE::creer);
   public static RegistryObject<ThrowableItem> THROWABLE = ITEMS.register("throwable", THROWABLE_USINE::creer);
   public static RegistryObject<MeleeItem> MELEE = ITEMS.register("melee", MELEE_USINE::creer);
   public static RegistryObject<FlashShieldItem> FLASH_SHIELD = ITEMS.register("flash_shield", FLASH_SHIELD_USINE::creer);
   public static RegistryObject<DetonatorItem> DETONATOR = ITEMS.register("detonator", DETONATOR_USINE::creer);

   public static ItemStack getThrowableIcon() {
      ItemStack stack = new ItemStack((ItemLike)THROWABLE.get());
      IThrowable iThrowable = IThrowable.of(stack);
      if (iThrowable != null) {
         iThrowable.setId(stack, new ResourceLocation("heralisz_grenade", "m67"));   // HeralisZ : contenu dans heralisz_grenade
      }

      return stack;
   }

   public static ItemStack getConsumableIcon() {
      ItemStack stack = new ItemStack((ItemLike)CONSUMABLE.get());
      IConsumable consumable = IConsumable.of(stack);
      if (consumable != null) {
         consumable.setId(stack, new ResourceLocation("heralisz_heal", "blood_pack"));   // HeralisZ : contenu dans heralisz_heal
      }

      return stack;
   }

   public static ItemStack getMeleeIcon() {
      ItemStack stack = new ItemStack((ItemLike)MELEE.get());
      IMeleeWeapon iMeleeWeapon = IMeleeWeapon.of(stack);
      if (iMeleeWeapon != null) {
         iMeleeWeapon.setId(stack, new ResourceLocation("heralisz_melee", "dagger"));   // HeralisZ : contenu dans heralisz_melee
      }

      return stack;
   }

   /** Groupe « Mêlée » de l'onglet HeraliZ · Tactique : toutes les armes de mêlée (aussi rangées dans l'onglet Heavy Weapon de TaCZ). */
   public static void remplirMelee(Output pOutput) {
      for (MeleeWeaponIndex<?> index : HeraliszTacticalAPI.getMeleeIndexes()) {
         pOutput.accept(index.createItemStack());
      }
   }

   /** Groupe « Soins » : contenu de l'ancien onglet heralisz_tactical:consumable. */
   public static void remplirSoins(Output pOutput) {
      fillConsumables(null, pOutput);
   }

   /** Groupe « Grenades » : contenu de l'ancien onglet heralisz_tactical:throwable (lancers + détonateur). */
   public static void remplirGrenades(Output pOutput) {
      fillThrowables(null, pOutput);
   }

   public static void fillConsumables(ItemDisplayParameters pParameters, Output pOutput) {
      for (ConsumableIndex index : HeraliszTacticalAPI.getConsumableIndexes()) {
         ItemStack stack = index.createItemStack();
         pOutput.accept(stack);
      }
   }

   public static void fillThrowables(ItemDisplayParameters pParameters, Output pOutput) {
      for (ThrowableIndex<?, ?> index : HeraliszTacticalAPI.getThrowableIndexes()) {
         ItemStack stack = index.createItemStack();
         pOutput.accept(stack);
      }

      pOutput.accept(new ItemStack((ItemLike)DETONATOR.get()));
   }

   /**
    * HeralisZ : les armes de mêlée sont rangées dans l'onglet Heavy Weapon de TaCZ avec les couteaux (voir HeraliszTabs) ;
    * cet onglet reste donc vide et n'est pas affiché. Le Flash Shield n'est plus proposé.
    */
   public static void fillMeleeWeapons(ItemDisplayParameters pParameters, Output pOutput) {
   }
}
