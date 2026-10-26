package fr.heralisz.bunker.serveur.salle;

import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.mojang.logging.LogUtils;
import fr.heralisz.bunker.keycard.KeycardDoors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sortie forcée à la reconnexion (demande des propriétaires, 25/10) : un joueur qui s'est déconnecté dans une salle à
 * carte (ou dans l'encadrement d'une de ses portes) est remis, à sa reconnexion, juste devant la porte côté couloir, par
 * une vraie téléportation, et la porte se referme derrière lui. Avant : se déconnecter dans la salle, attendre la recharge
 * du loot (un déconnecté ne compte pas), puis se reconnecter dedans redonnait le loot sans carte.
 * <p>La règle « un déconnecté ne compte pas » ne change pas : pendant son absence la porte se ferme et la recharge part.
 * Après un redémarrage, même chose (la boîte des salles est gardée avec le monde). Salle retirée entre-temps : plus rien à
 * faire. Joueur mort (déconnexion en combat), créatif ou spectateur : rien. Joueur à terre : sorti aussi.</p>
 * <p>Place choisie : devant une porte liée, du côté qui n'est pas dans la salle, à 1,5 bloc au moins de l'encadrement (la
 * porte ne reste pas tenue ouverte pour lui), sol solide, place libre, sans liquide, hors de toute salle ; sinon devant une
 * face de la boîte ; sinon au-dessus, à la surface.</p>
 */
final class SortieSalles {
   private static final Logger LOG = LogUtils.getLogger();
   private static final int[] LATERAL = {0, -1, 1, -2, 2};
   private static final int[] HAUTEUR = {0, 1, -1, 2, -2};

   private SortieSalles() {}

