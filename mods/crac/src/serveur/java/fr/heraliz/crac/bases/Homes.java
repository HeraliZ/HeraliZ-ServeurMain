package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Les homes des joueurs dans le monde des bases :
 * <ul>
 *   <li>{@code /sethome [nom]} (« base » par défaut) : seulement dans le monde des bases, dans SA base (zone du claim de
 *   son groupe CRac, ou de son clan avec le droit d'entrer) ; le même nom déplace le home.</li>
 *   <li>{@code /delhome <nom>}, {@code /homes} (liste).</li>
 *   <li>{@code /home [nom]} (sans nom : le premier) : seulement depuis le spawn ({@link Deplacements#auSpawn}), une fois
 *   toutes les {@code [home] delai_s} secondes, après {@code [home] immobile_s} secondes sans bouger. Sans home : un
 *   message, aucune téléportation (la zone de construction, c'est {@code /zone base}). Un home qui n'est plus dans sa
 *   base (base détruite, claim perdu, groupe quitté) est supprimé, sans téléportation.</li>
 * </ul>
 * Nombre de homes : {@code [home] homes_base} (1) sans grade de la boutique, plus {@code homes_par_grade} (1) par rang
 * (Rescapé 2, Traqueur 3, Roi de la Zone 4), lu par réflexion dans HeraliZ Grades
 * ({@code fr.heraliz.grades.serveur.GradesApi.rangBoutique(UUID)} ; sans le mod : aucun grade). Le staff (permission 2)
 * n'a ni limite, ni règle de lieu, ni délai. Le home « base » est créé tout seul à la pose de sa première base (Claim
 * Block), s'il n'en a aucun ; un joueur qui avait déjà une base avant les homes le reçoit à son premier /home.
 * Gardés dans {@code data/crac_bases.dat} ({@link BasesDonnees}).
 */
public final class Homes {
    public static final String DEFAUT = "base";
    private static final Pattern NOM = Pattern.compile("[a-z0-9_-]{1,16}");

    @Nullable
    private static Method rangBoutique;
    private static boolean gradesCherche;

    private Homes() {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> noms = (c, b) -> SharedSuggestionProvider.suggest(noms(c.getSource()), b);
        d.register(Commands.literal("home").executes(c -> home(c.getSource().getPlayerOrException(), null))
            .then(Commands.argument("nom", StringArgumentType.word()).suggests(noms)
                .executes(c -> home(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom")))));
        d.register(Commands.literal("sethome").executes(c -> sethome(c.getSource().getPlayerOrException(), null))
            .then(Commands.argument("nom", StringArgumentType.word()).suggests(noms)
                .executes(c -> sethome(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom")))));
        d.register(Commands.literal("delhome")
            .then(Commands.argument("nom", StringArgumentType.word()).suggests(noms)
                .executes(c -> delhome(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom")))));
        d.register(Commands.literal("homes").executes(c -> homes(c.getSource().getPlayerOrException())));
    }

    private static List<String> noms(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            return List.of();
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).siConnu(p.getUUID());
        return j == null ? List.of() : new ArrayList<>(j.homes.keySet());
    }

    @Nullable
    private static String nom(@Nullable String brut) {
        String n = brut == null ? DEFAUT : brut.toLowerCase(Locale.ROOT);
        return NOM.matcher(n).matches() ? n : null;
    }

    // ---------------------------------------------------------------------------------------------- limite

    /** Homes permis au joueur : 1 + 1 par rang de grade de la boutique ; le staff n'a pas de limite. */
    static int limite(ServerPlayer p) {
        if (Deplacements.staff(p)) {
            return Integer.MAX_VALUE;
        }
        int base = BasesReglages.i(BasesReglages.HOMES_BASE, 1);
        int parGrade = BasesReglages.i(BasesReglages.HOMES_PAR_GRADE, 1);
        return base + parGrade * rang(p.getUUID());
    }

    private static String texteLimite(ServerPlayer p) {
        int l = limite(p);
        return l == Integer.MAX_VALUE ? "∞" : String.valueOf(l);
    }

    /** Rang du grade de la boutique (0 à 3) lu dans HeraliZ Grades ; 0 sans le mod. */
    private static int rang(UUID joueur) {
        if (!gradesCherche) {
            gradesCherche = true;
            try {
                rangBoutique = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("rangBoutique", UUID.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                Crac.LOGGER.info("[CRac] HeraliZ Grades absent : un seul home par joueur ({})", e.toString());
            }
        }
        if (rangBoutique == null) {
            return 0;
        }
        try {
            Object r = rangBoutique.invoke(null, joueur);
            return r instanceof Integer i ? Math.max(0, Math.min(3, i)) : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Crac.LOGGER.warn("[CRac] lecture du grade de {} impossible ({}) : aucun grade compté", joueur, e.toString());
            return 0;
        }
    }

    // ---------------------------------------------------------------------------------------------- /sethome

    static int sethome(ServerPlayer p, @Nullable String brut) {
        String n = nom(brut);
        if (n == null) {
            T.chat(p, T.t("crac.home.nom_invalide", "Nom de home invalide : lettres, chiffres, _ ou -, 16 au plus."), ChatFormatting.RED);
            return 0;
        }
        boolean staff = Deplacements.staff(p);
        if (!staff) {
            if (!MondeBases.estBases(p.level())) {
                T.chat(p, T.t("crac.home.sethome_monde", "/sethome ne marche que dans le monde des bases, dans ta base."), ChatFormatting.RED);
                return 0;
            }
            if (!Deplacements.dansSaBase(p)) {
                T.chat(p, T.t("crac.home.sethome_base", "Tu n'es pas dans ta base (zone du Claim Block de ton groupe) : /sethome refusé."),
                    ChatFormatting.RED);
                return 0;
            }
        }
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        boolean nouveau = !j.homes.containsKey(n);
        if (nouveau && j.homes.size() >= limite(p)) {
            T.chat(p, T.t("crac.home.limite",
                "Tu as déjà %s home(s) sur %s : /delhome <nom> pour en libérer un. Chaque grade de la boutique en donne un de plus.",
                String.valueOf(j.homes.size()), texteLimite(p)), ChatFormatting.RED);
            return 0;
        }
        j.homes.put(n, ici(p));
        j.homeAuto = true;
        d.marquer();
        if (nouveau) {
            T.chat(p, T.t("crac.home.enregistre", "Home « %s » enregistré ici (%s/%s). Du spawn, /home %s t'y ramène.",
                n, String.valueOf(j.homes.size()), texteLimite(p), n), ChatFormatting.GREEN);
        } else {
            T.chat(p, T.t("crac.home.deplace", "Home « %s » déplacé ici.", n), ChatFormatting.GREEN);
        }
        return 1;
    }

    private static BasesDonnees.Home ici(ServerPlayer p) {
        return new BasesDonnees.Home(p.level().dimension().location(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
    }

    // ---------------------------------------------------------------------------------------------- /delhome, /homes

    static int delhome(ServerPlayer p, String brut) {
        String n = nom(brut);
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        if (n == null || j.homes.remove(n) == null) {
            T.chat(p, T.t("crac.home.inconnu", "Tu n'as pas de home « %s ». Tes homes : %s.", brut, liste(j)), ChatFormatting.RED);
            return 0;
        }
        d.marquer();
        T.chat(p, T.t("crac.home.supprime", "Home « %s » supprimé (%s/%s).", n, String.valueOf(j.homes.size()), texteLimite(p)),
            ChatFormatting.YELLOW);
        return 1;
    }

    static int homes(ServerPlayer p) {
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        migrer(p, j);
        if (j.homes.isEmpty()) {
            T.chat(p, aucun(), ChatFormatting.YELLOW);
            return 0;
        }
        T.chat(p, T.t("crac.home.liste", "Tes homes (%s/%s) : %s.", String.valueOf(j.homes.size()), texteLimite(p), liste(j)),
            ChatFormatting.GREEN);
        return 1;
    }

    private static String liste(BasesDonnees.Joueur j) {
        return j.homes.isEmpty() ? "-" : String.join(", ", j.homes.keySet());
    }

    private static net.minecraft.network.chat.MutableComponent aucun() {
        return T.t("crac.home.aucun", "Tu n'as pas de home. Fais /sethome dans ta base, ou /zone base pour aller dans la zone de construction.");
    }

    // ---------------------------------------------------------------------------------------------- /home

    static int home(ServerPlayer p, @Nullable String brut) {
        boolean staff = Deplacements.staff(p);
        if (!staff && !Deplacements.auSpawn(p)) {
            T.chat(p, T.t("crac.home.depuis_spawn", "/home ne marche que depuis le spawn. Retourne au spawn avec /spawn depuis ta base."),
                ChatFormatting.RED);
            return 0;
        }
        if (p.server.getLevel(MondeBases.BASES) == null) {
            T.chat(p, T.t("crac.bases.absent", "Le monde des bases n'existe pas sur ce serveur (crac:bases)."), ChatFormatting.RED);
            return 0;
        }
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        migrer(p, j);
        if (j.homes.isEmpty()) {
            T.chat(p, aucun(), ChatFormatting.YELLOW);
            return 0;
        }
        String n;
        if (brut == null) {
            n = j.homes.keySet().iterator().next();
        } else {
            n = nom(brut);
            if (n == null || !j.homes.containsKey(n)) {
                T.chat(p, T.t("crac.home.inconnu", "Tu n'as pas de home « %s ». Tes homes : %s.", brut, liste(j)), ChatFormatting.RED);
                return 0;
            }
        }
        long delai = BasesReglages.i(BasesReglages.HOME_DELAI_S, 180) * 1000L;
        long reste = j.dernierHome + delai - System.currentTimeMillis();
        if (!staff && reste > 0) {
            T.chat(p, T.t("crac.home.attendre", "Prochain /home dans %s.", T.duree(reste + 999)), ChatFormatting.RED);
            return 0;
        }
        if (!verifier(p, d, j, n)) {
            return 0;
        }
        final String choisi = n;
        Deplacements.attendre(p, "home", () -> partir(p, choisi));
        return 1;
    }

    /** Le home est-il toujours valable ? Sinon il est supprimé (message) et rend false. */
    private static boolean verifier(ServerPlayer p, BasesDonnees d, BasesDonnees.Joueur j, String n) {
        BasesDonnees.Home h = j.homes.get(n);
        if (h == null) {
            T.chat(p, T.t("crac.home.inconnu", "Tu n'as pas de home « %s ». Tes homes : %s.", n, liste(j)), ChatFormatting.RED);
            return false;
        }
        ServerLevel l = niveau(p, h);
        boolean ok = l != null && (Deplacements.staff(p)
            || MondeBases.estBases(l) && Deplacements.baseEn(l, BlockPos.containing(h.x(), h.y(), h.z()), p.getUUID()));
        if (!ok) {
            j.homes.remove(n);
            d.marquer();
            T.chat(p, T.t("crac.home.perdu", "Ton home « %s » n'est plus dans ta base (base détruite, claim perdu ou groupe quitté) : il est supprimé.",
                n), ChatFormatting.RED);
            if (j.homes.isEmpty()) {
                T.chat(p, aucun(), ChatFormatting.YELLOW);
            }
        }
        return ok;
    }

    @Nullable
    private static ServerLevel niveau(ServerPlayer p, BasesDonnees.Home h) {
        return p.server.getLevel(ResourceKey.create(Registries.DIMENSION, h.monde()));
    }

    private static void partir(ServerPlayer p, String n) {
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        if (!verifier(p, d, j, n)) {
            return;
        }
        BasesDonnees.Home h = j.homes.get(n);
        ServerLevel l = niveau(p, h);
        if (l == null) {
            return;
        }
        BlockPos pieds = BlockPos.containing(h.x(), h.y(), h.z());
        l.getChunk(pieds.getX() >> 4, pieds.getZ() >> 4);
        Vec3 dest;
        float lacet = h.lacet();
        float tangage = h.tangage();
        if (Points.libre(l, p, pieds)) {
            dest = new Vec3(h.x(), h.y(), h.z());
        } else {
            dest = Points.presDe(l, p, pieds);
            tangage = 0f;
        }
        if (dest == null) {
            T.chat(p, T.t("crac.home.obstrue", "Ton home « %s » est bouché : libère la place, ou refais /sethome %s ailleurs dans ta base.", n, n),
                ChatFormatting.RED);
            return;
        }
        p.stopRiding();
        p.teleportTo(l, dest.x, dest.y, dest.z, lacet, tangage);
        p.resetFallDistance();
        j.dernierHome = System.currentTimeMillis();
        d.marquer();
        Deplacements.apres(p, T.t("crac.home.arrive_nom", "Bienvenue chez toi (home « %s »).", n).withStyle(ChatFormatting.GREEN));
    }

    // ---------------------------------------------------------------------------------------------- home automatique

    /**
     * Appelé quand un joueur vient de poser un Claim Block (base créée) : dans le monde des bases, s'il n'a encore aucun
     * home, son home « base » est posé là où il se tient (dans la zone de la base), sinon juste au-dessus du Claim Block.
     */
    public static void premiereBase(ServerPlayer p, ServerLevel level, BlockPos ancre) {
        if (!MondeBases.estBases(level)) {
            return;
        }
        try {
            BasesDonnees d = BasesDonnees.get(p.server);
            BasesDonnees.Joueur j = d.joueur(p.getUUID());
            if (!j.homes.isEmpty()) {
                return;
            }
            BasesDonnees.Home h = p.level() == level && Deplacements.baseEn(level, p.blockPosition(), p.getUUID())
                ? ici(p)
                : new BasesDonnees.Home(MondeBases.ID, ancre.getX() + 0.5, ancre.getY() + 1.0, ancre.getZ() + 0.5, p.getYRot(), 0f);
            j.homes.put(DEFAUT, h);
            j.homeAuto = true;
            d.marquer();
            T.chat(p, T.t("crac.home.auto", "Home « %s » créé dans ta base : du spawn, /home t'y ramène (/sethome pour le déplacer).", DEFAUT),
                ChatFormatting.GREEN);
        } catch (RuntimeException e) {
            Crac.LOGGER.error("[CRac] home automatique de {} en erreur", p.getGameProfile().getName(), e);
        }
    }

    /**
     * Un joueur qui avait déjà une base avant les homes (CRac 1.5) et n'en a aucun : son home « base » est posé une fois,
     * au-dessus de son Claim Block (à l'arrivée, la place libre la plus proche).
     */
    private static void migrer(ServerPlayer p, BasesDonnees.Joueur j) {
        if (j.homeAuto || !j.homes.isEmpty()) {
            return;
        }
        Claim c = Deplacements.baseDe(p);
        if (c == null) {
            return;
        }
        BlockPos a = c.anchor();
        j.homes.put(DEFAUT, new BasesDonnees.Home(MondeBases.ID, a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5, p.getYRot(), 0f));
        j.homeAuto = true;
        BasesDonnees.get(p.server).marquer();
        T.chat(p, T.t("crac.home.auto", "Home « %s » créé dans ta base : du spawn, /home t'y ramène (/sethome pour le déplacer).", DEFAUT),
            ChatFormatting.GREEN);
    }
}
