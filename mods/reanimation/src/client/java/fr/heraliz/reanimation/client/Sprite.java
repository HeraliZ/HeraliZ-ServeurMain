package fr.heraliz.reanimation.client;

enum Sprite {
   GLOW,
   SPARK,
   STAR,
   RING,
   SMOKE,
   SHARD,
   EMBER,
   SNOWFLAKE,
   PETAL,
   CONFETTI,
   DROP,
   CRESCENT,
   CARD,
   RUNE_A,
   RUNE_B,
   RUNE_C,
   RUNE_D,
   NOTE,
   STREAK,
   DUST,
   BUBBLE,
   FLAME,
   TWINKLE,
   WAVE,
   ROCK,
   CANDY,
   SNOWBALL,
   STAR5,
   SPLAT,
   SMOKE_DARK,
   CROSS,
   DOT,
   FEATHER,
   BOLT,
   HEART,
   SHARD_B,
   BLOSSOM,
   STAR4,
   CLAW;

   private static final float CELL = 0.125F;
   private static final float INSET = 9.765625E-4F;
   private static final Sprite[] RUNES = new Sprite[]{RUNE_A, RUNE_B, RUNE_C, RUNE_D};

   float u0() {
      return this.ordinal() % 8 * 0.125F + 9.765625E-4F;
   }

   float v0() {
      return this.ordinal() / 8 * 0.125F + 9.765625E-4F;
   }

   float u1() {
      return (this.ordinal() % 8 + 1) * 0.125F - 9.765625E-4F;
   }

   float v1() {
      return (this.ordinal() / 8 + 1) * 0.125F - 9.765625E-4F;
   }

   static Sprite rune(int i) {
      return RUNES[Math.floorMod(i, RUNES.length)];
   }
}
