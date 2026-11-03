package fr.heraliz.armory.cible;

import fr.heraliz.armory.armor.Equiper;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.network.PaquetsCible;
import fr.heraliz.armory.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Partie SERVEUR de {@link CibleEssai} (docs/METHODE_CLIENT_SERVEUR.md §5) : coups mesurés, tenue enfilée, écran,
 * sauvegarde. Créée par l'usine ModEntities.CIBLE_USINE ; jamais « new CibleEssai(...) » côté serveur.
 * <p><b>Même calcul qu'un joueur</b> : un coup passe par {@link #hurt}, qui fait d'abord ce que fait Player.hurt (coup nul
 * ignoré, mise à l'échelle selon la difficulté : le serveur est en « facile », les explosions y sont réduites pour un
 * joueur), puis par le calcul de Minecraft et de Forge comme pour tout être vivant (temps d'invincibilité, événements de
 * dégâts : réglages des armes, tête, armures de l'Armurerie avec leur plafond, armure de Minecraft). Les dégâts avant
 * armure sont relevés au tout début de LivingHurtEvent, ceux qu'aurait perdus un joueur au tout début de LivingDamageEvent,
 * qui est alors annulé (CiblesServeur) : la cible ne perd jamais de vie, et rien ne compte ce coup comme des dégâts infligés
 * (statistiques et quêtes de clan, activité) : la cible ne fait rien gagner.</p>
 */
public class CibleEssaiServeur extends CibleEssai {
    /** Distance au plus pour agir sur la cible (écran, ramasser). */
    static final double PORTEE = 6.0D;
    /** Un changement de tenue au plus toutes les 4 ticks (chaque changement renvoie l'équipement à ceux qui la voient). */
    private static final long ECART_CHOIX = 4L;

    /** Joueur qui l'a posée (il peut la ramasser), null si inconnu. */
    @Nullable
    UUID poseur;
    /** Tenue à enfiler au prochain tick (pose, chargement : Curios a alors fini de lire ses cases). */
    private boolean aHabiller = true;
    private long dernierChoix = Long.MIN_VALUE / 2;

    /** Coup mesuré en ce moment (pendant {@link #hurt}) ; lu par CiblesServeur. */
    @Nullable
    Coup enCours;
    /** Balle de TaCZ en cours (de EntityHurtByGunEvent.Pre à .Post) : ses deux parts (normale, perce-armure) s'additionnent. */
    @Nullable
    Coup balle;
    /** Dernier point touché par une balle (mixin), et son tick. */
    private Vec3 point = Vec3.ZERO;
    private boolean pointTete;
    private long pointTick = -1L;

    /** Morceaux du corps (HitboxCible), gardés tant que la cible ne bouge ni ne tourne. */
    @Nullable
    private Matrix4f[] morceaux;
    private double mx = Double.NaN, my, mz;
    private float mLacet;

    public CibleEssaiServeur(EntityType<? extends CibleEssai> type, Level level) {
        super(type, level);
    }

    /** Un coup en cours de mesure. */
    static final class Coup {
        final ServerPlayer tireur;
        final long tick;
        final byte zone;
        final Vec3 point;
        final Component nomArme;
        final long tir;
        @Nullable
        final Entity projectile;
        float avant, recu;
        boolean plafond;

        Coup(ServerPlayer tireur, long tick, byte zone, Vec3 point, Component nomArme, long tir, @Nullable Entity projectile) {
            this.tireur = tireur;
            this.tick = tick;
            this.zone = zone;
            this.point = point;
            this.nomArme = nomArme;
            this.tir = tir;
            this.projectile = projectile;
        }
    }

    // ------------------------------------------------------------------ coups
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {   // /kill, vide : elle disparaît
            remove(RemovalReason.KILLED);
            return false;
        }
        // seuls les coups d'un joueur sont mesurés (pas de feu, de chute, de créature...)
        if (!(source.getEntity() instanceof ServerPlayer tireur) || tireur.isSpectator() || isInvulnerableTo(source)) return false;
        // comme Player.hurt (Minecraft 1.20.1) : difficulté, puis coup nul ignoré
        if (source.scalesWithDifficulty()) {
            Difficulty d = level().getDifficulty();
            if (d == Difficulty.PEACEFUL) amount = 0.0F;
            if (d == Difficulty.EASY) amount = Math.min(amount / 2.0F + 1.0F, amount);
            if (d == Difficulty.HARD) amount = amount * 3.0F / 2.0F;
        }
        if (amount == 0.0F) return false;
        Coup coup = coupDe(source, tireur);
        enCours = coup;
        boolean touche;
        try {
            touche = super.hurt(source, amount);
        } finally {
            enCours = null;
        }
        remettreANeuf();
        // balle de TaCZ : envoyée à la fin de la balle (ses deux parts) ; le reste (mêlée, couteau, explosion) : tout de suite
        if (coup != balle) CiblesServeur.envoyer(this, coup);
        return touche;
    }

    /** Coup d'une balle de TaCZ en cours (même balle, même tick), ou nouveau coup (mêlée, explosion, autre projectile). */
    private Coup coupDe(DamageSource source, ServerPlayer tireur) {
        long t = level().getGameTime();
        Entity direct = source.getDirectEntity();
        if (balle != null && balle.tick == t && direct != null && direct == balle.projectile) return balle;
        byte zone;
        Component nom;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            zone = PaquetsCible.EXPLOSION;
            nom = direct != null && direct != tireur ? direct.getDisplayName() : tireur.getMainHandItem().getHoverName();
        } else {
            zone = direct == tireur ? PaquetsCible.MELEE : PaquetsCible.CORPS;
            nom = tireur.getMainHandItem().getHoverName();
        }
        return new Coup(tireur, t, zone, position().add(0.0D, 1.1D, 0.0D), nom, -1L, null);
    }

    /** Point touché par la balle de ce tick (mixin des balles), sinon le milieu de la zone. */
    Vec3 pointBalle(long tick, boolean tete) {
        if (pointTick == tick && pointTete == tete) return point;
        return position().add(0.0D, tete ? 1.65D : 1.1D, 0.0D);
    }

    /**
     * Balle de TaCZ contre la cible (mixin TaczHitboxCibleMixin, fil du serveur) : le corps en morceaux d'un joueur. Null :
     * balle à côté.
     */
    @Nullable
    public HitboxCible.Touche toucherBalle(Vec3 de, Vec3 a) {
        float lacet = getYRot();
        if (morceaux == null || getX() != mx || getY() != my || getZ() != mz || lacet != mLacet) {
            mx = getX();
            my = getY();
            mz = getZ();
            mLacet = lacet;
            morceaux = HitboxCible.inverses(mx, my, mz, lacet);
        }
        HitboxCible.Touche t = HitboxCible.toucher(morceaux, getBoundingBox(), de, a);
        if (t != null) {
            point = t.point();
            pointTete = t.tete();
            pointTick = level().getGameTime();
        }
        return t;
    }

    /** Les objets Curios s'usent quand ils protègent (ProtectionEvents) : ceux de la cible restent neufs. */
    private void remettreANeuf() {
        for (ItemStack s : getArmorSlots()) if (s.isDamageableItem() && s.getDamageValue() > 0) s.setDamageValue(0);
        CuriosApi.getCuriosInventory(this).ifPresent(inv -> inv.getCurios().values().forEach(h -> {
            IDynamicStackHandler stacks = h.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack s = stacks.getStackInSlot(i);
                if (s.isDamageableItem() && s.getDamageValue() > 0) s.setDamageValue(0);
            }
        }));
    }

    // ------------------------------------------------------------------ tenue
    /** Enfile la tenue (les vrais objets, dans les vrais emplacements) ; tout le reste est retiré. */
    void habiller(String id) {
        Tenues.Tenue t = Tenues.de(id);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) setItemSlot(slot, ItemStack.EMPTY);
        }
        CuriosApi.getCuriosInventory(this).ifPresent(inv -> inv.getCurios().values().forEach(h -> {
            for (IDynamicStackHandler stacks : new IDynamicStackHandler[]{h.getStacks(), h.getCosmeticStacks()}) {
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (!stacks.getStackInSlot(i).isEmpty()) stacks.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }));
        for (PieceDef def : t.pieces()) Equiper.piece(this, def, new ItemStack(ModItems.get(def)));
        entityData.set(TENUE, t.id());
    }

    /** Choix reçu de l'écran : au plus un changement toutes les {@link #ECART_CHOIX} ticks. */
    boolean choisir(String id) {
        long t = level().getGameTime();
        if (t - dernierChoix < ECART_CHOIX || Tenues.index(id) < 0) return false;
        dernierChoix = t;
        habiller(id);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (aHabiller && !level().isClientSide) {
            aHabiller = false;
            habiller(tenue());
        }
    }

    /** Joueur qui la pose (il pourra la ramasser). */
    public void poseePar(UUID joueur) {
        poseur = joueur;
    }

    @Override
    void ouvrir(ServerPlayer joueur) {
        CiblesServeur.ouvrir(joueur, this);
    }

    // ------------------------------------------------------------------ sauvegarde (tenue, poseur ; pas les objets)
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Tenue", tenue());
        if (poseur != null) tag.putUUID("Poseur", poseur);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Tenue")) entityData.set(TENUE, Tenues.de(tag.getString("Tenue")).id());
        poseur = tag.hasUUID("Poseur") ? tag.getUUID("Poseur") : null;
        aHabiller = true;
    }
}
