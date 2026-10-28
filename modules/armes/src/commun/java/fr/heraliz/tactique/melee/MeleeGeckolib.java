package fr.heraliz.tactique.melee;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

   private MeleeGeckolib() {
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(MeleeGeckolib::attributs);
   }

   private static void attributs(ItemAttributeModifierEvent e) {
      if (e.getSlotType() != EquipmentSlot.MAINHAND) return;
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
      if (id == null || !NAMESPACE.equals(id.getNamespace())) return;
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
