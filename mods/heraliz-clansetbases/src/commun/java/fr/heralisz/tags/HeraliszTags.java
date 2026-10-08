package fr.heralisz.tags;

import com.mojang.logging.LogUtils;
import fr.heraliz.clansetbases.HeralizClansetbases;
import fr.heraliz.clansetbases.pont.Parties;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.network.TagsNet;
import fr.heralisz.tags.registry.TagItems;
import fr.heralisz.tags.registry.Particules;
import fr.heralisz.tags.registry.Sons;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * HeraliZ Tags : sprays facon CS:GO / Valorant. Catalogue de 36 tags a debloquer (objets), roue de 4 (touche J),
 * collection, pose sur les murs pendant 30 s. Ce fichier branche les registres, le reseau et la config ; le stockage, les
 * regles de pose et les commandes sont dans la partie serveur ({@code tags.serveur.PartieServeur}), l'affichage dans la
 * partie client.
 */
@Mod(HeraliszTags.MODID)
public class HeraliszTags {
    public static final String MODID = "heralisz_tags";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeraliszTags() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // le catalogue est lu dans le jar (commun client/serveur) avant l'enregistrement des objets
        TagCatalog.charger();
        TagItems.ITEMS.register(modBus);
        Sons.SOUNDS.register(modBus);
        Particules.PARTICLES.register(modBus);
        TagsNet.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TagsConfig.SPEC);
        // onglet creatif commun « HeraliZ · Clans et bases » : les objets tags, dans l'ordre du catalogue
        HeralizClansetbases.ONGLET.ajouter("tags", "onglet.heraliz_clansetbases.tags", HeraliszTags::icone, out -> {
            for (RegistryObject<net.minecraft.world.item.Item> o : TagItems.TAGS.values()) out.accept(o.get());
        });
        // cycle de vie serveur (stockage, tags poses, commandes, connexion) : partie serveur ; rendu, roue, collection :
        // partie client (docs/METHODE_CLIENT_SERVEUR.md §4)
        Parties.serveur(MODID, "fr.heralisz.tags.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heralisz.tags.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    /** Icone du groupe de l'onglet : le premier tag legendaire du catalogue (sinon le premier tag). */
    private static ItemStack icone() {
        var n07 = TagItems.TAGS.get("n07");
        if (n07 != null) return new ItemStack(n07.get());
        return TagItems.TAGS.isEmpty() ? new ItemStack(Items.PAPER) : new ItemStack(TagItems.TAGS.values().iterator().next().get());
    }
}
