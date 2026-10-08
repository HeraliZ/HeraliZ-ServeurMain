package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Tas de materiaux de recuperation lache par un zombie (ZOMBIES_V2_SPEC §2, etablis) : engrenages, boulons et tole qui
 * brillent au sol, tournent lentement ; plus gros selon la quantite ; clignote avant de disparaitre
 * ({@link #clignoter}). Purement visuel : c'est le jeu qui detecte le joueur et retire l'entite.
 */
public class MateriauEntite extends Entity {
    private static final EntityDataAccessor<Integer> QUANTITE = SynchedEntityData.defineId(MateriauEntite.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RESTE = SynchedEntityData.defineId(MateriauEntite.class, EntityDataSerializers.INT);

    public MateriauEntite(EntityType<? extends MateriauEntite> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static MateriauEntite creer(Level level, double x, double y, double z, int quantite) {
        MateriauEntite m = new MateriauEntite(ZbRegistres.MATERIAU.get(), level);
        m.setPos(x, y, z);
        m.setYRot(level.random.nextFloat() * 360);
        m.entityData.set(QUANTITE, Mth.clamp(quantite, 1, 99));
        m.getPersistentData().putBoolean(ZombiesApi.TAG_DECOR, true);
        return m;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(QUANTITE, 1);
        this.entityData.define(RESTE, 0);
    }

    public int quantite() {
        return this.entityData.get(QUANTITE);
    }

    public int reste() {
        return this.entityData.get(RESTE);
    }

    public void clignoter(int secondes) {
        this.entityData.set(RESTE, Mth.clamp(secondes, 1, 60));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.random.nextInt(7) == 0) {
            this.level().addParticle(ZbRegistres.LUEUR.get(), getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.12,
                    getZ() + (random.nextDouble() - 0.5) * 0.4, 1.0, 0.9, 0.55);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(QUANTITE, Math.max(1, tag.getInt("Q")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Q", quantite());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d2) {
        return d2 < 48 * 48;
    }
}
