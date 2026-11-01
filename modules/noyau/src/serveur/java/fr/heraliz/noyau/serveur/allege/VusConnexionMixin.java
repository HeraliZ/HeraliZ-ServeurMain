package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.SuiviParTroncon;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Point 1 de l'étape 2 du chantier « Minecraft allégé » (01/11) : voir {@link SuiviParTroncon}. La connexion d'un joueur
 * (l'objet que Minecraft range dans la liste {@code seenBy} de chaque entité qu'il voit) porte le miroir de ces listes : les
 * entités que ce joueur voit. Créé à la première demande ; gardé d'une réapparition à l'autre comme la connexion.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VusConnexionMixin implements SuiviParTroncon.AvecVus {
   @Unique
   private SuiviParTroncon.VusJoueur heraliz$vusJoueur;

   @Override
   public SuiviParTroncon.VusJoueur heraliz$vus() {
      SuiviParTroncon.VusJoueur v = heraliz$vusJoueur;
      if (v == null) {
         v = heraliz$vusJoueur = SuiviParTroncon.nouveauVusJoueur();
      }
      return v;
   }
}
