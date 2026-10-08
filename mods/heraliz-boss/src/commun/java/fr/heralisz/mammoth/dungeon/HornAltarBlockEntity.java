package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * L'autel du Cor, cœur de l'arène : garde Ymirak endormi dans sa glace, déroule le rituel quand un joueur y pose le cor de
 * guerre (les totems d'os s'allument de proche en proche, puis le glacier cède), déclenche l'alerte (braseros bleus, mur de
 * glace fermé), surveille le combat, remet la salle en état après sa mort et relance le délai.
 * États : PRÊT -> RITUEL (4 s) -> ÉVEILLÉ -> REPOS -> PRÊT. Plus personne pendant une minute : il se rendort.
 */
public class HornAltarBlockEntity extends BlockEntity implements GeoBlockEntity {
   public static final int READY = 0, RITUAL = 1, AWAKE = 2, COOLDOWN = 3, GUARDIANS = 4;
   public static final int RITUAL_TICKS = 80, LIGHTS_FROM = 20, LIGHTS_TO = 72;
   static final Set<HornAltarBlockEntity> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   int state;
   long stateTime, nextReady;
   private long clientRitual = Long.MIN_VALUE;

   public HornAltarBlockEntity(BlockPos pos, BlockState st) {
      super(ModBlocks.ALTAR_BE.get(), pos, st);
   }

   /** L'autel le plus proche (même monde) dans un rayon donné, ou null (ambiance, commandes). */
   public static HornAltarBlockEntity nearest(Level level, BlockPos p, double r) {
      HornAltarBlockEntity best = null;
      double bd = r * r;
      synchronized (LOADED) {
         for (HornAltarBlockEntity s : LOADED) {
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

   public long stateTime() {
      return stateTime;
   }

   public Direction facing() {
      BlockState s = getBlockState();
      return s.hasProperty(HornAltarBlock.FACING) ? s.getValue(HornAltarBlock.FACING) : Direction.NORTH;
   }

   public Vec3 center() {
      return Vec3.atBottomCenterOf(worldPosition);
   }

   // ------------------------------------------------------------------ serveur
   public static void serverTick(Level lvl, BlockPos pos, BlockState st, HornAltarBlockEntity be) {
      be.tick((ServerLevel) lvl);
   }

   /** Logique de l'autel (rituel, Gardiens, combat, remise en état) : dans HornAltarBlockEntityServeur ; neutre ici. */
   void tick(ServerLevel level) {
   }

   /** Clic droit avec le cor : dans HornAltarBlockEntityServeur ; neutre ici (jar sans partie serveur). */
   public void use(ServerPlayer p) {
   }

   /** Côté client : seul l'état synchronisé (getUpdateTag de la version serveur) ; la sauvegarde complète est lue côté serveur. */
   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      state = tag.getInt("State");
      stateTime = tag.getLong("StateTime");
      nextReady = tag.getLong("NextReady");
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

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(4);
   }

   // ------------------------------------------------------------------ GeckoLib
   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      RawAnimation idle = RawAnimation.begin().thenLoop("animation.shrine_mammoth.idle");
      RawAnimation activate = RawAnimation.begin().thenPlayAndHold("animation.shrine_mammoth.activate");
      RawAnimation open = RawAnimation.begin().thenLoop("animation.shrine_mammoth.open");
      reg.add(new AnimationController<>(this, "main", 3, s -> {
         if (state == RITUAL) {
            if (clientRitual != stateTime) {
               clientRitual = stateTime;
               s.getController().forceAnimationReset();
            }
            return s.setAndContinue(activate);
         }
         if (state == AWAKE || state == COOLDOWN) return s.setAndContinue(open);
         return s.setAndContinue(idle);
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
