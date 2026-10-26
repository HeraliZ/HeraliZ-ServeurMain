package fr.heralisz.n07.scene;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.scene.Scene;
import fr.heralisz.n07.api.scene.SceneHandle;
import fr.heralisz.n07.api.scene.SceneResult;
import fr.heralisz.n07.api.scene.SkipMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Deroule les scenes cote serveur : envoie la scene aux spectateurs, les protege (CinematicGuard), joue les actions serveur a
 * leur tick, compte les votes « passer », previent l'appelant a la fin. Cout : quelques operations par scene et par tick.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class SceneRunner {
   private static final Map<Integer, Run> RUNS = new LinkedHashMap<>();
   private static int nextId = 1;

   private SceneRunner() {}

   public static SceneHandle play(ServerLevel level, Scene scene, Collection<ServerPlayer> viewers, SkipMode mode, Consumer<SceneResult> onEnd) {
      Run r = new Run(nextId++, level, scene, viewers, scene.overlayOnly ? SkipMode.NONE : mode, onEnd);
      RUNS.put(r.id, r);
      if (!scene.overlayOnly) CinematicGuard.protect(r.viewers(), scene.length + 10);
      ScenePackets.send(r.viewers(), new ScenePackets.Start(r.id, scene, r.vote.mode.ordinal()));
      // actions du tick 0 tout de suite
      r.runCues(0);
      return r;
   }

   /** Scene en cours dont ce joueur est spectateur (ou null). */
   public static Run runOf(ServerPlayer p) {
      for (Run r : RUNS.values()) if (!r.scene.overlayOnly && r.ids.contains(p.getUUID())) return r;
      return null;
   }

   public static void onHold(ServerPlayer p, int run, boolean on) {
      if (run < 0) {
         fr.heralisz.n07.encounter.EncounterManager.onSkipHold(p, on);
         return;
      }
      Run r = RUNS.get(run);
      if (r != null) r.hold(p, on);
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
      for (Run r : new ArrayList<>(RUNS.values())) r.tick();
   }

   @SubscribeEvent
   public static void stopping(ServerStoppingEvent e) {
      RUNS.clear();
   }

   public static final class Run implements SceneHandle {
      final int id;
      final ServerLevel level;
      final Scene scene;
      final Set<UUID> ids = new HashSet<>();
      final SkipVote vote;
      final Consumer<SceneResult> onEnd;
      final Set<UUID> personal = new HashSet<>();
      int time, nextCue;
      boolean done;

      Run(int id, ServerLevel level, Scene scene, Collection<ServerPlayer> viewers, SkipMode mode, Consumer<SceneResult> onEnd) {
         this.id = id;
         this.level = level;
         this.scene = scene;
         for (ServerPlayer p : viewers) ids.add(p.getUUID());
         this.vote = new SkipVote(mode);
         this.onEnd = onEnd;
      }

      @Override
      public Scene scene() {
         return scene;
      }

      @Override
      public int time() {
         return time;
      }

      @Override
      public boolean running() {
         return !done;
      }

      @Override
      public List<ServerPlayer> viewers() {
         List<ServerPlayer> out = new ArrayList<>();
         for (UUID u : ids) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p != null) out.add(p);
         }
         return out;
      }

      void runCues(int upTo) {
         List<Scene.ServerCue> list = scene.serverCues;
         while (nextCue < list.size() && list.get(nextCue).tick() <= upTo) {
            Scene.ServerCue c = list.get(nextCue++);
            try {
               c.action().run();
            } catch (RuntimeException ex) {
               HeraliszN07.LOGGER.error("[n07] action de scene {} (tick {}) en erreur", scene.id, c.tick(), ex);
            }
         }
      }

      private void runEssentials() {
         List<Scene.ServerCue> list = scene.serverCues;
         while (nextCue < list.size()) {
            Scene.ServerCue c = list.get(nextCue++);
            if (!c.essential()) continue;
            try {
               c.action().run();
            } catch (RuntimeException ex) {
               HeraliszN07.LOGGER.error("[n07] action de scene {} (passage) en erreur", scene.id, ex);
            }
         }
      }

      void tick() {
         if (done) return;
         time++;
         List<ServerPlayer> vs = viewers();
         if (vs.isEmpty()) {
            // plus personne : la suite est appliquee d'un coup (etat coherent), sans image
            runEssentials();
            finish(SceneResult.CANCELLED);
            return;
         }
         if (time % 20 == 0 && !scene.overlayOnly) {
            for (ServerPlayer p : vs) if (!personal.contains(p.getUUID())) CinematicGuard.protect(p, scene.length - time + 10);
         }
         runCues(time);
         if (time >= scene.length) finish(SceneResult.COMPLETED);
      }

      void hold(ServerPlayer p, boolean on) {
         if (done || !ids.contains(p.getUUID()) || !vote.hold(p.getUUID(), on)) return;
         List<ServerPlayer> vs = viewers();
         if (vote.mode == SkipMode.PER_PLAYER) {
            if (on && personal.add(p.getUUID())) {
               ScenePackets.send(p, new ScenePackets.Stop(id));
               CinematicGuard.release(List.of(p));
            }
            return;
         }
         ScenePackets.send(vs, new ScenePackets.Votes(id, vote.count(vs), vote.needed(vs.size())));
         if (vote.decided(vs)) skip();
      }

      @Override
      public void skip() {
         if (done) return;
         runEssentials();
         finish(SceneResult.SKIPPED);
      }

      @Override
      public void cancel() {
         if (done) return;
         finish(SceneResult.CANCELLED);
      }

      private void finish(SceneResult res) {
         done = true;
         RUNS.remove(id);
         List<ServerPlayer> vs = viewers();
         if (res != SceneResult.COMPLETED) {
            ScenePackets.send(vs, new ScenePackets.Stop(id));
            CinematicGuard.release(vs);
         }
         if (onEnd != null) {
            try {
               onEnd.accept(res);
            } catch (RuntimeException ex) {
               HeraliszN07.LOGGER.error("[n07] fin de scene {} en erreur", scene.id, ex);
            }
         }
      }
   }
}
