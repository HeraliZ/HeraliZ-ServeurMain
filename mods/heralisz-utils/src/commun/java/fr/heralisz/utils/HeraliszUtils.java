package fr.heralisz.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import fr.heralisz.utils.onglet.OngletGroupe;
import fr.heralisz.utils.pont.Parties;

/**
 * HeralisZ Utils (porteur du jar fusionné HeraliZ Utils 2.0.0 : pont client/serveur, onglet commun) : plans de fabrication d'armes, composants de craft et document de loot bonus, repris de l'ancien
 * onglet « UTILS » d'ArkaniaZ (jar geckolib222). Catalogue : heralisz_utils_catalog.json (tools/gen_utils.py).
 * Retire aussi du jeu les anciens objets ArkaniaZ abandonnés (voir HIDDEN) et renomme « ArkaniaZ » en « HeralisZ »
 * dans les textes des autres mods (traductions de ce mod, chargé après eux).
 */
@Mod(HeraliszUtils.MODID)
public final class HeraliszUtils {
    public static final String MODID = "heralisz_utils";
    public static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final List<RegistryObject<Item>> ALL = new ArrayList<>();
    /** « Bonus de loot » (boutique) : version serveur (clic droit : bonus de loot pour un temps de jeu) branchée par la partie serveur. */
    public static final fr.heralisz.utils.pont.Usine<String, LootBonusDocument> DOCUMENT_USINE =
            new fr.heralisz.utils.pont.Usine<>(LootBonusDocument::new);
    /** Groupes de l'onglet commun « HeraliZ · Utils » (porteur du jar fusionné : voir mods.toml). */
    public static final OngletGroupe ONGLET = new OngletGroupe();
    /** L'onglet commun (heralisz_utils:utils), installé avec son panneau par client.PartieClient. */
    public static final RegistryObject<CreativeModeTab> TAB;

    /**
     * Anciens objets ArkaniaZ retirés des onglets créatifs : tout l'ancien onglet UTILS (repris ici ou abandonné),
     * les anciens soins (remplacés par HeralisZ Soins), et des aliments sans texture.
     */
    private static final Map<ResourceLocation, Set<String>> HIDDEN = Map.of(
            new ResourceLocation("geckolib", "utils"), Set.of("*"),
            new ResourceLocation("geckolib", "food"), Set.of("food_box", "food_gailety", "food_bita", "food_bitaderevo"),
            new ResourceLocation("geckolib", "melee"), Set.of("splint"));

