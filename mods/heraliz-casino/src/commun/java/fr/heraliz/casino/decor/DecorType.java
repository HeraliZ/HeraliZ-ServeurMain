package fr.heraliz.casino.decor;

import fr.heraliz.casino.decor.DecorGeom;
import fr.heraliz.casino.decor.DecorNames;
import fr.heraliz.casino.machine.Footprint;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static fr.heraliz.casino.machine.Footprint.box;

/**
 * Elements de decor du casino du bunker : enseignes et ecrans recuperes qui gresillent, neons bricoles, affiches,
 * vieille tele, baril-brasero, poteaux a chaine, baladeuse ; sieges du Trou et du Coffre, porte du coffre-fort...
 * Encombrement en pixels (repere du modele : face avant vers +Z ; pieces murales : dos en z = 0 contre le mur).
 * Clic droit du personnel : variante suivante (ou marche / arret s'il n'y en a qu'une) ; accroupi : marche / arret.
 * Sieges : clic droit de n'importe quel joueur = s'asseoir. Porte du coffre : clic droit = ouvrir / fermer.
 */
public enum DecorType {
    /** Devanture : dalle d'ecran mince "CASINO", lettres qui meurent, parasites. Variantes : couleur du neon. */
    ENSEIGNE("enseigne_casino", Place.WALL, 12, 0xFF3A8A, DecorNames.SIGN_COLORS.length, new Footprint(box(1, 2.4f, 0, 31, 13.6f, 3.2f))),
    /** Petit ecran OUVERT / FERME pendu a un clou. */
    OUVERT("panneau_ouvert", Place.WALL, 7, 0x5CFF6A, 2, new Footprint(box(2.2f, 2.4f, 0, 13.8f, 15, 2.6f))),
    /** Afficheur a diodes du jackpot progressif, en direct. */
    JACKPOT("ecran_jackpot", Place.WALL, 9, 0xFFB040, 1, new Footprint(box(0.6f, 1.6f, 0, 31.4f, 14.4f, 3.4f))),
    /** Fleche a ampoules chenillard : CAISSE, JEUX, SORTIE... dans les deux sens. */
    FLECHE("fleche_neon", Place.WALL, 10, 0xFFD36A, DecorNames.ARROW_LABELS.length * 2, new Footprint(box(0.4f, 2.2f, 0, 31.6f, 13.8f, 2.8f))),
    /** Neon bricole sur un grillage : 777, cerises, as de pique, des, BAR, fer a cheval. */
    NEON("neon_mural", Place.WALL, 11, 0xFF4FD8, DecorNames.NEON.length, new Footprint(box(0.8f, 0.8f, 0, 15.2f, 15.2f, 2.6f))),
    /** Affiche de papier collee au mur (plusieurs modeles). */
    AFFICHE("affiche", Place.WALL, 0, 0xE8D8A8, DecorNames.POSTERS.length, new Footprint(box(1.8f, 0.4f, 0, 14.2f, 15.8f, 0.8f))),
    /** Vieille tele cathodique a antennes (chaines). */
    TELE("tele_crt", Place.FLOOR, 6, 0x9CFF3A, DecorNames.TV.length, new Footprint(box(1.4f, 0, 1.4f, 14.6f, 11.2f, 13.8f))),
    /** Baril-brasero : fut perce, braises et flammes. */
    BARIL("baril_feu", Place.FLOOR, 15, 0xFF8A2A, 1, new Footprint(box(3, 0, 3, 13, 14.6f, 13))),
    /** Poteau de file d'attente : tuyau rouille sur une jante, chaine jusqu'au poteau voisin. */
    POTEAU("poteau_chaine", Place.FLOOR, 0, 0xD8B070, 1, new Footprint(box(5.2f, 0, 5.2f, 10.8f, 15.6f, 10.8f))),
    /** Baladeuse pendue au plafond : ampoule grillagee sous un abat-jour emaille. */
    LAMPE("lampe_grillagee", Place.CEILING, 13, 0xFFE3A0, 1, new Footprint(box(5, 3.6f, 5, 11, 16, 11))),
    // ---- le Trou (casino des clochards)
    /** Ampoule nue pendue a son fil torsade, douille de bakelite ; elle se balance et gresille. */
    AMPOULE("ampoule_fil", Place.CEILING, 12, 0xFFD890, 1, new Footprint(box(6, 3, 6, 10, 16, 10))),
    /** Matelas crasseux sur deux palettes, couverture militaire, bouteilles vides (deux blocs de long). */
    MATELAS("matelas", Place.FLOOR, 0, 0xB8A888, 1, new Footprint(box(0, 0, 0.5f, 32, 7.5f, 15.5f))),
    /** Touret de cable en guise de table : cartes, jetons, bouteille, bougie plantee dans un goulot. */
    TOURET("touret_table", Place.FLOOR, 7, 0xFFC870, 1, new Footprint(box(0.5f, 0, 0.5f, 15.5f, 12.5f, 15.5f))),
    // ---- le Coffre (casino VIP)
    /** Potelet de laiton recupere, cordon de velours rouge jusqu'au potelet voisin. */
    POTELET("potelet_velours", Place.FLOOR, 0, 0xC83A3A, 1, new Footprint(box(5, 0, 5, 11, 15.5f, 11))),
    /** Lustre de recuperation : jante de roue, ampoules et bouteilles pendues a des chaines. */
    LUSTRE("lustre", Place.CEILING, 15, 0xFFD890, 1, new Footprint(box(0.5f, 2, 0.5f, 15.5f, 16, 15.5f))),
    /** Fauteuil club en cuir craquele, clous de laiton, coussin rapiece. */
    FAUTEUIL("fauteuil_club", Place.FLOOR, 0, 0xA86A3A, 1, new Footprint(box(1, 0, 1, 15, 14, 15))),
    /**
     * Porte ronde de coffre-fort dans son dormant (trois blocs de large et de haut), qui s'ouvre et se ferme : volant,
     * pennes, puis le vantail pivote sur son gond. Fermee, elle bouche le passage ; ouverte, on passe par le rond du
     * dormant. Le vantail ouvert balaie deux blocs a gauche et quatre devant (reserves a la pose).
     */
    COFFRE("porte_coffre", Place.WALL, 0, 0xD8B070, 1, Footprint.withSpots(new float[][]{DecorGeom.VAULT_SWING_ZONE}, DecorGeom.VAULT_CLOSED)),
    // ---- 2.0.0 : le Trou
    /** Botte de paille ficelee : on s'y assoit. Variantes : nue, couverture, carton et canette. */
    BOTTE("botte_paille", Place.FLOOR, 0, 0xE8C860, 3, new Footprint(box(1.5f, 0, 3.6f, 14.5f, 8.2f, 12.4f))),
    /** Reglette a tubes fluorescents pendue au plafond : elle gresille, claque et s'eteint par a-coups (vraie lumiere). */
    NEON_PLAFOND("neon_plafond", Place.CEILING, 13, 0xD8F4FF, 3, new Footprint(box(0.5f, 11.5f, 4.5f, 31.5f, 16, 11.5f))),
    /** Graffiti CAISSE a la bombe fluo, eclaire par un tube a lumiere noire (trois blocs sur deux). */
    GRAFFITI_CAISSE("graffiti_caisse", Place.WALL, 6, 0x7CFF3A, 8, new Footprint(box(-16, 0, 0, 32, 32, 3.5f))),
    /** Caddie de supermarche rempli de bric-a-brac (sacs, cartons, couverture, bouteilles). */
    CADDIE("caddie", Place.FLOOR, 0, 0xB8C0C8, 1, new Footprint(box(2.6f, 0, 0.5f, 13.4f, 15.5f, 15.5f))),
    /** Canape defonce : velours a fleurs tache, ressorts qui sortent, accoudoir scotche (deux places). */
    CANAPE("canape_defonce", Place.FLOOR, 0, 0xB0805A, 1, new Footprint(box(0, 0, 1.5f, 32, 13, 15))),
    /** Tas de sacs poubelle, un sac eventre, des mouches. */
    SACS("sacs_poubelle", Place.FLOOR, 0, 0x5A5A5A, 1, new Footprint(box(0.5f, 0, 0.5f, 15.5f, 10, 15.5f))),
    /** Rechaud de camping a gaz, gamelle qui fume, boite de haricots. */
    RECHAUD("rechaud", Place.FLOOR, 6, 0x5AA0FF, 1, new Footprint(box(2.5f, 0, 2.5f, 13.5f, 7.5f, 13.5f))),
    /** Pile de pneus en guise de tabouret. */
    PNEUS("pneus", Place.FLOOR, 0, 0x8A8A8A, 1, new Footprint(box(1.2f, 0, 1.2f, 14.8f, 12, 14.8f))),
    /** Pancarte de carton ecrite au feutre et sa boite de conserve a jetons. */
    PANCARTE("pancarte_carton", Place.FLOOR, 0, 0xD8B080, DecorNames.SIGNS.length, new Footprint(box(2, 0, 0.5f, 14, 12, 9))),
    /** Radio-cassette (ghetto-blaster) posee sur une caisse : afficheur et vu-metres qui dansent. */
    RADIO("radio_cassette", Place.FLOOR, 4, 0x40FF80, 1, new Footprint(box(1.5f, 0, 3.5f, 14.5f, 12, 12.5f))),
    /** Chaise de jardin en plastique blanc, sale, un pied rafistole. */
    CHAISE("chaise_jardin", Place.FLOOR, 0, 0xE8E4D4, 1, new Footprint(box(2.2f, 0, 2.2f, 13.8f, 14, 13.8f))),
    /** Cabane de carton : grand carton couche, couverture et sac de couchage dedans (deux blocs de profondeur). */
    CABANE("cabane_carton", Place.FLOOR, 0, 0xC0956A, 1, new Footprint(box(0.5f, 0, 0.5f, 15.5f, 22.6f, 31))),
    // ---- 2.0.0 : le Coffre
    /** Graffiti VIP dore, couronne et etincelles, eclaire par une lampe de tableau en laiton (trois blocs sur deux). */
    GRAFFITI_VIP("graffiti_vip", Place.WALL, 9, 0xFFC840, 8, new Footprint(box(-16, 0, 0, 32, 32, 3.5f))),
    /** Canape Chesterfield capitonne en cuir sang-de-boeuf (deux places). */
    CHESTERFIELD("canape_chesterfield", Place.FLOOR, 0, 0xB0402A, 1, new Footprint(box(0, 0, 1.2f, 32, 13, 15))),
    /** Tabouret de bar : assise de cuir, pied chrome, repose-pieds. */
    TABOURET("tabouret_bar", Place.FLOOR, 0, 0xD8B060, 1, new Footprint(box(3.8f, 0, 3.8f, 12.2f, 13.6f, 12.2f))),
    /** Lampe de banquier : abat-jour de verre vert, pied de laiton. */
    LAMPE_BANQUIER("lampe_banquier", Place.FLOOR, 10, 0x3AD870, 1, new Footprint(box(3.5f, 0, 5, 12.5f, 8.5f, 11))),
    /** Gueridon : carafe de whisky, verres, cigare qui fume, jetons. */
    GUERIDON("table_whisky", Place.FLOOR, 0, 0xD8A040, 1, new Footprint(box(2, 0, 2, 14, 12.5f, 14))),
    /** Mur de coffres de depot numerotes, l'un entrouvert (trois blocs sur deux). */
    CASIERS("casiers_coffres", Place.WALL, 0, 0xC8C8D0, 1, new Footprint(box(-16, 0, 0, 32, 32, 7.5f))),
    /** Pile de lingots d'or estampilles. */
    LINGOTS("lingots", Place.FLOOR, 3, 0xFFD040, 1, new Footprint(box(1.5f, 0, 2, 14.5f, 7.5f, 14))),
    /** Mallette d'aluminium ouverte, pleine de liasses de billets. */
    VALISE("valise_billets", Place.FLOOR, 0, 0x7AC07A, 1, new Footprint(box(1.5f, 0, 2.5f, 14.5f, 11, 13.5f))),
    /** Applique murale de laiton a deux bougies et abat-jours plisses. */
    APPLIQUE("applique_laiton", Place.WALL, 11, 0xFFD890, 1, new Footprint(box(2, 3, 0, 14, 15, 6.5f))),
    /** Juke-box des annees 50 : arche de tubes lumineux qui changent de couleur, disques (deux blocs de haut). */
    JUKEBOX("juke_box", Place.FLOOR, 9, 0xFF7AD0, 1, new Footprint(box(1, 0, 4, 15, 20.5f, 12.8f))),
    /** Palmier en pot de laiton, un peu poussiereux (deux blocs de haut). */
    PALMIER("palmier_pot", Place.FLOOR, 0, 0x6AB048, 1, Footprint.withSpots(new float[][]{box(0, 9, 0, 16, 31, 16)}, box(3.5f, 0, 3.5f, 12.5f, 9, 12.5f)));

