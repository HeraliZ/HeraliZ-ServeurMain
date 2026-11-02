package fr.heraliz.reanimation.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Objets de Réanimation. 02/11 (audit, mineur 13) : l'œuf du bot d'essai ({@code reanimation:bot_spawn_egg}) est retiré
 * avec le bot (commandes retirées le 24/10).
 */
public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "reanimation");

   /** Finisher à débloquer (26/10) : caisses Vote et Quête, clic droit = ajouté à la collection. */
   public static final RegistryObject<Item> FINISHER = ITEMS.register("finisher", fr.heraliz.reanimation.execution.ObjetFinisher::new);

   private ModItems() {
   }
}
