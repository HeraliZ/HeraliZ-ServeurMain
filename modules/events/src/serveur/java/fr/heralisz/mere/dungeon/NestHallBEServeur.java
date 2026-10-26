package fr.heralisz.mere.dungeon;

import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.MereConfig;
import fr.heralisz.mere.entity.RuinMother;
import fr.heralisz.mere.entity.RuinMotherServeur;
import fr.heralisz.mere.entity.SpiderlingServeur;
import fr.heralisz.mere.fx.Particles;
import fr.heralisz.mere.sound.Sounds;
import fr.heralisz.mere.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link NestHallBE} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new NestHallBE(...) » côté serveur.
 */
public class NestHallBEServeur extends NestHallBE {
   public NestHallBEServeur(BlockPos pos, BlockState st) {
      super(pos, st);
   }

   /** Le cœur du nid le plus proche, en version serveur (null s'il n'y en a pas, ou si ce n'est pas la version serveur). */
   public static NestHallBEServeur nearestServeur(Level level, BlockPos p, double r) {
      return nearest(level, p, r) instanceof NestHallBEServeur h ? h : null;
   }

   private static final int LONELY_LIMIT = 1200, SCAN_R = 48, SCAN_DOWN = 6, SCAN_UP = 40;
   private long nextReady;
   private long restoreAt = -1, nextCluster = Long.MAX_VALUE, nextBeams;
   private UUID boss;
   private BlockPos bossPos, doorPos, gatePos;
   private float bossYaw = Float.NaN, doorYaw = Float.NaN;
   private int loaded, lonely, litUpTo;
   private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
   private Map<HeroKind, List<BlockPos>> props;
   private List<BlockPos> sacs, sacOrder, beams;
   private final List<UUID> participants = new ArrayList<>();
   private boolean needRestore;

   private record Sched(long at, Runnable run) {}

   private final List<Sched> sched = new ArrayList<>();

   private void later(ServerLevel level, int delay, Runnable r) {
      sched.add(new Sched(level.getGameTime() + Math.max(0, delay), r));
   }

   private void runSched(long now) {
      if (sched.isEmpty()) return;
      List<Sched> due = new ArrayList<>();
      sched.removeIf(x -> {
         if (x.at > now) return false;
         due.add(x);
         return true;
      });
      for (Sched x : due) x.run.run();
   }

   public BlockPos bossPos() {
      return bossPos != null ? bossPos : worldPosition.above().relative(Direction.SOUTH, 10);
   }

   public float bossYaw() {
      return Float.isNaN(bossYaw) ? 180F : bossYaw;
   }

   /** Vers la galerie (devant la Mère). */
   public Vec3 front() {
      return Vec3.directionFromRotation(0, bossYaw());
   }

   public void setMarkers(BlockPos boss, float yaw, BlockPos door, float dYaw, BlockPos gate) {
      this.bossPos = boss;
      this.bossYaw = yaw;
      this.doorPos = door;
      this.doorYaw = dYaw;
      this.gatePos = gate;
      setChanged();
   }

   // ------------------------------------------------------------------ remise en état
   public void recordChange(BlockPos p, BlockState original) {
      changes.putIfAbsent(p.immutable(), original);
      setChanged();
   }

   private void setRecorded(ServerLevel level, BlockPos p, BlockState st) {
      if (!level.isLoaded(p)) return;
      recordChange(p, level.getBlockState(p));
      level.setBlock(p, st, 3);
   }

