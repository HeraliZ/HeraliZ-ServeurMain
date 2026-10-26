package fr.heralisz.n07.api;

import fr.heralisz.n07.api.scene.Scene;
import fr.heralisz.n07.dungeon.ArenaGateBlockEntity;
import fr.heralisz.n07.dungeon.ArenaGates;
import fr.heralisz.n07.dungeon.LampBlock;
import fr.heralisz.n07.dungeon.PropBlockEntityServeur;
import fr.heralisz.n07.dungeon.RadChannelBlock;
import fr.heralisz.n07.dungeon.ShrineBlockEntity;
import fr.heralisz.n07.dungeon.ShrineBlockEntityServeur;
import fr.heralisz.n07.dungeon.SirenBlock;
import fr.heralisz.n07.dungeon.SwitchLightBlock;
import fr.heralisz.n07.dungeon.Teleporter935Block;
import fr.heralisz.n07.hall.HallPlacer;
import fr.heralisz.n07.hall.HallRegistry;
import fr.heralisz.n07.hall.Later;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Une salle du N-07 posee dans le monde (hall + puits du monte-charge) : reperes en coordonnees du monde, commandes de son
 * etat (buses, rigoles, terminal, alerte, lumieres, teleporteur), remise a zero, evacuation. Fil serveur.
 * Obtenue par N07Api.placeHall (pose) ou N07Api.bindHall (salle deja posee, ex. par le generateur de carte).
 */
public final class PlacedHall {
   public final ServerLevel level;
   public final HallLayout layout;
   /** Origine (coin min de la structure non tournee) et rotation de pose (pivot 0,0,0, comme StructureTemplate). */
   public final BlockPos origin;
   public final Rotation rotation;
   private N07Events events = new N07Events() {
   };
   private Encounter encounter;

   public PlacedHall(ServerLevel level, HallLayout layout, BlockPos origin, Rotation rotation) {
      this.level = level;
      this.layout = layout;
      this.origin = origin;
      this.rotation = rotation;
   }

