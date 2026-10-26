package fr.heralisz.plague.dungeon;

import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.PlagueConfig;
import fr.heralisz.plague.entity.PlagueBearer;
import fr.heralisz.plague.entity.PlagueBearerServeur;
import fr.heralisz.plague.particle.ModParticles;
import fr.heralisz.plague.sound.Sounds;
import fr.heralisz.plague.vfx.Vfx;
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
 * Partie SERVEUR de {@link StationHallBE} (docs/METHODE_CLIENT_SERVEUR.md §5.2) : le rituel (volant sur la vanne de purge,
 * alerte, décompte, rideau, sas de décontamination), la garde du Porteur de Peste, la fin de combat, la remise en état de la
 * salle et la sauvegarde. Créée par l'usine du code commun (ModBlocks.STATION_USINE) dès que la partie serveur est dans le
 * jar ; ne jamais faire « new StationHallBE(...) ». Avec -complet, les entités de bloc du client sont aussi cette version :
 * le code reste sûr côté client comme dans le mod d'origine (ticker serveur seulement, vérifications isClientSide).
 */
public class StationHallBEServeur extends StationHallBE {
   public StationHallBEServeur(BlockPos pos, BlockState st) {
      super(pos, st);
   }

   /** La salle la plus proche, en version serveur (null s'il n'y en a pas, ou si ce n'est pas la version serveur). */
   public static StationHallBEServeur nearestServeur(Level level, BlockPos p, double r) {
      return nearest(level, p, r) instanceof StationHallBEServeur s ? s : null;
   }

   private static final int LONELY_LIMIT = 1200, SCAN_R = 72, SCAN_DOWN = 12, SCAN_UP = 60;
   private long nextReady, restoreAt = -1;
   private UUID boss;
   private BlockPos doorPos, gatePos;
   private float bossYaw = Float.NaN, doorYaw = Float.NaN;
   private int loaded, lonely;
   private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
   private Map<HeroKind, List<BlockPos>> props;
   private List<BlockPos> doorCells;
   private final List<UUID> participants = new ArrayList<>();
   private boolean needRestore;
   private ItemEntity card;

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

   /** De la porte blindée vers la salle. */
   public Vec3 inward() {
      return Vec3.directionFromRotation(0, Float.isNaN(doorYaw) ? bossYaw() + 180F : doorYaw);
   }

   public void setMarkers(BlockPos boss, float yaw, BlockPos door, float dYaw, BlockPos gate) {
      this.bossPos = boss;
      this.bossYaw = yaw;
      this.doorPos = door;
      this.doorYaw = dYaw;
      this.gatePos = gate;
      sync();
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
      if (card != null) card.discard();
      card = null;
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) if (level.isLoaded(e.getKey())) level.setBlock(e.getKey(), e.getValue(), 3);
      changes.clear();
      restoreAt = -1;
      sched.clear();
      tankState = TANK_IDLE;
      tankTime = level.getGameTime();
      sync();
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

   private void setStages(ServerLevel level, HeroKind k, int stage) {
      for (BlockPos p : props(level).get(k)) setStage(level, p, stage);
   }

   /** Repère la cuve et ses bobines (synchronisés : le rendu de la salle dessine le verre, le liquide et les arcs). */
   private void locateTank(ServerLevel level) {
      BlockPos t = prop(level, HeroKind.SLUDGE_VAT);
      List<BlockPos> c = props(level).get(HeroKind.GAS_VENT);
      if (t != null && (!t.equals(tankPos) || coils.size() != c.size())) {
         tankPos = t;
         coils.clear();
         coils.addAll(c);
         sync();
      }
   }

   /** L'alerte : gyrophares, écrans rouges, sirène. */
   private void alarm(ServerLevel level, boolean on) {
      setStages(level, HeroKind.ALARM_BEACON, on ? 1 : 0);
      setStages(level, HeroKind.QUARANTINE_SIGN, on ? 1 : 0);
   }

   /** Les bobines montent en charge : la cuve crépite, les arcs frappent le verre. */
   public void chargeTank(ServerLevel level) {
      locateTank(level);
      setStages(level, HeroKind.GAS_VENT, 1);
      if (tankPos != null) setStage(level, tankPos, 1);
      if (tankState != TANK_CHARGE) {
         tankState = TANK_CHARGE;
         tankTime = level.getGameTime();
         sync();
      }
   }