   public void restoreRoom(ServerLevel level) {
      if (egg != null) egg.discard();
      if (courier != null) courier.discard();
      egg = null;
      courier = null;
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) if (level.isLoaded(e.getKey())) level.setBlock(e.getKey(), e.getValue(), 3);
      changes.clear();
      restoreAt = -1;
      nextCluster = Long.MAX_VALUE;
      sched.clear();
      setChanged();
   }

   // ------------------------------------------------------------------ inventaire du nid
   private List<BlockPos> scan(ServerLevel level, java.util.function.Predicate<BlockState> test) {
      List<BlockPos> out = new ArrayList<>();
      BlockPos c = bossPos();
      int cx0 = (c.getX() - SCAN_R) >> 4, cx1 = (c.getX() + SCAN_R) >> 4;
      int cz0 = (c.getZ() - SCAN_R) >> 4, cz1 = (c.getZ() + SCAN_R) >> 4;
      int y0 = c.getY() - SCAN_DOWN, y1 = c.getY() + SCAN_UP;
      for (int cx = cx0; cx <= cx1; cx++) {
         for (int cz = cz0; cz <= cz1; cz++) {
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk ch = level.getChunk(cx, cz);
            LevelChunkSection[] secs = ch.getSections();
            for (int i = 0; i < secs.length; i++) {
               LevelChunkSection sec = secs[i];
               int sy = level.getSectionYFromSectionIndex(i) << 4;
               if (sec.hasOnlyAir() || sy + 15 < y0 || sy > y1) continue;
               if (!sec.getStates().maybeHas(test)) continue;
               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        if (test.test(sec.getBlockState(x, y, z))) out.add(new BlockPos((cx << 4) + x, sy + y, (cz << 4) + z));
                     }
                  }
               }
            }
         }
      }
      return out;
   }

   private Map<HeroKind, List<BlockPos>> props(ServerLevel level) {
      if (props == null) {
         props = new EnumMap<>(HeroKind.class);
         for (HeroKind k : HeroKind.values()) props.put(k, new ArrayList<>());
         for (BlockPos p : scan(level, s -> s.getBlock() instanceof HeroPropBlock)) {
            if (level.getBlockState(p).getBlock() instanceof HeroPropBlock b) props.get(b.kind).add(p);
         }
      }
      return props;
   }

   private List<BlockPos> props(ServerLevel level, HeroKind k) {
      return props(level).get(k);
   }

   /** Les grappes d'œufs pendues à la voûte (les rejetons en descendent). */
   public List<BlockPos> clusters(ServerLevel level) {
      return props(level, HeroKind.BROOD_CLUSTER);
   }

   private BlockPos shrine(ServerLevel level) {
      List<BlockPos> l = props(level, HeroKind.SHRINE);
      return l.isEmpty() ? null : l.get(0);
   }

   private List<BlockPos> sacs(ServerLevel level) {
      if (sacs == null) sacs = scan(level, s -> s.getBlock() instanceof AlertBlock);
      return sacs;
   }

   /** Les sacs d'œufs, dans l'ordre où ils s'allument : de la bouche de la galerie, en faisant le tour de l'arène. */
   private List<BlockPos> sacOrder(ServerLevel level) {
      if (sacOrder == null) {
         sacOrder = new ArrayList<>(sacs(level));
         Vec3 c = Vec3.atBottomCenterOf(bossPos());
         Vec3 f = front();
         double a0 = Math.atan2(f.z, f.x);
         sacOrder.sort(Comparator.comparingDouble(p -> {
            double a = Math.atan2(p.getZ() + 0.5 - c.z, p.getX() + 0.5 - c.x) - a0;
            a = Mth.wrapDegrees(Math.toDegrees(a));
            return Math.abs(a) + (a < 0 ? 0.5 : 0);                             // de part et d'autre de la galerie, deux à deux
         }));
      }
      return sacOrder;
   }

   /** Les failles de la voûte (repères « beam » : du bloc de lumière pâle posé dans la faille). */
   private List<BlockPos> beams(ServerLevel level) {
      if (beams == null) beams = scan(level, s -> s.is(Blocks.LIGHT) && s.getValue(net.minecraft.world.level.block.LightBlock.LEVEL) == 14);
      return beams;
   }

   private void setStage(ServerLevel level, BlockPos p, int stage) {
      if (p == null || !level.isLoaded(p)) return;
      BlockState st = level.getBlockState(p);
      if (!(st.getBlock() instanceof HeroPropBlock) || st.getValue(HeroPropBlock.STAGE) == stage) return;
      recordChange(p, st);
      level.setBlock(p, st.setValue(HeroPropBlock.STAGE, stage), 3);
   }

   private void lightSac(ServerLevel level, BlockPos p, boolean on, boolean sound) {
      BlockState st = level.getBlockState(p);
      if (!(st.getBlock() instanceof AlertBlock) || st.getValue(AlertBlock.ALERT) == on) return;
      setRecorded(level, p, st.setValue(AlertBlock.ALERT, on));
      if (on) level.sendParticles(RuinMother.AMBER_FX, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.02);
      if (sound) level.playSound(null, p, Sounds.EGG_PULSE.get(), SoundSource.BLOCKS, 1.6F, on ? 0.9F + level.random.nextFloat() * 0.3F : 0.5F);
   }

   // ------------------------------------------------------------------ la porte de toile (chapelle ↔ rotonde)
   private BlockPos webDoor(ServerLevel level) {
      List<BlockPos> l = props(level, HeroKind.WEB_DOOR);
      return l.isEmpty() ? null : l.get(0);
   }

   /** Les cases de l'arc (volume invisible quand la toile est tendue). */
   private List<BlockPos> doorCells(ServerLevel level) {
      List<BlockPos> out = new ArrayList<>();
      BlockPos g = webDoor(level);
      if (g == null) return out;
      Direction right = Direction.fromYRot(doorYaw()).getClockWise();
      for (int dx = -4; dx <= 4; dx++) {
         for (int dy = 0; dy <= 9; dy++) {
            double ax = Math.abs(dx);
            boolean in = ax <= 3.5 && (dy <= 5 || ax * ax + (dy - 5) * (dy - 5) <= 3.5 * 3.5);
            if (in && !(dx == 0 && dy == 0)) out.add(g.relative(right, dx).above(dy));
         }
      }
      return out;
   }

   /** La toile se déchire : elle retombe en lambeaux, l'arc s'ouvre. */
   private void tearDoor(ServerLevel level) {
      BlockPos g = webDoor(level);
      if (g == null) return;
      setStage(level, g, 1);
      later(level, 48, () -> setStage(level, g, 2));
      for (BlockPos p : doorCells(level)) if (level.getBlockState(p).is(ModBlocks.PROP_SOLID.get())) setRecorded(level, p, Blocks.AIR.defaultBlockState());
      Vec3 c = Vec3.atBottomCenterOf(g).add(0, 4, 0);
      level.playSound(null, g, Sounds.COCOON_TEAR.get(), SoundSource.BLOCKS, 6F, 0.6F);
      level.playSound(null, g, Sounds.SILK_STRAIN.get(), SoundSource.BLOCKS, 5F, 0.5F);
      level.sendParticles(RuinMother.WEB_FX, c.x, c.y, c.z, 80, 2.5, 3, 0.4, 0.06);
      Vfx.shake(level, c, 0.5F, 30, 10);
   }

   /** La toile se retisse derrière l'équipe : le nid est scellé. */
   private void sealDoor(ServerLevel level) {
      BlockPos g = webDoor(level);
      if (g == null) return;
      setStage(level, g, 3);
      for (BlockPos p : doorCells(level)) if (level.getBlockState(p).isAir()) setRecorded(level, p, ModBlocks.PROP_SOLID.get().defaultBlockState());
      level.playSound(null, g, Sounds.SILK.get(), SoundSource.BLOCKS, 6F, 0.7F);
      level.playSound(null, g, Sounds.COCOON.get(), SoundSource.BLOCKS, 5F, 0.8F);
      level.playSound(null, g, Sounds.CHITTER.get(), SoundSource.BLOCKS, 4F, 1.3F);
   }

   /** Fin du combat : la toile s'ouvre (la remise en état la retendra). */
   private void openGate(ServerLevel level) {
      BlockPos g = webDoor(level);
      if (g == null || level.getBlockState(g).getValue(HeroPropBlock.STAGE) == 2) return;
      tearDoor(level);
   }

   private float doorYaw() {
      return Float.isNaN(doorYaw) ? bossYaw() + 180F : doorYaw;
   }

   @Override
   void tick(ServerLevel level) {
      loaded++;
      long now = level.getGameTime();
      runSched(now);
      if (now >= nextBeams && now % 20 == 0) beamsTick(level, now);
      switch (state) {
         case READY -> {
            if (needRestore) {
               needRestore = false;
               restoreRoom(level);
            }
            if (loaded > 60 && now % 20 == 0) ensureSleeper(level);
         }
         case COUNTDOWN -> countdown(level, (int) (now - stateTime));
         case ENTRY -> entry(level, (int) (now - stateTime));
         case AWAKE -> {
            if (now % 20 == 0) watch(level, now);
            combatTick(level, now);
         }
         case COOLDOWN -> {
            if (restoreAt >= 0 && now >= restoreAt) {
               restoreRoom(level);
               ensureSleeper(level);
            }
            if (now >= nextReady && now % 20 == 0) {
               if (restoreAt >= 0) restoreRoom(level);
               setState(READY, now);
            }
         }
         default -> setState(READY, now);
      }
   }

   /** Les rayons pâles qui tombent des failles de la voûte (tant que quelqu'un est dans le nid). */
   private void beamsTick(ServerLevel level, long now) {
      nextBeams = now + 600;
      if (nearbyPlayers(level, 70).isEmpty()) return;
      for (BlockPos b : beams(level)) {
         Vec3 top = Vec3.atCenterOf(b);
         double gy = RuinMotherServeur.groundY(level, top.x, top.y - 1, top.z);
         Vec3 floor = new Vec3(top.x + (level.random.nextDouble() - 0.5) * 0.6, gy, top.z + (level.random.nextDouble() - 0.5) * 0.6);
         Vfx.beam(level, top, floor, 2.6F, 0.55F, 640, 0xB8CCD8);
      }
   }

   private void setState(int s, long now) {
      state = s;
      stateTime = now;
      sync();
   }

   private void sync() {
      setChanged();
      if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
   }

   public RuinMotherServeur findBoss(ServerLevel level) {
      if (boss == null) return null;
      Entity e = level.getEntity(boss);
      return e instanceof RuinMotherServeur k && k.isAlive() ? k : null;
   }

   /** La Mère dort dans son cocon (on l'adopte si elle existe déjà, sinon on la crée, endormie). */
   private void ensureSleeper(ServerLevel level) {
      BlockPos bp = bossPos();
      if (!level.areEntitiesLoaded(ChunkPos.asLong(bp))) return;
      RuinMotherServeur k = findBoss(level);
      if (k == null) {
         List<RuinMotherServeur> near = level.getEntitiesOfClass(RuinMotherServeur.class, new AABB(bp).inflate(24), Entity::isAlive);
         if (!near.isEmpty()) {
            k = near.get(0);
            boss = k.getUUID();
            setChanged();
         }
      }
      if (k != null) {
         k.setHall(worldPosition);
         k.setHome(bp, bossYaw());
         if (!k.dormant() && !k.isDeadOrDying() && state != AWAKE) k.sleep();
         return;
      }
      if (!(HeraliszMere.RUIN_MOTHER.get().create(level) instanceof RuinMotherServeur e)) return;          // créée par l'usine
      float yaw = bossYaw();
      e.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, yaw, 0);
      e.setYHeadRot(yaw);
      e.setYBodyRot(yaw);
      e.setHome(bp, yaw);
      e.setHall(worldPosition);
      e.setDormant(true);
      level.addFreshEntity(e);
      boss = e.getUUID();
      setChanged();
   }

   // ------------------------------------------------------------------ le rituel
   /** Un joueur dépose l'Œuf de la Couvée sur l'autel. */
   @Override
   public boolean offerEgg(ServerLevel level, Player p, Vec3 idolTop) {
      if (state != READY) return false;
      p0 = idolTop;
      ensureSleeper(level);
      RuinMotherServeur k = findBoss(level);
      if (k == null || !k.dormant()) return false;
      startCountdown(level, 0);
      return true;
   }

   private Vec3 p0;
   private BlockPos idol;
   private net.minecraft.world.entity.item.ItemEntity egg;
   private SpiderlingServeur courier;

   /** L'offrande : la petite araignée descend la toile, prend l'œuf sur l'idole, le rapporte et passe de l'autre côté. */
   private void offering(ServerLevel level, int t) {
      BlockPos g = webDoor(level);
      if (g == null || idol == null) return;
      Vec3 f = front();                                                           // de la toile vers la chapelle (et l'idole)
      Vec3 door = Vec3.atBottomCenterOf(g);
      Vec3 I = Vec3.atBottomCenterOf(idol);
      double gy0 = door.y;
      Vec3 up0 = new Vec3(0, 1, 0), down = new Vec3(0, -1, 0);
      Vec3 web = door.add(f.scale(0.35));                                        // à la surface de la toile, côté chapelle
      Vec3 foot = new Vec3(web.x, gy0, web.z).add(f.scale(0.5));
      Vec3 base = new Vec3(I.x, gy0, I.z).subtract(f.scale(2.7));                // au pied du socle, côté toile
      Vec3 top = new Vec3(I.x, I.y, I.z).subtract(f.scale(0.9));                 // sur la marche, face à l'idole
      if (t == 1) {
         courier = HeraliszMere.SPIDERLING.get().create(level) instanceof SpiderlingServeur c ? c : null;   // créée par l'usine
         if (courier == null) return;
         courier.moveTo(web.x, gy0 + 8.5, web.z, 0, 0);
         level.addFreshEntity(courier);
         level.playSound(null, g, Sounds.CHITTER.get(), SoundSource.HOSTILE, 2.5F, 1.4F);
      }
      if (courier == null || !courier.isAlive()) return;
      Vec3 p, up, fw;
      int anim = fr.heralisz.mere.entity.Spiderling.A_RUN;
      if (t < 40) {                                                               // elle descend la toile, tête en bas
         double u = t / 40.0;
         p = new Vec3(web.x, gy0 + 8.5 - 8.3 * u, web.z);
         up = f;
         fw = down;
      } else if (t < 46) {                                                        // elle bascule au sol
         double u = (t - 40) / 6.0;
         p = new Vec3(web.x, gy0 + 0.2, web.z).lerp(foot, u);
         up = f.scale(1 - u).add(up0.scale(u)).normalize();
         fw = down.scale(1 - u).add(f.scale(u)).normalize();
      } else if (t < 92) {                                                        // elle trottine jusqu'au socle
         double u = (t - 46) / 46.0;
         p = foot.lerp(base, u);
         up = up0;
         fw = f;
      } else if (t < 104) {                                                       // elle grimpe sur la marche
         double u = (t - 92) / 12.0;
         p = base.lerp(top, u).add(0, 0.3 * Math.sin(Math.PI * u), 0);
         up = up0;
         fw = f;
         anim = fr.heralisz.mere.entity.Spiderling.A_WALK;
      } else if (t < 128) {                                                       // elle prend l'œuf
         p = top;
         up = up0;
         fw = f;
         anim = fr.heralisz.mere.entity.Spiderling.A_HOLD;
         if (t == 112) {
            if (egg != null) egg.discard();
            egg = null;
            courier.setEgg(true);
            level.playSound(null, idol, Sounds.EGG_PLACE.get(), SoundSource.HOSTILE, 2F, 1.2F);
            level.playSound(null, idol, Sounds.CHITTER.get(), SoundSource.HOSTILE, 2F, 1.6F);
         }
      } else if (t < 140) {                                                       // demi-tour, redescend
         double u = (t - 128) / 12.0;
         p = top.lerp(base, u).add(0, 0.3 * Math.sin(Math.PI * u), 0);
         up = up0;
         fw = f.scale(1 - 2 * Math.min(1, u * 1.5)).add(new Vec3(-f.z, 0, f.x).scale(Math.sin(Math.PI * Math.min(1, u * 1.5)))).normalize();
      } else if (t < 186) {                                                       // elle revient vers la toile
         double u = (t - 140) / 46.0;
         p = base.lerp(foot, u);
         up = up0;
         fw = f.scale(-1);
      } else if (t < 192) {                                                       // elle se dresse contre la toile
         double u = (t - 186) / 6.0;
         p = foot.lerp(new Vec3(web.x, gy0 + 0.3, web.z), u);
         up = up0.scale(1 - u).add(f.scale(u)).normalize();
         fw = f.scale(-(1 - u)).add(up0.scale(u)).normalize();
      } else if (t < 232) {                                                       // elle remonte la toile avec l'œuf
         double u = (t - 192) / 40.0;
         p = new Vec3(web.x, gy0 + 0.3 + 8.2 * u, web.z);
         up = f;
         fw = up0;
      } else {                                                                    // et se glisse de l'autre côté, par le haut de l'arc
         double u = Math.min(1, (t - 232) / 6.0);
         p = new Vec3(web.x, gy0 + 8.5, web.z).subtract(f.scale(0.9 * u));
         up = f.scale(1 - u).add(up0.scale(u)).normalize();
         fw = up0.scale(1 - u).add(f.scale(-u)).normalize();
      }
      courier.puppet(p, up, fw, anim);
      if (t % 5 == 0 && t < 232) level.playSound(null, p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.5F, 1.5F);
      if (t >= 238) {                                                             // derrière la toile : l'œuf est déposé, le cocon s'agite
         courier.discard();
         courier = null;
         BlockPos s = shrine(level);
         setStage(level, s, 1);
         level.playSound(null, g, Sounds.SILK_STRAIN.get(), SoundSource.HOSTILE, 5F, 0.6F);
         level.playSound(null, g, Sounds.EGG_PULSE.get(), SoundSource.HOSTILE, 5F, 0.5F);
      }
   }

   private void startCountdown(ServerLevel level, int skip) {
      litUpTo = 0;
      sacOrder = null;
      participants.clear();
      setState(COUNTDOWN, level.getGameTime() - skip);
      BlockPos s = shrine(level);
      BlockPos at = s != null ? s : worldPosition;
      level.playSound(null, at, Sounds.EGG_PLACE.get(), SoundSource.BLOCKS, 3F, 0.8F);
      level.playSound(null, at, Sounds.SQUELCH.get(), SoundSource.BLOCKS, 3F, 0.6F);
      level.sendParticles(RuinMother.AMBER_FX, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.05);
      // l'œuf reste posé sur l'idole ; une petite araignée va venir le chercher (offering)
      idol = BlockPos.containing(p0 != null ? p0 : Vec3.atCenterOf(worldPosition));
      egg = new net.minecraft.world.entity.item.ItemEntity(level, idol.getX() + 0.5, idol.getY() + 1.05, idol.getZ() + 0.5,
         new net.minecraft.world.item.ItemStack(fr.heralisz.mere.HeraliszMere.BROOD_EGG.get()));
      egg.setNeverPickUp();
      egg.setUnlimitedLifetime();
      egg.setNoGravity(true);
      egg.setDeltaMovement(Vec3.ZERO);
      level.addFreshEntity(egg);
      courier = null;
      RuinMotherServeur k0 = findBoss(level);
      if (k0 != null && MereConfig.CINEMATICS.get())
         Vfx.cinematic(level, Vec3.atBottomCenterOf(idol), k0.getId(), RuinMother.OFFER_K, OFFER_TICKS, front());
   }

   /** Le décompte : le cocon bat, la voûte grince ; ceux qui veulent affronter la Mère restent dans le nid. */
   private void countdown(ServerLevel level, int t) {
      if (t <= OFFER_TICKS) offering(level, t);
      int left = (COUNTDOWN_TICKS - t + 19) / 20;
      if (t % 20 == 0) {
         for (Player p : nearbyPlayers(level, 70)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_mere.countdown", left)
               .withStyle(net.minecraft.ChatFormatting.GOLD), true);
         }
      }
      BlockPos g0 = webDoor(level);
      Vec3 cc = g0 != null ? Vec3.atBottomCenterOf(g0).add(0, 4, 0) : Vec3.atBottomCenterOf(bossPos());
      // derrière la toile : ça bat, ça gratte, ça hurle ; la toile se tend et respire
      if (t % 40 == 0) level.playSound(null, cc.x, cc.y, cc.z, Sounds.EGG_PULSE.get(), SoundSource.HOSTILE, 4F, 0.5F + t * 0.001F);
      if (t % 100 == 50) level.playSound(null, cc.x, cc.y, cc.z, Sounds.AMB_SCREAM.get(), SoundSource.HOSTILE, 3F, 0.8F);
      if (t % 60 == 30) level.playSound(null, cc.x, cc.y, cc.z, Sounds.CHITTER.get(), SoundSource.HOSTILE, 3F, 0.7F + level.random.nextFloat() * 0.3F);
      if (t % 20 == 10) level.sendParticles(RuinMother.WEB_FX, cc.x, cc.y + 2, cc.z, 6, 2.5, 2.5, 0.3, 0.02);
      if (t < COUNTDOWN_TICKS) return;
      participants.clear();
      for (Player p : nearbyPlayers(level, 60)) if (!p.isSpectator()) participants.add(p.getUUID());
      if (participants.isEmpty()) {
         restoreRoom(level);
         setState(READY, level.getGameTime());
         return;
      }
      setState(ENTRY, level.getGameTime());
      RuinMotherServeur k = findBoss(level);
      BlockPos g = webDoor(level);
      if (k != null && MereConfig.CINEMATICS.get())
         Vfx.cinematic(level, g != null ? Vec3.atBottomCenterOf(g) : Vec3.atBottomCenterOf(bossPos()), k.getId(), RuinMother.ENTRY_K, ENTRY_TICKS,
            front().scale(-1));
   }

   private List<Player> nearbyPlayers(ServerLevel level, double r) {
      return new ArrayList<>(level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(r, 30, r), pl -> !pl.isSpectator()));
   }

   /** L'entrée : l'équipe, rassemblée devant la toile ; la toile se déchire ; ils franchissent l'arc et découvrent la rotonde, le
    *  cocon géant sous l'oculus ; la toile se retisse derrière eux ; les sacs d'œufs s'allument deux à deux ; la Mère éclôt. */
   private void entry(ServerLevel level, int t) {
      Vec3 c = Vec3.atBottomCenterOf(bossPos());
      BlockPos g = webDoor(level);
      Vec3 door = g != null ? Vec3.atBottomCenterOf(g) : c.add(front().scale(28));
      Vec3 in = flat(c.subtract(door));                                           // de la chapelle vers la rotonde
      Vec3 across = new Vec3(-in.z, 0, in.x);
      if (t == 1) {                                                               // l'équipe, en rangs, devant la toile
         int n = 0, cnt = 0;
         for (UUID id : participants) if (level.getPlayerByUUID(id) != null) cnt++;
         for (UUID id : participants) {
            if (!(level.getPlayerByUUID(id) instanceof ServerPlayer p)) continue;
            int row = n / 4, col = n % 4, inRow = Math.min(4, cnt - row * 4);
            Vec3 at = door.add(in.scale(-4 - row * 2.2)).add(across.scale((col - (inRow - 1) / 2.0) * 1.6));
            float yaw = (float) (Mth.atan2(in.z, in.x) * Mth.RAD_TO_DEG) - 90F;
            p.teleportTo(level, at.x, door.y, at.z, yaw, 0F);
            n++;
         }
      }
      if (t == TEAR_T - 20) level.playSound(null, BlockPos.containing(door), Sounds.SCREECH.get(), SoundSource.HOSTILE, 3F, 0.6F);
      if (t == TEAR_T) tearDoor(level);
      if (t == SEAL_T) sealDoor(level);
      int i = 0;
      for (UUID id : participants) {
         if (!(level.getPlayerByUUID(id) instanceof ServerPlayer p) || p.isSpectator()) continue;
         int cnt = participants.size(), row = i / 4, inRow = Math.min(4, cnt - row * 4);
         double lane = ((i % 4) - (inRow - 1) / 2.0) * 1.6;
         Vec3 start = door.add(in.scale(-4 - row * 2.2)).add(across.scale(lane));
         Vec3 goal = door.add(in.scale(6 - row * 2.2)).add(across.scale(lane));
         boolean walking = t > TEAR_T + 30 && t < SEAL_T;
         // la marche est une trajectoire (jamais une poussée contre un mur) : on avance le long de la ligne départ → but
         double u = Mth.clamp((t - TEAR_T - 30) / (double) (SEAL_T - TEAR_T - 40), 0, 1);
         Vec3 want = start.lerp(goal, u * u * (3 - 2 * u));
         Vec3 d = want.subtract(p.position());
         double dist = Math.sqrt(d.x * d.x + d.z * d.z);
         if (dist > 1.5) p.teleportTo(want.x, p.getY(), want.z);                  // coincé (un décor, un mur) : on le remet sur sa ligne
         else if (walking && dist > 0.05) {
            p.setDeltaMovement(d.x * 0.5, p.getDeltaMovement().y, d.z * 0.5);
            p.hurtMarked = true;
         } else if (!walking) {
            p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
            p.hurtMarked = true;
         }
         if (t % 2 == 0) {
            float base = (float) (Mth.atan2(in.z, in.x) * Mth.RAD_TO_DEG) - 90F;
            float yaw, pitch;
            if (t < TEAR_T + 30) {                                                  // ils fixent la toile qui frémit
               yaw = base + 6F * Mth.sin(t * 0.05F + i);
               pitch = -4F;
            } else if (t < SEAL_T) {                                                // ils entrent, et lèvent les yeux vers le cocon
               yaw = base + 25F * Mth.sin(t * 0.03F + i * 1.7F);
               pitch = -30F * (float) Mth.clamp((t - TEAR_T - 40) / 60.0, 0, 1);
            } else if (t < SEAL_T + 40) {                                           // la toile se referme : ils se retournent
               float v = Mth.clamp((t - SEAL_T) / 10F, 0, 1);
               yaw = base + 180F * v;
               pitch = 0F;
            } else {                                                                // puis, lentement, vers le cocon là-haut
               float v = Mth.clamp((t - SEAL_T - 40) / 30F, 0, 1);
               yaw = base + 180F * (1 - v);
               pitch = -35F * v;
            }
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch,
               java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X, net.minecraft.world.entity.RelativeMovement.Y,
                  net.minecraft.world.entity.RelativeMovement.Z));
         }
         i++;
      }
      if (t >= SACS_FROM) {                                                       // les sacs d'œufs s'éveillent, deux à deux
         List<BlockPos> order = sacOrder(level);
         int want = (int) (order.size() * Mth.clamp((t - SACS_FROM) / (float) (SACS_TO - SACS_FROM), 0, 1));
         int k = 0;
         while (litUpTo < want) lightSac(level, order.get(litUpTo++), true, k++ % 2 == 0);
      }
      if (t == SACS_TO) {
         Vec3 cc = c.add(0, RuinMother.COCOON_C, 0);
         level.playSound(null, cc.x, cc.y, cc.z, Sounds.EGG_PULSE.get(), SoundSource.HOSTILE, 6F, 0.5F);
         level.playSound(null, cc.x, cc.y, cc.z, Sounds.AMB_SCREAM.get(), SoundSource.HOSTILE, 5F, 0.7F);
      }
      if (t >= ENTRY_TICKS) {
         RuinMotherServeur k = findBoss(level);
         if (k == null || !k.dormant()) {
            setState(READY, level.getGameTime());
            return;
         }
         k.setHall(worldPosition);
         k.wake();
         lonely = 0;
         setState(AWAKE, level.getGameTime());
      }
   }

   private static Vec3 flat(Vec3 v) {
      Vec3 f = new Vec3(v.x, 0, v.z);
      return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
   }

   // ------------------------------------------------------------------ appels de la Mère
   /** Éclosion, 4,95 s : le cocon se déchire. */
   public void cocoonTear(ServerLevel level) {
      BlockPos s = shrine(level);
      setStage(level, s, 2);
      if (s != null) later(level, 125, () -> setStage(level, s, 3));             // déchiré, vide, il se balance
   }

   /** Éclosion, 14,5 s : elle hurle ; tous les sacs palpitent d'un coup. */
   public void motherScreams(ServerLevel level) {
      for (BlockPos p : sacs(level)) lightSac(level, p, true, false);
      Vfx.shake(level, Vec3.atBottomCenterOf(bossPos()), 0.6F, 60, 20);
   }

   /** Phase 2 : le nid répond — les sacs s'éteignent et se rallument en vague, la poussière tombe de la voûte. */
   public void phase2(ServerLevel level) {
      List<BlockPos> order = sacOrder(level);
      for (int i = 0; i < order.size(); i++) {
         BlockPos p = order.get(i);
         lightSac(level, p, false, false);
         later(level, 10 + i * 2, () -> lightSac(level, p, true, true));
      }
      Vec3 c = Vec3.atBottomCenterOf(bossPos());
      level.sendParticles(Particles.MIST.get(), c.x, c.y + 14, c.z, 60, 10, 3, 10, 0.02);
   }

   /** Phase 3 : les grappes vont crever sur les joueurs. */
   public void phase3(ServerLevel level) {
      nextCluster = level.getGameTime() + 120;
      Vec3 c = Vec3.atBottomCenterOf(bossPos());
      Vfx.shake(level, c, 1.0F, 70, 30);
      level.playSound(null, c.x, c.y + 12, c.z, Sounds.AMB_SCREAM.get(), SoundSource.HOSTILE, 6F, 0.6F);
   }

   private void combatTick(ServerLevel level, long now) {
      if (now < nextCluster) return;
      nextCluster = now + MereConfig.CHANDELIER_SECONDS.get() * 20L;
      RuinMotherServeur k = findBoss(level);
      if (k == null || k.isDeadOrDying() || k.phase() < 3) return;
      List<Player> ps = k.targets(60);
      List<BlockPos> cl = clusters(level);
      if (ps.isEmpty() || cl.isEmpty()) return;
      Player p = ps.get(level.random.nextInt(ps.size()));
      BlockPos best = null;
      double bd = Double.MAX_VALUE;
      for (BlockPos b : cl) {
         double d = Vec3.atCenterOf(b).distanceToSqr(p.getX(), b.getY(), p.getZ());
         if (d < bd) {
            bd = d;
            best = b;
         }
      }
      if (best != null) k.clusterFall(level, best, p);
   }

   /** Mort : le nid s'éteint — les sacs d'œufs meurent un à un, en partant d'elle. */
   public void deathDark(ServerLevel level, Vec3 at) {
      nextCluster = Long.MAX_VALUE;
      List<BlockPos> l = new ArrayList<>(sacs(level));
      l.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(at)));
      for (int i = 0; i < l.size(); i++) {
         BlockPos p = l.get(i);
         later(level, 30 + i * 3, () -> lightSac(level, p, false, level.random.nextInt(3) == 0));
      }
   }

   /** Mort : un rayon pâle tombe de la voûte sur le cocon hissé. */
   public void deathBeam(ServerLevel level, Vec3 at, double ceilingY) {
      Vfx.beam(level, new Vec3(at.x + 1.5, ceilingY, at.z - 1.0), at, 4.5F, 0.8F, 600, 0xDDE6F0);
   }

   public void deathEnd(ServerLevel level) {
      RuinMotherServeur k = findBoss(level);
      openGate(level);
      long now = level.getGameTime();
      nextReady = now + Math.max(MereConfig.RESPAWN_MINUTES.get() * 1200L, RuinMother.REMAINS + 1200L);
      restoreAt = now + RuinMother.REMAINS;
      boss = null;
      setState(COOLDOWN, now);
   }

   private void watch(ServerLevel level, long now) {
      RuinMotherServeur k = findBoss(level);
      if (k == null) {
         List<RuinMother> dying = level.getEntitiesOfClass(RuinMother.class, new AABB(bossPos()).inflate(60), RuinMother::isDeadOrDying);
         if (!dying.isEmpty()) return;                                          // elle meurt : deathEnd prendra le relais
         openGate(level);
         restoreRoom(level);
         setState(READY, now);
         return;
      }
      if (k.isDeadOrDying()) return;
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(56, 24, 56), pl -> !pl.isSpectator() && !pl.isCreative()).isEmpty();
      lonely = anyone ? 0 : lonely + 20;
      if (lonely < LONELY_LIMIT) return;
      k.sleep();
      openGate(level);
      restoreRoom(level);
      setState(READY, now);
   }

   /** Réveil immédiat (/boss mere eveil) : sans décompte. */
   public boolean forceAwaken(ServerLevel level) {
      if (state != READY && state != COOLDOWN) return false;
      if (restoreAt >= 0) restoreRoom(level);
      nextReady = 0;
      loaded = Math.max(loaded, 81);
      ensureSleeper(level);
      RuinMotherServeur k = findBoss(level);
      if (k == null || !k.dormant()) return false;
      startCountdown(level, COUNTDOWN_TICKS - 40);
      return true;
   }

   /** Remise à zéro (/boss mere reset). */
   public void reset(ServerLevel level) {
      RuinMotherServeur k = findBoss(level);
      if (k != null) k.sleep();
      nextReady = 0;
      openGate(level);
      restoreRoom(level);
      props = null;
      sacs = null;
      sacOrder = null;
      beams = null;
      setState(READY, level.getGameTime());
   }

   // ------------------------------------------------------------------ sauvegarde / synchro
   @Override
   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putInt("State", state);
      tag.putLong("StateTime", stateTime);
      tag.putLong("NextReady", nextReady);
      tag.putLong("RestoreAt", restoreAt);
      if (boss != null) tag.putUUID("Boss", boss);
      if (bossPos != null) tag.put("BossPos", NbtUtils.writeBlockPos(bossPos));
      if (doorPos != null) tag.put("DoorPos", NbtUtils.writeBlockPos(doorPos));
      if (gatePos != null) tag.put("GatePos", NbtUtils.writeBlockPos(gatePos));
      if (!Float.isNaN(bossYaw)) tag.putFloat("BossYaw", bossYaw);
      if (!Float.isNaN(doorYaw)) tag.putFloat("DoorYaw", doorYaw);
      ListTag list = new ListTag();
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.put("Pos", NbtUtils.writeBlockPos(e.getKey()));
         c.put("State", NbtUtils.writeBlockState(e.getValue()));
         list.add(c);
      }
      tag.put("Changes", list);
   }

   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      state = tag.getInt("State");
      stateTime = tag.getLong("StateTime");
      nextReady = tag.getLong("NextReady");
      restoreAt = tag.contains("RestoreAt") ? tag.getLong("RestoreAt") : -1;
      if (tag.hasUUID("Boss")) boss = tag.getUUID("Boss");
      if (tag.contains("BossPos")) bossPos = NbtUtils.readBlockPos(tag.getCompound("BossPos"));
      if (tag.contains("DoorPos")) doorPos = NbtUtils.readBlockPos(tag.getCompound("DoorPos"));
      if (tag.contains("GatePos")) gatePos = NbtUtils.readBlockPos(tag.getCompound("GatePos"));
      if (tag.contains("BossYaw")) bossYaw = tag.getFloat("BossYaw");
      if (tag.contains("DoorYaw")) doorYaw = tag.getFloat("DoorYaw");
      if (tag.contains("Changes")) {
         changes.clear();
         var reg = level != null ? level.holderLookup(net.minecraft.core.registries.Registries.BLOCK)
            : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
         for (Tag t : tag.getList("Changes", 10)) {
            CompoundTag c = (CompoundTag) t;
            changes.put(NbtUtils.readBlockPos(c.getCompound("Pos")), NbtUtils.readBlockState(reg, c.getCompound("State")));
         }
      }
   }

   @Override
   public void onLoad() {
      super.onLoad();
      LOADED.add(this);
      if (level != null && !level.isClientSide && (state == COUNTDOWN || state == ENTRY || state == AWAKE)) {
         state = READY;
         needRestore = true;
      }
   }

   @Override
   public CompoundTag getUpdateTag() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("State", state);
      tag.putLong("StateTime", stateTime);
      return tag;
   }
}
