package fr.heralisz.clans.client.gui;

import fr.heralisz.clans.client.ClansClient;
import fr.heralisz.clans.client.ClansKeys;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Echelle;
import fr.heralisz.clans.client.gui.kit.HzArea;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzField;
import fr.heralisz.clans.client.gui.kit.Hote;
import fr.heralisz.clans.client.gui.kit.HzIcon;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.client.gui.tabs.ApercuTab;
import fr.heralisz.clans.client.gui.tabs.BanqueTab;
import fr.heralisz.clans.client.gui.tabs.ClassementTab;
import fr.heralisz.clans.client.gui.tabs.MembresTab;
import fr.heralisz.clans.client.gui.tabs.ParametresTab;
import fr.heralisz.clans.client.gui.tabs.PermissionsTab;
import fr.heralisz.clans.client.gui.tabs.NiveauTab;
import fr.heralisz.clans.client.gui.tabs.QuetesTab;
import fr.heralisz.clans.client.gui.tabs.Tab;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.InvitationVue;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ecran principal du clan (echelle forcee, canevas 640 x 360, panneau min(560, l-32) x min(320, h-24)) : en-tete
 * (banniere, nom, tag, niveau, barre d'XP, en ligne, croix), barre d'onglets APERCU · MEMBRES · BANQUE · QUETES ·
 * CLASSEMENT · PERMISSIONS (chef) · PARAMETRES, contenu avec marge 16. Sans clan : invitations en attente (cartes
 * dans une liste defilante, 4 visibles, ACCEPTER / REFUSER) ou « Vous n'avez pas de clan » + FONDER UN CLAN.
 * <p>Se reconstruit seulement quand l'etat qu'il affiche change ({@link ClientClans#versionSnapshot()}, invitations
 * sans clan, classement sur cet onglet) ; les tags et la barre de zone ne le touchent pas. Une reconstruction garde le
 * champ focalise, le defilement des listes et les brouillons.
 */
public class ClanScreen extends Screen implements Hote {
    /** Onglets de l'ecran (ordre de la barre). */
    public enum Onglet {
        APERCU, MEMBRES, BANQUE, QUETES, NIVEAU, CLASSEMENT, PERMISSIONS, PARAMETRES;

        public String libelle() {
            return "gui.onglet." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final int ENTETE = 44, ONGLETS = 18;
    /** Ecran sans clan : en-tete (titre + section), hauteur d'une carte d'invitation, pied (FONDER + cout). */
    private static final int SANS_CLAN_ENTETE = 58, INVITATION_H = 44, SANS_CLAN_PIED = 44, INVITATIONS_VISIBLES = 4;
    private Onglet onglet;
    private int px, py, pw, ph;
    private int vSnapshot = -1, vInvitations = -1, vClassement = -1;
    @Nullable
    private Tab tab;
    private final List<AbstractWidget> tickables = new ArrayList<>();
    private final int[] ongletX = new int[Onglet.values().length], ongletW = new int[Onglet.values().length];
    private final Map<String, String> brouillons = new HashMap<>();
    @Nullable
    private List<Component> infobulle;
    private long ouvertLe = System.currentTimeMillis();

    public ClanScreen() {
        this(Onglet.APERCU);
    }

    public ClanScreen(Onglet onglet) {
        super(Component.translatable("gui.titre"));
        this.onglet = onglet;
    }

    /** Change d'onglet (barre d'onglets, banc d'essai) : brouillons et defilements oublies. */
    public void onglet(Onglet o) {
        if (o == Onglet.PERMISSIONS) {
            ClanSnapshot s = ClientClans.snapshot();
            if (s != null && !voitPermissions(s)) return;
        }
        this.onglet = o;
        brouillons.clear();
        super.rebuildWidgets();
    }

    public Onglet onglet() {
        return onglet;
    }

    /** L'onglet affiche (null sans clan). */
    @Nullable
    public Tab tab() {
        return tab;
    }

    /** L'onglet Permissions est pour le chef et pour ceux qui peuvent GERER_PERMISSIONS (reglages par joueur). */
    public static boolean voitPermissions(ClanSnapshot s) {
        return s.jeSuisChef() || s.jePeux(fr.heralisz.clans.model.Perm.GERER_PERMISSIONS);
    }

    /** Reconstruit l'onglet courant de zero (changement de mode d'un onglet : defilements oublies). */
    public void reconstruireOnglet() {
        super.rebuildWidgets();
    }

    /** Ajoute un widget d'onglet (les champs sont tickes pour le curseur). */
    @Override
    public <T extends AbstractWidget> T ajouter(T w) {
        addRenderableWidget(w);
        if (w instanceof HzField || w instanceof HzArea) tickables.add(w);
        return w;
    }

    /** Brouillon conserve entre deux reconstructions (le snapshot arrive pendant la saisie). */
    public String brouillon(String cle, String defaut) {
        String v = brouillons.get(cle);
        return v == null ? defaut : v;
    }

    public void garderBrouillon(String cle, @Nullable String valeur) {
        if (valeur == null) brouillons.remove(cle);
        else brouillons.put(cle, valeur);
    }

    /** Infobulle a dessiner a la fin de l'image (posee par un onglet pendant son rendu). */
    @Override
    public void infobulle(List<Component> lignes) {
        infobulle = lignes;
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        Echelle.forcer(this);
        tickables.clear();
        ClanSnapshot s = ClientClans.snapshot();
        List<InvitationVue> inv = ClientClans.invitations();
        if (s != null) {
            pw = Math.min(560, width - 32);
            ph = Math.min(320, height - 24);
        } else if (inv.isEmpty()) {                                    // « vous n'avez pas de clan »
            pw = Math.min(380, width - 32);
            ph = Math.min(190, height - 24);
        } else {                                                       // invitations : hauteur selon leur nombre (4 au plus)
            pw = Math.min(560, width - 32);
            ph = Math.min(SANS_CLAN_ENTETE + Math.min(inv.size(), INVITATIONS_VISIBLES) * INVITATION_H + SANS_CLAN_PIED, height - 24);
        }
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        vSnapshot = ClientClans.versionSnapshot();
        vInvitations = ClientClans.versionInvitations();
        vClassement = ClientClans.versionClassement();
        addRenderableWidget(new HzIcon(px + pw - 24, py + 10, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        tab = null;
        if (s != null) {
            if (onglet == Onglet.PERMISSIONS && !voitPermissions(s)) onglet = Onglet.APERCU;
            // positions des onglets
            int ox = px + Ui.MARGE;
            for (Onglet o : Onglet.values()) {
                if (o == Onglet.PERMISSIONS && !voitPermissions(s)) {
                    ongletW[o.ordinal()] = 0;
                    continue;
                }
                int lw = (int) Ui.spacedWidth(font, Component.translatable(o.libelle()).getString().toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f);
                ongletX[o.ordinal()] = ox;
                ongletW[o.ordinal()] = lw;
                ox += lw + 16;
            }
            tab = switch (onglet) {
                case APERCU -> new ApercuTab();
                case MEMBRES -> new MembresTab();
                case BANQUE -> new BanqueTab();
                case QUETES -> new QuetesTab();
                case NIVEAU -> new NiveauTab();
                case CLASSEMENT -> new ClassementTab();
                case PERMISSIONS -> new PermissionsTab();
                case PARAMETRES -> new ParametresTab();
            };
            int cy = py + ENTETE + ONGLETS + 10;
            tab.init(this, s, px + Ui.MARGE, cy, pw - 2 * Ui.MARGE, py + ph - 14 - cy);
        } else {
            construireSansClan(inv);
        }
    }

    private void construireSansClan(List<InvitationVue> inv) {
        int accent = Ui.accent();
        if (inv.isEmpty()) {
            addRenderableWidget(new HzButton(px + pw / 2 - 70, py + ph / 2 + 4, 140, 20, Component.translatable("gui.fonder"), accent,
                    () -> ClansClient.ouvrir("creation")).look(HzButton.Look.PRIMARY));
        } else {
            ListeInvitations liste = new ListeInvitations(px + Ui.MARGE, py + SANS_CLAN_ENTETE, pw - 2 * Ui.MARGE,
                    ph - SANS_CLAN_ENTETE - SANS_CLAN_PIED, inv);
            liste.accent(accent);
            addRenderableWidget(liste);
            addRenderableWidget(new HzButton(px + Ui.MARGE, py + ph - 30, 110, 14, Component.translatable("gui.fonder"), accent,
                    () -> ClansClient.ouvrir("creation")).look(HzButton.Look.LINK));
        }
    }

    private void repondre(InvitationVue i, boolean accepte) {
        if (minecraft != null && minecraft.getConnection() != null) ClansNet.toServer(new Packets.ReponseInvitationC2S(i.clanId(), accepte));
        else fr.heralisz.clans.HeraliszClans.LOG.info("[clans] (hors ligne) reponse invitation {} : {}", i.tag(), accepte);
    }

    /**
     * Reconstruction sur un nouvel etat : le champ focalise, le defilement des listes (dans l'ordre des enfants) et les
     * brouillons survivent ; le focus restaure ne rearme pas « la premiere frappe remplace ».
     */
    @Override
    protected void rebuildWidgets() {
        int focus = tickables.indexOf(getFocused());
        List<Integer> defilements = new ArrayList<>();
        for (GuiEventListener c : children()) if (c instanceof HzScroll sc) defilements.add(sc.getScroll());
        super.rebuildWidgets();
        if (focus >= 0 && focus < tickables.size()) {
            AbstractWidget w = tickables.get(focus);
            setFocused(w);
            if (w instanceof HzField f) f.annulerRemplacement();
        }
        int k = 0;
        for (GuiEventListener c : children()) {
            if (c instanceof HzScroll sc && k < defilements.size()) sc.setScroll(defilements.get(k++));
        }
    }

    @Override
    public void tick() {
        boolean sansClan = ClientClans.snapshot() == null;
        if (ClientClans.versionSnapshot() != vSnapshot
                || (sansClan && ClientClans.versionInvitations() != vInvitations)
                || (!sansClan && onglet == Onglet.CLASSEMENT && ClientClans.versionClassement() != vClassement)) {
            rebuildWidgets();
        }
        for (AbstractWidget w : tickables) {
            if (w instanceof HzField f) f.tick();
            else if (w instanceof HzArea a) a.tick();
        }
        if (tab != null) tab.tick();
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        infobulle = null;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        ClanSnapshot s = ClientClans.snapshot();
        if (s != null) {
            entete(g, s);
            barreOnglets(g, s, mouseX, mouseY);
            if (tab != null) tab.render(g, mouseX, mouseY, partialTick);
        } else {
            sansClan(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (infobulle != null) Ui.tooltip(g, font, infobulle, mouseX, mouseY, Ui.accent(), px + pw);
    }

    private void entete(GuiGraphics g, ClanSnapshot s) {
        int accent = s.accent();
        BannerArt.draw(g, s.banniere(), px + Ui.MARGE, py + 6, 24, 32);
        int tx = px + Ui.MARGE + 34;
        Component nom = Ui.lit(s.nom(), Ui.BOLD);
        g.drawString(font, nom, tx, py + 10, Ui.argb(Ui.TEXT, 255), false);
        int nw = font.width(nom);
        Component tag = Ui.lit("[" + s.tag() + "]", Ui.FONT);
        g.drawString(font, tag, tx + nw + 6, py + 10, Ui.argb(Ui.MUTED, 255), false);
        Ui.chip(g, font, Component.translatable("gui.niveau", s.niveau()).getString(), tx + nw + 6 + font.width(tag) + 8, py + 9, accent);
        float ratio = s.xpSuivant() <= 0 ? 1 : s.xp() / (float) s.xpSuivant();
        Ui.bar(g, tx, py + 24, 150, 2, ratio, accent);
        String xp = s.xpSuivant() <= 0 ? Component.translatable("gui.niveau_max").getString()
                : Ui.nombre(s.xp()) + " / " + Ui.nombre(s.xpSuivant()) + " XP";
        g.drawString(font, Ui.lit(xp, Ui.SMALL), tx, py + 29, Ui.argb(Ui.MUTED, 255), false);
        // a droite : en ligne
        int enLigne = s.enLigne();
        Ui.point(g, px + pw - 40 - (int) Ui.spacedWidth(font, Component.translatable("gui.en_ligne", enLigne, s.membres().size()).getString()
                .toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) - 10, py + 15, Ui.OK, 255);
        Ui.spacedRight(g, font, Component.translatable("gui.en_ligne", enLigne, s.membres().size()).getString(), Ui.SMALL,
                px + pw - 40, py + 13, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
    }

    private void barreOnglets(GuiGraphics g, ClanSnapshot s, int mouseX, int mouseY) {
        int accent = s.accent();
        int ty = py + ENTETE + 6;
        for (Onglet o : Onglet.values()) {
            int i = o.ordinal();
            if (ongletW[i] == 0) continue;
            boolean actif = o == onglet;
            boolean hover = !actif && mouseX >= ongletX[i] - 6 && mouseX < ongletX[i] + ongletW[i] + 6 && mouseY >= ty - 5 && mouseY < ty + 12;
            String label = Component.translatable(o.libelle()).getString();
            Ui.spaced(g, font, label, Ui.SMALL, ongletX[i], ty, Ui.argb(actif ? Ui.TEXT : hover ? Ui.mix(Ui.MUTED, Ui.TEXT, 0.5f) : Ui.MUTED, 255), 1.0f);
            if (actif) g.fill(ongletX[i], ty + 10, ongletX[i] + ongletW[i], ty + 11, Ui.argb(accent, 230));
            else if (hover) g.fill(ongletX[i], ty + 10, ongletX[i] + ongletW[i], ty + 11, Ui.argb(0xFFFFFF, 40));
        }
        Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE + ONGLETS + 2, 0xFFFFFF, 22, true, true);
    }

    /** Cadre de l'ecran sans clan (les cartes d'invitation sont dessinees par {@link ListeInvitations}). */
    private void sansClan(GuiGraphics g) {
        Ui.spaced(g, font, Component.translatable("gui.titre").getString(), Ui.BOLD, px + Ui.MARGE, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        Ui.hairline(g, px + 8, px + pw - 8, py + 28, 0xFFFFFF, 40, true, true);
        List<InvitationVue> inv = ClientClans.invitations();
        Component cout = Component.translatable("gui.cout", Ui.jetons(coutCreation(), Ui.SMALL));
        if (inv.isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.BOLD, "gui.sans_clan"), px + pw / 2f, py + ph / 2f - 28, Ui.argb(Ui.TEXT, 255));
            Ui.centered(g, font, Ui.tr(Ui.FONT, "gui.sans_clan_sous"), px + pw / 2f, py + ph / 2f - 14, Ui.argb(Ui.MUTED, 255));
            Ui.centered(g, font, Ui.in(cout, Ui.SMALL), px + pw / 2f, py + ph / 2f + 30, Ui.argb(Ui.FAINT, 255));
            return;
        }
        Ui.section(g, font, Component.translatable("gui.invitations", inv.size()).getString(), px + Ui.MARGE, py + 40, pw - 2 * Ui.MARGE);
        Ui.hairline(g, px + 8, px + pw - 8, py + ph - 40, 0xFFFFFF, 30, true, true);
        g.drawString(font, Ui.in(cout, Ui.SMALL), px + Ui.MARGE + 118, py + ph - 27, Ui.argb(Ui.FAINT, 255), false);
    }

    private static long coutCreation() {
        return fr.heralisz.clans.client.ClientClans.coutCreation();
    }

    /**
     * Liste defilante des invitations : cartes (banniere, nom + tag colore, « invite par X · expire dans 3 min »,
     * n/max membres) et boutons ACCEPTER / REFUSER dessines ensemble en coordonnees de contenu, donc toujours alignes.
     */
    private final class ListeInvitations extends HzScroll {
        private final List<InvitationVue> inv;

        ListeInvitations(int x, int y, int w, int h, List<InvitationVue> inv) {
            super(x, y, w, h);
            this.inv = inv;
            int cy = y;
            for (InvitationVue i : inv) {
                int bx = x + w - 2 - 150;
                add(new HzButton(bx, cy + 11, 84, 18, Component.translatable("gui.action.accepter"), i.banniere().accent(),
                        () -> repondre(i, true)).look(HzButton.Look.PRIMARY));
                add(new HzButton(bx + 92, cy + 13, 56, 14, Component.translatable("gui.action.refuser"), Ui.accent(),
                        () -> repondre(i, false)).look(HzButton.Look.LINK));
                cy += INVITATION_H;
            }
            setContentHeight(inv.size() * INVITATION_H - 4);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            long age = System.currentTimeMillis() - ClientClans.invitationsRecuesLe();
            int cw = getWidth() - 2, haut = getY() + getScroll(), bas = haut + getHeight();
            for (int k = 0; k < inv.size(); k++) {
                int y = getY() + k * INVITATION_H;
                if (y + 40 < haut || y > bas) continue;
                InvitationVue i = inv.get(k);
                Ui.soft(g, getX(), y, cw, 40, Ui.argb(0xFFFFFF, 8));
                Ui.softOutline(g, getX() + 1, y + 1, cw - 2, 38, Ui.argb(0xFFFFFF, 14));
                BannerArt.draw(g, i.banniere(), getX() + 8, y + 4, 24, 32);
                int tx = getX() + 40;
                Component nom = Ui.lit(i.nom(), Ui.BOLD);
                g.drawString(font, nom, tx, y + 7, Ui.argb(Ui.TEXT, 255), false);
                g.drawString(font, Ui.lit("[" + i.tag() + "]", Ui.FONT), tx + font.width(nom) + 6, y + 7, Ui.argb(i.banniere().accent(), 255), false);
                long reste = Math.max(0, i.resteMs() - age);
                g.drawString(font, Ui.tr(Ui.SMALL, "gui.invite_par", i.auteurNom(), Ui.duree(reste)), tx, y + 19, Ui.argb(Ui.MUTED, 255), false);
                g.drawString(font, Ui.tr(Ui.SMALL, "gui.invitation_membres", i.membres(), i.membresMax()), tx, y + 29, Ui.argb(Ui.FAINT, 255), false);
            }
            super.renderContent(g, mouseX, mouseY, partialTick);
        }
    }

    // ------------------------------------------------------------------ evenements
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ClanSnapshot s = ClientClans.snapshot();
        if (s != null && button == 0) {
            int ty = py + ENTETE + 6;
            if (my >= ty - 5 && my < ty + 12) {
                for (Onglet o : Onglet.values()) {
                    int i = o.ordinal();
                    if (ongletW[i] == 0 || o == onglet) continue;
                    if (mx >= ongletX[i] - 6 && mx < ongletX[i] + ongletW[i] + 6) {
                        Ui.son(Sons.UI_CLIC, 0.5f);
                        onglet(o);
                        return true;
                    }
                }
            }
            if (tab != null && tab.mouseClicked(mx, my, button)) {
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Le relachement va a tous les enfants (pas seulement a celui sous la souris) : un maintien lache hors de sa liste s'annule. */
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        setDragging(false);
        boolean pris = false;
        for (GuiEventListener c : children()) pris |= c.mouseReleased(mx, my, button);
        return pris;
    }

    private boolean saisieEnCours() {
        GuiEventListener f = getFocused();
        if (f instanceof HzScroll sc) f = sc.getFocused();
        return (f instanceof HzField || f instanceof HzArea) && ((AbstractWidget) f).isFocused();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean saisie = saisieEnCours();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (saisie) {
                setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        if (!saisie && ClansKeys.OUVRIR.matches(key, scan) && System.currentTimeMillis() - ouvertLe > 300) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Ui.son(Sons.UI_FERMER, 0.6f);
        super.onClose();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
