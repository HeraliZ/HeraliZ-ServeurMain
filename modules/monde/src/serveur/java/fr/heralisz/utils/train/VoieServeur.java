package fr.heralisz.utils.train;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * /train voie (op 2, comme tout /train) : la voie dessinée, vue et retouchée en jeu par le staff. Rien ne touche au
 * tracé du train (l'horaire, les voitures et les heurts lisent toujours Reseau) :
 * <ul>
 * <li>info : le point de la voie visé (à moins de 3 blocs de ce que l'on vise) : indice, abscisse, genre, coordonnées,
 *     et l'étendue du même genre autour ;</li>
 * <li>genre &lt;p|r|t|T|P|x&gt; [longueur] : dessine ce genre à partir du point visé (ex. x -> p : plus de planches
 *     de passage à niveau) ; retirer [longueur] : n'y dessine plus rien ; annuler, liste, effacer ;</li>
 * <li>remplir [ponts|tout] : pose la voie pleine (Train.VOIE_PLEINE, collision invisible) sous la voie dessinée, du
 *     tablier jusqu'au dessus du ballast (et des rails), SEULEMENT dans l'air ; vider [ponts|tout] : retire ces blocs, et
 *     rien d'autre. Seulement dans les chunks chargés (les autres sont comptés et signalés).</li>
 * </ul>
 * Les retouches sont gardées dans les données du monde (TrainServeur.Donnees) et envoyées aux joueurs (Train.Voie).
 */
final class VoieServeur {
    /** Distance au point visé (blocs). */
    private static final double PORTEE = 3.0;
    /** Portée du regard pour viser la voie (blocs). */
    private static final double VUE = 64.0;
    /** Profil de la voie dessinée (le même que VoieRendu) : demi-largeur du dessus du ballast, pied du talus sur le sol
     *  (demi-largeur, profondeur), pied sur un pont, platelage des passages à niveau (demi-largeur, hauteur). */
    private static final double HAUT = 2.05, BAS_SOL = 3.9, PROF_SOL = 1.45, BAS_PONT = 2.25, PLATELAGE = 2.5,
            H_PLATELAGE = Reseau.RAIL - 0.01;
    private static final List<String> LETTRES = List.of("p", "r", "t", "T", "P", "x");
    private static final int LISTE_MAX = 100;

    private VoieServeur() {}

    static LiteralArgumentBuilder<CommandSourceStack> commande() {
        return Commands.literal("voie")
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("genre").then(Commands.argument("genre", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(LETTRES, b))
                        .executes(c -> retoucher(c.getSource(), StringArgumentType.getString(c, "genre"), 1))
                        .then(Commands.argument("longueur", IntegerArgumentType.integer(1, 100000))
                                .executes(c -> retoucher(c.getSource(), StringArgumentType.getString(c, "genre"),
                                        IntegerArgumentType.getInteger(c, "longueur"))))))
                .then(Commands.literal("retirer")
                        .executes(c -> retoucher(c.getSource(), null, 1))
                        .then(Commands.argument("longueur", IntegerArgumentType.integer(1, 100000))
                                .executes(c -> retoucher(c.getSource(), null, IntegerArgumentType.getInteger(c, "longueur")))))
                .then(Commands.literal("annuler").executes(c -> annuler(c.getSource())))
                .then(Commands.literal("liste").executes(c -> liste(c.getSource())))
                .then(Commands.literal("effacer").executes(c -> effacer(c.getSource())))
                .then(Commands.literal("remplir")
                        .executes(c -> remplir(c.getSource(), true))
                        .then(Commands.literal("ponts").executes(c -> remplir(c.getSource(), true)))
                        .then(Commands.literal("tout").executes(c -> remplir(c.getSource(), false))))
                .then(Commands.literal("vider")
                        .executes(c -> vider(c.getSource(), true))
                        .then(Commands.literal("ponts").executes(c -> vider(c.getSource(), true)))
                        .then(Commands.literal("tout").executes(c -> vider(c.getSource(), false))));
    }

    // ------------------------------------------------------------------------------------------------ outils

    /** Réseau de ce monde (choisi par ses données), ou null avec un message. */
    private static Reseau reseau(CommandSourceStack src) {
        TrainServeur.donnees(src.getServer());
        Reseau r = Reseau.get();
        if (r == null) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.train.pas_de_reseau", "Pas de réseau ferré dans ce jar."));
        }
        return r;
    }

    private static String nombre(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component nomGenre(byte g) {
        return Component.translatableWithFallback("heralisz_utils.train.voie.genre." + Retouches.nom(g), Retouches.nomFrancais(g));
    }

    private static Component mode(boolean ponts) {
        return ponts ? Component.translatableWithFallback("heralisz_utils.train.voie.mode.ponts", "ponts")
                : Component.translatableWithFallback("heralisz_utils.train.voie.mode.tout", "toute la voie");
    }

    /** Point de la voie visé par le joueur : le plus proche du bloc visé (à moins de 3 blocs), sinon le plus proche de
     *  la ligne du regard (à moins de 3 blocs, avant le bloc visé) ; -1 si aucun. Le joueur doit être dans le monde du
     *  train (le monde principal). */
    private static int viser(ServerPlayer p, Reseau r) {
        if (p.getServer() == null || p.level() != p.getServer().overworld()) return -1;
        Vec3 oeil = p.getEyePosition();
        Vec3 dir = p.getViewVector(1.0f);
        BlockHitResult hit = p.level().clip(new ClipContext(oeil, oeil.add(dir.scale(VUE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        double tMax = VUE;
        int meilleur = -1;
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 h = hit.getLocation();
            tMax = h.distanceTo(oeil);
            double best = PORTEE * PORTEE;
            for (int i = 0; i < r.n; i++) {
                double dx = r.x[i] - h.x, dy = r.y[i] + 0.2 - h.y, dz = r.z[i] - h.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) {
                    best = d2;
                    meilleur = i;
                }
            }
            if (meilleur >= 0) return meilleur;
        }
        double best = PORTEE;
        for (int i = 0; i < r.n; i++) {
            double vx = r.x[i] - oeil.x, vy = r.y[i] + 0.2 - oeil.y, vz = r.z[i] - oeil.z;
            double t = vx * dir.x + vy * dir.y + vz * dir.z;
            if (t < 0 || t > tMax + PORTEE) continue;
            double perp = Math.sqrt(Math.max(0, vx * vx + vy * vy + vz * vz - t * t));
            if (perp < best) {
                best = perp;
                meilleur = i;
            }
        }
        return meilleur;
    }

    /** Point visé, ou -1 avec un message (pas de joueur : erreur de commande habituelle). */
    private static int vise(CommandSourceStack src, Reseau r) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (p.getServer() != null && p.level() != p.getServer().overworld()) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.train.voie.autre_monde",
                    "La voie est dans le monde principal : allez-y pour la viser."));
            return -1;
        }
        int i = viser(p, r);
        if (i < 0) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.train.voie.rien_vise",
                    "Aucun point de la voie à moins de 3 blocs de ce que vous visez."));
        }
        return i;
    }

    // ------------------------------------------------------------------------------------------------ retouches

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        Reseau r = reseau(src);
        if (r == null) return 0;
        int i = vise(src, r);
        if (i < 0) return 0;
        TrainServeur.Donnees d = TrainServeur.donnees(src.getServer());
        byte[] tab = Retouches.table(r, d.retouches());
        byte dessin = Retouches.genre(r, tab, i), trace = r.genre[i];
        int avant = 0, apres = 0;
        while (avant < r.n - 1 && Retouches.genre(r, tab, i - avant - 1) == dessin) avant++;
        while (avant + apres < r.n - 1 && Retouches.genre(r, tab, i + apres + 1) == dessin) apres++;
        String debut = String.valueOf(Math.floorMod(i - avant, r.n)), fin = String.valueOf(Math.floorMod(i + apres, r.n));
        String nb = String.valueOf(avant + apres + 1);
        Component genre = nomGenre(dessin);
        String lettre = Retouches.lettre(dessin);
        Component retouche = dessin != trace
                ? Component.translatableWithFallback("heralisz_utils.train.voie.info.retouche", " (retouché ; tracé : %s)", nomGenre(trace))
                : Component.empty();
        String reseau = Reseau.nom(), indice = String.valueOf(i), s = nombre(r.s[i]);
        String x = nombre(r.x[i]), y = nombre(r.y[i]), z = nombre(r.z[i]);
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.info",
                "Voie (réseau %s) : point %s, s = %s, genre %s [%s]%s, x %s y %s z %s. Même genre du point %s au point %s (%s points).",
                reseau, indice, s, genre, lettre, retouche, x, y, z, debut, fin, nb), false);
        return 1;
    }

    /** Pose une retouche (lettre null : rien de dessiné) à partir du point visé, sur longueur points. */
    private static int retoucher(CommandSourceStack src, String lettre, int longueur) throws CommandSyntaxException {
        byte g = lettre == null ? Retouches.CACHE : Retouches.depuisLettre(lettre);
        if (g < Retouches.CACHE) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.train.voie.genre_inconnu",
                    "Genre inconnu : %s (p plat, r remblai, t tranchée, T pont, P tunnel, x passage à niveau).", lettre));
            return 0;
        }
        Reseau r = reseau(src);
        if (r == null) return 0;
        int i = vise(src, r);
        if (i < 0) return 0;
        MinecraftServer srv = src.getServer();
        TrainServeur.Donnees d = TrainServeur.donnees(srv);
        List<Retouches.Retouche> l = d.retouches();
        if (l.size() >= Retouches.MAX) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.train.voie.trop",
                    "Trop de retouches (%s au plus) : annulez-en ou effacez-les d'abord.", String.valueOf(Retouches.MAX)));
            return 0;
        }
        int lg = Math.min(longueur, r.n);
        l.add(new Retouches.Retouche(i, lg, g));
        d.setDirty();
        TrainServeur.voie(srv, null);
        String debut = String.valueOf(i), fin = String.valueOf(Math.floorMod(i + lg - 1, r.n)), nb = String.valueOf(lg);
        if (g == Retouches.CACHE) {
            src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.retiree",
                    "Voie cachée du point %s au point %s (%s points). /train voie annuler pour revenir en arrière.", debut, fin, nb), true);
        } else {
            Component genre = nomGenre(g);
            src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.retouchee",
                    "Voie dessinée en %s du point %s au point %s (%s points). /train voie annuler pour revenir en arrière.",
                    genre, debut, fin, nb), true);
        }
        return 1;
    }

    private static int annuler(CommandSourceStack src) {
        if (reseau(src) == null) return 0;
        MinecraftServer srv = src.getServer();
        TrainServeur.Donnees d = TrainServeur.donnees(srv);
        List<Retouches.Retouche> l = d.retouches();
        if (l.isEmpty()) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.train.voie.rien_a_annuler",
                    "Aucune retouche de la voie à annuler."));
            return 0;
        }
        Retouches.Retouche x = l.remove(l.size() - 1);
        d.setDirty();
        TrainServeur.voie(srv, null);
        String debut = String.valueOf(x.debut()), nb = String.valueOf(x.longueur()), reste = String.valueOf(l.size());
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.annulee",
                "Dernière retouche annulée (point %s, %s points). Retouches restantes : %s.", debut, nb, reste), true);
        return 1;
    }

    private static int liste(CommandSourceStack src) {
        Reseau r = reseau(src);
        if (r == null) return 0;
        List<Retouches.Retouche> l = TrainServeur.donnees(src.getServer()).retouches();
        String reseau = Reseau.nom();
        if (l.isEmpty()) {
            src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.liste_vide",
                    "Aucune retouche de la voie (réseau %s).", reseau), false);
            return 1;
        }
        String total = String.valueOf(l.size());
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.liste",
                "Retouches de la voie (réseau %s) : %s, la dernière l'emporte.", reseau, total), false);
        int premier = Math.max(0, l.size() - LISTE_MAX);
        if (premier > 0) {
            String plus = String.valueOf(premier);
            src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.liste_plus",
                    "(%s plus anciennes ne sont pas montrées)", plus), false);
        }
        for (int k = premier; k < l.size(); k++) {
            Retouches.Retouche x = l.get(k);
            String num = String.valueOf(k + 1), debut = String.valueOf(x.debut()),
                    fin = String.valueOf(Math.floorMod(x.debut() + x.longueur() - 1, r.n)), nb = String.valueOf(x.longueur());
            Component genre = nomGenre(x.genre());
            String lettre = Retouches.lettre(x.genre());
            src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.liste_ligne",
                    "%s. points %s à %s (%s) : %s [%s]", num, debut, fin, nb, genre, lettre), false);
        }
        return 1;
    }

    private static int effacer(CommandSourceStack src) {
        if (reseau(src) == null) return 0;
        MinecraftServer srv = src.getServer();
        TrainServeur.Donnees d = TrainServeur.donnees(srv);
        List<Retouches.Retouche> l = d.retouches();
        String nb = String.valueOf(l.size());
        l.clear();
        d.setDirty();
        TrainServeur.voie(srv, null);
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.effacees",
                "Toutes les retouches de la voie sont effacées (%s).", nb), true);
        return 1;
    }

    // ------------------------------------------------------------------------------------------------ voie pleine

    /** Colonnes (x, z) sous la voie dessinée : {dessus le plus haut, bas le plus bas} de ce qu'il faut remplir. */
    private static Map<Long, double[]> empreinte(Reseau r, byte[] tab, boolean ponts) {
        Map<Long, double[]> cols = new HashMap<>();
        double[] p = new double[3], av = new double[3], ar = new double[3];
        for (int i = 0; i < r.n; i++) {
            byte g = Retouches.genre(r, tab, i);
            if (g == Retouches.CACHE || ponts && g != Reseau.PONT) continue;
            double si = r.s[i];
            r.point(si + 0.75, av);
            r.point(si - 0.75, ar);
            double tx = av[0] - ar[0], tz = av[2] - ar[2], l = Math.sqrt(tx * tx + tz * tz);
            if (l < 1e-6) continue;
            tx /= l;
            tz /= l;
            double nx = -tz, nz = tx, px = r.x[i], pz = r.z[i];
            boolean pont = g == Reseau.PONT, passage = g == Reseau.PASSAGE;
            double demi = passage ? PLATELAGE : pont ? BAS_PONT : BAS_SOL;
            for (int bx = (int) Math.floor(px - demi - 1); bx <= (int) Math.floor(px + demi + 1); bx++) {
                for (int bz = (int) Math.floor(pz - demi - 1); bz <= (int) Math.floor(pz + demi + 1); bz++) {
                    double cx = bx + 0.5 - px, cz = bz + 0.5 - pz;
                    double le = cx * tx + cz * tz, lat = Math.abs(cx * nx + cz * nz);
                    if (Math.abs(le) > 0.55 || lat > demi) continue;              // milieu de la colonne sous ce point
                    r.point(si + le, p);
                    double ys = p[1], dessus, bas;
                    if (passage) {
                        dessus = ys + H_PLATELAGE;
                        bas = ys - 1;
                    } else {
                        double prof = pont ? ys - Math.floor(ys - 0.25) : PROF_SOL;
                        if (lat <= HAUT) {
                            dessus = Math.abs(lat - Reseau.FILE) <= 0.5 ? ys + Reseau.RAIL : ys;   // rail : on marche dessus
                        } else {
                            dessus = ys - prof * (lat - HAUT) / ((pont ? BAS_PONT : BAS_SOL) - HAUT);
                        }
                        bas = pont ? Math.floor(ys - 0.25) : ys - PROF_SOL;
                    }
                    long cle = ChunkPos.asLong(bx, bz);
                    double[] c = cols.get(cle);
                    if (c == null) {
                        cols.put(cle, new double[]{dessus, bas});
                    } else {
                        c[0] = Math.max(c[0], dessus);
                        c[1] = Math.min(c[1], bas);
                    }
                }
            }
        }
        return cols;
    }

    private static int remplir(CommandSourceStack src, boolean ponts) {
        Reseau r = reseau(src);
        if (r == null) return 0;
        MinecraftServer srv = src.getServer();
        ServerLevel lvl = srv.overworld();
        byte[] tab = Retouches.table(r, TrainServeur.donnees(srv).retouches());
        if (!(Train.VOIE_PLEINE.get() instanceof VoiePleineBlock bloc)) return 0;
        int poses = 0;
        Set<Long> sautes = new HashSet<>();
        for (Map.Entry<Long, double[]> e : empreinte(r, tab, ponts).entrySet()) {
            int bx = ChunkPos.getX(e.getKey()), bz = ChunkPos.getZ(e.getKey());
            double dessus = e.getValue()[0], bas = e.getValue()[1];
            if (dessus - bas < 1.0 / 32) continue;
            if (!lvl.hasChunkAt(new BlockPos(bx, lvl.getMinBuildHeight(), bz))) {
                sautes.add(ChunkPos.asLong(bx >> 4, bz >> 4));
                continue;
            }
            int yHaut = (int) Math.floor(dessus - 1e-4), yBas = (int) Math.floor(bas + 1e-4);
            for (int y = yHaut; y >= yBas && yHaut - y < 4; y--) {
                BlockPos bp = new BlockPos(bx, y, bz);
                if (!lvl.isInWorldBounds(bp) || !lvl.getBlockState(bp).isAir()) continue;   // jamais à la place d'un bloc
                double h = y == yHaut ? dessus - y : 1.0;
                if (h < 1.0 / 32) continue;
                lvl.setBlock(bp, bloc.hauteur(h), Block.UPDATE_CLIENTS);
                poses++;
            }
        }
        String nb = String.valueOf(poses);
        Component quoi = mode(ponts);
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.remplie",
                "Voie pleine posée : %s bloc(s) sous la voie dessinée (%s), seulement dans l'air.", nb, quoi), true);
        nonCharges(src, sautes);
        return poses > 0 ? 1 : 0;
    }

    private static int vider(CommandSourceStack src, boolean ponts) {
        Reseau r = reseau(src);
        if (r == null) return 0;
        MinecraftServer srv = src.getServer();
        ServerLevel lvl = srv.overworld();
        byte[] tab = Retouches.table(r, TrainServeur.donnees(srv).retouches());
        Block bloc = Train.VOIE_PLEINE.get();
        BlockState air = Blocks.AIR.defaultBlockState();
        // colonnes : une marge autour de tout ce que remplir a pu poser, quelles que soient les retouches depuis
        Map<Long, int[]> cols = new HashMap<>();
        for (int i = 0; i < r.n; i++) {
            if (ponts && r.genre[i] != Reseau.PONT && Retouches.genre(r, tab, i) != Reseau.PONT) continue;
            double px = r.x[i], py = r.y[i], pz = r.z[i], demi = BAS_SOL + 0.5;
            for (int bx = (int) Math.floor(px - demi); bx <= (int) Math.floor(px + demi); bx++) {
                for (int bz = (int) Math.floor(pz - demi); bz <= (int) Math.floor(pz + demi); bz++) {
                    int y0 = (int) Math.floor(py - PROF_SOL - 1), y1 = (int) Math.floor(py + 1);
                    int[] c = cols.computeIfAbsent(ChunkPos.asLong(bx, bz), k -> new int[]{y0, y1});
                    c[0] = Math.min(c[0], y0);
                    c[1] = Math.max(c[1], y1);
                }
            }
        }
        int retires = 0;
        Set<Long> sautes = new HashSet<>();
        for (Map.Entry<Long, int[]> e : cols.entrySet()) {
            int bx = ChunkPos.getX(e.getKey()), bz = ChunkPos.getZ(e.getKey());
            if (!lvl.hasChunkAt(new BlockPos(bx, lvl.getMinBuildHeight(), bz))) {
                sautes.add(ChunkPos.asLong(bx >> 4, bz >> 4));
                continue;
            }
            for (int y = e.getValue()[0]; y <= e.getValue()[1]; y++) {
                BlockPos bp = new BlockPos(bx, y, bz);
                if (lvl.getBlockState(bp).is(bloc)) {                         // seulement la voie pleine
                    lvl.setBlock(bp, air, Block.UPDATE_CLIENTS);
                    retires++;
                }
            }
        }
        String nb = String.valueOf(retires);
        Component quoi = mode(ponts);
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.videe",
                "Voie pleine retirée : %s bloc(s) (%s).", nb, quoi), true);
        nonCharges(src, sautes);
        return 1;
    }

    private static void nonCharges(CommandSourceStack src, Set<Long> sautes) {
        if (sautes.isEmpty()) return;
        String nb = String.valueOf(sautes.size());
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.train.voie.non_charges",
                "%s chunk(s) non chargé(s) sauté(s) : approchez-vous de ces parties de la voie et relancez la commande (ce qui est déjà fait reste).",
                nb), false);
    }
}
