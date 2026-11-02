package fr.heralisz.utils.zombies;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Zombie de la survie côté serveur (01/11) : créé par {@link ZombiesSurvie#ZOMBIE_USINE} dans les mondes du serveur
 * (docs/METHODE_CLIENT_SERVEUR.md §4) ; le jeu des joueurs garde {@link ZombieSurvie}. Même entité, même identifiant
 * ({@code heralisz_utils:zombie_survie}), mêmes données gardées : sa navigation change ({@link NavigationZombie}, chemin
 * gardé vers une cible loin ou hors d'atteinte), et il n'apparaît jamais équipé (03/11). Toujours le créer par son type
 * ({@code ZombiesSurvie.ZOMBIE.get().create}).
 */
public class ZombieSurvieServeur extends ZombieSurvie {
    /** Les six emplacements (mains et armure) ; tableau gardé : {@code values()} en recopie un à chaque appel. */
    private static final EquipmentSlot[] EMPLACEMENTS = EquipmentSlot.values();
    /**
     * Chance de lâcher un objet au-delà de laquelle il a été ramassé (Minecraft la met à 2 pour un objet pris au sol, qui
     * tombe toujours à la mort) ; l'équipement donné à l'apparition reste sous 1 (0,085 ; 0 pour la citrouille).
     */
    private static final float RAMASSE = 1.0F;

    public ZombieSurvieServeur(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NavigationZombie(this, level);
    }

    /**
     * Aucun zombie n'apparaît avec un objet en main ni une armure (03/11, retour du propriétaire : « un zombie a spawn avec
     * une pelle en fer »). C'était Minecraft : à l'apparition ({@code Zombie.finalizeSpawn}), un zombie reçoit une épée ou,
     * 2 fois sur 3, une pelle en fer (1 fois sur 100 en facile, 5 en difficile), une armure selon la difficulté de
     * l'endroit, et le 31 octobre une citrouille sur la tête 1 fois sur 4. Toutes les apparitions de ce zombie passent par
     * ici : peuplement de la carte, zombie de Minecraft remplacé à son apparition naturelle, renforts, vagues des drapeaux,
     * {@code /summon} sans données, œuf. Sa tenue (ville, hazmat, arctique, militaire, prisonnier, police) n'est pas un
     * objet : c'est la texture de son corps ({@link ZombieSurvie#VARIANTE}), elle ne change pas. Jamais de bébé (déjà
     * refusé par {@link ZombieSurvie#setBaby}) : le tirage du bébé est retiré aussi, sinon un zombie adulte pouvait encore
     * monter sur un poulet (« chicken jockey », 1 fois sur 200 environ).
     */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor monde, DifficultyInstance difficulte, MobSpawnType raison,
                                        @Nullable SpawnGroupData donnees, @Nullable CompoundTag tag) {
        if (donnees == null || donnees instanceof Zombie.ZombieGroupData) donnees = new Zombie.ZombieGroupData(false, false);
        SpawnGroupData r = super.finalizeSpawn(monde, difficulte, raison, donnees, tag);
        retirerEquipementApparition();
        return r;
    }

    /** Ni épée, ni pelle, ni armure tirées par Minecraft (le reste de {@code finalizeSpawn} est inchangé). */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource alea, DifficultyInstance difficulte) {
    }

    /**
     * Zombie relu du disque (tronçon rechargé) : celui qui était apparu équipé avant ce correctif perd cet équipement. Un
     * objet ramassé au sol (réglage {@code ramassage}, coupé depuis le 01/11) est gardé : il appartenait à un joueur et
     * tombe à la mort du zombie.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        retirerEquipementApparition();
    }

    /** Vide chaque emplacement dont l'objet n'a pas été ramassé (voir {@link #RAMASSE}). */
    private void retirerEquipementApparition() {
        for (EquipmentSlot s : EMPLACEMENTS) {
            if (!getItemBySlot(s).isEmpty() && getEquipmentDropChance(s) <= RAMASSE) setItemSlot(s, ItemStack.EMPTY);
        }
    }

    /**
     * Un zombie du peuplement ({@link Peuplement#TAG}) n'est jamais écrit dans le monde (02/11, audit mods-2 §11) : quand son
     * tronçon se décharge (joueur téléporté, mort, extraction, déconnexion), il disparaît et sa place revient tout de suite
     * à son secteur ({@link Peuplement}) ; avant, il était gardé sur le disque et revenait au retour d'un joueur, en plus
     * des nouveaux. Les autres zombies sont gardés comme avant.
     */
    @Override
    public boolean shouldBeSaved() {
        return !getTags().contains(Peuplement.TAG) && super.shouldBeSaved();
    }
}
