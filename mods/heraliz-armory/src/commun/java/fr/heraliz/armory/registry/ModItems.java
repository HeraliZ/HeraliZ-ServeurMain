package fr.heraliz.armory.registry;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ArmorCatalog;
import fr.heraliz.armory.armor.GearCatalog;
import fr.heraliz.armory.armor.GearDef;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.SetCatalog;
import fr.heraliz.armory.armor.SetDef;
import fr.heraliz.armory.armor.SuitCatalog;
import fr.heraliz.armory.item.GearArmorItem;
import fr.heraliz.armory.item.GearCurioItem;
import fr.heraliz.armory.item.MannequinItem;
import fr.heraliz.armory.item.SetArmorItem;
import fr.heraliz.armory.item.SetCurioItem;
import fr.heraliz.armory.item.SetKitItem;
import fr.heraliz.armory.item.SuitItem;
import fr.heraliz.armory.pont.Usine;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un item par piece du catalogue (genere), une tenue complete par ensemble (SetCatalog), les combinaisons (SuitCatalog,
 * 0.9.0), les objets repris de HeralisZ Armor (GearCatalog, 0.9.0 : memes chemins, espace de noms heraliz_armory),
 * plus deux onglets creatifs : « HeraliZ Armurerie » et « HeraliZ Armurerie · Event ».
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizArmory.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeralizArmory.MOD_ID);
    public static final Map<String, RegistryObject<Item>> PIECES = new LinkedHashMap<>();
    /** Tenues completes (kit_<ensemble>), par identifiant d'ensemble. */
    public static final Map<String, RegistryObject<Item>> KITS = new LinkedHashMap<>();
    /** Combinaisons integrales (<ensemble>_suit), dans l'ordre du catalogue. */
    public static final Map<String, RegistryObject<Item>> SUITS = new LinkedHashMap<>();
    /** Objets repris de HeralisZ Armor, par identifiant, dans l'ordre de l'ancien catalogue. */
    public static final Map<String, RegistryObject<Item>> GEAR = new LinkedHashMap<>();

    /**
     * Usines des objets qui portent de la logique serveur (docs/METHODE_CLIENT_SERVEUR.md §5.3) : tenue complete (enfiler)
     * et mannequin (pose dans le monde). Versions serveur (SetKitItemServeur, MannequinItemServeur) branchees par la partie
     * serveur, avant l'enregistrement des objets.
     */
    public static final Usine<SetDef, Item> KIT_USINE = new Usine<>(SetKitItem::new);
    public static final Usine<Item.Properties, Item> MANNEQUIN_USINE = new Usine<>(MannequinItem::new);

    static {
        for (PieceDef def : ArmorCatalog.PIECES) {
            PIECES.put(def.itemId(), ITEMS.register(def.itemId(), () -> def.curioSlot() != null
                    ? new SetCurioItem(def, new Item.Properties())
                    : new SetArmorItem(def, new Item.Properties())));
        }
        for (SetDef set : SetCatalog.SETS) {
            if (set.kit()) KITS.put(set.set(), ITEMS.register(set.kitId(), () -> KIT_USINE.apply(set)));
        }
        for (PieceDef def : SuitCatalog.SUITS) {
            SUITS.put(def.itemId(), ITEMS.register(def.itemId(), () -> new SuitItem(def, new Item.Properties())));
        }
        for (GearDef g : GearCatalog.GEAR) {
            GEAR.put(g.id(), ITEMS.register(g.id(), () -> g.armor() ? new GearArmorItem(g) : new GearCurioItem(g)));
        }
    }

    public static final RegistryObject<Item> MANNEQUIN = ITEMS.register("mannequin", () -> MANNEQUIN_USINE.apply(new Item.Properties()));

    /**
     * Onglet « HeraliZ Armurerie » : tenues completes NV1..NV23, combinaisons, pieces par niveau, puis la section des
     * accessoires repris de HeralisZ Armor (masques a gaz, ghillies, brassards), et le mannequin.
     */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("armory", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heraliz_armory"))
            .icon(() -> {
                RegistryObject<Item> first = PIECES.values().stream().reduce((a, b) -> b).orElse(null);
                return first == null ? new ItemStack(Items.IRON_CHESTPLATE) : new ItemStack(first.get());
            })
            .displayItems((params, out) -> {
                // tenues completes NV1..NV23, puis les combinaisons, puis les pieces par niveau d'ensemble (ordre du
                // catalogue dans un ensemble ; les casques thermiques a part se rangent a leur niveau)
                SetCatalog.SETS.stream().filter(set -> set.kit() && !set.pieces().isEmpty()).sorted(Comparator.comparingInt(SetDef::level))
                        .forEach(set -> out.accept(KITS.get(set.set()).get()));
                SUITS.values().forEach(ro -> out.accept(ro.get()));
                ArmorCatalog.PIECES.stream().filter(def -> def.tier() > 0).sorted(Comparator.comparingInt(def -> SetDef.sortKey(def.set())))
                        .forEach(def -> out.accept(PIECES.get(def.itemId()).get()));
                GearCatalog.GEAR.stream().filter(g -> !g.event()).forEach(g -> out.accept(GEAR.get(g.id()).get()));
                out.accept(MANNEQUIN.get());
            })
            .build());

    /** Onglet « HeraliZ Armurerie · Event » : les objets event repris de HeralisZ Armor, juste apres l'onglet principal. */
    public static final RegistryObject<CreativeModeTab> EVENT_TAB = TABS.register("event", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heraliz_armory.event"))
            .withTabsBefore(HeralizArmory.id("armory"))
            .icon(() -> {
                RegistryObject<Item> icon = GEAR.get("pumpkin_head");
                return icon == null ? new ItemStack(Items.CARVED_PUMPKIN) : new ItemStack(icon.get());
            })
            .displayItems((params, out) -> GearCatalog.GEAR.stream().filter(GearDef::event).forEach(g -> out.accept(GEAR.get(g.id()).get())))
            .build());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    public static Item get(PieceDef def) {
        RegistryObject<Item> ro = PIECES.get(def.itemId());
        return (ro != null ? ro : SUITS.get(def.itemId())).get();
    }
}
