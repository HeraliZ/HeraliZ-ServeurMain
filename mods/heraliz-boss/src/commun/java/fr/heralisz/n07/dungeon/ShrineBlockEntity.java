package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Cœur d'un donjon : garde le lien avec « son » boss (endormi sur place ou à invoquer), déroule le rituel quand un joueur
 * présente le bon objet, réveille le boss, surveille le combat, puis relance le délai de réapparition après sa mort.
 * <p>
 * États : PRÊT (le boss dort / attend) → RITUEL (le joueur s'avance, l'objet entre, le donjon s'éveille) → ÉVEILLÉ (combat) →
 * REPOS (délai après la mort) → PRÊT. Si plus personne n'est là pendant une minute, le boss se rendort (ou disparaît).
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.2) : état synchronisé (lu par le rendu du terminal, la cuve, les lampes,
 * l'ambiance), géométrie, animations. Rituel, réveil du boss, alerte, remise en état, sauvegarde : ShrineBlockEntityServeur
 * (créée par l'usine DungeonRegistry.SHRINE_USINE).
 */
public class ShrineBlockEntity extends BlockEntity implements GeoBlockEntity {
   public static final int READY = 0, RITUAL = 1, AWAKE = 2, COOLDOWN = 3;
   /** Délai entre la mort du boss et la remise en état de la salle (débris, décors cassés, cuve...). */
   public static final int RESTORE_DELAY = 600;
   /** Sanctuaires chargés (client et serveur) : les décors du donjon s'y rattachent (lampes, cuve...). */
   static final Set<ShrineBlockEntity> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public final DungeonKind kind;
   int state, walk;
   long stateTime, nextReady, denyTime = -1000;
   private long clientRitual = Long.MIN_VALUE;
   long restored = -100000;
   /**
    * Pilote par un autre mod (salle du mode Zombies, N07Api) : ni boss endormi automatique, ni rituel a la carte, ni minuteur,
    * ni remise en etat ; l'etat (rituel visuel, alerte, eveil) ne change que sur ordre. Ecrit dans la structure (« Managed »).
    */
   boolean managed;

   public ShrineBlockEntity(BlockPos pos, BlockState st) {
      super(DungeonRegistry.SHRINE_BE.get(), pos, st);
      this.kind = st.getBlock() instanceof ShrineBlock sb ? sb.kind : DungeonKind.N07;
   }

   /** Le sanctuaire le plus proche (même monde) dans un rayon donné, ou null. */
   public static ShrineBlockEntity nearest(Level level, BlockPos p, double r) {
      ShrineBlockEntity best = null;
      double bd = r * r;
      synchronized (LOADED) {
         for (ShrineBlockEntity s : LOADED) {
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

   /**
    * Perturbation électrique du donjon (0..1) : monte quand le sanctuaire accepte l'objet, reste au maximum pendant l'éveil du
    * boss puis retombe. Les lampes grésillent et clignotent d'autant plus.
    */
   public float surge(float partial) {
      if (level == null) return 0;
      float now = level.getGameTime() + partial;
      if (state == RITUAL) {
         float s = now - stateTime - walk - kind.grant;
         return Mth.clamp(s / 30F, 0, 1);
      }
      if (state == AWAKE) return Mth.clamp(1 - (now - stateTime - 140) / 60F, 0, 1);
      return 0;
   }

   /** Ticks depuis la dernière remise en état de la salle (côté client : animation de la cuve qui se reconstruit...). */
   public float sinceRestore(float partial) {
      return level == null ? 1e6F : level.getGameTime() + partial - restored;
   }

   public long nextReady() {
      return nextReady;
   }

   public boolean managed() {
      return managed;
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

   // ------------------------------------------------------------------ géométrie
   public Direction facing() {
      BlockState s = getBlockState();
      return s.hasProperty(ShrineBlock.FACING) ? s.getValue(ShrineBlock.FACING) : Direction.NORTH;
   }

   /** Vers l'avant du sanctuaire (là où se tient le joueur). */
   public Vec3 front() {
      Direction f = facing();
      return new Vec3(f.getStepX(), 0, f.getStepZ());
   }

   public Vec3 center() {
      return Vec3.atBottomCenterOf(worldPosition);
   }

   /** Où le joueur se tient pendant le rituel. */
   public Vec3 stand() {
      return center().add(front().scale(kind.stand));
   }

   /** Pieds du boss (derrière le sanctuaire) ; le marqueur « boss » de la structure n'est connu que du serveur (ShrineBlockEntityServeur). */
   public BlockPos bossPos() {
      return BlockPos.containing(center().subtract(front().scale(kind.bossDist)));
   }

   /** Le boss regarde vers l'avant du sanctuaire (vers le joueur) ; l'orientation de la structure est dans ShrineBlockEntityServeur. */
   public float bossYaw() {
      Vec3 f = front();
      return (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90F;
   }

   /** Alerte en cours (combat) : état serveur, non synchronisé (toujours faux côté client, comme à l'origine). */
   public boolean alert() {
      return false;
   }

   public int state() {
      return state;
   }

   public long stateTime() {
      return stateTime;
   }

   public int walk() {
      return walk;
   }

   public long denyTime() {
      return denyTime;
   }

   // ------------------------------------------------------------------ serveur
   public static void serverTick(Level lvl, BlockPos pos, BlockState st, ShrineBlockEntity be) {
      be.tick((ServerLevel) lvl);
   }

   /** Logique du sanctuaire (rituel, boss, alerte, remise en état) : dans ShrineBlockEntityServeur ; neutre ici. */
   void tick(ServerLevel level) {
   }

   /** Clic droit sur le sanctuaire (ShrineBlock.use, banc d'essai) : dans ShrineBlockEntityServeur ; neutre ici. */
   public void use(ServerPlayer p) {
   }

   /** Côté client : seul l'état synchronisé (getUpdateTag de la version serveur) ; la sauvegarde complète est lue côté serveur. */
   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      state = tag.getInt("State");
      stateTime = tag.getLong("StateTime");
      walk = tag.getInt("Walk");
      if (tag.contains("DenyTime")) denyTime = tag.getLong("DenyTime");
      if (tag.contains("NextReady")) nextReady = tag.getLong("NextReady");
      if (tag.contains("Restored")) restored = tag.getLong("Restored");
      managed = tag.getBoolean("Managed");
   }

   @Override
   public void onLoad() {
      super.onLoad();
      LOADED.add(this);
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(kind.renderRadius);
   }

   // ------------------------------------------------------------------ GeckoLib
   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      String base = "animation." + kind.model + ".";
      RawAnimation idle = RawAnimation.begin().thenLoop(base + "idle");
      RawAnimation activate = RawAnimation.begin().thenPlayAndHold(base + "activate");
      RawAnimation open = RawAnimation.begin().thenLoop(base + "open");
      reg.add(new AnimationController<>(this, "main", 3, s -> {
         long now = level == null ? 0 : level.getGameTime();
         if (state == RITUAL && now - stateTime >= walk) {
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

   /** Temps (ticks) écoulé depuis l'arrivée du joueur devant le sanctuaire, côté client (rendu). */
   public float ritualTime(float partial) {
      if (level == null || state != RITUAL) return -1;
      return level.getGameTime() + partial - stateTime - walk;
   }
}
