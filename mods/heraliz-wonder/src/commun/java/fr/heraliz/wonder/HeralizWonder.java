package fr.heraliz.wonder;

import com.mojang.logging.LogUtils;
import fr.heraliz.wonder.net.Reseau;
import fr.heraliz.wonder.pont.Parties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Wonder Weapons : les Wonder Weapons du mode zombies (gunpack {@code heraliz_wonder}) refaites facon Call of
 * Duty Zombies.
 * <ul>
 *   <li><b>Pistolet a rayons</b> ({@code heraliz_wonder:raygun_bo6}) : vraie balle TaCZ lente et visible, dessinee ici en
 *   projectile d'energie vert (coeur lumineux, trainee, lueur au sol) ; eclaboussure verte a l'impact
 *   ({@link fr.heraliz.wonder.serveur.Eclaboussure}). Pack-a-Punch = « Porter's X2 » : rayons rouges, eclaboussure plus
 *   large, chargeur de 40.</li>
 *   <li><b>Pistolet a rayons Mk II</b> ({@code heraliz_wonder:ray_gun_mk2}) : rafales de 3 eclairs rouges quasi
 *   instantanes, perforants. Pack-a-Punch = « Mk II-Z » : chaque tir part en double (rafales de 6), chargeur de 42.</li>
 *   <li><b>Wunderwaffe DG-2</b> ({@code heraliz_wonder:wonder_waffle}) : la balle TaCZ est remplacee par une foudre en
 *   chaine calculee une fois par tir ({@link Foudre}). Pack-a-Punch = « DG-3 JZ » : plus d'arcs, plus loin, chargeur de 6.</li>
 *   <li><b>Thundergun</b> ({@code heraliz_wonder:thundergun}, 1.0.2, modele et sons faits maison) : la balle TaCZ est
 *   remplacee par une onde de choc en cone qui souffle et tue la horde ({@link fr.heraliz.wonder.serveur.Tonnerre}),
 *   jamais de blocs casses ni de joueurs touches. Pack-a-Punch = « Zeus Cannon » : cone plus long et plus large, chargeur
 *   de 4, lueurs dorees.</li>
 *   <li><b>1.1.0</b> : Jet Gun ({@code jetgun}, aspiration et chaleur), Blundergat / Acid Gat ({@code blundergat}, {@code acidgat},
 *   gerbe d'obus / cartouches d'acide ; PaP : The Sweeper / Vitriolic Withering), Winter's Howl ({@code winters_howl}, souffle
 *   de givre ; PaP : Winter's Fury), Sliquifier ({@code sliquifier}, gelee qui fait glisser et fondre ; PaP : Sliquidator),
 *   Paralyzer ({@code paralyzer}, rayon qui fige ; PaP : Petrifier). Chacun a son paquet, son canal reseau, ses sons et sa
 *   section de reglages ; le Thundergun passe en version HD (gunpack seulement).</li>
 * </ul>
 * Le serveur seul decide (cibles, degats) ; les clients recoivent de petits paquets et dessinent les effets avec des
 * reserves fixes (aucune allocation par image). TaCZ et HeraliZ Matchs sont branches en douceur.
 */
@Mod(HeralizWonder.MODID)
public final class HeralizWonder {
    public static final String MODID = "heraliz_wonder";
    public static final Logger LOG = LogUtils.getLogger();

    public HeralizWonder() {
        IEventBus mod = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WonderConfig.SPEC, MODID + "-server.toml");
        Sons.REGISTRE.register(mod);
        Reseau.init();
        // onglet creatif « Wonder Weapons » (les armes quittent les onglets de TaCZ)
        if (ModList.get().isLoaded("tacz")) Onglet.brancher(mod);
        IEventBus forge = MinecraftForge.EVENT_BUS;
        // Partie commune de chaque arme (sons, canal reseau), dans l'ordre d'origine. La logique (cibles, degats, commandes,
        // TaCZ cote serveur) est branchee par la partie serveur, les effets (rendus, sons, TaCZ cote client) par la partie
        // client (docs/METHODE_CLIENT_SERVEUR.md §4).
        // Blundergat et Acid Gat (fr.heraliz.wonder.blundergat)
        fr.heraliz.wonder.blundergat.Blundergat.init(mod, forge);
        // Jet Gun (BO2) : paquet fr.heraliz.wonder.jetgun
        fr.heraliz.wonder.jetgun.JetGun.init(mod, forge);
        // Winter's Howl (souffle de givre) : fr.heraliz.wonder.wintershowl
        fr.heraliz.wonder.wintershowl.WintersHowl.init(mod, forge);
        // Sliquifier (BO2 Die Rise) : fr.heraliz.wonder.sliquifier
        fr.heraliz.wonder.sliquifier.Sliquifier.init(mod, forge);
        // Paralyzer (BO2 Buried ; Pack-a-Punch : Petrifier) : fr.heraliz.wonder.paralyzer
        fr.heraliz.wonder.paralyzer.Paralyseur.brancher(mod, forge);
        Parties.serveur(MODID, "fr.heraliz.wonder.serveur.PartieServeur", mod);
        Parties.client(MODID, "fr.heraliz.wonder.client.PartieClient", mod);
    }
}
