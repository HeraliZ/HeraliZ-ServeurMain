package fr.heraliz.recolte;

import com.mojang.logging.LogUtils;
import fr.heraliz.recolte.entites.EntitesRecolte;
import fr.heraliz.recolte.objets.ObjetsRecolte;
import fr.heraliz.recolte.pont.Parties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Récolte (cahier des charges : {@code modules/recolte/A_FAIRE.md} ; barème et décisions du propriétaire :
 * {@code maquettes/recolte/PROPOSITION.md}). Quelques arbres à abattre et gisements de pierre et de métal apparaissent sur
 * la carte, décidés par le serveur : ce sont des entités dessinées avec nos modèles en cubes (aucun bloc posé ni cassé).
 * La hache abat les arbres, la pioche casse les gisements, la hache-pioche de survie (légendaire) fait les deux ; on
 * récolte des matières brutes que le Contremaître du spawn échange contre les matériaux des bases.
 * <p>Commun : objets, entités et leur état synchronisé. Serveur ({@code fr.heraliz.recolte.serveur}) : coups, étapes,
 * chute, apparition, réglages, commandes. Client ({@code fr.heraliz.recolte.client}) : modèles cuits, rendus, outils en
 * main. Identifiants figés dès la première mise en ligne (objets gardés dans les inventaires, entités posées sur la carte).
 */
@Mod(HeralizRecolte.MODID)
public class HeralizRecolte {
    public static final String MODID = "heraliz_recolte";
    public static final Logger LOG = LogUtils.getLogger();

    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

    public HeralizRecolte() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ObjetsRecolte.enregistrer(bus);
        EntitesRecolte.enregistrer(bus);
        Parties.serveur(MODID, "fr.heraliz.recolte.serveur.PartieServeur", bus);
        Parties.client(MODID, "fr.heraliz.recolte.client.PartieClient", bus);
    }
}
