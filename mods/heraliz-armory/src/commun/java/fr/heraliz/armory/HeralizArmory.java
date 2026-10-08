package fr.heraliz.armory;

import com.mojang.logging.LogUtils;
import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.armor.GearCatalog;
import fr.heraliz.armory.armor.GearDef;
import fr.heraliz.armory.item.ProtectiveItem;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.pont.Parties;
import fr.heraliz.armory.registry.ModEntities;
import fr.heraliz.armory.registry.ModItems;
import fr.heraliz.armory.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.HashSet;
import java.util.Set;

/**
 * HeraliZ Armurerie. Depuis 0.9.0, le seul mod de tenues du serveur : il reprend de HeralisZ Armor (heralisz_armor,
 * retire) les ghillies, brassards, masques a gaz et objets event (memes rendus GeckoLib, memes stats), son mixin qui
 * coupe l'ancien gestionnaire d'armures ArkaniaZ, le masquage de l'ancien onglet ArkaniaZ et les slots Curios du
 * joueur ; il ajoute les combinaisons integrales (Hazmat, contre le froid, plongee) qui se portent seules.
 */
@Mod(HeralizArmory.MOD_ID)
public class HeralizArmory {
    public static final String MOD_ID = "heraliz_armory";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Ancien mod de tenues : ses objets gardes ont le meme chemin ici (heralisz_armor:x -> heraliz_armory:x). */
    public static final String OLD_MOD = "heralisz_armor";
    /** Ancien onglet « ARMOR » d'ArkaniaZ (jar geckolib222) : vide, donc masque ; ses objets ne sont plus distribues. */
    public static final ResourceLocation OLD_ARMOR_TAB = new ResourceLocation("geckolib", "zlifearmor");

    public HeralizArmory() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(bus);
        ModEntities.register(bus);
        ModSounds.register(bus);
        ArmoryNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ArmorConfig.SPEC);
        bus.addListener(HeralizArmory::commonSetup);
        bus.addListener(HeralizArmory::aliases);
        // Parties séparées (docs/METHODE_CLIENT_SERVEUR.md §4), chargées si elles sont dans le jar :
        //  - serveur : remappage des anciens objets heralisz_armor (monde), tirs a la tete TaCZ, crochets reseau et plongee ;
        //  - client  : masquage de l'ancien onglet ArkaniaZ, rendus des objets, crochets reseau.
        Parties.serveur(MOD_ID, "fr.heraliz.armory.serveur.PartieServeur", bus);
        Parties.client(MOD_ID, "fr.heraliz.armory.client.PartieClient", bus);
    }

    /**
     * Validateur Curios « heraliz_armory:piece » (cite par nos slots mask, belt, hands, knees, body, bracelet) : un objet
     * de l'Armurerie entre dans son slot sans dependre des tags Curios. Le tag curios:face du serveur est rejete au
     * chargement (des objets absents y sont cites) : un tag casse ne doit plus empecher d'equiper nos objets.
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CuriosApi.registerCurioPredicate(id("piece"), result ->
                result.stack().getItem() instanceof ProtectiveItem p && result.slotContext().identifier().equals(p.wornCurioSlot())));
    }

    /** Identifiants des objets repris de HeralisZ Armor. */
    public static Set<String> keptIds() {
        Set<String> ids = new HashSet<>();
        for (GearDef g : GearCatalog.GEAR) ids.add(g.id());
        return ids;
    }

    /**
     * Alias heralisz_armor:x -> heraliz_armory:x des objets gardes, poses des l'enregistrement : un objet lu par son nom
     * (coffre, structure, commande) retrouve son nouvel identifiant meme hors du remappage du monde ci-dessous.
     */
    @SuppressWarnings("unchecked")
    private static void aliases(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) return;
        if (!(ForgeRegistries.ITEMS instanceof ForgeRegistry<?> registry)) return;
        try {
            for (String id : keptIds()) ((ForgeRegistry<Item>) registry).addAlias(new ResourceLocation(OLD_MOD, id), id(id));
        } catch (RuntimeException e) {
            LOGGER.warn("[armurerie] alias des anciens objets HeralisZ Armor impossibles : {}", e.toString());
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
