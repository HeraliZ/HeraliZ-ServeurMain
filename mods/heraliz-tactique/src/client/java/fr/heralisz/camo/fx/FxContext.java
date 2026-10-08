package fr.heralisz.camo.fx;

/**
 * Tout ce qu'un effet connaît pour construire son image : temps, faces de l'arme, repère, silhouette, caméra.
 * Positions et directions sont dans le repère où l'arme est dessinée (caméra à l'origine, sauf inventaire).
 */
public final class FxContext {
   public float t;
   public FxMesh mesh;
   public Surfaces surf;
   public FxFrame f;
   public GunShape shape;
   /** Inventaire / interface : projection orthogonale, caméra « à l'infini » vers +z. */
   public boolean gui;
   /** Arme tenue en 1re personne (les éléments trop près de l'œil s'effacent dans le shader). */
   public boolean firstPerson;
   /** Haut du monde dans le repère de rendu (fumées, flammes, chutes). */
   public V up = V.Y;
   /** 1 = pleine qualité (1re personne), moins dans l'inventaire ou de loin. */
   public float quality = 1;
   /** Inspection du skin en cours : progression 0..1 (-1 sinon) et temps écoulé (s) ; l'effet peut s'animer pour l'occasion. */
   public float inspect = -1;
   public float inspectTime;
   /** Apparition du skin quand on sort l'arme (0..1 de la crosse au canon, -1 = arme entière). */
   public float reveal = -1;
   /** Temps écoulé depuis le dernier tir du porteur (s) ; grand si inconnu (armes des autres, au sol). */
   public float fireAge = 99;
   /** Âges (s) des derniers tirs de l'arme (joueur local), du plus récent au plus ancien ; null si inconnus. */
   public float[] shotAges;
   /** Numéro du dernier tir (le tir i de shotAges a le numéro shotSeq - i) : graine stable de ce qu'il éjecte. */
   public int shotSeq;
   /** Chaleur de l'arme (0 froide .. 1 à blanc) et nombre de kills qu'elle a faits. */
   public float heat;
   public int kills;
   /** Inspection de skin : paume de la main gauche animée (repère de rendu), null si elle n'est pas en scène. */
   public V handPos;
   /** Poignet gauche (même repère), null hors scène. */
   public V wristPos;

   /** Inspection en cours ? */
   public boolean inspecting() {
      return this.inspect >= 0;
   }

   /** Direction du point vers la caméra. */
   public V toCam(V p) {
      return this.gui ? V.Z : p.neg().norm();
   }

   /** Longueur de l'arme (repère de rendu). */
   public float L() {
      return this.f.s;
   }

   /** Nombre d'éléments selon la qualité (au moins min). */
   public int count(int full, int min) {
      return Math.max(min, Math.round(full * this.quality));
   }

   // ------------------------------------------------------------------ petits outils

   public static float hash(int a, int b, int c) {
      int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9E3779B1;
      h ^= h >>> 15;
      h *= 0x2c1b3c6d;
      h ^= h >>> 12;
      h *= 0x297a2d39;
      h ^= h >>> 15;
      return (h >>> 8) / 16777216F;
   }

   public static int ihash(int a, int b, int c) {
      return (int) (hash(a, b, c) * 32767);
   }

   public static float smooth(float e0, float e1, float x) {
      float v = clamp((x - e0) / (e1 - e0), 0, 1);
      return v * v * (3 - 2 * v);
   }

   public static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   public static float fract(float v) {
      return v - (float) Math.floor(v);
   }

   public static float mix(float a, float b, float t) {
      return a + (b - a) * t;
   }

   /** Bruit de valeur 1D lisse (-1..1), pour des mouvements organiques. */
   public static float noise(float x, int seed) {
      int i = (int) Math.floor(x);
      float f = x - i;
      float u = f * f * (3 - 2 * f);
      return (hash(i, seed, 7) * (1 - u) + hash(i + 1, seed, 7) * u) * 2 - 1;
   }

   public static float sin(float a) {
      return (float) Math.sin(a);
   }

   public static float cos(float a) {
      return (float) Math.cos(a);
   }

   public static final float PI = (float) Math.PI, TAU = (float) (Math.PI * 2);
}
