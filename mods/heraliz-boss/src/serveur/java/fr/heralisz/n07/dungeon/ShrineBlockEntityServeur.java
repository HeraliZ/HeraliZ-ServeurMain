package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.BossConfig;
import fr.heralisz.n07.entity.SujetN07Serveur;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
import java.util.UUID;

/**
 * Partie SERVEUR de {@link ShrineBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new ShrineBlockEntity(...) ».
 */
public class ShrineBlockEntityServeur extends ShrineBlockEntity {
   /** Le sanctuaire le plus proche, version serveur (code serveur : SujetN07Serveur...), ou null. */
   public static ShrineBlockEntityServeur nearestServeur(Level level, BlockPos p, double r) {
      return nearest(level, p, r) instanceof ShrineBlockEntityServeur s ? s : null;
   }

   public ShrineBlockEntityServeur(BlockPos pos, BlockState st) {
      super(pos, st);
   }

   /** Ticks sans joueur autour avant que le boss éveillé ne se rendorme. */
   private static final int LONELY_LIMIT = 1200;
   /** Portée de recherche des blocs rituels autour du sanctuaire. */
   private static final int LAMP_R = 48, LAMP_DOWN = 10, LAMP_UP = 28;
   private UUID boss, player;
   private BlockPos bossPos;
   private float bossYaw = Float.NaN;
   // non sauvegardé
   private int loaded, lonely, litUpTo;
   private List<BlockPos> lamps;
   /** Blocs du donjon modifiés pendant le combat (poutres tombées, cuve brisée...) : état d'origine, remis à la fin. */
   private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
   /** Alerte (combat en cours) : plafonniers et néons coupés, sirènes rouges, grille de l'arène fermée. */
   private boolean alert;
   private List<BlockPos> alertables;
   private long restoreAt = -1;

   @Override
   public boolean alert() {
      return alert;
   }

   public void setManaged(boolean on) {
      managed = on;
      sync();
   }

   /** Salle pilotee : le terminal s'active (ecran, bobines de la cuve qui accelerent, arcs, sons du rituel), sans joueur. */
   public void managedRitual(ServerLevel level) {
      if (!managed) return;
      walk = 0;
      player = null;
      litUpTo = 0;
      lamps = null;
      setState(RITUAL, level.getGameTime());
   }

   /** Salle pilotee : combat en cours (cuve brisee) ; alarm = sirenes + grille fermee. */
   public void managedAwake(ServerLevel level, boolean alarm) {
      if (!managed) return;
      setState(AWAKE, level.getGameTime());
      setAlert(level, alarm);
   }

   /** Salle pilotee : alerte seule (sirenes + grille), sans toucher aux lumieres. */
   public void managedAlert(ServerLevel level, boolean on) {
      if (managed) setAlert(level, on);
   }

   /** Salle pilotee : retour au repos (cuve pleine, terminal eteint, pas d'alerte, lampes rituelles eteintes). */
   public void managedReady(ServerLevel level) {
      if (!managed) return;
      player = null;
      changes.clear();
      restoreAt = -1;
      setLamps(level, false);
      setAlert(level, false);
      setState(READY, level.getGameTime());
      restored = level.getGameTime() - 100000;
      sync();
   }

