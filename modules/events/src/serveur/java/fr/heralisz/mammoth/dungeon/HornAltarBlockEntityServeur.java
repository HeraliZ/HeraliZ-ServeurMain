package fr.heralisz.mammoth.dungeon;

import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.MammothConfig;
import fr.heralisz.mammoth.entity.Mammoth;
import fr.heralisz.mammoth.entity.MammothServeur;
import fr.heralisz.mammoth.sound.Sounds;
import fr.heralisz.mammoth.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link HornAltarBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new HornAltarBlockEntity(...) » côté serveur.
 */
public class HornAltarBlockEntityServeur extends HornAltarBlockEntity {
   public HornAltarBlockEntityServeur(BlockPos pos, BlockState st) {
      super(pos, st);
   }

   /** L'autel le plus proche, en version serveur (null s'il n'y en a pas, ou si ce n'est pas la version serveur). */
   public static HornAltarBlockEntityServeur nearestServeur(Level level, BlockPos p, double r) {
      return nearest(level, p, r) instanceof HornAltarBlockEntityServeur a ? a : null;
   }

   private static final int LONELY_LIMIT = 1200, RESTORE_DELAY = 600;
   private static final int SCAN_R = 72, SCAN_DOWN = 12, SCAN_UP = 44;
   private UUID boss;
   private BlockPos bossPos;
   private float bossYaw = Float.NaN;
   private int loaded, lonely, litUpTo;
   private List<BlockPos> totems, alertables;
   private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
   private boolean alert;
   private long restoreAt = -1;

   public BlockPos bossPos() {
      if (bossPos != null) return bossPos;
      Direction f = facing();
      return worldPosition.relative(f.getOpposite(), 16);
   }

   public float bossYaw() {
      if (!Float.isNaN(bossYaw)) return bossYaw;
      Direction f = facing();
      return (float) (Mth.atan2(f.getStepZ(), f.getStepX()) * Mth.RAD_TO_DEG) - 90F;
   }

   public void setBoss(BlockPos pos, float yaw) {
      this.bossPos = pos;
      this.bossYaw = yaw;
      setChanged();
   }

   // ------------------------------------------------------------------ remise en état
   /** À appeler AVANT de modifier un bloc de l'arène pendant le combat : il reprendra cet état après. */
   public void recordChange(BlockPos p, BlockState original) {
      changes.putIfAbsent(p.immutable(), original);
      setChanged();
   }

