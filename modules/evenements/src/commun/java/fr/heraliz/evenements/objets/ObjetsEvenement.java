package fr.heraliz.evenements.objets;

import fr.heraliz.evenements.HeralizEvenements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Objets d'événement à revendre (docs/EVENEMENTS.md, « L'objet d'événement à revendre » ; 25/10, rendus validés par le
 * propriétaire : les trois, du plus courant au plus rare). Un coffre d'événement en contient toujours un ; revendu à un
 * marchand racheteur du spawn (Mila), il donne des hérals au vendeur et des points de saison à son clan. Le prix, les
 * chances par événement et les points sont des données du serveur ({@code config/heraliz_evenements/objets.json},
 * {@code events.points.objet_<id>} du barème de la saison). Chaque exemplaire porte l'étiquette {@link #CLE} posée par le
 * serveur (numéro unique et signature) : une copie ou un objet fabriqué à la main n'est jamais racheté.
 * Identifiants figés (objets gardés dans les inventaires). Modèles écrits par {@code modules/evenements/outils/objets.py}.
 */
public final class ObjetsEvenement {
    private ObjetsEvenement() {
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizEvenements.MODID);

    /** Étiquette de l'exemplaire : {@code {Id, Ev, Sig}} (numéro unique, événement d'origine, signature du serveur). */
    public static final String CLE = "HeralizObjetEvenement";

    /**
     * Du plus courant au plus rare (raretés Rare, Épique, Légendaire de HeraliZ Loot) ; puis la boîte noire de
     * l'hélicoptère abattu (Légendaire, 25/10 : son seul objet ; autre objet que la boîte noire de quête de Varga).
     */
    public static final List<String> IDS = List.of("dossier_classe", "mallette_scellee", "disque_dur_militaire", "boite_noire_helico");

    public static final RegistryObject<Item> DOSSIER_CLASSE = ITEMS.register("dossier_classe", Objet::new);
    public static final RegistryObject<Item> MALLETTE_SCELLEE = ITEMS.register("mallette_scellee", Objet::new);
    public static final RegistryObject<Item> DISQUE_DUR_MILITAIRE = ITEMS.register("disque_dur_militaire", Objet::new);
    public static final RegistryObject<Item> BOITE_NOIRE_HELICO = ITEMS.register("boite_noire_helico", Objet::new);

    public static void enregistrer(IEventBus bus) {
        ITEMS.register(bus);
    }

    /** Un objet d'événement : un seul par case, ne brûle pas. */
    public static final class Objet extends Item {
        public Objet() {
            super(new Item.Properties().stacksTo(1).fireResistant());
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
            lignes.add(Component.translatable("item.heraliz_evenements.objet.aide").withStyle(ChatFormatting.GRAY));
        }
    }
}
