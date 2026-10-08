package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Aides de dessin communes aux ecrans des duels : icones d'objets (pile reelle de TaCZ / heralisz_tactical / armure,
 * sinon pastille avec les initiales, jamais d'objet vanilla), noms traduits avec repli lisible, libelles des types
 * d'armes et des emplacements d'accessoires. Les noms resolus sont mis en cache (pas de calcul par image).
 */
final class Rendu {
    private Rendu() {
    }

    private static final Map<String, String> NOMS = new HashMap<>();

    /**
     * Dessine une icone carree de {@code taille} px en (x, y) : la pile si elle existe, sinon une pastille sobre avec les
     * initiales du nom de repli.
     */
    static void icone(GuiGraphics g, ItemStack pile, int x, int y, int taille, String repli, int alpha) {
        if (pile != null && !pile.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            float s = taille / 16f;
            g.pose().scale(s, s, 1);
            g.renderItem(pile, 0, 0);
            g.pose().popPose();
            return;
        }
        Ui.soft(g, x, y, taille, taille, Ui.argb(0xFFFFFF, alpha / 12));
        Ui.softOutline(g, x + 1, y + 1, taille - 2, taille - 2, Ui.argb(0xFFFFFF, alpha / 6));
        Font font = Minecraft.getInstance().font;
        String ini = initiales(repli);
        Ui.centered(g, font, Ui.lit(ini, Ui.SMALL), x + taille / 2f, y + taille / 2f - 3, Ui.argb(Ui.MUTED, alpha));
    }

    private static final Map<String, String> INITIALES = new HashMap<>();

    private static String initiales(String s) {
        if (s == null || s.isEmpty()) return "·";
        return INITIALES.computeIfAbsent(s, Rendu::calculerInitiales);
    }

    private static String calculerInitiales(String s) {
        String t = s.replace('_', ' ').trim();
        StringBuilder sb = new StringBuilder();
        for (String m : t.split("[ \\-]+")) {
            if (m.isEmpty()) continue;
            sb.append(Character.toUpperCase(m.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.length() == 0 ? "·" : sb.toString();
    }

    /** Nom affichable : traduction de la cle si elle existe, sinon nom lisible tire de l'id ou de la cle. */
    static String nom(String cle, String id) {
        String k = id == null || id.isEmpty() ? cle : id;
        if (k == null) return "";
        String n = NOMS.get(k);
        if (n != null) return n;
        if (cle != null && !cle.isEmpty() && I18n.exists(cle)) n = I18n.get(cle);
        else if (cle != null && !cle.isEmpty() && !cle.contains(".") && !cle.contains(":")) n = cle;
        else n = lisible(id);
        NOMS.put(k, n);
        return n;
    }

    /** « tacz:hk_mp5a5 » -> « Hk Mp5a5 » (repli). */
    static String lisible(String id) {
        if (id == null || id.isEmpty()) return "";
        int i = id.indexOf(':');
        String p = i >= 0 ? id.substring(i + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String m : p.split("_")) {
            if (m.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(m.charAt(0))).append(m.substring(1));
        }
        return sb.toString();
    }

    /** Libelle d'un type d'arme TaCZ (cle duel.type.<type>, repli : le type). */
    static String type(String type) {
        String k = "duel.type." + type.toLowerCase(Locale.ROOT);
        return I18n.exists(k) ? I18n.get(k) : type.toUpperCase(Locale.ROOT);
    }

    /** Libelle d'un emplacement d'accessoire (cle duel.emplacement.<emp>). */
    static String emplacement(String emp) {
        String k = "duel.emplacement." + emp;
        return I18n.exists(k) ? I18n.get(k) : emp.toUpperCase(Locale.ROOT);
    }

    /** Vide le cache des noms (changement de langue, nouveau catalogue). */
    static void oublierNoms() {
        NOMS.clear();
    }
}
