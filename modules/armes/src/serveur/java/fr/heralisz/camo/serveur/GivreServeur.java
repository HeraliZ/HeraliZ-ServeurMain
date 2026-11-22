package fr.heralisz.camo.serveur;

import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.GivreVivant;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.GivrePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Givre Vivant (22/11) : inspection reçue d'un joueur (GivrePacket) -> bascule du givre de l'arme qu'il tient. L'arme
 * réécrite est renvoyée d'elle-même à son joueur et aux joueurs autour (synchronisation des objets de Minecraft). Fil du
 * serveur seulement ; cadence oubliée à la déconnexion et à l'arrêt (PartieServeur).
 */
final class GivreServeur {
   /**
    * Écart minimal entre deux bascules d'un même joueur, en ticks : une inspection dure bien plus. Un jeu modifié qui les
    * enverrait en rafale ne fait pas clignoter l'arme pour les joueurs autour.
    */
   private static final long ECART = 20L;

   private static final Map<UUID, Long> DERNIERE = new HashMap<>();

   private GivreServeur() {
   }

   static void basculer(CamoNetwork.Recu<GivrePacket> recu) {
      ServerPlayer joueur = recu.joueur();
      if (joueur == null || !joueur.isAlive() || joueur.isSpectator()) {
         return;
      }
      ItemStack arme = joueur.getMainHandItem();
      if (IGun.getIGunOrNull(arme) == null || CamoData.get(arme) != Camo.GIVRE_VIVANT) {
         return;
      }
      long maintenant = joueur.level().getGameTime();
      Long avant = DERNIERE.get(joueur.getUUID());
      if (avant != null && maintenant - avant >= 0 && maintenant - avant < ECART) {
         return;
      }
      DERNIERE.put(joueur.getUUID(), maintenant);
      GivreVivant.basculer(arme, maintenant);
   }

   static void oublier(UUID joueur) {
      DERNIERE.remove(joueur);
   }

   static void vider() {
      DERNIERE.clear();
   }
}
