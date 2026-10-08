package fr.heraliz.clansetbases;

import fr.heraliz.clansetbases.onglet.OngletGroupe;
import fr.heraliz.clansetbases.pont.Parties;
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
 *    HeralizClansetbases.ONGLET.ajouter("melee", "onglet.heraliz_clansetbases.melee", () -&gt; new ItemStack(...), out -&gt; { ... });
 * </pre>
 */
@Mod(HeralizClansetbases.MODID)
public class HeralizClansetbases {
   public static final String MODID = "heraliz_clansetbases";

   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

   /** Groupes de l'onglet : remplis par chaque mod du jar dans son constructeur @Mod. */
   public static final OngletGroupe ONGLET = new OngletGroupe();

   public static final RegistryObject<CreativeModeTab> TAB = TABS.register("onglet",
      () -> ONGLET.construire(Component.translatable("itemGroup.heraliz_clansetbases"), HeralizClansetbases::icone));

   public HeralizClansetbases() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      TABS.register(bus);
      Parties.client(MODID, "fr.heraliz.clansetbases.client.ClientClansetbases", bus);
   }

   /** Icône de l'onglet : celle du premier groupe. */
   private static ItemStack icone() {
      var g = ONGLET.groupes();
      return g.isEmpty() ? new ItemStack(Items.BOOK) : g.get(0).icone().get();
   }
}
