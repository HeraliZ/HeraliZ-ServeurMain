package fr.heraliz.gabarit;

import fr.heraliz.gabarit.onglet.OngletGroupe;
import fr.heraliz.gabarit.pont.Parties;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Mod porteur du jar fusionné (docs/METHODE_CLIENT_SERVEUR.md §2) : il ne contient aucun contenu de jeu ; il porte ce qui
 * est partagé par les mods du jar : l'onglet créatif unique (et son panneau de filtres côté client) et le pont client /
 * serveur (paquet {@code pont}). Les mods fusionnés gardent chacun leur modId et leur classe @Mod, et ajoutent leur groupe :
 * <pre>
 *    HeralizGabarit.ONGLET.ajouter("melee", "onglet.heraliz_gabarit.melee", () -&gt; new ItemStack(...), out -&gt; { ... });
 * </pre>
 */
@Mod(HeralizGabarit.MODID)
public class HeralizGabarit {
   public static final String MODID = "heraliz_gabarit";

   /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
   public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

   /** Groupes de l'onglet : remplis par chaque mod du jar dans son constructeur @Mod. */
   public static final OngletGroupe ONGLET = new OngletGroupe();

   public static final RegistryObject<CreativeModeTab> TAB = TABS.register("onglet",
      () -> ONGLET.construire(Component.translatable("itemGroup.heraliz_gabarit"), HeralizGabarit::icone));

   public HeralizGabarit() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      TABS.register(bus);
      Parties.client(MODID, "fr.heraliz.gabarit.client.ClientGabarit", bus);
   }

   /** Icône de l'onglet : celle du premier groupe. */
   private static ItemStack icone() {
      var g = ONGLET.groupes();
      return g.isEmpty() ? new ItemStack(Items.BOOK) : g.get(0).icone().get();
   }
}
