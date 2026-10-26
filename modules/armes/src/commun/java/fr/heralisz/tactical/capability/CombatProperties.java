package fr.heralisz.tactical.capability;

import fr.heraliz.tactique.pont.Crochet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import fr.heralisz.tactical.HeraliszTactical;
import fr.heralisz.tactical.api.item.ICustomItem;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.item.melee.CombatData;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.CMeleeAttackRequest;
import fr.heralisz.tactical.network.message.CPrepareMeleeAttack;
import fr.heralisz.tactical.network.message.SResetMeleeSyncMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

@AutoRegisterCapability
public class CombatProperties {
   public static final ResourceLocation ID = new ResourceLocation("heralisz_tactical", "combat_data");
   private final List<CombatProperties.DelayTask> delayedActions = new ArrayList<>();
   private ItemStack lastItem = ItemStack.EMPTY;
   private final Player entity;
   private int coolDownTick = 0;
   private int lastMaxTick = 0;
   private int lastSelected = 0;
   private int drawingTick = 0;
   private boolean preparingAttack = false;
   private int preparingAttackCnt = 0;
   // HeraliZ (serveur) : élan du coup en cours. Une demande de coup arrivée avant la fin de l'élan (jeu modifié qui
   // frappe sans attendre) attend jusqu'à cette fin ; une petite marge couvre les paquets regroupés par le réseau.
   private static final int MARGE_ELAN = 3;
   private int preparingTick = 0;
   private int preparingDelay = 0;
   private MeleeAction attenteAction;
   private int attenteCnt;
   private List<Entity> attenteCibles;
   private final Map<MeleeAction, Integer> actionCounts = new EnumMap<>(MeleeAction.class);

