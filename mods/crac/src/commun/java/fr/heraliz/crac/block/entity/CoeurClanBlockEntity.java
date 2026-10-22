package fr.heraliz.crac.block.entity;

import fr.heraliz.crac.registry.ModBlockEntities;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cœur de clan : le groupe (base du clan) à qui il est, et ce qu'il montre à tous : points de saison du clan et ses
 * rangs (PVE, PVP, events, général ; 0 = pas classé). Les points sont remplis par le système de saison (à venir) avec
 * {@link #majScores} ; d'ici là, 0 et « non classé ».
 */
public class CoeurClanBlockEntity extends BlockEntity {
    public static final int PVE = 0, PVP = 1, EVENTS = 2, GENERAL = 3;
    @Nullable
    private UUID groupe;
    private long points;
    private int[] rangs = new int[4];

    public CoeurClanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COEUR_CLAN.get(), pos, state);
    }

    @Nullable
    public UUID groupe() {
        return this.groupe;
    }

    public void setGroupe(@Nullable UUID g) {
        this.groupe = g;
        this.setChanged();
    }

    public long points() {
        return this.points;
    }

    /** Rang dans un classement ({@link #PVE}…{@link #GENERAL}), 0 si pas classé. */
    public int rang(int classement) {
        return classement >= 0 && classement < this.rangs.length ? this.rangs[classement] : 0;
    }

    /** Serveur : nouveaux points et rangs ; renvoyés aux joueurs seulement s'ils changent. */
    public void majScores(long points, int[] rangs) {
        int[] r = Arrays.copyOf(rangs, 4);
        if (points == this.points && Arrays.equals(r, this.rangs)) {
            return;
        }
        this.points = points;
        this.rangs = r;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    /** L'hologramme flotte au-dessus du bloc. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).expandTowards(0, 1, 0).inflate(0.5);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.groupe != null) {
            tag.putUUID("Groupe", this.groupe);
        }
        tag.putLong("Points", this.points);
        tag.putIntArray("Rangs", this.rangs.clone());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.groupe = tag.hasUUID("Groupe") ? tag.getUUID("Groupe") : null;
        this.lireScores(tag);
    }

    private void lireScores(CompoundTag tag) {
        this.points = tag.getLong("Points");
        this.rangs = Arrays.copyOf(tag.getIntArray("Rangs"), 4);
    }

    /** Aux joueurs : points et rangs seulement (jamais le groupe). */
    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putLong("Points", this.points);
        t.putIntArray("Rangs", this.rangs.clone());
        return t;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.lireScores(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            this.lireScores(pkt.getTag());
        }
    }
}
