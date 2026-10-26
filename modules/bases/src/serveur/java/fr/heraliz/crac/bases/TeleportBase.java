package fr.heraliz.crac.bases;

import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Téléportation vers la base de son clan (25/10, demande du propriétaire) : UN seul système pour {@code /home} et pour
 * la réapparition à la base après une mort, donc un seul délai, partagé :
 * <ul>
 *   <li><b>délai</b> (26/10) : la permission « Recharge du /home » du grade (éditeur des grades : 8 min sans grade,
 *   Rescapé 7 min 30, Traqueur 7 min, Roi de la Zone 6 min 30 ; {@code [home] delai_s} de {@code crac-bases.toml} sans
 *   HeraliZ Grades) entre deux téléportations vers la base, quelle que soit la façon. L'arbre du clan (branche « tp » : 8,
 *   7, 6, 5 min) et le grade ne s'ajoutent pas : le plus court des deux. Durée fixée au départ et gardée avec le monde
 *   ({@code BasesDonnees}, {@code finHome} / {@code dureeHome}) : ni la mort, ni une déconnexion, ni un redémarrage, ni un
 *   changement de grade en cours de recharge ne la changent ; indépendante de la recharge du /tpa ;</li>
 *   <li><b>point d'arrivée</b> : juste devant l'armoire à outils de la base (côté de sa porte), sinon la place libre la
 *   plus proche autour d'elle ; jamais dans un bloc ni sur du vide ({@link Points}). Le joueur n'a rien à régler : la
 *   base change, l'armoire est déplacée, le point suit ;</li>
 *   <li><b>droits</b> : membre du clan avec le droit « Se téléporter à la base » (HeraliZ Clans, réglable par grade et
 *   membre par membre ; le chef l'a toujours).</li>
 * </ul>
 * Tout est vérifié ici, côté serveur, à chaque demande et encore au départ. Le staff (permission 2) n'a pas de délai.
 */
public final class TeleportBase {
    /** Raisons d'un refus (envoyées au joueur pour l'écran de réapparition). */
    public static final int OK = 0, SANS_BASE = 1, SANS_DROIT = 2, MATCH = 3, ATTENTE = 4, CARTE = 5, TUTO = 6;

    private TeleportBase() {
    }

    /** Délai partagé entre deux téléportations vers la base (ms), calculé maintenant (grade, arbre du clan). */
    static long delaiMs(ServerPlayer p) {
        long defaut = BasesReglages.i(BasesReglages.HOME_DELAI_S, 480) * 1000L;
        int grade = Homes.permGrade(p.getUUID(), "home_recharge");
        long parGrade = grade >= 0 ? grade * 1000L : defaut;
        // pas de cumul (26/10) : le plus court du grade et de l'arbre du clan (qui part du délai de crac-bases.toml)
        return Math.min(parGrade, fr.heraliz.crac.heraliz.ArbreBases.delaiHomeMs(p.getUUID(), defaut));
    }

    /** Temps avant la prochaine téléportation vers la base (0 : possible maintenant). */
    public static long resteMs(ServerPlayer p) {
        if (Deplacements.staff(p)) {
            return 0;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        long maintenant = System.currentTimeMillis();
        if (j.finHome > 0) {
            // jamais plus que la durée posée au départ (horloge du serveur reculée)
            return Math.max(0, Math.min(j.finHome - maintenant, j.dureeHome));
        }
        if (j.dernierHome <= 0) {
            return 0;
        }
        long d = delaiMs(p);   // téléportation d'avant le 26/10 : ancienne règle
        return Math.max(0, Math.min(j.dernierHome + d - maintenant, d));
    }

    private static void marquer(ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        long maintenant = System.currentTimeMillis(), duree = delaiMs(p);
        j.dernierHome = maintenant;
        j.finHome = maintenant + duree;
        j.dureeHome = duree;
        d.marquer();
    }

    /** La base (claim) du clan du joueur sur la carte, ou null. */
    @Nullable
    static Claim base(ServerPlayer p) {
        return Deplacements.baseDe(p);
    }

    /** Raison qui empêche la téléportation maintenant ({@link #OK} si elle est possible). */
    public static int raison(ServerPlayer p) {
        if (p.getPersistentData().getBoolean(Deplacements.EN_MATCH)) {
            return MATCH;
        }
        // pendant le tutoriel des bases (cabane d'essai, tour de caméra de la fin) : jamais vers la vraie base, il en
        // sortirait coincé sur la carte avec le kit du tutoriel
        if (Tutoriel.occupe(p.server, p.getUUID())) {
            return TUTO;
        }
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return CARTE;
        }
        Claim c = base(p);
        if (c == null) {
            return SANS_BASE;
        }
        Group g = GroupData.get(p.server).getById(c.groupId());
        if (!Deplacements.staff(p) && (g == null || !ClanAccess.allows(g, p.getUUID(), ClanAccess.Action.TELEPORT))) {
            return SANS_DROIT;
        }
        return resteMs(p) > 0 ? ATTENTE : OK;
    }

    /** Texte d'un refus, pour le chat. */
    static MutableComponent texte(ServerPlayer p, int raison) {
        return switch (raison) {
            case MATCH -> T.t("crac.cmd.match", "Impossible pendant un match.");
            case TUTO -> T.t("crac.tpbase.tuto", "Pas pendant le tutoriel des bases : finis d'abord ta cabane d'essai.");
            case CARTE -> T.t("crac.bases.absent", "La carte n'est pas chargée sur ce serveur : préviens le staff.");
            case SANS_BASE -> T.t("crac.tpbase.sans_base", "Ton clan n'a pas de base (armoire à outils posée sur la carte).");
            case SANS_DROIT -> T.t("crac.tpbase.sans_droit", "Ton clan ne t'a pas donné le droit « Se téléporter à la base ».");
            case ATTENTE -> T.t("crac.tpbase.attendre", "Prochaine téléportation vers ta base dans %s (/home et réapparition partagent ce délai).",
                T.duree(resteMs(p) + 999));
            default -> T.t("crac.tpbase.impossible", "Téléportation vers ta base impossible.");
        };
    }

    /** Point d'arrivée devant l'armoire de la base (place sûre), et le regard tourné vers elle ; null si rien n'est libre. */
    @Nullable
    private static Arrivee arrivee(ServerPlayer p, ServerLevel l, Claim c) {
        BlockPos tc = c.anchor();
        // tronçons de l'armoire ET de sa place devant / autour (4 blocs) : une armoire au bord d'un tronçon avait sa
        // place devant dans un tronçon pas chargé, refusée comme « bouchée » ou remplacée par une place derrière le mur
        for (int cx = (tc.getX() - 4) >> 4; cx <= (tc.getX() + 4) >> 4; cx++) {
            for (int cz = (tc.getZ() - 4) >> 4; cz <= (tc.getZ() + 4) >> 4; cz++) {
                l.getChunk(cx, cz);
            }
        }
        BlockState s = l.getBlockState(tc);
        Direction face = s.hasProperty(ClaimBlock.FACING) ? s.getValue(ClaimBlock.FACING) : Direction.NORTH;
        BlockPos devant = tc.relative(face);
        float lacet = face.getOpposite().toYRot();
        if (Points.libre(l, p, devant)) {
            return new Arrivee(Vec3.atBottomCenterOf(devant), lacet);
        }
        Vec3 pres = Points.presDe(l, p, tc);
        return pres == null ? null : new Arrivee(pres, lacet);
    }

    private record Arrivee(Vec3 pos, float lacet) {
    }

    /**
     * Téléporte le joueur devant l'armoire de sa base et démarre le délai partagé ; revérifie tout. Rend la raison du
     * refus ({@link #OK} si c'est fait).
     */
    public static int teleporter(ServerPlayer p) {
        int r = raison(p);
        if (r != OK) {
            return r;
        }
        ServerLevel l = p.server.getLevel(MondeBases.BASES);
        Claim c = base(p);
        if (l == null || c == null) {
            return SANS_BASE;
        }
        Arrivee a = arrivee(p, l, c);
        if (a == null) {
            return -1;
        }
        p.stopRiding();
        p.teleportTo(l, a.pos().x, a.pos().y, a.pos().z, a.lacet(), 0f);
        p.resetFallDistance();
        marquer(p);
        return OK;
    }

    /** Pour l'écran de réapparition : la base existe et le joueur y a droit (peut-être plus tard, à cause du délai). */
    public static boolean possibleUnJour(int raison) {
        return raison == OK || raison == ATTENTE;
    }
}