   /** Fusion (heraliz_tactique) : son de l'attaque joué localement par le client (code d'origine, lu dans l'affichage de l'arme),
    *  branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SonAttaque> SON_ATTAQUE_CLIENT = new Crochet<>();

   public record SonAttaque(Player joueur, ItemStack stack, MeleeAction action, Vec3 origine) {
   }

   public CombatProperties(Player entity) {
      this.entity = entity;
   }

   public int getCoolDownTick() {
      return this.coolDownTick;
   }

   public int getLastMaxTick() {
      return this.lastMaxTick;
   }

   public void setCoolDownTick(int coolDownTick) {
      this.coolDownTick = coolDownTick;
   }

   public void tick() {
      if (this.entity.getMainHandItem().getItem() instanceof ICustomItem customItem) {
         if (this.lastSelected != this.entity.getInventory().selected) {
            this.lastSelected = this.entity.getInventory().selected;
            this.reset(customItem, this.lastItem);
         } else if (!customItem.isSame(this.lastItem, this.entity.getMainHandItem())) {
            this.reset(customItem, this.lastItem);
         }
      } else if (this.lastItem.getItem() instanceof ICustomItem) {
         // HeraliZ (opti) : l'objet précédent n'est lu que s'il est un objet de mêlée / soin / lancer (temps de rangement,
         // isSame) ; tant qu'on tient autre chose (arme TaCZ...), plus de comparaison complète des NBT à chaque tick ni de
         // copie de l'arme à chaque tir : seule la sortie d'un objet ICustomItem est notée, comme avant
         this.lastItem = this.entity.getMainHandItem().copy();
      }

      if (this.coolDownTick > 0) {
         this.coolDownTick--;
         if (this.entity.getMainHandItem().getItem() instanceof IMeleeWeapon weapon && !weapon.canSprintingAttack()) {
            this.entity.setSprinting(false);
         }

         if (this.coolDownTick <= 0) {
            this.attenteCibles = null;
            if (!this.entity.level().isClientSide() && this.preparingAttack) {
               this.forceResetMeleeSync("melee attack request timed out");
            }

            this.preparingAttack = false;
         }
      }

      if (!this.entity.level().isClientSide() && this.attenteCibles != null && this.preparingAttack
         && this.entity.tickCount - this.preparingTick >= this.preparingDelay - MARGE_ELAN) {
         List<Entity> cibles = this.attenteCibles;
         this.attenteCibles = null;
         this.postAttack(this.attenteAction, this.attenteCnt, cibles);
      }

      if (this.entity.level().isClientSide()) {
         for (CombatProperties.DelayTask task : this.delayedActions) {
            if (task.tick()) {
               task.perform(this.entity);
            }
         }

         this.delayedActions.removeIf(CombatProperties.DelayTask::expired);
      }

      if (this.drawingTick > 0) {
         this.drawingTick--;
      }
   }

   public boolean isDrawing() {
      return this.drawingTick > 0;
   }

   public void reset(ICustomItem customItem, ItemStack last) {
      this.lastItem = this.entity.getMainHandItem().copy();
      int newCoolDown = customItem.getDrawTime(this.entity.getMainHandItem());
      if (last.getItem() instanceof ICustomItem customItem1) {
         newCoolDown += customItem1.getPutAwayTime(last);
      }

      this.coolDownTick = newCoolDown;
      this.lastMaxTick = newCoolDown;
      this.drawingTick = newCoolDown;
      this.preparingAttack = false;
      this.attenteCibles = null;
      this.delayedActions.clear();
      this.actionCounts.clear();
      this.preparingAttackCnt = 0;
   }

   public int getActionCount(MeleeAction meleeAction) {
      return this.actionCounts.getOrDefault(meleeAction, 0);
   }

   public void resetMeleeSync() {
      this.coolDownTick = 0;
      this.lastMaxTick = 0;
      this.preparingAttack = false;
      this.attenteCibles = null;
      this.actionCounts.clear();
      this.delayedActions.clear();
      this.preparingAttackCnt = 0;
   }

   public boolean preAttack(MeleeAction action, Vec3 origin, Vec3 direction) {
      ItemStack stack = this.entity.getMainHandItem();
      if (!(stack.getItem() instanceof IMeleeWeapon weapon)) {
         return false;
      } else {
         if (!this.entity.level().isClientSide()) {
            if (this.preparingAttack || this.coolDownTick > 0) {
               return false;
            }
         } else if (this.coolDownTick > 0) {
            return false;
         }

         if (!weapon.canAttack(this.entity, stack, action)) {
            return false;
         }

         int cnt = this.actionCounts.getOrDefault(action, 0);
         int nextCount = cnt + 1;
         this.actionCounts.put(action, nextCount);
         this.coolDownTick = weapon.getAttackCoolDown(stack, action, cnt);
         this.lastMaxTick = this.coolDownTick;
         if (!this.entity.level().isClientSide()) {
            this.preparingAttack = true;
            this.preparingAttackCnt = cnt;
            this.preparingTick = this.entity.tickCount;
            this.preparingDelay = Math.max(0, weapon.getAttackDelay(this.entity, stack, action, cnt));
            this.attenteCibles = null;
            this.coolDownTick = Math.max(0, this.coolDownTick - 1);
         } else {
            NetworkHandler.CHANNEL.sendToServer(new CPrepareMeleeAttack(action, origin, direction));
            int delay = weapon.getAttackDelay(this.entity, stack, action, cnt);
            CombatProperties.DelayAttack attack = new CombatProperties.DelayAttack(delay, stack, action, cnt);
            if (attack.getDelay() == 0) {
               attack.perform(this.entity);
            } else {
               this.delayedActions.add(attack);
            }

            CombatData.MeleeMovement moveInfo = weapon.getAttackMovement(this.entity, stack, action);
            if (moveInfo != null) {
               CombatProperties.DelayMove move = new CombatProperties.DelayMove(moveInfo.getDelay(), moveInfo.getSpeed(), stack);
               if (move.getDelay() == 0) {
                  move.perform(this.entity);
               } else {
                  this.delayedActions.add(move);
               }
            }

            SON_ATTAQUE_CLIENT.accept(new SonAttaque(this.entity, stack, action, origin));
         }

         return true;
      }
   }

   public void postAttack(MeleeAction action, int actionCount, List<Entity> entities) {
      if (this.preparingAttack) {
         if (this.preparingAttackCnt != actionCount) {
            this.forceResetMeleeSync("received a melee attack request with mismatched action count");
         } else if (!this.entity.level().isClientSide() && this.entity.tickCount - this.preparingTick < this.preparingDelay - MARGE_ELAN) {
            // HeraliZ : coup demandé avant la fin de l'élan : il part quand l'élan est fini (tick), pas avant
            this.attenteAction = action;
            this.attenteCnt = actionCount;
            this.attenteCibles = new ArrayList<>(entities);
         } else {
            ItemStack stack = this.entity.getMainHandItem();
            if (stack.getItem() instanceof IMeleeWeapon weapon) {
               weapon.attack(this.entity, stack, action, entities, this.preparingAttackCnt);
            }

            this.preparingAttack = false;
         }
      }
   }

   private void forceResetMeleeSync(String reason) {
      this.resetMeleeSync();
      if (!this.entity.level().isClientSide()) {
         HeraliszTactical.LOGGER.warn("Force resetting melee sync for player {}: {}", this.entity.getScoreboardName(), reason);
         NetworkHandler.sendToClientPlayer(new SResetMeleeSyncMessage(), this.entity);
      }
   }

   public static class DelayAttack extends CombatProperties.DelayTask {
      private final ItemStack stack;
      private final MeleeAction action;
      private final int actionCount;

      DelayAttack(int delay, ItemStack stack, MeleeAction action, int actionCount) {
         super(delay);
         this.action = action;
         this.actionCount = actionCount;
         this.stack = stack;
      }

      @Override
      public void perform(Player player) {
         if (this.stack.getItem() instanceof IMeleeWeapon weapon && weapon.isSame(this.stack, player.getMainHandItem())) {
            List<Entity> entities = weapon.collectTargets(player, this.stack, this.action, player.getEyePosition(), player.getLookAngle());
            NetworkHandler.CHANNEL.sendToServer(new CMeleeAttackRequest(this.action, this.actionCount, entities));
         }
      }
   }

   public static class DelayMove extends CombatProperties.DelayTask {
      private final ItemStack stack;
      private final double speed;

      DelayMove(int delay, double speed, ItemStack stack) {
         super(delay);
         this.stack = stack;
         this.speed = speed;
      }

      @Override
      public void perform(Player player) {
         if (this.stack.getItem() instanceof IMeleeWeapon weapon && weapon.isSame(this.stack, player.getMainHandItem())) {
            double factor = player.onGround() ? 1.0 : 0.5;
            Vec3 motion = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(factor * this.speed);
            player.addDeltaMovement(motion);
         }
      }
   }

   public abstract static class DelayTask {
      protected int delay;

      protected DelayTask(int delay) {
         this.delay = delay;
      }

      abstract void perform(Player var1);

      public boolean tick() {
         this.delay--;
         return this.delay <= 0;
      }

      public boolean expired() {
         return this.delay <= 0;
      }

      public int getDelay() {
         return this.delay;
      }
   }
}
