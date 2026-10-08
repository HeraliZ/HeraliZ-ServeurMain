package fr.heralisz.n07.block;

import fr.heralisz.n07.BossConfig;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.entity.SujetN07Serveur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link ContainmentVatBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5.2) : réveil du boss quand un joueur
 * approche, délai de réapparition, sauvegarde. Créée par l'usine du code commun dès que la partie serveur est dans le jar.
 */
public class ContainmentVatBlockEntityServeur extends ContainmentVatBlockEntity {
   public ContainmentVatBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }

   private UUID boss;
   private long nextSpawn;

   @Override
   void tick(Level lvl, BlockPos pos, BlockState state) {
      tickServeur(lvl, pos, state, this);
   }

   /** Corps d'origine de ContainmentVatBlockEntity.serverTick. */
   private static void tickServeur(Level lvl, BlockPos pos, BlockState state, ContainmentVatBlockEntityServeur be) {
      ServerLevel level = (ServerLevel) lvl;
      if (level.getGameTime() % 10 == 0) {
         level.sendParticles(ParticleTypes.SNEEZE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.01);
      }
      if (level.getGameTime() % 20 != 0) return;
      if (be.boss != null) {
         // vivant ou en train de mourir : on attend ; introuvable alors que la zone est chargée : il est mort
         if (level.getEntity(be.boss) != null || be.bossNearbyUnloaded(level)) return;
         be.boss = null;
         be.nextSpawn = level.getGameTime() + BossConfig.RESPAWN_MINUTES.get() * 1200L;
         be.setChanged();
         return;
      }
      if (level.getGameTime() < be.nextSpawn) return;
      int r = BossConfig.ACTIVATION_RADIUS.get();
      boolean player = !level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(r), p -> !p.isCreative() && !p.isSpectator()).isEmpty();
      if (!player) return;
      if (!(HeraliszN07.SUJET_N07.get().create(level) instanceof SujetN07Serveur s)) return;
      s.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
      s.setHome(pos);
      level.addFreshEntity(s);
      be.boss = s.getUUID();
      be.setChanged();
   }

   /** Le boss peut être dans un chunk voisin déchargé : on ne relance pas le délai à tort. */
   private boolean bossNearbyUnloaded(ServerLevel level) {
      int c = 3;
      for (int dx = -c; dx <= c; dx++) {
         for (int dz = -c; dz <= c; dz++) {
            if (!level.hasChunk((worldPosition.getX() >> 4) + dx, (worldPosition.getZ() >> 4) + dz)) return true;
         }
      }
      return false;
   }

   @Override
   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      if (boss != null) tag.putUUID("Boss", boss);
      tag.putLong("NextSpawn", nextSpawn);
   }

   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      boss = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
      nextSpawn = tag.getLong("NextSpawn");
   }
}
