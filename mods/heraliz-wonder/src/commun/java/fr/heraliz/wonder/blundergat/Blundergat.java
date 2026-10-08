package fr.heraliz.wonder.blundergat;

import fr.heraliz.wonder.blundergat.net.BlunderReseau;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Blundergat (Call of Duty : Black Ops II, Mob of the Dead) et Acid Gat, dans heraliz_wonder. Tout le code est dans ce
 * paquet ; un seul point d'entree, appele par le constructeur de {@link HeralizWonder} :
 * <pre>    fr.heraliz.wonder.blundergat.Blundergat.init(mod, forge);</pre>
 * <ul>
 *   <li><b>Blundergat</b> ({@code heraliz_wonder:blundergat}) : gerbe de 8 obus brulants (vraies balles TaCZ : tete, points
 *   et Pack-a-Punch du mode zombies), tres forte de pres ; jamais un joueur blesse, aucun bloc touche ; effets : flamme de
 *   poudre et etincelles de bronze a la bouche, obus traceurs, impacts. Pack-a-Punch « The Sweeper » : degats x2/x3/x4 par
 *   le mode zombies, grosse reserve, obus incandescents, lueurs de braise sur l'arme.</li>
 *   <li><b>Acid Gat</b> ({@code heraliz_wonder:acidgat}, kit de l'etabli du mode zombies, ou {@code /blundergat acide}) :
 *   trois cartouches d'acide qui collent puis explosent ({@link Cartouches}). Pack-a-Punch « Vitriolic Withering » :
 *   chargeur de 6, explosion plus large, acide jaune-vert.</li>
 * </ul>
 * Le serveur seul decide ; les clients recoivent de petits paquets sur un canal propre ({@link BlunderReseau}) et dessinent
 * les effets dans des reserves fixes.
 */
public final class Blundergat {
    private Blundergat() {
    }

    /**
     * Partie commune : sons et canal reseau. La logique (cartouches, eclats, commandes, TaCZ) est branchee par la partie
     * serveur (BlundergatServeur.brancher), les effets par la partie client (BlunderClient.init).
     */
    public static void init(IEventBus mod, IEventBus forge) {
        BlunderSons.REGISTRE.register(mod);
        BlunderReseau.init();
    }
}
