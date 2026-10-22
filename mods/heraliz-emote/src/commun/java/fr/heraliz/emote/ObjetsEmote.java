package fr.heraliz.emote;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Objets du mod : {@code heraliz_emote:icone}, l'icône d'une emote, affichée dans la roue de la caisse des emotes
 * (HeraliZ Caisses) ; jamais donnée aux joueurs. Son dessin vient de {@code CustomModelData} = rang de l'emote dans
 * {@code liste.json} plus 1 (modèles écrits par {@code outils/liste_emotes.py}).
 */
public final class ObjetsEmote {
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizEmote.MODID);
    public static final RegistryObject<Item> ICONE = OBJETS.register("icone", () -> new Item(new Item.Properties().stacksTo(1)));

    private ObjetsEmote() {
    }

    public static void enregistrer(IEventBus bus) {
        OBJETS.register(bus);
    }
}
