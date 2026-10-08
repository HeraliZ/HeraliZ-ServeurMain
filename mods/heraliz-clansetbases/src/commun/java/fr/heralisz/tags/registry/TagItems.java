package fr.heralisz.tags.registry;

import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.item.TagItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Objets du mod : un TagItem par tag du catalogue (heralisz_tags:tag_&lt;id&gt;). Pas d'onglet creatif dedie :
 * les tags se donnent par /tags donner ou par le butin des caisses.
 */
public final class TagItems {
    private TagItems() {
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszTags.MODID);

    /** Objet de chaque tag, par id de tag (ordre du catalogue). */
    public static final Map<String, RegistryObject<Item>> TAGS;

    static {
        Map<String, RegistryObject<Item>> m = new LinkedHashMap<>();
        for (TagDef d : TagCatalog.tous()) {
            m.put(d.id(), ITEMS.register("tag_" + d.id(), () -> new TagItem(d, new Item.Properties().stacksTo(16).rarity(d.rarete().vanilla))));
        }
        TAGS = Collections.unmodifiableMap(m);
    }

    /** L'objet d'un tag, ou null si l'id est inconnu. */
    public static Item tag(String id) {
        RegistryObject<Item> ro = TAGS.get(id);
        return ro == null ? null : ro.get();
    }
}