   // ------------------------------------------------------------------ reperes
   public BlockPos world(BlockPos local) {
      return origin.offset(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO));
   }

   public Vec3 world(Vec3 local) {
      return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO).add(origin.getX(), origin.getY(), origin.getZ());
   }

   /** Centre-bas d'un bloc local (pieds). */
   public Vec3 feet(BlockPos local) {
      return world(new Vec3(local.getX() + 0.5, local.getY(), local.getZ() + 0.5));
   }

   /** Lacet local -> lacet du monde. */
   public float yaw(float localYaw) {
      return localYaw + switch (rotation) {
         case CLOCKWISE_90 -> 90F;
         case CLOCKWISE_180 -> 180F;
         case COUNTERCLOCKWISE_90 -> 270F;
         default -> 0F;
      };
   }

   /** Origine du puits du monte-charge (local hall). */
   public BlockPos liftLocal(BlockPos liftLocal) {
      return layout.liftOffset.offset(liftLocal);
   }

   public BlockPos liftWorld(BlockPos liftLocal) {
      return world(liftLocal(liftLocal));
   }

   /** Repere « hall » : pieds du boss, regard vers le fond (+z local). */
   public Scene.Frame hallFrame() {
      return new Scene.Frame(feet(layout.boss), yaw(0));
   }

   public Vec3 bossFeet() {
      return feet(layout.boss);
   }

   public float bossYaw() {
      return yaw(layout.bossYaw);
   }

   public Vec3 padCenter() {
      return feet(layout.pad);
   }

   /** Pieds des marques des joueurs dans le hall (apres le sas) et leur lacet. */
   public List<Vec3> playerMarks() {
      List<Vec3> out = new ArrayList<>();
      for (BlockPos p : layout.playerMarks) out.add(feet(p));
      return out;
   }

   public float playerMarksYaw() {
      return yaw(layout.marksYaw);
   }

   /** Points d'apparition des renforts (zombies, chiens) proposes au jeu. */
   public List<Vec3> reinforcementSpawns() {
      List<Vec3> out = new ArrayList<>();
      for (BlockPos p : layout.reinforcements) out.add(feet(p));
      return out;
   }

   /** Point de largage des bonus (Max Ammo...). */
   public Vec3 dropPoint() {
      return feet(layout.drop);
   }

   /** Pieds en haut du monte-charge (derriere la porte de confinement) et en bas (cabine arrivee). */
   public Vec3 liftTop() {
      return world(new Vec3(layout.liftOffset.getX() + layout.liftTopFeet.getX() + 0.5, layout.liftOffset.getY() + layout.liftTopFeet.getY(),
         layout.liftOffset.getZ() + layout.liftTopFeet.getZ() + 0.5));
   }

   public Vec3 liftBottom() {
      return world(new Vec3(layout.liftOffset.getX() + layout.liftBottomFeet.getX() + 0.5, layout.liftOffset.getY() + layout.liftBottomFeet.getY(),
         layout.liftOffset.getZ() + layout.liftBottomFeet.getZ() + 0.5));
   }

   /** Interieur du hall (arene du boss), monde. */
   public AABB interior() {
      return box(layout.interiorMin, layout.interiorMax.offset(1, 1, 1));
   }

   /** Tout le volume (hall + puits), monde. */
   public AABB bounds() {
      AABB hall = box(BlockPos.ZERO, new BlockPos(layout.size.getX(), layout.size.getY(), layout.size.getZ()));
      AABB lift = box(layout.liftOffset, layout.liftOffset.offset(layout.liftSize.getX(), layout.liftSize.getY(), layout.liftSize.getZ()));
      return hall.minmax(lift);
   }

   private AABB box(BlockPos localMin, BlockPos localMaxExclusive) {
      Vec3 a = world(Vec3.atLowerCornerOf(localMin)), b = world(Vec3.atLowerCornerOf(localMaxExclusive));
      return new AABB(a, b);
   }

   public List<ServerPlayer> playersInside() {
      AABB b = bounds().inflate(1);
      List<ServerPlayer> out = new ArrayList<>();
      for (ServerPlayer p : level.players()) if (b.contains(p.position())) out.add(p);
      return out;
   }

   // ------------------------------------------------------------------ liens
   public N07Events events() {
      return events;
   }

   /** Ecouteur des evenements de la salle (teleporteur, evacuation) ; celui du combat le remplace pendant un combat. */
   public PlacedHall events(N07Events e) {
      events = e != null ? e : new N07Events() {
      };
      return this;
   }

   public Encounter encounter() {
      return encounter;
   }

   /** (interne) Combat en cours dans cette salle. */
   public void attach(Encounter e) {
      encounter = e;
   }

   public ShrineBlockEntity terminal() {
      return level.getBlockEntity(world(layout.terminal)) instanceof ShrineBlockEntity s ? s : null;
   }

   // ------------------------------------------------------------------ etat de la salle
   /** Buse numero i (ordre de mise en route, 0..7). */
   public void pipe(int i, boolean on) {
      if (i < 0 || i >= layout.pipes.size()) return;
      if (level.getBlockEntity(world(layout.pipes.get(i))) instanceof PropBlockEntityServeur be) be.setFlow(on);
   }

   public int pipeCount() {
      return layout.pipes.size();
   }

   /** Toutes les buses, etalees sur ticks (0 : d'un coup). */
   public void pipes(boolean on, int ticks) {
      List<Integer> idx = new ArrayList<>();
      for (int i = 0; i < layout.pipes.size(); i++) idx.add(i);
      Later.spread(idx, ticks, i -> pipe(i, on));
   }

   /** Rigoles pleines / seches ; la vague part du plus loin de la cuve et finit a la cuve (ticks : duree). */
   public void channels(boolean wet, int ticks) {
      BlockPos tank = layout.tank;
      List<BlockPos> order = new ArrayList<>(layout.channels);
      order.sort(Comparator.comparingDouble(p -> -p.distSqr(tank)));
      if (!wet) java.util.Collections.reverse(order);
      Later.spread(order, ticks, lp -> {
         BlockPos p = world(lp);
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof RadChannelBlock && st.getValue(RadChannelBlock.DRY) == wet) {
            level.setBlock(p, st.setValue(RadChannelBlock.DRY, !wet), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
         }
      });
   }

   /** Plafonniers et suspensions : allumes / eteints, etales sur ticks (chaque changement recalcule l'eclairage). */
   public void lights(boolean on, int ticks) {
      List<BlockPos> order = new ArrayList<>(layout.lights);
      BlockPos c = layout.boss;
      order.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
      Later.spread(order, ticks, lp -> {
         BlockPos p = world(lp);
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof SwitchLightBlock && st.getValue(SwitchLightBlock.LIT) != on) {
            level.setBlock(p, st.setValue(SwitchLightBlock.LIT, on), Block.UPDATE_CLIENTS);
         } else if (st.getBlock() instanceof LampBlock && st.getValue(LampBlock.POWER) != (on ? 2 : 0)) {
            level.setBlock(p, st.setValue(LampBlock.POWER, on ? 2 : 0), Block.UPDATE_CLIENTS);
         }
      });
   }

   /** Plafonniers seuls (les suspensions a cage restent) : la coupure de courant de la phase 3. */
   public void ceilingLights(boolean on, int ticks) {
      List<BlockPos> order = new ArrayList<>();
      for (BlockPos lp : layout.lights) if (level.getBlockState(world(lp)).getBlock() instanceof SwitchLightBlock) order.add(lp);
      Later.spread(order, ticks, lp -> {
         BlockPos p = world(lp);
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof SwitchLightBlock && st.getValue(SwitchLightBlock.LIT) != on) level.setBlock(p, st.setValue(SwitchLightBlock.LIT, on),
            Block.UPDATE_CLIENTS);
      });
   }

   public void sirens(boolean on) {
      for (BlockPos lp : layout.sirens) {
         BlockPos p = world(lp);
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof SirenBlock && st.getValue(SirenBlock.LIT) != on) level.setBlock(p, st.setValue(SirenBlock.LIT, on), Block.UPDATE_CLIENTS);
      }
   }

   /** Grille du sas (entre le vestibule et le hall). Fermer pousse ceux qui sont dans l'embrasure cote hall. */
   public void gate(boolean closed) {
      if (level.getBlockEntity(world(layout.gate)) instanceof ArenaGateBlockEntity g) ArenaGates.setClosed(g, level, closed);
   }

   /** Terminal : ecran actif, bobines de la cuve qui accelerent, arcs (visuel du rituel, sans joueur). */
   public void terminalRitual() {
      if (terminal() instanceof ShrineBlockEntityServeur t) {
         t.setManaged(true);
         t.managedRitual(level);
      }
   }

   /** Combat : cuve brisee (visuel) ; alarm : sirenes + son d'alerte du terminal. */
   public void terminalAwake(boolean alarm) {
      if (terminal() instanceof ShrineBlockEntityServeur t) {
         t.setManaged(true);
         t.managedAwake(level, false);
      }
      sirens(alarm);
   }

   public void terminalReady() {
      if (terminal() instanceof ShrineBlockEntityServeur t) {
         t.setManaged(true);
         t.managedReady(level);
      }
   }

   /** Teleporteur 935 : allume (retour a la carte) / eteint. */
   public void teleporter(boolean active) {
      BlockPos p = world(layout.pad);
      BlockState st = level.getBlockState(p);
      if (st.getBlock() instanceof Teleporter935Block && st.getValue(Teleporter935Block.ACTIVE) != active) {
         level.setBlock(p, st.setValue(Teleporter935Block.ACTIVE, active), Block.UPDATE_CLIENTS);
         if (active) {
            Vec3 c = padCenter();
            Vfx.flash(level, c.add(0, 1, 0), 4F, 10, 0x9CE8FF);
            Vfx.shockwave(level, c, 6F, 18, 0x9CE8FF);
            level.playSound(null, c.x, c.y, c.z, fr.heralisz.n07.sound.BossSounds.DG_COIL_CHARGE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 3F, 1.2F);
         }
      }
   }

   public boolean teleporterActive() {
      BlockState st = level.getBlockState(world(layout.pad));
      return st.getBlock() instanceof Teleporter935Block && st.getValue(Teleporter935Block.ACTIVE);
   }

   /**
    * Remise a zero avant un nouveau combat (ou apres) : combat en cours interrompu, entites du boss retirees (boss, statue,
    * projectiles, objets au sol), effets clients effaces, blocs remis a l'identique du gabarit (budget perTick par tick),
    * puis buses coupees, rigoles seches, terminal au repos, alerte coupee, grille ouverte, lumieres allumees, teleporteur
    * eteint. done : appele a la fin.
    */
   public void reset(int perTick, Runnable done) {
      if (encounter != null && encounter.active()) encounter.abort("reset");
      clearEntities();
      Vfx.clear(level, interior().getCenter(), 80);
      Runnable after = () -> {
         pipes(false, 0);
         channels(false, 0);
         terminalReady();
         sirens(false);
         gate(false);
         lights(true, 0);
         teleporter(false);
         if (done != null) done.run();
      };
      HallPlacer.place(level, layout.structure.substring(layout.structure.indexOf(':') + 1), origin, rotation, perTick, true,
         () -> HallPlacer.place(level, layout.liftStructure.substring(layout.liftStructure.indexOf(':') + 1), world(layout.liftOffset), rotation, perTick,
            true, after));
   }

   /** Retire les entites du boss et les objets au sol dans la salle (aucun balayage de blocs). */
   public int clearEntities() {
      int n = 0;
      for (Entity e : level.getEntities((Entity) null, bounds().inflate(2), e -> e instanceof fr.heralisz.n07.entity.SujetN07
         || e instanceof fr.heralisz.n07.entity.N07Shell || e instanceof fr.heralisz.n07.entity.N07Boulder || e instanceof fr.heralisz.n07.entity.N07Isotope
         || e instanceof fr.heralisz.n07.entity.BossRemains || e instanceof ItemEntity || e instanceof ExperienceOrb)) {
         e.discard();
         n++;
      }
      return n;
   }

   /**
    * Evacuation : demande au jeu de remonter chaque joueur present dans la salle (onReturnRequested, EVACUATION) ; si un
    * combat tourne, il est interrompu. Rend les joueurs concernes.
    */
   public List<ServerPlayer> evacuate() {
      if (encounter != null && encounter.active()) encounter.abort("evacuation");
      List<ServerPlayer> ps = playersInside();
      for (ServerPlayer p : ps) {
         fr.heralisz.n07.scene.CinematicGuard.release(List.of(p));
         HallRegistry.fireReturn(this, p, N07Events.ReturnReason.EVACUATION);
      }
      return ps;
   }
}
