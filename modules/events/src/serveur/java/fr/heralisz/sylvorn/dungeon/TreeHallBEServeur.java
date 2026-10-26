package fr.heralisz.sylvorn.dungeon;

import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.SylvornConfig;
import fr.heralisz.sylvorn.entity.Sylvorn;
import fr.heralisz.sylvorn.entity.SylvornServeur;
import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link TreeHallBE} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun (ModBlocks.TREE_USINE) dès que la partie serveur est dans le jar : avec -complet, les entités
 * de bloc du client sont aussi de cette classe ; son code reste donc sûr côté client, comme dans le mod d'origine (le ticker
 * serveur n'est donné qu'au monde serveur). Ne jamais faire « new TreeHallBE(...) » : passer par ModBlocks.TREE_BE.
 */
public class TreeHallBEServeur extends TreeHallBE {
   public TreeHallBEServeur(BlockPos pos, BlockState st) {
      super(pos, st);
   }

   /** La salle la plus proche, en version serveur (null s'il n'y en a pas, ou si ce n'est pas la version serveur). */
   public static TreeHallBEServeur nearestServeur(Level level, BlockPos p, double r) {
      return nearest(level, p, r) instanceof TreeHallBEServeur h ? h : null;
   }

   private static final int LONELY_LIMIT = 1200, SCAN_R = 72, SCAN_DOWN = 12, SCAN_UP = 92;
   private long nextReady;
   private long restoreAt = -1;
   private UUID boss;
   private BlockPos doorPos, gatePos;
   private float bossYaw = Float.NaN, doorYaw = Float.NaN;
   private int loaded, lonely;
   private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
   private Map<HeroKind, List<BlockPos>> props;
   private List<BlockPos> doorCells;
   private final List<UUID> participants = new ArrayList<>();
   private boolean needRestore;
   private ItemEntity seed;

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

   public float bossYaw() {
      return Float.isNaN(bossYaw) ? 180F : bossYaw;
   }

