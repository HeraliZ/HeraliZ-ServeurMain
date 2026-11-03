package fr.heraliz.tactique.melee;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Armes de mêlée de geckolib222 (contenu ArkaniaZ : batte, machette, tuyau de plomb, masse, matraque télescopique, hache
 * de pompier, couteaux…), dégâts divisés par 2 (28/10, demande du propriétaire), contre les joueurs comme contre les
 * créatures. Le jar de geckolib222 (autre auteur) n'est pas touché : ce sont de simples épées de Minecraft, dont les dégâts
 * viennent de l'attribut « dégâts d'attaque » de l'objet en main. Ici, cet attribut est réécrit pour que le total
 * (1 point du joueur + l'objet) soit divisé par 2 : bonus' = (1 + bonus) / 2 - 1. Le coup critique, le coup balayé et la
 * force s'appliquent ensuite comme d'habitude, sur ce total réduit.
 * <p>
 * Code commun : le serveur décide des dégâts avec ces attributs, le joueur affiche les mêmes chiffres dans l'infobulle.
 * Seuls les objets {@code geckolib:*} sont concernés : ni les armes TaCZ (et leurs couteaux), ni la mêlée de LR
 * ({@code heralisz_melee:*}), qui ont leurs propres dégâts.
 */
public final class MeleeGeckolib {
   private static final String NAMESPACE = "geckolib";
   /** Part des dégâts gardée (total du coup, joueur compris). */
   public static final double FACTEUR = 0.5;
   /**
    * Couteaux de geckolib222 (couteau de cuisine, couteaux à éviscérer 1 à 5, baïonnettes) : portée des couteaux
    * (PorteeMelee.couteaux, 03/11). Ni dans le loot ni en vente aujourd'hui (staff seulement) ; la machette n'est pas un
    * couteau.
    */
   private static final Set<String> COUTEAUX = Set.of("knife", "gut_knife_1", "gut_knife_2", "gut_knife_3", "gut_knife_4",
      "gut_knife_5", "bayonet_knife", "bayonet_knife_2");
   /**
    * Vitesse en main des couteaux de geckolib222 : +10 % de la vitesse de base, comme tous les couteaux (03/11, demande du
    * propriétaire ; avant : aucun bonus). Main principale seulement.
    */
   private static final AttributeModifier VITESSE_COUTEAU = new AttributeModifier(
      UUID.fromString("7c6b0bba-47ef-4e97-b0a4-8bcbf42209e1"), "HeraliZ : vitesse des couteaux", 0.1,
      AttributeModifier.Operation.MULTIPLY_BASE);

   private MeleeGeckolib() {
   }

   /** L'objet est-il un couteau de geckolib222 ? */
   public static boolean estCouteau(ItemStack stack) {
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
      return id != null && NAMESPACE.equals(id.getNamespace()) && COUTEAUX.contains(id.getPath());
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(MeleeGeckolib::attributs);
   }

   private static void attributs(ItemAttributeModifierEvent e) {
      if (e.getSlotType() != EquipmentSlot.MAINHAND) return;
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
      if (id == null || !NAMESPACE.equals(id.getNamespace())) return;
      if (COUTEAUX.contains(id.getPath())) {
         e.addModifier(Attributes.MOVEMENT_SPEED, VITESSE_COUTEAU);
      }
      Multimap<Attribute, AttributeModifier> mods = e.getModifiers();
      if (!mods.containsKey(Attributes.ATTACK_DAMAGE)) return;
      // une seule addition au total (normalement il n'y en a qu'une) : le point du joueur n'est compté qu'une fois
      List<AttributeModifier> additions = new ArrayList<>();
      double somme = 0;
      for (AttributeModifier m : mods.get(Attributes.ATTACK_DAMAGE)) {
         if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
            additions.add(m);
            somme += m.getAmount();
         }
      }
      if (additions.isEmpty()) return;
      AttributeModifier premier = additions.get(0);
      for (AttributeModifier m : additions) {
         e.removeModifier(Attributes.ATTACK_DAMAGE, m);
      }
      e.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(premier.getId(), premier.getName(),
            (1.0 + somme) * FACTEUR - 1.0, AttributeModifier.Operation.ADDITION));
   }
}
