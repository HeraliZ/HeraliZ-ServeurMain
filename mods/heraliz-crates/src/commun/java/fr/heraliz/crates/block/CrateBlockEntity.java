package fr.heraliz.crates.block;

import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Etat d'un coffre : sequence d'ouverture en cours, objets de la roue, recompense.
 * Le serveur decide de tout ; le client ne fait qu'animer a partir de l'heure de depart.
 */
public class CrateBlockEntity extends BlockEntity {
    public static final int EVENT_SHAKE = 1;

    final CrateType type;

    // ---- synchronise avec le client
    boolean active;
    long startTick;
    final List<ItemStack> ring = new ArrayList<>();
    /** Rarete de chaque case de la roue (cadres de couleur facon CS:GO). */
    final List<LootRarity> ringRarity = new ArrayList<>();
    int winIndex;
    int winCount = 1;
    LootRarity winRarity = LootRarity.COMMON;
    @Nullable
    UUID opener;

    // ---- client uniquement (effets)
    public long shakeTick = Long.MIN_VALUE / 4;
    public long clientLastTick = Long.MIN_VALUE;
    public long clientSeenStart = Long.MIN_VALUE;
    public final float seed;

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CRATE_BE.get(), pos, state);
        this.type = state.getBlock() instanceof CrateBlock cb ? cb.type : CrateType.VOTE;
        this.seed = (Mth.murmurHash3Mixer(pos.hashCode()) & 0xFFFF) / 65535f;
    }

    // ------------------------------------------------------------------ accesseurs
    public CrateType getCrateType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public long getStartTick() {
        return startTick;
    }

    public List<ItemStack> getRing() {
        return Collections.unmodifiableList(ring);
    }

    public List<LootRarity> getRingRarity() {
        return Collections.unmodifiableList(ringRarity);
    }

    public int getWinIndex() {
        return winIndex;
    }

    public int getWinCount() {
        return winCount;
    }

    public LootRarity getWinRarity() {
        return winRarity;
    }

    @Nullable
    public UUID getOpener() {
        return opener;
    }

    // ------------------------------------------------------------------ logique serveur (CrateBlockEntityServeur)
    /** Clic droit sur la caisse (serveur) : interface d'ouverture, clé, tirage. Neutre sans partie serveur. */
    public void clicDroit(ServerPlayer player, boolean accroupi) {
    }

    /** Le coffre est casse pendant une ouverture : le joueur recoit quand meme son lot (version serveur). */
    public void onCrateRemoved() {
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity be) {
        be.tickServeur(level, pos, state);
    }

    /** Parties, fin de sequence, remise du lot : version serveur ; neutre ici. */
    protected void tickServeur(Level level, BlockPos pos, BlockState state) {
    }

    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ client
    /** Ticker du client (effets, sons) : branche par la partie client (client/CrateClientTicker::tick). */
    public static volatile BlockEntityTicker<CrateBlockEntity> TICK_CLIENT;

    public static void clientTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity be) {
        BlockEntityTicker<CrateBlockEntity> tk = TICK_CLIENT;
        if (tk != null) tk.tick(level, pos, state, be);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_SHAKE) {
            if (level != null && level.isClientSide) {
                shakeTick = level.getGameTime();
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    private AABB restBox, activeBox;

    /**
     * Boite de visibilite : au repos, juste la caisse et son nom flottant (les caisses hors champ ne sont pas dessinees) ;
     * pendant une ouverture, de quoi contenir la roue et les effets.
     */
    @Override
    public AABB getRenderBoundingBox() {
        if (active) {
            if (activeBox == null) {
                activeBox = new AABB(worldPosition.getX() - 3, worldPosition.getY() - 1, worldPosition.getZ() - 3,
                        worldPosition.getX() + 4, worldPosition.getY() + 12, worldPosition.getZ() + 4);
            }
            return activeBox;
        }
        if (restBox == null) {
            double half = CrateShapes.halfWidth(type) / 16.0 + 0.1;
            // caisse et nom flottant au-dessus
            double top = (CrateShapes.height(type) + 26f) / 16.0;
            double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
            restBox = new AABB(cx - half, worldPosition.getY(), cz - half, cx + half, worldPosition.getY() + top, cz + half);
        }
        return restBox;
    }

    // ------------------------------------------------------------------ sauvegarde & synchro
    void writeSync(CompoundTag tag) {
        tag.putBoolean("Active", active);
        if (active) {
            tag.putLong("Start", startTick);
            tag.putInt("Win", winIndex);
            tag.putInt("WinCount", winCount);
            tag.putString("Rarity", winRarity.id);
            if (opener != null) tag.putUUID("Opener", opener);
            ListTag list = new ListTag();
            for (ItemStack s : ring) {
                list.add(s.save(new CompoundTag()));
            }
            tag.put("Ring", list);
            int[] rar = new int[ringRarity.size()];
            for (int i = 0; i < rar.length; i++) rar[i] = ringRarity.get(i).ordinal();
            tag.putIntArray("RingRarity", rar);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeSync(tag);   // le lot tiré (Reward, Rewarded) n'est écrit que par la version serveur
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        active = tag.getBoolean("Active");
        ring.clear();
        ringRarity.clear();
        if (active) {
            startTick = tag.getLong("Start");
            winIndex = tag.getInt("Win");
            winCount = Math.max(1, tag.getInt("WinCount"));
            winRarity = LootRarity.byId(tag.getString("Rarity"));
            opener = tag.hasUUID("Opener") ? tag.getUUID("Opener") : null;
            ListTag list = tag.getList("Ring", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ring.add(ItemStack.of(list.getCompound(i)));
            }
            if (winIndex < 0 || winIndex >= ring.size()) winIndex = 0;
            // anciennes sauvegardes : pas de raretes par case -> commun, sauf l'objet gagnant
            int[] rar = tag.getIntArray("RingRarity");
            LootRarity[] all = LootRarity.values();
            for (int i = 0; i < ring.size(); i++) {
                ringRarity.add(i < rar.length && rar[i] >= 0 && rar[i] < all.length ? all[rar[i]] : (i == winIndex ? winRarity : LootRarity.COMMON));
            }
        } else {
            opener = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeSync(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
