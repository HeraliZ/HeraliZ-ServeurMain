package fr.heraliz.quetes.client;

import fr.heraliz.quetes.Contenu;
import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.HzButton;
import fr.heraliz.quetes.client.kit.HzField;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Écran d'un marchand (style des écrans de clan). Vendeur : une carte par article (icône, nom, rareté, prix, ACHETER ;
 * 19/11 : ACHETER ouvre le panneau de confirmation, avec la quantité, voir plus bas). Racheteur : l'inventaire du joueur,
 * on clique ce qu'on vend (prix au survol), total à droite, « Vendre la sélection » ou « Tout vendre ». Échanges (1.11.0, onglet à part, ou seul pour un marchand de troc) : une carte par
 * échange (objet donné × n → objet reçu × n, combien on en a, ÉCHANGER ; Maj + clic = 5 fois). Prix, solde et échanges
 * viennent du serveur, qui revérifie tout.
 * <p>
 * 18/11 (retour du propriétaire : « pas besoin d'avoir autant de texte, c'est très intuitif ») : plus d'aide du rachat ni
 * de pied (Maj + clic, objets abîmés) ; un échange dont le joueur n'a que des objets abîmés le dit sur sa carte, à la
 * place de « Vous en avez 0 / n » (le marchand ne les prend pas, {@code Echanges.possede}).
 * <p>
 * 19/11 (demande du propriétaire : « quand on appuie sur Acheter, un panneau de sécurité au cas où on clique par erreur,
 * et combien on en veut : 500 brindilles, c'est 500 clics » ; maquette maquettes/achat_quantite/) : ACHETER ouvre un
 * panneau au milieu de l'écran : l'article (icône, nom à la couleur de sa rareté, prix à l'unité), la quantité (champ, − et
 * +, Maj = 10 ; molette ; pastilles ×1, ×10, une pile ou ×5, Max), le plafond et sa raison (hérals ou place dans
 * l'inventaire, calculée comme le serveur range les objets), le total, le solde avant et après, Annuler (Échap) et
 * Confirmer (Entrée ; éteint pendant {@link #CONF_ARMEMENT} ticks : un double clic sur ACHETER ne confirme jamais).
 * Le serveur revérifie tout (quantité, prix confirmé, place, hérals) : c'est lui qui décide.
 */
public final class EcranMarchand extends Screen {
    /** Titre à sa vraie taille (police 14) : jamais de texte agrandi, flou (25/10, audit des écrans). */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre");
    /** Couleurs des raretés (Commun ... Unique / Mythique). */
    private static final int[] RARETES = {0xAAAAAA, 0x5FD06E, 0x508CFF, 0xB96EF0, 0xF0BE46, 0xEB4646};
    private static final int COLS = 4, CARTE_H = 44, CASE = 20, COLS_E = 2;
    /** Panneau de confirmation d'un achat (19/11) : taille, profondeur (au-dessus des icônes des cartes, sous l'infobulle). */
    private static final int CONF_L = 300, CONF_H = 196, CONF_Z = 300;
    /** Ticks après l'ouverture du panneau pendant lesquels Confirmer reste éteint (double clic sur ACHETER). */
    private static final int CONF_ARMEMENT = 8;

    private Reseau.Boutique b;
    private final Set<Integer> choisies = new LinkedHashSet<>();
    private int px, py, pw, ph, defil;
    private HzButton vendreSel, vendreTout;
    /** Onglet des échanges ouvert (toujours pour un marchand de troc). */
    private boolean ongletEchanges;
    /** Boutons à refaire au prochain tick (onglet changé, échanges apparus ou disparus). */
    private boolean aRefaire;
    /** Article du panneau de confirmation ouvert (null : fermé) ; quantité choisie (0 : champ vide) ; ticks depuis l'ouverture. */
    private Reseau.Article enAchat;
    private int quantite, ticksAchat;
    private HzField champQuantite;
    private HzButton confirmer;
    /** Pastilles de quantité et leur valeur (-1 : Max). */
    private final List<HzButton> rapides = new ArrayList<>();
    private final List<Integer> rapidesN = new ArrayList<>();
    /** Panneau ouvert ou fermé pendant un clic : boutons refaits juste après lui (jamais pendant le parcours des boutons). */
    private boolean refaireApres;

    private EcranMarchand(Reseau.Boutique b) {
        super(Component.translatable("gui.heraliz_quetes.marchand"));
        this.b = b;
        this.ongletEchanges = b.troc();
    }

    static void recevoir(Reseau.Boutique m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.entite() < 0) {
            if (mc.screen instanceof EcranMarchand) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof EcranMarchand e && e.b.entite() == m.entite()) {
            boolean onglets = e.onglets();
            e.b = m;
            e.choisies.removeIf(c -> m.prixSlots()[c] <= 0);
            if (m.troc()) e.ongletEchanges = true;
            else if (m.echanges().isEmpty()) e.ongletEchanges = false;
            if (onglets != e.onglets()) e.aRefaire = true;
            // panneau d'achat ouvert : le même article (même objet) au même rang, prix à jour ; sinon il se ferme
            if (e.enAchat != null) {
                Reseau.Article neuf = null;
                for (Reseau.Article a : m.articles()) if (a.index() == e.enAchat.index()) neuf = a;
                if (neuf == null || m.rachat() || m.troc() || !ItemStack.isSameItemSameTags(neuf.objet(), e.enAchat.objet())) {
                    e.fermerAchat();
                    e.refaire();
                } else {
                    e.enAchat = neuf;
                }
            }
            return;
        }
        if (!m.ouvrir()) return;
        SonsUi.ouvrir(0.5F);
        mc.setScreen(new EcranMarchand(m));
    }

    private static int couleur(int rarete) {
        return rarete < 0 || rarete >= RARETES.length ? Ui.TEXT : RARETES[rarete];
    }

    @Override
    protected void init() {
        Echelle.forcer(this, 600, 330);
        pw = Math.min(600, width - 24);
        ph = Math.min(330, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        vendreSel = null;
        vendreTout = null;
        confirmer = null;
        champQuantite = null;
        rapides.clear();
        rapidesN.clear();
        if (enAchat != null) {
            initAchat();   // panneau d'achat ouvert : seulement ses boutons (les onglets reviennent à sa fermeture)
            return;
        }
        if (onglets()) {
            // deux onglets en haut, au milieu : ce qu'il vend (ou rachète) / ses échanges
            int cx = px + pw / 2;
            HzButton b1 = addRenderableWidget(new HzButton(cx - 104, py + 15, 100, 16,
                    Component.literal(TexteClient.tr(b.rachat() ? "gui.heraliz_quetes.marchand.onglet.vendre" : "gui.heraliz_quetes.marchand.onglet.acheter")),
                    Ui.OR, () -> changerOnglet(false)));
            HzButton b2 = addRenderableWidget(new HzButton(cx + 4, py + 15, 100, 16,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.marchand.onglet.echanges")), Ui.OR, () -> changerOnglet(true)));
            (ongletEchanges ? b2 : b1).look(HzButton.Look.PRIMARY);
        }
        if (b.rachat() && !ongletEchanges) {
            int bx = px + pw - 190, bw = 172;
            vendreTout = addRenderableWidget(new HzButton(bx, py + ph - 56, bw, 18,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.marchand.tout")), Ui.OR, this::toutVendre));
            vendreSel = addRenderableWidget(new HzButton(bx, py + ph - 32, bw, 20,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.marchand.vendre")), Ui.OR, this::vendre).look(HzButton.Look.PRIMARY));
        }
    }

    /** Deux onglets : un vendeur ou un racheteur qui a aussi des échanges. */
    private boolean onglets() {
        return !b.troc() && !b.echanges().isEmpty();
    }

    private void changerOnglet(boolean echanges) {
        if (ongletEchanges == echanges) return;
        ongletEchanges = echanges;
        defil = 0;
        choisies.clear();
        aRefaire = true;
        SonsUi.clic(0.5F);
    }

    @Override
    public void tick() {
        super.tick();
        if (aRefaire || refaireApres) {
            aRefaire = false;
            refaireApres = false;
            rebuildWidgets();
        }
        if (enAchat != null) {
            ticksAchat++;
            if (champQuantite != null) champQuantite.tick();
            majAchat();
        }
    }

    // ------------------------------------------------------------------ panneau de confirmation d'un achat (19/11)
    private int confX() {
        return px + (pw - CONF_L) / 2;
    }

    private int confY() {
        return py + (ph - CONF_H) / 2;
    }

    private void initAchat() {
        int x0 = confX(), y0 = confY(), yq = y0 + 86;
        addRenderableWidget(new HzButton(x0 + 14, yq, 18, 16, Component.literal("−"), Ui.OR, () -> pas(-1)));
        champQuantite = addRenderableWidget(new HzField(x0 + 36, yq, 54, 16, 4, HzField.NOMBRE).accent(Ui.OR));
        champQuantite.setValue(quantite > 0 ? String.valueOf(quantite) : "");
        champQuantite.setResponder(v -> {
            quantite = lireQuantite(v);
            majAchat();
        });
        addRenderableWidget(new HzButton(x0 + 94, yq, 18, 16, Component.literal("+"), Ui.OR, () -> pas(1)));
        // ×1, ×10, une pile (×64 pour les brindilles, ×60 pour les balles de 9 mm) ou ×5 (objet seul : arme), Max
        int pile = Math.max(1, enAchat.objet().getMaxStackSize());
        int[] valeurs = {1, 10, pile >= 16 ? pile : 5, -1};
        int x = x0 + 120;
        for (int i = 0; i < valeurs.length; i++) {
            int v = valeurs[i], w = i == valeurs.length - 1 ? 46 : 36;
            String nom = v < 0 ? TexteClient.tr("gui.heraliz_quetes.achat.max") : "×" + v;
            rapides.add(addRenderableWidget(new HzButton(x, yq, w, 16, Component.literal(nom), Ui.OR,
                    () -> choisir(v < 0 ? maximum() : v)).look(HzButton.Look.PILL)));
            rapidesN.add(v);
            x += w + 4;
        }
        int bw = (CONF_L - 28 - 16) / 2, by = y0 + CONF_H - 28;
        addRenderableWidget(new HzButton(x0 + 14, by, bw, 18, Component.literal(TexteClient.tr("gui.heraliz_quetes.achat.annuler")),
                Ui.OR, this::fermerAchat));
        confirmer = addRenderableWidget(new HzButton(x0 + CONF_L - 14 - bw, by, bw, 18,
                Component.literal(TexteClient.tr("gui.heraliz_quetes.achat.confirmer")), Ui.OR, () -> confirmerAchat(false))
                .look(HzButton.Look.PRIMARY));
        setFocused(champQuantite);   // on tape la quantité tout de suite (le premier chiffre remplace « 1 »)
        majAchat();
    }

    private void ouvrirAchat(Reseau.Article a) {
        enAchat = a;
        quantite = 1;
        ticksAchat = 0;
        refaireApres = true;
        SonsUi.clic(0.5F);
    }

    private void fermerAchat() {
        enAchat = null;
        refaireApres = true;
    }

    /** Boutons refaits tout de suite si le panneau vient de s'ouvrir ou de se fermer (hors du parcours des boutons). */
    private void refaire() {
        if (!refaireApres) return;
        refaireApres = false;
        rebuildWidgets();
    }

    private static int lireQuantite(String v) {
        try {
            return v.isBlank() ? 0 : Math.max(0, Math.min(Reseau.ACHAT_MAX * 10, Integer.parseInt(v.trim())));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Quantité choisie (bouton, pastille, molette, flèches), gardée entre 1 et ce que le joueur peut acheter. */
    private void choisir(int n) {
        quantite = Math.max(1, Math.min(Math.max(1, maximum()), n));
        if (champQuantite != null) champQuantite.setValue(String.valueOf(quantite));
        majAchat();
    }

    /** − ou + : 1 de plus ou de moins (Maj : 10). */
    private void pas(int sens) {
        choisir(Math.max(0, quantite) + sens * (hasShiftDown() ? 10 : 1));
    }

    /** Autant que ses hérals en paient. */
    private int maxHerals() {
        return enAchat == null ? 0 : (int) Math.min(Reseau.ACHAT_MAX, b.solde() / Math.max(1, enAchat.prix()));
    }

    /**
     * Autant qu'il en tient dans l'inventaire, rangé comme le serveur le fait ({@code ObjetsARendre.tiennent}) : sur les
     * piles pareilles (36 cases et main gauche), puis dans les cases vides des 36 cases.
     */
    private int maxPlace() {
        var p = Minecraft.getInstance().player;
        if (p == null || enAchat == null) return 0;
        ItemStack m = enAchat.objet();
        int max = Math.max(1, m.getMaxStackSize());
        long n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty()) n += max;
            else if (m.isStackable() && ItemStack.isSameItemSameTags(s, m)) n += Math.max(0, max - s.getCount());
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (!s.isEmpty() && m.isStackable() && ItemStack.isSameItemSameTags(s, m)) n += Math.max(0, max - s.getCount());
        }
        return (int) Math.min(Reseau.ACHAT_MAX, n);
    }

    private int maximum() {
        return Math.min(maxHerals(), maxPlace());
    }

    /** Confirmer et les pastilles allumés ou éteints selon la quantité et le plafond. */
    private void majAchat() {
        if (enAchat == null) return;
        int max = maximum();
        if (confirmer != null) confirmer.active = quantite >= 1 && quantite <= max && ticksAchat >= CONF_ARMEMENT;
        for (int i = 0; i < rapides.size(); i++) {
            int v = rapidesN.get(i);
            boolean ok = v < 0 ? max >= 1 : v <= max;
            rapides.get(i).active = ok;
            rapides.get(i).accent(ok ? Ui.OR : Ui.FAINT);
        }
    }

    /** Envoie l'achat confirmé (quantité et prix à l'unité vus par le joueur) ; le serveur revérifie tout. */
    private void confirmerAchat(boolean clavier) {
        majAchat();
        if (enAchat == null || confirmer == null || !confirmer.active) return;
        Reseau.CANAL.sendToServer(new Reseau.Achat(b.entite(), enAchat.index(), quantite, enAchat.prix()));
        if (clavier) SonsUi.clic(0.5F);
        fermerAchat();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (enAchat != null) {
            boolean pris = true;
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> fermerAchat();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirmerAchat(true);
                case GLFW.GLFW_KEY_UP -> pas(1);
                case GLFW.GLFW_KEY_DOWN -> pas(-1);
                default -> pris = super.keyPressed(key, scan, mods);
            }
            refaire();
            return pris;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        // un chiffre tapé après un clic sur une pastille va dans le champ (le premier remplace la quantité)
        if (enAchat != null && champQuantite != null && c >= '0' && c <= '9' && getFocused() != champQuantite) setFocused(champQuantite);
        return super.charTyped(c, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    private ItemStack caseInv(int i) {
        var p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getInventory().items.get(i);
    }

    private long prixCase(int i) {
        return (long) b.prixSlots()[i] * caseInv(i).getCount();
    }

    private void vendre() {
        if (choisies.isEmpty()) return;
        Reseau.CANAL.sendToServer(new Reseau.Vente(b.entite(), new ArrayList<>(choisies)));
        choisies.clear();
    }

    /** Tout ce qu'il prend, sauf les objets d'événement quand ils ne rapporteraient aucun point (gardés pour plus tard). */
    private void toutVendre() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < Reseau.CASES; i++) {
            if (b.prixSlots()[i] > 0 && !caseInv(i).isEmpty() && !(b.pointsSlots()[i] > 0 && b.raisonPoints() != 0)) l.add(i);
        }
        if (l.isEmpty()) return;
        Reseau.CANAL.sendToServer(new Reseau.Vente(b.entite(), l));
        choisies.clear();
    }

    // ------------------------------------------------------------------ disposition
    /** Case de l'inventaire n° i (0-8 barre d'objets, en bas) : coin haut-gauche. */
    private int[] posCase(int i) {
        int col = i % 9, rang = i < 9 ? 3 : i / 9 - 1;
        int x = px + 18 + col * (CASE + 3), y = py + 66 + rang * (CASE + 3) + (i < 9 ? 6 : 0);
        return new int[]{x, y};
    }

    private int carteL() {
        return (pw - 36 - (COLS - 1) * 8) / COLS;
    }

    private int[] posCarte(int n) {
        int x = px + 18 + (n % COLS) * (carteL() + 8), y = py + 56 + (n / COLS) * (CARTE_H + 8) - defil;
        return new int[]{x, y};
    }

    private boolean dansListe(int y) {
        return y >= py + 52 && y + CARTE_H <= py + ph - 20;
    }

    private int carteLE() {
        return (pw - 36 - (COLS_E - 1) * 8) / COLS_E;
    }

    private int[] posEchange(int n) {
        int x = px + 18 + (n % COLS_E) * (carteLE() + 8), y = py + 56 + (n / COLS_E) * (CARTE_H + 8) - defil;
        return new int[]{x, y};
    }

    /** L'échange n° n peut-il se faire (actif, et le joueur a de quoi donner) ? */
    private static boolean faisable(Reseau.Echange x) {
        return x.actif() && x.possede() >= x.nDonne();
    }

    /** Le joueur a-t-il cet objet, mais abîmé (usure) ? Le marchand ne le compte pas : la carte le dit (18/11). */
    private static boolean abime(ItemStack modele) {
        var p = Minecraft.getInstance().player;
        if (p == null || modele.isEmpty()) return false;
        for (ItemStack s : p.getInventory().items) {
            if (!s.isEmpty() && s.getItem() == modele.getItem() && s.isDamaged()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ souris
    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        boolean pris = super.mouseClicked(mx, my, bouton);
        refaire();
        if (pris || enAchat != null) return true;   // panneau d'achat ouvert : rien d'autre ne se clique
        if (ongletEchanges) {
            for (int n = 0; n < b.echanges().size(); n++) {
                int[] c = posEchange(n);
                if (!dansListe(c[1])) continue;
                int bx = c[0] + carteLE() - 58, by = c[1] + CARTE_H - 17;
                if (mx >= bx && mx < bx + 52 && my >= by && my < by + 13) {
                    Reseau.Echange x = b.echanges().get(n);
                    if (faisable(x)) {
                        Reseau.CANAL.sendToServer(new Reseau.Troc(b.entite(), x.index(), hasShiftDown() ? 5 : 1));
                        SonsUi.clic(0.5F);
                    }
                    return true;
                }
            }
        } else if (b.rachat()) {
            for (int i = 0; i < Reseau.CASES; i++) {
                int[] c = posCase(i);
                if (mx >= c[0] && mx < c[0] + CASE && my >= c[1] && my < c[1] + CASE) {
                    if (b.prixSlots()[i] > 0 && !caseInv(i).isEmpty()) {
                        if (!choisies.remove(i)) choisies.add(i);
                        SonsUi.clic(0.5F);
                    }
                    return true;
                }
            }
        } else {
            for (int n = 0; n < b.articles().size(); n++) {
                int[] c = posCarte(n);
                if (!dansListe(c[1])) continue;
                int bx = c[0] + carteL() - 46, by = c[1] + CARTE_H - 17;
                if (mx >= bx && mx < bx + 40 && my >= by && my < by + 13) {
                    ouvrirAchat(b.articles().get(n));
                    refaire();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (enAchat != null) {
            if (d != 0) pas(d > 0 ? 1 : -1);   // molette : la quantité (Maj : 10)
            return true;
        }
        if (ongletEchanges) {
            int rangs = (b.echanges().size() + COLS_E - 1) / COLS_E;
            int max = Math.max(0, rangs * (CARTE_H + 8) - (ph - 76));
            defil = (int) Math.max(0, Math.min(max, defil - d * 26));
            return true;
        }
        if (b.rachat()) return false;
        int rangs = (b.articles().size() + COLS - 1) / COLS;
        int max = Math.max(0, rangs * (CARTE_H + 8) - (ph - 76));
        defil = (int) Math.max(0, Math.min(max, defil - d * 26));
        return true;
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 215);
        g.drawString(f, Ui.lit(TexteClient.str(b.nom()), TITRE), px + 18, py + 11, Ui.argb(Ui.TEXT, 255), false);
        Ui.spaced(g, f, TexteClient.str(b.role()).toUpperCase(), Ui.SMALL, px + 19, py + 32, Ui.argb(Ui.OR, 255), 0.8f);
        Ui.right(g, f, Ui.lit(Ui.nombre(b.solde()) + " " + TexteClient.tr("gui.heraliz_quetes.marchand.herals"), Ui.BOLD), px + pw - 18, py + 15, Ui.argb(Ui.OR, 255));
        Ui.spacedRight(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.solde"), Ui.SMALL, px + pw - 18, py + 28, Ui.argb(Ui.MUTED, 255), 0.6f);
        Ui.hairline(g, px + 18, px + pw - 18, py + 44, 0xFFFFFF, 30, false, true);
        // panneau d'achat ouvert : l'écran dessous ne réagit plus à la souris (ni survol ni infobulle)
        int sx = enAchat != null ? -10000 : mx, sy = enAchat != null ? -10000 : my;
        List<Component> bulle = ongletEchanges ? echanges(g, f, sx, sy) : b.rachat() ? rachat(g, f, sx, sy) : achat(g, f, sx, sy);
        if (enAchat != null) {
            // au-dessus des icônes des cartes (le jeu les dessine plus haut que les formes) : panneau et ses boutons décalés
            g.pose().pushPose();
            g.pose().translate(0, 0, CONF_Z);
            bulle = panneauAchat(g, f, mx, my);
            super.render(g, mx, my, pt);
            g.pose().popPose();
        } else {
            super.render(g, mx, my, pt);
        }
        if (bulle != null) Ui.tooltip(g, f, bulle, mx, my, Ui.OR);
    }

    /** Le panneau de confirmation (maquette maquettes/achat_quantite/) ; rend l'infobulle de l'objet survolé, ou null. */
    private List<Component> panneauAchat(GuiGraphics g, Font f, int mx, int my) {
        Reseau.Article a = enAchat;
        int x0 = confX(), y0 = confY(), col = couleur(a.rarete()), tw = CONF_L - 68;
        String herals = " " + TexteClient.tr("gui.heraliz_quetes.marchand.herals");
        g.fill(0, 0, width, height, Ui.argb(Ui.VERRE, 160));
        Ui.glass(g, x0, y0, CONF_L, CONF_H, 252);
        g.fill(x0 + 2, y0 + 1, x0 + CONF_L - 2, y0 + 2, Ui.argb(Ui.OR, 200));
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.achat.titre"), Ui.SMALL, x0 + 14, y0 + 11, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.hairline(g, x0 + 14, x0 + CONF_L - 14, y0 + 25, 0xFFFFFF, 30, false, true);
        // l'article : icône teintée de sa rareté, nom à la couleur de sa rareté, rareté, prix à l'unité
        icone(g, a.objet(), x0 + 14, y0 + 33, col);
        g.drawString(f, Ui.lit(Ui.ellipsis(f, a.objet().getHoverName().getString(), Ui.BOLD, tw), Ui.BOLD), x0 + 54, y0 + 33,
                Ui.argb(col, 255), false);
        if (a.rarete() >= 0) {
            g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.rarete." + a.rarete()), Ui.SMALL), x0 + 54, y0 + 44, Ui.argb(col, 255), false);
        }
        g.drawString(f, Ui.lit(Ui.ellipsis(f, TexteClient.tr("gui.heraliz_quetes.achat.unite", Ui.nombre(a.prix())), Ui.SMALL, tw), Ui.SMALL),
                x0 + 54, y0 + 55, Ui.argb(Ui.MUTED, 255), false);
        // la quantité (boutons et champ : widgets), puis le plafond et sa raison
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.achat.quantite"), Ui.SMALL, x0 + 14, y0 + 74, Ui.argb(Ui.MUTED, 255), 0.8f);
        int maxH = maxHerals(), maxP = maxPlace(), max = Math.min(maxH, maxP);
        boolean trop = quantite > max;
        String ligne = max < 1 ? TexteClient.tr(maxH < 1 ? "gui.heraliz_quetes.achat.pas_assez" : "gui.heraliz_quetes.achat.plein")
                : TexteClient.tr("gui.heraliz_quetes.achat." + (trop ? "trop_" : "limite_") + (maxH <= maxP ? "herals" : "place"), Ui.nombre(max));
        g.drawString(f, Ui.lit(Ui.ellipsis(f, ligne, Ui.SMALL, CONF_L - 28), Ui.SMALL), x0 + 14, y0 + 106,
                Ui.argb(trop || max < 1 ? Ui.DANGER : Ui.FAINT, 255), false);
        // le total, le solde avant et après
        Ui.hairline(g, x0 + 14, x0 + CONF_L - 14, y0 + 120, 0xFFFFFF, 50, false, false);
        long total = (long) Math.max(0, quantite) * a.prix(), apres = b.solde() - total;
        g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.achat.total"), Ui.BOLD), x0 + 14, y0 + 127, Ui.argb(Ui.TEXT, 255), false);
        Ui.right(g, f, Ui.lit(Ui.nombre(total) + herals, Ui.BOLD), x0 + CONF_L - 14, y0 + 127, Ui.argb(trop ? Ui.DANGER : Ui.OR, 255));
        g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.achat.solde"), Ui.FONT), x0 + 14, y0 + 140, Ui.argb(Ui.MUTED, 255), false);
        Ui.right(g, f, Ui.lit(Ui.nombre(b.solde()) + herals, Ui.FONT), x0 + CONF_L - 14, y0 + 140, Ui.argb(Ui.TEXT, 255));
        g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.achat.apres"), Ui.FONT), x0 + 14, y0 + 152, Ui.argb(Ui.MUTED, 255), false);
        Ui.right(g, f, Ui.lit((apres < 0 ? "−" : "") + Ui.nombre(Math.abs(apres)) + herals, Ui.FONT), x0 + CONF_L - 14, y0 + 152,
                Ui.argb(apres < 0 ? Ui.DANGER : Ui.OK, 255));
        if (mx >= x0 + 14 && mx < x0 + 46 && my >= y0 + 33 && my < y0 + 65) return new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), a.objet()));
        return null;
    }

    /** Une icône d'objet dans sa case (32 × 32), teintée de sa rareté. */
    private void icone(GuiGraphics g, ItemStack s, int x, int y, int col) {
        g.fill(x, y, x + 32, y + 32, Ui.argb(col, 40));
        Ui.softOutline(g, x, y, 32, 32, Ui.argb(col, 170));
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    private List<Component> echanges(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        int cl = carteLE();
        if (b.echanges().isEmpty()) vide(g, f, "gui.heraliz_quetes.marchand.echanges_vide");
        g.enableScissor(px, py + 50, px + pw, py + ph - 16);
        for (int n = 0; n < b.echanges().size(); n++) {
            Reseau.Echange x = b.echanges().get(n);
            int[] c = posEchange(n);
            if (c[1] + CARTE_H < py + 50 || c[1] > py + ph - 16) continue;
            boolean visible = dansListe(c[1]), possible = faisable(x);
            int col = couleur(x.rarete());
            Ui.glass(g, c[0], c[1], cl, CARTE_H, 110);
            // [donné] ×n → [reçu] ×n
            icone(g, x.donne(), c[0] + 6, c[1] + 6, Ui.MUTED);
            g.drawString(f, Ui.lit("×" + x.nDonne(), Ui.BOLD), c[0] + 40, c[1] + 18, Ui.argb(Ui.TEXT, 255), false);
            Ui.centered(g, f, Ui.lit("→", Ui.BOLD), c[0] + 70, c[1] + 18, Ui.argb(Ui.OR, 255));
            icone(g, x.recu(), c[0] + 78, c[1] + 6, col);
            g.drawString(f, Ui.lit("×" + x.nRecu(), Ui.BOLD), c[0] + 112, c[1] + 18, Ui.argb(Ui.TEXT, 255), false);
            // texte : ce qu'on reçoit, contre quoi, combien on en a
            int tx = c[0] + 138, tw = cl - 138 - 6;
            g.drawString(f, Ui.lit(Ui.ellipsis(f, x.recu().getHoverName().getString(), Ui.BOLD, tw), Ui.BOLD), tx, c[1] + 6, Ui.argb(col, 255), false);
            String contre = TexteClient.tr("gui.heraliz_quetes.marchand.contre", x.nDonne(), x.donne().getHoverName().getString());
            g.drawString(f, Ui.lit(Ui.ellipsis(f, contre, Ui.SMALL, tw), Ui.SMALL), tx, c[1] + 17, Ui.argb(Ui.MUTED, 255), false);
            String etat = !x.actif() ? TexteClient.tr("gui.heraliz_quetes.marchand.echange_coupe")
                    : !possible && abime(x.donne()) ? TexteClient.tr("gui.heraliz_quetes.marchand.abime")
                    : TexteClient.tr("gui.heraliz_quetes.marchand.possede", x.possede(), x.nDonne());
            g.drawString(f, Ui.lit(Ui.ellipsis(f, etat, Ui.SMALL, tw - 60), Ui.SMALL), tx, c[1] + CARTE_H - 13,
                    Ui.argb(possible ? Ui.OK : Ui.DANGER, 255), false);
            // bouton ÉCHANGER (éteint si impossible)
            int bx = c[0] + cl - 58, by = c[1] + CARTE_H - 17;
            boolean survol = mx >= bx && mx < bx + 52 && my >= by && my < by + 13 && visible;
            g.fill(bx, by, bx + 52, by + 13, possible ? Ui.argb(Ui.OR, survol ? 255 : 210) : Ui.argb(0xFFFFFF, 20));
            Ui.spacedCentered(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.echanger"), Ui.SMALL, bx + 26, by + 3,
                    possible ? 0xFF0D0F14 : Ui.argb(Ui.MUTED, 255), 0.4f);
            if (visible && my >= c[1] + 6 && my < c[1] + 38) {
                if (mx >= c[0] + 6 && mx < c[0] + 38) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), x.donne()));
                else if (mx >= c[0] + 78 && mx < c[0] + 110) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), x.recu()));
            }
        }
        g.disableScissor();
        return bulle;
    }

    /** Message d'un onglet vide, en haut de la liste, coupé en lignes s'il est trop long. */
    private void vide(GuiGraphics g, Font f, String cle) {
        int y = py + 56;
        for (String l : Ui.lignesEspacees(f, TexteClient.tr(cle), Ui.SMALL, 0.4f, pw - 36)) {
            Ui.spaced(g, f, l, Ui.SMALL, px + 18, y, Ui.argb(Ui.MUTED, 255), 0.4f);
            y += 10;
        }
    }

    private List<Component> achat(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        int cl = carteL();
        if (b.articles().isEmpty()) vide(g, f, "gui.heraliz_quetes.marchand.articles_vide");
        g.enableScissor(px, py + 50, px + pw, py + ph - 16);
        for (int n = 0; n < b.articles().size(); n++) {
            Reseau.Article a = b.articles().get(n);
            int[] c = posCarte(n);
            if (c[1] + CARTE_H < py + 50 || c[1] > py + ph - 16) continue;
            int col = couleur(a.rarete());
            Ui.glass(g, c[0], c[1], cl, CARTE_H, 110);
            g.fill(c[0] + 6, c[1] + 6, c[0] + 38, c[1] + 38, Ui.argb(col, 40));
            Ui.softOutline(g, c[0] + 6, c[1] + 6, 32, 32, Ui.argb(col, 170));
            g.pose().pushPose();
            g.pose().translate(c[0] + 10, c[1] + 10, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(a.objet(), 0, 0);
            g.pose().popPose();
            String nom = Ui.ellipsis(f, a.objet().getHoverName().getString(), Ui.BOLD, cl - 50);
            g.drawString(f, Ui.lit(nom, Ui.BOLD), c[0] + 44, c[1] + 6, Ui.argb(Ui.TEXT, 255), false);
            if (a.rarete() >= 0) g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.rarete." + a.rarete()), Ui.SMALL), c[0] + 44, c[1] + 17, Ui.argb(col, 255), false);
            g.drawString(f, Ui.lit(Ui.nombre(a.prix()), Ui.BOLD), c[0] + 44, c[1] + CARTE_H - 14, Ui.argb(Ui.OR, 255), false);
            int bx = c[0] + cl - 46, by = c[1] + CARTE_H - 17;
            boolean survol = mx >= bx && mx < bx + 40 && my >= by && my < by + 13 && dansListe(c[1]);
            g.fill(bx, by, bx + 40, by + 13, Ui.argb(Ui.OR, survol ? 255 : 210));
            Ui.spacedCentered(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.acheter"), Ui.SMALL, bx + 20, by + 3, 0xFF0D0F14, 0.4f);
            if (mx >= c[0] + 6 && mx < c[0] + 38 && my >= c[1] + 6 && my < c[1] + 38 && dansListe(c[1])) {
                bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), a.objet()));
            }
        }
        g.disableScissor();
        return bulle;
    }

    private List<Component> rachat(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.inventaire"), Ui.SMALL, px + 18, py + 50, Ui.argb(Ui.MUTED, 255), 0.8f);
        for (int i = 0; i < Reseau.CASES; i++) {
            int[] c = posCase(i);
            ItemStack s = caseInv(i);
            boolean prend = b.prixSlots()[i] > 0 && !s.isEmpty();
            boolean choisie = choisies.contains(i);
            g.fill(c[0], c[1], c[0] + CASE, c[1] + CASE, Ui.argb(0xFFFFFF, choisie ? 34 : 14));
            if (prend && b.rarSlots()[i] >= 0) g.fill(c[0] + 2, c[1] + CASE - 2, c[0] + CASE - 2, c[1] + CASE - 1, Ui.argb(couleur(b.rarSlots()[i]), 230));
            if (choisie) Ui.softOutline(g, c[0], c[1], CASE, CASE, Ui.argb(Ui.OR, 255));
            if (!s.isEmpty()) {
                g.renderItem(s, c[0] + 2, c[1] + 2);
                g.renderItemDecorations(font, s, c[0] + 2, c[1] + 2);
                if (!prend) g.fill(c[0], c[1], c[0] + CASE, c[1] + CASE, 0xA00D0F14);
            }
            if (!s.isEmpty() && mx >= c[0] && mx < c[0] + CASE && my >= c[1] && my < c[1] + CASE) {
                bulle = new ArrayList<>();
                int teinte = prend ? couleur(b.rarSlots()[i]) : Ui.MUTED;
                bulle.add(Ui.lit(s.getHoverName().getString(), Ui.BOLD).withStyle(st -> st.withColor(teinte)));
                if (prend) {
                    bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.prix", Ui.nombre(b.prixSlots()[i])), Ui.FONT));
                    if (s.getCount() > 1) bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.prix_pile", s.getCount(), Ui.nombre(prixCase(i))), Ui.FONT));
                    // objet d'événement (25/10) : points de saison pour le clan, ou pourquoi il n'en aurait pas
                    if (b.pointsSlots()[i] > 0) {
                        if (b.raisonPoints() == 0) {
                            bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.points", b.pointsSlots()[i]), Ui.FONT).withStyle(st -> st.withColor(Ui.OK)));
                        } else {
                            bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.sans_points." + b.raisonPoints()), Ui.FONT).withStyle(st -> st.withColor(Ui.AMBRE)));
                            bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.garder"), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
                        }
                    } else if (b.pointsSlots()[i] == -2) {
                        // objet d'essai du staff (11/11) : hérals seulement, jamais de points (avant : rien ne le disait)
                        bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.essai"), Ui.FONT).withStyle(st -> st.withColor(Ui.AMBRE)));
                    }
                } else {
                    // 15/11 : arme chargée ou avec des accessoires, boîte de munitions ou sac pleins : ce qu'il faut vider d'abord
                    int contenu = Contenu.de(s);
                    bulle.add(Ui.lit(TexteClient.tr(contenu != Contenu.RIEN ? "gui.heraliz_quetes.contenu." + contenu : "gui.heraliz_quetes.marchand.refuse"),
                            Ui.FONT).withStyle(st -> st.withColor(contenu != Contenu.RIEN ? Ui.AMBRE : Ui.MUTED)));
                }
            }
        }
        // panneau de droite : sélection et total
        int rx = px + pw - 200, ry = py + 52, rw = 190;
        Ui.glass(g, rx, ry, rw, ph - 60, 120);
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.a_vendre"), Ui.SMALL, rx + 9, ry + 8, Ui.argb(Ui.MUTED, 255), 0.8f);
        int y = ry + 22;
        long total = 0, points = 0;
        int n = 0;
        for (int i : choisies) {
            ItemStack s = caseInv(i);
            if (s.isEmpty()) continue;
            total += prixCase(i);
            points += Math.max(0, b.pointsSlots()[i]);
            if (n++ >= 9) continue;
            String nom = Ui.ellipsis(f, s.getHoverName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : ""), Ui.FONT, rw - 70);
            g.drawString(f, Ui.lit(nom, Ui.FONT), rx + 9, y, Ui.argb(couleur(b.rarSlots()[i]), 255), false);
            Ui.right(g, f, Ui.lit(Ui.nombre(prixCase(i)), Ui.BOLD), rx + rw - 9, y, Ui.argb(Ui.TEXT, 255));
            y += 11;
        }
        if (n > 9) g.drawString(f, Ui.lit("+" + (n - 9), Ui.FONT), rx + 9, y, Ui.argb(Ui.MUTED, 255), false);
        // objets d'événement choisis : une ligne de plus sous le total (points de saison, ou pourquoi il n'y en a pas)
        int ty = py + ph - (points > 0 ? 93 : 82);
        Ui.hairline(g, rx + 9, rx + rw - 9, ty, 0xFFFFFF, 50, false, false);
        g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.total"), Ui.BOLD), rx + 9, ty + 6, Ui.argb(Ui.TEXT, 255), false);
        Ui.right(g, f, Ui.lit(Ui.nombre(total) + " " + TexteClient.tr("gui.heraliz_quetes.marchand.herals"), Ui.BOLD), rx + rw - 9, ty + 6, Ui.argb(Ui.OR, 255));
        if (points > 0) {
            String ligne = b.raisonPoints() == 0 ? TexteClient.tr("gui.heraliz_quetes.marchand.points", points)
                    : TexteClient.tr("gui.heraliz_quetes.marchand.sans_points." + b.raisonPoints());
            g.drawString(f, Ui.lit(Ui.ellipsis(f, ligne, Ui.FONT, rw - 18), Ui.FONT), rx + 9, ty + 17,
                    Ui.argb(b.raisonPoints() == 0 ? Ui.OK : Ui.AMBRE, 255), false);
        }
        if (vendreSel != null) vendreSel.active = !choisies.isEmpty();
        return bulle;
    }
}
