package fr.heralisz.plague.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * La Station de Quarantaine (bloc caché sous la salle des vannes) : elle garde le Porteur de Peste agenouillé dans son bassin, mène le
 * rituel (le volant remonté sur la vanne de purge, l'alerte, les bouches de gaz qui sifflent, le décompte, le rideau métallique qui
 * se lève, la traversée du sas de décontamination et le rideau qui retombe derrière l'équipe), et remet tout en état.
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.2) : l'état synchronisé (état, cuve, bobines, repère du boss) lu par
 * l'ambiance et la musique côté client. Le rituel, le combat, la remise en état et la sauvegarde sont dans StationHallBEServeur.
 */
public class StationHallBE extends BlockEntity {
   public static final int READY = 0, COUNTDOWN = 1, ENTRY = 2, AWAKE = 3, COOLDOWN = 4;
   public static final int COUNTDOWN_TICKS = 320, OFFER_TICKS = 200, ENTRY_TICKS = 300, OPEN_T = 20, WALK0 = 118, WALK_T = 104, SEAL_T = 236;
   /** Cuve : intacte, en charge (les bobines crachent), brisée. */
   public static final int TANK_IDLE = 0, TANK_CHARGE = 1, TANK_BROKEN = 2;
   static final Set<StationHallBE> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

   int state, tankState;
   long stateTime, tankTime;
   BlockPos bossPos, tankPos;
   final List<BlockPos> coils = new ArrayList<>();

   public StationHallBE(BlockPos pos, BlockState st) {
      super(ModBlocks.STATION_BE.get(), pos, st);
   }

   public static StationHallBE nearest(Level level, BlockPos p, double r) {
      StationHallBE best = null;
      double bd = r * r;
      synchronized (LOADED) {
         for (StationHallBE s : LOADED) {
            if (s.level != level || s.isRemoved()) continue;
            double d = s.worldPosition.distSqr(p);
            if (d < bd) {
               bd = d;
               best = s;
            }
         }
      }
      return best;
   }

   public int state() {
      return state;
   }

   public int tankState() {
      return tankState;
   }

   public long tankTime() {
      return tankTime;
   }

   public BlockPos tankPos() {
      return tankPos;
   }

   public List<BlockPos> coils() {
      return coils;
   }

   public BlockPos bossPos() {
      return bossPos != null ? bossPos : worldPosition.above(3);
   }

   // ------------------------------------------------------------------ serveur
   public static void serverTick(Level lvl, BlockPos pos, BlockState st, StationHallBE be) {
      be.tick((ServerLevel) lvl);
   }

   /** Logique de la salle (rituel, combat, remise en état) : dans StationHallBEServeur ; neutre ici (jar sans partie serveur). */
   void tick(ServerLevel level) {
   }

   // ------------------------------------------------------------------ le rituel
   /** Un joueur remonte le volant sur la vanne de purge (appelé par HeroPropBlock.use) : dans StationHallBEServeur ; neutre ici. */
   public boolean mountWheel(ServerLevel level, Player p, BlockPos terminal) {
      return false;
   }

   void readCoils(CompoundTag tag) {
      if (!tag.contains("Coils")) return;
      coils.clear();
      for (Tag t : tag.getList("Coils", 10)) coils.add(NbtUtils.readBlockPos((CompoundTag) t));
   }

   /** Côté client : seul l'état synchronisé (getUpdateTag de la version serveur) ; la sauvegarde complète est lue côté serveur. */
   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      state = tag.getInt("State");
      stateTime = tag.getLong("StateTime");
      if (tag.contains("BossPos")) bossPos = NbtUtils.readBlockPos(tag.getCompound("BossPos"));
      if (tag.contains("TankPos")) tankPos = NbtUtils.readBlockPos(tag.getCompound("TankPos"));
      readCoils(tag);
      if (tag.contains("TankState")) {
         tankState = tag.getInt("TankState");
         tankTime = tag.getLong("TankTime");
      }
   }

   @Override
   public void onLoad() {
      super.onLoad();
      LOADED.add(this);
   }

   @Override
   public void onChunkUnloaded() {
      super.onChunkUnloaded();
      LOADED.remove(this);
   }

   @Override
   public void setRemoved() {
      super.setRemoved();
      LOADED.remove(this);
   }

   /** La cuve est rendue loin du bloc (toute la salle). */
   @Override
   public net.minecraft.world.phys.AABB getRenderBoundingBox() {
      return INFINITE_EXTENT_AABB;
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
