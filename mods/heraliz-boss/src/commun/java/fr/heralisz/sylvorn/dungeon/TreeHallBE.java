package fr.heralisz.sylvorn.dungeon;

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
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * L'Arbre-Monde (bloc caché sous le sol du tronc) : il garde Sylvorn endormi dans l'écorce, mène le rituel (la graine ancestrale
 * déposée sur l'autel du Sanctuaire des Racines, la sève qui court jusqu'au mur de racines, le décompte, le mur qui s'ouvre sur le
 * tronc creux et se referme derrière l'équipe), fait battre le cœur vert tout là-haut au rythme du combat, et remet l'Arbre en état.
 * <p>
 * Classe COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.2) : état synchronisé et liste des salles chargées (nearest, lue par
 * l'ambiance et la musique du client). Rituel, entrée, combat, remise en état et sauvegarde : TreeHallBEServeur (usine
 * ModBlocks.TREE_USINE).
 */
public class TreeHallBE extends BlockEntity {
   public static final int READY = 0, COUNTDOWN = 1, ENTRY = 2, AWAKE = 3, COOLDOWN = 4;
   public static final int COUNTDOWN_TICKS = 420, OFFER_TICKS = 240, ENTRY_TICKS = 300, OPEN_T = 30, SEAL_T = 200;
   static final Set<TreeHallBE> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

   int state;
   long stateTime;
   BlockPos bossPos;

   public TreeHallBE(BlockPos pos, BlockState st) {
      super(ModBlocks.TREE_BE.get(), pos, st);
   }

   public static TreeHallBE nearest(Level level, BlockPos p, double r) {
      TreeHallBE best = null;
      double bd = r * r;
      synchronized (LOADED) {
         for (TreeHallBE s : LOADED) {
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

   public BlockPos bossPos() {
      return bossPos != null ? bossPos : worldPosition.above(3);
   }

   // ------------------------------------------------------------------ serveur
   public static void serverTick(Level lvl, BlockPos pos, BlockState st, TreeHallBE be) {
      be.tick((ServerLevel) lvl);
   }

   /** Logique de la salle (rituel, entrée, combat, remise en état) : dans TreeHallBEServeur ; neutre ici (jar sans partie serveur). */
   void tick(ServerLevel level) {
   }

   // ------------------------------------------------------------------ le rituel
   /** Un joueur dépose la graine ancestrale sur l'autel (HeroPropBlock.use) : dans TreeHallBEServeur ; neutre ici (refusée). */
   public boolean offerSeed(ServerLevel level, Player p, BlockPos altar) {
      return false;
   }

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
