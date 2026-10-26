package fr.heralisz.utils.ascenseur;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Cabine de l'ascenseur côté serveur (créée par Ascenseur.CABINE_USINE dans un monde serveur) : départ vers un palier,
 * déplacement en douceur, arrivée, sauvegarde. Tout ce qui est debout sur le plancher (joueurs et créatures) est porté
 * à chaque tick, et rien ne passe à travers le plancher. Pour les joueurs, le compteur « vole depuis trop longtemps » du
 * serveur est remis à zéro tant qu'ils sont portés (sinon un trajet de plus de 4 secondes au milieu du puits les ferait
 * expulser pour vol) ; leur propre client les porte aussi (PorteurCabine), si bien que les deux restent d'accord.
 */
public class CabineServeur extends Cabine {
    /** ServerGamePacketListenerImpl.aboveGroundTickCount (nom SRG f_9737_). */
    private static final Field AU_DESSUS_DU_SOL = champ();

    public CabineServeur(EntityType<? extends Cabine> type, Level level) {
        super(type, level);
    }

    private static Field champ() {
        try {
            return ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9737_");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Partie serveur du tick (après super.tick() et la branche client). */
    @Override
    void tickServeur() {
        if (enMarche()) {
            List<LivingEntity> portes = level().getEntitiesOfClass(LivingEntity.class, zone(), this::porte);
            double c = entityData.get(CIBLE), y = getY(), d = c - y;
            double v = Math.min(VMAX * Math.min(1.0, ++ticksMarche / 20.0), Math.max(0.03, Math.abs(d) * 0.1));
            boolean fini = Math.abs(d) <= v;
            setPos(getX(), fini ? c : y + Math.signum(d) * v, getZ());
            double dy = getY() - y;
            for (LivingEntity e : portes) {
                porter(e, dy);
            }
            if (fini) {
                arrivee();
            }
            if (ticksMarche % 30 == 1) {
                level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.8f, 0.5f);
            }
        }
    }

    private static final double VMAX = 0.2;                 // 4 blocs par seconde

    private int ticksMarche;
    boolean libere;

    private AABB zone() {
        double f = plancher();
        return new AABB(getX() - DEMI - 0.2, f - 0.8, getZ() - DEMI - 0.2, getX() + DEMI + 0.2, f + 0.6, getZ() + DEMI + 0.2);
    }

    /** Monte ou descend avec la cabine, jamais sous le plancher ; ni chute ni « vol » comptés pendant le trajet. */
    private void porter(LivingEntity e, double dy) {
        e.setPos(e.getX(), Math.max(e.getY() + dy, plancher()), e.getZ());
        e.fallDistance = 0;
        e.setOnGround(true);
        if (e.getDeltaMovement().y < 0) {
            e.setDeltaMovement(e.getDeltaMovement().multiply(1, 0, 1));
        }
        if (e instanceof ServerPlayer sp && sp.connection != null && AU_DESSUS_DU_SOL != null) {
            try {
                AU_DESSUS_DU_SOL.setInt(sp.connection, 0);
            } catch (IllegalAccessException ignored) {
                // pas grave : au pire, le contrôle anti-vol du serveur reste actif
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(CIBLE, t.getFloat("cible"));
        entityData.set(MARCHE, t.getBoolean("marche"));
        entityData.set(FACADE, t.getInt("facade"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putFloat("cible", entityData.get(CIBLE));
        t.putBoolean("marche", entityData.get(MARCHE));
        t.putInt("facade", entityData.get(FACADE));
    }

    void facade(Direction d) {
        entityData.set(FACADE, d.get2DDataValue());
    }

    /** Part vers le palier dont le plancher est à `deck` (ceux qui sont dessus restent libres de bouger). */
    void allerA(double deck) {
        double cible = deck - PONT;
        if (Math.abs(cible - getY()) < 0.05) {
            return;
        }
        entityData.set(CIBLE, (float) cible);
        entityData.set(MARCHE, true);
        ticksMarche = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2f, 0.55f);
    }

    private void arrivee() {
        entityData.set(MARCHE, false);
        double top = plancher();
        libere = true;
        for (Entity p : List.copyOf(getPassengers())) {                 // ancien mode passager (mondes d'avant la 2.0.1)
            p.stopRiding();
            p.teleportTo(p.getX(), top + 0.02, p.getZ());
        }
        libere = false;
        level().playSound(null, getX(), top, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 0.7f);
    }

    /** Laisse descendre un passager (déconnexion). */
    void liberer(Entity p) {
        libere = true;
        p.stopRiding();
        libere = false;
    }

    /** La descente en marche est refusée (voir AscenseurEvenements), sauf quand c'est la cabine qui libère. */
    boolean descentePermise() {
        return libere || !enMarche();
    }
}