    static {
        for (JsonObject o : catalog()) {
            String id = o.get("id").getAsString(), kind = o.get("kind").getAsString(), rarity = o.get("rarity").getAsString();
            ALL.add(ITEMS.register(id, () -> "document".equals(kind) ? DOCUMENT_USINE.apply(rarity) : new UtilItem(kind, rarity)));
        }
        // onglet commun du jar fusionné « HeraliZ · Utils » (même identifiant heralisz_utils:utils et même titre qu'avant) :
        // un groupe par contenu, filtré par le panneau de gauche côté client (client.onglet.PanneauOnglet).
        ONGLET.ajouter("plans", "onglet.heralisz_utils.plans",
                () -> new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, "plan_m4a1"))),
                output -> ALL.forEach(ro -> output.accept(ro.get())));
        ONGLET.ajouter("carte", "onglet.heralisz_utils.carte", () -> new ItemStack(MapBlocks.SABLE_DESERT.get()),
                output -> MapBlocks.ITEMS.forEach(ro -> output.accept(ro.get())));
        ONGLET.ajouter("gares", "onglet.heralisz_utils.gares",
                () -> new ItemStack(fr.heralisz.utils.train.Train.TABLEAU.get()),
                output -> {
                    output.accept(fr.heralisz.utils.train.Train.TABLEAU.get());
                    output.accept(fr.heralisz.utils.ascenseur.Ascenseur.MACHINE.get());
                    output.accept(fr.heralisz.utils.ascenseur.Ascenseur.PALIER.get());
                });
        // groupes des autres mods du jar, déclarés ICI pour un ordre fixe (FML construit les mods en parallèle : des
        // ajouts faits dans leurs @Mod changeraient d'ordre d'un lancement à l'autre) ; leur contenu est lu par lambdas
        ONGLET.ajouter("vehicules", "onglet.heralisz_utils.vehicules",
                () -> fr.heraliz.vehicules.Contenu.vehicule(fr.heraliz.vehicules.Contenu.VEHICULES.get(0)), output -> {
                    fr.heraliz.vehicules.Contenu.VEHICULES.forEach(v -> output.accept(fr.heraliz.vehicules.Contenu.vehicule(v)));
                    fr.heraliz.vehicules.Contenu.outils().forEach(output::accept);
                });
        ONGLET.ajouter("usure", "onglet.heralisz_utils.usure",
                () -> new ItemStack(fr.heraliz.usure.Contenu.KIT_STANDARD.get()),
                output -> fr.heraliz.usure.Contenu.KITS.forEach(k -> output.accept(k.get())));
        TAB = TABS.register("utils", () -> ONGLET.construire(Component.translatable("itemGroup.heralisz_utils"),
                () -> new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, "plan_m4a1")))));
        // onglet des décors de la map, gardé À PART tel quel : c'est la palette de construction de la carte
        TABS.register("decors", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.heralisz_utils.decors"))
                .icon(() -> new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, "lampadaire_tete"))))
                .displayItems((params, output) -> fr.heralisz.utils.deco.DecoBlocks.ITEMS.forEach(ro -> output.accept(ro.get())))
                .build());
    }

    private static List<JsonObject> catalog() {
        try (Reader r = new InputStreamReader(HeraliszUtils.class.getResourceAsStream("/heralisz_utils_catalog.json"), StandardCharsets.UTF_8)) {
            List<JsonObject> out = new ArrayList<>();
            new Gson().fromJson(r, JsonObject.class).getAsJsonArray("items").forEach(e -> out.add(e.getAsJsonObject()));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("heralisz_utils_catalog.json illisible", e);
        }
    }

    public HeraliszUtils() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        fr.heralisz.utils.deco.DecoBlocks.register(bus);             // décors 3D de la map (avant ITEMS : ils y ajoutent leurs objets)
        fr.heralisz.utils.deco.VentParticules.register(bus);         // particules du vent de la faille (rafales)
        fr.heralisz.utils.ascenseur.Ascenseur.register(bus);         // grand ascenseur de la faille des nains (idem)
        fr.heralisz.utils.train.Train.registres(bus);                // voyageurs du train, panneaux de gare (idem)
        MapBlocks.BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        fr.heralisz.utils.cine.CineReseau.init(bus);                 // cinématique d'arrivée (première connexion, /cinematique)
        fr.heralisz.utils.train.Train.init(bus);                     // réseau ferré : horaire, annonce aux joueurs, /train
        bus.addListener(HeraliszUtils::hideOldItems);
        // ambiance de la map (ciel, nuits, brouillard, filtre d'image) : réglages côté client
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                fr.heralisz.utils.ambiance.AmbianceConfig.SPEC, "heralisz_utils-ambiance.toml");
        // objet « Bonus de loot » (boutique) : bonus et durée, réglages du serveur
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                BonusLootConfig.SPEC, "heralisz_utils-server.toml");
        // parties du jar fusionné (docs/METHODE_CLIENT_SERVEUR.md §4) : logique (serveur) et affichage (client)
        Parties.serveur(MODID, "fr.heralisz.utils.serveur.PartieServeur", bus);
        Parties.client(MODID, "fr.heralisz.utils.client.PartieClient", bus);
    }

    private static void hideOldItems(BuildCreativeModeTabContentsEvent event) {
        Set<String> hidden = HIDDEN.get(event.getTabKey().location());
        if (hidden == null) {
            return;
        }
        List<ItemStack> remove = new ArrayList<>();
        event.getEntries().forEach(e -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getKey().getItem());
            if (hidden.contains("*") || id != null && hidden.contains(id.getPath())) {
                remove.add(e.getKey());
            }
        });
        remove.forEach(s -> event.getEntries().remove(s));
    }
}
