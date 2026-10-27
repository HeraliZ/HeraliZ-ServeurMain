package fr.heraliz.quetes.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.objet.Baguette;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

/**
 * Commandes : {@code /quetes} (tous les joueurs : le journal) ; {@code /pnj} et {@code /quete} (staff, permission 2) pour
 * poser les PNJ, les points de quête, relire les fichiers et aider un joueur. Réponses en message direct.
 */
final class Commandes {
    private Commandes() {
    }

    private static final SuggestionProvider<CommandSourceStack> PNJ_IDS = (c, b) ->
            SharedSuggestionProvider.suggest(Catalogue.tous().stream().map(p -> p.id), b);
    private static final SuggestionProvider<CommandSourceStack> POINTS = (c, b) ->
            SharedSuggestionProvider.suggest(Moteur.points().tous().stream().map(Points.Point::id), b);
    private static final SuggestionProvider<CommandSourceStack> GROUPES = (c, b) ->
            SharedSuggestionProvider.suggest(Moteur.points().tous().stream().map(Points.Point::groupe).distinct(), b);

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        // 20/10 : remise à zéro complète d'un joueur (op seulement), « confirmer » pour éviter un accident
        d.register(Commands.literal("wipe").requires(src -> src.hasPermission(4))
                .then(WipeServeur.commande())
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.literal("confirmer").executes(c -> wipe(c, EntityArgument.getPlayer(c, "joueur"))))));
        d.register(Commands.literal("quetes").executes(c -> {
            Moteur.envoyerJournal(c.getSource().getPlayerOrException(), true);
            return 1;
        }));
        // quêtes du jour (26/10) : /journalieres ouvre le menu ; le staff (op 2) voit l'état d'un joueur ou relance sa remise
        d.register(Commands.literal("journalieres").executes(c -> {
            Journalieres.envoyer(c.getSource().getPlayerOrException(), true);
            return 1;
        }).then(Commands.literal("voir").requires(s -> s.hasPermission(2)).then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                    c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " : " + Journalieres.resume(p)), false);
                    return 1;
                }))).then(Commands.literal("remettre").requires(s -> s.hasPermission(2)).then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                    Journalieres.remettre(p, Journalieres.etat(p));
                    c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " : " + Journalieres.resume(p)), false);
                    return 1;
                }))).then(Commands.literal("essai").requires(s -> s.hasPermission(4)).then(Commands.argument("joueur", EntityArgument.player())
                .then(Commands.literal("forcer").then(Commands.argument("difficulte", StringArgumentType.word())
                        .then(Commands.argument("quete", StringArgumentType.word()).executes(c -> essaiJour(c, "forcer",
                                StringArgumentType.getString(c, "difficulte"), StringArgumentType.getString(c, "quete"), 0)))))
                .then(Commands.literal("avancer").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100000))
                        .executes(c -> essaiJour(c, "avancer", "", "", com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("minuit").executes(c -> essaiJour(c, "minuit", "", "", 0))))));

        d.register(Commands.literal("pnj").requires(s -> s.hasPermission(2))
                .then(Commands.literal("creer").then(Commands.argument("id", StringArgumentType.word()).suggests(PNJ_IDS)
                        .executes(c -> creer(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("deplacer").then(Commands.argument("id", StringArgumentType.word()).suggests(PNJ_IDS)
                        .executes(c -> deplacer(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("retirer").then(Commands.argument("id", StringArgumentType.word()).suggests(PNJ_IDS)
                        .executes(c -> retirer(c, StringArgumentType.getString(c, "id")))))
                .then(Commands.literal("skin").then(Commands.argument("id", StringArgumentType.word()).suggests(PNJ_IDS)
                        .then(Commands.argument("skin", StringArgumentType.word())
                                .executes(c -> skin(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "skin"))))))
                .then(Commands.literal("bras").then(Commands.argument("id", StringArgumentType.word()).suggests(PNJ_IDS)
                        .then(Commands.literal("fins").executes(c -> bras(c, StringArgumentType.getString(c, "id"), true)))
                        .then(Commands.literal("larges").executes(c -> bras(c, StringArgumentType.getString(c, "id"), false)))))
                .then(Commands.literal("apparence").then(Commands.argument("id", StringArgumentType.word()).suggests(PNJ_IDS)
                        .then(Commands.literal("moi").executes(c -> apparenceMoi(c, StringArgumentType.getString(c, "id"))))
                        .then(Commands.literal("fichier").executes(c -> apparenceFichier(c, StringArgumentType.getString(c, "id"))))
                        .then(Commands.literal("aucune").executes(c -> apparenceAucune(c, StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("liste").executes(Commandes::listePnj)));

        EditionMarchands.commandes(d);

        d.register(Commands.literal("quete").requires(s -> s.hasPermission(2))
                .then(Commands.literal("baguette").then(Commands.argument("groupe", StringArgumentType.word()).suggests(GROUPES)
                        .executes(c -> baguette(c, StringArgumentType.getString(c, "groupe")))))
                .then(Commands.literal("point")
                        .then(Commands.literal("ajouter").then(Commands.argument("groupe", StringArgumentType.word()).suggests(GROUPES)
                                .executes(c -> ajouterPoint(c, StringArgumentType.getString(c, "groupe"), ""))
                                .then(Commands.argument("nom", StringArgumentType.greedyString())
                                        .executes(c -> ajouterPoint(c, StringArgumentType.getString(c, "groupe"), StringArgumentType.getString(c, "nom"))))))
                        .then(Commands.literal("retirer").then(Commands.argument("id", StringArgumentType.word()).suggests(POINTS)
                                .executes(c -> retirerPoint(c, StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("nom").then(Commands.argument("id", StringArgumentType.word()).suggests(POINTS)
                                .then(Commands.argument("nom", StringArgumentType.greedyString())
                                        .executes(c -> nommerPoint(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "nom"))))))
                        .then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.word()).suggests(POINTS)
                                .executes(c -> tpPoint(c, StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("points").executes(c -> listePoints(c, ""))
                        .then(Commands.argument("groupe", StringArgumentType.word()).suggests(GROUPES)
                                .executes(c -> listePoints(c, StringArgumentType.getString(c, "groupe")))))
                .then(Commands.literal("recharger").executes(Commandes::recharger))
                .then(Commands.literal("joueur").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> voirJoueur(c, EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("donner").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("pnj", StringArgumentType.word()).suggests(PNJ_IDS)
                                .then(Commands.argument("quete", StringArgumentType.word())
                                        .executes(c -> donnerQuete(c, EntityArgument.getPlayer(c, "joueur"),
                                                StringArgumentType.getString(c, "pnj"), StringArgumentType.getString(c, "quete")))))))
                .then(Commands.literal("finir").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("cle", StringArgumentType.greedyString())
                                .executes(c -> finir(c, EntityArgument.getPlayer(c, "joueur"), StringArgumentType.getString(c, "cle"))))))
                .then(Commands.literal("reset").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> reset(c, EntityArgument.getPlayer(c, "joueur"))))));
    }

    private static void dire(CommandContext<CommandSourceStack> c, String cle, String fr, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatableWithFallback(cle, fr, args), false);
    }

    private static int erreur(CommandContext<CommandSourceStack> c, String cle, String fr, Object... args) {
        c.getSource().sendFailure(Component.translatableWithFallback(cle, fr, args));
        return 0;
    }

    // ------------------------------------------------------------------ PNJ
    static List<Pnj> trouver(String id) {
        List<Pnj> l = new ArrayList<>();
        for (ServerLevel lvl : Moteur.serveur.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) if (e instanceof Pnj p && p.pnjId().equals(id)) l.add(p);
        }
        return l;
    }

    /** Pose le PNJ {@code id} là où se tient ce joueur, tourné comme lui (null si impossible). */
    static Pnj poser(ServerPlayer moi, String id) {
        Defs.Pnj def = Catalogue.pnj(id);
        Pnj pnj = HeralizQuetes.PNJ.get().create(moi.serverLevel());
        if (pnj == null) return null;
        pnj.moveTo(moi.getX(), moi.getY(), moi.getZ(), moi.getYRot(), 0);
        pnj.setYHeadRot(moi.getYRot());
        pnj.setYBodyRot(moi.getYRot());
        pnj.definirId(id);
        pnj.definirNom(def == null || def.nom.vide() ? Texte.fr(id) : def.nom);
        if (def != null) {
            pnj.definirSkin(def.skin);
            pnj.definirBrasFins(def.brasFins);
            if (def.apparence != null) pnj.definirApparence(def.apparence, false);
        }
        moi.serverLevel().addFreshEntity(pnj);
        return pnj;
    }

    private static int creer(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        ServerPlayer moi = c.getSource().getPlayerOrException();
        id = id.toLowerCase(Locale.ROOT);
        Defs.Pnj def = Catalogue.pnj(id);
        if (poser(moi, id) == null) return 0;
        int deja = trouver(id).size() - 1;
        dire(c, "heraliz_quetes.cmd.pnj_cree", "PNJ « %s » posé ici.", id);
        if (def == null) erreur(c, "heraliz_quetes.cmd.pnj_sans_fichier", "Ce PNJ (%s) n'a pas de fichier dans config/heraliz_quetes/pnj/.", id);
        if (deja > 0) erreur(c, "heraliz_quetes.cmd.pnj_double", "Attention : %s autre(s) PNJ « %s » existe(nt) déjà (/pnj retirer pour les enlever).", deja, id);
        return 1;
    }

    private static int deplacer(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        ServerPlayer moi = c.getSource().getPlayerOrException();
        List<Pnj> l = trouver(id);
        if (l.isEmpty()) return erreur(c, "heraliz_quetes.cmd.pnj_introuvable", "Aucun PNJ « %s » chargé (il faut être près de lui, ou le recréer).", id);
        Pnj p = l.get(0);
        if (p.level() != moi.level()) {
            p.discard();
            return creer(c, id);
        }
        p.moveTo(moi.getX(), moi.getY(), moi.getZ(), moi.getYRot(), 0);
        p.setYHeadRot(moi.getYRot());
        p.setYBodyRot(moi.getYRot());
        dire(c, "heraliz_quetes.cmd.pnj_deplace", "PNJ « %s » déplacé ici.", id);
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> c, String id) {
        List<Pnj> l = trouver(id);
        for (Pnj p : l) p.discard();
        dire(c, "heraliz_quetes.cmd.pnj_retire", "%s PNJ « %s » retiré(s).", l.size(), id);
        return l.size();
    }

    private static int skin(CommandContext<CommandSourceStack> c, String id, String skin) {
        List<Pnj> l = trouver(id);
        for (Pnj p : l) p.definirSkin(skin.equals("defaut") ? "" : skin);
        dire(c, "heraliz_quetes.cmd.pnj_skin", "Skin « %s » pour %s PNJ (fichier textures/pnj/%s.png du jar joueur).", skin, l.size(), skin);
        return l.size();
    }

    /** Le PNJ prend le personnage CaraCrea du staff qui tape la commande (fait dans /perso). */
    private static int apparenceMoi(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        CompoundTag a = Apparences.deJoueur(c.getSource().getPlayerOrException());
        if (a == null) return erreur(c, "heraliz_quetes.cmd.apparence_sans", "Tu n'as pas encore de personnage CaraCrea (/perso).");
        List<Pnj> l = trouver(id);
        if (l.isEmpty()) return erreur(c, "heraliz_quetes.cmd.pnj_introuvable", "Aucun PNJ « %s » chargé (il faut être près de lui, ou le recréer).", id);
        for (Pnj p : l) p.definirApparence(a, true);
        dire(c, "heraliz_quetes.cmd.apparence_moi", "%s PNJ « %s » prend ton personnage CaraCrea. Pour la garder dans son fichier : %s", l.size(), id, Apparences.decrire(a));
        return l.size();
    }

    /** Retour à l'apparence écrite dans le fichier du PNJ (sinon son skin). */
    private static int apparenceFichier(CommandContext<CommandSourceStack> c, String id) {
        Defs.Pnj def = Catalogue.pnj(id);
        CompoundTag a = def == null ? null : def.apparence;
        List<Pnj> l = trouver(id);
        for (Pnj p : l) p.definirApparence(a, false);
        dire(c, "heraliz_quetes.cmd.apparence_fichier", "%s PNJ « %s » : apparence de son fichier.", l.size(), id);
        return l.size();
    }

    /** Plus d'apparence CaraCrea : le skin du PNJ (ou Steve / Alex), jusqu'à /pnj apparence fichier. */
    private static int apparenceAucune(CommandContext<CommandSourceStack> c, String id) {
        List<Pnj> l = trouver(id);
        for (Pnj p : l) p.definirApparence(new CompoundTag(), true);
        dire(c, "heraliz_quetes.cmd.apparence_aucune", "%s PNJ « %s » : plus d'apparence CaraCrea (skin du PNJ).", l.size(), id);
        return l.size();
    }

    private static int bras(CommandContext<CommandSourceStack> c, String id, boolean fins) {
        List<Pnj> l = trouver(id);
        for (Pnj p : l) p.definirBrasFins(fins);
        dire(c, "heraliz_quetes.cmd.pnj_bras", "Bras changés pour %s PNJ.", l.size());
        return l.size();
    }

    private static int listePnj(CommandContext<CommandSourceStack> c) {
        for (Defs.Pnj p : Catalogue.tous()) {
            dire(c, "heraliz_quetes.cmd.pnj_ligne", "%s (%s) : %s quêtes de suite, %s modèles de contrats, %s posé(s) et chargé(s)",
                    p.id, p.nom.francais(), p.suite.size(), p.contrats.size(), trouver(p.id).size());
        }
        return 1;
    }

    // ------------------------------------------------------------------ points
    private static int baguette(CommandContext<CommandSourceStack> c, String groupe) throws CommandSyntaxException {
        ServerPlayer moi = c.getSource().getPlayerOrException();
        ItemStack s = new ItemStack(HeralizQuetes.BAGUETTE.get());
        s.getOrCreateTag().putString(Baguette.GROUPE, groupe.toLowerCase(Locale.ROOT));
        s.setHoverName(Component.translatableWithFallback("item.heraliz_quetes.baguette.nom", "Baguette de quête : %s", groupe.toLowerCase(Locale.ROOT)));
        if (!moi.getInventory().add(s)) moi.drop(s, false);
        dire(c, "heraliz_quetes.cmd.baguette", "Baguette du groupe « %s » : clic droit sur un bloc = un point ; accroupi + clic droit = retirer.", groupe);
        return 1;
    }

    private static int ajouterPoint(CommandContext<CommandSourceStack> c, String groupe, String nom) throws CommandSyntaxException {
        ServerPlayer moi = c.getSource().getPlayerOrException();
        Points.Point p = Moteur.points().ajouter(groupe, moi.level().dimension().location(), moi.blockPosition(), nom);
        dire(c, "heraliz_quetes.cmd.point_ajoute", "Point %s posé en %s %s %s.", p.id(), p.pos().getX(), p.pos().getY(), p.pos().getZ());
        return 1;
    }

    private static int retirerPoint(CommandContext<CommandSourceStack> c, String id) {
        if (!Moteur.points().retirer(id)) return erreur(c, "heraliz_quetes.cmd.point_inconnu", "Point inconnu : %s", id);
        dire(c, "heraliz_quetes.cmd.point_retire", "Point %s retiré.", id);
        return 1;
    }

    private static int nommerPoint(CommandContext<CommandSourceStack> c, String id, String nom) {
        if (!Moteur.points().nommer(id, nom)) return erreur(c, "heraliz_quetes.cmd.point_inconnu", "Point inconnu : %s", id);
        dire(c, "heraliz_quetes.cmd.point_nomme", "Point %s : « %s ».", id, nom);
        return 1;
    }

    private static int tpPoint(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        ServerPlayer moi = c.getSource().getPlayerOrException();
        Points.Point p = Moteur.points().point(id);
        if (p == null) return erreur(c, "heraliz_quetes.cmd.point_inconnu", "Point inconnu : %s", id);
        ResourceLocation rl = ResourceLocation.tryParse(p.monde());
        ServerLevel lvl = rl == null ? null : Moteur.serveur.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (lvl == null) return erreur(c, "heraliz_quetes.cmd.monde_inconnu", "Monde introuvable : %s", p.monde());
        BlockPos b = p.pos();
        moi.teleportTo(lvl, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, moi.getYRot(), moi.getXRot());
        return 1;
    }

    private static int listePoints(CommandContext<CommandSourceStack> c, String groupe) {
        int n = 0;
        for (Points.Point p : Moteur.points().tous()) {
            if (!groupe.isEmpty() && !p.groupe().equals(groupe)) continue;
            n++;
            dire(c, "heraliz_quetes.cmd.point_ligne", "%s · %s %s %s %s %s", p.id(), p.monde(), p.pos().getX(), p.pos().getY(), p.pos().getZ(),
                    p.nom().isEmpty() ? "" : "« " + p.nom() + " »");
        }
        dire(c, "heraliz_quetes.cmd.points_total", "%s point(s).", n);
        return n;
    }

    /** /journalieres essai (op 4) : quête du jour forcée, avancée ou minuit simulé, pour les essais (noté au journal). */
    private static int essaiJour(CommandContext<CommandSourceStack> c, String quoi, String diff, String quete, int n) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
        boolean ok = Journalieres.essai(p, quoi, diff, quete, n);
        if (ok) c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " : " + Journalieres.resume(p)), true);
        else c.getSource().sendFailure(Component.translatableWithFallback("heraliz_quetes.cmd.jour_essai", "Impossible (quête inconnue ou pas en cours)."));
        return ok ? 1 : 0;
    }

    private static int recharger(CommandContext<CommandSourceStack> c) {
        List<String> erreurs = new java.util.ArrayList<>(Catalogue.charger());
        if (!Journalieres.charger()) erreurs.add("config/heraliz_quetes/journalieres.json illisible : quêtes du jour inchangées");
        Moteur.oublierContrats();
        Ponts.relireArmes();
        dire(c, "heraliz_quetes.cmd.recharge", "%s PNJ relus.", Catalogue.tous().size());
        for (String e : erreurs) c.getSource().sendFailure(Component.literal(e));
        if (Moteur.serveur != null) for (ServerPlayer p : Moteur.serveur.getPlayerList().getPlayers()) Moteur.envoyerSuivi(p);
        return 1;
    }

    // ------------------------------------------------------------------ aider un joueur
    private static int voirJoueur(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        dire(c, "heraliz_quetes.cmd.joueur", "%s : %s quête(s) en cours, %s finie(s), %s contrat(s) fini(s).", p.getGameProfile().getName(),
                j.actives.size(), j.finies.size(), j.contratsFinis);
        for (Donnees.DActive a : j.actives.values()) {
            Defs.Quete q = Moteur.def(a);
            dire(c, "heraliz_quetes.cmd.joueur_quete", "· %s « %s » étape %s/%s%s", a.cle, q == null ? "?" : q.titre.francais(), a.etape + 1,
                    q == null ? "?" : q.etapes.size(), a.aRendre ? " (à rendre)" : "");
        }
        for (var e : j.pnj.entrySet()) {
            dire(c, "heraliz_quetes.cmd.joueur_pnj", "· %s : réputation %s, suite %s", e.getKey(), e.getValue().rep, e.getValue().suite);
        }
        return 1;
    }

    private static int donnerQuete(CommandContext<CommandSourceStack> c, ServerPlayer p, String pnj, String quete) {
        Defs.Pnj def = Catalogue.pnj(pnj);
        Defs.Quete q = def == null ? null : def.quete(quete);
        if (q == null) return erreur(c, "heraliz_quetes.cmd.quete_inconnue", "Quête inconnue : %s / %s", pnj, quete);
        if (Donnees.de(p.getUUID()).actives.containsKey("s:" + pnj + ":" + quete))
            return erreur(c, "heraliz_quetes.cmd.deja", "Il a déjà cette quête.");
        Moteur.accepter(p, def, q, null);
        dire(c, "heraliz_quetes.cmd.donnee", "Quête « %s » donnée à %s.", q.titre.francais(), p.getGameProfile().getName());
        return 1;
    }

    private static int finir(CommandContext<CommandSourceStack> c, ServerPlayer p, String cle) {
        Donnees.DActive a = Donnees.de(p.getUUID()).actives.get(cle);
        Defs.Quete q = a == null ? null : Moteur.def(a);
        if (q == null) return erreur(c, "heraliz_quetes.cmd.pas_en_cours", "Pas de quête en cours « %s » (voir /quete joueur).", cle);
        Moteur.terminer(p, a, q);
        dire(c, "heraliz_quetes.cmd.finie", "Quête finie pour %s.", p.getGameProfile().getName());
        return 1;
    }

    /** Données du joueur gardées à la mort et qui disent « déjà fait » (cinématique, guide, Bâtisseur). */
    private static final String[] MARQUES = {"heraliz_cinematique_vue", "heraliz_cinematique_finie", "heraliz_guide_visite",
            "heraliz_batisseur_presente", "heraliz_kit_depart"};

    /**
     * Remise à zéro complète : cabane d'essai du tutoriel (jamais la base du clan), coffre de match, échanges /trade en
     * attente, tutoriel, personnage CaraCrea, quêtes, inventaire, coffre de l'Ender, Curios,
     * coffres personnels (planque, VIP), clan (retiré ; chef : direction passée au membre suivant, ou clan dissous s'il
     * était seul), hérals et compte de capsules remis à 0, marques de la cinématique et du guide ; puis le joueur est expulsé : à sa
     * reconnexion il refait toute l'arrivée (créateur de personnage, cinématique, guide). Gardés : objets à vie
     * (couteaux, skins à vie, rendus dans l'inventaire), collection de camos à vie, grades et compte AuthMe. Chaque mod
     * absent est simplement sauté (commandes passées au serveur, appels par réflexion).
     */
    private static int wipe(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        String nom = p.getGameProfile().getName();
        com.mojang.logging.LogUtils.getLogger().info("[Quêtes] /wipe de {} par {}", nom, c.getSource().getTextName());
        dire(c, "heraliz_quetes.cmd.wipe", "%s a été remis à zéro : il refera toute l'arrivée à sa reconnexion.", nom);
        remettreAZero(p, List.of(), Component.translatableWithFallback("heraliz_quetes.cmd.wipe_kick",
                "Ton personnage a été remis à zéro. Reconnecte-toi pour recommencer."));
        return 1;
    }

    /**
     * Le cœur de {@code /wipe <joueur>}, repris par {@code /wipe tout} ({@link WipeServeur}) à la connexion de chaque
     * joueur : tout est remis à zéro, ses objets à vie (et {@code enPlus}, ceux trouvés dans sa base ou le coffre de son
     * clan) lui sont rendus, puis il est expulsé avec {@code message}.
     */
    static void remettreAZero(ServerPlayer p, List<ItemStack> enPlus, Component message) {
        String nom = p.getGameProfile().getName();
        var srv = p.server;
        // 25/10 : clés de caisse sur le compte (HeraliZ Caisses) : celles des caisses de jeu partent, comme quand elles
        // étaient dans l'inventaire ; les clés cosmétiques achetées (Gemme, Camo, emotes) restent. Avant de vider l'inventaire.
        appel("fr.heraliz.crates.api.ClesApi", "wipe", new Class<?>[]{UUID.class}, p.getUUID());
        // 27/10 : compte de capsules du casino (HeraliZ Casino) remis à 0, comme les hérals et les capsules de l'inventaire
        appel("fr.heraliz.casino.api.CapsulesApi", "wipe", new Class<?>[]{UUID.class}, p.getUUID());
        List<ItemStack> aVie = new ArrayList<>();
        garderAVie(aVie, p.getInventory());
        garderAVie(aVie, p.getEnderChestInventory());
        ItemStack souris = p.containerMenu.getCarried();
        if (estAVie(souris)) aVie.add(souris.copy());
        viderCoffresPerso(srv, p.getUUID(), aVie);
        for (ItemStack s : enPlus) if (!s.isEmpty()) aVie.add(s.copy());
        // 23/10 : son coffre de match (vrai inventaire d'un joueur en duel, ClashWar, Zombies) et ses échanges /trade en
        // attente partent aussi (sinon rendus après la remise à zéro) ; seuls leurs objets à vie sont gardés
        garderAVie(aVie, appelObjets("fr.heraliz.clashwar.serveur.WipeClashWar", "vider",
                new Class<?>[]{net.minecraft.server.MinecraftServer.class, UUID.class}, srv, p.getUUID()));
        garderAVie(aVie, appelObjets("fr.heraliz.grades.serveur.EchangeServeur", "wipe", new Class<?>[]{UUID.class}, p.getUUID()));
        // 23/10 : seulement sa cabane d'essai du tutoriel, AVANT de le retirer de son clan ; plus jamais « base effacer »
        // (le groupe CRac du clan ne suit le clan que toutes les 5 s : c'était la base de tout le clan qui partait)
        appel("fr.heraliz.crac.api.WipeApi", "cabaneEssai", new Class<?>[]{net.minecraft.server.MinecraftServer.class, UUID.class},
                srv, p.getUUID());
        appel("fr.heralisz.clans.server.ClanService", "adminRetirer", new Class<?>[]{UUID.class, String.class}, p.getUUID(), nom);
        appel("fr.heralisz.economie.api.EconomieApi", "definir", new Class<?>[]{UUID.class, long.class, String.class},
                p.getUUID(), 0L, "wipe");
        CommandSourceStack console = srv.createCommandSourceStack().withSuppressedOutput();
        for (String cmd : new String[]{"tuto rejouer " + nom, "caracrea reset " + nom, "curios clear " + nom}) {
            try {
                srv.getCommands().performPrefixedCommand(console, cmd);
            } catch (Exception ignore) {
                // mod absent ou commande refusée : on continue
            }
        }
        resetQuetes(p);
        p.getInventory().clearContent();
        p.getEnderChestInventory().clearContent();
        p.containerMenu.setCarried(ItemStack.EMPTY);
        for (ItemStack s : aVie) if (!p.getInventory().add(s)) p.getEnderChestInventory().addItem(s);
        CompoundTag pers = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        for (String m : MARQUES) pers.remove(m);
        Accueil.oublier(p.getUUID());
        p.connection.disconnect(message);
    }

    private static java.lang.reflect.Method estAVie;

    /** Objet à vie (couteau, skin à vie) d'après HeraliZ Tactique, par réflexion ; sans le mod : rien n'est gardé. */
    static boolean estAVie(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        try {
            if (estAVie == null) estAVie = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("estAVie", ItemStack.class);
            return (Boolean) estAVie.invoke(null, s);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static void garderAVie(List<ItemStack> garde, net.minecraft.world.Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (estAVie(s)) garde.add(s.copy());
        }
    }

    /** Vide la planque et le coffre VIP (HeraliZ Utils) ; leurs objets à vie vont dans {@code garde}. */
    private static void viderCoffresPerso(net.minecraft.server.MinecraftServer srv, UUID joueur, List<ItemStack> garde) {
        try {
            Class<?> cp = Class.forName("fr.heralisz.utils.coffres.CoffresPersonnels");
            Class<?> sorte = Class.forName("fr.heralisz.utils.coffres.CoffresPersonnels$Sorte");
            var m = cp.getMethod("coffre", net.minecraft.server.MinecraftServer.class, UUID.class, sorte);
            for (Object so : sorte.getEnumConstants()) {
                net.minecraft.world.Container coffre = (net.minecraft.world.Container) m.invoke(null, srv, joueur, so);
                garderAVie(garde, coffre);
                coffre.clearContent();
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            com.mojang.logging.LogUtils.getLogger().warn("[Quêtes] /wipe : coffres personnels non vidés ({})", e.toString());
        }
    }

    private static void garderAVie(List<ItemStack> garde, List<ItemStack> objets) {
        for (ItemStack s : objets) if (estAVie(s)) garde.add(s.copy());
    }

    /** Appel par réflexion qui rend des objets (liste vide si le mod est absent ou si l'appel échoue). */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> appelObjets(String classe, String methode, Class<?>[] types, Object... args) {
        try {
            Object r = Class.forName(classe).getMethod(methode, types).invoke(null, args);
            return r instanceof List<?> l ? (List<ItemStack>) l : List.of();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            com.mojang.logging.LogUtils.getLogger().warn("[Quêtes] /wipe : {}.{} impossible ({})", classe, methode, e.toString());
            return List.of();
        }
    }

    private static void appel(String classe, String methode, Class<?>[] types, Object... args) {
        try {
            Class.forName(classe).getMethod(methode, types).invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            com.mojang.logging.LogUtils.getLogger().warn("[Quêtes] /wipe : {}.{} impossible ({})", classe, methode, e.toString());
        }
    }

    private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        resetQuetes(p);
        dire(c, "heraliz_quetes.cmd.reset", "Progression des quêtes de %s remise à zéro.", p.getGameProfile().getName());
        return 1;
    }

    private static void resetQuetes(ServerPlayer p) {
        Donnees.DJoueur j = Donnees.de(p.getUUID());
        for (String cle : new ArrayList<>(j.actives.keySet())) Moteur.retirerObjets(p, cle);
        j.actives.clear();
        j.pnj.clear();
        j.finies.clear();
        j.titres.clear();
        j.suivie = "";
        j.contratsFinis = 0;
        Donnees.sale(p.getUUID());
        Moteur.envoyerSuivi(p);
    }
}