    /** Maniere de poser l'element. */
    public enum Place {
        /** Contre la face du bloc vise, dos au mur. */
        WALL,
        /** Au sol, face au joueur. */
        FLOOR,
        /** Pendu au plafond, face au joueur. */
        CEILING
    }

    /**
     * Place assise (repere du modele, px) : centre de l'assise (x, z) et sa hauteur, direction du regard, point ou l'on
     * se releve, ecart permis de la tete (degres).
     */
    public record Seat(float x, float z, float h, float lookX, float lookZ, float exitX, float exitZ, float range) {
        /** Hauteur des pieds du joueur assis au-dessus du sol du bloc (blocs) : les hanches du modele sont a 12 px. */
        public float feet() {
            return (h - 12f) / 16f;
        }
    }

    /** Porte du coffre ouverte : on passe par le rond du dormant, le vantail rabattu garde sa collision. */
    private static final class OpenVault {
        static final Footprint FOOTPRINT = Footprint.withSpots(new float[][]{DecorGeom.VAULT_SWING_ZONE}, DecorGeom.VAULT_OPEN);
    }

    public final String id;
    public final Place place;
    /** Lumiere emise quand l'element est allume (0-15). */
    public final int light;
    /** Couleur principale (nom de l'objet). */
    public final int glow;
    /** Nombre de variantes choisies au clic droit (textes, motifs, chaines) ; 1 : le clic allume / eteint. */
    public final int modes;
    public final Footprint footprint;

