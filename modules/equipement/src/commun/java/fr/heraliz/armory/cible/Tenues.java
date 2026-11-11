package fr.heraliz.armory.cible;

import fr.heraliz.armory.armor.GearCatalog;
import fr.heraliz.armory.armor.GearDef;
import fr.heraliz.armory.armor.Guard;
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
 * (SetCatalog, du Commun à l'Unique, dans l'ordre de l'onglet), puis les combinaisons (SuitCatalog), puis (11/11) les deux
 * ghillies portées seules ({@link #GHILLIES}, emplacement Curios « body » : de quoi voir leur protection au corps).
 * <p>La cible porte les VRAIS objets de la tenue, dans leurs vrais emplacements (armure de Minecraft et Curios) : le calcul
 * des protections (armor.Protection, ProtectionEvents) est donc exactement celui d'un joueur habillé ainsi. 03/11 (choix
 * du propriétaire) : seulement des tenues complètes, casque compris, jamais de pièces mélangées.</p>
 */
public final class Tenues {
    private Tenues() {}

    public static final String AUCUNE = "aucune";
    /** Ghillies proposées (11/11) : une de chaque modèle, la couleur ne change rien à la protection. */
    static final List<String> GHILLIES = List.of("ghillie_leafy_forest", "ghillie_full_forest");
    /** Au plus ce nombre de tenues dans un paquet, de 64 lettres chacune. */
    public static final int MAX = 64, MAX_LETTRES = 64;

    /**
     * @param id          « aucune », identifiant d'ensemble (citadin…), de combinaison (hazmat_suit…) ou d'objet repris de
     *                    HeralisZ Armor (ghillie_full_forest…)
     * @param rang        rang de rareté (1 Commun … 6 Unique), 0 sans armure
     * @param pieces      toutes les pièces de la tenue
     * @param combinaison une combinaison intégrale (un seul objet, tout le corps)
     * @param objets      objets repris de HeralisZ Armor portés (11/11 : une ghillie seule), vide pour les autres tenues
     */
    public record Tenue(String id, int rang, List<PieceDef> pieces, boolean combinaison, List<GearDef> objets) {
        /** Rareté (clé rarity.heraliz_armory.*), « none » sans armure. */
        public String rarete() {
            return rang <= 0 ? "none" : PieceItem.rarity(rang);
        }

        /** Nom affiché (textes déjà présents dans le mod : ensembles, combinaisons, noms des objets). */
        public Component nom() {
            if (AUCUNE.equals(id)) return Component.translatable("gui.heraliz_armory.cible.aucune");
            if (combinaison || !objets.isEmpty()) return Component.translatable("item.heraliz_armory." + id);
            return Component.translatable("tooltip.heraliz_armory.set." + id);
        }

        /** Tout ce qui protège dans cette tenue (pièces et objets), pour le calcul des protections. */
        public List<Guard> protections() {
            List<Guard> l = new ArrayList<>(pieces.size() + objets.size());
            l.addAll(pieces);
            l.addAll(objets);
            return l;
        }
    }

    public static final Tenue SANS_ARMURE = new Tenue(AUCUNE, 0, List.of(), false, List.of());

    /** Toutes les tenues, dans l'ordre de l'écran (le serveur envoie les protections dans ce même ordre). */
    public static final List<Tenue> TOUTES;

    static {
        List<Tenue> l = new ArrayList<>();
        l.add(SANS_ARMURE);
        SetCatalog.SETS.stream().filter(s -> s.kit() && !s.pieces().isEmpty()).sorted(Comparator.comparingInt(SetDef::level))
                .forEach(s -> {
                    int rang = 1;
                    for (PieceDef p : s.pieces()) rang = Math.max(rang, p.tier());
                    l.add(new Tenue(s.set(), rang, s.pieces(), false, List.of()));
                });
        for (PieceDef suit : SuitCatalog.SUITS) l.add(new Tenue(suit.itemId(), suit.tier(), List.of(suit), true, List.of()));
        // 11/11 : ajoutées à la fin (les numéros des tenues d'avant ne changent pas)
        for (String id : GHILLIES) {
            for (GearDef g : GearCatalog.GEAR) {
                if (g.id().equals(id)) l.add(new Tenue(g.id(), rang(g.rarity()), List.of(), false, List.of(g)));
            }
        }
        TOUTES = List.copyOf(l);
    }

    /** Rang d'une rareté des objets repris de HeralisZ Armor (« epic » → 4), comme les paliers des pièces. */
    private static int rang(String rarete) {
        return switch (rarete) {
            case "uncommon" -> 2;
            case "rare" -> 3;
            case "epic" -> 4;
            case "legendary" -> 5;
            case "unique" -> 6;
            default -> 1;
        };
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