   public void restoreRoom(ServerLevel level) {
      for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(40))) {
         for (BlockPos p : changes.keySet()) {
            if (pl.getBoundingBox().intersects(new AABB(p))) {
               Vec3 c = center();
               pl.teleportTo(c.x, c.y, c.z);
               break;
            }
         }
      }
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) if (level.isLoaded(e.getKey())) level.setBlock(e.getKey(), e.getValue(), 3);
      changes.clear();
      restoreAt = -1;
      setChanged();
   }

   // ------------------------------------------------------------------ recherche des blocs de l'arène
   private List<BlockPos> scan(ServerLevel level, java.util.function.Predicate<BlockState> test) {
      return scan(level, test, SCAN_R, SCAN_DOWN, SCAN_UP);
   }

   private List<BlockPos> scan(ServerLevel level, java.util.function.Predicate<BlockState> test, int R, int down, int up) {
      List<BlockPos> out = new ArrayList<>();
      int cx0 = (worldPosition.getX() - R) >> 4, cx1 = (worldPosition.getX() + R) >> 4;
      int cz0 = (worldPosition.getZ() - R) >> 4, cz1 = (worldPosition.getZ() + R) >> 4;
      int y0 = worldPosition.getY() - down, y1 = worldPosition.getY() + up;
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

   private List<BlockPos> totems(ServerLevel level) {
      if (totems == null) {
         totems = scan(level, s -> s.getBlock() instanceof BoneTotemBlock);
         totems.sort(Comparator.comparingDouble(p -> p.distSqr(worldPosition)));
      }
      return totems;
   }

   private List<BlockPos> alertables(ServerLevel level) {
      if (alertables == null) alertables = scan(level, s -> s.getBlock() instanceof TribeBrazierBlock || s.getBlock() instanceof IceGateBlock
         || s.hasProperty(SetPieceBlocks.ALERT));
      return alertables;
   }

   private void setAlert(ServerLevel level, boolean on) {
      if (alert == on) return;
      alert = on;
      setChanged();
      for (BlockPos p : alertables(level)) {
         if (!level.isLoaded(p)) continue;
         BlockState st = level.getBlockState(p);
         if (st.hasProperty(SetPieceBlocks.ALERT)) {
            if (on && st.getBlock() instanceof SetPieceBlocks.Meltwater) continue;     // l'eau ne rougit que par la vague de chaleur
            if (st.getValue(SetPieceBlocks.ALERT) != on) level.setBlock(p, st.setValue(SetPieceBlocks.ALERT, on), 2);
         } else if (st.getBlock() instanceof TribeBrazierBlock) {
            if (st.getValue(TwoStateBlock.LIT) != on) level.setBlock(p, st.setValue(TwoStateBlock.LIT, on), 3);
         } else if (st.getBlock() instanceof IceGateBlock && level.getBlockEntity(p) instanceof IceGateBlockEntity g) {
            IceGates.setClosed(g, level, on);
            level.playSound(null, p, on ? Sounds.ICE_CRACK.get() : Sounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 4F, on ? 0.7F : 1.2F);
         }
      }
   }

   /** Mort d'Ymirak : l'alerte s'éteint en vague depuis sa carcasse (le rouge reflue vers le bleu, bloc par bloc). */
   public void calmWave(ServerLevel level, Vec3 from, double r0, double r1) {
      for (BlockPos p : alertables(level)) {
         double d = Math.sqrt(p.distToCenterSqr(from));
         if (d < r0 || d >= r1 || !level.isLoaded(p)) continue;
         BlockState st = level.getBlockState(p);
         if (st.hasProperty(SetPieceBlocks.ALERT) && st.getValue(SetPieceBlocks.ALERT)) level.setBlock(p, st.setValue(SetPieceBlocks.ALERT, false), 2);
         else if (st.getBlock() instanceof TribeBrazierBlock && st.getValue(TwoStateBlock.LIT))
            level.setBlock(p, st.setValue(TwoStateBlock.LIT, false), 2);
      }
   }

   /** Cinématique d'apparition : quand il barrit, les braseros et les totems lancent une gerbe de flammes d'aurore. */
   public void flare(ServerLevel level) {
      List<BlockPos> all = new ArrayList<>(alertables(level));
      all.addAll(totems(level));
      for (BlockPos p : all) {
         if (!level.isLoaded(p)) continue;
         BlockState st = level.getBlockState(p);
         if (!(st.getBlock() instanceof TribeBrazierBlock) && !(st.getBlock() instanceof BoneTotemBlock)) continue;
         level.sendParticles(fr.heralisz.mammoth.fx.Particles.AURORA.get(), p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 16, 0.25, 0.5, 0.25, 0.08);
      }
   }

   private void setTotems(ServerLevel level, boolean on) {
      for (BlockPos p : totems(level)) {
         if (!level.isLoaded(p)) continue;
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof BoneTotemBlock && st.getValue(TwoStateBlock.LIT) != on) level.setBlock(p, st.setValue(TwoStateBlock.LIT, on), 3);
      }
      litUpTo = 0;
   }

   @Override
   void tick(ServerLevel level) {
      loaded++;
      long now = level.getGameTime();
      if (alert && state != AWAKE && now % 20 == 0) setAlert(level, false);
      switch (state) {
         case READY -> {
            if (loaded > 60 && now % 20 == 0) ensureSleeper(level);
            if (now % 12 == 0) level.sendParticles(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.5,
               worldPosition.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.01);
         }
         case RITUAL -> ritual(level, (int) (now - stateTime));
         case AWAKE -> {
            if (now % 20 == 0) watch(level, now);
         }
         case GUARDIANS -> guardians(level, (int) (now - stateTime));
         case COOLDOWN -> {
            if (restoreAt >= 0 && now >= restoreAt) restoreRoom(level);
            if (now >= nextReady && now % 20 == 0) {
               if (restoreAt >= 0) restoreRoom(level);
               setTotems(level, false);
               setState(READY, now);
            }
         }
         default -> setState(READY, now);
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

   private MammothServeur findBoss(ServerLevel level) {
      if (boss == null) return null;
      Entity e = level.getEntity(boss);
      return e instanceof MammothServeur m && m.isAlive() ? m : null;
   }

   /** Ymirak est dans sa glace (on l'adopte s'il existe déjà, sinon on le crée, endormi). */
   private void ensureSleeper(ServerLevel level) {
      BlockPos bp = bossPos();
      if (!level.areEntitiesLoaded(ChunkPos.asLong(bp))) return;
      MammothServeur m = findBoss(level);
      if (m == null) {
         List<MammothServeur> near = level.getEntitiesOfClass(MammothServeur.class, new AABB(bp).inflate(12), Entity::isAlive);
         if (!near.isEmpty()) {
            m = near.get(0);
            boss = m.getUUID();
            setChanged();
         }
      }
      if (m != null) {
         m.setAltar(worldPosition);
         if (!m.dormant() && !m.isDeadOrDying()) m.sleep();
         return;
      }
      // créé par son type : la version serveur (IA) ; jamais new Mammoth(...)
      if (!(HeraliszMammoth.MAMMOTH.get().create(level) instanceof MammothServeur e)) return;
      float yaw = bossYaw();
      e.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, yaw, 0);
      e.setYHeadRot(yaw);
      e.setYBodyRot(yaw);
      e.setDormant(true);
      e.setHome(bp);
      e.setAltar(worldPosition);
      level.addFreshEntity(e);
      boss = e.getUUID();
      setChanged();
   }

   /** Clic droit sur l'autel. */
   @Override
   public void use(ServerPlayer p) {
      ServerLevel level = (ServerLevel) this.level;
      if (level == null) return;
      long now = level.getGameTime();
      if (!p.getMainHandItem().is(HeraliszMammoth.WAR_HORN.get())) {
         tell(p, "need");
         return;
      }
      switch (state) {
         case RITUAL -> {
            tell(p, "busy");
            return;
         }
         case AWAKE -> {
            tell(p, "awake");
            return;
         }
         case COOLDOWN -> {
            long min = Math.max(1, (nextReady - now + 1199) / 1200);
            p.displayClientMessage(Component.translatable("altar.heralisz_mammoth.cooldown", min), true);
            return;
         }
         default -> {
         }
      }
      MammothServeur sleeper = findBoss(level);
      if (sleeper == null || !sleeper.dormant()) {
         ensureSleeper(level);
         tell(p, "busy");
         return;
      }
      if (!p.getAbilities().instabuild) {
         ItemStack st = p.getItemInHand(InteractionHand.MAIN_HAND);
         st.shrink(1);
      }
      litUpTo = 0;
      totems = null;
      setState(RITUAL, now);
      level.playSound(null, worldPosition, Sounds.TRUMPET.get(), SoundSource.BLOCKS, 8F, 1.35F);
      Vfx.shake(level, center(), 0.4F, 40, 20);
      tell(p, "blown");
   }

   private void tell(ServerPlayer p, String key) {
      p.displayClientMessage(Component.translatable("altar.heralisz_mammoth." + key), true);
   }

   private void ritual(ServerLevel level, int t) {
      Vec3 c = center();
      if (t == 20 || t == 44 || t == 62) level.playSound(null, worldPosition, Sounds.HEARTBEAT.get(), SoundSource.BLOCKS, 6F, 0.8F + t / 200F);
      if (t == 30) level.playSound(null, worldPosition, Sounds.AMB_MOAN.get(), SoundSource.BLOCKS, 6F, 0.8F);
      if (t % 4 == 0) level.sendParticles(fr.heralisz.mammoth.fx.Particles.AURORA.get(), c.x, c.y + 1.2, c.z, 3, 0.4, 0.3, 0.4, 0.02);
      if (t >= LIGHTS_FROM) {
         List<BlockPos> list = totems(level);
         int want = (int) Math.ceil(list.size() * Mth.clamp((t - LIGHTS_FROM) / (float) (LIGHTS_TO - LIGHTS_FROM), 0, 1));
         while (litUpTo < want && litUpTo < list.size()) {
            BlockPos p = list.get(litUpTo++);
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof BoneTotemBlock && !st.getValue(TwoStateBlock.LIT)) {
               level.setBlock(p, st.setValue(TwoStateBlock.LIT, true), 3);
               level.playSound(null, p, Sounds.ICE_CRACK.get(), SoundSource.BLOCKS, 2F, 1.3F + level.random.nextFloat() * 0.3F);
               level.sendParticles(fr.heralisz.mammoth.fx.Particles.AURORA.get(), p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.03);
            }
         }
      }
      if (t >= RITUAL_TICKS) {
         lonely = 0;
         setState(GUARDIANS, level.getGameTime());
      }
   }

   // ------------------------------------------------------------------ les Gardiens d'Ymirak
   private void setGate(ServerLevel level, boolean closed) {
      for (BlockPos p : alertables(level)) {
         if (level.isLoaded(p) && level.getBlockEntity(p) instanceof IceGateBlockEntity g) IceGates.setClosed(g, level, closed);
      }
   }

   private List<BlockPos> titans(ServerLevel level) {
      List<BlockPos> out = new ArrayList<>();
      for (BlockPos p : alertables(level)) if (level.getBlockState(p).getBlock() instanceof SetPieceBlocks.Titan) out.add(p);
      // ordre des plans de la cinématique (repère d'Ymirak) : les deux titans lointains (droite puis gauche), puis les deux proches
      MammothServeur m = findBoss(level);
      if (m != null) {
         Vec3 o = m.position(), f = m.fwd(), r = new Vec3(-f.z, 0, f.x);
         out.sort(Comparator.comparingDouble((BlockPos p) -> -Math.round(Vec3.atCenterOf(p).subtract(o).dot(f) / 8.0))
            .thenComparingDouble(p -> -Vec3.atCenterOf(p).subtract(o).dot(r)));
      } else {
         out.sort(Comparator.comparingDouble(p -> p.distSqr(worldPosition)));
      }
      return out;
   }

   /**
    * Le cor a sonné : la porte se ferme, les titans pris dans la glace rougissent un à un (cinématique), la glace éclate et les
    * Gardiens en sortent. Quand le dernier tombe, il court s'arracher le cœur et l'enfonce dans la glace d'Ymirak (Draugr).
    */
   private void guardians(ServerLevel level, int t) {
      MammothServeur m = findBoss(level);
      if (t == 1) {
         setGate(level, true);
         if (m != null && MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, m.position(), m.getId(), Mammoth.THAW_K, 330);
         level.playSound(null, worldPosition, Sounds.AMB_MOAN.get(), SoundSource.BLOCKS, 8F, 0.6F);
      }
      List<BlockPos> ts = titans(level);
      if (t >= 30 && t < 270 && (t - 30) % 60 == 0) {                 // les titans rougissent l'un après l'autre (un plan chacun)
         int i = (t - 30) / 60;
         if (i < ts.size()) {
            BlockPos p = ts.get(i);
            BlockState st = level.getBlockState(p);
            recordChange(p, st);
            level.setBlock(p, st.setValue(SetPieceBlocks.ALERT, true), 3);
            level.playSound(null, p, Sounds.HEARTBEAT.get(), SoundSource.BLOCKS, 6F, 0.7F);
            level.playSound(null, p, Sounds.ICE_CRACK.get(), SoundSource.BLOCKS, 5F, 0.6F);
            level.sendParticles(fr.heralisz.mammoth.fx.Particles.CINDER.get(), p.getX() + 0.5, p.getY() + 9, p.getZ() + 0.5, 40, 2, 1, 1.2, 0.05);
         }
      }
      if (t >= 30 && t < 280 && t % 3 == 0) {                          // les titans chauffent : braises, vapeur, gouttes
         for (BlockPos p : ts) {
            BlockState st = level.getBlockState(p);
            if (!st.hasProperty(SetPieceBlocks.ALERT) || !st.getValue(SetPieceBlocks.ALERT)) continue;
            double x = p.getX() + 0.5, z = p.getZ() + 0.5;
            level.sendParticles(fr.heralisz.mammoth.fx.Particles.CINDER.get(), x, p.getY() + 8 + level.random.nextDouble() * 3, z, 6, 2.2, 0.8, 1.2, 0.04);
            level.sendParticles(fr.heralisz.mammoth.fx.Particles.MIST.get(), x, p.getY() + 10, z, 3, 2, 0.5, 1, 0.03);
            level.sendParticles(fr.heralisz.mammoth.fx.Particles.SHARD.get(), x, p.getY() + 6, z, 2, 2.5, 3, 1.2, 0.02);
            if (t % 15 == 0) level.playSound(null, p, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5F, 0.6F);
         }
      }
      if (heatStart >= 0) heatTick(level, m, (int) (level.getGameTime() - heatStart));
      if (t == 280) {                                                   // la glace éclate, les Gardiens sortent
         for (BlockPos p : ts) {
            BlockState st = level.getBlockState(p);
            float yaw = st.getValue(SetPieceBlocks.Titan.FACING).toYRot();
            recordChange(p, st);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            Vec3 c = Vec3.atBottomCenterOf(p);
            Vfx.debris(level, c.add(0, 4, 0), 3F, 7F, 50, ModBlocks.ICE_CHUNK.get().defaultBlockState(), Vec3.ZERO);
            Vfx.flash(level, c.add(0, 5, 0), 8F, 10);
            level.playSound(null, p, Sounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 7F, 0.7F);
            if (m != null) {
               if (HeraliszMammoth.DRAUGR.get().create(level) instanceof fr.heralisz.mammoth.entity.DraugrServeur g) {
                  g.moveTo(c.x, c.y, c.z, yaw, 0);
                  g.setYBodyRot(yaw);
                  g.setYHeadRot(yaw);
                  g.setMaster(m);
                  g.makeGuardian();
                  level.addFreshEntity(g);
               }
            }
         }
         Vfx.shake(level, center(), 1.6F, 80, 24);
      }
      if (t > 300 && t % 20 == 0) {
         boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(60), pl -> !pl.isSpectator()).isEmpty();
         lonely = anyone ? 0 : lonely + 20;
         if (lonely >= LONELY_LIMIT || m == null) {                    // tout le monde est parti : on remet la salle
            if (m != null) m.sleep();
            heatStart = -1;
            setGate(level, false);
            setTotems(level, false);
            restoreRoom(level);
            setState(READY, level.getGameTime());
            return;
         }
         boolean guardiansLeft = !level.getEntitiesOfClass(fr.heralisz.mammoth.entity.Draugr.class, new AABB(worldPosition).inflate(80),
            fr.heralisz.mammoth.entity.Draugr::guardian).isEmpty();
         if (!guardiansLeft && m.dormant() && heatStart < 0 && t > 400) guardiansDone(level);      // filet de sécurité (Gardiens disparus)
      }
   }

   /**
    * Ymirak est mort : la glace qui murait les trois passages secrets s'effondre (par vagues, avec fracas), les joueurs peuvent
    * sortir ailleurs que par l'entrée. Remis en place avec le reste de la salle.
    */
   public void openExits(ServerLevel level) {
      List<BlockPos> seals = scan(level, s -> s.is(ModBlocks.EXIT_SEAL.get()), 110, 30, 80);
      seals.sort(Comparator.comparingInt(BlockPos::getY));
      java.util.Set<Long> done = new java.util.HashSet<>();
      for (BlockPos p : seals) {
         recordChange(p, level.getBlockState(p));
         level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
         long col = ((long) (p.getX() >> 2) << 32) ^ (p.getZ() >> 2);
         if (done.add(col)) {
            Vfx.debris(level, Vec3.atCenterOf(p), 2.5F, 5F, 26, ModBlocks.ICE_CHUNK.get().defaultBlockState(), Vec3.ZERO);
            Vfx.debris(level, Vec3.atCenterOf(p), 2F, 3F, 12, ModBlocks.RIME_BRICKS.get().defaultBlockState(), Vec3.ZERO);
            level.playSound(null, p, Sounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 6F, 0.6F);
            level.playSound(null, p, Sounds.STOMP.get(), SoundSource.BLOCKS, 5F, 0.5F);
            level.sendParticles(fr.heralisz.mammoth.fx.Particles.MIST.get(), p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 20, 1.2, 1.2, 1.2, 0.03);
            Vfx.shake(level, Vec3.atCenterOf(p), 0.8F, 40, 16);
         }
      }
   }

   /** Le cœur est dans la glace : Ymirak s'éveille (sa propre cinématique prend le relais). */
   public void guardiansDone(ServerLevel level) {
      if (state != GUARDIANS) return;
      if (heatCells != null) while (heatDone < heatCells.size()) {       // toute l'eau restante rougit
         BlockPos p = heatCells.get(heatDone++);
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof SetPieceBlocks.Meltwater && !st.getValue(SetPieceBlocks.ALERT)) level.setBlock(p, st.setValue(SetPieceBlocks.ALERT, true), 2);
      }
      heatStart = -1;
      awaken(level);
   }

   // ------------------------------------------------------------------ la vague de chaleur (du piédestal à la prison)
   private long heatStart = -1;
   private List<BlockPos> heatCells;
   private int[] heatDist;
   private int heatMax;
   private static final float HEAT_SPEED = 0.45F;                     // cases par tick

   /** Le cœur brûle sur son piédestal : l'eau de fonte chauffe de proche en proche, rougit, et court jusqu'à la prison. */
   public void startHeat(ServerLevel level, BlockPos socket) {
      if (state != GUARDIANS || heatStart >= 0) return;
      List<BlockPos> water = new ArrayList<>();
      for (BlockPos p : alertables(level)) if (level.getBlockState(p).getBlock() instanceof SetPieceBlocks.Meltwater) water.add(p);
      java.util.Map<BlockPos, Integer> dist = new java.util.HashMap<>();
      java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
      java.util.Set<BlockPos> set = new java.util.HashSet<>(water);
      for (BlockPos p : water) {
         if (Math.abs(p.getX() - socket.getX()) + Math.abs(p.getZ() - socket.getZ()) <= 6 && Math.abs(p.getY() - socket.getY()) <= 2) {
            dist.put(p, Math.abs(p.getX() - socket.getX()) + Math.abs(p.getZ() - socket.getZ()));
            q.add(p);
         }
      }
      if (q.isEmpty() && dist.isEmpty() && !water.isEmpty()) {           // aucune graine : on part de la case la plus proche
         BlockPos near = water.stream().min(Comparator.comparingDouble(p -> p.distSqr(socket))).get();
         dist.put(near, 0);
         q.add(near);
      }
      while (!q.isEmpty()) {                                             // voisins en 3D (coins, marches) : tout le réseau est atteint
         BlockPos c = q.poll();
         for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos n = c.offset(dx, dy, dz);
            if (set.contains(n) && !dist.containsKey(n)) {
               dist.put(n, dist.get(c) + 1);
               q.add(n);
            }
         }
      }
      for (BlockPos p : water) {                                         // morceaux isolés : chauffés selon leur distance au cœur
         if (!dist.containsKey(p)) dist.put(p, (int) Math.sqrt(p.distSqr(socket)));
      }
      heatCells = new ArrayList<>(dist.keySet());
      heatCells.sort(Comparator.comparingInt(dist::get));
      heatDist = heatCells.stream().mapToInt(dist::get).toArray();
      heatMax = heatDist.length == 0 ? 0 : heatDist[heatDist.length - 1];
      heatStart = level.getGameTime();
      heatDone = 0;
      MammothServeur m = findBoss(level);
      if (m != null && MammothConfig.CINEMATICS.get()) Vfx.cinematic(level, m.position(), m.getId(), Mammoth.HEAT_K, (int) (heatMax / HEAT_SPEED) + 40);
      level.playSound(null, socket, Sounds.HEARTBEAT.get(), SoundSource.BLOCKS, 8F, 0.8F);
   }

   private int heatDone;

   private void heatTick(ServerLevel level, MammothServeur m, int t) {
      float front = t * HEAT_SPEED;
      while (heatDone < heatCells.size() && heatDist[heatDone] <= front) {
         BlockPos p = heatCells.get(heatDone++);
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof SetPieceBlocks.Meltwater && !st.getValue(SetPieceBlocks.ALERT)) {
            level.setBlock(p, st.setValue(SetPieceBlocks.ALERT, true), 2);
            level.sendParticles(fr.heralisz.mammoth.fx.Particles.MIST.get(), p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.02);
            if (heatDone % 6 == 0) level.playSound(null, p, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 0.7F);
         }
      }
      if (t == (int) (heatMax / HEAT_SPEED) + 20) guardiansDone(level);    // l'eau brûlante atteint la prison : elle cède
   }

   private void awaken(ServerLevel level) {
      long now = level.getGameTime();
      MammothServeur m = findBoss(level);
      if (m != null && m.dormant()) {
         m.setAltar(worldPosition);
         m.wake();
      } else if (m == null) {
         ensureSleeper(level);
         m = findBoss(level);
         if (m != null) m.wake();
      }
      lonely = 0;
      setState(AWAKE, now);
      setAlert(level, true);
   }

   private void watch(ServerLevel level, long now) {
      // pendant la cinématique de mort, la salle reste fermée et c'est le boss qui éteint l'alerte (vague qui s'étend)
      if (boss != null && level.getEntity(boss) instanceof Mammoth md && md.isDeadOrDying() && md.deathTime < Mammoth.DEATH_TICKS) return;
      MammothServeur m = findBoss(level);
      if (m == null) {
         if (!level.areEntitiesLoaded(ChunkPos.asLong(bossPos()))) return;
         boss = null;
         // la carcasse gelée reste cinq minutes : la salle n'est remise en état qu'après
         long remains = Mammoth.REMAINS - Mammoth.DEATH_TICKS + 200L;
         nextReady = now + Math.max(MammothConfig.RESPAWN_MINUTES.get() * 1200L, remains + 1200L);
         restoreAt = now + remains;
         setState(COOLDOWN, now);
         setAlert(level, false);
         openExits(level);                                                // filet : les passages secrets s'ouvrent aussi ici
         return;
      }
      if (m.isDeadOrDying()) return;
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(60), pl -> !pl.isSpectator()).isEmpty();
      lonely = anyone ? 0 : lonely + 20;
      if (lonely < LONELY_LIMIT) return;
      m.sleep();
      setTotems(level, false);
      restoreRoom(level);
      setAlert(level, false);
      setState(READY, now);
   }

   /** Réveil immédiat (/boss mammouth eveil) : sans cor ni rituel. */
   public boolean forceAwaken(ServerLevel level) {
      if (state == AWAKE || state == RITUAL || state == GUARDIANS) return false;
      if (restoreAt >= 0) restoreRoom(level);
      loaded = Math.max(loaded, 61);
      ensureSleeper(level);
      MammothServeur sleeper = findBoss(level);
      if (sleeper == null || !sleeper.dormant()) return false;
      setTotems(level, true);
      awaken(level);
      return true;
   }

   /** Remise à zéro (/boss mammouth reset). */
   public void reset(ServerLevel level) {
      MammothServeur m = findBoss(level);
      if (m != null) m.sleep();
      nextReady = 0;
      setTotems(level, false);
      restoreRoom(level);
      setAlert(level, false);
      setState(READY, level.getGameTime());
   }

   // ------------------------------------------------------------------ sauvegarde / synchro
   @Override
   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putInt("State", state);
      tag.putLong("StateTime", stateTime);
      tag.putLong("NextReady", nextReady);
      if (boss != null) tag.putUUID("Boss", boss);
      if (bossPos != null) tag.put("BossPos", NbtUtils.writeBlockPos(bossPos));
      if (!Float.isNaN(bossYaw)) tag.putFloat("BossYaw", bossYaw);
      tag.putLong("RestoreAt", restoreAt);
      tag.putBoolean("Alert", alert);
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
      if (tag.hasUUID("Boss")) boss = tag.getUUID("Boss");
      if (tag.contains("BossPos")) bossPos = NbtUtils.readBlockPos(tag.getCompound("BossPos"));
      if (tag.contains("BossYaw")) bossYaw = tag.getFloat("BossYaw");
      if (tag.contains("RestoreAt")) restoreAt = tag.getLong("RestoreAt");
      alert = tag.getBoolean("Alert");
      if (tag.contains("Changes")) {
         changes.clear();
         var reg = level != null ? level.holderLookup(net.minecraft.core.registries.Registries.BLOCK)
            : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
         for (Tag t : tag.getList("Changes", 10)) {
            CompoundTag c = (CompoundTag) t;
            changes.put(NbtUtils.readBlockPos(c.getCompound("Pos")), NbtUtils.readBlockState(reg, c.getCompound("State")));
         }
      }
      if (state == RITUAL && level != null && !level.isClientSide) state = READY;
   }

   @Override
   public void onLoad() {
      super.onLoad();
      LOADED.add(this);
      if (state == RITUAL && level != null && !level.isClientSide) state = READY;
   }

   @Override
   public CompoundTag getUpdateTag() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("State", state);
      tag.putLong("StateTime", stateTime);
      tag.putLong("NextReady", nextReady);
      return tag;
   }
}
