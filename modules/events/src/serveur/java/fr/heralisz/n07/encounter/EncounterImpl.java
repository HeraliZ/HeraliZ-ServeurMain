package fr.heralisz.n07.encounter;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.Encounter;
import fr.heralisz.n07.api.EncounterSpec;
import fr.heralisz.n07.api.N07Api;
import fr.heralisz.n07.api.PlacedHall;
import fr.heralisz.n07.api.ZombiesBalance;
import fr.heralisz.n07.api.scene.SkipMode;
import fr.heralisz.n07.entity.BossRemains;
import fr.heralisz.n07.entity.HeraliszBoss;
import fr.heralisz.n07.entity.SujetN07;
import fr.heralisz.n07.entity.SujetN07Serveur;
import fr.heralisz.n07.scene.CinematicGuard;
import fr.heralisz.n07.scene.ScenePackets;
import fr.heralisz.n07.scene.SkipVote;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Combat pilote (interne) : le boss le consulte pour ses reglages (PV, degats, cibles, laisse, butin...) et le previent de
 * chaque etape ; il relaie aux evenements du jeu (N07Events) et gere le vote « passer » des cinematiques d'apparition et de
 * mort.
 */
public final class EncounterImpl implements Encounter {
   private final EncounterSpec spec;
   private SujetN07Serveur boss;
   private State state = State.PREPARED;
   private SkipVote vote;
   private int cineKind = -1;
   private List<ServerPlayer> cineViewers = List.of();
   private boolean deathSkipped;
   private BossRemains remains;
   /** Action a lancer 4 ticks avant la fin de l'apparition (raccord de l'intro), une fois. */
   private Runnable spawnEnding;
   private final List<Consumer<Encounter>> onVictory = new ArrayList<>();
   /** Reactions internes au changement de phase (repliques radio de l'intro de The Giant...). */
   private final List<java.util.function.IntConsumer> onPhaseInternal = new ArrayList<>();
   /** Appeles une fois si l'apparition est passee (vote ou intro passee). */
   private final List<Runnable> onSpawnSkipped = new ArrayList<>();
   /** Scenes liees au combat (intro, outro) : arretees si le combat est interrompu. */
   private final List<fr.heralisz.n07.api.scene.SceneHandle> scenes = new ArrayList<>();
   /** Position / lacet du boss au debut de l'apparition (cadrage du raccord de fin d'apparition). */
   private Vec3 spawnAnchor;
   private float spawnYaw;

   EncounterImpl(EncounterSpec spec) {
      this.spec = spec;
   }

   // ------------------------------------------------------------------ creation
   /** Cree le boss endormi dans sa cuve (ou a la position demandee). */
   boolean spawnDormant() {
      ServerLevel level = spec.level;
      if (!(HeraliszN07.SUJET_N07.get().create(level) instanceof SujetN07Serveur b)) return false;
      Vec3 pos = spec.hall != null ? spec.hall.bossFeet() : spec.spawnPos;
      float yaw = spec.hall != null ? spec.hall.bossYaw() : spec.spawnYaw;
      b.encounter = this;
      b.moveTo(pos.x, pos.y, pos.z, yaw, 0);
      b.setYHeadRot(yaw);
      b.setYBodyRot(yaw);
      b.setHome(net.minecraft.core.BlockPos.containing(pos));
      b.setDormant(true);
      if (!spec.music) b.setFlags(b.flags() | HeraliszBoss.FLAG_NO_MUSIC);
      mark(b);
      if (!level.addFreshEntity(b)) return false;
      boss = b;
      if (spec.hall != null) {
         spec.hall.attach(this);
         spec.hall.terminalReady();
      }
      state = State.PREPARED;
      return true;
   }

   public void onSpawnEnding(Runnable r) {
      spawnEnding = r;
   }

   public void onVictory(Consumer<Encounter> c) {
      onVictory.add(c);
   }

   public void onSpawnSkipped(Runnable r) {
      onSpawnSkipped.add(r);
   }

   public void attachScene(fr.heralisz.n07.api.scene.SceneHandle h) {
      if (h != null) scenes.add(h);
   }

   private void spawnSkipped() {
      spawnEnding = null;
      if (boss != null) {
         boss.skipSpawn();
         cinematicEnd(boss, N07Api.CINE_SPAWN);
      }
      List<Runnable> rs = new ArrayList<>(onSpawnSkipped);
      onSpawnSkipped.clear();
      for (Runnable r : rs) fire(r);
   }

   public void onPhaseInternal(java.util.function.IntConsumer c) {
      onPhaseInternal.add(c);
   }

   public Vec3 spawnAnchor() {
      return spawnAnchor;
   }

   public float spawnYaw() {
      return spawnYaw;
   }

   public SujetN07Serveur sujet() {
      return boss != null && !boss.isRemoved() ? boss : null;
   }

