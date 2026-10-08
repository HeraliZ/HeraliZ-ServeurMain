package fr.heraliz.crac.build.world;

import fr.heraliz.crac.pont.Serveur;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Holds the pieces passing through one structure block. Collision and rendering are derived from it. */
public class StructureBlockEntity extends BlockEntity {
    public static final ModelProperty<List<Occupant>> OCCUPANTS = new ModelProperty<>();
    /** The block position, so the model can resolve each occupant's quads for this block (render threads). */
    public static final ModelProperty<BlockPos> POS = new ModelProperty<>();
    /** NBT format of the occupant list. */
    public static final int FORMAT = 3;

    private List<Occupant> occupants = List.of();
    /** v2 occupant data ({@code Occ}) kept untouched while the store is a stand-in for old building data (§7.6). */
    @Nullable
    private long[] legacy;
    @Nullable
    private VoxelShape shape;
    @Nullable
    private VoxelShape occlusion;

    public StructureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE.get(), pos, state);
    }

    public List<Occupant> occupants() {
        return this.occupants;
    }

    public boolean isEmpty() {
        return this.occupants.isEmpty();
    }

    /** Whether the block still holds v2 occupant data (old world started with this version): integrity skips it. */
    public boolean hasLegacyData() {
        return this.legacy != null;
    }

    /** Server: replace the occupant list and push it to clients. */
    public void setOccupants(List<Occupant> list) {
        this.occupants = List.copyOf(list);
        this.legacy = null;
        this.shape = null;
        this.occlusion = null;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public VoxelShape shape() {
        VoxelShape s = this.shape;
        if (s == null) {
            s = Shapes.empty();
            for (Occupant o : this.occupants) {
                s = Shapes.joinUnoptimized(s, o.shape(this.worldPosition), BooleanOp.OR);
            }
            s = s.optimize();
            this.shape = s;
        }
        return s;
    }

    /** Union of the occupants that close faces ({@link Occupant#occludes}): drives the block state's face flags. */
    public VoxelShape occlusionShape() {
        VoxelShape s = this.occlusion;
        if (s == null) {
            s = Shapes.empty();
            for (Occupant o : this.occupants) {
                if (o.occludes()) {
                    s = Shapes.joinUnoptimized(s, o.occlusion(this.worldPosition), BooleanOp.OR);
                }
            }
            s = s.optimize();
            this.occlusion = s;
        }
        return s;
    }

    @Override
    public @NotNull ModelData getModelData() {
        return ModelData.builder().with(OCCUPANTS, this.occupants).with(POS, this.worldPosition).build();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("V", FORMAT);
        long[] data = new long[this.occupants.size() * Occupant.STRIDE];
        for (int i = 0; i < this.occupants.size(); i++) {
            this.occupants.get(i).pack(data, i * Occupant.STRIDE);
        }
        tag.putLongArray("Occ3", data);
        if (this.legacy != null) {
            tag.putLongArray("Occ", this.legacy);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        List<Occupant> list = new ArrayList<>();
        if (tag.getInt("V") == FORMAT) {
            long[] data = tag.getLongArray("Occ3");
            for (int i = 0; i + Occupant.STRIDE <= data.length; i += Occupant.STRIDE) {
                Occupant o = Occupant.unpack(data, i);
                if (o != null) {
                    list.add(o);
                }
            }
        }
        // v2 data is never interpreted (fresh worlds only): it is kept as is so a mistaken start loses nothing
        this.legacy = tag.contains("Occ", Tag.TAG_LONG_ARRAY) ? tag.getLongArray("Occ") : null;
        this.occupants = List.copyOf(list);
        this.shape = null;
        this.occlusion = null;
        if (this.level != null && this.level.isClientSide()) {
            this.clientChanged();
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.load(tag);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            if (this.level.isClientSide()) {
                this.clientChanged();
            } else {
                Serveur.Moteur m = Serveur.MOTEUR;
                if (m != null) m.structureVerifier(this.level, this.worldPosition);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide()) {
            ClientMirror.remove(this.worldPosition);
        }
    }

    private void clientChanged() {
        ClientMirror.update(this.worldPosition, this.occupants);
        this.requestModelDataUpdate();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }
}
