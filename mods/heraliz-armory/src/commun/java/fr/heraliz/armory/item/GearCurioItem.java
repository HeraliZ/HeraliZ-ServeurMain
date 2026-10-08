package fr.heraliz.armory.item;

import fr.heraliz.armory.armor.GearDef;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Objet Curios repris de HeralisZ Armor (HzCurioItem) : ghillie (slot body), brassard (bracelet), masque a gaz (mask),
 * pattes d'araignee (body). Ce n'est pas une armure vanilla. Rendu GeckoLib enregistre dans client.ClientSetup.
 */
public class GearCurioItem extends Item implements GeoItem, ICurioItem, GearItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final GearDef gear;

    public GearCurioItem(GearDef gear) {
        super(new Properties().durability(gear.durability()));
        this.gear = gear;
    }

    @Override
    public GearDef gear() {
        return gear;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().equals(gear.curio());
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(GearItem.rarityStyle(gear.rarity()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        GearItem.appendTooltip(gear, lines);
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
    }

    /** Au-dela de 20 blocs, les meches ne s'animent plus (meme valeur que client.Lod.ANIM_RANGE_SQR). */
    private static final double ANIM_RANGE_SQR = 20 * 20;
    /** Distance camera -> entite au carre (client.Lod.distSqr), branchee par la partie client ; les animations ne tournent que la. */
    public static volatile ToDoubleFunction<Entity> DISTANCE_CAMERA = e -> 0;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ghillie.idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("animation.ghillie.move");
    private static final RawAnimation SPRINT = RawAnimation.begin().thenLoop("animation.ghillie.sprint");

    /**
     * Tenues animees (ghillies) : les meches et feuilles respirent au repos, balancent en marchant, battent en sprint.
     * La vitesse vient de l'animation de marche du porteur (fiable aussi pour les autres joueurs).
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (gear.anim() == null) return;
        controllers.add(new AnimationController<>(this, "tenue", 8, state -> {
            Entity e = state.getData(DataTickets.ENTITY);
            if (DISTANCE_CAMERA.applyAsDouble(e) > ANIM_RANGE_SQR) {
                return PlayState.STOP;   // de loin : tenue figee, aucun calcul d'animation
            }
            float speed = e instanceof LivingEntity living ? living.walkAnimation.speed(state.getPartialTick()) : 0;
            if (speed < 0.12F) return state.setAndContinue(IDLE);
            return state.setAndContinue(e.isSprinting() && speed > 0.5F ? SPRINT : MOVE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
