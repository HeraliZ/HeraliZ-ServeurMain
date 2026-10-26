package fr.heraliz.boss;

import fr.heraliz.boss.onglet.OngletGroupe;
import fr.heraliz.boss.pont.Parties;
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
 * HeraliZ Boss : le mod « porteur » du jar fusionné. Il ne contient aucun boss ; il porte ce qui est partagé par les cinq :
 * l'onglet créatif unique « HeraliZ · Boss » (et son panneau de filtres, côté client) et le pont client / serveur
 * (paquet {@code fr.heraliz.boss.pont}). Les boss gardent chacun leur modId et leur classe @Mod.
 */
@Mod(HeralizBoss.MODID)
public class HeralizBoss {
   public static final String MODID = "heraliz_boss";

   /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
   public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

   /** Groupes de l'onglet (un par boss, plus les décors du spawn) : déclarés ci-dessous, dans un ordre fixe. */
   public static final OngletGroupe ONGLET = new OngletGroupe();

   public static final RegistryObject<CreativeModeTab> TAB = TABS.register("boss",
      () -> ONGLET.construire(Component.translatable("itemGroup.heraliz_boss"), HeralizBoss::icone));

   /*
    * Groupes de l'onglet, dans l'ordre du panneau (Tous, puis Ymirak, Mère des Ruines, Sujet N-07, Décors du spawn, Porteur
    * de Peste, Sylvorn). Déclarés ICI et non dans chaque @Mod : FML construit les mods en parallèle, l'ordre d'ajout ne
    * serait pas stable (docs/METHODE_CLIENT_SERVEUR.md §16, piège n°24). Le contenu reste fourni par chaque boss, appelé
    * seulement au remplissage de l'onglet.
    */
   static {
      ONGLET.ajouter("mammoth", "onglet.heraliz_boss.mammoth", () -> fr.heralisz.mammoth.HeraliszMammoth.iconeOnglet(),
         out -> fr.heralisz.mammoth.HeraliszMammoth.remplirOnglet(out));
      ONGLET.ajouter("mere", "onglet.heraliz_boss.mere", () -> fr.heralisz.mere.HeraliszMere.iconeOnglet(),
         out -> fr.heralisz.mere.HeraliszMere.remplirOnglet(out));
      ONGLET.ajouter("n07", "onglet.heraliz_boss.n07", () -> fr.heralisz.n07.HeraliszN07.iconeOnglet(),
         out -> fr.heralisz.n07.HeraliszN07.remplirOnglet(out));
      ONGLET.ajouter("n07_decor", "onglet.heraliz_boss.n07_decor", () -> fr.heralisz.n07.HeraliszN07.iconeDecor(),
         out -> fr.heralisz.n07.decor.DecorRegistry.remplir(out));
      ONGLET.ajouter("plague", "onglet.heraliz_boss.plague", () -> fr.heralisz.plague.HeraliszPlague.iconeOnglet(),
         out -> fr.heralisz.plague.HeraliszPlague.remplirOnglet(out));
      ONGLET.ajouter("sylvorn", "onglet.heraliz_boss.sylvorn", () -> fr.heralisz.sylvorn.HeraliszSylvorn.iconeOnglet(),
         out -> fr.heralisz.sylvorn.HeraliszSylvorn.remplirOnglet(out));
   }

   public HeralizBoss() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      TABS.register(bus);
      Parties.client(MODID, "fr.heraliz.boss.client.ClientBoss", bus);
   }

   /** Icône de l'onglet : celle du premier groupe (le cor de guerre d'Ymirak). */
   private static ItemStack icone() {
      var g = ONGLET.groupes();
      return g.isEmpty() ? new ItemStack(Items.DRAGON_HEAD) : g.get(0).icone().get();
   }
}
