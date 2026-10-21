package fr.heralisz.matchs.clashwar.client;

import fr.heralisz.matchs.clashwar.net.VueTableau;
import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ecran {@code /clashwar tableau} : le tableau du tournoi en grand (meme texture que le mur de la salle d'attente,
 * {@link TextureTableau}), mis a jour en direct ; survol d'une equipe = infobulle (clan, joueurs) ; clic sur un match en
 * direct = le regarder ({@code /clashwar regarder <tour> <match>}, revalide par le serveur). Echelle forcee
 * (canevas 640 x 360), fond en degrade, panneau verre, croix ; Echap ferme.
 */
public class EcranTableau extends Screen {
    private int px, py, pw, ph, bx, by, bw, bh;
    private int versionInfos = Integer.MIN_VALUE;
    /** Infobulle de chaque equipe (preparees au changement de la vue). */
    private final List<List<Component>> infos = new ArrayList<>();

    public EcranTableau() {
        super(Component.translatable("clashwar.ecran.tableau"));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(620, width - 16);
        ph = Math.min(344, height - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        bw = pw - 20;
        bh = bw / 2;
        if (bh > ph - 34) {
            bh = ph - 34;
            bw = bh * 2;
        }
        bx = px + (pw - bw) / 2;
        by = py + 26;
        addRenderableWidget(new HzIcon(px + pw - 22, py + 6, 14, Icons.CROIX, Component.translatable("clashwar.ecran.fermer"), this::onClose)
                .couleurs(Ui.MUTED, Ui.TEXT));
        SonsUi.ouvrir(0.6f);
    }

    @Override
    public void tick() {
        if (versionInfos != CwClient.version()) {
            versionInfos = CwClient.version();
            infos.clear();
            for (VueTableau.Equipe e : CwClient.vue().equipes()) {
                List<Component> l = new ArrayList<>();
                l.add(Ui.lit(e.libelle() + "  " + e.nomClan(), Ui.BOLD).withStyle(s -> s.withColor(e.couleur())));
                for (String j : e.joueurs()) l.add(Ui.lit(j, Ui.FONT));
                if (e.eliminee()) l.add(Ui.lit(PeintreTableau.tr("clashwar.ecran.eliminee"), Ui.SMALL).withStyle(s -> s.withColor(Ui.DANGER)));
                infos.add(l);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        Ui.spaced(g, font, PeintreTableau.tr("clashwar.ecran.tableau").toUpperCase(Locale.ROOT), Ui.SMALL, px + 12, py + 10, Ui.argb(Ui.MUTED, 255), 1.2f);
        Ui.softOutline(g, bx, by, bw, bh, Ui.argb(0xFFFFFF, 30));
        TextureTableau.dessinerGui(g, bx, by, bx + bw, by + bh, 1f);
        if (!TextureTableau.prete()) {
            Ui.spacedCentered(g, font, "…", Ui.SMALL, bx + bw / 2f, by + bh / 2f, Ui.argb(Ui.FAINT, 255), 1f);
        }
        super.render(g, mx, my, pt);
        // survol : equipe sous la souris (coordonnees du canevas du tableau)
        PeintreTableau.Zone z = zone(mx, my);
        if (z != null) {
            VueTableau.Equipe e = CwClient.vue().equipes().get(Math.min(z.equipe(), CwClient.vue().equipes().size() - 1));
            List<Component> l = infos.get(z.equipe());
            if (z.direct()) {
                l = new ArrayList<>(l);
                l.add(Ui.lit(PeintreTableau.tr("clashwar.ecran.regarder"), Ui.SMALL).withStyle(s -> s.withColor(Ui.DANGER)));
            }
            Ui.tooltip(g, font, l, mx, my, e.couleur());
        }
    }

    /** Ligne d'equipe sous la souris, ou null. */
    @javax.annotation.Nullable
    private PeintreTableau.Zone zone(double mx, double my) {
        if (mx < bx || my < by || mx >= bx + bw || my >= by + bh) return null;
        float cx = (float) (mx - bx) * PeintreTableau.L / bw, cy = (float) (my - by) * PeintreTableau.H / bh;
        for (PeintreTableau.Zone z : PeintreTableau.zones()) {
            if (cx >= z.x() && cy >= z.y() && cx < z.x() + z.w() && cy < z.y() + z.h() && z.equipe() < infos.size()) return z;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton == 0) {
            PeintreTableau.Zone z = zone(mx, my);
            if (z != null && z.direct() && minecraft != null && minecraft.player != null) {
                SonsUi.clic(0.6f);
                minecraft.player.connection.sendCommand("clashwar regarder " + z.tour() + " " + z.index());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        SonsUi.fermer(0.5f);
        super.removed();
    }
}
