package fr.heraliz.armory.serveur;

import fr.heraliz.armory.cible.ChiffresJoueurs;
import fr.heraliz.armory.cible.CibleEssaiServeur;
import fr.heraliz.armory.cible.CiblesServeur;
import fr.heraliz.armory.compat.GeckolibServeur;
import fr.heraliz.armory.compat.TaczCompat;
import fr.heraliz.armory.curios.ContenuReserve;
import fr.heraliz.armory.curios.MajClicCurios;
import fr.heraliz.armory.curios.MajClicCuriosServeur;
import fr.heraliz.armory.entity.MannequinServeur;
import fr.heraliz.armory.item.CibleItemServeur;
import fr.heraliz.armory.item.MannequinItemServeur;
import fr.heraliz.armory.item.SetKitItemServeur;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.nvg.NightVisionServeur;
import fr.heraliz.armory.pont.Partie;
import fr.heraliz.armory.registry.ModEntities;
import fr.heraliz.armory.registry.ModItems;
import fr.heraliz.armory.suit.Diving;
import fr.heraliz.armory.suit.DivingServeur;
import fr.heraliz.armory.suit.LaboSousMarin;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de l'Armurerie (docs/METHODE_CLIENT_SERVEUR.md §4), chargee par HeralizArmory si elle est dans le jar
 * (-serveur, -complet). Les ecouteurs serveur a annotation (ProtectionEvents, SuitEventsServeur, NightVisionServeur,
 * ...) s'inscrivent seuls ; ici : usines, crochets et ecouteurs poses par l'ancien constructeur.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // heraliz_armory-common.toml : réglage du serveur seulement (audit du 02/11, point 11)
        ReglagesArmurerieServeur.brancher(busMod);
        // usines : mannequin (casse, objets Curios, menu), tenue complete (enfiler), mannequin pose
        ModEntities.MANNEQUIN_USINE.serveur(MannequinServeur::new);
        ModItems.KIT_USINE.serveur(SetKitItemServeur::new);
        ModItems.MANNEQUIN_USINE.serveur(MannequinItemServeur::new);
        // cible d'essai (03/11) : coups mesures comme sur un joueur, tenue, ecran, pose
        ModEntities.CIBLE_USINE.serveur(CibleEssaiServeur::new);
        ModItems.CIBLE_USINE.serveur(CibleItemServeur::new);
        // crochets : touche des lunettes (paquet ToggleNvg), bouteille de plongee (tick du porteur)
        ArmoryNetwork.RECEPTION_SERVEUR.brancher(NightVisionServeur::toggle);
        Diving.BOUTEILLE_SERVEUR.brancher(DivingServeur::bouteille);
        // 17/11 : eaux du labo sous-marin : apnee courte et noir sans la tenue de plongee, vision avec
        LaboSousMarin.brancher();
        // Maj + clic de l'inventaire vers les slots Curios (mixin commun InventoryCuriosQuickMoveMixin)
        MajClicCurios.SERVEUR = MajClicCuriosServeur::ranger;
        // ecouteurs de l'ancien constructeur, dans le meme ordre : remappage heralisz_armor, puis tirs a la tete TaCZ
        MinecraftForge.EVENT_BUS.addListener(AnciensObjets::remapOldItems);
        TaczCompat.register();
        CiblesServeur.brancher();
        ChiffresJoueurs.brancher();
        // geckolib222 (audit du 15/11) : message traduit au début d'une jambe cassée ou d'un saignement, liste des
        // irradiés vidée à la mort et au départ
        GeckolibServeur.brancher();
        // 20/11 (audit réseau) : contenu des sacs portés envoyé seulement au porteur (CuriosContenuPorteurMixin) ; le
        // journal du démarrage dit si les paquets de Curios sont bien ceux attendus
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false,
                net.minecraftforge.event.server.ServerStartedEvent.class, e -> ContenuReserve.verifier());
    }
}
