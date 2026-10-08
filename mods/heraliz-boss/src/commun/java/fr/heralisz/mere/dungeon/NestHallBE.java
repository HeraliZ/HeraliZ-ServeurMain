package fr.heralisz.mere.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Le cœur du Nid (sous l'autel) : il garde la Mère endormie dans son cocon, mène le rituel (l'œuf déposé sur l'autel, le décompte,
 * l'entrée de l'équipe, la toile qui scelle la galerie, les sacs d'œufs qui s'allument autour de l'arène, le cocon qui bat), le
 * combat (rayons pâles de la voûte, grappes qui crèvent en phase 3), la mort (le nid s'éteint), et remet le nid en état.
 */
public class NestHallBE extends BlockEntity {
   public static final int READY = 0, COUNTDOWN = 1, ENTRY = 2, AWAKE = 3, COOLDOWN = 4;
   public static final int COUNTDOWN_TICKS = 420, ENTRY_TICKS = 300, TEAR_T = 30, SEAL_T = 200, SACS_FROM = 140, SACS_TO = 230;
   static final Set<NestHallBE> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

   int state;
   long stateTime;

   public NestHallBE(BlockPos pos, BlockState st) {
      super(ModBlocks.NEST_BE.get(), pos, st);
   }

   public static NestHallBE nearest(Level level, BlockPos p, double r) {
      NestHallBE best = null;
      double bd = r * r;
      synchronized (LOADED) {
         for (NestHallBE s : LOADED) {
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

   // ------------------------------------------------------------------ serveur
   public static void serverTick(Level lvl, BlockPos pos, BlockState st, NestHallBE be) {
      be.tick((ServerLevel) lvl);
   }

   /** Logique du nid (rituel, combat, remise en état) : dans NestHallBEServeur ; neutre ici (jar sans partie serveur). */
   void tick(ServerLevel level) {
   }

   // ------------------------------------------------------------------ le rituel
   /** Un joueur dépose l'Œuf de la Couvée sur l'autel. */
   public boolean offerEgg(ServerLevel level, Player p) {
      return offerEgg(level, p, null);
   }

   /** L'offrande (clic droit sur l'autel ou l'idole avec l'Œuf) : dans NestHallBEServeur ; neutre ici (jar sans partie serveur). */
   public boolean offerEgg(ServerLevel level, Player p, Vec3 idolTop) {
      return false;
   }

   public static final int OFFER_TICKS = 240;

   /** Côté client : seul l'état synchronisé (getUpdateTag de la version serveur) ; la sauvegarde complète est lue côté serveur. */
   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      state = tag.getInt("State");
      stateTime = tag.getLong("StateTime");
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

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
