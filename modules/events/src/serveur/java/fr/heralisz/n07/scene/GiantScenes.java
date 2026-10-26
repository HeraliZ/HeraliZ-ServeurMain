package fr.heralisz.n07.scene;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.Encounter;
import fr.heralisz.n07.api.HallLayout;
import fr.heralisz.n07.api.N07Api;
import fr.heralisz.n07.api.PlacedHall;
import fr.heralisz.n07.api.scene.Scene;
import fr.heralisz.n07.api.scene.SceneHandle;
import fr.heralisz.n07.api.scene.SceneResult;
import fr.heralisz.n07.api.scene.SkipMode;
import fr.heralisz.n07.encounter.EncounterImpl;
import fr.heralisz.n07.entity.BossRemains;
import fr.heralisz.n07.entity.SujetN07;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scenes du combat final de The Giant (ANALYSE.md §7), ecrites comme donnees (plans + repliques) avec l'API de scenes.
 * Reperes : « porte » (porte de confinement, regard du tunnel vers le puits), « puits » / « puits_bas » (palier du haut /
 * cabine en bas), « hall » (pieds du boss dans sa cuve, regard vers le fond du hall). Unites : blocs, ticks (20 / s).
 * Dans le repere « hall » : avant = vers le fond (sud local), droite = vers l'ouest local, hauteur depuis le sol.
 */
public final class GiantScenes {
   private static final int GREEN = 0x7CFF3A, MAXIS = 0xE8C46A;
   private static final String S = "scene.heralisz_n07.";

   private GiantScenes() {}

