package fr.heraliz.tactique.client.allonge;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import fr.heraliz.tactique.allonge.CompatReanimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * Corps allongé propre (24/10, « le corps est démembré quand on rampe ») : les gestes « allongé » de TaCZ (avec ou sans
 * arme) tournent le torse (15° sur lui-même, penché de 8°) et jettent la jambe droite de côté, genou plié (65°, 43°) ;
 * faits pour le modèle à genoux pliables de Player Animator, ils détachent le torse et les jambes du corps de CaraCrea,
 * qui ne plie pas. Cette couche passe par-dessus toutes les autres pour le torse et les jambes seulement : torse droit,
 * jambes droites sous le bassin, un peu écartées, qui battent doucement l'une après l'autre en rampant. Bras et tête
 * restent ceux de TaCZ (arme en main) ou de {@link RamperSansArme}. Rien en 1re personne. Même pose dans la hitbox
 * (HitboxCorps.allonge).
 */
final class CorpsAllonge implements IAnimation {
   /** Écart des jambes (radians) et battement en rampant. */
   private static final float ECART = 0.06F, BATTEMENT = 0.22F;
   private static final Vec3f TORSE = new Vec3f(0.0F, 0.0F, 0.0F);
   private static final Vec3f JAMBE_DROITE = new Vec3f(-1.9F, 12.0F, 0.1F), JAMBE_GAUCHE = new Vec3f(1.9F, 12.0F, 0.1F);

   private final AbstractClientPlayer joueur;
   private float tickDelta;

   CorpsAllonge(AbstractClientPlayer joueur) {
      this.joueur = joueur;
   }

   @Override
   public boolean isActive() {
      return allonge(joueur);
   }

   @Override
   public void setupAnim(float tickDelta) {
      this.tickDelta = tickDelta;
   }

   @Override
   public Vec3f get3DTransform(String partie, TransformType type, float tickDelta, Vec3f avant) {
      switch (partie) {
         case "torso":
            return TORSE;
         case "rightLeg":
         case "leftLeg": {
            boolean droite = partie.equals("rightLeg");
            if (type == TransformType.POSITION) return droite ? JAMBE_DROITE : JAMBE_GAUCHE;
            if (type == TransformType.BEND) return TORSE;
            float vitesse = Math.min(1.0F, joueur.walkAnimation.speed(this.tickDelta) * 1.5F);
            float pas = Mth.sin(joueur.walkAnimation.position(this.tickDelta) * 0.6662F) * BATTEMENT * vitesse;
            float roulis = droite ? ECART + pas : -ECART + pas;
            return new Vec3f(0.0F, 0.0F, roulis);
         }
         default:
            return avant;
      }
   }

   /** Mêmes règles que l'allongé de TaCZ et {@link RamperSansArme}, avec ou sans arme. */
   static boolean allonge(AbstractClientPlayer joueur) {
      if (joueur.isSwimming() || joueur.isInWater() || joueur.isPassenger() || joueur.isSpectator() || joueur.isSleeping()
            || joueur.isFallFlying()) {
         return false;
      }
      return joueur.getSwimAmount(1.0F) > 0.5F && !CompatReanimation.aTerre(joueur);
   }
}
