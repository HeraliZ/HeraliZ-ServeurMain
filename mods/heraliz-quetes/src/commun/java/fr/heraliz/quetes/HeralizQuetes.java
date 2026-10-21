package fr.heraliz.quetes;

import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.objet.Baguette;
import fr.heraliz.quetes.objet.ObjetQuete;
import fr.heraliz.quetes.objet.RegistreMarchand;
import fr.heraliz.quetes.pont.Parties;
import fr.heraliz.quetes.pont.UsineEntite;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HeraliZ Quêtes (docs/QUETES_SCENARIO.md) : PNJ posés au spawn par le staff, quêtes (suite de chaque PNJ + tableau de
 * contrats), points de quête posés à la baguette sur la carte, objets de quête, commande /quetes et suivi à l'écran.
 * Ici : ce que les deux côtés partagent (entité PNJ, objets, canal réseau). Toute la logique (dialogues, progression,
 * récompenses) est dans la partie serveur ; le jeu du joueur ne fait qu'afficher et demander.
 */
@Mod(HeralizQuetes.MODID)
public class HeralizQuetes {
    public static final String MODID = "heraliz_quetes";

    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final UsineEntite<Pnj> PNJ_USINE = new UsineEntite<>(Pnj::new);
    public static final RegistryObject<EntityType<Pnj>> PNJ = ENTITES.register("pnj",
            () -> EntityType.Builder.of(PNJ_USINE, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("pnj"));

    /** Modèles des objets de quête : le nom et la description viennent de la quête (« Carnet de tir de Jack »). */
    public static final List<String> MODELES = List.of("document", "carnet", "lettre", "photo", "cle", "cle_usb", "colis",
            "echantillon", "plaque", "piece", "insigne_regiment", "boite_noire", "sacoche_deserteurs");
    public static final Map<String, RegistryObject<Item>> OBJETS_QUETE = new LinkedHashMap<>();

    static {
        for (String m : MODELES) OBJETS_QUETE.put(m, OBJETS.register(m, () -> new ObjetQuete(new Item.Properties().stacksTo(1))));
    }

    public static final RegistryObject<Item> BAGUETTE = OBJETS.register("baguette", () -> new Baguette(new Item.Properties().stacksTo(1)));
    /** Registre du marchand (staff) : modifier en jeu ce que vendent les PNJ et leurs prix. */
    public static final RegistryObject<Item> REGISTRE = OBJETS.register("registre_marchand", () -> new RegistreMarchand(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> ONGLET = ONGLETS.register("onglet", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heraliz_quetes"))
            .icon(() -> new ItemStack(OBJETS_QUETE.get("carnet").get()))
            .displayItems((p, out) -> {
                out.accept(BAGUETTE.get());
                out.accept(REGISTRE.get());
                for (RegistryObject<Item> o : OBJETS_QUETE.values()) out.accept(o.get());
            })
            .build());

    public HeralizQuetes() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITES.register(bus);
        OBJETS.register(bus);
        ONGLETS.register(bus);
        bus.addListener(HeralizQuetes::attributs);
        Reseau.enregistrer();
        Parties.serveur(MODID, "fr.heraliz.quetes.serveur.ServeurQuetes", bus);
        Parties.client(MODID, "fr.heraliz.quetes.client.ClientQuetes", bus);
    }

    private static void attributs(EntityAttributeCreationEvent e) {
        e.put(PNJ.get(), Pnj.attributs().build());
    }

    public static Item modele(String nom) {
        RegistryObject<Item> o = OBJETS_QUETE.get(nom);
        return (o == null ? OBJETS_QUETE.get("document") : o).get();
    }
}
