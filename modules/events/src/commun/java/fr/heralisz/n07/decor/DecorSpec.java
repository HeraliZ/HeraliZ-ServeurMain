package fr.heralisz.n07.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Fiche d'un décor du spawn (écrite par tools/decor/build.py d'après le modèle 3D) : comment il se pose, la place qu'il occupe,
 * d'où vient sa lumière, ses effets (fumée, braises, vapeur...), son son d'ambiance et ses mouvements procéduraux.
 * <p>
 * Toutes les positions sont dans le repère du modèle : origine au milieu du bas du bloc posé, avant = -Z (tourné vers le joueur
 * qui le pose), +X à droite du décor, unités = 1/16 de bloc pour les points, blocs entiers pour les cases.
 */
public final class DecorSpec {
   /** Pose : au sol, suspendu sous un plafond, accroché à un mur (le dos contre le mur). */
   public enum Mount { FLOOR, CEILING, WALL }

   /** Effets de particules. */
   public enum Fx { FIRE, SMOKE, STEAM, EXHAUST, SPARK, EMBER, DUST, DRIP }

   public final String id;
   public Mount mount = Mount.FLOOR;
   public int radius = 3;
   /** Boîte de sélection et de collision du bloc posé (1/16 de bloc, repère du modèle : x et z de -8 à 8, y de 0 à 16). */
   public double[] box = {-8, 0, -8, 8, 16, 8};
   public boolean collide = true;
   /** Cases pleines invisibles autour du bloc : triplets (x, y, z) en blocs. */
   public int[] foot = {};
   /** Lumières invisibles : quadruplets (x, y, z, niveau). */
   public int[] lights = {};
   /** Effets : pour chaque effet, (type, x, y, z, dispersion) en 1/16 de bloc. */
   public Fx[] fxType = {};
   public double[] fx = {};
   /** Son d'ambiance en boucle (identifiant BossSounds) et son volume ; sons ponctuels (vanilla) gérés par DecorFx. */
   public String loop;
   public float loopVolume = 0.8F;
   /** Balancement (degrés, période en ticks), rotation continue (degrés par tick), ondulation du tissu (degrés). */
   public float swingAmp, swingPeriod = 80, spin, wave;
   /** Chaîne rallongeable d'un clic droit (0 à 3 blocs de plus). */
   public boolean drop;
   /** Véhicule : posé au-dessus d'un rail, il descend d'un bloc pour rouler sur la voie. */
   public boolean rail;
   /** Faisceaux lumineux : (x, y, z, direction x, y, z, longueur, rayon d'arrivée) en 1/16 de bloc, couleur RGB. */
   public double[] beams = {};
   public int beamColor = 0xFFE8B0;
   /** Encombrement du modèle en cases (x0, y0, z0, x1, y1, z1), repère du modèle — pour la vitrine. */
   public int[] bounds = {0, 0, 0, 0, 0, 0};

   private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

   public DecorSpec(String id) {
      this.id = id;
   }

   public DecorSpec mount(Mount m) {
      this.mount = m;
      return this;
   }

   public DecorSpec radius(int r) {
      this.radius = r;
      return this;
   }

   public DecorSpec box(double x0, double y0, double z0, double x1, double y1, double z1, boolean collide) {
      this.box = new double[] {x0, y0, z0, x1, y1, z1};
      this.collide = collide;
      return this;
   }

   public DecorSpec foot(int... f) {
      this.foot = f;
      return this;
   }

   public DecorSpec lights(int... l) {
      this.lights = l;
      return this;
   }

   public DecorSpec fx(Fx[] types, double... pts) {
      this.fxType = types;
      this.fx = pts;
      return this;
   }

   public DecorSpec loop(String sound, float vol) {
      this.loop = sound;
      this.loopVolume = vol;
      return this;
   }

   public DecorSpec swing(float amp, float period) {
      this.swingAmp = amp;
      this.swingPeriod = period;
      return this;
   }

   public DecorSpec spin(float degPerTick) {
      this.spin = degPerTick;
      return this;
   }

   public DecorSpec wave(float amp) {
      this.wave = amp;
      return this;
   }

   public DecorSpec drop() {
      this.drop = true;
      return this;
   }

   public DecorSpec rail() {
      this.rail = true;
      return this;
   }

   public DecorSpec bounds(int... b) {
      this.bounds = b;
      return this;
   }

   public DecorSpec beams(int color, double... b) {
      this.beamColor = color;
      this.beams = b;
      return this;
   }

   /** Lumière émise par le bloc lui-même (entrée de lumière placée sur sa propre case). */
   public int selfLight(int drop, boolean onRail) {
      for (int i = 0; i + 3 < lights.length; i += 4) {
         int y = lights[i + 1] - (this.drop && lights[i + 1] < 0 ? drop : 0) - (onRail ? 1 : 0);
         if (lights[i] == 0 && y == 0 && lights[i + 2] == 0) return lights[i + 3];
      }
      return 0;
   }

   // ------------------------------------------------------------------ repère du modèle -> monde
   /** Tourne un décalage horizontal du repère du modèle selon l'orientation du bloc (même rotation que le rendu). */
   public static int[] rotate(int x, int z, Direction f) {
      return switch (f) {
         case SOUTH -> new int[] {-x, -z};
         case WEST -> new int[] {z, -x};
         case EAST -> new int[] {-z, x};
         default -> new int[] {x, z};
      };
   }

   public static double[] rotate(double x, double z, Direction f) {
      return switch (f) {
         case SOUTH -> new double[] {-x, -z};
         case WEST -> new double[] {z, -x};
         case EAST -> new double[] {-z, x};
         default -> new double[] {x, z};
      };
   }

   /** Forme du bloc posé, tournée ; le repère du modèle est centré sur le bloc (x, z de -8 à 8). */
   public VoxelShape shape(Direction f) {
      return shapes.computeIfAbsent(f, d -> {
         double[] a = rotate(box[0], box[2], d), b = rotate(box[3], box[5], d);
         double x0 = Math.max(0, Math.min(a[0], b[0]) + 8), x1 = Math.min(16, Math.max(a[0], b[0]) + 8);
         double z0 = Math.max(0, Math.min(a[1], b[1]) + 8), z1 = Math.min(16, Math.max(a[1], b[1]) + 8);
         double y0 = Math.max(0, box[1]), y1 = Math.min(16, box[4]);
         if (x1 <= x0 || y1 <= y0 || z1 <= z0) return Shapes.block();
         return Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
      });
   }
}