   public List<ServerPlayer> viewers() {
      return viewers(spec.level);
   }

   /** Reveil sans cinematique (intro passee) : la cuve cede, il se pose, le combat commence tout de suite. */
   public void wakeSkipped() {
      if (state == State.PREPARED) wake();
      if (state != State.SPAWNING) return;
      if (cineKind == N07Api.CINE_SPAWN) skipCinematic();
      else spawnSkipped();
   }

   public boolean deathSkipped() {
      return deathSkipped;
   }

   public BossRemains remainsEntity() {
      return remains;
   }

   // ------------------------------------------------------------------ Encounter
   @Override
   public State state() {
      return state;
   }

   @Override
   public EncounterSpec spec() {
      return spec;
   }

   @Override
   public Mob boss() {
      return boss != null && !boss.isRemoved() ? boss : null;
   }

   @Override
   public int phase() {
      return boss != null ? boss.phase() : 1;
   }

   @Override
   public float healthFraction() {
      return boss == null || boss.isRemoved() ? 0 : boss.getHealth() / boss.getMaxHealth();
   }

   @Override
   public int virtualHp() {
      return (int) virtualHp(spec.playerCount);
   }

   @Override
   public void wake() {
      if (state != State.PREPARED || boss == null || boss.isRemoved()) return;
      if (spec.hall != null) {
         spec.hall.terminalAwake(true);
         spec.hall.gate(true);
      }
      state = State.SPAWNING;
      spawnAnchor = boss.position();
      spawnYaw = boss.yBodyRot;
      boss.wake();
   }

   @Override
   public void skipCinematic() {
      if (boss == null || cineKind < 0) return;
      int kind = cineKind;
      List<ServerPlayer> vs = viewers(spec.level);
      Vfx.cinematicStop(vs, boss.getId());
      CinematicGuard.release(vs);
      if (kind == N07Api.CINE_SPAWN) {
         cineKind = -1;
         spawnSkipped();
      } else if (kind == N07Api.CINE_DEATH) {
         deathSkipped = true;
         boss.skipDeath();
      }
      cineKind = -1;
   }

   @Override
   public void abort(String reason) {
      if (!active() && state != State.VICTORY) return;
      boolean wasActive = active();
      state = State.ABORTED;
      for (fr.heralisz.n07.api.scene.SceneHandle h : scenes) if (h.running()) h.cancel();
      scenes.clear();
      if (boss != null && !boss.isRemoved()) {
         List<ServerPlayer> vs = viewers(spec.level);
         Vfx.cinematicStop(vs, boss.getId());
         CinematicGuard.release(vs);
         boss.clearHazards();
         // flaques, alertes et debris encore affiches chez les clients
         Vfx.clear(spec.level, boss.position(), 72);
         SujetN07Serveur b = boss;
         boss = null;
         b.encounter = null;
         b.discard();
      }
      if (remains != null && !remains.isRemoved()) remains.discard();
      if (spec.hall != null && spec.hall.encounter() == this) spec.hall.attach(null);
      EncounterManager.remove(this);
      if (wasActive) {
         try {
            spec.events.onAborted(this, reason);
         } catch (RuntimeException ex) {
            HeraliszN07.LOGGER.error("[n07] onAborted en erreur", ex);
         }
      }
   }

   @Override
   public boolean active() {
      return state == State.PREPARED || state == State.SPAWNING || state == State.FIGHT || state == State.DYING;
   }

   // ------------------------------------------------------------------ appels du boss
   public boolean targetable(Player p) {
      return spec.targetable.test(p);
   }

   public float virtualHp(int engaged) {
      if (spec.virtualHp > 0) return spec.virtualHp;
      return ZombiesBalance.virtualHp(spec.hpFollowsEngaged ? engaged : spec.playerCount, spec.round, spec.papOnBossHits);
   }

   public float remap(Player p, String kind, float base, float current, int phase) {
      if (spec.damageRemap == null) return current;
      try {
         return Math.max(0, spec.damageRemap.remap(p, kind, base, current, phase));
      } catch (RuntimeException ex) {
         HeraliszN07.LOGGER.error("[n07] remappage des degats en erreur", ex);
         return current;
      }
   }

   public float remapEffect(Player p, String kind, float d) {
      return remap(p, kind, d, d * spec.damageMultiplier, phase());
   }

   public float hitCap() {
      return spec.hitCap;
   }

   public float dpsCap() {
      return spec.dpsCap;
   }

   public float damageMultiplier() {
      return spec.damageMultiplier;
   }

   public AABB arena() {
      return spec.arena;
   }

   public boolean leash() {
      return spec.leash;
   }

   public boolean loot() {
      return spec.loot;
   }

   public boolean remains() {
      return spec.remains;
   }

   public int remainsLife() {
      return spec.remainsLifeTicks;
   }