    DecorType(String id, Place place, int light, int glow, int modes, Footprint footprint) {
        this.id = id;
        this.place = place;
        this.light = light;
        this.glow = glow;
        this.modes = modes;
        this.footprint = footprint;
    }

    public boolean wall() {
        return place == Place.WALL;
    }

    /** Poteaux relies au voisin du meme type (chaine, cordon de velours). */
    public boolean linked() {
        return this == POTEAU || this == POTELET;
    }

    /**
     * L'element a un etat marche / arret (lumiere, ecran, feu, chaine) que le personnel bascule. Les sieges, les
     * meubles, les affiches et la porte du coffre n'en ont pas : le clic ne doit jamais y afficher "Allume / Eteint".
     */
    public boolean switchable() {
        return switch (this) {
            case ENSEIGNE, OUVERT, JACKPOT, FLECHE, NEON, TELE, BARIL, POTEAU, LAMPE, AMPOULE, TOURET, POTELET, LUSTRE,
                    NEON_PLAFOND, GRAFFITI_CAISSE, GRAFFITI_VIP, RECHAUD, RADIO, LAMPE_BANQUIER, APPLIQUE, JUKEBOX -> true;
            default -> false;
        };
    }

    /** Porte qui s'ouvre et se ferme (l'etat "allume" du bloc = ouverte). */
    public boolean door() {
        return this == COFFRE;
    }

