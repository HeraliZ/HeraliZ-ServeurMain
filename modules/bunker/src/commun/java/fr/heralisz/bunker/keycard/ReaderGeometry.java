package fr.heralisz.bunker.keycard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Géométrie du lecteur (doit correspondre au modèle block/keycard/card_reader.json, orienté nord) : la tête de lecture
 * et sa rainure verticale sont à droite du joueur qui fait face au lecteur. Sert à la vérification de distance (serveur)
 * et au trajet de la carte / de la caméra (client).
 */
public final class ReaderGeometry {
   /** Rainure : centre à 3/16 du centre du bloc vers la droite du joueur, de y = 1.9/16 à 13.7/16, fond à 12.2/16. */
   public static final double SLOT_OFFSET = 3.0 / 16, SLOT_TOP = 13.7 / 16, SLOT_BOTTOM = 1.9 / 16, GROOVE_DEPTH = 4.2 / 16;
   /** Écran : centre à 1.5/16 vers la gauche du joueur, y = 11.9/16, face avant à 12.8/16. */
   public static final double SCREEN_OFFSET = 1.5 / 16, SCREEN_Y = 11.9 / 16, SCREEN_DEPTH = 4.8 / 16;
   /** Carte dessinée pendant la cinématique : modèle 16 x 10 px mis à l'échelle. */
   public static final double CARD_SCALE = 0.34, CARD_LONG = CARD_SCALE, CARD_SHORT = CARD_SCALE * 10 / 16;

   public static Vec3 front(Direction facing) {
      return Vec3.atLowerCornerOf(facing.getNormal());
   }

   /** Côté de la rainure (droite du joueur qui regarde le lecteur). */
   public static Vec3 slotSide(Direction facing) {
      return Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal());
   }

   /** Point du fond de la rainure ; along = 0 en haut, 1 en bas. */
   public static Vec3 slot(BlockPos pos, Direction facing, double along) {
      double y = SLOT_TOP + (SLOT_BOTTOM - SLOT_TOP) * along;
      return new Vec3(pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5)
         .add(slotSide(facing).scale(SLOT_OFFSET))
         .subtract(front(facing).scale(GROOVE_DEPTH));
   }

   public static Vec3 screen(BlockPos pos, Direction facing) {
      return new Vec3(pos.getX() + 0.5, pos.getY() + SCREEN_Y, pos.getZ() + 0.5)
         .subtract(slotSide(facing).scale(SCREEN_OFFSET))
         .subtract(front(facing).scale(SCREEN_DEPTH));
   }

   /** Il faut être juste devant le lecteur : dans le bloc devant lui (ou tout contre), à hauteur d'homme. */
   public static boolean inReach(Player player, BlockPos pos, Direction facing) {
      Vec3 p = player.position();
      Vec3 center = Vec3.atCenterOf(pos);
      Vec3 f = front(facing);
      double ahead = p.subtract(center).dot(f);                 // distance devant la face du lecteur
      double lateral = Math.abs(p.subtract(center).dot(slotSide(facing)));
      double dy = pos.getY() - p.y;
      return ahead > 0.2 && ahead < 1.45 && lateral < 0.9 && dy > -0.5 && dy < 2.0;
   }

   private ReaderGeometry() {}
}
