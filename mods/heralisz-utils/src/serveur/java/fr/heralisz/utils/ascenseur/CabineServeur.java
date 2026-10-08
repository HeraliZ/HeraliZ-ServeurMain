package fr.heralisz.utils.ascenseur;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cabine de l'ascenseur côté serveur (créée par Ascenseur.CABINE_USINE dans un monde serveur ; code d'origine déplacé
 * tel quel) : départ vers un palier (les joueurs debout montent à bord), déplacement en douceur, arrivée (chacun reposé
 * sur le plancher), descente refusée en marche, sauvegarde.
 */
public class CabineServeur extends Cabine {
    public CabineServeur(EntityType<? extends Cabine> type, Level level) {
        super(type, level);
    }

    /** Partie serveur du tick d'origine (après super.tick() et la branche client). */
    @Override
    void tickServeur() {
        if (enMarche()) {
            double c = entityData.get(CIBLE), y = getY(), d = c - y;
            double v = Math.min(VMAX * Math.min(1.0, ++ticksMarche / 20.0), Math.max(0.03, Math.abs(d) * 0.1));
            if (Math.abs(d) <= v) {
                setPos(getX(), c, getZ());
                arrivee();
            } else {
                setPos(getX(), y + Math.signum(d) * v, getZ());
            }
            if (ticksMarche % 30 == 1) {
                level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.8f, 0.5f);
            }
        }
    }

    private static final double VMAX = 0.2;                 // 4 blocs par seconde

    private int ticksMarche;
    boolean libere;

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

    /** Part vers le palier dont le plancher est à `deck` ; les joueurs debout sur la cabine montent à bord. */
    void allerA(double deck) {
        double cible = deck - PONT;
        if (Math.abs(cible - getY()) < 0.05) {
            return;
        }
        AABB dessus = getBoundingBox().deflate(0.1, 0, 0.1).expandTowards(0, 2.2, 0);
        for (Player p : level().getEntitiesOfClass(Player.class, dessus, p -> !p.isPassenger() && !p.isSpectator())) {
            p.startRiding(this, true);
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
        for (Entity p : List.copyOf(getPassengers())) {
            Vec3 pos = p.position();
            p.stopRiding();
            p.teleportTo(pos.x, top + 0.02, pos.z);
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
