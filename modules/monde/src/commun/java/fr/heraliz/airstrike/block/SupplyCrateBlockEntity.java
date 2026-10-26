package fr.heraliz.airstrike.block;

import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SupplyCrateBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    int smokeTicks;
    int despawnTicks;
    boolean despawning;

    public SupplyCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPPLY_CRATE.get(), pos, state);
    }

    public boolean isDespawning() {
        return despawning;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SupplyCrateBlockEntity be) {
        be.tickServeur(level, pos);
    }

    /** Server ticker (smoke and despawn timers): SupplyCrateBlockEntityServeur. Neutral here. */
    void tickServeur(Level level, BlockPos pos) {
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SupplyCrateBlockEntity be) {
        if (be.smokeTicks <= 0) return;
        be.smokeTicks--;
        RandomSource rnd = level.random;
        if (level.getGameTime() % 2 == 0) {
            level.addParticle(SmokeParticleOptions.of(0x3FBF4F, 1.3f + rnd.nextFloat() * 0.8f, 100 + rnd.nextInt(40), 0.11f, 0.95f),
                    pos.getX() + 0.5 + (rnd.nextFloat() - 0.5) * 0.3, pos.getY() + 1.0, pos.getZ() + 0.5 + (rnd.nextFloat() - 0.5) * 0.3,
                    0.015 + (rnd.nextFloat() - 0.5) * 0.02, 0.1, 0.006 + (rnd.nextFloat() - 0.5) * 0.02);
        }
        if (rnd.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.airstrike.supply_crate");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        this.items = list;
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
        smokeTicks = tag.getInt("SmokeTicks");
        despawnTicks = tag.getInt("DespawnTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
        tag.putInt("SmokeTicks", smokeTicks);
        tag.putInt("DespawnTicks", despawnTicks);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SmokeTicks", smokeTicks);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        smokeTicks = tag.getInt("SmokeTicks");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
