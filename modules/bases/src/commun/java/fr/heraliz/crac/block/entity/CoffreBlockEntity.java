package fr.heraliz.crac.block.entity;

import fr.heraliz.crac.block.CoffreBlock;
import fr.heraliz.crac.menu.CoffreMenu;
import fr.heraliz.crac.registry.ModBlockEntities;
import fr.heraliz.crac.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * Inventaire d'un coffre par rareté ({@link CoffreBlock}), dans sa case principale. Ouverture et fermeture : le serveur
 * change l'état {@code open} des deux cases (modèle sans la pièce mobile) et envoie un évènement de bloc ; chez le joueur,
 * la pièce mobile est dessinée par {@code client/render/CoffreRendu} pendant l'animation et tant que le coffre est ouvert,
 * jamais quand il est fermé. La fermeture attend la fin de l'animation avant de remettre le modèle fermé.
 */
public class CoffreBlockEntity extends RandomizableContainerBlockEntity {
    /** Évènement de bloc : param 1 = ouverture, 0 = fermeture. */
    private static final int EV_OUVERTURE = 1;
    /** Ticks pendant lesquels la pièce mobile reste dessinée après la fermeture (le temps que le modèle fermé arrive). */
    private static final int RELAIS = 4;

    private NonNullList<ItemStack> items;
    /** Serveur : heure de jeu où la fermeture est finie. */
    private long fermeA;
    /** Joueur : début et sens de la dernière animation ; heure où le modèle fermé est revenu. */
    private long animDebut = Long.MIN_VALUE;
    private boolean animOuvre;
    private long fermeVu = Long.MIN_VALUE;

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(@NotNull Level l, @NotNull BlockPos p, @NotNull BlockState s) {
            CoffreBlock c = CoffreBlockEntity.this.coffre();
            l.playSound(null, p, c.sonOuvre(), SoundSource.BLOCKS, 0.6F, c.hauteurSon() * (l.getRandom().nextFloat() * 0.1F + 0.95F));
            l.blockEvent(p, s.getBlock(), EV_OUVERTURE, 1);
            CoffreBlockEntity.this.setOpen(s, true);
        }

        @Override
        protected void onClose(@NotNull Level l, @NotNull BlockPos p, @NotNull BlockState s) {
            CoffreBlock c = CoffreBlockEntity.this.coffre();
            l.playSound(null, p, c.sonFerme(), SoundSource.BLOCKS, 0.6F, c.hauteurSon() * (l.getRandom().nextFloat() * 0.1F + 0.95F));
            l.blockEvent(p, s.getBlock(), EV_OUVERTURE, 0);
            int d = CoffreBlockEntity.this.duree();
            CoffreBlockEntity.this.fermeA = l.getGameTime() + d;
            l.scheduleTick(p, s.getBlock(), d + 1);
        }

        @Override
        protected void openerCountChanged(@NotNull Level l, @NotNull BlockPos p, @NotNull BlockState s, int prev, int now) {
        }

