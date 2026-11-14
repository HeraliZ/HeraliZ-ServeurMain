package fr.heraliz.noyau.client;

import com.mojang.datafixers.util.Either;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Badge de rareté des infobulles (14/11, demande du propriétaire : « pour toutes les raretés, le même genre de badge que
 * EPIC : un rectangle de la couleur de la rareté, avec le nom de la rareté écrit dedans, juste sous le nom de l'objet »),
 * dessiné ici pour tous nos mods :
 * <ul>
 * <li>rectangle plein de la couleur de la rareté (celles de fr.heraliz.loot.Rarete : Commun gris, Peu commun vert, Rare
 * bleu, Épique violet, Légendaire jaune, Unique rouge), coins adoucis d'un point comme les panneaux du kit ;</li>
 * <li>dedans, le nom de la rareté dans la langue du joueur, en capitales espacées : les noms de HeraliZ Loot (clés
 * {@code heraliz_loot.rarete.<id>} en français, anglais et russe, nom français en secours), en DM Sans gras 6,5
 * ({@link PoliceHud#MINI_GRAS}, la police des pastilles) à sa vraie taille, posé sur un point entier de l'écran (net), en
 * presque noir : lisible sur les six couleurs (contraste de 5 à 12 pour 1, jaune compris) ;</li>
 * <li>même hauteur pour les six ({@link #HAUTEUR}), largeur selon le nom.</li>
 * </ul>
 * Qui le demande : une ligne d'infobulle faite du seul « marqueur » d'une rareté, le caractère de la police des rangs que
 * posent HeraliZ Loot (Rarete.badge), Tactique (camos, stickers, module StatTrak) et Monde (Bonus de loot) ; c'était
 * l'ancien petit badge en image, en anglais seulement. Ici :
 * <ul>
 * <li>infobulles du jeu (inventaire, coffres, sacs, marchands, réparation, mannequin…) : la ligne devient un composant
 * d'infobulle de Forge, remis juste sous le nom, même devant les caractéristiques d'une arme ou d'un accessoire
 * ({@link #rassembler}) ;</li>
 * <li>infobulles dessinées par nos écrans (kits des marchands, de la Table d'armurier, des caisses) : ces écrans demandent
 * ici la largeur et le dessin de la ligne ({@link #largeur(Font, Component)}, {@link #dessiner(GuiGraphics, Font,
 * Component, int, int, int)}, ou les mêmes sur une ligne déjà coupée : {@link #sequence}) ;</li>
 * <li>ligne « badge + texte » (cartes des caisses : le badge, puis la chance) :
 * {@code Component.empty().append(BadgeRarete.ligne(rang)).append(texte)}.</li>
 * </ul>
 * Rien n'est alloué à chaque image : textes et largeurs gardés par rareté, recalculés seulement quand la langue change
 * (ou au rechargement des ressources) ; composants d'infobulle gardés par rareté.
 */
public final class BadgeRarete {
    private BadgeRarete() {
    }

    /** Hauteur du badge (unités d'interface), la même pour les six raretés : il tient dans une ligne d'infobulle (10). */
    public static final int HAUTEUR = 9;
    /** Marge du texte à gauche et à droite ; écart entre le badge et le texte qui le suit sur la même ligne. */
    private static final int MARGE = 4, ECART = 4;
    /** Espace entre deux capitales (unités d'interface). */
    private static final float ESPACE = 0.5f;
    /** Couleur des lettres (ARGB) : presque noir. */
    private static final int LETTRES = 0xFF15171C;
    /** Couleurs des raretés (RVB), de Commun à Unique : celles de fr.heraliz.loot.Rarete. */
    private static final int[] COULEURS = {0x9A9A9A, 0x5CBF4A, 0x3F8CFF, 0xA64DFF, 0xF2B83A, 0xFF3333};
    /** Identifiants des raretés : clés {@code heraliz_loot.rarete.<id>} des fichiers de langue de HeraliZ Loot. */
    private static final String[] IDS = {"commun", "peu_commun", "rare", "epique", "legendaire", "unique"};
    /** Noms français, en secours si la clé manque. */
    private static final String[] NOMS = {"Commun", "Peu commun", "Rare", "Épique", "Légendaire", "Unique"};
    /** Marqueurs, de Commun à Unique : caractères de la police des rangs (jar des meubles) que posent nos mods. */
    private static final char[] MARQUEURS = {'\ue806', '\ue804', '\ue803', '\ue80d', '\ue805', '\ue810'};
    /** Style des marqueurs créés ici ({@link #ligne}) : ceux de HeraliZ Loot (blanc, police par défaut). */
    private static final Style STYLE_MARQUEUR = Style.EMPTY.withColor(0xFFFFFF).withFont(Style.DEFAULT_FONT).withBold(false)
            .withItalic(false).withUnderlined(false);

    /** Nom de chaque rareté, en capitales espacées, dans la langue du joueur. */
    private static final TexteHud[] TEXTES = new TexteHud[6];
    /** Largeur de chaque badge (unités d'interface). */
    private static final int[] LARGEURS = new int[6];
    /** Langue des textes gardés : Language.getInstance() change à chaque langue choisie et à chaque rechargement. */
    private static Language langue;

    /** Badge dans une infobulle du jeu : la donnée (côté jeu), seulement son rang. */
    public record Infobulle(int rang) implements TooltipComponent {
    }

    /** Dessin du badge dans une infobulle du jeu : une ligne de la hauteur d'une ligne de texte. */
    private record Dessin(int rang) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return HAUTEUR + 1;
        }

        @Override
        public int getWidth(Font font) {
            return largeur(rang);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            dessiner(g, rang, x, y - 1);
        }
    }

    /** Tient la place d'un badge dans une infobulle qui coupe ses lignes avant de les dessiner (rien à écrire). */
    private record Place(int rang) implements FormattedCharSequence {
        @Override
        public boolean accept(FormattedCharSink sink) {
            return true;
        }
    }

    private static final Infobulle[] INFOBULLES = new Infobulle[6];
    private static final Dessin[] DESSINS = new Dessin[6];
    private static final Place[] PLACES = new Place[6];
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Either<FormattedText, TooltipComponent>[] ELEMENTS = new Either[6];

    static {
        for (int r = 0; r < 6; r++) {
            TEXTES[r] = new TexteHud(PoliceHud.MINI_GRAS, ESPACE);
            INFOBULLES[r] = new Infobulle(r);
            DESSINS[r] = new Dessin(r);
            PLACES[r] = new Place(r);
            ELEMENTS[r] = Either.right(INFOBULLES[r]);
        }
    }

    /** Branche le badge dans les infobulles du jeu (partie client du noyau). */
    public static void enregistrer(IEventBus busMod) {
        busMod.addListener(EventPriority.NORMAL, false, RegisterClientTooltipComponentFactoriesEvent.class,
                BadgeRarete::fabriques);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderTooltipEvent.GatherComponents.class,
                BadgeRarete::rassembler);
    }

    private static void fabriques(RegisterClientTooltipComponentFactoriesEvent e) {
        e.register(Infobulle.class, i -> DESSINS[Math.max(0, Math.min(5, i.rang()))]);
    }

    /**
     * Infobulles du jeu : la ligne « badge seul » (jamais le nom) devient le badge dessiné, remis juste sous le nom (Forge
     * pose l'image d'un objet, caractéristiques d'une arme ou d'un accessoire de TaCZ, juste sous le nom : avant, le badge
     * venait après elles). Un seul badge par infobulle.
     */
    private static void rassembler(RenderTooltipEvent.GatherComponents e) {
        List<Either<FormattedText, TooltipComponent>> l = e.getTooltipElements();
        for (int i = 1; i < l.size(); i++) {
            int r = l.get(i).map(BadgeRarete::rangTexte, BadgeRarete::aucun);
            if (r >= 0) {
                l.remove(i);
                l.add(1, ELEMENTS[r]);
                return;
            }
        }
    }

    private static Integer rangTexte(FormattedText t) {
        return t instanceof Component c ? marqueur(c) : -1;
    }

    private static Integer aucun(TooltipComponent c) {
        return -1;
    }

    /** Textes et largeurs dans la langue du joueur, recalculés seulement quand la langue (ou les ressources) change. */
    private static void maj() {
        Language l = Language.getInstance();
        if (l == langue) return;
        langue = l;
        for (int r = 0; r < 6; r++) {
            TEXTES[r].oublier();
            TEXTES[r].texte(l.getOrDefault("heraliz_loot.rarete." + IDS[r], NOMS[r]));
            LARGEURS[r] = (int) Math.ceil(TEXTES[r].largeur()) + 2 * MARGE;
        }
    }

    // ------------------------------------------------------------------ lignes d'infobulle
    /** Une ligne d'infobulle « badge » de ce rang (0 Commun … 5 Unique) : son marqueur, nouvelle ligne à chaque appel. */
    public static MutableComponent ligne(int rang) {
        return Component.literal(String.valueOf(MARQUEURS[Math.max(0, Math.min(5, rang))])).setStyle(STYLE_MARQUEUR);
    }

    /** Rang du marqueur que porte ce texte à lui seul, -1 sinon. Sans allocation. */
    private static int marqueur(Component c) {
        if (!c.getSiblings().isEmpty() || !(c.getContents() instanceof LiteralContents lc)) return -1;
        String t = lc.text();
        if (t.length() != 1) return -1;
        char ch = t.charAt(0);
        for (int r = 0; r < 6; r++) {
            if (MARQUEURS[r] == ch) return r;
        }
        return -1;
    }

    /** Rang d'une ligne faite d'un badge seul, -1 sinon. */
    public static int rangSeul(Component ligne) {
        return marqueur(ligne);
    }

    /** Rang du badge d'une ligne (badge seul, ou « badge + texte » : vide, puis le marqueur et un texte), -1 sinon. */
    public static int rang(Component ligne) {
        int r = marqueur(ligne);
        if (r >= 0) return r;
        List<Component> s = ligne.getSiblings();
        return s.size() == 2 && ligne.getContents() == ComponentContents.EMPTY ? marqueur(s.get(0)) : -1;
    }

    /** Rang du badge d'une infobulle du jeu déjà rassemblée (ses composants dessinés), -1 sans badge. Sans allocation. */
    public static int rang(List<ClientTooltipComponent> composants) {
        for (int i = 0; i < composants.size(); i++) {
            if (composants.get(i) instanceof Dessin d) return d.rang();
        }
        return -1;
    }

    /** Couleur (RVB) de la rareté de ce rang (0 Commun … 5 Unique). */
    public static int couleur(int rang) {
        return COULEURS[Math.max(0, Math.min(5, rang))];
    }

    /** Rang de la rareté de cette couleur (RVB, celles de HeraliZ Loot), -1 pour une autre couleur. */
    public static int rangCouleur(int rgb) {
        for (int r = 0; r < 6; r++) {
            if (COULEURS[r] == (rgb & 0xFFFFFF)) return r;
        }
        return -1;
    }

    /** Nom de la rareté de ce rang dans la langue du joueur (celui des badges). */
    public static String nom(int rang) {
        int r = Math.max(0, Math.min(5, rang));
        return Language.getInstance().getOrDefault("heraliz_loot.rarete." + IDS[r], NOMS[r]);
    }

    /** Largeur du badge de ce rang (unités d'interface). */
    public static int largeur(int rang) {
        maj();
        return LARGEURS[rang];
    }

    /** Largeur d'une ligne d'infobulle : badge (et texte qui le suit) ou texte ordinaire. */
    public static int largeur(Font font, Component ligne) {
        int r = rang(ligne);
        if (r < 0) return font.width(ligne);
        return ligne.getSiblings().isEmpty() ? largeur(r) : largeur(r) + ECART + font.width(ligne.getSiblings().get(1));
    }

    /**
     * Dessine le badge de ce rang, coin haut gauche en (x, y). Dans une ligne d'infobulle de haut {@code yLigne}, il se
     * pose en {@code yLigne - 1} : centré sur les capitales des lignes de texte (7 points), 3 points sous le nom et 2
     * au-dessus de la ligne suivante.
     */
    public static void dessiner(GuiGraphics g, int rang, int x, int y) {
        maj();
        int w = LARGEURS[rang], c = 0xFF000000 | COULEURS[rang];
        g.fill(x + 1, y, x + w - 1, y + 1, c);
        g.fill(x, y + 1, x + w, y + HAUTEUR - 1, c);
        g.fill(x + 1, y + HAUTEUR - 1, x + w - 1, y + HAUTEUR, c);
        TEXTES[rang].ajouter(g, x + w / 2f, y + HAUTEUR / 2f, LETTRES, TexteHud.CENTRE, false);
        g.flush();
    }

    /**
     * Dessine une ligne d'infobulle si c'est un badge (puis le texte qui le suit, de la couleur donnée, sans ombre) en
     * (x, y), haut de la ligne ; faux pour une ligne ordinaire (à dessiner par l'appelant).
     */
    public static boolean dessiner(GuiGraphics g, Font font, Component ligne, int x, int y, int couleur) {
        int r = rang(ligne);
        if (r < 0) return false;
        dessiner(g, r, x, y - 1);
        if (!ligne.getSiblings().isEmpty()) {
            g.drawString(font, ligne.getSiblings().get(1), x + LARGEURS[r] + ECART, y, couleur, false);
        }
        return true;
    }

    // ------------------------------------------------------------------ lignes déjà coupées
    /** Ligne déjà coupée qui tient la place d'un badge seul de ce rang (infobulles qui coupent leurs lignes). */
    public static FormattedCharSequence sequence(int rang) {
        return PLACES[Math.max(0, Math.min(5, rang))];
    }

    /** Largeur d'une ligne déjà coupée : celle du badge pour une {@link #sequence}, sinon celle du texte. */
    public static int largeur(Font font, FormattedCharSequence ligne) {
        return ligne instanceof Place p ? largeur(p.rang()) : font.width(ligne);
    }

    /**
     * Dessine le badge d'une {@link #sequence} dans la ligne de haut (x, y) ; faux pour une ligne ordinaire (à dessiner
     * par l'appelant).
     */
    public static boolean dessiner(GuiGraphics g, FormattedCharSequence ligne, int x, int y) {
        if (!(ligne instanceof Place p)) return false;
        dessiner(g, p.rang(), x, y - 1);
        return true;
    }
}