   /** La cuve vole en éclats : le verre et le liquide disparaissent, les dents restent ; son volume n'est plus un obstacle. */
   public void breakTank(ServerLevel level) {
      locateTank(level);
      if (tankPos == null) return;
      setStages(level, HeroKind.GAS_VENT, 0);
      tankState = TANK_BROKEN;
      tankTime = level.getGameTime();
      sync();
      // le liquide déferle dans la douve : il fume longtemps
      Vec3 c = Vec3.atBottomCenterOf(tankPos);
      for (int i = 0; i < 12; i++) later(level, i * 10, () -> level.sendParticles(ModParticles.TOXIC_SMOKE.get(), c.x, c.y + 0.6, c.z, 20, 6, 0.2, 6, 0.01));
   }

   // ------------------------------------------------------------------ la porte blindée
   private BlockPos blastDoor(ServerLevel level) {
      return prop(level, HeroKind.SHUTTER_DOOR);
   }

   /** Le volume invisible qui ferme l'ouverture tant que la porte est close. */
   private List<BlockPos> doorCells(ServerLevel level) {
      if (doorCells == null) {
         doorCells = new ArrayList<>();
         BlockPos g = blastDoor(level);
         if (g != null) {
            for (BlockPos p : BlockPos.betweenClosed(g.offset(-4, 0, -1), g.offset(4, 8, 1))) {
               if (level.getBlockState(p).is(ModBlocks.PROP_SOLID.get())) doorCells.add(p.immutable());
            }
         }
      }
      return doorCells;
   }

   /** Le volant tourne, les pênes rentrent, la porte recule puis roule dans le mur (6 s). */
   private void openDoor(ServerLevel level) {
      BlockPos g = blastDoor(level);
      if (g == null) return;
      doorCells(level);
      if (level.getBlockState(g).getValue(HeroPropBlock.STAGE) == 2) return;
      setStage(level, g, 1);
      later(level, 120, () -> setStage(level, g, 2));
      level.playSound(null, g, Sounds.DG_VALVE_TURN.get(), SoundSource.BLOCKS, 4F, 0.7F);
      later(level, 32, () -> level.playSound(null, g, Sounds.FX_METAL_LAND.get(), SoundSource.BLOCKS, 4F, 0.6F));
      later(level, 44, () -> level.playSound(null, g, Sounds.DG_STEAM.get(), SoundSource.BLOCKS, 4F, 0.8F));
      later(level, 60, () -> {
         level.playSound(null, g, Sounds.DG_VENT.get(), SoundSource.BLOCKS, 5F, 0.5F);
         for (BlockPos p : doorCells(level)) if (level.getBlockState(p).is(ModBlocks.PROP_SOLID.get())) setRecorded(level, p, Blocks.AIR.defaultBlockState());
      });
      Vec3 c = Vec3.atBottomCenterOf(g).add(0, 4, 0);
      later(level, 44, () -> level.sendParticles(ModParticles.STEAM.get(), c.x, c.y, c.z, 60, 3, 3, 0.4, 0.05));
      for (int i = 0; i < 5; i++) later(level, 60 + i * 12, () -> level.playSound(null, g, Sounds.DG_SPARK.get(), SoundSource.BLOCKS, 1.5F, 0.6F));
      Vfx.shake(level, c, 0.3F, 30, 12);
   }

   private void closeDoor(ServerLevel level) {
      BlockPos g = blastDoor(level);
      if (g == null) return;
      setStage(level, g, 3);
      for (BlockPos p : doorCells(level)) if (level.getBlockState(p).isAir()) setRecorded(level, p, ModBlocks.PROP_SOLID.get().defaultBlockState());
      level.playSound(null, g, Sounds.DG_VENT.get(), SoundSource.BLOCKS, 5F, 0.7F);
      later(level, 40, () -> {
         level.playSound(null, g, Sounds.FX_METAL_LAND.get(), SoundSource.BLOCKS, 7F, 0.4F);
         level.playSound(null, g, Sounds.FX_EXPLODE.get(), SoundSource.BLOCKS, 4F, 0.5F);
         Vfx.shake(level, Vec3.atCenterOf(g), 1.0F, 50, 16);
         level.sendParticles(ModParticles.DUST.get(), g.getX() + 0.5, g.getY() + 3, g.getZ() + 0.5, 80, 3.5, 3, 0.5, 0.1);
      });
      later(level, 48, () -> level.playSound(null, g, Sounds.DG_VALVE_TURN.get(), SoundSource.BLOCKS, 4F, 0.9F));
   }

