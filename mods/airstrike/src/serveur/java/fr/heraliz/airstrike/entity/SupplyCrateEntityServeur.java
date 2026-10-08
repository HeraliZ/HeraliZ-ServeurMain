package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Partie SERVEUR de {@link SupplyCrateEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SupplyCrateEntity(...) » côté serveur.
 */
public class SupplyCrateEntityServeur extends SupplyCrateEntity {
   public SupplyCrateEntityServeur(EntityType<? extends SupplyCrateEntity> type, Level level) {
      super(type, level);
   }

    // ---------------------------------------------------------------- server parts of tick() (original bodies)

    @Override
    void parachuteOuvert() {
        level().playSound(null, getX(), getY(), getZ(), ModSounds.PARACHUTE_OPEN.get(), SoundSource.NEUTRAL, 2.5f, 1.0f);
    }

    @Override
    void chuteServeur() {
        if (fallTicks % 10 == 0) entityData.set(FALL_TICKS, fallTicks);
        if (onGround() || isInWater() || isInLava() || fallTicks > 4000 || getY() < level().getMinBuildHeight()) {
            land();
        }
    }

    @Override
    void poseServeur() {
        if (smokeTicks > 0 && --smokeTicks == 0) entityData.set(SMOKING, false);
        if (fillDirty && tickCount % 5 == 0) updateFill();
        long at = getOpenAt();
        if (opener != null && at >= 0 && level().getGameTime() - at >= REVEAL_TICKS) {
            // end of the reveal: the inventory opens for the player who broke the crate open
            Player p = level().getPlayerByUUID(opener);
            opener = null;
            if (p != null && p.isAlive() && p.distanceToSqr(this) < 10 * 10 && p.containerMenu == p.inventoryMenu) {
                p.openMenu(this);
            }
        }
        if (despawnTicks > 0 && --despawnTicks == 0) discard();
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    @Nullable
    private ResourceLocation lootTable;
    private long lootSeed;
    private int smokeTicks;
    private int despawnTicks;
    /** Player who broke the crate open: the inventory opens for him at the end of the animation. */
    @Nullable
    private java.util.UUID opener;
    private boolean fillDirty;

    public static SupplyCrateEntity create(Level level, Vec3 pos, Vec3 velocity, float yaw) {
        // created by the type (factory): the server version
        SupplyCrateEntity e = ModEntities.SUPPLY_CRATE.get().create(level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setDeltaMovement(velocity);
        e.setYRot(yaw);
        e.yRotO = yaw;
        return e;
    }

    private void land() {
        if (!(level() instanceof ServerLevel server)) return;
        // square up with the world grid so the crate lines up with nearby buildings
        float yaw = Math.round(getYRot() / 90f) * 90f;
        setYRot(yaw);
        yRotO = yaw;
        setDeltaMovement(Vec3.ZERO);
        entityData.set(LANDED, true);
        entityData.set(LANDED_AT, server.getGameTime());
        ResourceLocation rl = ResourceLocation.tryParse(AirstrikeConfig.SUPPLY_LOOT_TABLE.get());
        lootTable = rl;
        lootSeed = random.nextLong();
        smokeTicks = AirstrikeConfig.SUPPLY_SMOKE_SECONDS.get() * 20;
        despawnTicks = AirstrikeConfig.SUPPLY_DESPAWN_MINUTES.get() * 1200;
        entityData.set(SMOKING, smokeTicks > 0);
        // the crate's own smoke pot takes over from the target marker
        for (EffectCloudEntity marker : server.getEntitiesOfClass(EffectCloudEntity.class, getBoundingBox().inflate(24),
                e -> e.getKind() == EffectCloudEntity.MARKER)) {
            marker.discard();
        }
        server.playSound(null, getX(), getY(), getZ(), ModSounds.CRATE_LAND.get(), SoundSource.BLOCKS, 2.5f, 0.8f);
        Network.sendImpact(server, new ImpactPacket(ImpactPacket.CRATE_LAND, getX(), getY(), getZ(), 1.0f));
    }

    private void updateFill() {
        fillDirty = false;
        int n = 0;
        for (ItemStack st : items) if (!st.isEmpty()) n++;
        entityData.set(FILL, n);
        if (n > entityData.get(FILL_MAX)) entityData.set(FILL_MAX, n);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Container

    private void unpackLoot(@Nullable Player player) {
        if (lootTable == null || !(level() instanceof ServerLevel server)) return;
        LootTable table = server.getServer().getLootData().getLootTable(lootTable);
        lootTable = null;
        LootParams.Builder params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position());
        if (player != null) params.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        table.fill(this, params.create(LootContextParamSets.CHEST), lootSeed);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isLanded()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            long at = getOpenAt();
            if (at < 0) {
                // first use: break the crate open, the inventory follows at the end of the animation
                unpackLoot(player);
                updateFill();
                entityData.set(OPEN_AT, level().getGameTime());
                opener = player.getUUID();
                level().playSound(null, getX(), getY() + 1.2, getZ(), ModSounds.CRATE_REVEAL.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
            } else if (level().getGameTime() - at >= REVEAL_TICKS) {
                player.openMenu(this);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        unpackLoot(player);
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public boolean isEmpty() {
        unpackLoot(null);
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        unpackLoot(null);
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        unpackLoot(null);
        return ContainerHelper.removeItem(items, slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        unpackLoot(null);
        ItemStack s = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        unpackLoot(null);
        if (slot >= 0 && slot < SLOTS) items.set(slot, stack);
    }

    @Override
    public void setChanged() {
        fillDirty = true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // only creative players can remove a crate (attack it)
        if (!level().isClientSide && source.getEntity() instanceof Player p && p.getAbilities().instabuild) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        fallTicks = tag.getInt("FallTicks");
        entityData.set(FALL_TICKS, fallTicks);
        entityData.set(LANDED, tag.getBoolean("Landed"));
        entityData.set(LANDED_AT, tag.contains("LandedAt") ? tag.getLong("LandedAt") : level().getGameTime() - 100000L);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        lootTable = tag.contains("LootTable") ? ResourceLocation.tryParse(tag.getString("LootTable")) : null;
        lootSeed = tag.getLong("LootSeed");
        smokeTicks = tag.getInt("SmokeTicks");
        despawnTicks = tag.getInt("DespawnTicks");
        entityData.set(SMOKING, smokeTicks > 0);
        entityData.set(OPEN_AT, tag.contains("OpenAt") ? tag.getLong("OpenAt") : -1L);
        entityData.set(FILL_MAX, tag.getInt("FillMax"));
        fillDirty = true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("FallTicks", fallTicks);
        tag.putBoolean("Landed", isLanded());
        tag.putLong("LandedAt", entityData.get(LANDED_AT));
        ContainerHelper.saveAllItems(tag, items);
        if (lootTable != null) {
            tag.putString("LootTable", lootTable.toString());
            tag.putLong("LootSeed", lootSeed);
        }
        tag.putInt("SmokeTicks", smokeTicks);
        tag.putInt("DespawnTicks", despawnTicks);
        if (getOpenAt() >= 0) tag.putLong("OpenAt", getOpenAt());
        tag.putInt("FillMax", entityData.get(FILL_MAX));
    }
}
