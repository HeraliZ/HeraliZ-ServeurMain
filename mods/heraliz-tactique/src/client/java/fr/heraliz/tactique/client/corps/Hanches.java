package fr.heraliz.tactique.client.corps;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Jambes collées au torse avec une arme TaCZ en main (18/10, vraie cause trouvée le 19/10). Les animations 3e personne
 * de TaCZ (Player Animator, gunpacks non modifiés) DÉPLACENT le pivot de chaque jambe, en plus de la tourner : course
 * (« run_lower ») jusqu'à 3,1 pixels en hauteur et 2,7 en profondeur, marche 3,6, arrêt 2,9 (« hold_lower »), pistolet
 * comme fusil, minigun jusqu'à 5,3. Le haut de la cuisse sortait du torse, devant ou derrière : « les jambes se
 * détachent ». Sans arme, Minecraft ne fait que tourner les jambes autour de la hanche : rien ne se détache. La 2.21.3
 * ne compensait que la bascule du torse (2 pixels au plus), pas ces déplacements : aucun changement visible.
 * <p>
 * Ici, debout (pas accroupi : la pose à genou des gestes accroupis en a besoin) avec une arme TaCZ en main : chaque
 * jambe reprend la place que Minecraft lui donne, plus le chemin qu'a fait sa hanche avec le torse animé (bascule,
 * torsion, décalage). Ses rotations (le pas) restent celles de TaCZ. Le haut de chaque cuisse reste donc toujours sous le
 * torse. Les poses sont relevées juste avant Player Animator ({@link #avant}) et corrigées juste après
 * ({@link #recoller}). Armures et corps de CaraCrea recopient ces positions.
 */
public final class Hanches {
   /** Hanche sur le torse : sous le pivot du torse, à la place du pivot de chaque jambe au repos (pixels). */
   private static final float HANCHE_X = 1.9F, HANCHE_Y = 12.0F;
   private static boolean releve;
   private static float x, y, z, rx, ry, rz;
   private static float dx, dy, dz, gx, gy, gz;

   private Hanches() {
   }

   /** Torse et jambes tels que Minecraft les a posés, avant Player Animator. */
   public static void avant(ModelPart torse, ModelPart droite, ModelPart gauche) {
      x = torse.x;
      y = torse.y;
      z = torse.z;
      rx = torse.xRot;
      ry = torse.yRot;
      rz = torse.zRot;
      dx = droite.x;
      dy = droite.y;
      dz = droite.z;
      gx = gauche.x;
      gy = gauche.y;
      gz = gauche.z;
      releve = true;
   }

   /** Après Player Animator : chaque jambe à sa place de Minecraft, déplacée comme sa hanche. */
   public static void recoller(LivingEntity entite, ModelPart torse, ModelPart droite, ModelPart gauche) {
      if (!releve) return;
      releve = false;
      if (!(entite instanceof Player joueur) || !debout(joueur)) return;
      if (!(joueur.getMainHandItem().getItem() instanceof IGun)) return;
      placer(droite, -HANCHE_X, dx, dy, dz, torse);
      placer(gauche, HANCHE_X, gx, gy, gz, torse);
   }

   private static void placer(ModelPart jambe, float hancheX, float px, float py, float pz, ModelPart torse) {
      Vector3f apres = hanche(hancheX, torse.x, torse.y, torse.z, torse.xRot, torse.yRot, torse.zRot);
      Vector3f avant = hanche(hancheX, x, y, z, rx, ry, rz);
      jambe.x = px + apres.x - avant.x;
      jambe.y = py + apres.y - avant.y;
      jambe.z = pz + apres.z - avant.z;
   }

   /** Position de la hanche, comme ModelPart.translateAndRotate place le torse (rotation Z, Y puis X). */
   private static Vector3f hanche(float hancheX, float px, float py, float pz, float xRot, float yRot, float zRot) {
      return new Vector3f(hancheX, HANCHE_Y, 0.0F).rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot)).add(px, py, pz);
   }

   private static boolean debout(Player joueur) {
      if (joueur.getPose() != Pose.STANDING || joueur.isCrouching()) return false;
      if (joueur.isVisuallySwimming() || joueur.isFallFlying() || joueur.isSleeping() || joueur.isPassenger()) return false;
      return joueur.getSwimAmount(Minecraft.getInstance().getFrameTime()) < 0.01F;
   }
}
