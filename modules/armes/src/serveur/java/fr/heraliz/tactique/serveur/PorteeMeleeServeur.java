package fr.heraliz.tactique.serveur;

import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.melee.PorteeMelee;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Portée des armes de corps à corps vérifiée par le serveur à CHAQUE coup porté (03/11, demande du propriétaire : tous
 * les couteaux à 1 bloc au plus, clic gauche et clic droit, même avec un jeu modifié).
 * <p>
 * Les coups de nos armes passent tous par l'événement d'attaque de Forge (AttackEntityEvent) : couteaux TaCZ
 * (CouteauxServeur), armes de mêlée de LR (MeleeItemServeur, IMeleeWeapon.attackTarget) et le coup de Minecraft
 * (paquet « attaquer »). Ce dernier n'est jamais envoyé par le jeu du joueur avec une arme de mêlée ou un couteau en main
 * (clic gauche bloqué chez lui), mais un jeu modifié pouvait l'envoyer jusqu'à la portée de Minecraft (3 blocs, ce que
 * l'anticheat tolère) avec les dégâts de l'arme. Ici, un coup porté avec une arme de corps à corps en main
 * ({@link PorteeMelee#porteeEnMain} : couteaux et batte 2 blocs depuis le 04/11) sur une cible plus loin que sa portée + {@link
 * PorteeMelee#MARGE_SERVEUR} (décalage du réseau, comme les cibles des armes de LR) est annulé. Mesure : des yeux au point
 * le plus proche de la cible. Pas touchés : créatif et spectateur (staff), coup d'essai d'une exécution de Réanimation
 * (ce n'est pas un coup), objets qui ne sont pas des armes de corps à corps (armes à feu, poing). Rien par tick : seulement
 * quand un coup est porté.
 */
final class PorteeMeleeServeur {
   private PorteeMeleeServeur() {
   }

   static void brancher() {
      // HIGH : refusé avant que les autres règles (dégâts, tag de combat, anticheat) ne le voient
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, AttackEntityEvent.class, PorteeMeleeServeur::coup);
   }

   private static void coup(AttackEntityEvent e) {
      Player p = e.getEntity();
      if (p.level().isClientSide || p.isCreative() || p.isSpectator() || e.getTarget() == null) {
         return;
      }
      double portee = PorteeMelee.porteeEnMain(p.getMainHandItem());
      if (portee < 0.0D || CompatReanimation.essaiExecution()) {
         return;
      }
      if (PorteeMelee.distance(p, e.getTarget()) > portee + PorteeMelee.MARGE_SERVEUR) {
         e.setCanceled(true);
      }
   }
}
