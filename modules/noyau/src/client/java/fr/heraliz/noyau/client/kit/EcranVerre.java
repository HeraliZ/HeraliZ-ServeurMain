package fr.heraliz.noyau.client.kit;

/**
 * Écran à cases dessiné « en verre » (inventaire D1 validé le 14/11 ; plus tard le sac, la fouille et les coffres) :
 * marque lue par le noyau pour habiller ce que Minecraft dessine lui-même dans ces écrans :
 * <ul>
 *     <li>infobulles des objets aux couleurs du verre, cadre de la couleur de la rareté ({@link InfobulleVerre}) ;</li>
 *     <li>icônes des cases vides atténuées (mixin {@code CaseVideMixin}).</li>
 * </ul>
 * Le reste se fait avec les pièces du kit : fond ({@link FondVerre}), cases ({@link CaseVerre}), place des cases
 * ({@link CasesEcran}), infobulles des textes à nous ({@link InfobulleVerre.Bulle}).
 */
public interface EcranVerre {
    /** Opacité des icônes des cases vides. */
    float ICONE_VIDE = 0.3f;
}
