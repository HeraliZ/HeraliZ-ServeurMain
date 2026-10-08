package fr.heraliz.armory.armor;

import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un ensemble (genere par tools/build_all.py dans SetCatalog) : sa tenue complete est l'item kit_<ensemble>.
 *
 * @param set    identifiant de l'ensemble (citadin, sentinelle...)
 * @param level  niveau NV1..NV23 (un ensemble par niveau ; NV24 pour un casque a part), ordre de l'onglet creatif
 * @param family famille de couleur (bleu, rouge, beige, vert, gris, noir, blanc, marron)
 * @param kit    true : l'ensemble a une tenue complete (kit_<ensemble>) ; false : piece a part (casques de vision nocturne)
 */
public record SetDef(String set, int level, String family, boolean kit) {

    /** Identifiant de l'item "tenue complete". */
    public String kitId() {
        return "kit_" + set;
    }

    /** Pieces de l'ensemble, dans l'ordre du catalogue (tete, visage, torse, gilet, mains, jambes, genoux, pieds). */
    public List<PieceDef> pieces() {
        return Index.PIECES.getOrDefault(set, List.of());
    }

    /** Couleur de la ligne "coloris" de l'infobulle. */
    public ChatFormatting familyColor() {
        return switch (family) {
            case "bleu" -> ChatFormatting.BLUE;
            case "rouge" -> ChatFormatting.RED;
            case "beige" -> ChatFormatting.YELLOW;
            case "vert" -> ChatFormatting.GREEN;
            case "gris" -> ChatFormatting.GRAY;
            case "noir" -> ChatFormatting.GRAY;
            case "blanc" -> ChatFormatting.WHITE;
            case "marron" -> ChatFormatting.GOLD;
            default -> ChatFormatting.GRAY;
        };
    }

    @Nullable
    public static SetDef of(String set) {
        return Index.SETS.get(set);
    }

    /** Niveau de l'ensemble, 0 s'il est absent de SetCatalog. */
    public static int levelOf(String set) {
        SetDef def = of(set);
        return def == null ? 0 : def.level();
    }

    /** Cle de tri par niveau : les ensembles sans niveau passent en dernier. */
    public static int sortKey(String set) {
        int level = levelOf(set);
        return level > 0 ? level : Integer.MAX_VALUE;
    }

    /** Index construits une seule fois, au premier acces (apres le chargement des deux catalogues). */
    private static final class Index {
        static final Map<String, SetDef> SETS = new HashMap<>();
        static final Map<String, List<PieceDef>> PIECES = new HashMap<>();

        static {
            for (SetDef def : SetCatalog.SETS) SETS.put(def.set(), def);
            Map<String, List<PieceDef>> pieces = new HashMap<>();
            for (PieceDef def : ArmorCatalog.PIECES) pieces.computeIfAbsent(def.set(), k -> new ArrayList<>()).add(def);
            pieces.forEach((k, v) -> PIECES.put(k, List.copyOf(v)));
        }
    }
}
