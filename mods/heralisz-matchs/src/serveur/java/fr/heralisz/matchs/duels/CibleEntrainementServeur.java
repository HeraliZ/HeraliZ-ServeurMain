package fr.heralisz.matchs.duels;

import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

/** Version serveur de la cible d'entraînement (docs/METHODE_CLIENT_SERVEUR.md §5.1) : rattachée à sa partie de duel. */
public class CibleEntrainementServeur extends CibleEntrainement {
    public CibleEntrainementServeur(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Nullable
    private DuelPartie partie() {
        if (partieId == null) return null;
        Partie p = MatchApi.partie(partieId);
        return p instanceof DuelPartie d ? d : null;
    }


    /** Gardee tant que sa partie existe (avant, le tick commun la retirait toujours des la premiere seconde). */
    @Override
    protected boolean rattachee() {
        return partie() != null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        DuelPartie d = partie();
        if (d == null || !d.cibleVulnerable()) return false;
        Entity e = source.getEntity();
        if (!(e instanceof Player)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // jamais de vraie mort : la partie compte l'elimination et replace la cible
        setHealth(getMaxHealth());
        DuelPartie d = partie();
        if (d != null) d.eliminerCible(this, source.getEntity() instanceof LivingEntity l ? l : null, source);
    }

}
