package fr.heralisz.mammoth.entity;

/** Entité qui porte une cinématique (plans de caméra, titres, couleur) : Ymirak, ou le Gardien qui s'arrache le cœur. */
public interface CineSource {
   /** {avancement, devant, à droite, hauteur, hauteur visée[, coupe franche[, visée devant, visée à droite]]}. */
   float[][] cinematicKeys(int kind);

   String[] cinematicTitles(int kind);

   float cinematicTitleAt(int kind);

   int accentColor();

   /** La caméra suit l'entité (sinon elle reste ancrée là où la cinématique a commencé). */
   default boolean cinematicFollows(int kind) {
      return false;
   }

   /** Axe « devant » des plans (null : l'orientation de l'entité) : sert à toujours filmer depuis le côté ouvert de la salle. */
   default net.minecraft.world.phys.Vec3 cinematicFacing(int kind) {
      return null;
   }
}