    /** Lumiere qui gresille pour de bon (le serveur fait varier la lumiere du bloc). */
    public boolean flicker() {
        return this == NEON_PLAFOND;
    }

    /** Encombrement selon l'etat (porte ouverte : on passe par le rond du dormant). */
    public Footprint footprint(boolean on) {
        return door() && on ? OpenVault.FOOTPRINT : footprint;
    }

    private static final Seat[] NO_SEAT = new Seat[0];

    /** Places assises de l'element (vide : on ne s'y assoit pas). */
    public Seat[] seats(int mode) {
        return switch (this) {
            case FAUTEUIL -> new Seat[]{new Seat(8, 8.6f, 7.2f, 0, 1, 8, 24, 85)};
            case MATELAS -> new Seat[]{new Seat(7.5f, 11, 6.8f, 0, 1, 7.5f, 26, 80), new Seat(23.5f, 11, 6.8f, 0, 1, 23.5f, 26, 80)};
            case BOTTE -> new Seat[]{new Seat(8, 8, Math.floorMod(mode, modes) == 1 ? 8.9f : 8.5f, 0, 1, 8, 22, 90)};
            case CANAPE -> new Seat[]{new Seat(8.6f, 9, 7.0f, 0, 1, 8.6f, 25, 85), new Seat(23.4f, 9, 7.0f, 0, 1, 23.4f, 25, 85)};
            case PNEUS -> new Seat[]{new Seat(8, 8, 12.3f, 0, 1, 8, 22, 100)};
            case CHAISE -> new Seat[]{new Seat(8, 8.4f, 7.4f, 0, 1, 8, 22, 85)};
            case CABANE -> new Seat[]{new Seat(8, 21, 1.4f, 0, 1, 8, 38, 70)};
            case CHESTERFIELD -> new Seat[]{new Seat(8.6f, 8.8f, 7.6f, 0, 1, 8.6f, 25, 85), new Seat(23.4f, 8.8f, 7.6f, 0, 1, 23.4f, 25, 85)};
            case TABOURET -> new Seat[]{new Seat(8, 8, 13.6f, 0, 1, 8, 22, 180)};
            default -> NO_SEAT;
        };
    }

