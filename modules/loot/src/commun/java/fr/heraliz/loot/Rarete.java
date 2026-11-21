package fr.heraliz.loot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.world.item.ItemStack;

/**
 * Les 6 raretés des objets (choix du propriétaire) : couleur (fond des cases, cadre des coffres T7, infobulle), nom
 * affiché, temps d'identification dans un coffre T7 (ticks) — plus c'est rare, plus c'est long, et badge de l'infobulle.
 * L'ancienne rareté « mythique » des tables est lue comme Unique.
 */
public enum Rarete {
    COMMUN("commun", "Commun", 0x9A9A9A, 20, "\ue806"),
    PEU_COMMUN("peu_commun", "Peu commun", 0x5CBF4A, 30, "\ue804"),
    RARE("rare", "Rare", 0x3F8CFF, 45, "\ue803"),
    EPIQUE("epique", "Épique", 0xA64DFF, 70, "\ue80d"),
    LEGENDAIRE("legendaire", "Légendaire", 0xF2B83A, 110, "\ue805"),
    UNIQUE("unique", "Unique", 0xFF3333, 220, "\ue810");

    public static final String CLE = "HeralizRarete";
    public final String id, nom;
    public final int couleur, duree;
    /**
     * Marqueur du badge de la rareté (14/11, retour du propriétaire : « juste une fois le petit encadré avec écrit
     * EPIC ») : un caractère de la police des rangs du jar des meubles (cfm), une petite image en anglais (« COMMON »…
     * « LEGENDARY », « ARTIFACT » pour Unique). Le noyau (fr.heraliz.noyau.client.BadgeRarete) remplace cette ligne par le
     * vrai badge : rectangle de la couleur de la rareté, nom dans la langue du joueur ; l'image n'apparaît que si le
     * noyau manque.
     */
    public final String glyphe;

    Rarete(String id, String nom, int couleur, int duree, String glyphe) {
        this.id = id;
        this.nom = nom;
        this.couleur = couleur;
        this.duree = duree;
        this.glyphe = glyphe;
    }

    /**
     * Style du badge : blanc (l'image garde ses couleurs), police par défaut (la seule qui a ce caractère), sans gras ni
     * italique, même posé dans une ligne qui a une autre police ou une autre couleur (écrans des caisses, marchands…).
     */
    private static final Style STYLE_BADGE = Style.EMPTY.withColor(0xFFFFFF).withFont(Style.DEFAULT_FONT).withBold(false)
            .withItalic(false).withUnderlined(false);

    /** La ligne d'infobulle de cette rareté : son badge seul (une nouvelle ligne à chaque appel, jamais partagée). */
    public MutableComponent badge() {
        return Component.literal(glyphe).setStyle(STYLE_BADGE);
    }

    /** Cette ligne est-elle un badge de rareté seul (le nôtre, ou celui d'un autre mod : camos, stickers…) ? Sans allocation. */
    public static boolean estBadge(Component ligne) {
        if (!ligne.getSiblings().isEmpty() || !(ligne.getContents() instanceof LiteralContents lc)) return false;
        String t = lc.text();
        if (t.length() != 1) return false;
        for (Rarete r : TOUTES) {
            if (r.glyphe.charAt(0) == t.charAt(0)) return true;
        }
        return false;
    }

    /** Les raretés, une fois pour toutes (values() copie le tableau à chaque appel). */
    private static final Rarete[] TOUTES = values();

    /**
     * Couleur du NOM d'un objet de cette rareté (infobulle, nom affiché au-dessus de la barre d'objets : {@link
     * RaretesObjets#couleurNom}) : celle de la rareté, sauf Commun en blanc (comme les noms Commun depuis le 21/10 ; le
     * gris reste au fond des cases).
     */
    public int couleurNom() {
        return this == COMMUN ? 0xFFFFFF : couleur;
    }

    /** Nom affiché, dans la langue du joueur (clé heraliz_loot.rarete.&lt;id&gt;, nom français en secours). */
    public MutableComponent texte() {
        return Component.translatableWithFallback("heraliz_loot.rarete." + id, nom);
    }

    /** Rareté d'après son identifiant (« mythique », ancien nom, = Unique) ; inconnu : Commun. */
    public static Rarete de(String id) {
        Rarete r = lire(id);
        return r == null ? COMMUN : r;
    }

    /** Rareté d'après son identifiant, null si inconnu. */
    public static Rarete lire(String id) {
        if (id == null) return null;
        if (id.equalsIgnoreCase("mythique")) return UNIQUE;
        // 21/11 (audit) : TOUTES plutôt que values() (une copie du tableau à chaque appel : objets des coffres T7, pour chaque
        // case à chaque image)
        for (Rarete r : TOUTES) {
            if (r.id.equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    /** Rareté inscrite sur un objet de coffre (null : objet ordinaire). */
    public static Rarete de(ItemStack st) {
        return st.hasTag() && st.getTag().contains(CLE) ? de(st.getTag().getString(CLE)) : null;
    }

    public boolean haute() {
        return ordinal() >= LEGENDAIRE.ordinal();
    }
}
