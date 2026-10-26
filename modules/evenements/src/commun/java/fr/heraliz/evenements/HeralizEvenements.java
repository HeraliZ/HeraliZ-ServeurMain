package fr.heraliz.evenements;

import com.mojang.logging.LogUtils;
import fr.heraliz.evenements.objets.ObjetsEvenement;
import fr.heraliz.evenements.pont.Parties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Événements (25/10) : le Directeur d'événements (planning, conditions, annonces, journal, points de saison), les
 * événements de la carte et les objets d'événement à revendre, tous rangés ici (docs/EVENEMENTS.md : la liste de
 * branchement de chaque événement). Le jar des joueurs porte les objets (enregistrement, modèles) et les textes ; tout le
 * reste est dans la partie serveur.
 */
@Mod(HeralizEvenements.MODID)
public class HeralizEvenements {
    public static final String MODID = "heraliz_evenements";
    public static final Logger LOG = LogUtils.getLogger();

    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

    public HeralizEvenements() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ObjetsEvenement.enregistrer(bus);
        Parties.serveur(MODID, "fr.heraliz.evenements.serveur.PartieServeur", bus);
    }
}
