package fr.heraliz.noyau;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.noyau.net.NoyauNet;
import fr.heraliz.noyau.pont.Parties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Noyau HeraliZ : ce que tous les autres modules partagent, sans contenu de jeu à lui.
 * <ul>
 *    <li>état du joueur lu par tout le serveur : en combat ({@link fr.heraliz.noyau.api.CombatTag}), à terre
 *    ({@link ATerre}), en match ({@link fr.heraliz.noyau.api.Matchs}) ;</li>
 *    <li>règles du combat : tag de 15 s en PvP, déconnexion en combat = mort, commandes refusées en combat, à terre et
 *    sur la carte, pas de tir à terre ;</li>
 *    <li>kit d'interface au style des écrans de clan (côté client, {@code fr.heraliz.noyau.client.kit}).</li>
 * </ul>
 * Remplace les morceaux « globaux » qui vivaient dans l'ancien mod des matchs.
 */
@Mod(HeralizNoyau.MODID)
public class HeralizNoyau {
   public static final String MODID = "heraliz_noyau";
   public static final Logger LOG = LogUtils.getLogger();

   /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
   public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

   public HeralizNoyau() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      NoyauConfig.enregistrer(bus);
      NoyauNet.init();
      ATerre.enregistrer();
      Parties.serveur(MODID, "fr.heraliz.noyau.serveur.NoyauServeur", bus);
      Parties.client(MODID, "fr.heraliz.noyau.client.ClientNoyau", bus);
   }

   public static ResourceLocation id(String chemin) {
      return new ResourceLocation(MODID, chemin);
   }
}
