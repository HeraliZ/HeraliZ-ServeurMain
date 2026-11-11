package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.LigneHistorique;
import fr.heralisz.clans.model.SaisonLigne;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Onglet Historique (11/11, demande du proprietaire : « une page Historique avec toutes les logs de son clan », triable
 * par categorie ; rendus : modules/clans/outils/apercu_historique.py) : titre et nombre de lignes, puces des categories
 * (Tout, Points, Combats, Membres, Base, Clan), pages de 40 lignes (« ← 1 / 3 → »), liste defilante : texte de la ligne,
 * « il y a 9 min · par Dziga », points de saison gagnes ou perdus et leur categorie. Infobulle d'une ligne : texte entier,
 * date et heure, joueur, points exacts.
 * <p>Le serveur garde l'historique, filtre et coupe les pages ({@code historique.Historique}) : l'onglet demande la page
 * affichee (HistoriqueC2S) a l'ouverture, au changement de filtre ou de page, puis toutes les 5 s tant qu'il est ouvert ;
 * la liste lit la derniere page recue a chaque image (pas de reconstruction de l'ecran a chaque reponse).
 */
public final class HistoriqueTab extends Tab {
    /** Mesures (les memes dans apercu_historique.py) : puces a y + 16, filet a y + 34, liste a y + 38, lignes de 22. */
    private static final int PUCES_Y = 16, PUCE_H = 12, FILET_Y = 34, LISTE_Y = 38, LIGNE = 22, FLECHE = 12;
    /** Filtres, dans l'ordre des puces : l'indice moins 1 est la categorie de {@link LigneHistorique} (TOUT = -1). */
    private static final String[] FILTRES = {"tout", "points", "combats", "membres", "base", "clan"};
    /** Couleur de chaque puce, et du point devant chaque ligne de sa categorie. */
    private static final int[] COULEURS = {Ui.TEXT, Ui.OR, Ui.DANGER, Ui.ACCENT_NEUTRE, 0x4FB6C9, 0x9FA8B8};
    /** Points de saison d'une ligne : couleurs et noms des categories de l'onglet Saison, plus « toutes » (prise de coeur). */
    private static final int[] COULEURS_SAISON = {SaisonTab.COULEURS[0], SaisonTab.COULEURS[1], SaisonTab.COULEURS[2], Ui.OR};
    private static final String[] NOMS_SAISON = {SaisonTab.CATEGORIES[0], SaisonTab.CATEGORIES[1], SaisonTab.CATEGORIES[2],
            "gui.historique.toutes"};
    /**
     * Rafraichissement de la page affichee ; nouvel essai quand la reponse attendue n'est pas venue ; onglet rouvert apres
     * ce delai : retour a la premiere page (les lignes les plus recentes).
     */
    private static final long RAFRAICHIR_MS = 5000, RELANCE_MS = 600, OUBLI_MS = 60_000;
    /** Filtre (indice dans FILTRES) et page affiches, gardes entre deux reconstructions de l'ecran. */
    private static int filtre, page;
    private static long derniereDemande;

    private Liste liste;

    @Override
    protected void construire() {
        long attente = System.currentTimeMillis() - derniereDemande;
        if (attente > OUBLI_MS) page = 0;
        if (attente > RELANCE_MS) demander();
        liste = new Liste(x, y + LISTE_Y, w, h - LISTE_Y - 4);
        Packets.HistoriqueS2C recue = affichee();
        liste.setContentHeight(recue == null ? 0 : recue.lignes().size() * LIGNE);
        ecran.ajouter(liste);
    }

    /** Demande la page affichee au serveur (banc d'essai : l'etat factice la coupe lui-meme ; hors connexion : rien). */
    private static void demander() {
        derniereDemande = System.currentTimeMillis();
        if (ClientClans.estFaux()) ClientClans.fauxHistorique(filtre - 1, page);
        else if (Minecraft.getInstance().getConnection() != null) envoyer(new Packets.HistoriqueC2S(filtre - 1, page));
    }

    /** La derniere page recue si c'est celle qu'on affiche (meme filtre, meme page), null sinon (« Chargement… »). */
    @Nullable
    private static Packets.HistoriqueS2C affichee() {
        Packets.HistoriqueS2C h = ClientClans.historique();
        return h != null && h.categorie() == filtre - 1 && h.page() == page ? h : null;
    }

    @Override
    public void tick() {
        Packets.HistoriqueS2C h = ClientClans.historique();
        // la page demandee n'existe plus (lignes trop vieilles effacees entre-temps) : le serveur a repondu par la derniere
        if (h != null && h.categorie() == filtre - 1 && page >= h.pages()) page = h.page();
        long attente = System.currentTimeMillis() - derniereDemande;
        if (attente > (affichee() == null ? RELANCE_MS : RAFRAICHIR_MS)) demander();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Packets.HistoriqueS2C recue = affichee();
        liste.setContentHeight(recue == null ? 0 : recue.lignes().size() * LIGNE);
        // ---- titre et nombre de lignes du filtre
        String titre = tr("gui.historique.titre");
        Ui.spaced(g, font, titre, Ui.BOLD, x, y, Ui.argb(Ui.TEXT, 255), 1.2f);
        int tw = (int) Ui.spacedWidth(font, titre.toUpperCase(Locale.ROOT), Ui.BOLD, 1.2f);
        if (recue != null) {
            String sous = tr("gui.historique.sous_titre", Ui.nombre(recue.total()));
            g.drawString(font, Ui.lit(Ui.ellipsis(font, sous, Ui.SMALL, w - tw - 12 - 110), Ui.SMALL), x + tw + 12, y + 1,
                    Ui.argb(Ui.FAINT, 255), false);
        }
        // ---- puces des categories (meme dessin que celles de l'onglet Saison)
        int px = x;
        for (int i = 0; i < FILTRES.length; i++) {
            String nom = tr("gui.historique.cat." + FILTRES[i]);
            int pw = puce(nom);
            boolean actif = i == filtre, survol = !actif && dans(mouseX, mouseY, px, y + PUCES_Y, pw, PUCE_H);
            Ui.soft(g, px, y + PUCES_Y, pw, PUCE_H, Ui.argb(COULEURS[i], actif ? 60 : survol ? 30 : 12));
            Ui.softOutline(g, px, y + PUCES_Y, pw, PUCE_H, Ui.argb(COULEURS[i], actif ? 200 : 50));
            Ui.spacedCentered(g, font, nom, Ui.SMALL, px + pw / 2f, y + PUCES_Y + 2, Ui.argb(actif ? Ui.TEXT : Ui.MUTED, 255), 0.8f);
            px += pw + 5;
        }
        // ---- pages, a droite : ← 1 / 3 →
        int pages = pages(recue);
        String num = tr("gui.historique.page", page + 1, pages);
        int nw = font.width(Ui.lit(num, Ui.SMALL)), xd = x + w - FLECHE, xg = xd - 8 - nw - 8 - FLECHE;
        fleche(g, xg, "←", "gui.historique.plus_recentes", page > 0, mouseX, mouseY);
        fleche(g, xd, "→", "gui.historique.plus_anciennes", page < pages - 1, mouseX, mouseY);
        Ui.centered(g, font, Ui.lit(num, Ui.SMALL), xd - 8 - nw / 2f, y + PUCES_Y + 2.5f, Ui.argb(Ui.MUTED, 255));
        Ui.hairline(g, x, x + w, y + FILET_Y, 0xFFFFFF, 22, false, false);
        if (recue == null || recue.lignes().isEmpty()) {
            Component c = Ui.tr(Ui.SMALL, recue == null ? "gui.historique.chargement" : "gui.historique.vide");
            Ui.centered(g, font, c, x + w / 2f, y + LISTE_Y + 60, Ui.argb(Ui.FAINT, 255));
        }
    }

    /** Nombre de pages du filtre (tant que la page n'est pas recue : celles deja ouvertes). */
    private static int pages(@Nullable Packets.HistoriqueS2C recue) {
        return recue == null ? page + 1 : Math.max(1, recue.pages());
    }

    private int puce(String nom) {
        return (int) Math.ceil(Ui.spacedWidth(font, nom.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f)) + 12;
    }

    /** Fleche de page : pleine si elle mene quelque part (infobulle « Plus récentes » / « Plus anciennes »), eteinte sinon. */
    private void fleche(GuiGraphics g, int fx, String sens, String bulle, boolean actif, int mouseX, int mouseY) {
        int fy = y + PUCES_Y;
        boolean survol = actif && dans(mouseX, mouseY, fx, fy, FLECHE, FLECHE);
        Ui.soft(g, fx, fy, FLECHE, FLECHE, Ui.argb(0xFFFFFF, !actif ? 5 : survol ? 30 : 14));
        Ui.softOutline(g, fx, fy, FLECHE, FLECHE, Ui.argb(0xFFFFFF, !actif ? 14 : survol ? 90 : 40));
        Ui.centered(g, font, Ui.lit(sens, Ui.BOLD), fx + FLECHE / 2f, fy + 2, Ui.argb(actif ? Ui.TEXT : Ui.FAINT, 255));
        if (survol) ecran.infobulle(List.of(Ui.lit(tr(bulle), Ui.FONT)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int px = x;
        for (int i = 0; i < FILTRES.length; i++) {
            int pw = puce(tr("gui.historique.cat." + FILTRES[i]));
            if (dans(mx, my, px, y + PUCES_Y, pw, PUCE_H)) {
                if (i != filtre) {
                    filtre = i;
                    page = 0;
                    changer();
                }
                return true;
            }
            px += pw + 5;
        }
        int pages = pages(affichee());
        String num = tr("gui.historique.page", page + 1, pages);
        int nw = font.width(Ui.lit(num, Ui.SMALL)), xd = x + w - FLECHE, xg = xd - 8 - nw - 8 - FLECHE;
        if (page > 0 && dans(mx, my, xg, y + PUCES_Y, FLECHE, FLECHE)) {
            page--;
            changer();
            return true;
        }
        if (page < pages - 1 && dans(mx, my, xd, y + PUCES_Y, FLECHE, FLECHE)) {
            page++;
            changer();
            return true;
        }
        return false;
    }

    /** Nouveau filtre ou nouvelle page : demande au serveur, liste remise en haut. */
    private void changer() {
        Ui.son(Sons.UI_CLIC, 0.5f);
        demander();
        ecran.reconstruireOnglet();
    }

    // ------------------------------------------------------------------ textes d'une ligne
    /** « +40 », « +0,37 », « -1 » ; vide sans points. */
    private static String points(long centiemes) {
        if (centiemes == 0) return "";
        return (centiemes > 0 ? "+" : "-") + SaisonTab.nombre(Math.abs(centiemes) / (double) SaisonLigne.CENTIEMES);
    }

    /** Couleur des points : rouge s'ils sont perdus, sinon celle de leur categorie de saison. */
    private static int couleurPoints(LigneHistorique l) {
        if (l.centiemes() < 0) return Ui.DANGER;
        return l.saison() >= 0 && l.saison() < COULEURS_SAISON.length ? COULEURS_SAISON[l.saison()] : Ui.MUTED;
    }

    private static String nomSaison(LigneHistorique l) {
        return l.saison() >= 0 && l.saison() < NOMS_SAISON.length ? tr(NOMS_SAISON[l.saison()]) : "";
    }

    /** Infobulle d'une ligne : texte entier, « le 11/11 à 14:32 », joueur, points de saison et leur categorie. */
    private static List<Component> infobulle(LigneHistorique l, String texte, String pts) {
        LocalDateTime d = LocalDateTime.ofInstant(Instant.ofEpochMilli(l.quand()), ZoneId.systemDefault());
        String jour = String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
        String heure = String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
        List<Component> lignes = new ArrayList<>(4);
        lignes.add(Ui.lit(texte, Ui.BOLD));
        lignes.add(Ui.lit(tr("gui.historique.le", jour, heure), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
        if (!l.qui().isEmpty()) lignes.add(Ui.lit(tr("gui.historique.par", l.qui()), Ui.FONT));
        if (!pts.isEmpty()) {
            int couleur = couleurPoints(l);
            lignes.add(Ui.lit(tr("gui.historique.infobulle.points", pts, nomSaison(l)), Ui.FONT).withStyle(st -> st.withColor(couleur)));
        }
        return lignes;
    }

    // ------------------------------------------------------------------ liste
    private final class Liste extends HzScroll {
        Liste(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Packets.HistoriqueS2C recue = affichee();
            if (recue == null) return;
            List<LigneHistorique> l = recue.lignes();
            int premiere = Math.max(0, getScroll() / LIGNE), derniere = Math.min(l.size(), (getScroll() + getHeight()) / LIGNE + 1);
            int wl = getWidth() - 2;
            long maintenant = ClientClans.maintenantServeur();
            List<Component> info = null;
            for (int i = premiere; i < derniere; i++) {
                LigneHistorique li = l.get(i);
                int ly = getY() + i * LIGNE;
                boolean survol = dans(mouseX, mouseY, getX(), ly, wl, LIGNE);
                if (survol) Ui.soft(g, getX(), ly, wl, LIGNE, Ui.argb(0xFFFFFF, 13));
                Ui.point(g, getX() + 2, ly + 5, COULEURS[Math.max(0, Math.min(COULEURS.length - 1, li.categorie() + 1))], 255);
                String pts = points(li.centiemes());
                Component pc = pts.isEmpty() ? null : Ui.lit(pts, Ui.BOLD);
                String texte = li.texte().getString();
                int place = wl - 12 - (pc == null ? 0 : font.width(pc) + 34);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, texte, Ui.FONT, place), Ui.FONT), getX() + 12, ly + 3, Ui.argb(Ui.TEXT, 255), false);
                String sous = Ui.ilYA(Math.max(0, maintenant - li.quand())) + (li.qui().isEmpty() ? "" : " · " + tr("gui.historique.par", li.qui()));
                g.drawString(font, Ui.lit(Ui.ellipsis(font, sous, Ui.SMALL, wl - 12 - 40), Ui.SMALL), getX() + 12, ly + 13, Ui.argb(Ui.FAINT, 255), false);
                if (pc != null) {
                    int couleur = Ui.argb(couleurPoints(li), 255);
                    Ui.right(g, font, pc, getX() + wl, ly + 3, couleur);
                    Ui.right(g, font, Ui.lit(nomSaison(li).toUpperCase(Locale.ROOT), Ui.SMALL), getX() + wl, ly + 13, couleur);
                }
                if (i < l.size() - 1) Ui.hairline(g, getX(), getX() + wl, ly + LIGNE - 1, 0xFFFFFF, 14, false, false);
                if (survol) info = infobulle(li, texte, pts);
            }
            if (info != null) ecran.infobulle(info);
        }
    }
}
