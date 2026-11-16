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

    /**
     * Une arme : arme TaCZ (couteaux compris), melee et grenades HeraliZ, epee, hache, arc, trident. 15/11 (audit M2) : les
     * armes de melee et les grenades de Tactique sont les objets {@code heralisz_tactical:melee} et
     * {@code heralisz_tactical:throwable} (leur arme exacte est dans l'objet : {@code heralisz_melee:...},
     * {@code heralisz_grenade:...}) ; avant, seuls ces espaces de noms etaient cherches, jamais vus sur un objet : une
     * victime armee d'une machette ou de grenades comptait comme « sans arme ».
     */
    static boolean arme(ItemStack s) {
        if (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || s.getItem() instanceof ProjectileWeaponItem
                || s.getItem() instanceof TridentItem) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return false;
        String ns = id.getNamespace(), path = id.getPath();
        if (ns.equals("tacz")) return path.contains("gun");
        if (ns.equals("heralisz_tactical")) return path.equals("melee") || path.equals("throwable");
        return ns.equals("heralisz_melee") || ns.equals("heralisz_grenade");
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

    private static Method priseSansPurge;
    private static boolean priseCherche;

    /**
     * 15/11 (Purge arretee par le proprietaire) : -1 quand le coeur ne se prend que pendant la Purge ; sinon il se prend a
     * toute heure et c'est le nombre d'heures entre deux prises d'un meme clan (0 : sans limite). CRac
     * ({@code fr.heraliz.crac.api.CoeurApi.priseSansPurgeHeures}, par reflexion) absent ou trop ancien : -1.
     */
    static long priseSansPurgeHeures() {
        if (!priseCherche) {
            priseCherche = true;
            try {
                priseSansPurge = Class.forName("fr.heraliz.crac.api.CoeurApi").getMethod("priseSansPurgeHeures");
            } catch (ReflectiveOperationException | LinkageError e) {
                priseSansPurge = null;
            }
        }
        if (priseSansPurge == null) return -1;
        try {
            return priseSansPurge.invoke(null) instanceof Long l ? l : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static Method cwConfig;
    private static java.lang.reflect.Field cwClansMin;
    private static boolean cwCherche;

    /**
     * Clans differents au minimum pour qu'un ClashWar rapporte des points de saison : le reglage du ClashWar
     * ({@code saison_clans_min} de heraliz_clashwar/clashwar.json, {@code fr.heraliz.clashwar.tournoi.CwConfig}, par
     * reflexion). 15/11 (audit B1) : avant, l'onglet Saison montrait 4 ecrit en dur. ClashWar absent : 4, son defaut.
     */
    static int clashwarClansMin() {
        if (!cwCherche) {
            cwCherche = true;
            try {
                Class<?> c = Class.forName("fr.heraliz.clashwar.tournoi.CwConfig");
                cwConfig = c.getMethod("get");
                cwClansMin = c.getField("saisonClansMin");
            } catch (ReflectiveOperationException | LinkageError e) {
                cwConfig = null;
                cwClansMin = null;
            }
        }
        if (cwConfig == null || cwClansMin == null) return 4;
        try {
            return Math.max(1, cwClansMin.getInt(cwConfig.invoke(null)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 4;
        }
    }

    @Nullable
    static String id(net.minecraft.world.entity.Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? null : id.toString();
    }
}