   /** Du mur de racines vers l'intérieur du tronc. */
   public Vec3 inward() {
      return Vec3.directionFromRotation(0, Float.isNaN(doorYaw) ? bossYaw() + 180F : doorYaw);
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
      if (seed != null) seed.discard();
      seed = null;
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) if (level.isLoaded(e.getKey())) level.setBlock(e.getKey(), e.getValue(), 3);
      changes.clear();
      restoreAt = -1;
      sched.clear();
      setChanged();
   }

   // ------------------------------------------------------------------ inventaire
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

   private BlockPos prop(ServerLevel level, HeroKind k) {
      List<BlockPos> l = props(level).get(k);
      return l.isEmpty() ? null : l.get(0);
   }

   private void setStage(ServerLevel level, BlockPos p, int stage) {
      if (p == null || !level.isLoaded(p)) return;
      BlockState st = level.getBlockState(p);
      if (!(st.getBlock() instanceof HeroPropBlock) || st.getValue(HeroPropBlock.STAGE) == stage) return;
      recordChange(p, st);
      level.setBlock(p, st.setValue(HeroPropBlock.STAGE, stage), 3);
   }

   /** Le cœur vert, tout là-haut (son centre). */
   public Vec3 heartPos() {
      if (level instanceof ServerLevel sl) {
         BlockPos h = prop(sl, HeroKind.HEART);
         if (h != null) return Vec3.atBottomCenterOf(h).add(0, 11, 0);
      }
      return Vec3.atBottomCenterOf(bossPos()).add(0, 80, 0);
   }

   /** Le cœur : 0 lent (il dort), 1 éveillé, 2 affolé, 3 repu (après la mort de son gardien). */
   public void heartStage(ServerLevel level, int s) {
      setStage(level, prop(level, HeroKind.HEART), s);
   }

   /** Le bas-relief : 1 l'écorce se fend, 2 elle éclate, 3 la niche vide. */
   public void barkStage(ServerLevel level, int s) {
      setStage(level, prop(level, HeroKind.BARK), s);
   }

   // ------------------------------------------------------------------ le mur de racines
   private BlockPos rootDoor(ServerLevel level) {
      return prop(level, HeroKind.ROOT_DOOR);
   }

   /** Le volume invisible qui bouche l'arche tant que le mur est fermé. */
   private List<BlockPos> doorCells(ServerLevel level) {
      if (doorCells == null) {
         doorCells = new ArrayList<>();
         BlockPos g = rootDoor(level);
         if (g != null) {
            for (BlockPos p : BlockPos.betweenClosed(g.offset(-6, 0, -2), g.offset(6, 11, 2))) {
               if (level.getBlockState(p).is(ModBlocks.PROP_SOLID.get())) doorCells.add(p.immutable());
            }
         }
      }
      return doorCells;
   }

   private void openDoor(ServerLevel level) {
      BlockPos g = rootDoor(level);
      if (g == null) return;
      doorCells(level);
      if (level.getBlockState(g).getValue(HeroPropBlock.STAGE) == 2) return;
      setStage(level, g, 1);
      later(level, 60, () -> setStage(level, g, 2));
      for (BlockPos p : doorCells(level)) if (level.getBlockState(p).is(ModBlocks.PROP_SOLID.get())) setRecorded(level, p, Blocks.AIR.defaultBlockState());
      Vec3 c = Vec3.atBottomCenterOf(g).add(0, 4, 0);
      level.playSound(null, g, Sounds.SY_ROOTS.get(), SoundSource.BLOCKS, 6F, 0.5F);
      level.playSound(null, g, Sounds.DG_CREAK.get(), SoundSource.BLOCKS, 6F, 0.45F);
      level.playSound(null, g, Sounds.FX_EARTH.get(), SoundSource.BLOCKS, 4F, 0.5F);
      level.sendParticles(ModParticles.DUST.get(), c.x, c.y, c.z, 60, 3, 3, 0.6, 0.05);
      level.sendParticles(ModParticles.WOOD_CHIP.get(), c.x, c.y, c.z, 30, 3, 3, 0.6, 0.12);
      Vfx.shake(level, c, 0.6F, 40, 20);
   }

   private void closeDoor(ServerLevel level) {
      BlockPos g = rootDoor(level);
      if (g == null) return;
      setStage(level, g, 3);
      for (BlockPos p : doorCells(level)) if (level.getBlockState(p).isAir()) setRecorded(level, p, ModBlocks.PROP_SOLID.get().defaultBlockState());
      level.playSound(null, g, Sounds.SY_ROOTS.get(), SoundSource.BLOCKS, 6F, 0.6F);
      level.playSound(null, g, Sounds.DG_GROAN.get(), SoundSource.BLOCKS, 5F, 0.5F);
      level.playSound(null, g, Sounds.FX_WOOD.get(), SoundSource.BLOCKS, 5F, 0.5F);
      Vfx.shake(level, Vec3.atCenterOf(g), 0.8F, 40, 16);
   }

   @Override
   void tick(ServerLevel level) {
      loaded++;
      long now = level.getGameTime();
      runSched(now);
      if (now % 80 == 0) ambience(level);
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

   /** Le tronc vit : feuilles mortes qui tombent des étages, battement sourd du cœur quand quelqu'un est là. */
   private void ambience(ServerLevel level) {
      if (nearbyPlayers(level, 80).isEmpty()) return;
      Vec3 h = heartPos();
      level.sendParticles(ModParticles.LEAF_AUTUMN.get(), h.x, h.y - 20, h.z, 12, 12, 8, 12, 0.02);
      // la frondaison pourrie laisse tomber feuilles et spores toxiques dans le tronc
      level.sendParticles(ModParticles.LEAF.get(), h.x, h.y + 10, h.z, 18, 16, 4, 16, 0.02);
      level.sendParticles(ModParticles.SPORE.get(), h.x, h.y + 6, h.z, 14, 14, 6, 14, 0.01);
      level.sendParticles(ModParticles.TOXIC_SMOKE.get(), h.x, h.y + 12, h.z, 3, 12, 2, 12, 0.005);
      if (state == READY) level.playSound(null, h.x, h.y, h.z, Sounds.FX_HEARTBEAT.get(), SoundSource.AMBIENT, 3F, 0.5F);
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

   public SylvornServeur findBoss(ServerLevel level) {
      if (boss == null) return null;
      Entity e = level.getEntity(boss);
      return e instanceof SylvornServeur k && k.isAlive() ? k : null;
   }

   /** Il dort dans l'écorce (on l'adopte s'il existe déjà, sinon on le crée, endormi). */
   private void ensureSleeper(ServerLevel level) {
      BlockPos bp = bossPos();
      if (!level.areEntitiesLoaded(ChunkPos.asLong(bp))) return;
      SylvornServeur k = findBoss(level);
      if (k == null) {
         List<SylvornServeur> near = level.getEntitiesOfClass(SylvornServeur.class, new AABB(bp).inflate(40), Entity::isAlive);
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
      if (!(HeraliszSylvorn.SYLVORN.get().create(level) instanceof SylvornServeur e)) return;   // créé par l'usine (version serveur)
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
   /** Un joueur dépose la graine ancestrale sur l'autel. */
   @Override
   public boolean offerSeed(ServerLevel level, Player p, BlockPos altar) {
      if (state != READY) return false;
      ensureSleeper(level);
      SylvornServeur k = findBoss(level);
      if (k == null || !k.dormant()) return false;
      startCountdown(level, 0, altar);
      return true;
   }

   private BlockPos altar;

   private void startCountdown(ServerLevel level, int skip, BlockPos at) {
      participants.clear();
      altar = at != null ? at : prop(level, HeroKind.ALTAR);
      setState(COUNTDOWN, level.getGameTime() - skip);
      if (altar == null) return;
      setStage(level, altar, 1);
      Vec3 top = Vec3.atBottomCenterOf(altar).add(0, 1.15, 0);
      level.playSound(null, altar, Sounds.DG_PLANT.get(), SoundSource.BLOCKS, 3F, 0.8F);
      level.playSound(null, altar, Sounds.SY_CHIMES.get(), SoundSource.BLOCKS, 2F, 0.7F);
      seed = new ItemEntity(level, top.x, top.y, top.z, new ItemStack(HeraliszSylvorn.ANCESTRAL_SEED.get()));
      seed.setNeverPickUp();
      seed.setUnlimitedLifetime();
      seed.setNoGravity(true);
      seed.setDeltaMovement(Vec3.ZERO);
      level.addFreshEntity(seed);
      SylvornServeur k = findBoss(level);
      if (skip == 0 && k != null && SylvornConfig.CINEMATICS.get())
         Vfx.cinematic(level, Vec3.atBottomCenterOf(altar), k.getId(), Sylvorn.OFFER_K, OFFER_TICKS, toDoor(level));
   }

   /** De l'autel vers le mur de racines. */
   private Vec3 toDoor(ServerLevel level) {
      BlockPos g = rootDoor(level);
      if (g == null || altar == null) return inward();
      Vec3 d = new Vec3(g.getX() - altar.getX(), 0, g.getZ() - altar.getZ());
      return d.lengthSqr() < 1e-3 ? inward() : d.normalize();
   }

   /** L'offrande : la graine s'enfonce dans l'autel, la sève court sur le sol jusqu'au mur de racines, qui se met à remuer. */
   private void offering(ServerLevel level, int t) {
      if (altar == null) return;
      Vec3 a = Vec3.atBottomCenterOf(altar);
      BlockPos g = rootDoor(level);
      Vec3 door = g != null ? Vec3.atBottomCenterOf(g) : a.add(inward().scale(8));
      if (seed != null && t < 110) {
         seed.setPos(a.x, a.y + 1.15 - 0.5 * (t / 110.0), a.z);
         seed.setDeltaMovement(Vec3.ZERO);
      }
      if (t % 4 == 0 && t < 110) level.sendParticles(ModParticles.SPORE.get(), a.x, a.y + 1.2, a.z, 2, 0.3, 0.2, 0.3, 0.01);
      if (t == 110) {
         if (seed != null) seed.discard();
         seed = null;
         level.playSound(null, altar, Sounds.FX_GOO.get(), SoundSource.BLOCKS, 3F, 0.6F);
         level.playSound(null, altar, Sounds.SY_WHISPER.get(), SoundSource.BLOCKS, 3F, 0.7F);
         level.sendParticles(ModParticles.SPORE.get(), a.x, a.y + 1.2, a.z, 40, 0.4, 0.4, 0.4, 0.06);
      }
      if (t > 110 && t < 200) {
         // la sève court sur le sol jusqu'au mur
         double u = (t - 110) / 90.0;
         Vec3 q = a.lerp(door, u);
         level.sendParticles(ModParticles.SPORE.get(), q.x, q.y + 0.2, q.z, 3, 0.4, 0.05, 0.4, 0.005);
         if (t % 15 == 0) level.playSound(null, q.x, q.y, q.z, Sounds.SY_ROOTS.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
      }
      if (t == 200 && g != null) {
         level.playSound(null, g, Sounds.DG_GROAN.get(), SoundSource.BLOCKS, 5F, 0.5F);
         level.playSound(null, g, Sounds.DG_CREAK.get(), SoundSource.BLOCKS, 5F, 0.5F);
         level.sendParticles(ModParticles.WOOD_CHIP.get(), door.x, door.y + 4, door.z, 30, 3, 3, 0.5, 0.08);
      }
   }

   private void countdown(ServerLevel level, int t) {
      if (t <= OFFER_TICKS) offering(level, t);
      int left = (COUNTDOWN_TICKS - t + 19) / 20;
      if (t % 20 == 0) {
         for (Player p : nearbyPlayers(level, 80)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_sylvorn.countdown", left)
               .withStyle(net.minecraft.ChatFormatting.GREEN), true);
         }
      }
      BlockPos g = rootDoor(level);
      Vec3 cc = g != null ? Vec3.atBottomCenterOf(g).add(0, 4, 0) : Vec3.atBottomCenterOf(bossPos());
      // derrière le mur : le cœur bat, le bois gémit
      if (t > 60 && t % 30 == 0) level.playSound(null, cc.x, cc.y, cc.z, Sounds.FX_HEARTBEAT.get(), SoundSource.HOSTILE, 4F, 0.5F + t * 0.001F);
      if (t % 90 == 45) level.playSound(null, cc.x, cc.y, cc.z, Sounds.DG_CREAK.get(), SoundSource.HOSTILE, 3F, 0.6F);
      if (t % 120 == 100) level.playSound(null, cc.x, cc.y, cc.z, Sounds.DG_WHISPER.get(), SoundSource.HOSTILE, 3F, 0.7F);
      if (t < COUNTDOWN_TICKS) return;
      participants.clear();
      for (Player p : nearbyPlayers(level, 80)) if (!p.isSpectator()) participants.add(p.getUUID());
      if (participants.isEmpty()) {
         restoreRoom(level);
         setState(READY, level.getGameTime());
         return;
      }
      setState(ENTRY, level.getGameTime());
      heartStage(level, 1);
      SylvornServeur k = findBoss(level);
      if (k != null && SylvornConfig.CINEMATICS.get())
         Vfx.cinematic(level, g != null ? Vec3.atBottomCenterOf(g) : Vec3.atBottomCenterOf(bossPos()), k.getId(), Sylvorn.ENTRY_K, ENTRY_TICKS, inward());
   }

   private List<Player> nearbyPlayers(ServerLevel level, double r) {
      return new ArrayList<>(level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(r, 90, r), pl -> !pl.isSpectator()));
   }

   /** L'entrée : l'équipe en rangs devant le mur de racines ; il s'ouvre ; ils entrent dans le tronc creux et lèvent les yeux vers
    *  le cœur ; le mur se referme derrière eux ; ils se retournent, puis découvrent la silhouette prise dans l'écorce, au fond. */
   private void entry(ServerLevel level, int t) {
      BlockPos g = rootDoor(level);
      Vec3 in = inward();
      Vec3 door = g != null ? Vec3.atBottomCenterOf(g) : Vec3.atBottomCenterOf(bossPos()).add(in.scale(-48));
      Vec3 across = new Vec3(-in.z, 0, in.x);
      if (t == 1) {
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
      if (t == OPEN_T) openDoor(level);
      if (t == SEAL_T) closeDoor(level);
      int i = 0;
      for (UUID id : participants) {
         if (!(level.getPlayerByUUID(id) instanceof ServerPlayer p) || p.isSpectator()) continue;
         int cnt = participants.size(), row = i / 4, inRow = Math.min(4, cnt - row * 4);
         double lane = ((i % 4) - (inRow - 1) / 2.0) * 1.6;
         Vec3 start = door.add(in.scale(-4 - row * 2.2)).add(across.scale(lane));
         Vec3 goal = door.add(in.scale(8 - row * 2.2)).add(across.scale(lane));
         boolean walking = t > OPEN_T + 40 && t < SEAL_T;
         double u = Mth.clamp((t - OPEN_T - 40) / (double) (SEAL_T - OPEN_T - 50), 0, 1);
         Vec3 want = start.lerp(goal, u * u * (3 - 2 * u));
         Vec3 d = want.subtract(p.position());
         double dist = Math.sqrt(d.x * d.x + d.z * d.z);
         if (dist > 1.5) p.teleportTo(want.x, p.getY(), want.z);
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
            if (t < OPEN_T + 40) {
               yaw = base + 6F * Mth.sin(t * 0.05F + i);
               pitch = -4F;
            } else if (t < SEAL_T) {
               yaw = base + 20F * Mth.sin(t * 0.03F + i * 1.7F);
               pitch = -60F * (float) Mth.clamp((t - OPEN_T - 60) / 60.0, 0, 1);
            } else if (t < SEAL_T + 40) {
               float v = Mth.clamp((t - SEAL_T) / 10F, 0, 1);
               yaw = base + 180F * v;
               pitch = 0F;
            } else {
               float v = Mth.clamp((t - SEAL_T - 40) / 30F, 0, 1);
               yaw = base + 180F * (1 - v);
               pitch = -8F * v;
            }
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch,
               java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X, net.minecraft.world.entity.RelativeMovement.Y,
                  net.minecraft.world.entity.RelativeMovement.Z));
         }
         i++;
      }
      if (t == SEAL_T + 60) {
         Vec3 b = Vec3.atBottomCenterOf(bossPos()).add(0, 5, 0);
         level.playSound(null, b.x, b.y, b.z, Sounds.SY_BREATH.get(), SoundSource.HOSTILE, 5F, 0.5F);
      }
      if (t >= ENTRY_TICKS) {
         SylvornServeur k = findBoss(level);
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

   // ------------------------------------------------------------------ fin de combat
   public void deathEnd(ServerLevel level) {
      openDoor(level);
      long now = level.getGameTime();
      nextReady = now + Math.max(SylvornConfig.RESPAWN_MINUTES.get() * 1200L, Sylvorn.REMAINS + 1200L);
      restoreAt = now + Sylvorn.REMAINS;
      boss = null;
      setState(COOLDOWN, now);
   }

   /** Plus personne dans le tronc : il regagne l'écorce, l'Arbre se referme sur lui-même. */
   public void bossLeft(ServerLevel level) {
      SylvornServeur k = findBoss(level);
      if (k != null) k.sleep();
      openDoor(level);
      restoreRoom(level);
      setState(READY, level.getGameTime());
   }

   private void watch(ServerLevel level, long now) {
      SylvornServeur k = findBoss(level);
      if (k == null) {
         List<Sylvorn> dying = level.getEntitiesOfClass(Sylvorn.class, new AABB(bossPos()).inflate(80), Sylvorn::isDeadOrDying);
         if (!dying.isEmpty()) return;
         openDoor(level);
         restoreRoom(level);
         setState(READY, now);
         return;
      }
      if (k.isDeadOrDying()) return;
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(60, 90, 60), pl -> !pl.isSpectator() && !pl.isCreative()).isEmpty();
      lonely = anyone ? 0 : lonely + 20;
      if (lonely < LONELY_LIMIT) return;
      bossLeft(level);
   }

   public boolean forceAwaken(ServerLevel level) {
      if (state != READY && state != COOLDOWN) return false;
      if (restoreAt >= 0) restoreRoom(level);
      nextReady = 0;
      loaded = Math.max(loaded, 81);
      ensureSleeper(level);
      SylvornServeur k = findBoss(level);
      if (k == null || !k.dormant()) return false;
      startCountdown(level, COUNTDOWN_TICKS - 40, null);
      return true;
   }

   public void reset(ServerLevel level) {
      SylvornServeur k = findBoss(level);
      if (k != null) k.sleep();
      nextReady = 0;
      restoreRoom(level);
      props = null;
      doorCells = null;
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
      super.load(tag);                                     // (version commune : BlockEntity.load, puis State et StateTime)
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
      super.onLoad();                                      // (version commune : BlockEntity.onLoad, puis LOADED.add)
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
