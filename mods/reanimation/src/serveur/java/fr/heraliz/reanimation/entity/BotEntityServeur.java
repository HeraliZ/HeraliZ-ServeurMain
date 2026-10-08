package fr.heraliz.reanimation.entity;

import fr.heraliz.reanimation.core.Downed;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Partie SERVEUR de {@link BotEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new BotEntity(...) » côté serveur.
 */
public class BotEntityServeur extends BotEntity {
   public BotEntityServeur(EntityType<? extends BotEntity> type, Level level) {
      super(type, level);
   }

   final NonNullList<ItemStack> backpack = NonNullList.withSize(36, ItemStack.EMPTY);
   private final BotInventory inventory = new BotInventory(this);

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new BotGoals.Downed(this));
      this.goalSelector.addGoal(2, new BotGoals.Help(this));
      this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
      this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
   }

   public BotInventory inventory() {
      return this.inventory;
   }

   public void setup(int skin, BotEntity.Mode mode) {
      this.entityData.set(DATA_SKIN, Math.floorMod(skin, SKINS.length));
      this.entityData.set(DATA_MODE, mode.ordinal());
      String name = SKINS[this.skin()];
      Component label = Component.literal("Bot " + Character.toUpperCase(name.charAt(0)) + name.substring(1));
      if (mode != BotEntity.Mode.PASSIVE) {
         label = label.copy().append(" [").append(Component.translatable("entity.reanimation.bot.mode." + mode.id())).append("]");
      }

      this.setCustomName(label);
      this.setCustomNameVisible(true);
   }

   public void giveKit() {
      this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
      this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
      this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
      this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
      this.setItemSlot(
         EquipmentSlot.MAINHAND,
         new ItemStack(this.mode() == BotEntity.Mode.MEDIC ? Items.GOLDEN_APPLE : (this.mode() == BotEntity.Mode.EXECUTIONER ? Items.AIR : Items.IRON_AXE))
      );
      ItemStack[] loot = new ItemStack[]{
         new ItemStack(Items.BREAD, 6),
         new ItemStack(Items.COOKED_BEEF, 4),
         new ItemStack(Items.APPLE, 3),
         new ItemStack(Items.BOW),
         new ItemStack(Items.ARROW, 24),
         new ItemStack(Items.TORCH, 16),
         new ItemStack(Items.GOLD_INGOT, 5),
         new ItemStack(Items.COMPASS),
         new ItemStack(Items.PAPER, 8),
         new ItemStack(Items.OAK_PLANKS, 32)
      };
      int[] slots = new int[]{1, 2, 3, 9, 10, 13, 20, 24, 28, 33};

      for (int i = 0; i < loot.length; i++) {
         this.backpack.set(slots[i], loot[i]);
      }
   }

   @Nullable
   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag
   ) {
      SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
      this.setup(this.getRandom().nextInt(SKINS.length), this.mode());
      this.giveKit();
      return data;
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         int flags = Downed.flags(this);
         Pose pose = (flags & 1) != 0 ? Pose.SWIMMING : ((flags & 24) != 0 ? Pose.CROUCHING : Pose.STANDING);
         if (this.getPose() != pose) {
            this.setPose(pose);
         }
      }
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.PLAYER_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.PLAYER_DEATH;
   }

   protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
      super.dropCustomDeathLoot(source, looting, recentlyHit);

      for (int i = 1; i < this.backpack.size(); i++) {
         ItemStack stack = (ItemStack)this.backpack.get(i);
         if (!stack.isEmpty()) {
            this.spawnAtLocation(stack);
         }

         this.backpack.set(i, ItemStack.EMPTY);
      }
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Skin", this.skin());
      tag.putString("Mode", this.mode().id());
      ContainerHelper.saveAllItems(tag, this.backpack);
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.entityData.set(DATA_SKIN, tag.getInt("Skin"));
      this.entityData.set(DATA_MODE, BotEntity.Mode.byId(tag.getString("Mode")).ordinal());
      ContainerHelper.loadAllItems(tag, this.backpack);
   }
}
