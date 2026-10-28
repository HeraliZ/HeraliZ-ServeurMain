package fr.heraliz.noyau.serveur.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Coups de mêlée à travers une porte ou un mur (27/10, demande du propriétaire). Le serveur de Minecraft ne vérifie jamais
 * qu'un coup au corps à corps a une ligne de vue : seule la visée du jeu du joueur s'en occupe. Un joueur collé à une
 * porte pouvait donc frapper celui qui était derrière (jeu modifié, crosse ou couteau qui ne regardaient que la distance,
 * créature collée à la porte...). Un coup direct sur un joueur (poing, arme de mêlée, couteau, crosse de TaCZ, coup de
 * grâce, créature) est annulé si aucun point de son corps n'est visible des yeux de l'attaquant à travers la collision des
 * blocs (murs, portes fermées, planchers des bases ; une porte ouverte, une fenêtre laissent passer). Balles, grenades et
 * explosions ne sont pas concernées (les balles : {@code TaczHitboxCorpsMixin} du mod tactique). Fil serveur.
 */
public final class CoupsAuTravers {
   /** Au-delà, ce n'est plus un coup au corps à corps (8 blocs) : rien n'est vérifié. */
   private static final double DISTANCE_MAX_SQ = 64.0D;

   private CoupsAuTravers() {}

   static void enregistrer() {
      // HIGH : annulé avant que les autres règles (dégâts, réanimation, tag de combat) ne le voient
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CoupsAuTravers::coup);
   }

   private static void coup(LivingAttackEvent e) {
      LivingEntity cible = e.getEntity();
      if (!(cible instanceof Player) || cible.level().isClientSide) return;
      DamageSource s = e.getSource();
      if (!(s.getDirectEntity() instanceof LivingEntity auteur) || s.getEntity() != auteur || auteur == cible) return;
      if (s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypes.THORNS)) return;
      if (auteur instanceof Player p && (p.isCreative() || p.isSpectator())) return;
      if (auteur.level() != cible.level() || auteur.distanceToSqr(cible) > DISTANCE_MAX_SQ) return;
      if (!visible(auteur, cible)) {
         e.setCanceled(true);
      }
   }

   /**
    * Un point du corps de la cible (yeux, milieu, coins de sa boîte un peu rentrés) est-il visible des yeux de l'auteur ?
    * Au plus 10 rayons, seulement pour un coup porté.
    */
   static boolean visible(LivingEntity auteur, LivingEntity cible) {
      Vec3 oeil = auteur.getEyePosition();
      if (libre(auteur, oeil, cible.getEyePosition())) return true;
      AABB b = cible.getBoundingBox().deflate(0.05D);
      if (libre(auteur, oeil, b.getCenter())) return true;
      for (int i = 0; i < 8; i++) {
         Vec3 coin = new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
         if (libre(auteur, oeil, coin)) return true;
      }
      return false;
   }

   private static boolean libre(LivingEntity auteur, Vec3 de, Vec3 a) {
      return auteur.level().clip(new ClipContext(de, a, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, auteur)).getType() == HitResult.Type.MISS;
   }
}
