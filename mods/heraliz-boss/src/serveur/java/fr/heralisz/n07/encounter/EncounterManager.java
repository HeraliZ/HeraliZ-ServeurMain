package fr.heralisz.n07.encounter;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.Encounter;
import fr.heralisz.n07.api.EncounterSpec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Combats pilotes en cours (interne) : creation, recherche par joueur, votes « passer », garde-fous. */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class EncounterManager {
   private static final List<EncounterImpl> ACTIVE = new ArrayList<>();

   private EncounterManager() {}

   /** Cree le combat et son boss endormi ; null si impossible. */
   public static EncounterImpl create(EncounterSpec spec) {
      EncounterImpl e = new EncounterImpl(spec);
      if (!e.spawnDormant()) return null;
      ACTIVE.add(e);
      return e;
   }

   static void remove(EncounterImpl e) {
      ACTIVE.remove(e);
   }

   public static List<Encounter> all() {
      return List.copyOf(ACTIVE);
   }

   /** Combat dont ce joueur fait partie (null : aucun). */
   public static EncounterImpl forPlayer(ServerPlayer p) {
      for (EncounterImpl e : ACTIVE) if (e.viewers(e.spec().level).contains(p)) return e;
      return null;
   }

   /** Appui « passer » pendant une cinematique du boss (run = -1). */
   public static void onSkipHold(ServerPlayer p, boolean on) {
      for (EncounterImpl e : new ArrayList<>(ACTIVE)) {
         if (e.watching(p)) {
            e.hold(p, on);
            return;
         }
      }
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent ev) {
      if (ev.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
      for (EncounterImpl e : new ArrayList<>(ACTIVE)) {
         // garde-fou : un boss qui n'existe plus (decharge sans evenement) interrompt le combat
         if (e.state() != Encounter.State.PREPARED && e.boss() == null && e.active() && e.state() != Encounter.State.DYING) e.abort("boss introuvable");
      }
   }

   @SubscribeEvent
   public static void stopping(ServerStoppingEvent ev) {
      ACTIVE.clear();
   }
}
