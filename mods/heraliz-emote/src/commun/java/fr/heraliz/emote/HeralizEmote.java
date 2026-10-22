package fr.heraliz.emote;

import fr.heraliz.emote.pont.Parties;
import fr.heraliz.emote.reseau.ReseauEmotes;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Heraliz Emote: emotes for Emotecraft with props, music and a menu, and borrowing the emote of the
 * player you look at. Display on the client; the server part keeps which emotes each player unlocked (emote crate,
 * staff), refuses the others and keeps weapons out of the hands during an emote and 10 s after it.
 */
@Mod(HeralizEmote.MODID)
public class HeralizEmote {
    public static final String MODID = "heraliz_emote";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeralizEmote() {
        ObjetsEmote.enregistrer(FMLJavaModLoadingContext.get().getModEventBus());
        ReseauEmotes.enregistrer();
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : serveur = déblocages, armes bloquées après une emote,
        // commande /emote ; client = tout l'affichage ; la règle serveur reste un mixin commun
        Parties.serveur(MODID, "fr.heraliz.emote.serveur.PartieServeur", FMLJavaModLoadingContext.get().getModEventBus());
        Parties.client(MODID, "fr.heraliz.emote.client.PartieClient", FMLJavaModLoadingContext.get().getModEventBus());
    }
}
