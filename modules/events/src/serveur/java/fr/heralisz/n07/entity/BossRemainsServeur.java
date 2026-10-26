package fr.heralisz.n07.entity;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.particle.ModParticles;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link BossRemains} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new BossRemains(...) » côté serveur.
 */
public class BossRemainsServeur extends BossRemains {
   public BossRemainsServeur(EntityType<? extends BossRemains> type, Level level) {
      super(type, level);
   }

   private int age;

   /** variant : phase du boss au moment de sa mort (visage, masque...). */
   public static void spawn(ServerLevel level, Kind kind, Vec3 pos, float yaw, int variant) {
      spawn(level, kind, pos, yaw, variant, fr.heralisz.n07.BossConfig.REMAINS_MINUTES.get() * 1200);
   }

   /** Idem avec une duree de vie donnee (ticks) ; rend la depouille creee (ou null). */
   public static BossRemains spawn(ServerLevel level, Kind kind, Vec3 pos, float yaw, int variant, int lifeTicks) {
      if (!(HeraliszN07.BOSS_REMAINS.get().create(level) instanceof BossRemainsServeur r)) return null;
      r.entityData.set(KIND, kind.ordinal());
      r.entityData.set(VARIANT, variant);
      r.entityData.set(LIFE, lifeTicks);
      r.moveTo(pos.x, pos.y, pos.z, yaw, 0);
      r.setYRot(yaw);
      r.yRotO = yaw;
      level.addFreshEntity(r);
      return r;
   }

   /** Âge synchronisé (toutes les secondes, puis à chaque tick pendant l'effritement). */
   @Override
   public int age() {
      return this.level().isClientSide ? this.entityData.get(AGE) : age;
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         clientEffects();
         return;
      }
      ServerLevel level = (ServerLevel) this.level();
      age++;
      int life = life();
      if (age % 20 == 0 || age > life - CRUMBLE - 20) this.entityData.set(AGE, age);
      // statue de trinitite : le verre crépite en refroidissant, le compteur Geiger ne se tait jamais
      if (age % 50 == 0 && random.nextInt(2) == 0) level.playSound(null, getX(), getY() + 2, getZ(), fr.heralisz.n07.sound.BossSounds.N7_GEIGER.get(),
         SoundSource.BLOCKS, 0.9F, 1F);
      if (age % 90 == 0) level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1.2F, 0.5F + random.nextFloat() * 0.4F);
      if (age == life - CRUMBLE) level.playSound(null, getX(), getY() + 3, getZ(), fr.heralisz.n07.sound.BossSounds.N7_CRACK.get(), SoundSource.BLOCKS, 3F, 0.8F);
      if (age > life - CRUMBLE && age % 6 == 0) level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.4F,
         0.6F + random.nextFloat() * 0.4F);
      if (age >= life) {
         Vec3 c = this.position().add(0, 2, 0);
         Vfx.debris(level, c, 2F, 5F, 30, Blocks.LIME_STAINED_GLASS.defaultBlockState(), Vec3.ZERO);
         Vfx.debris(level, c, 1.8F, 4F, 20, Blocks.GREEN_STAINED_GLASS.defaultBlockState(), Vec3.ZERO);
         level.sendParticles(ModParticles.GLASS_SHARD.get(), c.x, c.y + 1, c.z, 60, 1.5, 1.5, 1.5, 0.2);
         level.sendParticles(ModParticles.TOXIC_SPARK.get(), c.x, c.y + 1, c.z, 30, 1.5, 1.5, 1.5, 0.1);
         level.playSound(null, c.x, c.y, c.z, fr.heralisz.n07.sound.BossSounds.N7_VAT_BURST.get(), SoundSource.BLOCKS, 3F, 1.2F);
         this.discard();
      }
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      this.entityData.set(KIND, tag.getInt("Kind"));
      this.entityData.set(VARIANT, tag.contains("Variant") ? tag.getInt("Variant") : 3);
      age = tag.getInt("Age");
      this.entityData.set(AGE, age);
      if (tag.contains("Life")) this.entityData.set(LIFE, tag.getInt("Life"));
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putInt("Kind", this.entityData.get(KIND));
      tag.putInt("Variant", variant());
      tag.putInt("Age", age);
      tag.putInt("Life", life());
   }
}