   static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator() || p.isCreative()) return;
      ServerLevel l = p.serverLevel();
      SallesMonde m = SallesMonde.get(l);
      if (m.salles.isEmpty()) return;
      SallesMonde.Salle s = m.salleA(p.getX(), p.getY(), p.getZ());
      if (s == null) s = dansUnEncadrement(l, m, p);
      if (s == null) return;
      try {
         Vec3[] place = place(l, m, s, p);
         if (place == null) {
            LOG.warn("[salles] {} reconnecté dans la salle {} : aucune place sûre dehors trouvée, laissé sur place",
               p.getGameProfile().getName(), s.nom);
            return;
         }
         p.stopRiding();
         p.teleportTo(l, place[0].x, place[0].y, place[0].z, (float) place[1].x, p.getXRot());
         p.resetFallDistance();
         p.displayClientMessage(Component.translatableWithFallback("heralisz_bunker.salle.sortie",
            "Tu t'étais déconnecté dans une salle à carte : te voici devant sa porte.").withStyle(ChatFormatting.YELLOW), false);
         LOG.info("[salles] {} reconnecté dans la salle {} : sorti en {} {} {}", p.getGameProfile().getName(), s.nom,
            Mth.floor(place[0].x), Mth.floor(place[0].y), Mth.floor(place[0].z));
         m.apresSortie(l, s);
      } catch (RuntimeException ex) {
         LOG.error("[salles] sortie de {} hors de la salle {} impossible", p.getGameProfile().getName(), s.nom, ex);
      }
   }

   /** Salle dont le joueur occupe l'encadrement d'une porte (pièces chargées au besoin), ou null. */
   @Nullable
   private static SallesMonde.Salle dansUnEncadrement(ServerLevel l, SallesMonde m, ServerPlayer p) {
      AABB bb = p.getBoundingBox();
      for (SallesMonde.Salle s : m.salles.values()) {
         if (!s.boite().inflate(4).intersects(bb)) continue;
         for (AABB a : portes(l, s)) {
            if (a.inflate(0.3, 0, 0.3).intersects(bb)) return s;
         }
      }
      return null;
   }

   /** Boîtes des portes liées à la salle (pièces de chaque porte), secteurs chargés au besoin (rare : à la connexion). */
   private static List<AABB> portes(ServerLevel l, SallesMonde.Salle s) {
      List<AABB> out = new ArrayList<>();
      for (BlockPos c : s.portes) {
         l.getChunkAt(c);
         if (!(l.getBlockEntity(c) instanceof DoorBlockEntity d)) continue;
         AABB a = null;
         for (BlockPos q : KeycardDoors.parts(d)) a = a == null ? new AABB(q) : a.minmax(new AABB(q));
         if (a != null) out.add(a);
      }
      return out;
   }

   /** {position, (regard, 0, 0)} où poser le joueur, ou null. */
   @Nullable
   private static Vec3[] place(ServerLevel l, SallesMonde m, SallesMonde.Salle s, ServerPlayer p) {
      AABB boite = s.boite();
      Vec3 centre = boite.getCenter();
      // 1. devant une porte liée, côté couloir
      for (AABB d : portes(l, s)) {
         boolean axeX = d.getXsize() <= d.getZsize();                 // la porte est mince le long de X : on sort en X
         Vec3 dc = d.getCenter();
         int meilleur = 0;
         double loin = -1;
         for (int sens = -1; sens <= 1; sens += 2) {
            double bord = sens > 0 ? (axeX ? d.maxX : d.maxZ) : (axeX ? d.minX : d.minZ);
            double x = axeX ? bord + sens * 1.5 : dc.x, z = axeX ? dc.z : bord + sens * 1.5;
            if (boite.contains(x, d.minY + 0.5, z)) continue;
            double dist = (x - centre.x) * (x - centre.x) + (z - centre.z) * (z - centre.z);
            if (dist > loin) {
               loin = dist;
               meilleur = sens;
            }
         }
         if (meilleur == 0) continue;                                   // porte au milieu de la salle : pas un côté sûr
         double bord = meilleur > 0 ? (axeX ? d.maxX : d.maxZ) : (axeX ? d.minX : d.minZ);
         float regard = axeX ? (meilleur > 0 ? -90F : 90F) : (meilleur > 0 ? 0F : 180F);
         for (int k = 1; k <= 4; k++) {
            for (int lat : LATERAL) {
               double x = axeX ? bord + meilleur * (k + 0.5) : Math.floor(dc.x) + 0.5 + lat;
               double z = axeX ? Math.floor(dc.z) + 0.5 + lat : bord + meilleur * (k + 0.5);
               Vec3 v = sur(l, m, p, x, Mth.floor(d.minY), z);
               if (v != null) return new Vec3[]{v, new Vec3(regard, 0, 0)};
            }
         }
      }
      // 2. devant une face de la boîte
      for (Direction dir : Direction.Plane.HORIZONTAL) {
         for (int k = 1; k <= 3; k++) {
            double x = dir.getStepX() == 0 ? Math.floor(centre.x) + 0.5 : dir.getStepX() > 0 ? boite.maxX + k - 0.5 : boite.minX - k + 0.5;
            double z = dir.getStepZ() == 0 ? Math.floor(centre.z) + 0.5 : dir.getStepZ() > 0 ? boite.maxZ + k - 0.5 : boite.minZ - k + 0.5;
            Vec3 v = sur(l, m, p, x, s.min.getY(), z);
            if (v != null) return new Vec3[]{v, new Vec3(dir.toYRot(), 0, 0)};
         }
      }
      // 3. au-dessus, à la surface
      BlockPos haut = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(centre));
      if (haut.getY() > s.max.getY()) {
         Vec3 v = sur(l, m, p, haut.getX() + 0.5, haut.getY(), haut.getZ() + 0.5);
         if (v != null) return new Vec3[]{v, new Vec3(p.getYRot(), 0, 0)};
      }
      return null;
   }

   /** Position sûre (pieds) autour de cette colonne, à quelques blocs de y, ou null. */
   @Nullable
   private static Vec3 sur(ServerLevel l, SallesMonde m, ServerPlayer p, double x, int y0, double z) {
      l.getChunkAt(BlockPos.containing(x, y0, z));
      for (int dy : HAUTEUR) {
         int y = y0 + dy;
         if (y <= l.getMinBuildHeight() || y >= l.getMaxBuildHeight() - 2) continue;
         BlockPos pieds = BlockPos.containing(x, y, z), dessous = pieds.below();
         if (!l.getBlockState(dessous).isFaceSturdy(l, dessous, Direction.UP)) continue;
         Vec3 v = new Vec3(x, y, z);
         AABB bb = p.getDimensions(Pose.STANDING).makeBoundingBox(v);
         if (!l.noCollision(p, bb) || l.containsAnyLiquid(bb.inflate(0.1)) || !l.getWorldBorder().isWithinBounds(bb)) continue;
         if (m.salleA(x, y, z) != null || m.salleA(x, y + 1.0, z) != null) continue;
         return v;
      }
      return null;
   }
}
