package fr.heraliz.armory.armor;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.compat.TaczCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Degats et bonus des objets portes (0.9.0 : un seul calcul pour l'Armurerie et les objets repris de HeralisZ Armor).
 * <ul>
 *   <li>balles et projectiles (balles TaCZ comprises), explosions, coups de joueurs (melee), attaques de creatures
 *   (morsure) ; irradiation (degats magiques sous l'effet geckolib:irradiated) ;</li>
 *   <li>une seule protection par objet, la meme pour tous ces degats (0.15.0) ; tir a la tete (TaCZ) : seules les pieces
 *   de tete comptent ; autres coups : seules les pieces du corps ; une combinaison compte partout ;</li>
 *   <li>les % s'additionnent, un seul plafond commun : {@link Protection#CAP} (radiation 100 %, Hazmat seulement) ;</li>
 *   <li>les objets Curios s'usent d'un point quand ils protegent (les pieces d'armure s'usent comme en vanilla).</li>
 * </ul>
 * Bonus (repris de HeralisZ Armor, appliques toutes les 10 ticks aux joueurs) : filtre (masques a gaz, Hazmat) = effets
 * de la config bloques et retires ; radiation totale de 100 % = irradiation bloquee et retiree ; gel (combinaisons
 * contre le froid) = degats de gel annules et givre retire ; camouflage (ghillies) = effet geckolib:antizombie.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class ProtectionEvents {
    private ProtectionEvents() {}

    /** Balles TaCZ (bullet, bullet_ignore_armor, bullet_void...) : hors du tag vanilla is_projectile. */
    private static final TagKey<DamageType> TACZ_BULLETS = TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullets"));
    private static final ResourceLocation IRRADIATED = new ResourceLocation("geckolib", "irradiated");
    private static final ResourceLocation ANTIZOMBIE = new ResourceLocation("geckolib", "antizombie");

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        List<Protection.Worn> worn = Protection.worn(victim);
        if (worn.isEmpty()) return;
        DamageSource src = event.getSource();
        Protection.Kind kind = kind(victim, src);
        if (kind == null) return;
        boolean headshot = kind == Protection.Kind.BULLET && TaczCompat.headshot(victim);
        double mult = kind == Protection.Kind.EXPLOSION ? ArmorConfig.EXPLOSION_MULTIPLIER.get() : ArmorConfig.PROTECTION_MULTIPLIER.get();
        float total = 0;
        for (Protection.Worn w : worn) {
            float v = kind == Protection.Kind.RADIATION ? w.def().radiation() : Protection.value(w.def(), kind) * Protection.share(w.def(), headshot);
            if (v <= 0) continue;
            total += v;
            if (w.curio() && w.stack().isDamageableItem() && kind != Protection.Kind.RADIATION) w.stack().hurtAndBreak(1, victim, e -> {});
        }
        total *= (float) mult;
        if (total <= 0) return;
        float cap = kind == Protection.Kind.RADIATION ? 100.0F : Protection.CAP;
        event.setAmount(event.getAmount() * Math.max(0.0F, 1.0F - Math.min(cap, total) / 100.0F));
    }

    /** Famille du degat, ou null si nos objets n'en protegent pas (chute, feu, noyade...). */
    private static Protection.Kind kind(LivingEntity victim, DamageSource src) {
        Entity direct = src.getDirectEntity();
        if (src.is(DamageTypes.MAGIC)) {
            MobEffect irradiated = ForgeRegistries.MOB_EFFECTS.getValue(IRRADIATED);
            return irradiated != null && victim.hasEffect(irradiated) ? Protection.Kind.RADIATION : null;
        }
        if (direct instanceof Projectile || src.is(DamageTypeTags.IS_PROJECTILE) || src.is(TACZ_BULLETS) || extraBullet(src)) {
            return Protection.Kind.BULLET;
        }
        if (src.is(DamageTypeTags.IS_EXPLOSION)) return Protection.Kind.EXPLOSION;
        if (direct instanceof Player) return Protection.Kind.MELEE;
        if (direct instanceof LivingEntity) return Protection.Kind.BITE;
        return null;
    }

    private static boolean extraBullet(DamageSource src) {
        List<? extends String> extra = ArmorConfig.BULLET_DAMAGE_TYPES.get();
        if (extra.isEmpty()) return false;
        var key = src.typeHolder().unwrapKey();
        return key.isPresent() && extra.contains(key.get().location().toString());
    }

    /** Radiation totale (avec le multiplicateur de la config) : 100 = immunite a l'irradiation. */
    private static boolean radiationProof(List<Protection.Worn> worn) {
        return Protection.sum(worn, Protection.Kind.RADIATION, false) * ArmorConfig.PROTECTION_MULTIPLIER.get() >= 100.0;
    }

    /**
     * Filtre porte (masque a gaz, masque NRBC, Hazmat) : les effets de la liste de config ne s'appliquent pas.
     * Radiation totale de 100 % : l'irradiation ne s'applique pas.
     */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        var id = ForgeRegistries.MOB_EFFECTS.getKey(event.getEffectInstance().getEffect());
        if (id == null) return;
        boolean gas = ArmorConfig.GAS_EFFECTS.get().contains(id.toString());
        boolean rad = id.equals(IRRADIATED);
        if (!gas && !rad) return;
        List<Protection.Worn> worn = Protection.worn(event.getEntity());
        if (worn.isEmpty()) return;
        if (gas && Protection.any(worn, Guard::gas) || rad && radiationProof(worn)) event.setResult(Event.Result.DENY);
    }

    /** Immunite au gel (combinaisons contre le froid) : les degats de gel n'atteignent pas le porteur. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !event.getSource().is(DamageTypeTags.IS_FREEZING)) return;
        if (Protection.any(Protection.worn(e), Guard::cold)) event.setCanceled(true);
    }

    /** Bonus continus des joueurs, toutes les 10 ticks (comme HeralisZ Armor). */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % 10 != 0) return;
        List<Protection.Worn> worn = Protection.worn(player);
        if (worn.isEmpty()) return;
        if (Protection.any(worn, Guard::cold) && player.getTicksFrozen() > 0) player.setTicksFrozen(0);
        if (Protection.any(worn, Guard::gas)) {
            List<MobEffect> remove = new ArrayList<>();
            for (MobEffectInstance inst : player.getActiveEffects()) {
                var id = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
                if (id != null && ArmorConfig.GAS_EFFECTS.get().contains(id.toString())) remove.add(inst.getEffect());
            }
            remove.forEach(player::removeEffect);
        }
        MobEffect irradiated = ForgeRegistries.MOB_EFFECTS.getValue(IRRADIATED);
        if (irradiated != null && player.hasEffect(irradiated) && radiationProof(worn)) player.removeEffect(irradiated);
        MobEffect antiZombie = ForgeRegistries.MOB_EFFECTS.getValue(ANTIZOMBIE);
        if (antiZombie != null && Protection.any(worn, Guard::camouflage)) {
            player.addEffect(new MobEffectInstance(antiZombie, 40, 0, true, false, false));
        }
    }
}
