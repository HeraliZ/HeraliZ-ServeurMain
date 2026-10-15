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

/**
 * Cible d'entrainement : adversaire immobile de {@code /duel entrainement} (MATCHS_SPEC §6, banc d'essai sans second
 * joueur). Silhouette humaine a la texture maison (mannequin), sans IA, sans son ni butin. Elle ne prend des degats que
 * pendant le combat de sa partie ; a « 0 PV » elle n'est jamais tuee : la partie la compte eliminee
 * (DuelPartie.eliminerCible, serveur) puis la replace. Hors partie (chargement d'un monde), elle disparait.
 */
public class CibleEntrainement extends PathfinderMob {
    /** Etiquette d'entite (tirs TaCZ suivis, nettoyage). */
    public static final String ETIQUETTE = "heralisz_duel_cible";
    @Nullable
    String partieId;

    public CibleEntrainement(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setPersistenceRequired();
        addTag(ETIQUETTE);
    }

    public static AttributeSupplier.Builder attributs() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Rattache la cible a sa partie. */
    public void partie(String id) {
        this.partieId = id;
    }

    // tick, dégâts et « mort » : version serveur (CibleEntrainementServeur, rattachée à sa partie de duel) ;
    // sans partie serveur, aucune partie n'existe : la cible disparaît, ne prend aucun dégât, ne meurt jamais.
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && !rattachee()) discard();
    }

    /** Vrai si la cible a encore sa partie de duel (version serveur) ; ici, jamais : elle disparait. */
    protected boolean rattachee() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        setHealth(getMaxHealth());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * La partie est gardee avec l'entite : si son troncon est decharge puis recharge pendant le duel (cible posee loin du
     * joueur), elle reste rattachee au lieu de disparaitre ; une partie finie la retire toujours au tick suivant.
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (partieId != null) tag.putString("HeraliszPartie", partieId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HeraliszPartie")) partieId = tag.getString("HeraliszPartie");
    }
}
