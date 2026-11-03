package fr.heraliz.armory.cible;

import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.SetCatalog;
import fr.heraliz.armory.armor.SetDef;
import fr.heraliz.armory.armor.SuitCatalog;
import fr.heraliz.armory.item.PieceItem;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tenues proposées à la cible d'essai (03/11) : « sans armure », chaque ensemble de l'Armurerie qui a une tenue complète
 * (SetCatalog, du Commun à l'Unique, dans l'ordre de l'onglet), puis les combinaisons (SuitCatalog).
 * <p>La cible porte les VRAIS objets de la tenue, dans leurs vrais emplacements (armure de Minecraft et Curios) : le calcul
 * des protections (armor.Protection, ProtectionEvents) est donc exactement celui d'un joueur habillé ainsi. 03/11 (choix
 * du propriétaire) : seulement des tenues complètes, casque compris, jamais de pièces mélangées.</p>
 */
public final class Tenues {
    private Tenues() {}

    public static final String AUCUNE = "aucune";
    /** Au plus ce nombre de tenues dans un paquet, de 64 lettres chacune. */
    public static final int MAX = 64, MAX_LETTRES = 64;

    /**
     * @param id          « aucune », identifiant d'ensemble (citadin…) ou de combinaison (hazmat_suit…)
     * @param rang        rang de rareté (1 Commun … 6 Unique), 0 sans armure
     * @param pieces      toutes les pièces de la tenue
     * @param combinaison une combinaison intégrale (un seul objet, tout le corps)
     */
    public record Tenue(String id, int rang, List<PieceDef> pieces, boolean combinaison) {
        /** Rareté (clé rarity.heraliz_armory.*), « none » sans armure. */
        public String rarete() {
            return rang <= 0 ? "none" : PieceItem.rarity(rang);
        }

        /** Nom affiché (textes déjà présents dans le mod : ensembles, combinaisons). */
        public Component nom() {
            if (AUCUNE.equals(id)) return Component.translatable("gui.heraliz_armory.cible.aucune");
            if (combinaison) return Component.translatable("item.heraliz_armory." + id);
            return Component.translatable("tooltip.heraliz_armory.set." + id);
        }
    }

    public static final Tenue SANS_ARMURE = new Tenue(AUCUNE, 0, List.of(), false);

    /** Toutes les tenues, dans l'ordre de l'écran (le serveur envoie les protections dans ce même ordre). */
    public static final List<Tenue> TOUTES;

    static {
        List<Tenue> l = new ArrayList<>();
        l.add(SANS_ARMURE);
        SetCatalog.SETS.stream().filter(s -> s.kit() && !s.pieces().isEmpty()).sorted(Comparator.comparingInt(SetDef::level))
                .forEach(s -> {
                    int rang = 1;
                    for (PieceDef p : s.pieces()) rang = Math.max(rang, p.tier());
                    l.add(new Tenue(s.set(), rang, s.pieces(), false));
                });
        for (PieceDef suit : SuitCatalog.SUITS) l.add(new Tenue(suit.itemId(), suit.tier(), List.of(suit), true));
        TOUTES = List.copyOf(l);
    }

    public static int index(String id) {
        for (int i = 0; i < TOUTES.size(); i++) if (TOUTES.get(i).id().equals(id)) return i;
        return -1;
    }

    /** La tenue de cet identifiant, ou « sans armure » s'il est inconnu. */
    public static Tenue de(String id) {
        int i = index(id);
        return i < 0 ? SANS_ARMURE : TOUTES.get(i);
    }
}
