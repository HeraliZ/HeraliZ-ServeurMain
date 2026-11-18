package fr.heraliz.noyau.client;

import fr.heraliz.noyau.client.effets.EffetsHud;
import fr.heraliz.noyau.client.kit.Bandeau;
import fr.heraliz.noyau.client.kit.Formes;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les messages à l'écran, tous dans le même style ({@link Bandeau} ; 18/11, maquette validée) :
 * <ul>
 * <li><b>à droite</b>, empilés sur une colonne de même largeur, le plus récent en haut : quêtes, hôtel des ventes,
 * extractions, clans, hérals... ({@link #ajouter}, ou {@link #message} pour un texte seul) ; 4 visibles, les suivants
 * attendent leur tour ; cachés sous un écran (sauf le chat), et leur temps ne court qu'une fois montrés ;</li>
 * <li><b>au-dessus de la barre</b> : les messages courts que le jeu affichait en texte nu (« barre d'action » :
 * {@code displayClientMessage(..., true)} de tous les mods, repris par {@code BarreActionMixin}) ; trait de la couleur du
 * message, texte en blanc ; le suivant remplace le précédent.</li>
 * </ul>
 * Rien n'est alloué à l'image ; au repos (aucun message), le rendu sort tout de suite.
 */
public final class Bandeaux {
    private Bandeaux() {
    }

    private static final float MARGE = 5f, ECART = 2f, LARGEUR_MIN = 120f, GLISSE = 12f;
    private static final long ENTREE = 200, SORTIE = 500, DUREE = 4500, DUREE_BARRE = 3000;
    private static final int VISIBLES = 4, ATTENTE_MAX = 10;
    /** Milieu des messages au-dessus de la barre, sous le bas de l'écran (celui du texte du jeu). */
    private static final float BARRE_MILIEU = 68f, BARRE_MAX = 300f;

    private static final class Message {
        final Bandeau bandeau = new Bandeau();
        /** Clé d'un message qui se remplace lui-même ({@link #remplacer}), ou null. */
        @Nullable
        String cle;
        long duree;
        /** Première image où il est montré (0 : pas encore). */
        long debut;
        float y = Float.NaN;
    }

    /** Dans l'ordre d'arrivée : les {@value #VISIBLES} premiers sont à l'écran, les autres attendent. */
    private static final List<Message> PILE = new ArrayList<>();
    private static final Bandeau BARRE = new Bandeau().largeurMax(BARRE_MAX);
    private static boolean barreActive;
    private static long barreDebut, derniereImage;

    // ------------------------------------------------------------------ API (fil du jeu ; les autres fils y sont renvoyés)
    /** Message à droite : titre en gras, texte dessous (null : titre seul) ; {@code secondes} 0 = 4,5 s. */
    public static void ajouter(String titre, @Nullable String texte, int couleur, int secondes) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> ajouter(titre, texte, couleur, secondes));
            return;
        }
        nouveau(titre, texte, couleur, secondes);
    }

    /** Ajoute le message (fil du jeu) ; null s'il n'a ni titre ni texte. */
    @Nullable
    private static Message nouveau(String titre, @Nullable String texte, int couleur, int secondes) {
        String t = titre == null ? "" : titre.trim(), x = texte == null ? null : texte.trim();
        if (t.isEmpty()) {
            if (x == null || x.isEmpty()) return null;
            t = x;
            x = null;
        }
        Message m = new Message();
        m.bandeau.poser(t, x, couleur);
        m.duree = secondes > 0 ? secondes * 1000L : DUREE;
        PILE.add(m);
        // trop de messages en attente : le plus ancien de ceux qui attendent part (jamais un message déjà à l'écran)
        while (PILE.size() > ATTENTE_MAX) PILE.remove(VISIBLES < PILE.size() - 1 ? VISIBLES : 0);
        return m;
    }

    public static void ajouter(String titre, @Nullable String texte, int couleur) {
        ajouter(titre, texte, couleur, 0);
    }

    /**
     * Message à droite qui remplace celui de même {@code cle} encore à l'écran ou en attente (nouveau texte, même place,
     * son temps repart) ; sinon, un nouveau. Pour un message qui se met à jour (une vague d'extractions qui s'ouvrent...).
     */
    public static void remplacer(String cle, String titre, @Nullable String texte, int couleur, int secondes) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> remplacer(cle, titre, texte, couleur, secondes));
            return;
        }
        for (Message m : PILE) {
            if (!cle.equals(m.cle)) continue;
            m.bandeau.poser(titre == null ? "" : titre.trim(), texte, couleur);
            m.duree = secondes > 0 ? secondes * 1000L : DUREE;
            if (m.debut != 0) m.debut = Util.getMillis() - ENTREE;   // déjà là : il reste, sans revenir de la droite
            return;
        }
        Message m = nouveau(titre, texte, couleur, secondes);
        if (m != null) m.cle = cle;
    }

    /**
     * Message d'un seul texte (quêtes...) : coupé en titre et texte sur « · » (« +60 hérals · dans ta bourse (63) ») ou sur
     * un deux-points proche du début (« Étape terminée : Parler à Varga »), sinon tout en titre.
     */
    public static void message(String s, int couleur, int secondes) {
        if (s == null || s.isBlank()) return;
        String t = s.trim();
        int i = t.indexOf(" · ");
        if (i > 0 && i + 3 < t.length()) {
            ajouter(t.substring(0, i), t.substring(i + 3), couleur, secondes);
            return;
        }
        int j = deuxPoints(t);
        if (j > 0) {
            ajouter(t.substring(0, j), t.substring(j + 1), couleur, secondes);
            return;
        }
        ajouter(t, null, couleur, secondes);
    }

    /** Position du deux-points qui sépare un titre court (40 caractères au plus) du texte ; -1 sinon (« 12:30 » n'en est pas un). */
    private static int deuxPoints(String t) {
        int j = t.indexOf(':');
        while (j > 0 && j + 1 < t.length() && t.charAt(j + 1) != ' ') j = t.indexOf(':', j + 1);
        if (j <= 0 || j > 40 || j + 2 >= t.length()) return -1;
        return j;
    }

    /**
     * Message au-dessus de la barre (repris de la barre d'action du jeu) : remplace le précédent ; vide = efface. Vrai : pris
     * en charge (le jeu n'affiche pas le sien).
     */
    public static boolean barre(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> barre(c));
            return true;
        }
        String s = c.getString().trim();
        if (s.isEmpty()) {
            barreActive = false;
            return true;
        }
        BARRE.poser(s, null, accent(premiereCouleur(c)));
        barreDebut = Util.getMillis();
        barreActive = true;
        return true;
    }

    /** Déconnexion : plus rien. */
    public static void vider() {
        PILE.clear();
        barreActive = false;
    }

    /** Couleur d'un message de la barre (première couleur de son texte) ramenée à celles des bandeaux. */
    private static int premiereCouleur(Component c) {
        int[] rgb = {-1};
        c.visit((Style st, String t) -> {
            TextColor col = st.getColor();
            if (col != null && !t.isBlank()) {
                rgb[0] = col.getValue();
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return rgb[0];
    }

    static int accent(int rgb) {
        if (rgb < 0) return Bandeau.NEUTRE;
        int r = rgb >> 16 & 255, v = rgb >> 8 & 255, b = rgb & 255;
        if (r > 150 && v < 120 && b < 120) return Bandeau.DANGER;   // rouge
        if (v > 150 && r < 150 && b < 170) return Bandeau.OK;       // vert
        if (r > 190 && v > 140 && b < 130) return Bandeau.ALERTE;   // or, jaune
        if (b > 170 && r < 150) return Bandeau.INFO;                // bleu, cyan
        return Bandeau.NEUTRE;
    }

    // ------------------------------------------------------------------ rendu
    static void rendu(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (PILE.isEmpty() && !barreActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        long maintenant = Util.getMillis();
        float dt = derniereImage == 0 ? 0f : Math.min(0.1f, (maintenant - derniereImage) / 1000f);
        derniereImage = maintenant;
        if (barreActive) barre(g, w, h, maintenant);
        if (PILE.isEmpty() || (mc.screen != null && !(mc.screen instanceof ChatScreen))) return;
        pile(g, w, maintenant, dt);
    }

    private static void barre(GuiGraphics g, int w, int h, long maintenant) {
        long age = maintenant - barreDebut;
        if (age >= DUREE_BARRE) {
            barreActive = false;
            return;
        }
        float a = age > DUREE_BARRE - SORTIE ? (DUREE_BARRE - age) / (float) SORTIE : 1f;
        float bw = BARRE.largeur(), bh = BARRE.hauteur();
        BARRE.dessiner(g, Formes.caler((w - bw) / 2f), Formes.caler(h - BARRE_MILIEU - bh / 2f), bw, a);
    }

    private static void pile(GuiGraphics g, int w, long maintenant, float dt) {
        // finis : retirés (seuls les messages déjà montrés ont un temps qui court)
        PILE.removeIf(m -> m.debut != 0 && maintenant - m.debut >= m.duree);
        int n = Math.min(VISIBLES, PILE.size());
        if (n == 0) return;
        float largeur = LARGEUR_MIN;
        for (int i = 0; i < n; i++) largeur = Math.max(largeur, PILE.get(i).bandeau.largeur());
        float x = w - MARGE - largeur;
        // sous la rangée des effets du coin haut droit, quand il y en a
        float y = MARGE + EffetsHud.decalageHautDroite();
        // le plus récent en haut ; chacun glisse vers sa place quand un autre part
        for (int i = n - 1; i >= 0; i--) {
            Message m = PILE.get(i);
            if (m.debut == 0) m.debut = maintenant;
            m.y = Float.isNaN(m.y) ? y : m.y + (y - m.y) * Math.min(1f, dt * 14f);
            y += m.bandeau.hauteur() + ECART;
        }
        // formes de tous, puis textes de tous : deux lots seulement ; posés sur des pixels entiers de l'écran
        for (int i = n - 1; i >= 0; i--) {
            Message m = PILE.get(i);
            m.bandeau.formes(g, Formes.caler(x + decalage(m, maintenant)), Formes.caler(m.y), largeur, opacite(m, maintenant));
        }
        g.flush();
        for (int i = n - 1; i >= 0; i--) {
            Message m = PILE.get(i);
            m.bandeau.textes(g, Formes.caler(x + decalage(m, maintenant)), Formes.caler(m.y), opacite(m, maintenant));
        }
        g.flush();
    }

    private static float opacite(Message m, long maintenant) {
        long age = maintenant - m.debut;
        if (age < ENTREE) return age / (float) ENTREE;
        long reste = m.duree - age;
        return reste < SORTIE ? Math.max(0f, reste / (float) SORTIE) : 1f;
    }

    /** Glissement depuis la droite à l'entrée. */
    private static float decalage(Message m, long maintenant) {
        long age = maintenant - m.debut;
        return age < ENTREE ? (1f - age / (float) ENTREE) * GLISSE : 0f;
    }
}
