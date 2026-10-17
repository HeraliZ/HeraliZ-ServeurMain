package fr.heraliz.crac.bases;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.registry.ModItems;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Le parcours du tutoriel des bases (CRac 1.7.0), une étape à la fois (carte en haut à gauche chez le joueur). Chaque
 * étape a un identifiant (gardé avec le joueur : il reprend où il en était) ; l'ordre de {@link #PARCOURS} est l'ordre du
 * tutoriel. Pour ajouter des étapes AVANT (visite du spawn prévue), les insérer en tête de la liste : rien d'autre à
 * changer (numérotation et reprise suivent).
 * <ul>
 *   <li>étape « lecture » : avance seule après son temps de lecture ({@code [tuto] lecture_pourcent}) ;</li>
 *   <li>étape « action » : avance quand le serveur constate l'action (objet en main, pièce posée, Claim Block posé...),
 *   jamais avant {@link Tutoriel#MIN_ACTION} ticks d'affichage.</li>
 * </ul>
 * Textes : {@code crac.tuto.pas.<id>.titre} et {@code .texte} (fr_fr, en_us, ru_ru), avec le français en secours.
 */
final class EtapesTuto {
    /** Valeur de {@link BasesDonnees.Joueur#tuto} une fois le parcours terminé. */
    static final String FINI = "fini";

    /** Où l'étape se déroule : ailleurs, elle attend (carte cachée). */
    enum Lieu {
        SPAWN, BASES;

        boolean ok(ServerPlayer p) {
            return this == SPAWN ? MondeBases.estSpawn(p.level()) : MondeBases.estBases(p.level());
        }
    }

    /** Une touche à montrer : jeton ({@code attaque}, {@code utiliser}, {@code tourner}, {@code inventaire}, ou « /commande ») et légende. */
    record Touche(String jeton, String legende) {
    }

    /**
     * Une étape. {@code lectureS} > 0 : étape « lecture » ; sinon {@code progres} rend {fait, objectif} (objectif 0 :
     * pas de compteur affiché, l'étape est faite quand fait >= 1). {@code entree} : action en arrivant sur l'étape
     * (kit) ; {@code sortie} : action en la quittant, faux pour rester dessus et réessayer (envoi sans place libre).
     */
    record Pas(String id, Lieu lieu, int lectureS, List<Touche> touches, Predicate<ServerPlayer> utile,
               @Nullable Function<ServerPlayer, int[]> progres, boolean compteur, Function<ServerPlayer, MutableComponent> texte,
               String titreFr, @Nullable Consumer<ServerPlayer> entree, @Nullable Predicate<ServerPlayer> sortie) {
        MutableComponent titre() {
            return T.t("crac.tuto.pas." + this.id + ".titre", this.titreFr);
        }
    }

    static final List<Pas> PARCOURS = new ArrayList<>();

    private static Pas lecture(String id, Lieu lieu, int secondes, String titre, String texte, Touche... touches) {
        return new Pas(id, lieu, secondes, List.of(touches), p -> true, null, false, p -> T.t("crac.tuto.pas." + id + ".texte", texte),
            titre, null, null);
    }

    private static Pas action(String id, String titre, String texte, boolean compteur, Function<ServerPlayer, int[]> progres,
                              Touche... touches) {
        return new Pas(id, Lieu.BASES, 0, List.of(touches), p -> true, progres, compteur, p -> T.t("crac.tuto.pas." + id + ".texte", texte),
            titre, null, null);
    }

    private static Pas avec(Pas p, @Nullable Predicate<ServerPlayer> utile, @Nullable Consumer<ServerPlayer> entree,
                            @Nullable Predicate<ServerPlayer> sortie, @Nullable Function<ServerPlayer, MutableComponent> texte) {
        return new Pas(p.id(), p.lieu(), p.lectureS(), p.touches(), utile != null ? utile : p.utile(), p.progres(), p.compteur(),
            texte != null ? texte : p.texte(), p.titreFr(), entree != null ? entree : p.entree(), sortie != null ? sortie : p.sortie());
    }

    private static final Touche POSER = new Touche("attaque", "crac.tuto.touche.poser");
    private static final Touche ROUE = new Touche("utiliser", "crac.tuto.touche.roue");

    static {
        // ------------------------------------------------------------------ au spawn
        // (la visite du spawn, prévue plus tard, viendra ici, avant « bienvenue »)
        PARCOURS.add(lecture("bienvenue", Lieu.SPAWN, 8, "Bienvenue, survivant",
            "Avant de partir sur la carte, il te faut une base : un abri sûr où revenir."));
        PARCOURS.add(avec(lecture("kit", Lieu.SPAWN, 9, "Ton kit de départ",
                "Plan, marteau, Claim Block et du bois : de quoi bâtir une petite cabane avec une porte.",
                new Touche("inventaire", "crac.tuto.touche.inventaire")),
            null, Tutoriel::donnerKit, null, null));
        PARCOURS.add(avec(lecture("depart", Lieu.SPAWN, 7, "Direction le monde des bases",
                "C'est là que chacun bâtit sa base. Tu vas y être envoyé."),
            null, null, Tutoriel::envoyer, null));

        // ------------------------------------------------------------------ dans le monde des bases
        PARCOURS.add(lecture("coin", Lieu.BASES, 9, "Choisis ton coin",
            "Trouve un terrain plat qui te plaît. Ton contour vert te protège des joueurs jusqu'à ta base posée."));
        PARCOURS.add(action("plan", "Prends le plan", "Mets le Plan de construction dans ta main.", false,
            p -> un(p.getMainHandItem().is(ModItems.BUILDING_PLAN.get()))));
        PARCOURS.add(action("fondation", "Pose une fondation",
            "Vise le sol : l'aperçu bleu montre la pièce, rouge si c'est impossible ici.", false,
            p -> un(fondationsPosees(p) >= 1),
            POSER, new Touche("tourner", "crac.tuto.touche.tourner")));
        PARCOURS.add(action("claim", "Pose le Claim Block",
            "Prends le Claim Block et pose-le sur ta fondation : le terrain autour devient le tien.", false,
            p -> un(Deplacements.baseDe(p) != null),
            new Touche("utiliser", "crac.tuto.touche.poser")));
        PARCOURS.add(action("fondation2", "Agrandis ta base", "Plan en main, pose une 2e fondation collée à la première.", true,
            p -> new int[]{compter(p, k -> k == PieceKind.FOUNDATION || k == PieceKind.TRI_FOUNDATION),
                BasesReglages.i(BasesReglages.FONDATIONS_MIN, 2)},
            POSER));
        PARCOURS.add(avec(lecture("protegee", Lieu.BASES, 9, "Ta base est à toi",
                "Elle est protégée %s jours : personne ne peut la raider. Ton home « base » y est créé."),
            null, null, null, EtapesTuto::texteProtegee));
        PARCOURS.add(action("murs", "Monte les murs",
            "Maintiens clic droit pour ouvrir la roue des pièces et choisis Mur. Laisse un bord pour la porte.", true,
            p -> new int[]{compter(p, k -> k == PieceKind.WALL || k == PieceKind.HALF_WALL || k == PieceKind.WINDOW), 5},
            ROUE, POSER));
        PARCOURS.add(action("porte", "La porte",
            "Dans la roue, choisis Porte et pose-la sur le dernier bord. Le battant est compris.", false,
            p -> un(compter(p, k -> k == PieceKind.DOORWAY || k == PieceKind.DOUBLE_DOORWAY) >= 1),
            ROUE, POSER));
        PARCOURS.add(action("toit", "Le toit", "Choisis Plancher dans la roue et pose 2 planchers en haut de tes murs.", true,
            p -> new int[]{compter(p, k -> k == PieceKind.FLOOR || k == PieceKind.TRI_FLOOR || k == PieceKind.ROOF
                || k == PieceKind.TRI_ROOF), 2},
            ROUE, POSER));
        PARCOURS.add(avec(action("bois", "Renforce en bois",
                "Marteau en main, vise une pièce, maintiens clic droit et choisis Bois. Pour chaque pièce.", true,
                EtapesTuto::enBois, ROUE),
            p -> BasesReglages.b(BasesReglages.AMELIORATION_BOIS, true), null, null, null));
        PARCOURS.add(avec(action("entretien", "Paye l'entretien",
                "Clic droit sur le Claim Block et mets-y des planches : sans entretien, ta base s'abîme.", false,
                p -> un(entretienPaye(p)), new Touche("utiliser", "crac.tuto.touche.ouvrir")),
            p -> BasesReglages.i(BasesReglages.ENTRETIEN_HEURES, 24) > 0, null, null, null));
        PARCOURS.add(lecture("fin", Lieu.BASES, 10, "Ta base est prête",
            "Du spawn, /home te ramène ici ; d'ici, /spawn te ramène au spawn. /group pour inviter des amis.",
            new Touche("/home", ""), new Touche("/spawn", "")));
    }

    private EtapesTuto() {
    }

    static int index(String id) {
        for (int i = 0; i < PARCOURS.size(); i++) {
            if (PARCOURS.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------- constats

    private static int[] un(boolean fait) {
        return new int[]{fait ? 1 : 0, 0};
    }

    /** Le bâtiment de la base du joueur (sous le Claim Block de son groupe, dans le monde des bases), ou null. */
    @Nullable
    private static Building base(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        Claim c = Deplacements.baseDe(p);
        return bases == null || c == null ? null : StructureService.buildingOfClaim(bases, c.anchor());
    }

    private static List<Piece> pieces(ServerPlayer p) {
        Building b = base(p);
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        return b == null || bases == null ? List.of() : StructureService.store(bases).piecesOf(b);
    }

    private static int compter(ServerPlayer p, Predicate<PieceKind> genre) {
        int n = 0;
        for (Piece piece : pieces(p)) {
            if (genre.test(piece.kind())) {
                n++;
            }
        }
        return n;
    }

    /** Fondations de la base ; sans Claim Block encore : celles qu'il a posées lui-même à moins de 48 blocs. */
    private static int fondationsPosees(ServerPlayer p) {
        int n = compter(p, k -> k == PieceKind.FOUNDATION || k == PieceKind.TRI_FOUNDATION);
        if (n > 0 || !MondeBases.estBases(p.level())) {
            return n;
        }
        ServerLevel bases = p.serverLevel();
        for (Piece piece : StructureService.store(bases).index.near(null, new AABB(p.blockPosition()).inflate(48, 24, 48))) {
            if (piece.kind().isFoundation() && p.getUUID().equals(piece.owner)) {
                n++;
            }
        }
        return n;
    }

    /** Pièces en bois ou mieux, sur au plus 10 (la cabane du kit) ou toutes celles de la base si elle en a moins. */
    private static int[] enBois(ServerPlayer p) {
        List<Piece> l = pieces(p);
        int n = 0;
        for (Piece piece : l) {
            if (piece.tier.ordinal() >= Tier.WOOD.ordinal()) {
                n++;
            }
        }
        return new int[]{n, Math.max(1, Math.min(10, l.size()))};
    }

    private static boolean entretienPaye(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        Claim c = Deplacements.baseDe(p);
        return bases != null && c != null && bases.isLoaded(c.anchor())
            && bases.getBlockEntity(c.anchor()) instanceof ClaimBlockEntity be && !be.isStockEmpty();
    }

    private static MutableComponent texteProtegee(ServerPlayer p) {
        Group g = GroupData.get(p.server).getByMember(p.getUUID());
        if (g != null && Protections.groupeProtege(p.server, g.id())) {
            double jours = BasesReglages.d(BasesReglages.PROTECTION_JOURS, 4.0);
            String nb = jours == Math.rint(jours) ? String.valueOf((long) jours) : String.valueOf(jours);
            return T.t("crac.tuto.pas.protegee.texte", "Elle est protégée %s jours : personne ne peut la raider. Ton home « base » y est créé.", nb);
        }
        return T.t("crac.tuto.pas.protegee.texte_sans", "Ton home « base » y est créé : du spawn, /home t'y ramène.");
    }
}
