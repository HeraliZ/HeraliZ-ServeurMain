package fr.heraliz.capture;

import fr.heraliz.capture.pont.Parties;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * HeraliZ Capture : outil d'essai des interfaces (docs/AUDIT_UI.md). Sur un client d'essai, il joue le script
 * {@code <jeu>/capture/script.json} (touches, commandes, écrans, interactions) et capture chaque écran dans
 * {@code <jeu>/screenshots/}. Rien côté serveur ; jamais livré aux joueurs ni au serveur.
 */
@Mod(HeralizCapture.MODID)
public class HeralizCapture {
    public static final String MODID = "heraliz_capture";

    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

    public HeralizCapture() {
        Parties.client(MODID, "fr.heraliz.capture.client.PartieClient", FMLJavaModLoadingContext.get().getModEventBus());
    }
}
