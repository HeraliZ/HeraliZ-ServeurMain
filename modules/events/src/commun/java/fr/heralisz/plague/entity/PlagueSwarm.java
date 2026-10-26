package fr.heralisz.plague.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Essaim de mouches du Porteur de Peste : il fond sur sa cible (un peu moins vite qu'un joueur qui sprinte), la harcèle
 * (poison, nausée, petits dégâts) puis se disperse. Plonger dans l'eau le fait lâcher prise.
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.4) : cible et durée synchronisées, densité lue par le rendu. Comme dans le
 * mod d'origine, la traque (déplacement), les piqûres et la dispersion ne sont calculées que par le serveur (PlagueSwarmServeur) ;
 * le client suit la position envoyée par le serveur.
 */
public class PlagueSwarm extends Entity {
   static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(PlagueSwarm.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PlagueSwarm.class, EntityDataSerializers.INT);

   public PlagueSwarm(EntityType<? extends PlagueSwarm> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(TARGET, 0);
      this.entityData.define(LIFE, 200);
   }

   /** Id de l'entité traquée (rendu : les mouches s'agglutinent sur elle). */
   public int targetId() {
      return this.entityData.get(TARGET);
   }

   public int life() {
      return this.entityData.get(LIFE);
   }

   /** 0..1 : l'essaim se forme puis se disperse. */
   public float density(float partial) {
      float t = this.tickCount + partial;
      return Mth.clamp(Math.min(t / 10F, (life() - t) / 20F), 0F, 1F);
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 96 * 96;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }
}
