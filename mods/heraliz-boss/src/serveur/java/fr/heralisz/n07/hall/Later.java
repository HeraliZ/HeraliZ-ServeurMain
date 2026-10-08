package fr.heralisz.n07.hall;

import fr.heralisz.n07.HeraliszN07;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Petit ordonnanceur serveur : actions differees de quelques ticks (vagues de lumieres, rigoles qui se remplissent...). */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class Later {
   private record Task(long at, Runnable r) {}

   private static final List<Task> TASKS = new ArrayList<>();
   private static long now;

   private Later() {}

   public static void run(int delay, Runnable r) {
      if (delay <= 0) {
         r.run();
         return;
      }
      TASKS.add(new Task(now + delay, r));
   }

   /** Applique each a chaque element, etale sur ticks (ordre conserve). */
   public static <T> void spread(List<T> items, int ticks, Consumer<T> each) {
      if (items.isEmpty()) return;
      if (ticks <= 0) {
         for (T t : items) each.accept(t);
         return;
      }
      int n = items.size();
      for (int i = 0; i < n; i++) {
         T t = items.get(i);
         run((int) ((long) i * ticks / n), () -> each.accept(t));
      }
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      now++;
      if (TASKS.isEmpty()) return;
      List<Task> due = new ArrayList<>();
      TASKS.removeIf(t -> {
         if (t.at() > now) return false;
         due.add(t);
         return true;
      });
      for (Task t : due) {
         try {
            t.r().run();
         } catch (RuntimeException ex) {
            HeraliszN07.LOGGER.error("[n07] action differee en erreur", ex);
         }
      }
   }

   @SubscribeEvent
   public static void stopping(ServerStoppingEvent e) {
      TASKS.clear();
   }
}
