package fr.heralisz.tactical.item;

import java.util.List;
import fr.heralisz.tactical.api.collision.ITargetFilter;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.melee.AttackResult;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.config.CommonConfig;
import fr.heralisz.tactical.item.melee.CombatData;
import fr.heraliz.tactique.melee.PorteeMelee;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Partie SERVEUR de {@link MeleeItem} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun (ModItems) dès que la partie serveur est dans le jar (objet unique, partagé par les
 * deux côtés avec -complet : le code d'origine garde ses tests isClientSide). Ne jamais faire « new MeleeItem(...) ».
 */
public class MeleeItemServeur extends MeleeItem {
   public MeleeItemServeur() {
      super();
   }

   @Override
   public void attack(Player attacker, ItemStack stack, MeleeAction action, List<Entity> targets, int combo) {
      float base = (float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
      this.getMeleeIndex(stack).ifPresent(index -> {
         CombatData combatData = index.getData().getAttackInfo();
         if (combatData != null) {
            CombatData.MeleeAttackInfo attackInfo = combatData.getAttackInfo(action, combo);
            if (attackInfo != null) {
               ITargetFilter filter = attackInfo.getHitbox();
               IMeleeWeapon.playMeleeSound(attacker, index.getId(), action.getId(), 2.0F, 1.0F, true);
               float damage = base * attackInfo.getFactor();
               float knockback = attackInfo.getKnockback();
               if (!(damage <= 0.0F)) {
                  boolean hit = false;
                  boolean crit = false;
                  boolean kill = false;

                  for (Entity livingentity : targets) {
                     boolean flag = !(livingentity instanceof ArmorStand armorStand && armorStand.isMarker());
                     // HeraliZ : même mesure que la zone du coup chez le joueur et que Spartan (des yeux au point le plus
                     // proche de la cible, portée plafonnée par [melee] portee_max, petite marge pour le décalage du réseau) ;
                     // avant : distance des pieds aux pieds, jusqu'à la portée de la fiche (4 blocs)
                     boolean inRange = PorteeMelee.aPortee(attacker, livingentity, filter.getMaxRange());
                     if (livingentity != attacker && flag && inRange) {
                        AttackResult result = this.performAttack(attacker, livingentity, stack, damage, knockback);
                        hit |= result.hit();
                        crit |= result.crit();
                        kill |= result.kill();
                     }
                  }

                  if (hit) {
                     if ((Boolean)CommonConfig.MELEE_ITEM_CONSUME_DURABILITY.get()) {
                        stack.hurtAndBreak(attackInfo.getDurabilityDamage(), attacker, player -> player.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                     }

                     String soundKey;
                     if (kill) {
                        soundKey = "kill";
                     } else if (crit) {
                        soundKey = "crit";
                     } else {
                        soundKey = action.getId() + "_hit";
                     }

                     IMeleeWeapon.playMeleeSound(attacker, index.getId(), soundKey, 2.0F, 1.0F, true);
                     IMeleeWeapon.playMeleeSoundToAttacker(attacker, index.getId(), soundKey + "_feedback", 2.0F, 1.0F);
                  }
               }
            }
         }
      });
   }
}
