package fr.heraliz.banc;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.pont.Parties;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Banc d'essai HeraliZ : bots de charge ({@code /bots}, op niveau 4) pour éprouver le serveur avant une ouverture.
 * <b>Jamais installé sur le serveur en jeu normal</b> : livré à part ({@code livraison/banc/}), posé seulement le temps
 * d'un test.
 */
@Mod(HeralizBanc.MODID)
public class HeralizBanc {
    public static final String MODID = "heraliz_banc";
    public static final Logger LOG = LogUtils.getLogger();
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";

    public HeralizBanc() {
        Parties.serveur(MODID, "fr.heraliz.banc.BancServeur", FMLJavaModLoadingContext.get().getModEventBus());
    }
}
