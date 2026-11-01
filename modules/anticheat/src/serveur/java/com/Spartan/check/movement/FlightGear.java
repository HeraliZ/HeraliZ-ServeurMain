package com.Spartan.check.movement;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Équipement de vol légitime, porté (armure), tenu en main ou dans un slot Curios.
 *
 * - flyHoverItems (identifiants exacts : jetpacks…) : peut voler sur place et
 *   monter → Fly, Vertical et NoFall ignorent le joueur, Speed aussi en l'air.
 * - flyExemptKeywords (mots-clés : parachute, planeur…) : ralentit seulement la
 *   chute → Fly tolère une descente très lente, mais ni vol stationnaire ni montée.
 *
 * Un parachute DÉPLOYÉ sous forme d'entité chevauchée est déjà exempté (monture).
 */
public final class FlightGear {
    private static Method curiosInventory;   // CuriosApi.getCuriosInventory(LivingEntity)
    private static Method equippedCurios;    // ICuriosItemHandler.getEquippedCurios()
    private static boolean curiosResolved;

    private FlightGear() {
    }

    /**
     * Objets de flyHoverItems et de flyExemptKeywords, cherchés une fois dans le registre des objets (2.9.5, perf) et refaits
     * seulement quand la liste du réglage change. Avant, à chaque paquet de mouvement de chaque joueur : deux listes neuves
     * du réglage, l'identifiant de chacun des 6 objets portés recopié en texte deux fois, et les cases Curios lues par
     * réflexion deux fois (flyHoverItems : jetpacks de Mekanism, absents du serveur). Même résultat : un objet compte si et
     * seulement si son identifiant est dans la liste (ou contient un mot-clé) ; sans aucun objet qui corresponde (jetpacks),
     * ni l'équipement ni les cases Curios ne sont lus. Fil serveur.
     */
    private static Objets survol = Objets.AUCUN;
    private static Objets ralentissent = Objets.AUCUN;

    public static boolean hovers(ServerPlayer p) {
        List<String> ids = SpartanConfig.getFlyHoverItems();
        if (ids.isEmpty()) {
            return false;
        }
        Objets o = survol;
        if (o.reglage != ids) {
            o = Objets.chercher(ids, id -> ids.contains(id));
            survol = o;
        }
        return anyEquipped(p, o.objets);
    }

    public static boolean slowsFall(ServerPlayer p) {
        List<String> keywords = SpartanConfig.getFlyExemptKeywords();
        if (keywords.isEmpty()) {
            return false;
        }
        Objets o = ralentissent;
        if (o.reglage != keywords) {
            o = Objets.chercher(keywords, id -> keywords.stream().anyMatch(id::contains));
            ralentissent = o;
        }
        return anyEquipped(p, o.objets);
    }

    private interface IdTest {
        boolean test(String id);
    }

    /** Les objets du registre dont l'identifiant passe le test, pour une liste du réglage donnée. */
    private static final class Objets {
        static final Objets AUCUN = new Objets(null, Set.of());
        final List<String> reglage;
        final Set<Item> objets;

        private Objets(List<String> reglage, Set<Item> objets) {
            this.reglage = reglage;
            this.objets = objets;
        }

        static Objets chercher(List<String> reglage, IdTest test) {
            Set<Item> trouves = new HashSet<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                if (key != null && test.test(key.toString().toLowerCase(Locale.ROOT))) {
                    trouves.add(item);
                }
            }
            return new Objets(reglage, Set.copyOf(trouves));
        }
    }

    private static boolean anyEquipped(ServerPlayer p, Set<Item> objets) {
        if (objets.isEmpty()) {
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (matches(p.getItemBySlot(slot), objets)) {
                return true;
            }
        }
        for (ItemStack stack : curios(p)) {
            if (matches(stack, objets)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ItemStack stack, Set<Item> objets) {
        return !stack.isEmpty() && objets.contains(stack.getItem());
    }

    /** Objets équipés dans les slots Curios (dépendance optionnelle, par réflexion). Sert aussi à l'anti-duplication. */
    public static List<ItemStack> curios(ServerPlayer p) {
        if (!resolveCurios()) {
            return List.of();
        }
        try {
            Object lazy = curiosInventory.invoke(null, p);
            if (!(lazy instanceof LazyOptional<?> opt)) {
                return List.of();
            }
            Optional<?> handler = opt.resolve();
            if (handler.isEmpty()) {
                return List.of();
            }
            Object items = equippedCurios.invoke(handler.get());
            if (!(items instanceof IItemHandler h)) {
                return List.of();
            }
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < h.getSlots(); i++) {
                out.add(h.getStackInSlot(i));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    private static synchronized boolean resolveCurios() {
        if (curiosResolved) {
            return curiosInventory != null;
        }
        curiosResolved = true;
        if (!ModList.get().isLoaded("curios")) {
            return false;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            curiosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            equippedCurios = handler.getMethod("getEquippedCurios");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            curiosInventory = null;
            Spartan.LOGGER.warn("[Spartan] Curios présent mais API non reconnue : slots Curios ignorés ({})", e.toString());
            return false;
        }
    }
}