        @Override
        protected boolean isOwnContainer(@NotNull Player viewer) {
            return viewer.containerMenu instanceof ChestMenu cm && cm.getContainer() == CoffreBlockEntity.this
                || viewer.containerMenu instanceof CoffreMenu m && m.conteneur() == CoffreBlockEntity.this;
        }
    };

    public CoffreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COFFRE.get(), pos, state);
        this.items = NonNullList.withSize(taille(state), ItemStack.EMPTY);
    }

    private static int taille(BlockState s) {
        return s.getBlock() instanceof CoffreBlock c ? c.colonnes() * c.lignes() : 54;
    }

    public CoffreBlock coffre() {
        return (CoffreBlock) this.getBlockState().getBlock();
    }

    public int duree() {
        return this.getBlockState().getBlock() instanceof CoffreBlock c ? c.mesures().dureeTicks() : 8;
    }

    /** Serveur : ouvre l'écran du coffre (9 colonnes : écran de coffre de Minecraft ; plus large : {@link CoffreMenu}). */
    public void ouvrir(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        CoffreBlock c = this.coffre();
        if (c.colonnes() == 9) {
            sp.openMenu(this);
        } else {
            net.minecraftforge.network.NetworkHooks.openScreen(sp, this, buf -> {
                buf.writeVarInt(c.colonnes());
                buf.writeVarInt(c.lignes());
            });
        }
    }

    // ---------------------------------------------------------------- ouverture (serveur)

    /** Les deux cases du coffre passent ouvertes ou fermées (le modèle de chacune en dépend). */
    private void setOpen(BlockState state, boolean open) {
        if (this.level == null || !(state.getBlock() instanceof CoffreBlock c)) {
            return;
        }
        this.level.setBlock(this.worldPosition, state.setValue(CoffreBlock.OPEN, open), 3);
        BlockPos a = c.annexeDe(this.worldPosition, state.getValue(CoffreBlock.FACING));
        BlockState as = this.level.getBlockState(a);
        if (as.is(c) && as.getValue(CoffreBlock.ANNEXE) && as.getValue(CoffreBlock.OPEN) != open) {
            this.level.setBlock(a, as.setValue(CoffreBlock.OPEN, open), 3);
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

    /** Tick programmé : recompte ceux qui regardent ; personne et l'animation finie : modèle fermé. */
    public void verifierOuverture() {
        if (this.remove || this.level == null) {
            return;
        }
        this.openersCounter.recheckOpeners(this.level, this.getBlockPos(), this.getBlockState());
        BlockState state = this.getBlockState();
        if (this.openersCounter.getOpenerCount() == 0 && state.getValue(CoffreBlock.OPEN)) {
            long reste = this.fermeA - this.level.getGameTime();
            if (reste > 0) {
                this.level.scheduleTick(this.getBlockPos(), state.getBlock(), (int) Math.min(reste, 200));
            } else {
                this.setOpen(state, false);   // le compte n'est pas gardé : un coffre resté ouvert (arrêt brutal) se ferme
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.getBlockState().getValue(CoffreBlock.OPEN)) {
            this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 5);
        }
    }

    // ---------------------------------------------------------------- animation (joueur)

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EV_OUVERTURE) {
            if (this.level != null && this.level.isClientSide) {
                // repart de là où en est l'animation (ouvert puis refermé tout de suite : pas de saut)
                float p = this.progression(0F);
                int d = this.duree();
                boolean ouvre = param == 1;
                this.animDebut = this.level.getGameTime() - Math.round((ouvre ? p : 1F - p) * d);
                this.animOuvre = ouvre;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Ouverture affichée, de 0 (fermé) à 1 (ouvert). */
    public float progression(float partial) {
        if (this.level == null) {
            return 0F;
        }
        if (this.animDebut == Long.MIN_VALUE) {
            return this.getBlockState().getValue(CoffreBlock.OPEN) ? 1F : 0F;
        }
        float t = Mth.clamp((this.level.getGameTime() - this.animDebut + partial) / this.duree(), 0F, 1F);
        return this.animOuvre ? t : 1F - t;
    }

    /** La pièce mobile est à dessiner : coffre ouvert (modèle sans elle), ou fermé depuis un instant (modèle en route). */
    public boolean mobileVisible() {
        if (this.level == null) {
            return false;
        }
        return this.getBlockState().getValue(CoffreBlock.OPEN) || this.level.getGameTime() - this.fermeVu < RELAIS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(@NotNull BlockState state) {
        boolean avant = this.getBlockState().hasProperty(CoffreBlock.OPEN) && this.getBlockState().getValue(CoffreBlock.OPEN);
        super.setBlockState(state);
        if (this.level != null && this.level.isClientSide && avant && state.hasProperty(CoffreBlock.OPEN)
            && !state.getValue(CoffreBlock.OPEN)) {
            this.fermeVu = this.level.getGameTime();
        }
    }

    /** La porte ou le couvercle sort du bloc en s'ouvrant (et le coffre fait deux cases). */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(2.0);
    }

    // ---------------------------------------------------------------- objet adossé

    /** Coffre ajouté au monde (pose, chargement, aussi chez le joueur) : les cases de mur voisines refont leur forme. */
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        fr.heraliz.crac.block.Adosse.changement();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        fr.heraliz.crac.block.Adosse.changement();
    }

    // ---------------------------------------------------------------- inventaire

    @Override
    public int getContainerSize() {
        return this.items.size();
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
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @NotNull
    @Override
    protected AbstractContainerMenu createMenu(int id, @NotNull Inventory inv) {
        CoffreBlock c = this.coffre();
        if (c.colonnes() == 9) {
            return new ChestMenu(typeChest(c.lignes()), id, inv, this, c.lignes());
        }
        return new CoffreMenu(ModMenus.COFFRE.get(), id, inv, this, c.colonnes(), c.lignes());
    }

    private static MenuType<ChestMenu> typeChest(int lignes) {
        return switch (lignes) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
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
        this.items = NonNullList.withSize(taille(this.getBlockState()), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
    }

    /** Pour le rendu : sens du coffre. */
    public Direction sens() {
        return this.getBlockState().getValue(CoffreBlock.FACING);
    }
}
