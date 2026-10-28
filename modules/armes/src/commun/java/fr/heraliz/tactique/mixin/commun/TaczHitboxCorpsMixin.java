package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.HitboxHelper;
import com.tacz.guns.util.block.BlockRayTrace;
import fr.heraliz.tactique.hitbox.HitboxCorps;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Balles de TaCZ contre un joueur : la hitbox « en morceaux » (fr.heraliz.tactique.hitbox.HitboxCorps) remplace la
 * boîte droite de Minecraft. La balle ne touche que là où le corps est vraiment (debout, accroupi, allongé, à terre,
 * assis) ; tir à la tête = morceau « tête ». La place du joueur reste celle que TaCZ corrige pour le ping du tireur
 * (HitboxHelper.getFixedBoundingBox, train compris).
 * <p>
 * 25/10 (balles qui touchent à l'écran du tireur sans rien faire) :
 * <ul>
 *    <li>la boîte de TaCZ est la place du joueur « balayée » sur un tick de son mouvement (TaCZ l'étire de sa vitesse :
 *    le jeu du tireur montre les autres joueurs entre deux positions). Avant, le corps était posé au bas de cette boîte
 *    étirée : en tombant (fin d'un saut, chute), il était posé trop bas, et la tolérance de TaCZ sur le mouvement était
 *    perdue. Le corps est maintenant essayé au début, au milieu et à la fin de ce balayage (la touche la plus proche du
 *    tireur compte), sans jamais sortir de la boîte de TaCZ ;</li>
 *    <li>TaCZ ne regarde que les entités dont la boîte ACTUELLE est à moins d'1 bloc du trajet de la balle, puis teste
 *    leur place corrigée du ping, qui peut être bien plus loin (ping + 3 ticks de mouvement en arrière) : un joueur qui
 *    court de côté (0,28 bloc par tick, 0,5 en sautant) n'était même pas essayé dès 100 ms de ping, ou presque à tout
 *    ping en sautant. Le rayon de recherche suit maintenant ce recul ; le test précis (boîte de TaCZ, puis morceaux) ne
 *    change pas : aucun joueur ne peut être touché ailleurs que sur sa place corrigée.</li>
 * </ul>
 * 27/10 (tirs à travers une porte ou un mur) : un morceau peut dépasser du corps de plus d'épaisseur qu'une porte ou un mur
 * de base (bras tendus avec une arme : 0,7 bloc devant le joueur ; allongé : tête et pieds à 0,9 bloc ; une porte ne fait
 * que 0,125 bloc). Le tireur de l'autre côté touchait ce morceau sans traverser la porte, et blessait le joueur caché
 * derrière. Un morceau n'est plus touché si un bloc solide (même calcul que la balle : BlockRayTrace) est entre le point
 * touché et le corps (milieu de la place du joueur, à la hauteur du point) : seul ce qui est du même côté que le corps
 * compte. Un bras qui dépasse d'une ouverture (porte ouverte, fenêtre, coin de mur) reste touché.
 * <p>
 * Cible : EntityUtil de TaCZ 1.1.8 (getHitResult : null = manqué ; findEntityOnPath / findEntitiesOnPath : recherche des
 * entités). Seul le serveur décide. Noms de TaCZ : remap = false. require = 0 : si TaCZ change ces fonctions, son calcul
 * revient, rien ne plante.
 */
@Mixin(value = EntityUtil.class, remap = false)
public abstract class TaczHitboxCorpsMixin {
   /** Recherche d'origine de TaCZ : boîte actuelle à moins d'1 bloc du trajet. */
   @Unique
   private static final double HERALIZ$RECHERCHE_TACZ = 1.0D;
   /** Plus vite qu'un joueur à pied (sprint 0,28, saut en courant jusqu'à 0,5 bloc par tick), en blocs par tick. */
   @Unique
   private static final double HERALIZ$VITESSE_MAX = 0.6D;
   /** Ticks de recul de TaCZ en plus du ping : position gardée du tick d'avant, décalage ServerHitboxOffset - 5 (-2). */
   @Unique
   private static final int HERALIZ$RECUL_TACZ = 3;
   /** Rayon de recherche maximal (TaCZ garde au plus 1 s de positions). */
   @Unique
   private static final double HERALIZ$RECHERCHE_MAX = 16.0D;
   /** Rayon de recherche de la balle en cours (posé au début de la recherche, lu juste après ; fil du serveur). */
   @Unique
   private static double heraliz$recherche = HERALIZ$RECHERCHE_TACZ;

   @Inject(
      method = "findEntityOnPath(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;",
      at = @At("HEAD"),
      require = 0
   )
   private static void heraliz$rayonUne(Projectile balle, Vec3 de, Vec3 a, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
      heraliz$recherche = heraliz$rayonRecherche(balle.getOwner());
   }

   @Inject(
      method = "findEntitiesOnPath(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/List;",
      at = @At("HEAD"),
      require = 0
   )
   private static void heraliz$rayonPlusieurs(Projectile balle, Vec3 de, Vec3 a, CallbackInfoReturnable<List<EntityKineticBullet.EntityResult>> cir) {
      heraliz$recherche = heraliz$rayonRecherche(balle.getOwner());
   }

   /** Le seul 1.0 de ces deux fonctions (vérifié dans TaCZ 1.1.8) : {@code inflate(1.0D)} de la recherche des entités. */
   @ModifyConstant(
      method = {
         "findEntityOnPath(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;",
         "findEntitiesOnPath(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/List;"
      },
      constant = @Constant(doubleValue = 1.0D),
      require = 0
   )
   private static double heraliz$elargirRecherche(double origine) {
      return Math.max(origine, heraliz$recherche);
   }

   /** Recul de la place corrigée par TaCZ (ping du tireur + 3 ticks) à la vitesse d'un joueur, plus une marge. */
   @Unique
   private static double heraliz$rayonRecherche(Entity tireur) {
      int ticks = 0;
      if (tireur instanceof ServerPlayer sp && OtherConfig.SERVER_HITBOX_LATENCY_FIX.get()) {
         ticks = Mth.clamp(Mth.floor(sp.latency / 1000.0D * 20.0D + 0.5D), 0, 20);
      }
      return Math.min(HERALIZ$RECHERCHE_MAX, Math.max(HERALIZ$RECHERCHE_TACZ, 0.5D + (ticks + HERALIZ$RECUL_TACZ) * HERALIZ$VITESSE_MAX));
   }

   @Inject(
      method = "getHitResult(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private static void heraliz$hitboxCorps(Projectile balle, Entity cible, Vec3 de, Vec3 a, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
      if (!HitboxCorps.actif || !(cible instanceof Player joueur) || cible.level().isClientSide || joueur.isSleeping()) {
         return;
      }
      Entity tireur = balle.getOwner();
      AABB boite = HitboxHelper.getFixedBoundingBox(cible, tireur);
      // tri rapide : les morceaux ne sortent jamais de la boîte de TaCZ agrandie de cette marge (bras tendus devant avec
      // une arme ; allongé ou à terre : corps couché sur près d'un bloc de chaque côté ; assis : jambes devant)
      double marge = joueur.getPose() == Pose.SWIMMING ? 1.7D : joueur.isPassenger() ? 1.1D : 0.8D;
      AABB large = boite.inflate(marge);
      if (!large.contains(de) && large.clip(de, a).isEmpty()) {
         cir.setReturnValue(null);
         return;
      }
      // balayage de TaCZ (boîte étirée de la vitesse) : ce qu'elle a en plus de la boîte du joueur, dans le sens du
      // mouvement (une boîte du train, posée par HeraliZ Utils, n'est pas étirée : un seul essai, au milieu)
      Vec3 v = heraliz$vitesse(joueur, tireur);
      double largeur = joueur.getBbWidth();
      double ex = Math.max(0.0D, boite.getXsize() - largeur);
      double ez = Math.max(0.0D, boite.getZsize() - largeur);
      double hauteur = joueur.getBbHeight() + (joueur.isCrouching() ? 0.0D : 0.0625D);
      double ey = Mth.clamp(boite.getYsize() - hauteur, 0.0D, 4.0D);
      double cx = (boite.minX + boite.maxX) / 2.0D;
      double cz = (boite.minZ + boite.maxZ) / 2.0D;
      double x0 = cx - Math.copySign(ex / 2.0D, v.x), x1 = cx + Math.copySign(ex / 2.0D, v.x);
      double z0 = cz - Math.copySign(ez / 2.0D, v.z), z1 = cz + Math.copySign(ez / 2.0D, v.z);
      double y0 = v.y >= 0.0D ? boite.minY : boite.minY + ey, y1 = v.y >= 0.0D ? boite.minY + ey : boite.minY;
      int essais = ex + ey + ez < 0.02D ? 1 : 3;
      HitboxCorps.Touche meilleure = null;
      for (int i = 0; i < essais; i++) {
         double k = essais == 1 ? 0.5D : i * 0.5D;
         Vec3 pieds = new Vec3(Mth.lerp(k, x0, x1), Mth.lerp(k, y0, y1), Mth.lerp(k, z0, z1));
         HitboxCorps.Touche t = HitboxCorps.toucher(HitboxCorps.morceaux(joueur, pieds, 1.0F), de, a);
         if (t != null && heraliz$derriereUnMur(balle, joueur, pieds, de.add(a.subtract(de).scale(t.t())))) {
            t = null;   // morceau qui dépasse d'une porte ou d'un mur, corps de l'autre côté
         }
         if (t != null && (meilleure == null || t.t() < meilleure.t())) {
            meilleure = t;
         }
      }
      cir.setReturnValue(meilleure == null ? null
         : new EntityKineticBullet.EntityResult(cible, de.add(a.subtract(de).scale(meilleure.t())), meilleure.morceau().tete()));
   }

   /**
    * Un bloc solide pour une balle est-il entre le point touché et le corps du joueur (milieu de sa place {@code pieds}, à la
    * hauteur du point, ramenée dans sa taille) ? Un seul rayon, seulement quand un morceau est touché.
    */
   @Unique
   private static boolean heraliz$derriereUnMur(Projectile balle, Player joueur, Vec3 pieds, Vec3 point) {
      double haut = joueur.getBbHeight();
      Vec3 corps = new Vec3(pieds.x, Mth.clamp(point.y, pieds.y + 0.1D, pieds.y + Math.max(0.2D, haut - 0.1D)), pieds.z);
      double d2 = corps.distanceToSqr(point);
      if (d2 < 1.0E-4D) {
         return false;
      }
      BlockHitResult r = BlockRayTrace.rayTraceBlocks(joueur.level(), new ClipContext(point, corps, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, balle));
      return r.getType() != HitResult.Type.MISS && r.getLocation().distanceToSqr(point) < d2 - 1.0E-6D;
   }

   /** Sens du mouvement que TaCZ a utilisé pour étirer la boîte (même calcul que HitboxHelper.getFixedBoundingBox). */
   @Unique
   private static Vec3 heraliz$vitesse(Player joueur, Entity tireur) {
      if (OtherConfig.SERVER_HITBOX_LATENCY_FIX.get() && joueur instanceof ServerPlayer && tireur instanceof ServerPlayer sp) {
         return HitboxHelper.getVelocity(joueur, Mth.floor(sp.latency / 1000.0D * 20.0D + 0.5D));
      }
      return new Vec3(joueur.getX() - joueur.xo, joueur.getY() - joueur.yo, joueur.getZ() - joueur.zo);
   }
}
