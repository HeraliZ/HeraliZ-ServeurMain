package fr.heraliz.wonder.paralyzer;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Paralyzer (Call of Duty : Black Ops 2, Buried) : point d'entree, une seule ligne dans {@code HeralizWonder}
 * ({@code Paralyseur.brancher(mod, forge)}) et une dans {@code WonderConfig} ({@code ConfigParalyseur.definir(b)}).
 * <ul>
 *   <li>arme TaCZ {@code heraliz_wonder:paralyzer} (gunpack) : rayon continu tenu par la detente, sur charge (chaleur de
 *   TaCZ, script Lua), surchauffe quand elle est vide, recharge a la manivelle (touche de rechargement) ;</li>
 *   <li>serveur ({@link RayonServeur}) : les creatures touchees se figent puis tombent en poussiere ; viser le sol sous ses
 *   pieds fait flotter ;</li>
 *   <li>client ({@code client.FxParalyseur}) : rayon ondulant avec anneaux d'energie et eclat de lumiere, creatures
 *   figees en pleine pose et lumineuses, poussiere, bourdon du rayon ;</li>
 *   <li>Pack-a-Punch « Petrifier » : paralysie plus rapide, rayon plus long et plus large, charge plus longue, turquoise.</li>
 * </ul>
 */
public final class Paralyseur {
    private Paralyseur() {
    }

    /**
     * Partie commune : sons et canal reseau. Rayon, commandes et TaCZ : partie serveur (ParalyseurServeur.brancher) ;
     * effets : partie client (ClientParalyseur.init).
     */
    public static void brancher(IEventBus mod, IEventBus forge) {
        SonsParalyseur.REGISTRE.register(mod);
        ReseauParalyseur.init();
    }
}
