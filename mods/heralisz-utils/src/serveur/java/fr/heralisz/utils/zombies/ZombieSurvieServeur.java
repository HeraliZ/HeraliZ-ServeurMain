package fr.heralisz.utils.zombies;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Version serveur du zombie de la survie (24/10, performances ; créée par {@link ZombiesSurvie#USINE}, jamais par
 * {@code new}). Même zombie de près ; ce qui coûtait sans rien apporter sur la carte est retiré ou espacé
 * (section {@code [ia]} de heraliz_zombies-server.toml, {@code actif = false} : zombie de Minecraft) :
 * <ul>
 * <li><b>objectifs</b> : ni villageois, ni golem, ni tortue à attaquer (recherches d'entités dans une boîte de 70 blocs),
 * ni oeufs de tortue à piétiner (49 x 49 x 7 blocs lus toutes les 10 à 20 s), ni « traverser le village » la nuit : sur la
 * carte, lits, tonneaux, chaudrons et pupitres font des villages pour Minecraft ; chaque zombie inoccupé y tirait 10 points
 * au hasard, cherchait un lit ou un poste autour de chacun, puis calculait un chemin, toutes les 2 ticks ;</li>
 * <li><b>chemins</b> : recalculés moins souvent quand la cible est loin ou hors d'atteinte ({@link NavigationZombie}) ;</li>
 * <li><b>loin de tout joueur</b> ({@code distance_reduite}) et sans cible : plus de promenade (calcul de chemin toutes
 * les 6 s environ) et Zombie Awareness le laisse de côté (sa marque « vient d'agir », la même que la sienne) ; réveil dès
 * qu'il a une cible ou qu'un joueur approche (vérifié toutes les secondes) ;</li>
 * <li><b>portée de suivi</b> bornée ({@code suivi_max}) : le bonus de difficulté de Minecraft (« Random zombie-spawn
 * bonus ») pouvait la porter à 87 blocs (poursuites et chemins à travers toute une ville).</li>
 * </ul>
 */
public class ZombieSurvieServeur extends ZombieSurvie {
    /** Marque de Zombie Awareness (ZAUtil.ZA_LAST_ACTION) : tant qu'elle a moins de 300 ticks, il ne traite pas ce zombie. */
    private static final String ZA_DERNIERE_ACTION = "za_last_action";
    private static final String BONUS_SUIVI = "Random zombie-spawn bonus";

    private boolean loin;
    private int prochainControle;
    /** Valeur de la marque de Zombie Awareness posée ici (-1 : aucune), retirée au réveil si personne ne l'a changée. */
    private long marqueZa = -1;

    public ZombieSurvieServeur(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** Réglages lus à la construction (objectifs) : réglages pas encore chargés = optimisations actives. */
    private static boolean iaActive() {
        try {
            return ZombiesConfig.IA_ACTIVE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------------------------------ objectifs

    @Override
    protected void registerGoals() {
        if (!iaActive()) {
            super.registerGoals();
            return;
        }
        // Zombie : + ZombieAttackTurtleEggGoal (4), retiré
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addBehaviourGoals();
    }

    @Override
    protected void addBehaviourGoals() {
        if (!iaActive()) {
            super.addBehaviourGoals();
            return;
        }
        // Zombie : + MoveThroughVillageGoal (6) et cibles villageois, golem de fer, tortue (3, 3, 5), retirés
        goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NavigationZombie(this, level);
    }

    // ------------------------------------------------------------------------------------------ loin de tout joueur

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (loin && getTarget() != null) {   // touché de loin, cible donnée par un autre mod : réveil tout de suite
            reveiller();
            prochainControle = tickCount + 20;
            return;
        }
        if (tickCount < prochainControle) return;
        prochainControle = tickCount + 20;
        boolean eloigne;
        try {
            eloigne = ZombiesConfig.IA_ACTIVE.get() && getTarget() == null
                    && !level().hasNearbyAlivePlayer(getX(), getY(), getZ(), ZombiesConfig.IA_DISTANCE.get());
        } catch (IllegalStateException e) {
            eloigne = false;
        }
        if (eloigne) endormir();
        else if (loin) reveiller();
    }

    private void endormir() {
        loin = true;
        goalSelector.disableControlFlag(Goal.Flag.MOVE);
        CompoundTag pd = getPersistentData();
        long now = level().getGameTime();
        // rafraîchie toutes les secondes : Zombie Awareness compte 300 ticks depuis la marque
        pd.putLong(ZA_DERNIERE_ACTION, now);
        marqueZa = now;
    }

    private void reveiller() {
        loin = false;
        goalSelector.enableControlFlag(Goal.Flag.MOVE);
        CompoundTag pd = getPersistentData();
        if (marqueZa >= 0 && pd.getLong(ZA_DERNIERE_ACTION) == marqueZa) pd.remove(ZA_DERNIERE_ACTION);
        marqueZa = -1;
    }

    /** Minecraft remet les drapeaux d'objectifs toutes les 5 ticks (Mob.tick) : la promenade reste coupée tant qu'il est loin. */
    @Override
    protected void updateControlFlags() {
        super.updateControlFlags();
        if (loin) goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    // ------------------------------------------------------------------------------------------ portée de suivi

    @Override
    protected void handleAttributes(float multiplicateur) {
        super.handleAttributes(multiplicateur);
        bornerSuivi();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        bornerSuivi();   // zombies posés avant cette version
    }

    /** Retire le bonus de difficulté de la portée de suivi s'il la porte au-delà de suivi_max (le reste ne change pas). */
    private void bornerSuivi() {
        double max;
        try {
            if (!ZombiesConfig.IA_ACTIVE.get()) return;
            max = ZombiesConfig.IA_SUIVI_MAX.get();
        } catch (IllegalStateException e) {
            return;
        }
        AttributeInstance a = getAttribute(Attributes.FOLLOW_RANGE);
        if (max <= 0 || a == null || a.getValue() <= max) return;
        List<AttributeModifier> bonus = new ArrayList<>();
        for (AttributeModifier m : a.getModifiers()) if (BONUS_SUIVI.equals(m.getName())) bonus.add(m);
        for (AttributeModifier m : bonus) a.removeModifier(m.getId());
    }
}
