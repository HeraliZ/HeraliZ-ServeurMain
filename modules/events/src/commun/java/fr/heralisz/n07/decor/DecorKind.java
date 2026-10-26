package fr.heralisz.n07.decor;

/**
 * Décors du spawn. Fichier écrit par tools/decor/build.py d'après les modèles 3D (ne pas modifier à la main : relancer le
 * script) ; chaque fiche dit comment le décor se pose, ses cases de collision (déduites du volume du modèle), ses lumières,
 * ses effets, son son d'ambiance et ses mouvements. Modèle : geo/block/decor_&lt;id&gt;.geo.json.
 */
public enum DecorKind {
   STORM_LANTERN(true, true, new DecorSpec("storm_lantern")
      .mount(DecorSpec.Mount.CEILING)
      .radius(5)
      .box(-3.5, 0, -3.5, 3.5, 16, 3.5, false)
      .lights(0, -1, 0, 15)
      .swing(7F, 90F)
      .drop()
      .bounds(0, -3, 0, 0, 0, 0)),
   MINE_LAMP(true, false, new DecorSpec("mine_lamp")
      .mount(DecorSpec.Mount.CEILING)
      .radius(6)
      .box(-3.5, 0, -3.5, 3.5, 16, 3.5, false)
      .lights(0, -1, 0, 15)
      .swing(3F, 110F)
      .drop()
      .beams(0xFFD9A0, 0, -7.5, 0, 0, -1, 0, 84, 44)
      .bounds(-1, -3, -1, 1, 1, 1)),
   STRING_LIGHTS(true, false, new DecorSpec("string_lights")
      .mount(DecorSpec.Mount.CEILING)
      .radius(5)
      .box(-2, 10, -2, 2, 16, 2, false)
      .lights(0, 0, 0, 10, -2, 0, 0, 13, 2, 0, 0, 13, -3, 0, 0, 9, 3, 0, 0, 9)
      .swing(5F, 60F)
      .bounds(-4, -1, 0, 4, 0, 0)),
   WALL_LAMP(true, false, new DecorSpec("wall_lamp")
      .mount(DecorSpec.Mount.WALL)
      .radius(2)
      .box(-5, 3, 2, 5, 13, 8, true)
      .lights(0, 0, 0, 14)
      .bounds(0, -1, 0, 0, 0, 1)),
   FLOODLIGHT(true, false, new DecorSpec("floodlight")
      .radius(10)
      .box(-6, 0, -6, 6, 16, 6, true)
      .lights(0, 1, -1, 15, 0, 0, -4, 14, 0, 0, -7, 12)
      .fx(new DecorSpec.Fx[] {DecorSpec.Fx.DUST, DecorSpec.Fx.DUST}, 0, 24, -34, 18, 0, 18, -70, 30)
      .loop("decor.lamp_hum", 0.25F)
      .beams(0xE6F2FF, 0, 27.5, -4.5, 0, -0.276, -0.961, 176, 70)
      .bounds(-1, -1, 0, 1, 2, 1)),
   FIRE_BARREL(true, false, new DecorSpec("fire_barrel")
      .radius(3)
      .box(-6, 0, -6, 6, 16, 6, true)
      .lights(0, 0, 0, 15, 0, 1, 0, 13)
      .fx(new DecorSpec.Fx[] {DecorSpec.Fx.FIRE, DecorSpec.Fx.FIRE}, 0, 17, 0, 7, 0, 19, 0, 4)
      .bounds(0, -1, 0, 0, 1, 0)),
   LOCOMOTIVE(true, true, new DecorSpec("locomotive")
      .radius(10)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 0, -4, -1, 0, -3, -1, 0, -2, -1, 0, -1, -1, 0, 0, -1, 0, 1, -1, 0, 2, -1, 0, 3, -1, 1, -4, -1, 1, -3, -1, 1, -2, -1, 1, -1, -1, 1, 0, -1, 1, 1, -1, 1, 2, -1, 1, 3, -1, 1, 4, -1, 2, -4, -1, 2, -3, -1, 2, -2, -1, 2, -1, -1, 2, 0, -1, 2, 1, -1, 2, 2, -1, 2, 3, -1, 2, 4, 0, 0, -4, 0, 0, -3, 0, 0, -2, 0, 0, -1, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0, 4, 0, 1, -4, 0, 1, -3, 0, 1, -2, 0, 1, -1, 0, 1, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 1, 4, 0, 2, -4, 0, 2, -3, 0, 2, -2, 0, 2, -1, 0, 2, 0, 0, 2, 1, 0, 2, 2, 0, 2, 3, 0, 2, 4, 0, 3, -4, 0, 3, -3, 0, 3, -2, 0, 3, -1, 0, 3, 0, 0, 3, 1, 0, 3, 2, 0, 3, 3, 1, 0, -4, 1, 0, -3, 1, 0, -2, 1, 0, -1, 1, 0, 0, 1, 0, 1, 1, 0, 2, 1, 0, 3, 1, 1, -4, 1, 1, -3, 1, 1, -2, 1, 1, -1, 1, 1, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 1, 4, 1, 2, -4, 1, 2, -3, 1, 2, -2, 1, 2, -1, 1, 2, 0, 1, 2, 1, 1, 2, 2, 1, 2, 3, 1, 2, 4)
      .lights(0, 2, 1, 9, 0, 1, -7, 15, 0, 0, -10, 13, 0, 0, -14, 11)
      .fx(new DecorSpec.Fx[] {DecorSpec.Fx.SMOKE, DecorSpec.Fx.STEAM, DecorSpec.Fx.STEAM, DecorSpec.Fx.EMBER, DecorSpec.Fx.DUST}, 0, 60, -56, 3, -14, 10, -56, 2, 14, 10, -56, 2, 0, 25, 10, 4, 0, 38, -120, 40)
      .loop("decor.steam_idle", 0.55F)
      .rail()
      .beams(0xFFF0C8, 0, 45.5, -66.5, 0, -0.06, -1, 330, 96)
      .bounds(-1, 0, -5, 1, 3, 4)),
   FREIGHT_WAGON(false, false, new DecorSpec("freight_wagon")
      .radius(8)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 1, -3, -1, 1, -2, -1, 1, -1, -1, 1, 0, -1, 1, 1, -1, 1, 2, -1, 1, 3, -1, 2, -3, -1, 2, -2, -1, 2, -1, -1, 2, 0, -1, 2, 1, -1, 2, 2, -1, 2, 3, -1, 3, -3, -1, 3, -2, -1, 3, -1, -1, 3, 0, -1, 3, 1, -1, 3, 2, -1, 3, 3, 0, 0, -3, 0, 0, -2, 0, 0, 2, 0, 0, 3, 0, 1, -3, 0, 1, -2, 0, 1, -1, 0, 1, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 2, -3, 0, 2, -2, 0, 2, -1, 0, 2, 0, 0, 2, 1, 0, 2, 2, 0, 2, 3, 0, 3, -3, 0, 3, -2, 0, 3, -1, 0, 3, 0, 0, 3, 1, 0, 3, 2, 0, 3, 3, 1, 1, -3, 1, 1, -2, 1, 1, -1, 1, 1, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 2, -3, 1, 2, -2, 1, 2, -1, 1, 2, 0, 1, 2, 1, 1, 2, 2, 1, 2, 3, 1, 3, -3, 1, 3, -2, 1, 3, -1, 1, 3, 0, 1, 3, 1, 1, 3, 2, 1, 3, 3)
      .rail()
      .bounds(-1, -1, -4, 1, 3, 4)),
   ORE_WAGON(false, false, new DecorSpec("ore_wagon")
      .radius(6)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 1, -2, -1, 1, -1, -1, 1, 0, -1, 1, 1, -1, 1, 2, 0, 0, -2, 0, 0, -1, 0, 0, 1, 0, 0, 2, 0, 1, -2, 0, 1, -1, 0, 1, 0, 0, 1, 1, 0, 1, 2, 0, 2, -2, 0, 2, -1, 0, 2, 0, 0, 2, 1, 0, 2, 2, 1, 1, -2, 1, 1, -1, 1, 1, 0, 1, 1, 1, 1, 1, 2)
      .rail()
      .bounds(-1, -1, -3, 1, 2, 3)),
   MINE_CART(false, true, new DecorSpec("mine_cart")
      .radius(2)
      .box(-7, 0, -8, 7, 14, 8, true)
      .lights(0, 1, -1, 11)
      .swing(6F, 70F)
      .rail()
      .bounds(0, -1, -1, 1, 1, 1)),
   BUFFER_STOP(true, false, new DecorSpec("buffer_stop")
      .radius(3)
      .box(-8, 0, -4, 8, 16, 8, true)
      .lights(0, 2, 0, 7)
      .rail()
      .bounds(-1, 0, -1, 1, 2, 1)),
   NEON_SIGN(true, false, new DecorSpec("neon_sign")
      .mount(DecorSpec.Mount.WALL)
      .radius(6)
      .box(-8, 2, 4, 8, 14, 8, false)
      .lights(0, 0, -1, 11, -2, 0, -1, 10, 3, 0, -1, 10)
      .fx(new DecorSpec.Fx[] {DecorSpec.Fx.SPARK}, 1.3, 12.2, 2.4, 2)
      .loop("decor.neon_buzz", 0.3F)
      .bounds(-3, -1, 0, 3, 1, 0)),
   STATION_SIGN(false, false, new DecorSpec("station_sign")
      .radius(4)
      .box(-2, 0, -2, 2, 16, 2, true)
      .bounds(-2, -1, 0, 2, 2, 0)),
   WELCOME_SIGN(false, false, new DecorSpec("welcome_sign")
      .radius(5)
      .box(-3, 0, -3, 3, 16, 3, true)
      .bounds(-3, -1, 0, 3, 3, 0)),
   SIGNPOST(false, false, new DecorSpec("signpost")
      .radius(4)
      .box(-2.5, 0, -2.5, 2.5, 16, 2.5, true)
      .bounds(-1, -1, -2, 1, 3, 1)),
   SAFEZONE_SIGN(false, false, new DecorSpec("safezone_sign")
      .radius(4)
      .box(-3, 0, -3, 3, 16, 3, true)
      .bounds(-1, -1, 0, 1, 3, 0)),
   DANGER_SIGN(false, false, new DecorSpec("danger_sign")
      .radius(3)
      .box(-8, 0, -2, 8, 16, 2, true)
      .bounds(-1, -1, 0, 1, 2, 0)),
   NOTICE_BOARD(false, false, new DecorSpec("notice_board")
      .radius(3)
      .box(-8, 0, -3, 8, 16, 3, true)
      .bounds(-1, 0, 0, 2, 2, 0)),
   BANNER(false, false, new DecorSpec("banner")
      .mount(DecorSpec.Mount.CEILING)
      .radius(5)
      .box(-4, 8, -2, 4, 16, 2, false)
      .wave(7F)
      .bounds(-1, -4, 0, 1, 1, 0)),
   MILITARY_TENT(false, true, new DecorSpec("military_tent")
      .radius(5)
      .box(-1.5, 0, -1.5, 1.5, 16, 1.5, true)
      .foot(-2, 0, -2, -2, 0, -1, -2, 0, 0, -2, 0, 1, -2, 0, 2, 2, 0, -2, 2, 0, -1, 2, 0, 0, 2, 0, 1, 2, 0, 2, -2, 1, -2, -2, 1, -1, -2, 1, 0, -2, 1, 1, -2, 1, 2, 2, 1, -2, 2, 1, -1, 2, 1, 0, 2, 1, 1, 2, 1, 2, -1, 0, 2, 0, 0, 2, 1, 0, 2, -1, 1, 2, 0, 1, 2, 1, 1, 2)
      .lights(0, 1, 1, 12)
      .bounds(-2, -1, -2, 2, 2, 2)),
   GENERATOR(false, false, new DecorSpec("generator")
      .radius(3)
      .box(-8, 0, -8, 8, 16, 8, true)
      .fx(new DecorSpec.Fx[] {DecorSpec.Fx.EXHAUST}, -11, 22, 9, 1)
      .loop("decor.generator", 0.6F)
      .spin(24F)
      .bounds(-1, -1, -1, 1, 1, 2)),
   RADIO_STATION(true, false, new DecorSpec("radio_station")
      .radius(5)
      .box(-8, 0, -8, 8, 16, 8, true)
      .lights(0, 0, 0, 9)
      .loop("decor.radio", 0.35F)
      .bounds(-1, -1, 0, 1, 4, 1)),
   WORKBENCH(true, false, new DecorSpec("workbench")
      .radius(3)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(0, 1, 0)
      .lights(0, 0, 0, 10)
      .bounds(-1, -1, -1, 1, 2, 0)),
   SUPPLY_CRATES(false, false, new DecorSpec("supply_crates")
      .radius(3)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 0, 0, 0, 0, -1, 0, 1, 0, 1, 0, 0)
      .bounds(-1, -1, -1, 1, 1, 1)),
   FUEL_DRUMS(false, false, new DecorSpec("fuel_drums")
      .radius(3)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 0, 0, 0, 0, 1, 1, 0, 0)
      .bounds(-1, -1, -1, 1, 0, 1)),
   SANDBAGS(false, false, new DecorSpec("sandbags")
      .radius(3)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 0, 0, -1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1)
      .bounds(-2, -1, -1, 2, 1, 1)),
   CZECH_HEDGEHOG(false, false, new DecorSpec("czech_hedgehog")
      .radius(3)
      .box(-8, 0, -8, 8, 16, 8, true)
      .bounds(-1, -1, -1, 1, 1, 1)),
   WRECKED_CAR(false, false, new DecorSpec("wrecked_car")
      .radius(5)
      .box(-8, 0, -8, 8, 16, 8, true)
      .foot(-1, 0, -1, -1, 0, 0, -1, 0, 1, 0, 0, -2, 0, 0, -1, 0, 0, 1, 0, 0, 2, 0, 1, -2, 0, 1, -1, 0, 1, 0, 0, 1, 1, 0, 1, 2, 1, 0, -1, 1, 0, 0, 1, 0, 1)
      .bounds(-1, -1, -2, 1, 1, 2)),
   MINE_SUPPORT(false, true, new DecorSpec("mine_support")
      .radius(5)
      .box(-3, 0, -3, 3, 16, 3, false)
      .foot(-2, 0, 0, -2, 1, 0, -2, 2, 0, -2, 3, 0, 2, 0, 0, 2, 1, 0, 2, 2, 0, 2, 3, 0)
      .lights(0, 2, 0, 13)
      .swing(4F, 100F)
      .bounds(-2, -1, 0, 2, 4, 0));

   /** A une texture de lueur (_glowmask). */
   public final boolean glow;
   /** A des os « glass… » rendus en verre translucide. */
   public final boolean glass;
   public final DecorSpec spec;

   DecorKind(boolean glow, boolean glass, DecorSpec spec) {
      this.glow = glow;
      this.glass = glass;
      this.spec = spec;
   }
}
