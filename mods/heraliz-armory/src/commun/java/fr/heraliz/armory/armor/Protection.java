package fr.heraliz.armory.armor;

import fr.heraliz.armory.item.ProtectiveItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcul des protections (0.15.0, choix du proprietaire du 14/10) : une seule protection par objet, la meme contre les
 * balles, les explosions, le corps a corps et les morsures (valeurs ecrites par outils/equilibrage_armures.py). Les %
 * des objets portes s'additionnent, plafonnes a {@link #CAP} (radiation : 100 %, Hazmat seulement). Tir a la tete : seuls
 * le casque et le visage comptent ; autres coups : seules les pieces du corps. Une combinaison compte en entier partout.
 */
public final class Protection {
    private Protection() {}

    /** Plafond : une tenue Unique complete (60 % au corps, 50 % a la tete) ne peut pas etre depassee. */
    public static final float CAP = 60.0F;
    /** Part des pieces de tete sur un coup au corps (0.15.0 : aucune, chaque tenue atteint son total avec le corps seul). */
    public static final float HEAD_SHARE = 0.0F;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Types de degat geres. */
    public enum Kind { BULLET, MELEE, BITE, EXPLOSION, RADIATION }

    public record Worn(Guard def, ItemStack stack, boolean curio) {}

    /** Objets protecteurs de l'Armurerie portes (slots d'armure et slots Curios). */
    public static List<Worn> worn(LivingEntity entity) {
        List<Worn> list = new ArrayList<>(8);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof ProtectiveItem p && p.wornArmorSlot() == slot) list.add(new Worn(p.guard(), stack, false));
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getCurios().forEach((id, handler) -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStacks().getStackInSlot(i);
                if (stack.getItem() instanceof ProtectiveItem p && id.equals(p.wornCurioSlot())) list.add(new Worn(p.guard(), stack, true));
            }
        }));
        return list;
    }

    /** % brut de l'objet pour ce type de degat. */
    public static float value(Guard p, Kind kind) {
        return switch (kind) {
            case BULLET -> p.bullet();
            case MELEE -> p.melee();
            case BITE -> p.bite();
            case EXPLOSION -> p.explosion();
            case RADIATION -> p.radiation();
        };
    }

    /**
     * Part d'un objet dans un coup : 1 s'il couvre la zone touchee, sinon {@link #HEAD_SHARE} (tete sur un coup au
     * corps) ou 0 (corps sur un tir a la tete) ; une combinaison couvre tout (1 partout).
     */
    public static float share(Guard p, boolean headshot) {
        if (p.wholeBody()) return 1.0F;
        return headshot ? (p.protectsHead() ? 1.0F : 0.0F) : (p.protectsHead() ? HEAD_SHARE : 1.0F);
    }

    /** Somme non plafonnee des % pour un coup (la radiation compte tous les objets en entier). */
    public static float sum(List<Worn> worn, Kind kind, boolean headshot) {
        float total = 0;
        for (Worn w : worn) {
            if (kind == Kind.RADIATION) total += usee(w.stack()) ? 0 : w.def().radiation();
            else total += value(w.def(), kind) * share(w.def(), headshot);
        }
        return total;
    }

    /** Tenue complete d'un ensemble (infobulle du kit) : % de protection (corps, tete). */
    public static float[] setBullet(List<PieceDef> pieces) {
        float b = 0, h = 0;
        for (PieceDef p : pieces) {
            b += p.bullet() * share(p, false);
            h += p.bullet() * share(p, true);
        }
        return new float[]{Math.min(CAP, b), Math.min(CAP, h)};
    }

    /**
     * 0.16.0 (23/10) : objet « use » = a 1 point de la casse (barre vide). Il ne protege plus du gaz, de la radiation ni du
     * froid (il s'use dans les zones dangereuses de HeraliZ Decors, meme regle : ZonesServeur.usee) ; il protege toujours
     * des balles. Jamais casse par cette usure : il faut le reparer.
     */
    public static boolean usee(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** Vrai si un objet porte NON USE a ce filtre (gaz, gel) : un objet use ne filtre plus. */
    public static boolean anyFiltre(List<Worn> worn, java.util.function.Predicate<Guard> bonus) {
        for (Worn w : worn) if (bonus.test(w.def()) && !usee(w.stack())) return true;
        return false;
    }

    /** Vrai si un objet porte a ce bonus (filtre, gel, camouflage). */
    public static boolean any(List<Worn> worn, java.util.function.Predicate<Guard> bonus) {
        for (Worn w : worn) if (bonus.test(w.def())) return true;
        return false;
    }
}
