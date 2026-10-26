package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.pont.Crochet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Épave de l'hélicoptère abattu : partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.1). Modèle {@code helicopter_wreck}
 * immobile, tourné comme l'hélicoptère au moment de l'impact ; tant qu'elle brûle (heure de fin synchronisée), le jeu du
 * joueur fait sortir flammes et fumée de ses foyers ({@code fire_0} à {@code fire_3}), ensuite une fumée plus légère. Le
 * minuteur du feu, la zone de feu, la colonne de fumée et le retrait sont dans {@code HelicopterWreckServeur}. Pas de
 * collision (v1). Jamais sauvegardée : au redémarrage du serveur, l'épave n'est plus là.
 */
public class HelicopterWreck extends Entity {
    /** Tick de jeu (tronqué à 31 bits) où le feu s'éteint. */
    static final EntityDataAccessor<Integer> FEU_FIN = SynchedEntityData.defineId(HelicopterWreck.class, EntityDataSerializers.INT);

    /** Tick client d'une épave (flammes et fumée des foyers) : branché par PartieClient (docs §6). */
    public static final Crochet<HelicopterWreck> TICK_CLIENT = new Crochet<>();

    public HelicopterWreck(EntityType<? extends HelicopterWreck> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FEU_FIN, 0);
    }

    /** L'épave brûle encore. */
    public boolean enFeu() {
        return (int) (level().getGameTime() & 0x7FFFFFFFL) < entityData.get(FEU_FIN);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            TICK_CLIENT.accept(this);
        } else {
            tickServeur();
        }
    }

    /** Minuteurs du feu et du retrait : HelicopterWreckServeur. Neutre ici. */
    void tickServeur() {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 256.0 * 256.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9.0, 2.5, 9.0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
