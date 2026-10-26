package fr.heralisz.n07.hall;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pose (ou remise a l'identique) d'une structure du mod par tranches de N blocs par tick : pas de gel du serveur. Les blocs
 * sont poses sans mise a jour des voisins (comme une structure vanilla), les entites de bloc recoivent leurs donnees.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class HallPlacer {
   /** Structures lues (palette + blocs), par nom. */
   private static final Map<String, Template> TEMPLATES = new ConcurrentHashMap<>();
   private static final List<Job> JOBS = new ArrayList<>();

   private HallPlacer() {}

   record Entry(BlockPos pos, int state, CompoundTag nbt) {}

   static final class Template {
      final List<CompoundTag> palette = new ArrayList<>();
      final List<Entry> blocks = new ArrayList<>();
      BlockState[] states;
   }

   static Template template(ServerLevel level, String name) {
      Template t = TEMPLATES.computeIfAbsent(name, n -> {
         String path = "/data/heralisz_n07/structures/" + n + ".nbt";
         try (InputStream in = ModResources.open(path.substring(1))) {
            if (in == null) return null;
            CompoundTag root = NbtIo.readCompressed(in);
            Template tp = new Template();
            for (Tag e : root.getList("palette", Tag.TAG_COMPOUND)) tp.palette.add((CompoundTag) e);
            for (Tag e : root.getList("blocks", Tag.TAG_COMPOUND)) {
               CompoundTag b = (CompoundTag) e;
               ListTag p = b.getList("pos", Tag.TAG_INT);
               tp.blocks.add(new Entry(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), b.getInt("state"), b.contains("nbt") ? b.getCompound("nbt") : null));
            }
            return tp;
         } catch (Exception ex) {
            HeraliszN07.LOGGER.error("[n07] structure illisible : {}", path, ex);
            return null;
         }
      });
      if (t != null && t.states == null) {
         var lookup = level.holderLookup(Registries.BLOCK);
         BlockState[] st = new BlockState[t.palette.size()];
         for (int i = 0; i < st.length; i++) st[i] = NbtUtils.readBlockState(lookup, t.palette.get(i));
         t.states = st;
      }
      return t;
   }

   /** Une pose en cours. diffOnly : ne touche que les blocs differents du gabarit (remise a zero). */
   static final class Job {
      final ServerLevel level;
      final Template t;
      final BlockPos origin;
      final Rotation rot;
      final int perTick;
      final boolean diffOnly;
      /** Vide le volume (air) au lieu de poser. */
      boolean clear;
      final Runnable done;
      int index, changed;

      Job(ServerLevel level, Template t, BlockPos origin, Rotation rot, int perTick, boolean diffOnly, Runnable done) {
         this.level = level;
         this.t = t;
         this.origin = origin;
         this.rot = rot;
         this.perTick = perTick <= 0 ? Integer.MAX_VALUE : perTick;
         this.diffOnly = diffOnly;
         this.done = done;
      }

      /** Rend true quand c'est fini. */
      boolean step() {
         int end = (int) Math.min((long) index + perTick, t.blocks.size());
         for (; index < end; index++) {
            Entry e = t.blocks.get(index);
            BlockPos p = origin.offset(StructureTemplate.transform(e.pos(), Mirror.NONE, rot, BlockPos.ZERO));
            BlockState want = clear ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : t.states[e.state()].rotate(rot);
            BlockState have = level.getBlockState(p);
            if (have == want && (diffOnly || e.nbt() == null)) continue;
            if (have != want) {
               level.setBlock(p, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
               changed++;
            }
            if (!clear && e.nbt() != null && (!diffOnly || have.getBlock() != want.getBlock())) {
               BlockEntity be = level.getBlockEntity(p);
               if (be != null) {
                  CompoundTag tag = e.nbt().copy();
                  tag.putInt("x", p.getX());
                  tag.putInt("y", p.getY());
                  tag.putInt("z", p.getZ());
                  be.load(tag);
                  be.setChanged();
                  level.sendBlockUpdated(p, want, want, Block.UPDATE_CLIENTS);
               }
            }
         }
         return index >= t.blocks.size();
      }
   }

   /**
    * Pose la structure (ou la remet a l'identique si diffOnly). perTick &lt;= 0 : tout de suite. done : appele a la fin (fil
    * serveur). Rend false si la structure est introuvable.
    */
   public static boolean place(ServerLevel level, String name, BlockPos origin, Rotation rot, int perTick, boolean diffOnly, Runnable done) {
      Template t = template(level, name);
      if (t == null) return false;
      Job j = new Job(level, t, origin, rot, perTick, diffOnly, done);
      if (perTick <= 0) {
         j.step();
         if (done != null) done.run();
      } else {
         JOBS.add(j);
      }
      return true;
   }

   /** Vide (air) toutes les cases de la structure posee la, par tranches. */
   public static boolean clear(ServerLevel level, String name, BlockPos origin, Rotation rot, int perTick, Runnable done) {
      Template t = template(level, name);
      if (t == null) return false;
      Job j = new Job(level, t, origin, rot, perTick, true, done);
      j.clear = true;
      if (perTick <= 0) {
         j.step();
         if (done != null) done.run();
      } else {
         JOBS.add(j);
      }
      return true;
   }

   /** Nombre d'entrees d'une structure (pour estimer la duree d'une pose). */
   public static int entries(ServerLevel level, String name) {
      Template t = template(level, name);
      return t == null ? 0 : t.blocks.size();
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
      Iterator<Job> it = JOBS.iterator();
      // une pose a la fois : le budget par tick est respecte
      Job j = it.next();
      boolean fin;
      try {
         fin = j.step();
      } catch (RuntimeException ex) {
         HeraliszN07.LOGGER.error("[n07] pose de structure interrompue", ex);
         fin = true;
      }
      if (fin) {
         JOBS.remove(j);
         if (j.done != null) {
            try {
               j.done.run();
            } catch (RuntimeException ex) {
               HeraliszN07.LOGGER.error("[n07] fin de pose en erreur", ex);
            }
         }
      }
   }
}
