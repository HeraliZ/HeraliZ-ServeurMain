package fr.heraliz.tactique.onglets;

import com.tacz.guns.api.item.IGun;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Objets retirés du jeu (03/11, choix du propriétaire) : identifiants figés (CLAUDE.md, règle 8), toujours enregistrés et
 * utilisables par les joueurs qui en ont (une arme garde ses fichiers de gunpack et ses dégâts), mais plus montrés dans
 * les onglets du mode créatif ni dans sa recherche. Ni loot, ni fabrication, ni marchand, ni caisse : même liste dans
 * {@code outils/donnees/objets_retires.json}, lue par les outils qui écrivent ces fichiers (à tenir à jour ensemble).
 */
public final class Retires {
   /** Soins (heralisz_heal) : « Attelle et bandage », remplacé par le Bandage et l'Attelle séparés. */
   public static final Set<ResourceLocation> SOINS = Set.of(new ResourceLocation("heralisz_heal", "attelle_bandage"));
   /** Armes TaCZ : Deagle .12g et Thunderbird Short (pistolets de Max Stuff). */
   public static final Set<ResourceLocation> ARMES = Set.of(
      new ResourceLocation("maxstuff", "deagle_12g"), new ResourceLocation("maxstuff", "thunderbird_short"));

   private Retires() {
   }

   /** L'objet est-il une arme TaCZ retirée du jeu ? */
   public static boolean armeRetiree(ItemStack stack) {
      IGun gun = IGun.getIGunOrNull(stack);
      if (gun == null) {
         return false;
      }
      ResourceLocation id = gun.getGunId(stack);
      return id != null && ARMES.contains(id);
   }
}