   public void remainsSpawned(BossRemains r) {
      remains = r;
   }

   public void mark(Entity e) {
      if (spec.marker == null) return;
      try {
         spec.marker.accept(e);
      } catch (RuntimeException ex) {
         HeraliszN07.LOGGER.error("[n07] marqueur d'entite en erreur", ex);
      }
   }

   /** Joueurs du combat presents dans ce monde. */
   public List<ServerPlayer> viewers(ServerLevel level) {
      List<ServerPlayer> out = new ArrayList<>();
      List<ServerPlayer> all;
      try {
         all = spec.players.get();
      } catch (RuntimeException ex) {
         all = List.of();
      }
      for (ServerPlayer p : all) if (p != null && p.level() == level && !p.isRemoved()) out.add(p);
      return out;
   }

   public boolean skippable(int kind) {
      return (kind == N07Api.CINE_SPAWN || kind == N07Api.CINE_DEATH) && spec.skipMode != SkipMode.NONE;
   }

   public void cinematicStart(HeraliszBoss<?> b, int kind, int ticks, List<ServerPlayer> viewers) {
      if (skippable(kind)) {
         cineKind = kind;
         vote = new SkipVote(spec.skipMode);
         cineViewers = viewers;
      }
      fire(() -> spec.events.onCinematicStart(this, kind, ticks, viewers));
   }

   public void actionTick(HeraliszBoss<?> b, int ordinal, int t, int ticks) {
      if (ordinal == 1 && t == ticks - 4 && spawnEnding != null) {
         Runnable r = spawnEnding;
         spawnEnding = null;
         fire(r);
      }
      if (ordinal == 3 && t == SujetN07.P3_STAB && spec.blackoutPhase3 && spec.hall != null) {
         spec.hall.ceilingLights(false, 14);
         hook(N07Api.HOOK_P3_BLACKOUT);
      }
   }

   public void cinematicEnd(HeraliszBoss<?> b, int kind) {
      if (kind == cineKind) cineKind = -1;
      if (kind == N07Api.CINE_SPAWN && state == State.SPAWNING) state = State.FIGHT;
      fire(() -> spec.events.onCinematicEnd(this, kind));
      if (kind == N07Api.CINE_SPAWN) hook(N07Api.HOOK_FIGHT);
      else if (kind == N07Api.CINE_PHASE2) hook(N07Api.HOOK_P2_END);
      else if (kind == N07Api.CINE_PHASE3) hook(N07Api.HOOK_P3_END);
   }

   public void phase(HeraliszBoss<?> b, int phase) {
      fire(() -> spec.events.onPhase(this, phase));
      for (java.util.function.IntConsumer c : onPhaseInternal) fire(() -> c.accept(phase));
      hook(phase == 2 ? N07Api.HOOK_P2_START : N07Api.HOOK_P3_START);
   }

   public void deathStart(HeraliszBoss<?> b) {
      state = State.DYING;
      fire(() -> spec.events.onDeathStart(this));
   }

   public void deathEnd(HeraliszBoss<?> b) {
      if (state == State.ABORTED) return;
      state = State.VICTORY;
      cineKind = -1;
      EncounterManager.remove(this);
      fire(() -> spec.events.onDeath(this));
      for (Consumer<Encounter> c : onVictory) fire(() -> c.accept(this));
   }

   public void removed(HeraliszBoss<?> b, Entity.RemovalReason r) {
      if (b != boss) return;
      if (active() && state != State.DYING) {
         boss = null;
         abort(r == Entity.RemovalReason.KILLED ? "boss tue hors scenario" : "boss retire (" + r + ")");
      }
   }

   /** Crochet du scenario vers le jeu (onHook). */
   public void hook(String h) {
      fire(() -> spec.events.onHook(this, h));
   }

   private static void fire(Runnable r) {
      try {
         r.run();
      } catch (RuntimeException ex) {
         HeraliszN07.LOGGER.error("[n07] evenement du combat en erreur", ex);
      }
   }

   // ------------------------------------------------------------------ vote « passer » (apparition, mort)
   boolean watching(ServerPlayer p) {
      return cineKind >= 0 && cineViewers.contains(p);
   }

   void hold(ServerPlayer p, boolean on) {
      if (cineKind < 0 || vote == null || !vote.hold(p.getUUID(), on)) return;
      List<ServerPlayer> vs = viewers(spec.level);
      if (vote.mode == SkipMode.PER_PLAYER) {
         if (on && boss != null) {
            Vfx.cinematicStop(List.of(p), boss.getId());
            CinematicGuard.release(List.of(p));
         }
         return;
      }
      ScenePackets.send(vs, new ScenePackets.Votes(-1, vote.count(vs), vote.needed(vs.size())));
      if (vote.decided(vs)) skipCinematic();
   }
}