    public boolean seated() {
        return seats(0).length > 0;
    }

    /** Le clic droit fait quelque chose (sinon il laisse passer : poser un bloc contre, etc.). */
    public boolean interactive() {
        return modes > 1 || switchable() || door() || seated();
    }

    /**
     * Modele 3D de la variante (client) : les variantes dont la forme change (sens de la fleche, motif du neon,
     * affiche) ont chacune leur modele, fige une fois pour toutes.
     */
    public String designId(int mode) {
        int m = Math.floorMod(mode, modes);
        return switch (this) {
            case FLECHE -> DecorNames.arrowLeft(m) ? id + "_g" : id;
            case NEON, AFFICHE, BOTTE, PANCARTE, GRAFFITI_CAISSE, GRAFFITI_VIP -> id + "_" + m;
            default -> id;
        };
    }

    public MutableComponent displayName() {
        return Component.translatable("block.heralizcasino." + id);
    }

    public MutableComponent description() {
        return Component.translatable("decor.heralizcasino." + id + ".desc");
    }

    /** Nom de la variante (message au clic droit). */
    public MutableComponent modeName(int mode) {
        int m = Math.floorMod(mode, modes);
        if (this == FLECHE) {
            return Component.literal(DecorNames.ARROW_LABELS[DecorNames.arrowLabel(m)] + (DecorNames.arrowLeft(m) ? "  ←" : "  →"));
        }
        return Component.translatable("decor.heralizcasino." + id + ".mode." + m);
    }
}
