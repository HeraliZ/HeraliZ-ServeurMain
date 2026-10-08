package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
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
 * Bonus au sol du mode zombies (MATCHS_SPEC §4) : icone lumineuse qui flotte et tourne, halo de la couleur du bonus,
 * lueurs qui montent ; clignote quand il va disparaitre ({@link #clignoter(int)}). Purement visuel : ni ramassable,
 * ni touchable, ni sauvegarde ; c'est le jeu qui detecte le joueur et retire l'entite ({@code discard}).
 */
public class BonusEntite extends Entity {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(BonusEntite.class, EntityDataSerializers.INT);
    /** Secondes restantes annoncees par le jeu (0 = ne clignote pas encore). */
    private static final EntityDataAccessor<Integer> RESTE = SynchedEntityData.defineId(BonusEntite.class, EntityDataSerializers.INT);

    public BonusEntite(EntityType<? extends BonusEntite> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static BonusEntite creer(Level level, double x, double y, double z, TypeBonus type) {
        BonusEntite b = new BonusEntite(ZbRegistres.BONUS.get(), level);
        b.setPos(x, y, z);
        b.entityData.set(TYPE, type.ordinal());
        b.getPersistentData().putBoolean(ZombiesApi.TAG_DECOR, true);
        return b;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TYPE, 0);
        this.entityData.define(RESTE, 0);
    }

    public TypeBonus type() {
        return TypeBonus.de(this.entityData.get(TYPE));
    }

    /** Secondes restantes avant disparition (0 = pas encore annonce). */
    public int reste() {
        return this.entityData.get(RESTE);
    }

    /** Le bonus va disparaitre dans {@code secondes} : il clignote (de plus en plus vite). */
    public void clignoter(int secondes) {
        this.entityData.set(RESTE, Mth.clamp(secondes, 1, 60));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.random.nextInt(3) == 0) {
            int c = type().couleur;
            this.level().addParticle(ZbRegistres.LUEUR.get(), getX() + (random.nextDouble() - 0.5) * 0.7, getY() + 0.1,
                    getZ() + (random.nextDouble() - 0.5) * 0.7, ((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0);
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(TYPE, tag.getInt("Type"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Type", this.entityData.get(TYPE));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d2) {
        return d2 < 64 * 64;
    }
}
