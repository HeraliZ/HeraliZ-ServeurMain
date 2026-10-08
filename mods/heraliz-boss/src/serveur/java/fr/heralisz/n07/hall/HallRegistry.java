package fr.heralisz.n07.hall;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.N07Events;
import fr.heralisz.n07.api.PlacedHall;
import fr.heralisz.n07.encounter.EncounterImpl;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Salles liees par N07Api (pour retrouver la salle d'un teleporteur) et le teleporteur 935 : un joueur qui reste ~1,5 s sur
 * la plateforme allumee (ou clic droit) declenche onReturnRequested. Cout : une recherche de joueurs dans une petite boite
 * toutes les 5 ticks, seulement quand un teleporteur est allume.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class HallRegistry {
   private static final List<PlacedHall> HALLS = new ArrayList<>();
   /** Joueur -> ticks passes sur la plateforme ; joueur -> fin du delai avant un nouvel envoi. */
   private static final Map<UUID, Integer> ON_PAD = new HashMap<>();
   private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
   private static final int CHARGE = 30;

   private HallRegistry() {}

   public static void add(PlacedHall h) {
      HALLS.removeIf(o -> o.level == h.level && o.origin.asLong() == h.origin.asLong());
      HALLS.add(h);
   }

   public static void remove(PlacedHall h) {
      HALLS.remove(h);
   }

   public static List<PlacedHall> all() {
      return List.copyOf(HALLS);
   }

   /** Ecouteur actif d'une salle : celui de son combat s'il y en a un, sinon le sien. */
   private static N07Events events(PlacedHall h) {
      if (h.encounter() instanceof EncounterImpl e) return e.spec().events;
      return h.events();
   }

   public static void fireReturn(PlacedHall h, ServerPlayer p, N07Events.ReturnReason why) {
      try {
         events(h).onReturnRequested(p, why);
      } catch (RuntimeException ex) {
         HeraliszN07.LOGGER.error("[n07] onReturnRequested en erreur", ex);
      }
   }

   /** Clic droit sur une plateforme allumee. */
   public static void padUsed(ServerPlayer p, BlockPos pos) {
      for (PlacedHall h : HALLS) {
         if (h.level == p.level() && h.world(h.layout.pad).asLong() == pos.asLong()) {
            send(h, p);
            return;
         }
      }
   }

   private static void send(PlacedHall h, ServerPlayer p) {
      long now = p.level().getGameTime();
      Long cd = COOLDOWN.get(p.getUUID());
      if (cd != null && now < cd) return;
      COOLDOWN.put(p.getUUID(), now + 60);
      ON_PAD.remove(p.getUUID());
      Vec3 c = p.position();
      Vfx.flash(h.level, c.add(0, 1, 0), 3F, 8, 0x9CE8FF);
      h.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 40, 0.4, 0.9, 0.4, 0.3);
      h.level.playSound(null, c.x, c.y, c.z, fr.heralisz.n07.sound.BossSounds.DG_ARC_ZAP.get(), SoundSource.BLOCKS, 2.5F, 0.7F);
      fireReturn(h, p, N07Events.ReturnReason.TELEPORTER);
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || HALLS.isEmpty()) return;
      HALLS.removeIf(h -> h.level.getServer() == null);
      for (PlacedHall h : HALLS) {
         if (h.level.getGameTime() % 5 != 0 || !h.teleporterActive()) continue;
         Vec3 c = h.padCenter();
         double r = h.layout.padRadius;
         for (ServerPlayer p : h.level.players()) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            boolean on = dx * dx + dz * dz <= r * r && Math.abs(p.getY() - c.y) < 1.5 && !p.isSpectator();
            if (!on) {
               ON_PAD.remove(p.getUUID());
               continue;
            }
            int t = ON_PAD.merge(p.getUUID(), 5, Integer::sum);
            h.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2 + t / 30.0, p.getZ(), 6, 0.35, 0.4, 0.35, 0.15);
            p.displayClientMessage(Component.translatable("hall.heralisz_n07.teleporter.charge", Math.min(100, t * 100 / CHARGE)), true);
            if (t >= CHARGE) send(h, p);
         }
      }
   }

   @SubscribeEvent
   public static void stopping(ServerStoppingEvent e) {
      HALLS.clear();
      ON_PAD.clear();
      COOLDOWN.clear();
   }
}
