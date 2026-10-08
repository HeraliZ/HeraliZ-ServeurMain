package fr.heraliz.armory.serveur;

import fr.heraliz.armory.compat.TaczCompat;
import fr.heraliz.armory.entity.MannequinServeur;
import fr.heraliz.armory.item.MannequinItemServeur;
import fr.heraliz.armory.item.SetKitItemServeur;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.nvg.NightVisionServeur;
import fr.heraliz.armory.pont.Partie;
import fr.heraliz.armory.registry.ModEntities;
import fr.heraliz.armory.registry.ModItems;
import fr.heraliz.armory.suit.Diving;
import fr.heraliz.armory.suit.DivingServeur;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de l'Armurerie (docs/METHODE_CLIENT_SERVEUR.md §4), chargee par HeralizArmory si elle est dans le jar
 * (-serveur, -complet). Les ecouteurs serveur a annotation (ProtectionEvents, SuitEventsServeur, NightVisionServeur,
 * ArmoryCommands, HarnessServer) s'inscrivent seuls ; ici : usines, crochets et ecouteurs poses par l'ancien constructeur.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // usines : mannequin (casse, objets Curios, menu), tenue complete (enfiler), mannequin pose
        ModEntities.MANNEQUIN_USINE.serveur(MannequinServeur::new);
        ModItems.KIT_USINE.serveur(SetKitItemServeur::new);
        ModItems.MANNEQUIN_USINE.serveur(MannequinItemServeur::new);
        // crochets : touche des lunettes (paquet ToggleNvg), bouteille de plongee (tick du porteur)
        ArmoryNetwork.RECEPTION_SERVEUR.brancher(NightVisionServeur::toggle);
        Diving.BOUTEILLE_SERVEUR.brancher(DivingServeur::bouteille);
        // ecouteurs de l'ancien constructeur, dans le meme ordre : remappage heralisz_armor, puis tirs a la tete TaCZ
        MinecraftForge.EVENT_BUS.addListener(AnciensObjets::remapOldItems);
        TaczCompat.register();
    }
}
