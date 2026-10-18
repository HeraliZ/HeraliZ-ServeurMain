package fr.heraliz.tactique.client.corps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Jambes recollées au torse (18/10) : les animations 3e personne de TaCZ (Player Animator) penchent et décalent le torse
 * (course : 8 à 11° vers l'avant, jusqu'à 9° de côté) autour de son pivot, au cou, sans bouger les jambes. Le bas du torse
 * glissait de 2 à 3 pixels loin du haut des cuisses : « le haut de la jambe se détache du corps ». Le torse est relevé juste
 * avant Player Animator ({@link #avant}), puis, juste après, chaque jambe est déplacée du chemin qu'a fait sa hanche sur
 * le torse ({@link #recoller}) : ses rotations et son pas, eux, ne changent pas. Seulement debout ou accroupi (allongé,
 * à terre, à la nage : rien), et seulement si le torse a bougé. Armures et corps de CaraCrea recopient ces positions.
 */
public final class Hanches {
   /** Hanche sur le torse : sous le pivot du torse, à la place du pivot de chaque jambe au repos (pixels). */
   private static final float HANCHE_X = 1.9F, HANCHE_Y = 12.0F;
   private static boolean releve;
   private static float x, y, z, rx, ry, rz;

   private Hanches() {
   }

   /** Torse tel que Minecraft l'a posé, avant Player Animator. */
   public static void avant(ModelPart torse) {
      x = torse.x;
      y = torse.y;
      z = torse.z;
      rx = torse.xRot;
      ry = torse.yRot;
      rz = torse.zRot;
      releve = true;
   }

   /** Après Player Animator : chaque jambe suit sa hanche. */
   public static void recoller(LivingEntity entite, ModelPart torse, ModelPart droite, ModelPart gauche) {
      if (!releve) return;
      releve = false;
      if (!(entite instanceof Player joueur) || !debout(joueur)) return;
      if (torse.x == x && torse.y == y && torse.z == z && torse.xRot == rx && torse.yRot == ry && torse.zRot == rz) return;
      decaler(droite, -HANCHE_X, torse);
      decaler(gauche, HANCHE_X, torse);
   }

   private static void decaler(ModelPart jambe, float hancheX, ModelPart torse) {
      Vector3f apres = hanche(hancheX, torse.x, torse.y, torse.z, torse.xRot, torse.yRot, torse.zRot);
      Vector3f avant = hanche(hancheX, x, y, z, rx, ry, rz);
      jambe.x += apres.x - avant.x;
      jambe.y += apres.y - avant.y;
      jambe.z += apres.z - avant.z;
   }

   /** Position de la hanche, comme ModelPart.translateAndRotate place le torse (rotation Z, Y puis X). */
   private static Vector3f hanche(float hancheX, float px, float py, float pz, float xRot, float yRot, float zRot) {
      return new Vector3f(hancheX, HANCHE_Y, 0.0F).rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot)).add(px, py, pz);
   }

   private static boolean debout(Player joueur) {
      Pose pose = joueur.getPose();
      if (pose != Pose.STANDING && pose != Pose.CROUCHING) return false;
      if (joueur.isVisuallySwimming() || joueur.isFallFlying() || joueur.isSleeping()) return false;
      return joueur.getSwimAmount(Minecraft.getInstance().getFrameTime()) < 0.01F;
   }
}
