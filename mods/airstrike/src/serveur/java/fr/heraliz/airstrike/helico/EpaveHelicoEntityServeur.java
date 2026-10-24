package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.network.FouillePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link EpaveHelicoEntity} : chute du morceau projeté au crash (sans collision avec les blocs : il se
 * pose sur le dessus du relief, il ne peut donc jamais rester coincé dans un arbre), disparition à l'heure {@code fin},
 * sauvegarde avec le monde (un tronçon déchargé puis rechargé garde son épave et ses heures). La fouille est décidée par
 * {@link Fouilles}.
 */
public class EpaveHelicoEntityServeur extends EpaveHelicoEntity {
    private long fin;
    private float rotation;
    private boolean pose;

    public EpaveHelicoEntityServeur(EntityType<? extends EpaveHelicoEntity> type, Level level) {
        super(type, level);
    }

    void preparer(int piece, boolean fouillable, long feuFin, long fin, Vec3 vitesse, float rotation) {
        entityData.set(PIECE, (byte) piece);
        refreshDimensions();
        entityData.set(FOUILLABLE, fouillable);
        entityData.set(FEU_FIN, feuFin);
        this.fin = fin;
        this.rotation = rotation;
        setDeltaMovement(vitesse);
    }

    void vider() {
        entityData.set(FOUILLABLE, false);
    }

    @Override
    protected void tickServeur() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (fin > 0 && sl.getGameTime() >= fin) {
            discard();
            return;
        }
        if (pose) return;
        Vec3 v = getDeltaMovement();
        v = new Vec3(v.x * 0.985, Math.max(-1.6, v.y - 0.06), v.z * 0.985);
        double x = getX() + v.x, y = getY() + v.y, z = getZ() + v.z;
        double sol = HelicoEntityServeur.sol(sl, x, z);
        setYRot(getYRot() + rotation);
        if (!Double.isNaN(sol) && y <= sol && v.y <= 0) {
            y = sol;
            v = Vec3.ZERO;
            pose = true;
            sl.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7f, 0.45f + random.nextFloat() * 0.2f);
        } else if (Double.isNaN(sol) && y < sl.getMinBuildHeight()) {
            discard();
            return;
        }
        setDeltaMovement(v);
        setPos(x, y, z);
    }

    @Override
    public InteractionResult interact(Player joueur, InteractionHand main) {
        if (joueur instanceof ServerPlayer sp && main == InteractionHand.MAIN_HAND) Fouilles.clic(sp, this, FouillePacket.APPUI);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(PIECE, tag.getByte("Piece"));
        refreshDimensions();
        entityData.set(FOUILLABLE, tag.getBoolean("Fouillable"));
        entityData.set(FEU_FIN, tag.getLong("FeuFin"));
        fin = tag.getLong("Fin");
        pose = tag.getBoolean("Pose");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Piece", (byte) piece());
        tag.putBoolean("Fouillable", fouillable());
        tag.putLong("FeuFin", feuFin());
        tag.putLong("Fin", fin);
        tag.putBoolean("Pose", pose);
    }
}