   // ------------------------------------------------------------------ outils de reperes
   private static Vec3 lift(PlacedHall h, double x, double y, double z) {
      BlockPos o = h.layout.liftOffset;
      return h.world(new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z));
   }

   /** Repere de la porte deduit du haut du puits : milieu de l'ouverture (x 1..4), juste devant (z = -0.5). */
   public static Scene.Frame defaultDoor(PlacedHall h) {
      return new Scene.Frame(lift(h, 3.0, h.layout.liftDrop + 1, -0.5), h.yaw(0));
   }

   /** {avant, droite, hauteur} d'un bloc local du hall dans le repere « hall ». */
   private static float[] inHall(HallLayout l, BlockPos p, double dy) {
      return new float[] {(float) (p.getZ() - l.boss.getZ()), (float) (l.boss.getX() - p.getX()), (float) (p.getY() - l.boss.getY() + dy)};
   }

   // ------------------------------------------------------------------ intro
   public static SceneHandle playIntro(EncounterImpl enc, Scene.Frame door, SkipMode skip, Consumer<SceneResult> onFight) {
      PlacedHall h = enc.spec().hall;
      ServerLevel level = h.level;
      HallLayout l = h.layout;
      int drop = l.liftDrop;
      Scene.Frame doorF = door != null ? door : defaultDoor(h);
      Scene.Builder b = Scene.builder("giant_intro")
         .frame("porte", doorF.pos(), doorF.yaw())
         .frame("puits", lift(h, 3.5, drop + 1, 3.5), h.yaw(0))
         .frame("puits_bas", lift(h, 3.5, 1, 3.5), h.yaw(0))
         .frame("hall", h.bossFeet(), h.yaw(0));

      // S1 (0-6 s) : le tunnel du C, lente poussee vers la porte de confinement
      b.shot("porte", 120).fadeIn(30).key(0, -8, 0.6F, 1.2F, 0, 0, 1.8F).fov(62).key(1, -5, 0.3F, 1.5F, 0, 0, 1.9F).fov(58);
      b.label(20, 90, S + "intro.stencil", 0.05F, 0.16F, 1.2F, 0.8F, 0xD8D8C8);
      b.subtitle(14, 96, S + "maxis", S + "intro.maxis1", MAXIS);
      b.uiSound(4, "heralisz_n07:dungeon.amb_drone", 0.5F, 0.7F);
      b.uiSound(60, "heralisz_n07:dungeon.alarm", 0.25F, 0.6F);
      // S2 (6-14 s) : gros plan sur la serrure, les verrous sautent ; la porte s'ouvre sur la cabine eclairee
      b.shot("porte", 60).key(0, -1.6F, 1.4F, 1.5F, 0.2F, 1.9F, 1.3F).fov(50).key(1, -1.4F, 1.2F, 1.55F, 0.2F, 1.9F, 1.3F).fov(48);
      b.shot("porte", 100).key(0, -5.5F, -0.8F, 1.7F, 2, 0, 1.6F).fov(65).key(1, -4, -0.5F, 1.8F, 3, 0, 1.4F).fov(62);
      b.sound(124, "heralisz_n07:dungeon.key_swipe", 1.2F, 1F, "porte", 0, 1.8F, 1.3F);
      b.sound(138, "heralisz_n07:dungeon.key_granted", 1.2F, 1F, "porte", 0, 1.8F, 1.3F);
      for (int i = 0; i < 3; i++) b.sound(148 + i * 10, "minecraft:block.iron_door.open", 1.4F, 0.6F + 0.1F * i, "porte", 0, 0, 1 + i);
      b.sound(178, "heralisz_n07:dungeon.steam", 2F, 0.8F, "porte", 0, 0, 1.5F);
      b.particles(178, 50, "minecraft:cloud", "porte", 0.2F, -2.0F, 1.6F, 0.15F, 1.6F, 0.15F, 2, 0.04F, -1);
      b.particles(178, 50, "minecraft:cloud", "porte", 0.2F, 2.0F, 1.6F, 0.15F, 1.6F, 0.15F, 2, 0.04F, -1);
      b.subtitle(130, 80, S + "maxis", S + "intro.maxis2", MAXIS);
      b.server(140, () -> enc.hook(N07Api.HOOK_DOOR_KEY), false);
      b.server(180, () -> enc.hook(N07Api.HOOK_DOOR_OPEN), true);
      // cabine fantome : la vraie cabine (en bas) copiee en haut du puits, elle attend puis descend pendant S3
      BlockPos c1 = h.liftWorld(l.cageMin), c2 = h.liftWorld(l.cageMax);
      BlockPos cMin = new BlockPos(Math.min(c1.getX(), c2.getX()), Math.min(c1.getY(), c2.getY()), Math.min(c1.getZ(), c2.getZ()));
      BlockPos cMax = new BlockPos(Math.max(c1.getX(), c2.getX()), Math.max(c1.getY(), c2.getY()), Math.max(c1.getZ(), c2.getZ()));
      b.ghost(120, 160, 280, cMin, cMax, new Vec3(0, drop, 0), Vec3.ZERO, true);

      // S3 (14-28 s) : la descente, dans la cabine, contre-plongee sur la lumiere du tunnel qui rapetisse
      b.shot("puits", 280).fadeOut(14).key(0, 0.6F, 0.8F, 1.0F, 0.9F, 0.3F, 13).fov(75).roll(2)
         .key(1, 0.6F, 0.8F, 1.0F - drop, 0.9F, 0.3F, 13 - drop).fov(72).roll(2);
      b.server(280, () -> enc.hook(N07Api.HOOK_LIFT_DOWN), false);
      // les joueurs sont places sur leurs marques dans le hall (hors champ : la camera regarde le puits)
      b.server(290, () -> placePlayers(enc, h), true);
      b.particles(280, 280, "minecraft:electric_spark", "puits_bas", 1, 2.5F, 0.3F, 0.1F, 0.3F, 0.1F, 1.2F, 0.15F, 0);
      b.particles(280, 280, "minecraft:electric_spark", "puits_bas", -1, -2.5F, 0.3F, 0.1F, 0.3F, 0.1F, 1.2F, 0.15F, 0);
      b.uiSound(282, "minecraft:entity.minecart.inside", 0.6F, 0.55F);
      b.uiSound(382, "minecraft:entity.minecart.inside", 0.6F, 0.5F);
      b.uiSound(482, "minecraft:entity.minecart.inside", 0.5F, 0.45F);
      b.uiSound(300, "heralisz_n07:dungeon.amb_chains", 0.8F, 0.8F);
      for (int i = 0; i < 4; i++) b.label(300 + i * 60, 55, S + "intro.floor" + (i + 1), 0.05F, 0.16F, 1.3F, 1.5F, 0xE8C46A);
      b.subtitle(330, 90, S + "maxis", S + "intro.maxis3", MAXIS);
      b.uiSound(440, "heralisz_n07:sujet_n07.roar", 0.4F, 0.6F);
      b.shake(440, 0.25F, 34);
      b.uiSound(380, "heralisz_n07:sujet_n07.geiger", 0.4F, 1F);
      b.uiSound(450, "heralisz_n07:sujet_n07.geiger", 0.6F, 1.05F);
      b.uiSound(520, "heralisz_n07:sujet_n07.geiger", 0.8F, 1.1F);

      // S4 (28-40 s) : la galerie (plan d'ensemble derriere la vitre), puis travelling bas le long des rigoles vers la cuve
      b.shot("hall", 100).fadeIn(20).key(0, -31, 0, 8.6F, 0, 0, 1.5F).fov(60).key(1, -29.6F, -0.6F, 8.4F, 0, 0.4F, 1.8F).fov(58);
      b.server(560, () -> enc.hook(N07Api.HOOK_LIFT_ARRIVED), false);
      b.shot("hall", 140).key(0, -25.5F, -6, 1.8F, 0, 0, 4).fov(55).key(1, -13.5F, -4, 1.5F, 0, 0, 4.5F).fov(52);
      b.sound(680, "minecraft:entity.warden.heartbeat", 2F, 0.6F, "hall", 0, 0, 4);
      b.sound(740, "minecraft:entity.warden.heartbeat", 2.2F, 0.6F, "hall", 0, 0, 4);
      b.sound(712, "heralisz_n07:sujet_n07.bubble", 2F, 0.7F, "hall", 0, 0, 3);

      // S5 (40-50 s) : orbite autour de la cuve ; les buses demarrent une a une, les rigoles s'allument en vague vers la cuve
      float R = 16;
      float[] th = {200, 230, 260, 290};
      float[] hh = {9, 8.2F, 7, 6};
      b.shot("hall", 200);
      for (int i = 0; i < th.length; i++) {
         double a = Math.toRadians(th[i]);
         b.key(i / (th.length - 1F), (float) (R * Math.cos(a)), (float) (R * Math.sin(a)), hh[i], 0, 0, 4.5F).fov(66);
      }
      b.uiSound(800, "heralisz_n07:dungeon.amb_drone", 0.6F, 0.9F);
      for (int i = 0; i < h.pipeCount(); i++) {
         int k = i;
         float[] p = inHall(l, l.pipes.get(i), 0);
         b.sound(806 + 16 * i, "heralisz_n07:dungeon.valve_turn", 1.6F, 0.9F + 0.03F * i, "hall", p[0], p[1], p[2]);
         b.server(810 + 16 * i, () -> h.pipe(k, true), true);
      }
      b.server(830, () -> h.channels(true, 120), false);
      b.subtitle(840, 90, S + "maxis", S + "intro.maxis4", MAXIS);

      // S6 (50-62 s) : le terminal (ecran « EINDAMMUNG : FEHLER »), puis les bobines de la cuve qui s'emballent
      float[] t = inHall(l, l.terminal, 0);
      b.shot("hall", 100).key(0, t[0] - 2.2F, -0.7F, 1.6F, t[0] - 0.2F, 0, 1.2F).fov(45).key(1, t[0] - 1.6F, -0.4F, 1.55F, t[0] - 0.2F, 0, 1.2F).fov(42);
      b.server(1000, h::terminalRitual, false);
      b.label(1010, 110, S + "intro.terminal", 0.06F, 0.70F, 1.3F, 1.2F, GREEN);
      b.shot("hall", 140).key(0, -6.5F, -5.5F, 0.6F, 0, 0, 6).fov(70).key(1, -5.6F, -4.8F, 0.7F, 0, 0, 6.5F).fov(66);
      b.subtitle(1110, 100, S + "maxis", S + "intro.maxis5", MAXIS);
      b.shake(1180, 0.3F, 40);
      int len = b.length();
      // reveil juste avant la fin : la cinematique d'apparition prend le relais sans une image de jeu entre les deux
      b.server(len - 3, () -> {
         enc.hook(N07Api.HOOK_INTRO_END);
         enc.wake();
      }, false);
      Scene intro = b.build();

      List<ServerPlayer> viewers = enc.viewers();
      // la main revient aux joueurs une seule fois : fin du raccord, apparition passee ou intro passee
      java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
      Consumer<SceneResult> fight = r -> {
         if (once.compareAndSet(false, true) && onFight != null) onFight.accept(r);
      };
      // fin de l'apparition : raccord vers les yeux des joueurs
      enc.onSpawnEnding(() -> {
         SceneHandle hand = playHandover(enc, viewers, r -> fight.accept(SceneResult.COMPLETED));
         if (hand == null) fight.accept(SceneResult.COMPLETED);
         else enc.attachScene(hand);
      });
      enc.onSpawnSkipped(() -> fight.accept(SceneResult.SKIPPED));
      enc.onPhaseInternal(ph -> radio(enc, ph == 2 ? "p2.maxis" : "p3.maxis"));
      SceneHandle handle = SceneRunner.play(level, intro, viewers, skip, res -> {
         if (res == SceneResult.COMPLETED) return;   // la suite : reveil (action serveur) puis raccord
         if (!enc.active()) return;                  // combat interrompu entre-temps
         // passee ou interrompue : le combat commence tout de suite (cuve brisee, pas de cinematique d'apparition)
         enc.wakeSkipped();
         fight.accept(res);
      });
      enc.attachScene(handle);
      return handle;
   }

   /** Place les joueurs sur les marques du hall (regard vers la cuve), proteges et sans degats de chute. */
   static void placePlayers(EncounterImpl enc, PlacedHall h) {
      List<Vec3> marks = h.playerMarks();
      float yaw = h.playerMarksYaw();
      int i = 0;
      for (ServerPlayer p : enc.viewers()) {
         Vec3 m = marks.get(i % marks.size());
         double extra = (i / marks.size()) * 0.6;
         p.teleportTo(h.level, m.x + extra, m.y, m.z, yaw, 0);
         p.fallDistance = 0;
         i++;
      }
      enc.hook(N07Api.HOOK_PLAYERS_PLACED);
   }

   /** Raccord de fin d'apparition : depuis le dernier plan du boss jusque dans les yeux de chaque joueur (1,7 s). */
   static SceneHandle playHandover(EncounterImpl enc, List<ServerPlayer> viewers, Consumer<SceneResult> done) {
      Vec3 anchor = enc.spawnAnchor();
      if (anchor == null || viewers.isEmpty()) return null;
      // derniere cle de la cinematique d'apparition : {1, 13, 11, 4.4, 5} (SujetN07.CINE)
      Scene sc = Scene.builder("giant_handover").frame("boss", anchor, enc.spawnYaw())
         .shot("boss", 34).toPlayer().key(0, 13, 11, 4.4F, 0, 0, 5).key(1, 13, 11, 4.4F, 0, 0, 5).build();
      return SceneRunner.play(enc.spec().level, sc, viewers, SkipMode.NONE, done);
   }

   /** Replique radio de Maxis (habillage seul, par-dessus la cinematique de phase). */
   static void radio(EncounterImpl enc, String key) {
      Scene sc = Scene.builder("giant_radio").overlayOnly(110).subtitle(20, 85, S + "maxis", S + key, MAXIS).uiSound(18, "heralisz_n07:dungeon.key_beep", 0.5F, 0.6F)
         .build();
      SceneRunner.play(enc.spec().level, sc, enc.viewers(), SkipMode.NONE, null);
   }

   // ------------------------------------------------------------------ outro
   public static SceneHandle playOutro(EncounterImpl enc, SkipMode skip, Consumer<SceneResult> onEnd) {
      PlacedHall h = enc.spec().hall;
      HallLayout l = h.layout;
      Runnable finalState = () -> {
         h.pipes(false, 0);
         h.channels(false, 0);
         h.sirens(false);
         h.lights(true, 0);
         h.teleporter(true);
         enc.hook(N07Api.HOOK_TELEPORTER_ON);
      };
      List<ServerPlayer> viewers = enc.viewers();
      if (enc.deathSkipped() || viewers.isEmpty()) {
         finalState.run();
         enc.hook(N07Api.HOOK_OUTRO_END);
         if (onEnd != null) onEnd.accept(SceneResult.SKIPPED);
         return null;
      }
      BossRemains r = enc.remainsEntity();
      Vec3 statue = r != null && !r.isRemoved() ? r.position() : h.bossFeet();
      float sYaw = r != null ? r.getYRot() : h.bossYaw();
      Scene.Builder b = Scene.builder("giant_outro").frame("statue", statue, sYaw).frame("hall", h.bossFeet(), h.yaw(0));
      // O1 (0-7 s) : orbite lente autour de la statue de trinitite ; les buses se taisent une a une, les rigoles s'eteignent
      float[] th = {30, 70, 110, 150};
      b.shot("statue", 140).avoidWalls();
      for (int i = 0; i < th.length; i++) {
         double a = Math.toRadians(th[i]);
         b.key(i / (th.length - 1F), (float) (7 * Math.cos(a)), (float) (7 * Math.sin(a)), 3.2F + 0.25F * i, 0, 0, 3).fov(58);
      }
      int n = h.pipeCount();
      for (int i = 0; i < n; i++) {
         int k = n - 1 - i;
         b.server(10 + 14 * i, () -> h.pipe(k, false), true);
      }
      b.server(30, () -> h.channels(false, 100), false);
      b.server(120, () -> h.sirens(false), true);
      b.uiSound(10, "heralisz_n07:dungeon.amb_drone", 0.5F, 0.5F);
      // O2 (7-15 s) : la radio ; les lumieres reviennent, le teleporteur du Groupe 935 s'allume au milieu du hall
      float[] pad = inHall(l, l.pad, 0);
      b.shot("hall", 160).avoidWalls().key(0, pad[0] - 6, pad[1] + 4, 3, pad[0], pad[1], 0.8F).fov(55).key(1, pad[0] - 4.5F, pad[1] + 3, 2.4F, pad[0], pad[1], 0.8F)
         .fov(50);
      b.server(170, () -> h.lights(true, 30), false);
      b.server(185, () -> {
         h.teleporter(true);
         enc.hook(N07Api.HOOK_TELEPORTER_ON);
      }, true);
      b.subtitle(150, 70, S + "maxis", S + "outro.maxis1", MAXIS);
      b.subtitle(225, 75, S + "maxis", S + "outro.maxis2", MAXIS);
      // O3 (15-21 s) : noir, carte de titre ; O4 : retour dans les yeux du joueur
      b.shot("hall", 120).key(0, pad[0] - 4.5F, pad[1] + 3, 2.4F, pad[0], pad[1], 0.8F);
      b.fade(300, 20, 95, 14, 0xFF000000);
      b.title(318, 100, S + "outro.top", S + "outro.title", S + "outro.sub", GREEN);
      b.shot("hall", 30).toPlayer().key(0, pad[0] - 4.5F, pad[1] + 3, 2.4F, pad[0], pad[1], 0.8F);
      b.server(b.length() - 1, () -> enc.hook(N07Api.HOOK_OUTRO_END), true);
      // etat final garanti meme si la scene est interrompue
      b.server(b.length() - 1, () -> {
         if (!h.teleporterActive()) finalState.run();
      }, true);
      SceneHandle handle = SceneRunner.play(h.level, b.build(), viewers, skip, onEnd);
      enc.attachScene(handle);
      return handle;
   }

   static {
      // garde-fou de compilation : le raccord suppose la derniere cle d'apparition {1, 13, 11, 4.4, 5}
      if (SujetN07.Action.SPAWN.ticks() != 240) HeraliszN07.LOGGER.warn("[n07] duree d'apparition changee : verifier GiantScenes.playHandover");
   }

   /** Etat du combat utilise par les commandes de test. */
   public static boolean ready(Encounter e) {
      return e != null && e.spec().hall != null;
   }
}
