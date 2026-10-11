package fr.heralisz.n07.api;

/**
 * Ce que chaque boss fournit au HUD et aux cinématiques (communs à tous les boss HeralisZ).
 * Implémenté par l'entité boss (côté client comme serveur ; les valeurs d'affichage sont lues côté client).
 */
public interface BossInfo {
   int SPAWN = 0, PHASE2 = 1, PHASE3 = 2, DEATH = 3;

   int phase();

   /** PV « réels » affichés (échelle des dégâts d'armes). */
   int virtualMax();

   /** Nom en capitales pour la barre et les titres : clé de traduction (un texte qui n'en est pas une s'affiche tel quel). */
   String hudName();

   /** Couleurs de la jauge (haut, bas) selon la phase ; couleur du nom. */
   int hudTop(int phase);

   int hudBottom(int phase);

   int hudNameColor();

   /** Couleur des lueurs du HUD / des titres de cinématique. */
   int accentColor();

   /** Orientation de référence pour cadrer les cinématiques. */
   float cinematicYaw();

   /** Plans de caméra : {t (0..1), avant, droite, hauteur, hauteur visée} relatifs aux pieds du boss. */
   float[][] cinematicKeys(int kind);

   /** {bandeau, titre, sous-titre} de la cinématique. */
   String[] cinematicTitles(int kind);

   /** Moment (0..1) où le titre apparaît. */
   float cinematicTitleAt(int kind);

   /** Point d'ancrage des plans de caméra (par défaut les pieds du boss). */
   default net.minecraft.world.phys.Vec3 cinematicAnchor() {
      return ((net.minecraft.world.entity.Entity) this).position();
   }

   /** Musique d'ambiance du boss (jouée très bas tant qu'on est près de lui), null : aucune. */
   default net.minecraft.sounds.SoundEvent music() {
      return null;
   }

   /** Endormi dans son donjon (statue en attente du rituel) : ni barre de vie, ni musique. */
   default boolean dormant() {
      return false;
   }

   /** true : la caméra reste ancrée au point de départ (le boss peut s'enfoncer/voler sans l'entraîner). */
   default boolean cinematicAnchored(int kind) {
      return kind == SPAWN || kind == DEATH;
   }
}
