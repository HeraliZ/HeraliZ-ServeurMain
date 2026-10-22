package fr.heralisz.clans.saison;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Liens par reflexion vers les autres mods HeraliZ (tous facultatifs, absents = regle la plus prudente) :
 * <ul>
 * <li>CRac {@code fr.heraliz.crac.api.CoeurApi.clanAUnCoeur(UUID)} : un clan sans coeur pose ne gagne aucun point ;</li>
 * <li>HeraliZ Loot {@code fr.heraliz.loot.RaretesObjets.de(ItemStack)} -> enum {@code Rarete} (0 Commun .. 5 Unique) ;</li>
 * <li>Curios : emplacements portes par la victime (gilet, masque...).</li>
 * </ul>
 */
final class Ponts {
    private Ponts() {
    }

    private static Method coeur, rarete, curios;
    private static boolean coeurCherche, rareteCherche, curiosCherche;

    /** Le clan a son coeur pose (CRac). Sans CRac : faux (aucun point sans coeur). */
    static boolean aUnCoeur(UUID clan) {
        if (!coeurCherche) {
            coeurCherche = true;
            try {
                coeur = Class.forName("fr.heraliz.crac.api.CoeurApi").getMethod("clanAUnCoeur", UUID.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                HeraliszClans.LOG.warn("[saison] CRac sans CoeurApi : aucun clan ne peut marquer de points");
            }
        }
        if (coeur == null) return false;
        try {
            return Boolean.TRUE.equals(coeur.invoke(null, clan));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Rarete 0..5 de l'objet, -1 si inconnue (ou sans HeraliZ Loot). */
    static int rarete(ItemStack st) {
        if (!rareteCherche) {
            rareteCherche = true;
            try {
                rarete = Class.forName("fr.heraliz.loot.RaretesObjets").getMethod("de", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                rarete = null;
            }
        }
        if (rarete == null || st.isEmpty()) return -1;
        try {
            Object r = rarete.invoke(null, st);
            return r instanceof Enum<?> en ? en.ordinal() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Tout ce que porte la victime : inventaire, armure, main gauche, emplacements Curios. */
    static List<ItemStack> equipement(Player p) {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty()) l.add(s);
        for (EquipmentSlot es : EquipmentSlot.values()) {
            ItemStack s = p.getItemBySlot(es);
            if (!s.isEmpty()) l.add(s);
        }
        l.addAll(curios(p));
        return l;
    }

    private static List<ItemStack> curios(LivingEntity e) {
        List<ItemStack> l = new ArrayList<>();
        if (!curiosCherche) {
            curiosCherche = true;
            try {
                curios = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                curios = null;
            }
        }
        if (curios == null) return l;
        try {
            Object opt = curios.invoke(null, e);
            Object handler = opt.getClass().getMethod("orElse", Object.class).invoke(opt, (Object) null);
            if (handler == null) return l;
            Object items = handler.getClass().getMethod("getEquippedCurios").invoke(handler);
            Method slots = items.getClass().getMethod("getSlots"), get = items.getClass().getMethod("getStackInSlot", int.class);
            int n = (Integer) slots.invoke(items);
            for (int i = 0; i < n; i++) {
                ItemStack s = (ItemStack) get.invoke(items, i);
                if (s != null && !s.isEmpty()) l.add(s);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            // Curios change de version : on fait sans
        }
        return l;
    }

    /** Une arme : arme TaCZ (couteaux compris), melee et grenades HeraliZ, Wonder, epee, hache, arc, trident. */
    static boolean arme(ItemStack s) {
        if (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || s.getItem() instanceof ProjectileWeaponItem
                || s.getItem() instanceof TridentItem) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return false;
        String ns = id.getNamespace(), path = id.getPath();
        if (ns.equals("tacz")) return path.contains("gun");
        return ns.equals("heralisz_melee") || ns.equals("heralisz_grenade") || ns.equals("heraliz_wonder");
    }

    /** Une protection : armure de Minecraft ou piece de l'Armurerie HeraliZ. */
    static boolean protection(ItemStack s) {
        if (s.getItem() instanceof ArmorItem) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && id.getNamespace().equals("heraliz_armory");
    }

    /**
     * La victime est une menace : au moins une arme ou une protection de rarete >= minimum, hors objets exclus (ceux du
     * /kit). Rarete inconnue : l'objet ne compte pas (Commun par defaut).
     */
    static boolean menace(Player victime, int rareteMin, Set<String> exclus) {
        for (ItemStack s : equipement(victime)) {
            if (!arme(s) && !protection(s)) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (id != null && exclus.contains(id.toString())) continue;
            if (rarete(s) >= rareteMin) return true;
        }
        return false;
    }

    @Nullable
    static String id(net.minecraft.world.entity.Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? null : id.toString();
    }
}