   @Override
   void tick(ServerLevel level) {
      loaded++;
      long now = level.getGameTime();
      runSched(now);
      if (now % 80 == 0) ambience(level);
      if (loaded == 40 || now % 400 == 0) locateTank(level);
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
            if (now % 40 == 0) siren(level);
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

   private void siren(ServerLevel level) {
      BlockPos t = tankPos != null ? tankPos : bossPos();
      level.playSound(null, t.getX(), t.getY() + 14, t.getZ(), Sounds.DG_ALARM.get(), SoundSource.AMBIENT, 3F, 1F);
   }

   /** Le laboratoire ronronne : la cuve bouillonne, les néons grésillent, un compteur Geiger crépite. */
   private void ambience(ServerLevel level) {
      if (nearbyPlayers(level, 90).isEmpty() || tankPos == null) return;
      Vec3 c = Vec3.atBottomCenterOf(tankPos);
      if (tankState != TANK_BROKEN) {
         level.sendParticles(ModParticles.TOXIC_SMOKE.get(), c.x, c.y + 4, c.z, 6, 1.6, 3, 1.6, 0.02);
         if (level.random.nextInt(3) == 0) level.playSound(null, c.x, c.y + 3, c.z, Sounds.DG_VAT.get(), SoundSource.AMBIENT, 2F, 0.7F);
      }
      if (level.random.nextInt(4) == 0) level.playSound(null, c.x, c.y + 3, c.z, Sounds.DG_VAT.get(), SoundSource.AMBIENT, 2F, 0.8F);
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

   public PlagueBearerServeur findBoss(ServerLevel level) {
      if (boss == null) return null;
      Entity e = level.getEntity(boss);
      return e instanceof PlagueBearerServeur k && k.isAlive() ? k : null;
   }

   /** Il flotte dans la cuve (on l'adopte s'il existe déjà, sinon on le crée, endormi). */
   private void ensureSleeper(ServerLevel level) {
      BlockPos bp = bossPos();
      if (!level.areEntitiesLoaded(ChunkPos.asLong(bp))) return;
      PlagueBearerServeur k = findBoss(level);
      if (k == null) {
         List<PlagueBearerServeur> near = level.getEntitiesOfClass(PlagueBearerServeur.class, new AABB(bp).inflate(40), Entity::isAlive);
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
      if (!(HeraliszPlague.PLAGUE_BEARER.get().create(level) instanceof PlagueBearerServeur e)) return;   // créé par l'usine
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
   /** Un joueur remonte le volant sur la vanne de purge. */
   @Override
   public boolean mountWheel(ServerLevel level, Player p, BlockPos terminal) {
      if (state != READY) return false;
      ensureSleeper(level);
      PlagueBearerServeur k = findBoss(level);
      if (k == null || !k.dormant()) return false;
      startCountdown(level, 0, terminal);
      return true;
   }

   private BlockPos terminal;

   private void startCountdown(ServerLevel level, int skip, BlockPos at) {
      participants.clear();
      terminal = at != null ? at : prop(level, HeroKind.PURGE_VALVE);
      setState(COUNTDOWN, level.getGameTime() - skip);
      locateTank(level);
      if (terminal == null) return;
      setStage(level, terminal, 1);
      level.playSound(null, terminal, Sounds.DG_VALVE_MOUNT.get(), SoundSource.BLOCKS, 3F, 1F);
      later(level, 24, () -> level.playSound(null, terminal, Sounds.DG_VALVE_TURN.get(), SoundSource.BLOCKS, 3F, 1F));
      PlagueBearerServeur k = findBoss(level);
      if (skip == 0 && k != null && PlagueConfig.CINEMATICS.get())
         Vfx.cinematic(level, Vec3.atBottomCenterOf(terminal), k.getId(), PlagueBearer.OFFER_K, OFFER_TICKS, toDoor(level));
   }

   /** Du terminal vers la porte blindée. */
   private Vec3 toDoor(ServerLevel level) {
      BlockPos g = blastDoor(level);
      if (g == null || terminal == null) return inward();
      Vec3 d = new Vec3(g.getX() - terminal.getX(), 0, g.getZ() - terminal.getZ());
      return d.lengthSqr() < 1e-3 ? inward() : d.normalize();
   }

   private void countdown(ServerLevel level, int t) {
      if (t == 50) {
         alarm(level, true);
         if (terminal != null) setStage(level, terminal, 2);
      }
      if (t >= 50 && t % 40 == 10) siren(level);
      if (t == 90) chargeTank(level);
      if (tankPos != null && t > 90 && t % 7 == 0) {
         Vec3 c = Vec3.atBottomCenterOf(tankPos);
         level.playSound(null, c.x, c.y + 6, c.z, Sounds.DG_VENT.get(), SoundSource.BLOCKS, 3F, 0.7F + level.random.nextFloat() * 0.5F);
         if (t % 21 == 0) level.playSound(null, c.x, c.y + 6, c.z, Sounds.FX_HISS.get(), SoundSource.BLOCKS, 3F, 0.6F + t * 0.002F);
      }
      if (tankPos != null && (t == 150 || t == 240)) {
         Vec3 c = Vec3.atBottomCenterOf(tankPos);
         level.playSound(null, c.x, c.y + 5, c.z, Sounds.PB_BREATH.get(), SoundSource.HOSTILE, 4F, 0.6F);
      }
      int left = (COUNTDOWN_TICKS - t + 19) / 20;
      if (t % 20 == 0) {
         for (Player p : nearbyPlayers(level, 80)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.heralisz_plague.countdown", left)
               .withStyle(net.minecraft.ChatFormatting.GREEN), true);
         }
      }
      if (t < COUNTDOWN_TICKS) return;
      participants.clear();
      for (Player p : nearbyPlayers(level, 80)) if (!p.isSpectator()) participants.add(p.getUUID());
      if (participants.isEmpty()) {
         restoreRoom(level);
         setState(READY, level.getGameTime());
         return;
      }
      setState(ENTRY, level.getGameTime());
      PlagueBearerServeur k = findBoss(level);
      BlockPos g = blastDoor(level);
      if (k != null && PlagueConfig.CINEMATICS.get())
         Vfx.cinematic(level, g != null ? Vec3.atBottomCenterOf(g) : Vec3.atBottomCenterOf(bossPos()), k.getId(), PlagueBearer.ENTRY_K, ENTRY_TICKS, inward());
   }

   private List<Player> nearbyPlayers(ServerLevel level, double r) {
      return new ArrayList<>(level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(r, 60, r), pl -> !pl.isSpectator()));
   }

   /** L'entrée : l'équipe devant la porte blindée ; elle roule dans le mur ; ils traversent le sas dans la brume de
    *  décontamination et débouchent dans la salle ; la porte claque derrière eux ; ils découvrent la cuve. */
   private void entry(ServerLevel level, int t) {
      BlockPos g = blastDoor(level);
      Vec3 in = inward();
      Vec3 across = new Vec3(-in.z, 0, in.x);
      Vec3 door = g != null ? Vec3.atBottomCenterOf(g) : Vec3.atBottomCenterOf(bossPos()).add(in.scale(-40));
      int cnt = 0;
      for (UUID id : participants) if (level.getPlayerByUUID(id) != null) cnt++;
      if (t == OPEN_T) openDoor(level);
      if (t == WALK0) setStages(level, HeroKind.DECON_ARCH, 1);
      if (t == WALK0 + 10 || t == WALK0 + 50) {
         for (BlockPos p : props(level).get(HeroKind.DECON_ARCH)) {
            level.playSound(null, p, Sounds.DG_STEAM.get(), SoundSource.BLOCKS, 3F, 1.1F);
            level.sendParticles(ModParticles.STEAM.get(), p.getX() + 0.5, p.getY() + 2, p.getZ() + 0.5, 40, 1.8, 1.5, 0.6, 0.02);
         }
      }
      if (t == WALK0 + WALK_T) setStages(level, HeroKind.DECON_ARCH, 0);
      if (t == SEAL_T) closeDoor(level);
      int i = 0;
      for (UUID id : participants) {
         if (!(level.getPlayerByUUID(id) instanceof ServerPlayer p) || p.isSpectator()) continue;
         double lane = (i % 3 - 1) * 1.6;
         int row = i / 3;
         Vec3 start = door.add(in.scale(-3.5 - row * 1.8)).add(across.scale(lane));
         Vec3 end = door.add(in.scale(19 + row * 1.8)).add(across.scale(lane * 1.6));
         float base = (float) (Mth.atan2(in.z, in.x) * Mth.RAD_TO_DEG) - 90F;
         Vec3 want;
         float pitch;
         if (t < WALK0 + row * 6) {
            want = start;
            pitch = 0F;
         } else {
            double u = Mth.clamp((t - WALK0 - row * 6) / (double) WALK_T, 0, 1);
            want = start.lerp(end, u * u * (3 - 2 * u));
            pitch = t > SEAL_T + 10 ? -8F : 4F;
         }
         double y = fr.heralisz.plague.entity.Ground.y(level, want.x, want.y + 2, want.z);
         p.teleportTo(want.x, y, want.z);
         p.setDeltaMovement(Vec3.ZERO);
         p.fallDistance = 0;
         p.hurtMarked = true;
         if (t % 2 == 0)
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), base, pitch,
               java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X, net.minecraft.world.entity.RelativeMovement.Y,
                  net.minecraft.world.entity.RelativeMovement.Z));
         i++;
      }
      if (t >= ENTRY_TICKS) {
         PlagueBearerServeur k = findBoss(level);
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
      alarm(level, false);
      openDoor(level);
      long now = level.getGameTime();
      nextReady = now + Math.max(PlagueConfig.RESPAWN_MINUTES.get() * 1200L, PlagueBearer.REMAINS + 1200L);
      restoreAt = now + PlagueBearer.REMAINS;
      boss = null;
      setState(COOLDOWN, now);
   }

