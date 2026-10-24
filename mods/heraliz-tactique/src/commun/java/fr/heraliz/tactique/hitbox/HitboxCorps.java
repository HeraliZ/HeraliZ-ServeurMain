package fr.heraliz.tactique.hitbox;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hitbox « en morceaux » d'un joueur : tête, torse, deux bras, deux jambes, posés comme le modèle du joueur de Minecraft
 * (mêmes cubes, mêmes pivots, même échelle 0,9375, mêmes rotations du rendu : corps tourné selon {@code yBodyRot},
 * accroupi, allongé / nage / à terre par {@code swimAmount}, assis en véhicule, arme TaCZ tenue à deux mains devant).
 * Un demi-pixel de plus autour de la tête (calque du chapeau) et un quart autour du reste (calque de la veste).
 * <p>
 * Calcul commun : le serveur s'en sert pour les balles de TaCZ (mixin TaczHitboxCorpsMixin), le jeu du joueur pour
 * l'afficher avec F3+B (client/hitbox/HitboxCorpsRendu). Les gestes animés (pas, balancement des bras, animations des
 * gunpacks) ne sont pas connus du serveur : bras et jambes restent dans leur pose de base.
 */
public final class HitboxCorps {
   /** Coupe tout (balles comme avant, avec la boîte de Minecraft). */
   public static volatile boolean actif = true;

   private static final float ECHELLE = 0.9375F;

   /** Un morceau : cube en pixels du modèle (x0, y0, z0, x1, y1, z1) et sa matrice vers le monde. */
   public record Morceau(String nom, boolean tete, Matrix4f versMonde, float[] cube) {
   }

   private HitboxCorps() {
   }

   /** Morceaux du joueur, posé à {@code pos} (ses pieds), au moment {@code partiel} (1 côté serveur). */
   public static List<Morceau> morceaux(Player p, Vec3 pos, float partiel) {
      float corpsYaw = Mth.rotLerp(partiel, p.yBodyRotO, p.yBodyRot);
      float teteYaw = Mth.rotLerp(partiel, p.yHeadRotO, p.yHeadRot);
      float tangage = Mth.lerp(partiel, p.xRotO, p.getXRot());
      float nage = p.getSwimAmount(partiel);
      boolean accroupi = p.isCrouching();
      boolean nageVisible = p.getPose() == Pose.SWIMMING;
      boolean assis = p.isPassenger() && p.getVehicle() != null && p.getVehicle().shouldRiderSit();

      Matrix4f m = new Matrix4f().translate((float) pos.x, (float) pos.y, (float) pos.z);
      if (accroupi) m.translate(0.0F, -0.125F, 0.0F);
      m.rotateY((float) Math.toRadians(180.0F - corpsYaw));
      if (nage > 0.0F) {
         float cible = p.isInWater() ? -90.0F - tangage : -90.0F;
         m.rotateX((float) Math.toRadians(Mth.lerp(nage, 0.0F, cible)));
         if (nageVisible) m.translate(0.0F, -1.0F, 0.3F);
      }
      m.scale(-1.0F, -1.0F, 1.0F).scale(ECHELLE).translate(0.0F, -1.501F, 0.0F);
      if (nageVisible && nage > 0.5F && !p.isInWater() && !aTerre(p)) {
         return allonge(m);
      }

      // tête (comme HumanoidModel.setupAnim)
      float teteX = (float) Math.toRadians(tangage);
      float teteY = (float) Math.toRadians(Mth.wrapDegrees(teteYaw - corpsYaw));
      if (nage > 0.0F) teteX = Mth.lerp(nage, teteX, nageVisible ? (float) (-Math.PI / 4) : teteX);

      List<Morceau> liste = new ArrayList<>(6);
      float yTete = accroupi ? 4.2F : 0.0F;
      liste.add(new Morceau("tete", true, part(m, 0, yTete, 0, teteX, teteY), cube(-4, -8, -4, 8, 8, 8, 0.5F)));
      liste.add(new Morceau("torse", false, part(m, 0, accroupi ? 3.2F : 0, 0, accroupi ? 0.5F : 0, 0), cube(-4, 0, -2, 8, 12, 4, 0.25F)));

      float brasY = accroupi ? 5.2F : 2.0F;
      float brasDX = accroupi ? 0.4F : 0.0F, brasGX = brasDX, brasDY = 0.0F, brasGY = 0.0F;
      if (assis) {
         brasDX += (float) (-Math.PI / 5);
         brasGX += (float) (-Math.PI / 5);
      }
      if (armeTacz(p.getMainHandItem()) && nage <= 0.0F) {
         // tenue à deux mains, visée devant (pose « arc » de Minecraft, celle que TaCZ montre par défaut)
         brasDX = (float) (-Math.PI / 2) + teteX;
         brasGX = brasDX;
         brasDY = -0.1F + teteY;
         brasGY = 0.5F + teteY;
      }
      liste.add(new Morceau("bras_droit", false, part(m, -5, brasY, 0, brasDX, brasDY), cube(-3, -2, -2, 4, 12, 4, 0.25F)));
      liste.add(new Morceau("bras_gauche", false, part(m, 5, brasY, 0, brasGX, brasGY), cube(-1, -2, -2, 4, 12, 4, 0.25F)));

      float jambeY = accroupi ? 12.2F : 12.0F, jambeZ = accroupi ? 4.0F : 0.0F;
      float jambeX = 0.0F, jambeDY = 0.0F, jambeGY = 0.0F;
      if (assis) {
         jambeX = -1.4137167F;
         jambeDY = (float) (Math.PI / 10);
         jambeGY = (float) (-Math.PI / 10);
      }
      liste.add(new Morceau("jambe_droite", false, part(m, -1.9F, jambeY, jambeZ, jambeX, jambeDY), cube(-2, 0, -2, 4, 12, 4, 0.25F)));
      liste.add(new Morceau("jambe_gauche", false, part(m, 1.9F, jambeY, jambeZ, jambeX, jambeGY), cube(-2, 0, -2, 4, 12, 4, 0.25F)));
      return liste;
   }