   // ------------------------------------------------------------------ alerte : lumières coupées, sirènes, grille fermée
   private List<BlockPos> alertables(ServerLevel level) {
      if (alertables != null) return alertables;
      alertables = new ArrayList<>();
      int cx0 = (worldPosition.getX() - LAMP_R) >> 4, cx1 = (worldPosition.getX() + LAMP_R) >> 4;
      int cz0 = (worldPosition.getZ() - LAMP_R) >> 4, cz1 = (worldPosition.getZ() + LAMP_R) >> 4;
      int y0 = worldPosition.getY() - LAMP_DOWN, y1 = worldPosition.getY() + LAMP_UP;
      for (int cx = cx0; cx <= cx1; cx++) {
         for (int cz = cz0; cz <= cz1; cz++) {
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk ch = level.getChunk(cx, cz);
            LevelChunkSection[] secs = ch.getSections();
            for (int i = 0; i < secs.length; i++) {
               LevelChunkSection sec = secs[i];
               int sy = level.getSectionYFromSectionIndex(i) << 4;
               if (sec.hasOnlyAir() || sy + 15 < y0 || sy > y1) continue;
               if (!sec.getStates().maybeHas(ShrineBlockEntityServeur::alertable)) continue;
               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        if (alertable(sec.getBlockState(x, y, z))) alertables.add(new BlockPos((cx << 4) + x, sy + y, (cz << 4) + z));
                     }
                  }
               }
            }
         }
      }
      return alertables;
   }

   private static boolean alertable(BlockState st) {
      var b = st.getBlock();
      return b instanceof SirenBlock || b instanceof AlertLightBlock || b instanceof SwitchLightBlock || b instanceof ArenaGateBlock || b instanceof LampBlock lb && lb.kind.electric();
   }

   private void setAlert(ServerLevel level, boolean on) {
      if (alert == on) return;
      alert = on;
      setChanged();
      for (BlockPos p : alertables(level)) {
         if (!level.isLoaded(p)) continue;
         BlockState st = level.getBlockState(p);
         var b = st.getBlock();
         if (b instanceof SirenBlock) {
            if (st.getValue(SirenBlock.LIT) != on) level.setBlock(p, st.setValue(SirenBlock.LIT, on), 3);
         } else if (b instanceof AlertLightBlock) {
            if (st.getValue(AlertLightBlock.LIT) != on) level.setBlock(p, st.setValue(AlertLightBlock.LIT, on), 3);
         } else if (managed && (b instanceof SwitchLightBlock || b instanceof LampBlock)) {
            // salle pilotee : les lumieres ne bougent que sur ordre (N07Api), echelonnees
            continue;
         } else if (b instanceof SwitchLightBlock) {
            if (st.getValue(SwitchLightBlock.LIT) == on) level.setBlock(p, st.setValue(SwitchLightBlock.LIT, !on), 3);
         } else if (b instanceof LampBlock) {
            level.setBlock(p, st.setValue(LampBlock.POWER, on ? 0 : 2), 3);
         } else if (b instanceof ArenaGateBlock && level.getBlockEntity(p) instanceof ArenaGateBlockEntity g) {
            ArenaGates.setClosed(g, level, on);
         }
      }
      if (on) RitualFx.alert(this, level);
   }

   // ------------------------------------------------------------------ remise en état de la salle
   /** À appeler AVANT de modifier un bloc du donjon pendant le combat : il sera remis dans cet état après la mort du boss. */
   public void recordChange(BlockPos p, BlockState original) {
      changes.putIfAbsent(p.immutable(), original);
      setChanged();
   }

   /** Remet la salle à zéro : chaque bloc touché retrouve son état d'origine. */
   public void restoreRoom(ServerLevel level) {
      // personne ne reste coincé dans ce qui se reconstruit (cuve, poutres...) : on l'écarte
      if (!changes.isEmpty()) {
         Vec3 c = Vec3.atBottomCenterOf(bossPos());
         for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(bossPos()).inflate(48))) {
            boolean stuck = pl.position().distanceToSqr(c.x, pl.getY(), c.z) < (kind == DungeonKind.N07 ? 4.6 * 4.6 : 0);
            for (BlockPos p : changes.keySet()) {
               if (stuck) break;
               stuck = pl.getBoundingBox().intersects(new AABB(p));
            }
            if (!stuck) continue;
            Vec3 d = pl.position().subtract(c).multiply(1, 0, 1);
            d = d.lengthSqr() < 0.01 ? front() : d.normalize();
            Vec3 to = c.add(d.scale(kind == DungeonKind.N07 ? 6.0 : pl.position().distanceTo(c) + 2.5));
            pl.teleportTo(to.x, pl.getY(), to.z);
         }
      }
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) {
         if (level.isLoaded(e.getKey())) level.setBlock(e.getKey(), e.getValue(), 3);
      }
      changes.clear();
      restoreAt = -1;
      restored = level.getGameTime();
      sync();
   }

   /** Pieds du boss (marqueur « boss » de la structure, sinon derrière le sanctuaire). */
   @Override
   public BlockPos bossPos() {
      return bossPos != null ? bossPos : BlockPos.containing(center().subtract(front().scale(kind.bossDist)));
   }

   /** Le boss regarde vers l'avant du sanctuaire (vers le joueur) sauf indication contraire de la structure. */
   @Override
   public float bossYaw() {
      if (!Float.isNaN(bossYaw)) return bossYaw;
      Vec3 f = front();
      return (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90F;
   }

   /** Posé par la commande de donjon d'après le marqueur de la structure. */
   public void setBoss(BlockPos pos, float yaw) {
      this.bossPos = pos;
      this.bossYaw = yaw;
      setChanged();
   }

   @Override
   void tick(ServerLevel level) {
      loaded++;
      long now = level.getGameTime();
      if (managed) {
         managedTick(level, now);
         return;
      }
      // une alerte restée allumée hors combat (redémarrage...) s'éteint
      if (alert && state != AWAKE && now % 20 == 0) setAlert(level, false);
      switch (state) {
         case READY -> {
            if (kind.dormant && loaded > 60 && now % 20 == 0) ensureSleeper(level);
            if (now % 10 == 0) RitualFx.idle(this, level, now);
         }
         case RITUAL -> ritual(level, (int) (now - stateTime));
         case AWAKE -> {
            if (now % 20 == 0) watch(level, now);
            if (alert && now % 80 == 0) RitualFx.alarm(this, level);
         }
         case COOLDOWN -> {
            if (restoreAt >= 0 && now >= restoreAt) restoreRoom(level);
            if (now >= nextReady && now % 20 == 0) {
               if (restoreAt >= 0) restoreRoom(level);
               setLamps(level, false);
               setState(READY, now);
            }
         }
         default -> setState(READY, now);
      }
   }

   /** Salle pilotee : seulement les effets du rituel visuel et la sirene pendant l'alerte. */
   private void managedTick(ServerLevel level, long now) {
      if (state == RITUAL) {
         int s = (int) (now - stateTime) - walk;
         if (s >= 0 && s <= kind.end + 40) RitualFx.ritual(this, level, s);
         if (s >= kind.lights) lightStep(level, s - kind.lights);
      } else if (state == AWAKE && alert && now % 80 == 0) {
         RitualFx.alarm(this, level);
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

   private Mob findBoss(ServerLevel level) {
      if (boss == null) return null;
      Entity e = level.getEntity(boss);
      return e instanceof Mob m && m.isAlive() ? m : null;
   }

   /** Le boss endormi est à sa place (on l'adopte s'il existe déjà, sinon on le crée). */
   private void ensureSleeper(ServerLevel level) {
      BlockPos bp = bossPos();
      if (!level.areEntitiesLoaded(ChunkPos.asLong(bp))) return;
      Mob m = findBoss(level);
      if (m == null) {
         List<Mob> near = level.getEntitiesOfClass(Mob.class, new AABB(bp).inflate(8), e -> e.getType() == kind.bossType() && e.isAlive());
         if (!near.isEmpty()) {
            m = near.get(0);
            boss = m.getUUID();
            setChanged();
         }
      }
      if (m instanceof SujetN07Serveur hb) {
         if (!hb.dormant() && !hb.isDeadOrDying()) hb.sleep();
         return;
      }
      if (m != null) return;
      Entity e = kind.bossType().create(level);
      if (!(e instanceof SujetN07Serveur hb)) return;
      place(hb);
      hb.setDormant(true);
      hb.setHome(bp);
      level.addFreshEntity(hb);
      boss = hb.getUUID();
      setChanged();
   }

   private void place(Mob m) {
      BlockPos bp = bossPos();
      float yaw = bossYaw();
      m.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, yaw, 0);
      m.setYHeadRot(yaw);
      m.setYBodyRot(yaw);
   }

   /** Clic droit sur le sanctuaire. */
   @Override
   public void use(ServerPlayer p) {
      ServerLevel level = (ServerLevel) this.level;
      if (level == null) return;
      long now = level.getGameTime();
      if (managed) {
         // terminal d'une salle pilotee : la carte n'y fait rien (le jeu decide)
         deny(level, now);
         return;
      }
      boolean right = p.getMainHandItem().is(kind.item());
      if (!right) {
         tell(p, "need");
         return;
      }
      switch (state) {
         case RITUAL -> {
            tell(p, "busy");
            return;
         }
         case AWAKE -> {
            deny(level, now);
            tell(p, "awake");
            return;
         }
         case COOLDOWN -> {
            deny(level, now);
            long min = Math.max(1, (nextReady - now + 1199) / 1200);
            p.displayClientMessage(Component.translatable("shrine.heralisz_n07." + kind.id + ".cooldown", min), true);
            return;
         }
         default -> {
         }
      }
      if (kind.dormant && !(findBoss(level) instanceof SujetN07Serveur hb && hb.dormant())) {
         ensureSleeper(level);
         tell(p, "busy");
         return;
      }
      Vec3 stand = stand();
      double d = Math.sqrt(Mth.square(p.getX() - stand.x) + Mth.square(p.getZ() - stand.z));
      if (d > 7 || Math.abs(p.getY() - stand.y) > 2.5) {
         tell(p, "far");
         return;
      }
      walk = Mth.clamp((int) Math.ceil(d / 0.16), 8, 44);
      player = p.getUUID();
      litUpTo = 0;
      lamps = null;
      setState(RITUAL, now);
      Vfx.ritual(level, stand, center(), walk, kind.ordinal(), walk + kind.end, p.getId(), false);
   }

   private void tell(ServerPlayer p, String key) {
      p.displayClientMessage(Component.translatable("shrine.heralisz_n07." + kind.id + "." + key), true);
   }

   private void deny(ServerLevel level, long now) {
      denyTime = now;
      sync();
      RitualFx.deny(this, level);
   }

   private void ritual(ServerLevel level, int t) {
      int s = t - walk;
      ServerPlayer p = player == null ? null : level.getServer().getPlayerList().getPlayer(player);
      if (s < kind.insert && (p == null || !p.isAlive() || p.level() != level || p.position().distanceToSqr(stand()) > 100)) {
         abort(level, p);
         return;
      }
      if (s == kind.insert && !consume(p)) {
         abort(level, p);
         return;
      }
      if (s >= 0) RitualFx.ritual(this, level, s);
      if (s >= kind.lights) lightStep(level, s - kind.lights);
      if (s >= kind.end) awaken(level);
   }

   private boolean consume(ServerPlayer p) {
      if (p == null) return false;
      if (p.getAbilities().instabuild) return true;
      for (InteractionHand h : InteractionHand.values()) {
         ItemStack st = p.getItemInHand(h);
         if (st.is(kind.item())) {
            st.shrink(1);
            return true;
         }
      }
      for (ItemStack st : p.getInventory().items) {
         if (st.is(kind.item())) {
            st.shrink(1);
            return true;
         }
      }
      return false;
   }

   private void abort(ServerLevel level, ServerPlayer p) {
      if (p != null) {
         tell(p, "interrupted");
         Vfx.ritual(level, stand(), center(), 0, kind.ordinal(), 0, p.getId(), true);
      }
      player = null;
      setState(READY, level.getGameTime());
   }

   private void awaken(ServerLevel level) {
      long now = level.getGameTime();
      Mob m = findBoss(level);
      if (kind.dormant && m instanceof SujetN07Serveur hb && hb.dormant()) {
         hb.wake();
      } else {
         Entity e = kind.bossType().create(level);
         m = e instanceof Mob mob ? mob : null;
         if (m != null) {
            place(m);
            if (m instanceof SujetN07Serveur hb) hb.setHome(bossPos());
            level.addFreshEntity(m);
         }
      }
      boss = m == null ? null : m.getUUID();
      player = null;
      lonely = 0;
      setState(AWAKE, now);
      setAlert(level, true);
   }

   /** Pendant le combat : mort → délai de réapparition ; plus personne → il se rendort. */
   private void watch(ServerLevel level, long now) {
      Mob m = findBoss(level);
      if (m == null) {
         if (!level.areEntitiesLoaded(ChunkPos.asLong(bossPos()))) return;
         boss = null;
         nextReady = now + Math.max(BossConfig.RESPAWN_MINUTES.get() * 1200L, RESTORE_DELAY + 1200L);
         restoreAt = now + RESTORE_DELAY;
         setState(COOLDOWN, now);
         setAlert(level, false);
         return;
      }
      if (m.isDeadOrDying()) return;
      boolean anyone = !level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(56), pl -> !pl.isSpectator()).isEmpty();
      lonely = anyone ? 0 : lonely + 20;
      if (lonely < LONELY_LIMIT) return;
      if (kind.dormant && m instanceof SujetN07Serveur hb) {
         hb.sleep();
      } else {
         m.discard();
         boss = null;
      }
      setLamps(level, false);
      restoreRoom(level);
      setAlert(level, false);
      setState(READY, now);
   }

   /** Réveil immédiat par un admin (/heralisz sanctuaire eveil) : sans rituel ni objet. */
   public boolean forceAwaken(ServerLevel level) {
      if (state == AWAKE || state == RITUAL) return false;
      if (restoreAt >= 0) restoreRoom(level);
      if (kind.dormant) {
         loaded = Math.max(loaded, 61);
         ensureSleeper(level);
         if (!(findBoss(level) instanceof SujetN07Serveur hb && hb.dormant())) return false;
      }
      setLamps(level, true);
      awaken(level);
      return true;
   }

   /** Le boss de ce sanctuaire s'il est là (vivant). */
   public Mob boss(ServerLevel level) {
      return findBoss(level);
   }

   /** Remise à zéro par un admin (/heralisz sanctuaire reset). */
   public void reset(ServerLevel level) {
      Mob m = findBoss(level);
      if (m instanceof SujetN07Serveur hb && kind.dormant) hb.sleep();
      else if (m != null && !kind.dormant) {
         m.discard();
         boss = null;
      }
      nextReady = 0;
      player = null;
      setLamps(level, false);
      restoreRoom(level);
      setAlert(level, false);
      setState(READY, level.getGameTime());
   }

   // ------------------------------------------------------------------ blocs rituels (le donjon s'éveille)
   private List<BlockPos> lamps(ServerLevel level) {
      if (lamps != null) return lamps;
      lamps = new ArrayList<>();
      int cx0 = (worldPosition.getX() - LAMP_R) >> 4, cx1 = (worldPosition.getX() + LAMP_R) >> 4;
      int cz0 = (worldPosition.getZ() - LAMP_R) >> 4, cz1 = (worldPosition.getZ() + LAMP_R) >> 4;
      int y0 = worldPosition.getY() - LAMP_DOWN, y1 = worldPosition.getY() + LAMP_UP;
      for (int cx = cx0; cx <= cx1; cx++) {
         for (int cz = cz0; cz <= cz1; cz++) {
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk ch = level.getChunk(cx, cz);
            LevelChunkSection[] secs = ch.getSections();
            for (int i = 0; i < secs.length; i++) {
               LevelChunkSection sec = secs[i];
               int sy = level.getSectionYFromSectionIndex(i) << 4;
               if (sec.hasOnlyAir() || sy + 15 < y0 || sy > y1) continue;
               if (!sec.getStates().maybeHas(st -> st.getBlock() instanceof RitualLampBlock)) continue;
               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        if (sec.getBlockState(x, y, z).getBlock() instanceof RitualLampBlock) lamps.add(new BlockPos((cx << 4) + x, sy + y, (cz << 4) + z));
                     }
                  }
               }
            }
         }
      }
      lamps.sort(Comparator.comparingDouble(p -> p.distSqr(worldPosition)));
      return lamps;
   }

   private void lightStep(ServerLevel level, int u) {
      List<BlockPos> list = lamps(level);
      while (litUpTo < list.size()) {
         BlockPos p = list.get(litUpTo);
         if (Math.sqrt(p.distSqr(worldPosition)) * kind.lightSpeed > u) break;
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof RitualLampBlock && !st.getValue(RitualLampBlock.LIT)) {
            level.setBlock(p, st.setValue(RitualLampBlock.LIT, true), 3);
            RitualFx.lampLit(this, level, p, litUpTo);
         }
         litUpTo++;
      }
   }

   private void setLamps(ServerLevel level, boolean on) {
      for (BlockPos p : lamps(level)) {
         if (!level.isLoaded(p)) continue;
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof RitualLampBlock && st.getValue(RitualLampBlock.LIT) != on) level.setBlock(p, st.setValue(RitualLampBlock.LIT, on), 3);
      }
      litUpTo = 0;
   }

   // ------------------------------------------------------------------ sauvegarde / synchro
   @Override
   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      writeSynced(tag);
      if (boss != null) tag.putUUID("Boss", boss);
      if (player != null) tag.putUUID("Player", player);
      if (bossPos != null) tag.put("BossPos", NbtUtils.writeBlockPos(bossPos));
      if (!Float.isNaN(bossYaw)) tag.putFloat("BossYaw", bossYaw);
      tag.putLong("RestoreAt", restoreAt);
      tag.putBoolean("Alert", alert);
      net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
      for (Map.Entry<BlockPos, BlockState> e : changes.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.put("Pos", NbtUtils.writeBlockPos(e.getKey()));
         c.put("State", NbtUtils.writeBlockState(e.getValue()));
         list.add(c);
      }
      tag.put("Changes", list);
   }

   private void writeSynced(CompoundTag tag) {
      tag.putInt("State", state);
      tag.putLong("StateTime", stateTime);
      tag.putInt("Walk", walk);
      tag.putLong("DenyTime", denyTime);
      tag.putLong("NextReady", nextReady);
      tag.putLong("Restored", restored);
      tag.putBoolean("Managed", managed);
   }

   @Override
   public void load(CompoundTag tag) {
      // la version commune lit l'état synchronisé (State, StateTime, Walk, DenyTime, NextReady, Restored, Managed)
      super.load(tag);
      if (tag.hasUUID("Boss")) boss = tag.getUUID("Boss");
      if (tag.hasUUID("Player")) player = tag.getUUID("Player");
      if (tag.contains("BossPos")) bossPos = NbtUtils.readBlockPos(tag.getCompound("BossPos"));
      if (tag.contains("BossYaw")) bossYaw = tag.getFloat("BossYaw");
      if (tag.contains("RestoreAt")) restoreAt = tag.getLong("RestoreAt");
      alert = tag.getBoolean("Alert");
      if (tag.contains("Changes")) {
         changes.clear();
         var reg = level != null ? level.holderLookup(net.minecraft.core.registries.Registries.BLOCK)
            : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
         for (net.minecraft.nbt.Tag t : tag.getList("Changes", 10)) {
            CompoundTag c = (CompoundTag) t;
            changes.put(NbtUtils.readBlockPos(c.getCompound("Pos")), NbtUtils.readBlockState(reg, c.getCompound("State")));
         }
      }
      // un rituel coupé par un redémarrage ne reprend pas
      if (state == RITUAL && level != null && !level.isClientSide) state = READY;
   }

   @Override
   public void onLoad() {
      super.onLoad();   // version commune : inscription dans LOADED
      if (state == RITUAL && level != null && !level.isClientSide) state = READY;
   }

   @Override
   public CompoundTag getUpdateTag() {
      CompoundTag tag = new CompoundTag();
      writeSynced(tag);
      return tag;
   }
}
