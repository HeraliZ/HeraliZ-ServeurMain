package fr.heralisz.n07.dungeon;

import fr.heraliz.boss.pont.Crochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
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
 * Décor animé : boucle d'animation GeckoLib, particules et son d'ambiance côté client. Côté serveur (PropBlockEntityServeur,
 * usine DungeonRegistry.PROP_USINE) : jet des buses piloté par les scènes, grésillement des luminaires électriques.
 */
public class PropBlockEntity extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public final PropKind kind;
   /** Déphasage propre à chaque décor (les buses ne pulsent pas toutes ensemble). */
   public final float phase;

   public PropBlockEntity(BlockPos pos, BlockState st) {
      super(DungeonRegistry.PROP_BE.get(), pos, st);
      this.kind = st.getBlock() instanceof PropBlock pb ? pb.kind : PropKind.WASTE_PIPE;
      this.phase = (float) Math.floorMod(pos.asLong() * 2654435761L, 1000) / 1000F;
   }

   public Direction facing() {
      BlockState s = getBlockState();
      return s.hasProperty(PropBlock.FACING) ? s.getValue(PropBlock.FACING) : Direction.NORTH;
   }

   public Vec3 front() {
      Direction f = facing();
      return new Vec3(f.getStepX(), 0, f.getStepZ());
   }

   // ------------------------------------------------------------------ buse : jet en marche / coupe (pilote par les scenes)
   /** La buse crache (vrai par defaut, comme dans le donjon d'origine). */
   boolean flow = true;
   /** Instant (temps du monde) du dernier changement, pour l'animation du jet qui s'allonge / se detache. */
   long flowTime = -100000;

   public boolean flowing() {
      return flow;
   }

   /** Longueur visible du jet (0..1) : il s'allonge en 14 ticks a la mise en marche, se detache en 10 a l'arret. */
   public float flowAmount(float partial) {
      float t = level == null ? 1e6F : level.getGameTime() + partial - flowTime;
      return flow ? Mth.clamp(t / 14F, 0, 1) : 1 - Mth.clamp(t / 10F, 0, 1);
   }

   @Override
   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putBoolean("Flow", flow);
      tag.putLong("FlowTime", flowTime);
   }

   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      flow = !tag.contains("Flow") || tag.getBoolean("Flow");
      if (tag.contains("FlowTime")) flowTime = tag.getLong("FlowTime");
   }

   @Override
   public CompoundTag getUpdateTag() {
      CompoundTag t = new CompoundTag();
      t.putBoolean("Flow", flow);
      t.putLong("FlowTime", flowTime);
      return t;
   }

   @Override
   public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
      return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
   }

   /** Effets côté client (PropFx.tick) : branché par la partie client, neutre sinon. */
   public static final Crochet<PropBlockEntity> TICK_CLIENT = new Crochet<>();

   public static void clientTick(Level level, BlockPos pos, BlockState st, PropBlockEntity be) {
      TICK_CLIENT.accept(be);
   }
   private long shrineCheck = -1000;
   private ShrineBlockEntity shrine;
   /** Dernière puissance vue par le client (étincelles et grésillement quand elle change). */
   public int clientPower = -1;
   /** Cuve (client) : angle des bobines (degrés), dernier instant, boss repéré, dernier état vu. */
   public float orbitAngle, orbitT = -1;
   public net.minecraft.world.entity.Entity clientBoss;
   public int clientTankState = -1;

   /** Sanctuaire du donjon auquel ce décor appartient (ou null). */
   public ShrineBlockEntity shrine() {
      if (level == null) return null;
      long now = level.getGameTime();
      if (now - shrineCheck > 100 || shrine != null && shrine.isRemoved()) {
         shrineCheck = now;
         shrine = ShrineBlockEntity.nearest(level, worldPosition, 72);
      }
      return shrine;
   }

   /** Ampoule défectueuse (environ un tiers) : grésille de temps en temps même au calme. */
   public boolean faulty() {
      return phase < 0.34F;
   }

   /** Ticker serveur des luminaires électriques (LampBlock) : la logique (grésillement, coupures, alerte) est dans PropBlockEntityServeur. */
   public static void serverTick(Level level, BlockPos pos, BlockState st, PropBlockEntity be) {
      be.tick(level, pos, st);
   }

   /** Neutre ici (jar sans partie serveur) ; PropBlockEntityServeur la redéfinit. */
   void tick(Level level, BlockPos pos, BlockState st) {
   }

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(kind.renderRadius);
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      RawAnimation idle = RawAnimation.begin().thenLoop(kind.loopAnimation());
      reg.add(new AnimationController<>(this, "idle", 0, s -> s.setAndContinue(idle)));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
