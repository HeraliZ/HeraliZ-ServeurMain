package fr.heraliz.armory.armor;

import fr.heraliz.armory.item.ProtectiveItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcul des protections : chaque objet porte donne un % par type de degat, les % s'additionnent et sont plafonnes a 75 %
 * (radiation 100 %). Tir a la tete : seules les pieces de tete comptent. Autres coups : pieces du corps + un quart des
 * pieces de tete. Une combinaison compte en entier partout.
 * <p>0.9.0 : un seul calcul et un seul plafond pour tout ce que porte le joueur : pieces et combinaisons de l'Armurerie,
 * objets repris de HeralisZ Armor (ghillies, brassards, masques a gaz, objets event ; explosion = moitie des balles).
 * Partage par les degats (ProtectionEvents) et le panneau de l'inventaire (client).
 */
public final class Protection {
    private Protection() {}

    public static final float CAP = 75.0F;
    public static final float HEAD_SHARE = 0.25F;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Types de degat geres. */
    public enum Kind { BULLET, MELEE, BITE, EXPLOSION, RADIATION }

    public record Worn(Guard def, ItemStack stack, boolean curio) {}

    /** Totaux en % (deja plafonnes) : balles (corps), balles (tete), explosion, melee, morsure, radiation. */
    public record Totals(float bullet, float head, float explosion, float melee, float bite, float radiation) {
        public boolean isEmpty() {
            return bullet <= 0 && head <= 0 && explosion <= 0 && melee <= 0 && bite <= 0 && radiation <= 0;
        }
    }

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
     * Part d'un objet dans un coup : 1 s'il couvre la zone touchee, un quart pour la tete sur un coup au corps ;
     * une combinaison couvre tout (1 partout).
     */
    public static float share(Guard p, boolean headshot) {
        if (p.wholeBody()) return 1.0F;
        return headshot ? (p.protectsHead() ? 1.0F : 0.0F) : (p.protectsHead() ? HEAD_SHARE : 1.0F);
    }

    /** Somme non plafonnee des % pour un coup (la radiation compte tous les objets en entier). */
    public static float sum(List<Worn> worn, Kind kind, boolean headshot) {
        float total = 0;
        for (Worn w : worn) total += kind == Kind.RADIATION ? w.def().radiation() : value(w.def(), kind) * share(w.def(), headshot);
        return total;
    }

    public static Totals totals(List<Worn> worn) {
        float b = sum(worn, Kind.BULLET, false), h = sum(worn, Kind.BULLET, true), e = sum(worn, Kind.EXPLOSION, false);
        float m = sum(worn, Kind.MELEE, false), bite = sum(worn, Kind.BITE, false), rad = sum(worn, Kind.RADIATION, false);
        return new Totals(Math.min(CAP, b), Math.min(CAP, h), Math.min(CAP, e), Math.min(CAP, m), Math.min(CAP, bite), Math.min(100, rad));
    }

    /** Tenue complete d'un ensemble (infobulle du kit) : % balles (corps, tete). */
    public static float[] setBullet(List<PieceDef> pieces) {
        float b = 0, h = 0;
        for (PieceDef p : pieces) {
            b += p.bullet() * share(p, false);
            h += p.bullet() * share(p, true);
        }
        return new float[]{Math.min(CAP, b), Math.min(CAP, h)};
    }

    /** Vrai si un objet porte a ce bonus (filtre, gel, camouflage). */
    public static boolean any(List<Worn> worn, java.util.function.Predicate<Guard> bonus) {
        for (Worn w : worn) if (bonus.test(w.def())) return true;
        return false;
    }
}
