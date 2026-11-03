package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.lang.reflect.Method;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /home} (25/10, demande du propriétaire) : du spawn vers la base de son clan. Mêmes règles que la réapparition à
 * la base, avec le même délai partagé (8 min) : seulement depuis le spawn ({@link Deplacements#auSpawn}), hors combat, à
 * terre et match ({@link Deplacements#refuse}), avec le droit de clan « Se téléporter à la base », après
 * {@code [home] immobile_s} secondes sans bouger. Le staff (permission 2) part de partout, sans délai.
 * <p>HeraliZ (03/11, demande du propriétaire) : plus aucun home créé tout seul à la pose de l'armoire (il pouvait tomber
 * dehors, derrière des coffres). Un membre qui a le droit « Gérer la base » pose le home de la base lui-même, dedans, à
 * un endroit sûr, debout sur une fondation de la base, avec {@link #COMMANDE_POSER} ; {@code /home} et la réapparition à la base y mènent ({@link TeleportBase}).
 * Une base posée avant garde son home créé tout seul, devant l'armoire, tant qu'on n'en pose pas un.
 * <p>Avant le 25/10 : homes posés par {@code /home poser} (gardés dans {@code data/crac_bases.dat}, plus lus).
 */
public final class Homes {
    /**
     * Nom de la commande qui pose le home de la base (03/11, choix du propriétaire : {@code /sethome <nom>}). À changer ici
     * seulement : les textes le reçoivent en {@code %s} (fr_fr, en_us, ru_ru), suivi de « <nom> » écrit dans chaque langue.
     * La carte du noyau doit permettre la commande ({@code commandes_permises} de {@code heraliz_noyau-server.toml}).
     */
    public static final String COMMANDE_POSER = "sethome";
    /** Longueur maximale du nom d'un home. */
    private static final int NOM_MAX = 24;

    @Nullable
    private static Method permGrade;
    private static boolean permCherche;

    private Homes() {
    }

    /** La commande qui pose le home, telle que le joueur la tape ({@code /sethome}), pour les textes. */
    static String commande() {
        return "/" + COMMANDE_POSER;
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("home").executes(c -> home(c.getSource().getPlayerOrException())));
        d.register(Commands.literal(COMMANDE_POSER)
            .executes(c -> poser(c.getSource().getPlayerOrException(), ""))
            .then(Commands.argument("nom", StringArgumentType.greedyString())
                .executes(c -> poser(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom")))));
    }

    /**
     * Permission de grade (HeraliZ Grades 2.0.0, {@code GradesApi.perm}, réglée dans l'éditeur des grades) ; -1 sans le
     * mod ou avec une version plus ancienne.
     */
    static int permGrade(UUID joueur, String cle) {
        if (!permCherche) {
            permCherche = true;
            try {
                permGrade = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("perm", UUID.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                permGrade = null;
            }
        }
        if (permGrade == null) {
            return -1;
        }
        try {
            Object r = permGrade.invoke(null, joueur, cle);
            return r instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    static int home(ServerPlayer p) {
        if (Deplacements.refuse(p)) {
            return 0;
        }
        if (!Deplacements.staff(p) && !Deplacements.auSpawn(p)) {
            T.chat(p, T.t("crac.home.depuis_spawn", "/home ne marche que depuis le spawn."), ChatFormatting.RED);
            return 0;
        }
        int r = TeleportBase.raison(p);
        if (r != TeleportBase.OK) {
            T.chat(p, TeleportBase.texte(p, r), ChatFormatting.RED);
            return 0;
        }
        // 01/11 : le home (ou l'armoire) et ses alentours chargés en tâche de fond pendant le compte à rebours (avant : au
        // départ, sur le fil du serveur, d'où un gel du serveur quand personne n'était près de la base) ; départ quand ils
        // sont prêts
        TeleportBase.precharger(p, 2);
        Deplacements.attendre(p, "home", () -> partir(p), () -> TeleportBase.arriveeChargee(p));
        return 1;
    }

    private static void partir(ServerPlayer p) {
        Claim c = TeleportBase.base(p);
        int r = TeleportBase.teleporter(p);
        boolean pose = c != null && c.home() != null;
        if (r == TeleportBase.OK) {
            Deplacements.apres(p, (pose ? T.t("crac.home.arrive_home", "Bienvenue chez toi : te voici au home « %s » de ta base.", c.homeNom())
                : T.t("crac.home.arrive", "Bienvenue chez toi : te voici devant l'armoire de ta base.")).withStyle(ChatFormatting.GREEN));
        } else if (r < 0 && pose) {
            T.chat(p, T.t("crac.home.obstrue_home",
                "Le home « %s » de ta base est bouché, n'est plus sûr ou sa fondation a disparu : dégage-le, ou pose-en un autre avec %s <nom>.", c.homeNom(),
                commande()), ChatFormatting.RED);
        } else if (r < 0) {
            T.chat(p, T.t("crac.home.obstrue_tc", "La place devant l'armoire de ta base est bouchée : dégage-la (ou préviens ton clan)."),
                ChatFormatting.RED);
        } else {
            T.chat(p, TeleportBase.texte(p, r), ChatFormatting.RED);
        }
    }

    /**
     * HeraliZ (03/11) : {@link #COMMANDE_POSER} [nom] : le home de la base du clan, aux pieds du joueur. Seulement dans la
     * zone de l'armoire de sa base (carte), par un membre qui a le droit « Gérer la base » (le staff aussi), à un endroit
     * sûr (debout sur un sol, aucun bloc ni liquide dans le corps : {@link Points#homeSur}), hors combat, à terre, match et
     * tutoriel. Remplace le home d'avant (un seul par base) ; sans nom, celui du clan.
     */
    static int poser(ServerPlayer p, String nomTape) {
        if (Deplacements.refuse(p)) {
            return 0;
        }
        if (Tutoriel.occupe(p.server, p.getUUID())) {
            T.chat(p, T.t("crac.tpbase.tuto", "Pas pendant le tutoriel des bases : finis d'abord ta cabane d'essai."), ChatFormatting.RED);
            return 0;
        }
        ServerLevel l = p.serverLevel();
        if (!MondeBases.estBases(l)) {
            T.chat(p, T.t("crac.sethome.pas_carte", "Le home se pose dans la base de ton clan, sur la carte."), ChatFormatting.RED);
            return 0;
        }
        Claim c = Deplacements.baseDe(p);
        if (c == null) {
            T.chat(p, TeleportBase.texte(p, TeleportBase.SANS_BASE), ChatFormatting.RED);
            return 0;
        }
        Group g = GroupData.get(p.server).getById(c.groupId());
        if (!Deplacements.staff(p) && (g == null || !ClanAccess.allows(g, p.getUUID(), ClanAccess.Action.MANAGE))) {
            T.chat(p, T.t("crac.sethome.droit", "Seuls les membres qui ont le droit « Gérer la base » posent le home de la base."),
                ChatFormatting.RED);
            return 0;
        }
        Vec3 pieds = p.position();
        if (ClaimManager.getAt(l, BlockPos.containing(pieds)) != c) {
            T.chat(p, T.t("crac.sethome.hors_base", "Mets-toi dans ta base (dans la zone de son armoire) pour poser son home."),
                ChatFormatting.RED);
            return 0;
        }
        // 03/11 (choix du propriétaire) : seulement debout sur une fondation de la base, jamais sur le sol, un plancher, un
        // toit ou un autre bloc
        if (!surFondation(l, c, pieds)) {
            T.chat(p, T.t("crac.sethome.fondation",
                "Le home se pose seulement debout sur une fondation de ta base (pas sur le sol, un plancher, un toit ou un autre bloc)."),
                ChatFormatting.RED);
            return 0;
        }
        if (!p.onGround() || p.isPassenger() || p.isFallFlying() || p.isSwimming() || !Points.homeSur(l, p, pieds)) {
            T.chat(p, T.t("crac.sethome.pas_sur", "Endroit pas sûr pour le home : tiens-toi debout sur un sol, sans bloc ni liquide autour de toi."),
                ChatFormatting.RED);
            return 0;
        }
        String nom = nettoyer(nomTape);
        if (nom.isEmpty()) {
            nom = g != null ? nettoyer(g.name()) : "";
        }
        c.setHome(pieds, p.getYRot(), nom);
        ClaimData.get(l).markDirty();
        Crac.LOGGER.info("[CRac] home « {} » de la base du groupe {} posé par {} en {}", nom, c.groupId(), p.getGameProfile().getName(),
            BlockPos.containing(pieds).toShortString());
        T.chat(p, T.t("crac.sethome.ok", "Home « %s » posé ici : du spawn, /home t'y ramène (la réapparition à la base aussi).", nom),
            ChatFormatting.GREEN);
        if (g != null) {
            for (UUID m : g.members()) {
                ServerPlayer autre = m.equals(p.getUUID()) ? null : p.server.getPlayerList().getPlayer(m);
                if (autre != null) {
                    T.chat(autre, T.t("crac.sethome.autres", "%s a posé le home « %s » de ta base.", p.getGameProfile().getName(), nom),
                        ChatFormatting.GRAY);
                }
            }
        }
        return 1;
    }

    /**
     * HeraliZ (03/11, choix du propriétaire) : les pieds {@code pieds} reposent sur une fondation (pièce de construction de
     * CRac de type fondation) du bâtiment de l'armoire de la base {@code c} : le bloc juste sous les pieds en contient une.
     * Revu aussi au départ de /home et de la réapparition : fondation démolie ou détruite = home refusé.
     */
    static boolean surFondation(ServerLevel l, Claim c, Vec3 pieds) {
        BlockPos dessous = BlockPos.containing(pieds.x, pieds.y - 0.01, pieds.z);
        if (!l.isLoaded(dessous)) {
            return false;
        }
        for (Piece piece : StructureStore.get(l).index.occupants(dessous)) {
            if (piece.kind().isFoundation() && StructureService.claimOf(l, StructureService.buildingOf(l, piece)) == c) {
                return true;
            }
        }
        return false;
    }

    /** Nom d'un home : sans codes de couleur ni caractères de contrôle, {@value #NOM_MAX} caractères au plus. */
    private static String nettoyer(@Nullable String brut) {
        if (brut == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < brut.length() && sb.length() < NOM_MAX; i++) {
            char ch = brut.charAt(i);
            if (ch == '§') {
                i++;   // code de couleur et sa lettre
            } else if (!Character.isISOControl(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString().trim();
    }

    /**
     * HeraliZ (03/11, demande du propriétaire) : l'armoire de la base vient d'être posée : plus de home créé tout seul, le
     * joueur choisit lui-même où le poser ({@link #COMMANDE_POSER}). Rien dans le monde du tutoriel (cabane d'essai).
     */
    public static void armoirePosee(ServerPlayer p, ServerLevel level) {
        if (MondeBases.estTuto(level)) {
            return;
        }
        T.chat(p, T.t("crac.claim.poser_home",
            "Choisis maintenant le home de ta base : mets-toi dedans, debout sur une fondation, et tape %s <nom>. Sans home, ni /home ni réapparition à la base.",
            commande()), ChatFormatting.GOLD);
    }
}