   /** Plus personne dans la salle : on le rendort (le laboratoire le remet en cuve). */
   public void bossLeft(ServerLevel level) {
      PlagueBearerServeur k = findBoss(level);
      if (k != null) k.sleep();
      openDoor(level);
      restoreRoom(level);
      setState(READY, level.getGameTime());
   }

   private void watch(ServerLevel level, long now) {
      PlagueBearerServeur k = findBoss(level);
      if (k == null) {
         List<PlagueBearer> dying = level.getEntitiesOfClass(PlagueBearer.class, new AABB(bossPos()).inflate(80), PlagueBearer::isDeadOrDying);
         if (!dying.isEmpty()) return;
         openDoor(level);
         restoreRoom(level);
         setState(READY, now);
         return;
      }
      if (k.isDeadOrDying()) return;
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(60, 60, 60), pl -> !pl.isSpectator() && !pl.isCreative()).isEmpty();
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
      PlagueBearerServeur k = findBoss(level);
      if (k == null || !k.dormant()) return false;
      startCountdown(level, COUNTDOWN_TICKS - 40, null);
      return true;
   }

   public void reset(ServerLevel level) {
      PlagueBearerServeur k = findBoss(level);
      if (k != null) k.sleep();
      nextReady = 0;
      restoreRoom(level);
      props = null;
      doorCells = null;
      setState(READY, level.getGameTime());
   }

   // ------------------------------------------------------------------ sauvegarde / synchro
   private void writeCoils(CompoundTag tag) {
      ListTag l = new ListTag();
      for (BlockPos p : coils) l.add(NbtUtils.writeBlockPos(p));
      tag.put("Coils", l);
   }

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
      if (tankPos != null) tag.put("TankPos", NbtUtils.writeBlockPos(tankPos));
      writeCoils(tag);
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
      if (tag.contains("TankPos")) tankPos = NbtUtils.readBlockPos(tag.getCompound("TankPos"));
      readCoils(tag);
      if (tag.contains("TankState")) {
         tankState = tag.getInt("TankState");
         tankTime = tag.getLong("TankTime");
      }
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
      if (bossPos != null) tag.put("BossPos", NbtUtils.writeBlockPos(bossPos));
      if (tankPos != null) tag.put("TankPos", NbtUtils.writeBlockPos(tankPos));
      writeCoils(tag);
      tag.putInt("TankState", tankState);
      tag.putLong("TankTime", tankTime);
      return tag;
   }
}
