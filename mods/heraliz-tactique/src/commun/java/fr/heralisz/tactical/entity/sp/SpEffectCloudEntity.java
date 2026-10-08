package fr.heralisz.tactical.entity.sp;

import fr.heralisz.tactical.init.ModEntities;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class SpEffectCloudEntity extends AreaEffectCloud {
   public static EntityType<SpEffectCloudEntity> TYPE = Builder.<SpEffectCloudEntity>of(ModEntities.EFFECT_CLOUD_USINE, MobCategory.MISC)
      .fireImmune()
      .sized(6.0F, 0.5F)
      .noSave()
      .noSummon()
      .clientTrackingRange(10)
      .updateInterval(Integer.MAX_VALUE)
      .build("sp_effect_cloud");
   private boolean ignite = false;
   private int igniteTime = 2;
   private boolean extinguishBySmoke = false;

   // Fusion (heraliz_tactique) : mise à feu des entités dans le nuage et extinction par la fumée (tick) : serveur,
   // SpEffectCloudEntityServeur. Ici : le nuage lui-même (rayon, durée, particules : AreaEffectCloud).

   public SpEffectCloudEntity(EntityType<? extends AreaEffectCloud> type, Level level) {
      super(type, level);
   }

   public SpEffectCloudEntity(Level level, double x, double y, double z) {
      this(TYPE, level);
      this.setPos(x, y, z);
   }


   @NotNull
   public EntityDimensions getDimensions(@NotNull Pose pPose) {
      return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
   }

   public boolean isIgnite() {
      return this.ignite;
   }

   public void setIgnite(boolean ignite) {
      this.ignite = ignite;
   }

   public boolean isExtinguishBySmoke() {
      return this.extinguishBySmoke;
   }

   public void setExtinguishBySmoke(boolean extinguishBySmoke) {
      this.extinguishBySmoke = extinguishBySmoke;
   }

   public int getIgniteTime() {
      return this.igniteTime;
   }

   public void setIgniteTime(int igniteTime) {
      this.igniteTime = igniteTime;
   }
}
