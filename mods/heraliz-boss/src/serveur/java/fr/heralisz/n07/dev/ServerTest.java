package fr.heralisz.n07.dev;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.dungeon.AlertLightBlock;
import fr.heralisz.n07.dungeon.ArenaGateBlock;
import fr.heralisz.n07.dungeon.ArenaGateBlockEntity;
import fr.heralisz.n07.dungeon.DungeonCommand;
import fr.heralisz.n07.dungeon.DungeonKind;
import fr.heralisz.n07.dungeon.ShrineBlockEntity;
import fr.heralisz.n07.dungeon.ShrineBlockEntityServeur;
import fr.heralisz.n07.dungeon.SirenBlock;
import fr.heralisz.n07.dungeon.SwitchLightBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Essai sur serveur dédié (CI uniquement, activé par -Dheralisz.servertest=true) : pose chaque donjon (une orientation
 * différente à chaque fois), réveille chaque boss, vérifie l'alerte et la grille, tue le boss, attend la remise en état,
 * fait apparaître les boss sans donjon, mesure le temps de tick puis arrête le serveur. Résultat : servertest.txt.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class ServerTest {
   private static final boolean ON = Boolean.getBoolean("heralisz.servertest");
   private static final int SPACING = 400, Y = 60, R = 7;
   private static final int T_PLACE = 40, T_WAKE = 260, T_KILL = 560, T_CHECK = T_KILL + ShrineBlockEntity.RESTORE_DELAY + 80, T_END = T_CHECK + 20;

   private static final List<String> LOG = new ArrayList<>();
   private static final List<ShrineBlockEntityServeur> SHRINES = new ArrayList<>();
   private static final List<Mob> LOOSE = new ArrayList<>();
   private static int tick = -1, fails;
   private static double tickMsSum, tickMsMax;
   private static int tickMsN;

   private ServerTest() {}

   @SubscribeEvent
   public static void started(ServerStartedEvent e) {
      if (!ON) return;
      tick = 0;
      log("serveur démarré, dedicated=" + e.getServer().isDedicatedServer());
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent e) {
      if (!ON || tick < 0 || e.phase != TickEvent.Phase.END) return;
      MinecraftServer server = e.getServer();
      ServerLevel level = server.overworld();
      tick++;
      try {
         if (tick == 5) forceChunks(level);
         if (tick == T_PLACE) place(server, level);
         if (tick == T_WAKE) wake(level);
         if (tick > T_WAKE + 20 && tick < T_KILL) {
            double ms = server.getAverageTickTime();
            tickMsSum += ms;
            tickMsN++;
            tickMsMax = Math.max(tickMsMax, ms);
         }
         if (tick == T_KILL) kill(level);
         if (tick == T_CHECK) check(level);
      } catch (Throwable t) {
         fail("exception au tick " + tick + " : " + t);
         HeraliszN07.LOGGER.error("Essai serveur", t);
      }
      if (tick == T_END) {
         log(String.format("tick moyen pendant les combats : %.2f ms (max %.2f ms)", tickMsN == 0 ? 0 : tickMsSum / tickMsN, tickMsMax));
         log(fails == 0 ? "SERVERTEST OK" : "SERVERTEST FAIL (" + fails + ")");
         try {
            Files.write(Path.of("servertest.txt"), LOG);
         } catch (IOException ex) {
            HeraliszN07.LOGGER.error("servertest.txt", ex);
         }
         server.halt(false);
      }
   }

   private static BlockPos target(int i) {
      return new BlockPos(i * SPACING, Y, 0);
   }

   private static void forceChunks(ServerLevel level) {
      int n = DungeonKind.values().length;
      for (int i = 0; i <= n; i++) {
         ChunkPos c = new ChunkPos(target(i));
         for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) level.setChunkForced(c.x + dx, c.z + dz, true);
         }
      }
      log("chunks forcés");
   }

   private static void place(MinecraftServer server, ServerLevel level) {
      DungeonKind[] kinds = DungeonKind.values();
      for (int i = 0; i < kinds.length; i++) {
         BlockPos t = target(i);
         float yaw = (i * 90) % 360;
         long t0 = System.nanoTime();
         int ok = DungeonCommand.place(server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atBottomCenterOf(t))
            .withRotation(new Vec2(0, yaw)).withSuppressedOutput(), kinds[i]);
         long ms = (System.nanoTime() - t0) / 1_000_000;
         ShrineBlockEntityServeur s = shrine(level, t);
         if (ok != 1 || s == null) {
            fail(kinds[i].id + " : pose ratée (ok=" + ok + ", sanctuaire=" + s + ")");
            continue;
         }
         SHRINES.add(s);
         log(kinds[i].id + " posé (orientation " + yaw + "°) en " + ms + " ms, sanctuaire " + s.getBlockPos().toShortString());
      }
   }

   private static ShrineBlockEntityServeur shrine(ServerLevel level, BlockPos at) {
      ChunkPos c = new ChunkPos(at);
      for (int dx = -R; dx <= R; dx++) {
         for (int dz = -R; dz <= R; dz++) {
            for (BlockEntity be : level.getChunk(c.x + dx, c.z + dz).getBlockEntities().values()) {
               if (be instanceof ShrineBlockEntityServeur s) return s;
            }
         }
      }
      return null;
   }

   private static void wake(ServerLevel level) {
      for (ShrineBlockEntityServeur s : SHRINES) {
         boolean ok = s.forceAwaken(level);
         Mob b = s.boss(level);
         int[] gates = gates(level, s.getBlockPos());
         int[] lights = lights(level, s.getBlockPos());
         String st = s.kind.id + " : réveil=" + ok + ", état=" + s.state() + ", alerte=" + s.alert() + ", boss=" + (b == null ? "aucun" : b.getType().getDescriptionId())
            + ", grilles fermées " + gates[1] + "/" + gates[0] + ", néons allumés " + lights[1] + "/" + lights[0] + ", sirènes allumées " + lights[3] + "/" + lights[2] + ", feux d'alerte allumés " + lights[5] + "/" + lights[4];
         if (!ok || s.state() != ShrineBlockEntity.AWAKE || !s.alert() || b == null || gates[0] == 0 || gates[1] != gates[0] || lights[1] != 0
            || lights[3] != lights[2] || lights[5] != lights[4]) fail(st);
         else log(st);
      }
   }

   /** [grilles, fermées] autour du sanctuaire. */
   private static int[] gates(ServerLevel level, BlockPos at) {
      int n = 0, closed = 0;
      ChunkPos c = new ChunkPos(at);
      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            for (BlockEntity be : level.getChunk(c.x + dx, c.z + dz).getBlockEntities().values()) {
               if (be instanceof ArenaGateBlockEntity) {
                  n++;
                  if (be.getBlockState().getValue(ArenaGateBlock.CLOSED)) closed++;
               }
            }
         }
      }
      return new int[] {n, closed};
   }

   /** [néons, allumés, sirènes, allumées, feux d'alerte, allumés] dans la zone d'alerte du sanctuaire. */
   private static int[] lights(ServerLevel level, BlockPos at) {
      int[] n = new int[6];
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      for (int x = -48; x <= 48; x++) {
         for (int z = -48; z <= 48; z++) {
            for (int y = -10; y <= 28; y++) {
               var st = level.getBlockState(p.set(at.getX() + x, at.getY() + y, at.getZ() + z));
               if (st.getBlock() instanceof SwitchLightBlock) {
                  n[0]++;
                  if (st.getValue(SwitchLightBlock.LIT)) n[1]++;
               } else if (st.getBlock() instanceof SirenBlock) {
                  n[2]++;
                  if (st.getValue(SirenBlock.LIT)) n[3]++;
               } else if (st.getBlock() instanceof AlertLightBlock) {
                  n[4]++;
                  if (st.getValue(AlertLightBlock.LIT)) n[5]++;
               }
            }
         }
      }
      return n;
   }

   private static void kill(ServerLevel level) {
      for (ShrineBlockEntityServeur s : SHRINES) {
         Mob b = s.boss(level);
         if (b == null) {
            fail(s.kind.id + " : plus de boss avant la mise à mort");
            continue;
         }
         b.kill();
      }
      for (Mob m : LOOSE) {
         if (!m.isAlive()) fail("mort prématurée : " + m.getType().getDescriptionId());
         m.kill();
      }
      log("boss tués");
   }

   private static void check(ServerLevel level) {
      for (ShrineBlockEntityServeur s : SHRINES) {
         int[] gates = gates(level, s.getBlockPos());
         int[] lights = lights(level, s.getBlockPos());
         String st = s.kind.id + " : après la mort état=" + s.state() + ", alerte=" + s.alert() + ", grilles fermées " + gates[1] + "/" + gates[0]
            + ", néons allumés " + lights[1] + "/" + lights[0] + ", sirènes allumées " + lights[3] + "/" + lights[2] + ", feux d'alerte allumés "
            + lights[5] + "/" + lights[4];
         if (s.state() != ShrineBlockEntity.COOLDOWN || s.alert() || gates[1] != 0 || lights[1] != lights[0] || lights[3] != 0 || lights[5] != 0) fail(st);
         else log(st);
      }
   }

   private static void log(String s) {
      LOG.add(s);
      HeraliszN07.LOGGER.info("[essai serveur] {}", s);
   }

   private static void fail(String s) {
      fails++;
      log("ÉCHEC " + s);
   }
}
