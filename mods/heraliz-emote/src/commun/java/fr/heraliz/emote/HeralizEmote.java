package fr.heraliz.emote;

import fr.heraliz.emote.pont.Parties;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Heraliz Emote: emotes for Emotecraft with props, music and a menu, and borrowing the emote of the
 * player you look at. Everything happens on the client; on a dedicated server the mod does nothing
 * (the Emotecraft bundled in its jar still runs there).
 */
@Mod(HeralizEmote.MODID)
public class HeralizEmote {
    public static final String MODID = "heraliz_emote";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeralizEmote() {
        // partie client coupée (docs/METHODE_CLIENT_SERVEUR.md) : tout l'affichage ; la règle serveur est un mixin commun
        Parties.client(MODID, "fr.heraliz.emote.client.PartieClient", FMLJavaModLoadingContext.get().getModEventBus());
    }
}