   /**
    * Allongé (« ramper » de TaCZ, avec ou sans arme) : le corps couché de Minecraft, puis la pose « lie » de TaCZ
    * ({@code tacz:rifle_default.player_animation}, image de repos) telle que Player Animator la pose sur le modèle :
    * position = pivot de Minecraft + (x, -y, z) du fichier, rotations X, Y, Z du fichier, corps entier avancé de 2 px.
    * Tête relevée, bras tendus devant ; torse et jambes droits (24/10, client/allonge/CorpsAllonge chez le joueur).
    */
   private static List<Morceau> allonge(Matrix4f m) {
      Matrix4f corps = new Matrix4f(m).translate(0.0F, 0.0F, -2.0F / 16.0F);
      List<Morceau> liste = new ArrayList<>(6);
      liste.add(new Morceau("tete", true, lie(corps, 0, 0, 0, -77.5F, 0, 0), cube(-4, -8, -4, 8, 8, 8, 0.5F)));
      liste.add(new Morceau("torse", false, lie(corps, 0, 0, 0, 0, 0, 0), cube(-4, 0, -2, 8, 12, 4, 0.25F)));
      liste.add(new Morceau("bras_droit", false, lie(corps, -5 + 2.0325F, 2 - 0.6F, -0.9F, 179.9F, 0, 0), cube(-3, -2, -2, 4, 12, 4, 0.25F)));
      liste.add(new Morceau("bras_gauche", false, lie(corps, 5 - 2.9675F, 2 - 7.6F, -1.9F, 167.48F, -3.32F, -41.12F), cube(-1, -2, -2, 4, 12, 4, 0.25F)));
      liste.add(new Morceau("jambe_droite", false, lie(corps, -1.9F, 12, 0, 0, 0, 0), cube(-2, 0, -2, 4, 12, 4, 0.25F)));
      liste.add(new Morceau("jambe_gauche", false, lie(corps, 1.9F, 12, 0, 0, 0, 0), cube(-2, 0, -2, 4, 12, 4, 0.25F)));
      return liste;
   }

   /** Morceau posé par Player Animator : pivot (pixels), rotations en degrés appliquées Z, Y puis X. */
   private static Matrix4f lie(Matrix4f base, float x, float y, float z, float rx, float ry, float rz) {
      return new Matrix4f(base).translate(x / 16.0F, y / 16.0F, z / 16.0F)
         .rotateZ((float) Math.toRadians(rz)).rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx))
         .scale(1.0F / 16.0F);
   }

   /**
    * À terre (Réanimation, par réflexion) : pose couchée de Minecraft, pas celle de TaCZ. HeraliZ (opti) : par le lien
    * mis en cache de CompatReanimation (avant : Class.forName et getMethod à chaque balle près d'un joueur allongé, et à
    * chaque image avec F3+B).
    */
   private static boolean aTerre(Player p) {
      return fr.heraliz.tactique.allonge.CompatReanimation.aTerre(p);
   }

   /** Premier morceau traversé par le segment {@code de} → {@code a} : fraction du trajet (0 à 1) et morceau, ou null. */
   public static Touche toucher(List<Morceau> morceaux, Vec3 de, Vec3 a) {
      Touche meilleure = null;
      for (Morceau mo : morceaux) {
         Matrix4f inv = new Matrix4f(mo.versMonde()).invert();
         Vector3f d = inv.transformPosition(new Vector3f((float) de.x, (float) de.y, (float) de.z));
         Vector3f f = inv.transformPosition(new Vector3f((float) a.x, (float) a.y, (float) a.z));
         double t = rayonBoite(d, f, mo.cube());
         if (t >= 0.0 && (meilleure == null || t < meilleure.t())) meilleure = new Touche(mo, t);
      }
      return meilleure;
   }

   public record Touche(Morceau morceau, double t) {
   }

   static boolean armeTacz(ItemStack objet) {
      if (objet.isEmpty()) return false;
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(objet.getItem());
      return id != null && "tacz".equals(id.getNamespace());
   }

   /** Pivot du morceau (pixels) puis rotations Z, Y, X comme ModelPart.translateAndRotate, sur le pixel → bloc. */
   private static Matrix4f part(Matrix4f base, float x, float y, float z, float rx, float ry) {
      return new Matrix4f(base).translate(x / 16.0F, y / 16.0F, z / 16.0F).rotateY(ry).rotateX(rx).scale(1.0F / 16.0F);
   }

   private static float[] cube(float x, float y, float z, float l, float h, float p, float marge) {
      return new float[]{x - marge, y - marge, z - marge, x + l + marge, y + h + marge, z + p + marge};
   }

   /** Méthode des dalles dans le repère du cube : fraction d'entrée, 0 si le départ est dedans, -1 si manqué. */
   private static double rayonBoite(Vector3f d, Vector3f f, float[] c) {
      double tMin = 0.0, tMax = 1.0;
      float[] o = {d.x, d.y, d.z};
      float[] v = {f.x - d.x, f.y - d.y, f.z - d.z};
      for (int i = 0; i < 3; i++) {
         if (Math.abs(v[i]) < 1.0E-7F) {
            if (o[i] < c[i] || o[i] > c[i + 3]) return -1.0;
         } else {
            double t1 = (c[i] - o[i]) / v[i], t2 = (c[i + 3] - o[i]) / v[i];
            if (t1 > t2) { double s = t1; t1 = t2; t2 = s; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1.0;
         }
      }
      return tMin;
   }
}
