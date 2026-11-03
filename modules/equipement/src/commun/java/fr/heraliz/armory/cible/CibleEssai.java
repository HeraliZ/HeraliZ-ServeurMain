package fr.heraliz.armory.cible;

import fr.heraliz.armory.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Cible d'essai (03/11, demande du propriétaire) : un mannequin immobile qui encaisse les coups EXACTEMENT comme un joueur
 * (mêmes réglages des armes, même tête, mêmes armures, même plafond de 60 %), pour que les joueurs testent leurs armes et
 * que le staff équilibre. Elle ne meurt jamais et ne perd aucun point de vie : chaque coup est mesuré, puis annulé, et le
 * tireur reçoit les chiffres (dégâts de l'arme, part prise par l'armure, tête ou corps, distance, arme).
 * <ul>
 *    <li>porte les vrais objets de la tenue choisie ({@link Tenues}) dans les vrais emplacements (armure et Curios) ;</li>
 *    <li>même taille qu'un joueur (0,6 x 1,8, yeux à 1,62), même corps en morceaux pour les balles (tête comprise) ;</li>
 *    <li>clic droit main vide (ou accroupi) : écran du choix de l'armure ;</li>
 *    <li>aucun recul, aucun effet (saignement, poison…), jamais en feu, poussée par personne.</li>
 * </ul>
 * Partie commune : état synchronisé (tenue, casque) et comportement lu des deux côtés. La logique (coups mesurés, tenue
 * enfilée, sauvegarde, écran) est dans {@code CibleEssaiServeur}, créée par l'usine (ModEntities.CIBLE_USINE).
 */
public class CibleEssai extends LivingEntity {
    public static final EntityDataAccessor<String> TENUE = SynchedEntityData.defineId(CibleEssai.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Boolean> CASQUE = SynchedEntityData.defineId(CibleEssai.class, EntityDataSerializers.BOOLEAN);
    /** Points de vie d'un joueur : base du « tué en N tirs ». */
    public static final float PV_JOUEUR = 20.0F;
    /** Yeux à la hauteur de ceux d'un joueur debout. */
    public static final float YEUX = 1.62F;

    private final NonNullList<ItemStack> armure = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> mains = NonNullList.withSize(2, ItemStack.EMPTY);

    public CibleEssai(EntityType<? extends CibleEssai> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TENUE, Tenues.AUCUNE);
        this.entityData.define(CASQUE, true);
    }

    public String tenue() {
        return this.entityData.get(TENUE);
    }

    public boolean casque() {
        return this.entityData.get(CASQUE);
    }

    // ------------------------------------------------------------------ équipement (comme un porte-armure)
    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armure;
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return mains;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> mains.get(slot.getIndex());
            case ARMOR -> armure.get(slot.getIndex());
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND -> mains.set(slot.getIndex(), stack);
            case ARMOR -> armure.set(slot.getIndex(), stack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // ------------------------------------------------------------------ immobile, comme un joueur debout
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return YEUX;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Aucun recul : elle reste où elle a été posée (les dégâts, eux, sont ceux d'un joueur). */
    @Override
    public void knockback(double force, double x, double z) {
    }

    /** Aucun effet : un saignement ou un poison ferait des dégâts qui ne viennent pas du coup mesuré. */
    @Override
    public boolean canBeAffected(MobEffectInstance effet) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    /** Corps toujours tourné comme la cible (comme un porte-armure) : la forme visée ne bouge pas. */
    @Override
    protected float tickHeadTurn(float corps, float pas) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.getYRot();
        return 0.0F;
    }

    @Override
    public void setYBodyRot(float rot) {
        this.yBodyRotO = this.yRotO = rot;
        this.yHeadRotO = this.yHeadRot = rot;
    }

    @Override
    public void setYHeadRot(float rot) {
        this.yBodyRotO = this.yRotO = rot;
        this.yHeadRotO = this.yHeadRot = rot;
    }

    // ------------------------------------------------------------------ coups
    /** Monde du joueur (ou sans partie serveur) : aucun coup. La partie serveur mesure les coups (CibleEssaiServeur). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** /kill : la cible disparaît (sans rien rendre). */
    @Override
    public void kill() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    // ------------------------------------------------------------------ clic droit
    /**
     * Clic droit main vide, ou accroupi avec n'importe quel objet : écran du choix de l'armure. Avec une arme en main (sans
     * s'accroupir), le clic garde son usage (une arme TaCZ vise, une arme de mêlée frappe fort).
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        if (!player.getMainHandItem().isEmpty() && !player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer joueur) ouvrir(joueur);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Écran du choix de l'armure (serveur). Neutre ici ; partie serveur : CibleEssaiServeur. */
    void ouvrir(ServerPlayer joueur) {
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.CIBLE.get());
    }
}
