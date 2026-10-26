package fr.heralisz.n07.decor;

import fr.heraliz.boss.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Décor animé : le mouvement (balancement, flammes, néons, voyants, rotation...) est calculé à chaque image par DecorRenderer à
 * partir de l'heure du monde et d'un déphasage propre à chaque bloc — deux lanternes voisines ne se balancent jamais ensemble.
 * Particules et sons côté client (DecorFx), rien côté serveur.
 */
public class DecorBlockEntity extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public final DecorKind kind;
   /** Déphasage propre au bloc (0..1). */
   public final float phase;

   public DecorBlockEntity(BlockPos pos, BlockState st) {
      super(DecorRegistry.BE.get(), pos, st);
      this.kind = st.getBlock() instanceof DecorBlock db ? db.kind : DecorKind.values()[0];
      this.phase = (float) Math.floorMod(pos.asLong() * 2654435761L, 1000) / 1000F;
   }

   public Direction facing() {
      BlockState s = getBlockState();
      return s.hasProperty(DecorBlock.FACING) ? s.getValue(DecorBlock.FACING) : Direction.NORTH;
   }

   public int drop() {
      BlockState s = getBlockState();
      return s.hasProperty(DecorBlock.DROP) ? s.getValue(DecorBlock.DROP) : 0;
   }

   public boolean onRail() {
      BlockState s = getBlockState();
      return s.hasProperty(DecorBlock.ON_RAIL) && s.getValue(DecorBlock.ON_RAIL);
   }

   /** Point du modèle (1/16 de bloc) -> monde. */
   public Vec3 toWorld(double x, double y, double z) {
      double[] r = DecorSpec.rotate(x / 16.0, z / 16.0, facing());
      return new Vec3(worldPosition.getX() + 0.5 + r[0], worldPosition.getY() + y / 16.0 - (onRail() ? 1 : 0), worldPosition.getZ() + 0.5 + r[1]);
   }

   /** Effets côté client (DecorFx.tick) : branché par la partie client, neutre sinon. */
   public static final Crochet<DecorBlockEntity> TICK_CLIENT = new Crochet<>();

   public static void clientTick(Level level, BlockPos pos, BlockState st, DecorBlockEntity be) {
      TICK_CLIENT.accept(be);
   }

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(kind.spec.radius).move(0, onRail() ? -1 : 0, 0);
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      RawAnimation idle = RawAnimation.begin().thenLoop("animation.decor_" + kind.spec.id + ".idle");
      reg.add(new AnimationController<>(this, "idle", 0, s -> s.setAndContinue(idle)));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
