package fr.heraliz.loot.client;

import fr.heraliz.loot.RaretesObjets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.item.ItemStack;

/**
 * Nom d'un objet dans la couleur de sa rareté HeraliZ (11/11, retour du propriétaire : « un item violet doit avoir un nom
 * écrit en violet »). Une seule règle ({@link RaretesObjets#couleurNom}) pour l'infobulle ({@link ClientLoot}) et pour le
 * nom affiché au-dessus de la barre d'objets quand on change d'objet en main ({@code mixin.NomEnMainMixin}) :
 * <ul><li>objet d'une rareté choisie (raretes.json, /rarete, ou inscrite sur l'objet) : TOUT le nom dans sa couleur
 * (Commun : blanc) ; les autres couleurs du nom sont retirées : codes § des packs d'armes de TaCZ (« §6Viseur… » : une
 * autre échelle que la nôtre), couleur propre de l'objet (carte d'accès verte, pièces d'armure…). Gras et italique (objet
 * renommé) gardés ;</li>
 * <li>sans rareté choisie : la couleur de la rareté de Minecraft (Épique : rose, Rare : cyan, Peu commun : jaune) devient
 * la nôtre (violet, bleu, vert) ; les couleurs propres du nom restent (clés des caisses, machines du casino…) ;</li>
 * <li>couleur choisie par le staff (bons de HeraliZ Grades) ou objet Commun pour Minecraft sans rareté choisie : rien ne
 * change.</li></ul>
 * Avant : le nom en main gardait la rareté de Minecraft (armes, lunettes et munitions de TaCZ en blanc, composants III en
 * rose) et l'infobulle laissait les noms à codes § et les couleurs propres passer devant. La rareté vient de la table du
 * serveur (rechargée par /recharger raretes) : rien à colorer objet par objet. Appelé à chaque image tant que l'infobulle
 * ou le nom est affiché : un nom par appel, quelques petits objets.
 */
public final class NomsRarete {
    private NomsRarete() {
    }

    /** Le nom {@code nom} de l'objet {@code st} dans la couleur de sa rareté (voir la classe) ; {@code nom} s'il ne change pas. */
    public static Component nom(@Nullable ItemStack st, Component nom) {
        if (nom == null || st == null) return nom;
        int couleur = RaretesObjets.couleurNom(st);
        if (couleur < 0) return nom;
        if (RaretesObjets.choisie(st) != null) return colorer(nom, TextColor.fromRgb(couleur), true, null);
        // rareté de Minecraft : seul le texte de sa couleur (ou sans couleur) change, les couleurs propres du nom restent
        return colorer(nom, TextColor.fromRgb(couleur), false, st.getRarity().getStyleModifier().apply(Style.EMPTY).getColor());
    }

    /** Noms des packs d'armes déjà sans couleurs, par clé de traduction ; vidé quand la langue change (rechargement). */
    private static final Map<String, MutableComponent> SANS_COULEURS = new ConcurrentHashMap<>();
    @Nullable
    private static volatile Language langue;

    /**
     * Nom d'une arme, d'un accessoire ou d'une munition de TaCZ sans les couleurs écrites dans son pack d'armes (codes §
     * de couleur ; gras et autres styles gardés), null s'il n'en a pas : rien à changer. Ces couleurs (une autre échelle
     * que nos raretés : « §5Viseur LPVO » Légendaire, « §9Viseur PK06 » Commun…) passaient devant la couleur de la
     * rareté HeraliZ et celle des écrans (marchands, recycleur, atelier, caisses…). Appelé par {@code mixin.NomsTaczMixin}
     * (fil du jeu, ou du serveur intégré en solo) ; un seul calcul par nom et par langue.
     */
    @Nullable
    public static Component sansCouleursDuPack(@Nullable Component nom) {
        if (nom == null || !(nom.getContents() instanceof TranslatableContents tc) || tc.getArgs().length > 0
                || !nom.getSiblings().isEmpty() || !nom.getStyle().isEmpty()) return null;
        Language l = Language.getInstance();
        if (l.getOrDefault(tc.getKey()).indexOf('\u00A7') < 0) return null;
        if (l != langue) {
            SANS_COULEURS.clear();
            langue = l;
        }
        return SANS_COULEURS.computeIfAbsent(tc.getKey(), k -> colorer(nom, null, true, null)).copy();
    }

    /**
     * Copie à plat du nom (morceaux de texte simple), tel que la police l'afficherait (codes § appliqués comme elle le
     * fait), avec la couleur {@code couleur} (null : aucune) : sur tout le texte si {@code tout}, sinon seulement sur le
     * texte sans couleur ou de la couleur {@code remplacee} (les autres couleurs restent).
     */
    static MutableComponent colorer(Component nom, @Nullable TextColor couleur, boolean tout, @Nullable TextColor remplacee) {
        Copie c = new Copie(couleur, tout, remplacee);
        StringDecomposer.iterateFormatted(nom, Style.EMPTY, c);
        return c.fin();
    }

    /** Recopie le texte caractère par caractère, en morceaux de même style. */
    private static final class Copie implements FormattedCharSink {
        @Nullable
        private final TextColor couleur, remplacee;
        private final boolean tout;
        private final MutableComponent sortie = Component.empty();
        private final StringBuilder morceau = new StringBuilder();
        /** Dernier style lu (le même objet pour toute une suite de caractères) et le style écrit pour lui. */
        @Nullable
        private Style lu, ecrit;

        Copie(@Nullable TextColor couleur, boolean tout, @Nullable TextColor remplacee) {
            this.couleur = couleur;
            this.tout = tout;
            this.remplacee = remplacee;
        }

        @Override
        public boolean accept(int position, Style style, int point) {
            if (style != this.lu) {
                this.lu = style;
                TextColor c = style.getColor();
                Style s = this.tout || c == null || c.equals(this.remplacee) ? style.withColor(this.couleur) : style;
                if (!s.equals(this.ecrit)) {
                    vider();
                    this.ecrit = s;
                }
            }
            this.morceau.appendCodePoint(point);
            return true;
        }

        private void vider() {
            if (this.morceau.length() > 0) {
                this.sortie.append(Component.literal(this.morceau.toString()).setStyle(this.ecrit));
                this.morceau.setLength(0);
            }
        }

        MutableComponent fin() {
            vider();
            return this.sortie;
        }
    }
}
