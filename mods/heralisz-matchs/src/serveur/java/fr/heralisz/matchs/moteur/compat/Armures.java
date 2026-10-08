package fr.heralisz.matchs.moteur.compat;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenues des matchs : <b>HeraliZ Armurerie</b> ({@code heraliz_armory}). Une tenue {@code kit_<ensemble>} (ou
 * {@code <ensemble>}, ou {@code heraliz_armory:kit_<ensemble>}) donne les pieces {@code heraliz_armory:<ensemble>_<piece>}
 * presentes dans le registre, dans l'ordre tete, visage, torse, gilet, mains, jambes, genoux, pieds ({@link #PIECES}).
 * <p>L'ancien mod heralisz_armor (retire du serveur) n'est plus lu du tout.
 * <p>Tenue inconnue (ancien kit heralisz_armor, faute de frappe) = tenue par defaut {@link #TENUE_DEFAUT}, avec un seul
 * avertissement par id. Resultats gardes en memoire (le registre ne change plus apres le demarrage).
 * <p>Pose des pieces : armure vanilla dans son emplacement s'il est libre, sinon emplacement Curios libre qui l'accepte
 * (visage, gilet, mains, genoux de l'Armurerie), sinon inventaire. Fil serveur.
 */
public final class Armures {
    private Armures() {
    }

    /** Espace de noms de HeraliZ Armurerie. */
    public static final String ARMURERIE = "heraliz_armory";
    /** Tenue donnee a la place d'une tenue inconnue : ensemble Siberie (NV11, militaire russe). */
    public static final String TENUE_DEFAUT = fr.heralisz.matchs.duels.armes.Catalogue.TENUE_DEFAUT;
    /** Pieces d'un ensemble de l'Armurerie, dans l'ordre de pose. */
    public static final List<String> PIECES = List.of("head", "face", "chest", "vest", "hands", "legs", "knees", "feet");

    private static final Map<String, List<String>> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> SIGNALES = ConcurrentHashMap.newKeySet();

    /**
     * Pieces (ids complets) de la tenue {@code kit} ; tenue inconnue = pieces de {@link #TENUE_DEFAUT} (liste vide si
     * l'Armurerie est absente). Jamais null.
     */
    public static List<String> piecesDuKit(String kit) {
        if (kit == null || kit.isBlank()) return List.of();
        String k = kit.trim().toLowerCase(Locale.ROOT);
        List<String> l = CACHE.get(k);
        if (l != null) return l;
        l = piecesArmurerie(k);
        if (l.isEmpty()) {
            List<String> defaut = k.equals(TENUE_DEFAUT) ? List.of() : piecesArmurerie(TENUE_DEFAUT);
            if (SIGNALES.add(k)) {
                if (defaut.isEmpty()) HeraliszMatchs.LOG.warn("[matchs] tenue inconnue : {} (aucune piece donnee)", kit);
                else HeraliszMatchs.LOG.warn("[matchs] tenue inconnue : {} -> tenue par defaut {}", kit, TENUE_DEFAUT);
            }
            l = defaut;
        }
        CACHE.put(k, l);
        return l;
    }

    /** Pieces de l'Armurerie pour cette tenue ({@code kit_<ensemble>} ou {@code <ensemble>}), sans repli ni avertissement. */
    public static List<String> piecesArmurerie(String kit) {
        if (kit == null || kit.isBlank() || !charge(ARMURERIE)) return List.of();
        String id = kit.trim().toLowerCase(Locale.ROOT);
        int ns = id.indexOf(':');
        if (ns >= 0) id = id.substring(ns + 1);
        String ensemble = id.startsWith("kit_") ? id.substring(4) : id;
        if (ensemble.isEmpty()) return List.of();
        List<String> l = new ArrayList<>();
        for (String piece : PIECES) {
            ResourceLocation rl = ResourceLocation.tryParse(ARMURERIE + ":" + ensemble + "_" + piece);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) l.add(rl.toString());
        }
        return List.copyOf(l);
    }

    private static boolean charge(String mod) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(mod);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Pile d'une piece d'armure (id complet), ou pile vide si inconnue (un seul avertissement par id). */
    public static ItemStack piece(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            if (SIGNALES.add("piece:" + id)) HeraliszMatchs.LOG.warn("[matchs] piece d'armure inconnue ignoree : {}", id);
            return ItemStack.EMPTY;
        }
        return new ItemStack(it);
    }

    /**
     * Pose une piece : emplacement d'armure vanilla libre, sinon Curios, sinon inventaire. La pile doit deja etre
     * marquee (objet prete). Rend vrai si elle a ete placee quelque part.
     */
    public static boolean equiper(ServerPlayer p, ItemStack pile) {
        if (pile.isEmpty()) return false;
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(pile);
        if (slot.getType() == EquipmentSlot.Type.ARMOR && pile.getItem() instanceof net.minecraft.world.item.Equipable
                && p.getItemBySlot(slot).isEmpty()) {
            p.setItemSlot(slot, pile);
            return true;
        }
        if (Compat.equiperCurio(p, pile)) return true;
        return p.getInventory().add(pile);
    }
}
