package fr.heralisz.n07.dungeon;

/**
 * Décors animés des donjons (modèles GeckoLib, geo/block/prop_&lt;id&gt;.geo.json) : ce qui fait vivre une salle de boss.
 */
public enum PropKind {
   /** Buse d'évacuation rouillée qui sort du mur et crache un jet continu de liquide radioactif (jet dessiné en direct). */
   WASTE_PIPE("waste_pipe", 4, 11),
   /** Cuve géante de matière radioactive : verre renforcé, liquide qui bouillonne, blocs de minerai en suspension, gyrophare. */
   RAD_VAT("rad_vat", 7, 12),
   /** Buse de la station de quarantaine : déverse une boue toxique olive, épaisse. */
   SLUDGE_PIPE("sludge_pipe", 4, 7),
   /** Cuve de déchets toxiques : boue trouble, débris en suspension, rouille. */
   SLUDGE_VAT("sludge_vat", 7, 8),
   /** Chevalier de pierre agenouillé, le front sur la garde de son épée (chapelle du Serment) : le modèle du Chevalier Sans Visage
    * en pierre, dans la pose de sa mort. */
   KNIGHT_STATUE("knight_statue", 5, 0),
   /** Totem du Tertre sylvestre : pieu sculpté bagué de runes, crâne de cerf, breloques qui se balancent. */
   SYLVAN_TOTEM("sylvan_totem", 5, 6),
   /** Victime enveloppée de soie, pendue à la voûte du Nid des Ruines ; elle se balance et tressaille. */
   WRAPPED_VICTIM("wrapped_victim", 5, 0),
   // ------------------------------------------------------------------ luminaires suspendus (le bloc = la source de lumière)
   /** Suspension industrielle à cage (Secteur 7, quarantaine) : grésille, clignote, s'affole quand le donjon s'éveille. */
   CAGE_LAMP("cage_lamp", 3, 13),
   /** Lustre de fer forgé à bougies (chapelle du Serment). */
   CHANDELIER("chandelier", 3, 14),
   /** Cosse de spores lumineuse tenue par une racine (Tertre sylvestre). */
   SPORE_LANTERN("spore_lantern", 3, 10),
   /** Grappe d'œufs luminescents emmaillotés de soie (Nid des Ruines). */
   BROOD_LAMP("brood_lamp", 3, 9),
   /** Cuve de confinement du Sujet N-07 : il y flotte ; bobines qui tournent sur un rail, arcs pendant le rituel, explosion à son
    * réveil, reconstruction et nouveau clone qui grandit après sa mort. */
   CONTAINMENT_TANK("containment_tank", 14, 10),
   /** Ventilateur de plafond : rotor qui tourne devant un puits de lumière (faisceau stroboscopique). */
   VENT_FAN("vent_fan", 4, 8),
   /** Ventilateur géant (Station Borée) : trois pales de cinq blocs devant le puits d'aération ; les puits de lumière passent
    * entre les pales et tournent avec elles (dessinés en direct). */
   GIANT_FAN("giant_fan", 24, 12);

   public final String id;
   /** Portée du rendu autour du bloc (le modèle déborde largement de sa case). */
   public final int renderRadius;
   public final int light;

   PropKind(String id, int renderRadius, int light) {
      this.id = id;
      this.renderRadius = renderRadius;
      this.light = light;
   }

   public String model() {
      return "prop_" + id;
   }

   /** Buse qui crache un jet (rendu en direct). */
   public boolean pipe() {
      return this == WASTE_PIPE || this == SLUDGE_PIPE;
   }

   /** Cuve à verre et liquide translucides. */
   public boolean vat() {
      return this == RAD_VAT || this == SLUDGE_VAT;
   }

   public boolean fan() {
      return this == VENT_FAN;
   }

   public boolean giantFan() {
      return this == GIANT_FAN;
   }

   /** La cuve de confinement (rendu piloté par le sanctuaire et le boss). */
   public boolean tank() {
      return this == CONTAINMENT_TANK;
   }

   /** Luminaire suspendu : se balance au bout de sa suspension, dessinée jusqu'à la voûte. */
   public boolean lamp() {
      return this == CAGE_LAMP || this == CHANDELIER || this == SPORE_LANTERN || this == BROOD_LAMP;
   }

   /** Ampoule électrique : grésille et clignote (vraie lumière coupée côté serveur). */
   public boolean electric() {
      return this == CAGE_LAMP;
   }

   /** Hauteur (px, depuis le bas du bloc) du point d'accroche du modèle, d'où part la suspension. */
   public float attach() {
      return this == CHANDELIER ? 30F : 16F;
   }

   /** Suspension : 0 chaîne, 1 fil de soie, 2 liane. */
   public int cord() {
      return this == BROOD_LAMP ? 1 : this == SPORE_LANTERN ? 2 : 0;
   }

   /** Couleur de la lueur (halo, cône). */
   public int glowColor() {
      return switch (this) {
         case CHANDELIER -> 0xFFA040;
         case SPORE_LANTERN -> 0x5EE0A8;
         case BROOD_LAMP -> 0xE8A440;
         default -> 0xFFE7B0;
      };
   }

   /** Hauteur (blocs, depuis le bas du bloc) du cœur lumineux. */
   public float bulbY() {
      return switch (this) {
         case CHANDELIER -> 0.55F;
         case CAGE_LAMP -> 0.38F;
         default -> 0.4F;
      };
   }

   /** Nom de l'animation en boucle du décor. */
   public String loopAnimation() {
      return this == KNIGHT_STATUE ? "animation.faceless_knight.statue" : "animation." + model() + ".idle";
   }

   /** A une texture de lueur (_glowmask). */
   public boolean glows() {
      return this != KNIGHT_STATUE && this != WRAPPED_VICTIM;
   }

   /** Boue toxique (olive, empoisonne) plutôt que liquide radioactif (vert vif, irradie). */
   public boolean sludge() {
      return this == SLUDGE_PIPE || this == SLUDGE_VAT;
   }
}
