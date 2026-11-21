package fr.heralisz.camo.item;

import fr.heralisz.camo.Camo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Objet camo. Clic droit : le camo entre dans la collection à vie du joueur et l'objet est consommé (déjà possédé : message,
 * rien de consommé). Posé sur l'arme depuis l'onglet Camo de l'atelier ou l'onglet Skin de secours (21/11, demande du
 * propriétaire : les skins d'armes comme ceux des couteaux, impossibles à voler) : il entre aussi dans la collection
 * (consommé seulement s'il n'y était pas encore), puis il se pose comme un camo de la collection : lié au joueur, une arme
 * à la fois, perdu par l'arme qui change de main ou à la mort (fr.heralisz.camo.serveur.CamosAVie.poserObjet). Avant, il
 * devenait un camo définitif qui suivait l'arme.
 */
public class CamoItem extends Item {
   /**
    * Ajout à la collection à vie (CamosAVie.ajouter, branché par la partie serveur du mod) ; vrai si le camo vient d'y
    * entrer (un exemplaire est alors consommé). Sans partie serveur (jar -client) : rien n'est ajouté.
    */
   public static volatile BiPredicate<ServerPlayer, Camo> AJOUTER_COLLECTION = (p, c) -> false;

   private final Camo camo;

   public CamoItem(Camo camo) {
      super(new Properties().stacksTo(16));
      this.camo = camo;
   }

   public Camo camo() {
      return this.camo;
   }

   /**
    * Ce camo peut-il aller sur un sac ? Pas un skin réservé à une arme, ni un skin à vie (skins X : Séraphin, Chaînes de
    * l'Enfer, Phénix, Abysses, Prisme : seulement par la collection, jamais consommé). Même règle pour l'artisanat
    * (CamoBagRecipe).
    */
   public static boolean vaSurUnSac(Camo camo) {
      return camo.gun == null && !fr.heraliz.tactique.avie.ObjetsAVie.estSkinAVie(camo);
   }

   @Override
   public Component getName(ItemStack stack) {
      return super.getName(stack).copy().withStyle(this.camo.tier::nom);
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return this.camo.tier == Camo.Tier.LEGENDARY || this.camo.tier == Camo.Tier.MYTHIC;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         if (AJOUTER_COLLECTION.test(sp, this.camo) && !player.getAbilities().instabuild) {
            stack.shrink(1);
         }
         return InteractionResultHolder.consume(stack);
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   /**
    * Infobulle (03/11, choix du propriétaire : « pas de description, nom + rareté + une ligne simple ») : le nom (couleur
    * de la rareté), la rareté, l'arme réservée s'il y en a une (aucun camo aujourd'hui), puis « Clic droit : ajoute à la
    * collection à vie ». 14/11 (retour du propriétaire : « juste une fois le petit encadré avec écrit EPIC ») : la rareté
    * est le badge (Camo.Tier.badge), plus le mot « Épique ». 21/11 : la ligne dit aussi qu'un camo posé sur une arme
    * rejoint la collection (« Clic droit ou posé sur une arme : ajouté à ta collection à vie »).
    */
   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(this.camo.tier.badge());
      if (this.camo.gun != null) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.gun." + this.camo.id).withStyle(ChatFormatting.GOLD));
      }
      tooltip.add(Component.translatable("item.heralisz_camo.camo.collect").withStyle(ChatFormatting.GRAY));
   }
}
