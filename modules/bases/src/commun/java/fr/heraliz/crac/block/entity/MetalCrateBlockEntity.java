package fr.heraliz.crac.block.entity;

import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class MetalCrateBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(@NotNull Level l, @NotNull BlockPos p, @NotNull BlockState s) {
            l.playSound(null, p, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, l.getRandom().nextFloat() * 0.1F + 0.9F);
            MetalCrateBlockEntity.this.setOpen(s, true);
        }

        @Override
        protected void onClose(@NotNull Level l, @NotNull BlockPos p, @NotNull BlockState s) {
            l.playSound(null, p, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, l.getRandom().nextFloat() * 0.1F + 0.9F);
            MetalCrateBlockEntity.this.setOpen(s, false);
        }

        @Override
        protected void openerCountChanged(@NotNull Level l, @NotNull BlockPos p, @NotNull BlockState s, int prev, int now) {
        }

        @Override
        protected boolean isOwnContainer(@NotNull Player viewer) {
            return viewer.containerMenu instanceof ChestMenu cm && cm.getContainer() == MetalCrateBlockEntity.this;
        }
    };

    public MetalCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METAL_CRATE.get(), pos, state);
    }

    private void setOpen(BlockState state, boolean open) {
        if (this.level != null) {
            this.level.setBlock(this.worldPosition, state.setValue(MetalCrateBlock.OPEN, open), 3);
        }
    }

    @Override
    public void startOpen(Player p) {
        if (!this.remove && !p.isSpectator()) {
            this.openersCounter.incrementOpeners(p, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player p) {
        if (!this.remove && !p.isSpectator()) {
            this.openersCounter.decrementOpeners(p, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public void recheckOpen() {
        if (!this.remove && this.level != null) {
            this.openersCounter.recheckOpeners(this.level, this.getBlockPos(), this.getBlockState());
            // the opener count is not saved: a lid left open by a crash closes as soon as nobody is looking
            BlockState state = this.getBlockState();
            if (this.openersCounter.getOpenerCount() == 0 && state.getValue(MetalCrateBlock.OPEN)) {
                this.setOpen(state, false);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.getBlockState().getValue(MetalCrateBlock.OPEN)) {
            this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 5);
        }
    }

    /**
     * Caisse ajoutée au monde (pose, chargement du tronçon, aussi chez le joueur) : les cases de mur voisines refont leur
     * forme avec objets ({@link fr.heraliz.crac.block.Adosse#changement()}).
     */
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        fr.heraliz.crac.block.Adosse.changement();
    }

    /** Caisse retirée ou tronçon déchargé, aussi chez le joueur (seul signal du retrait de son côté) : idem. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        fr.heraliz.crac.block.Adosse.changement();
    }

    public boolean isEmptyCrate() {
        for (ItemStack s : this.items) {
            if (!s.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @NotNull
    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> list) {
        this.items = list;
    }

    @NotNull
    @Override
    protected Component getDefaultName() {
        return Component.translatable("crac.crate.title");
    }

    @NotNull
    @Override
    protected AbstractContainerMenu createMenu(int id, @NotNull Inventory inv) {
        return ChestMenu.threeRows(id, inv, this);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
    }
}
