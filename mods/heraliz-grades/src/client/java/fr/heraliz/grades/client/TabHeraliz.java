package fr.heraliz.grades.client;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nouveau tab HeraliZ (maquette validée) : panneau « verre » en haut de l'écran, à la taille du contenu, le staff puis les
 * survivants (triés par grade : Roi de la Zone, Traqueur, Rescapé, Survivant), sur 1 colonne jusqu'à 8 joueurs, 2 jusqu'à 40,
 * puis en mode compact (20 joueurs par colonne, jusqu'à 6 colonnes : tout le monde est visible) ; pour chacun une barre à la couleur du grade, le grade, le tag de clan, le pseudo, l'icône de voix de CaraCrea
 * (lue par réflexion) et le ping en barres. Grades et clans viennent du serveur ({@link ReseauGrades.InfosTab}) ; le
 * pseudo et le ping, de la liste de Minecraft. Remplace la liste de Minecraft (même touche).
 */
public final class TabHeraliz {
    private static final Map<UUID, ReseauGrades.LigneTab> INFOS = new HashMap<>();
    /** Au-delà de 40 joueurs : mode compact (lignes plus fines, grade en barre de couleur seule, 20 joueurs par colonne). */
    private static final int SEUIL_COMPACT = 40, MAX_LIGNES_COLONNE = 20, MAX_COLONNES = 6;
    private static int LIGNE_H = 11, ECART = 8;
    private static boolean compact;
    private static Method voix;
    private static boolean voixAbsente;

    private TabHeraliz() {
    }

    static void recevoir(ReseauGrades.InfosTab t) {
        INFOS.clear();
        for (ReseauGrades.LigneTab l : t.lignes()) INFOS.put(l.uuid(), l);
    }

    private record Joueur(PlayerInfo info, String nom, Grade grade, String clan, int couleurClan) {
        boolean staff() {
            return grade.colonneStaff;
        }

        /** Tri : staff du plus haut au plus bas, puis boutique, puis Survivant ; puis le pseudo. */
        int poids() {
            return grade.ordre;
        }
    }

    static void avantOverlay(RenderGuiOverlayEvent.Pre e) {
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener co = mc.getConnection();
        if (mc.player == null || co == null || !mc.options.keyPlayerList.isDown()) return;
        dessiner(e.getGuiGraphics(), mc, co, e.getWindow().getGuiScaledWidth());
    }

    private static Component voix(UUID uuid) {
        if (voixAbsente) return null;
        try {
            if (voix == null) voix = Class.forName("com.caracrea.client.voice.TabIcons").getMethod("suffix", UUID.class);
            return (Component) voix.invoke(null, uuid);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            voixAbsente = true;
            return null;
        }
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.tab." + cle, args).getString();
    }

    private static void dessiner(GuiGraphics g, Minecraft mc, ClientPacketListener co, int largeurEcran) {
        Font f = mc.font;
        List<Joueur> tous = new ArrayList<>();
        for (PlayerInfo info : co.getListedOnlinePlayers()) {
            UUID uuid = info.getProfile().getId();
            ReseauGrades.LigneTab l = INFOS.get(uuid);
            Grade gr = l == null ? null : Grade.parId(l.grade());
            if (gr == null || gr.type == Grade.Type.SOUTIEN) gr = Grade.SURVIVANT;
            tous.add(new Joueur(info, info.getProfile().getName(), gr, l == null ? "" : l.clan(), l == null ? 0xFFFFFF : l.couleurClan()));
        }
        tous.sort(Comparator.comparingInt(Joueur::poids).reversed().thenComparing(j -> j.nom().toLowerCase(Locale.ROOT)));
        List<Joueur> staff = tous.stream().filter(Joueur::staff).toList();
        List<Joueur> autres = tous.stream().filter(j -> !j.staff()).toList();
        // tout le monde visible : 1 colonne jusqu'à 8 joueurs, 2 jusqu'à 40, puis mode compact à 20 joueurs par colonne
        compact = tous.size() > SEUIL_COMPACT;
        LIGNE_H = compact ? 9 : 11;
        ECART = compact ? 6 : 8;
        int cols = tous.size() <= 8 ? 1 : !compact ? 2 : Math.min(MAX_COLONNES, (tous.size() + MAX_LIGNES_COLONNE - 1) / MAX_LIGNES_COLONNE);
        int place = cols * MAX_LIGNES_COLONNE * (compact ? 1 : 100) - staff.size();
        int cachés = Math.max(0, autres.size() - place);
        if (cachés > 0) autres = autres.subList(0, place);
        int colW = compact ? 70 : 110;
        for (Joueur j : tous) colW = Math.max(colW, largeurLigne(f, mc, j));
        if (cols > 1) colW = Math.min(colW, (largeurEcran - 40 - (cols - 1) * ECART) / cols);
        float tw = Ui.spacedWidth(f, "HERALIZ", Ui.BOLD, 2.2f);
        String enLigne = t("en_ligne", String.valueOf(tous.size())), pingTxt = t("ping", "000");
        float wEnLigne = Ui.spacedWidth(f, enLigne.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f), wSous = Ui.spacedWidth(f, "MINECRAFT-DAYZ", Ui.SMALL, 1.0f);
        int interieur = Math.max(cols * colW + (cols - 1) * ECART, (int) (tw + 14 + wEnLigne));
        interieur = Math.max(interieur, (int) (Ui.spacedWidth(f, "PLAY.HERALIZ.FR", Ui.SMALL, 1.0f) + 14 + Ui.spacedWidth(f, pingTxt.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f)));
        colW = cols == 1 ? interieur : (interieur - (cols - 1) * ECART) / cols;
        boolean sousTitre = interieur >= tw + wSous + wEnLigne + 26;
        int w = Math.min(largeurEcran - 16, interieur + 24), x0 = (largeurEcran - w) / 2, y0 = 8;
        int lignesStaff = (staff.size() + cols - 1) / cols, lignesAutres = (autres.size() + cols - 1) / cols;
        int h = 10 + 12 + 8 + (staff.isEmpty() ? 0 : 12 + lignesStaff * LIGNE_H + 4) + 12 + lignesAutres * LIGNE_H + (cachés > 0 ? LIGNE_H : 0) + 8 + 12 + 6;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Ui.glass(g, x0, y0, w, h);
        int x = x0 + 12, y = y0 + 10;

        // en-tête
        Ui.spaced(g, f, "HERALIZ", Ui.BOLD, x, y, Ui.argb(Ui.TEXT, 255), 2.2f);
        if (sousTitre) Ui.spaced(g, f, "Minecraft-DayZ", Ui.SMALL, x + tw + 10, y + 1.5f, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.spacedRight(g, f, enLigne, Ui.SMALL, x0 + w - 12, y + 1.5f, Ui.argb(Ui.MUTED, 255), 1.0f);
        y += 14;
        Ui.hairline(g, x, x0 + w - 12, y, 0xFFFFFF, 60, false, true);
        y += 6;

        // sections
        if (!staff.isEmpty()) {
            Ui.spaced(g, f, t("staff", String.valueOf(staff.size())), Ui.SMALL, x, y, Ui.argb(Ui.MUTED, 255), 1.0f);
            y += 12;
            y = lignes(g, f, mc, staff, x, y, colW, cols) + 4;
        }
        Ui.spaced(g, f, t("survivants", String.valueOf(tous.size() - staff.size())), Ui.SMALL, x, y, Ui.argb(Ui.MUTED, 255), 1.0f);
        y += 12;
        y = lignes(g, f, mc, autres, x, y, colW, cols);
        if (cachés > 0) {
            Ui.spaced(g, f, t("plus", String.valueOf(cachés)), Ui.SMALL, x, y + 2, Ui.argb(Ui.FAINT, 255), 0.6f);
            y += LIGNE_H;
        }

        // pied
        y += 4;
        Ui.hairline(g, x, x0 + w - 12, y, 0xFFFFFF, 60, false, true);
        y += 6;
        Ui.spaced(g, f, "play.heraliz.fr", Ui.SMALL, x, y, Ui.argb(Ui.MUTED, 255), 1.0f);
        PlayerInfo moi = co.getPlayerInfo(mc.player.getUUID());
        if (moi != null) Ui.spacedRight(g, f, t("ping", String.valueOf(Math.max(0, moi.getLatency()))), Ui.SMALL, x0 + w - 12, y, Ui.argb(Ui.MUTED, 255), 1.0f);
        g.pose().popPose();
    }

    /** Largeur d'une ligne : barre, grade, clan, pseudo, voix, ping. */
    private static int largeurLigne(Font f, Minecraft mc, Joueur j) {
        int w = 9;
        if (!compact && j.grade() != Grade.SURVIVANT) w += (int) Ui.spacedWidth(f, j.grade().nomSansCouleur().getString().toUpperCase(Locale.ROOT), Ui.SMALL, 0.4f) + 5;
        if (!j.clan().isEmpty()) w += f.width(Ui.lit("[" + j.clan() + "]", Ui.BOLD)) + 4;
        w += f.width(Ui.lit(j.nom(), Ui.BOLD));
        Component v = voix(j.info().getProfile().getId());
        if (v != null) w += 3 + f.width(v);
        return w + 10 + 14;
    }

    /** Les joueurs sur les colonnes, ligne par ligne ; rend le y sous la dernière ligne. */
    private static int lignes(GuiGraphics g, Font f, Minecraft mc, List<Joueur> liste, int x, int y, int colW, int cols) {
        for (int i = 0; i < liste.size(); i++) {
            int cx = x + (i % cols) * (colW + ECART), cy = y + (i / cols) * LIGNE_H, haut = compact ? 0 : 1;
            Joueur j = liste.get(i);
            if ((i / cols) % 2 == 0) g.fill(cx, cy, cx + colW, cy + LIGNE_H - 1, Ui.argb(0xFFFFFF, 9));
            g.fill(cx + 3, cy + 2, cx + 5, cy + LIGNE_H - 2 - haut, Ui.argb(j.grade().couleur, 255));
            int tx = cx + 9;
            if (!compact && j.grade() != Grade.SURVIVANT) {
                String gr = j.grade().nomSansCouleur().getString().toUpperCase(Locale.ROOT);
                Ui.spaced(g, f, gr, Ui.SMALL, tx, cy + 2.5f, Ui.argb(j.grade().couleur, 255), 0.4f);
                tx += (int) Ui.spacedWidth(f, gr, Ui.SMALL, 0.4f) + 5;
            }
            if (!j.clan().isEmpty()) {
                tx = g.drawString(f, Ui.lit("[" + j.clan() + "]", Ui.BOLD), tx, cy + haut, Ui.argb(j.couleurClan(), 255), false) + 4;
            }
            boolean moi = mc.player != null && j.info().getProfile().getId().equals(mc.player.getUUID());
            Component v = voix(j.info().getProfile().getId());
            int placeNom = cx + colW - 14 - (v == null ? 0 : 3 + f.width(v)) - tx;
            String nom = Ui.ellipsis(f, j.nom(), moi ? Ui.BOLD : Ui.FONT, Math.max(12, placeNom));
            tx = g.drawString(f, Ui.lit(nom, moi ? Ui.BOLD : Ui.FONT), tx, cy + haut, moi ? 0xFFFFFFFF : Ui.argb(Ui.TEXT, 255), false);
            if (v != null) g.drawString(f, v, tx + 3, cy + haut, 0xFFFFFFFF, false);
            ping(g, cx + colW - 14, cy + 1 + haut, j.info().getLatency());
        }
        return y + ((liste.size() + cols - 1) / cols) * LIGNE_H;
    }

    /** Ping en 5 barres : vert (bon), ambre (moyen), rouge (mauvais). */
    private static void ping(GuiGraphics g, int x, int y, int ms) {
        int niveau = ms < 0 ? 0 : ms < 150 ? 5 : ms < 300 ? 4 : ms < 600 ? 3 : ms < 1000 ? 2 : 1;
        int c = niveau >= 4 ? Ui.OK : niveau == 3 ? Ui.AMBRE : Ui.DANGER;
        for (int i = 0; i < 5; i++) {
            int bh = 2 + i;
            g.fill(x + i * 2, y + 7 - bh, x + i * 2 + 1, y + 7, Ui.argb(c, i < niveau ? 255 : 45));
        }
    }
}
