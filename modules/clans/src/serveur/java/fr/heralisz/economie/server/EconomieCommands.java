package fr.heralisz.economie.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.economie.EconomieConfig;
import fr.heralisz.economie.api.Compte;
import fr.heralisz.economie.api.EconomieApi;
import fr.heralisz.economie.api.Transaction;
import fr.heralisz.economie.vault.VaultPont;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Commandes : /herals [joueur], /herals payer &lt;joueur&gt; &lt;montant&gt;, /herals classement, et pour les operateurs
 * /eco donner|retirer|definir &lt;joueur&gt; &lt;montant&gt; [motif] (op 4 + AuthMe ou console, connecté ou non, 28/10),
 * /eco journal &lt;joueur&gt; [n], /eco info,
 * /eco recharger, /eco sauvegarder [--forcer]. Tous les textes passent par les cles economie.msg.* (francais d'abord).
 */
public final class EconomieCommands {
    private EconomieCommands() {
    }

    /** Couleur « laiton » des montants dans le chat. */
    private static final Style MONTANT = Style.EMPTY.withColor(TextColor.fromRgb(0xD6B05C));
    private static final Style ATTENUE = Style.EMPTY.withColor(TextColor.fromRgb(0x8F95A0));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Dernier paiement par joueur (ms), pour la cadence de /herals payer. */
    private static final Map<UUID, Long> DERNIER_PAIEMENT = new HashMap<>();

    public static void oublierCadence(UUID u) {
        DERNIER_PAIEMENT.remove(u);
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String s) {
        return Commands.literal(s);
    }

    /** Noms a proposer : joueurs en ligne + derniers noms connus des comptes (ensemble ordonne : O(n) par frappe). */
    private static final SuggestionProvider<CommandSourceStack> NOMS = (ctx, b) -> {
        Set<String> l = new LinkedHashSet<>(ctx.getSource().getOnlinePlayerNames());
        ComptesStore s = ComptesStore.get();
        if (s != null) {
            for (Compte c : s.tous()) {
                if (!c.dernierNom().isEmpty()) l.add(c.dernierNom());
            }
        }
        return SharedSuggestionProvider.suggest(l, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // 25/10 (tri des commandes) : /solde, /payer et /classement deviennent /herals, /herals payer et /herals classement
        d.register(lit("herals")
                .executes(EconomieCommands::soldeMoi)
                .then(lit("payer")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .then(Commands.argument("montant", LongArgumentType.longArg(1)).executes(EconomieCommands::payer))))
                .then(lit("classement").executes(EconomieCommands::classement))
                .then(Commands.argument("joueur", StringArgumentType.word()).requires(s -> s.hasPermission(2)).suggests(NOMS)
                        .executes(EconomieCommands::soldeAutre)));

        // 28/10 : donner / retirer / definir réservés aux op niveau 4 (et à la console), connectés sur AuthMe, un seul joueur
        // à la fois, connecté ou non (compte connu), montant borné, chaque opération notée (journal du compte, journal du
        // serveur, heralisz_economie/journal_admin.log)
        d.register(lit("eco").requires(s -> s.hasPermission(2))
                .then(lit("donner").requires(s -> s.hasPermission(4)).then(Commands.argument("joueur", StringArgumentType.word()).suggests(NOMS)
                        .then(Commands.argument("montant", LongArgumentType.longArg(1, MAX_OPERATION))
                                .executes(ctx -> admin(ctx, Op.DONNER, null))
                                .then(Commands.argument("motif", StringArgumentType.greedyString())
                                        .executes(ctx -> admin(ctx, Op.DONNER, StringArgumentType.getString(ctx, "motif")))))))
                .then(lit("retirer").requires(s -> s.hasPermission(4)).then(Commands.argument("joueur", StringArgumentType.word()).suggests(NOMS)
                        .then(Commands.argument("montant", LongArgumentType.longArg(1, MAX_OPERATION))
                                .executes(ctx -> admin(ctx, Op.RETIRER, null))
                                .then(Commands.argument("motif", StringArgumentType.greedyString())
                                        .executes(ctx -> admin(ctx, Op.RETIRER, StringArgumentType.getString(ctx, "motif")))))))
                .then(lit("definir").requires(s -> s.hasPermission(4)).then(Commands.argument("joueur", StringArgumentType.word()).suggests(NOMS)
                        .then(Commands.argument("montant", LongArgumentType.longArg(0, MAX_OPERATION))
                                .executes(ctx -> admin(ctx, Op.DEFINIR, null))
                                .then(Commands.argument("motif", StringArgumentType.greedyString())
                                        .executes(ctx -> admin(ctx, Op.DEFINIR, StringArgumentType.getString(ctx, "motif")))))))
                .then(lit("journal").then(Commands.argument("joueur", StringArgumentType.word()).suggests(NOMS)
                        .executes(ctx -> journal(ctx, 10))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, Compte.JOURNAL_MAX))
                                .executes(ctx -> journal(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(lit("info").executes(EconomieCommands::info))
                .then(lit("recharger").executes(EconomieCommands::recharger))
                .then(lit("sauvegarder").executes(ctx -> sauvegarder(ctx, false))
                        .then(lit("--forcer").executes(ctx -> sauvegarder(ctx, true)))));
    }

    // ------------------------------------------------------------------ aides
    private static MutableComponent montant(long v) {
        return Component.literal(EconomieApi.format(v)).withStyle(MONTANT);
    }

    private static MutableComponent montantSigne(long v) {
        return Component.literal(EconomieApi.formatSigne(v)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(v >= 0 ? 0x5FD08A : 0xFF6B6B)));
    }

    private static MutableComponent nom(String n) {
        return Component.literal(n).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent msg(String cle, Object... args) {
        return Component.translatable("economie.msg." + cle, args);
    }

    private static int echec(CommandContext<CommandSourceStack> ctx, String cle, Object... args) {
        ctx.getSource().sendFailure(msg(cle, args));
        return 0;
    }

    /**
     * Réponse d'une commande. À un joueur, elle est envoyée directement (message système) : la carte du serveur a la règle
     * sendCommandFeedback à false, qui masquerait un sendSuccess. Les réponses « diffusées » (actions d'admin) vont aussi
     * dans le journal du serveur. Console et blocs de commande : sendSuccess classique.
     */
    private static void repondre(CommandSourceStack src, java.util.function.Supplier<Component> message, boolean diffuser) {
        if (src.getEntity() instanceof ServerPlayer p) {
            Component c = message.get();
            p.sendSystemMessage(c);
            if (diffuser) fr.heralisz.economie.HeraliszEconomie.LOG.info("[economie] {} : {}", p.getGameProfile().getName(), c.getString());
        } else {
            src.sendSuccess(message, diffuser);
        }
    }

    @Nullable
    private static ComptesStore store(CommandContext<CommandSourceStack> ctx) {
        ComptesStore s = ComptesStore.get();
        if (s == null) ctx.getSource().sendFailure(msg("indisponible"));
        return s;
    }

    // ------------------------------------------------------------------ /herals
    private static int soldeMoi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (store(ctx) == null) return 0;
        long v = EconomieApi.solde(p.getUUID());
        repondre(ctx.getSource(), () -> msg("solde_moi", montant(v)), false);
        return 1;
    }

    private static int soldeAutre(CommandContext<CommandSourceStack> ctx) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        String n = StringArgumentType.getString(ctx, "joueur");
        UUID u = s.resoudre(n);
        if (u == null) return echec(ctx, "joueur_introuvable", n);
        long v = EconomieApi.solde(u);
        repondre(ctx.getSource(), () -> msg("solde_autre", nom(s.nomDe(u)), montant(v)), false);
        return 1;
    }

    // ------------------------------------------------------------------ /herals payer
    private static int payer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer de = ctx.getSource().getPlayerOrException();
        ServerPlayer vers = EntityArgument.getPlayer(ctx, "joueur");
        long montant = LongArgumentType.getLong(ctx, "montant");
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        if (de.getUUID().equals(vers.getUUID())) return echec(ctx, "payer_soi");
        long min = EconomieConfig.PAYER_MIN.get();
        if (montant < min) return echec(ctx, "payer_min", montant(min));
        long now = System.currentTimeMillis(), cadence = EconomieConfig.PAYER_CADENCE_S.get() * 1000L;
        Long dernier = DERNIER_PAIEMENT.get(de.getUUID());
        if (dernier != null && now - dernier < cadence) return echec(ctx, "payer_cadence", (cadence - (now - dernier) + 999) / 1000);
        long solde = EconomieApi.solde(de.getUUID());
        if (solde < montant) return echec(ctx, "payer_insuffisant", montant(montant - solde));
        if (EconomieApi.solde(vers.getUUID()) + montant > EconomieApi.maxSolde()) return echec(ctx, "payer_plafond", nom(vers.getGameProfile().getName()));
        if (!EconomieApi.transferer(de.getUUID(), vers.getUUID(), montant, "paiement")) return echec(ctx, "payer_insuffisant", montant(0));
        DERNIER_PAIEMENT.put(de.getUUID(), now);
        String nDe = de.getGameProfile().getName(), nVers = vers.getGameProfile().getName();
        repondre(ctx.getSource(), () -> msg("payer_envoye", montant(montant), nom(nVers)), false);
        vers.sendSystemMessage(msg("payer_recu", nom(nDe), montant(montant)));
        return 1;
    }

    // ------------------------------------------------------------------ /herals classement
    private static int classement(CommandContext<CommandSourceStack> ctx) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        List<Compte> top = s.classement(10);
        if (top.isEmpty()) {
            repondre(ctx.getSource(), () -> msg("classement_vide"), false);
            return 1;
        }
        repondre(ctx.getSource(), () -> msg("classement_titre").withStyle(ChatFormatting.BOLD), false);
        for (int i = 0; i < top.size(); i++) {
            Compte c = top.get(i);
            int rang = i + 1;
            repondre(ctx.getSource(), () -> msg("classement_ligne", Component.literal(Integer.toString(rang)).withStyle(ATTENUE),
                    nom(s.nomDe(c.uuid())), montant(c.solde())), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /eco donner|retirer|definir
    private enum Op { DONNER, RETIRER, DEFINIR }

    /** Plus gros montant d'une opération d'admin (une faute de frappe ne crée pas des milliards). */
    public static final long MAX_OPERATION = 10_000_000L;

    /** Op connecté sur AuthMe (HeraliZ Grades, par réflexion) ; console et RCON : toujours. Grades absent : refusé à un joueur. */
    private static boolean authentifie(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return true;
        try {
            Object r = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("authentifie", ServerPlayer.class).invoke(null, p);
            return Boolean.TRUE.equals(r);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return false;
        }
    }

    private static int admin(CommandContext<CommandSourceStack> ctx, Op op, @Nullable String motif) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        CommandSourceStack src = ctx.getSource();
        if (!authentifie(src)) return echec(ctx, "admin.login");
        String saisi = StringArgumentType.getString(ctx, "joueur");
        long montant = LongArgumentType.getLong(ctx, "montant");
        // joueur connecté (pseudo exact) d'abord, sinon un compte connu (dernier pseudo vu)
        ServerPlayer enLigne = null;
        for (ServerPlayer p : s.server().getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(saisi)) enLigne = p;
        }
        UUID u = enLigne != null ? enLigne.getUUID() : s.resoudre(saisi);
        if (u == null) return echec(ctx, "joueur_introuvable", saisi);
        String n = enLigne != null ? enLigne.getGameProfile().getName() : s.nomDe(u);
        if (enLigne != null) s.compte(u, n);
        String auteur = src.getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "console";
        String libre = motif == null ? "" : motif.replace('\n', ' ').replace('\r', ' ').strip();
        if (libre.length() > 80) libre = libre.substring(0, 80);
        String m = "admin:" + auteur + (libre.isEmpty() ? "" : ":" + libre);
        long avant = EconomieApi.solde(u), max = EconomieApi.maxSolde();
        boolean fait;
        switch (op) {
            case DONNER -> {
                if (avant + montant > max) return echec(ctx, "admin.plafond", nom(n), montant(max));
                EconomieApi.deposer(u, montant, m);
                fait = true;
            }
            case RETIRER -> {
                // jamais de solde négatif : refusé s'il n'a pas assez (rien ne change)
                if (!EconomieApi.retirer(u, montant, m)) return echec(ctx, "admin.retirer_echec", nom(n), montant(avant));
                fait = true;
            }
            default -> {
                if (!EconomieApi.definir(u, montant, m)) return echec(ctx, "admin.definir_echec", montant(max));
                fait = true;
            }
        }
        long apres = EconomieApi.solde(u);
        switch (op) {
            case DONNER -> repondre(src, () -> msg("admin.donner", nom(n), montant(montant), montant(apres)), true);
            case RETIRER -> repondre(src, () -> msg("admin.retirer", nom(n), montant(montant), montant(apres)), true);
            default -> repondre(src, () -> msg("admin.definir", nom(n), montant(apres)), true);
        }
        noterAdmin(s, auteur, op, n, u, montant, avant, apres, libre);
        // prévenu seulement d'un geste du staff (pas des récompenses jouées par la console : caisses, votes)
        if (enLigne != null && apres != avant && src.getEntity() instanceof ServerPlayer) {
            long delta = apres - avant;
            enLigne.sendSystemMessage(Component.translatableWithFallback(delta > 0 ? "economie.msg.admin.recu" : "economie.msg.admin.perdu",
                    delta > 0 ? "Le staff t'a crédité de %s (solde : %s)." : "Le staff t'a retiré %s (solde : %s).",
                    montant(Math.abs(delta)), montant(apres)));
        }
        return fait ? 1 : 0;
    }

    /** Journal des opérations d'admin : une ligne par opération, à côté de comptes.json, jamais réécrit. */
    private static void noterAdmin(ComptesStore s, String auteur, Op op, String nom, UUID u, long montant, long avant, long apres, String motif) {
        String ligne = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(java.time.LocalDateTime.now()) + " | " + auteur + " | "
                + op.name().toLowerCase(java.util.Locale.ROOT) + " | " + nom + " (" + u + ") | " + montant + " | " + avant + " -> " + apres
                + " | " + motif.replace('|', '/');
        fr.heralisz.economie.HeraliszEconomie.LOG.info("[economie] admin : {}", ligne);
        try {
            Path f = s.fichier().toAbsolutePath().getParent().resolve("journal_admin.log");
            java.nio.file.Files.writeString(f, ligne + System.lineSeparator(), java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ex) {
            fr.heralisz.economie.HeraliszEconomie.LOG.warn("[economie] journal_admin.log impossible à écrire : {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ /eco journal
    private static int journal(CommandContext<CommandSourceStack> ctx, int n) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        String nomJ = StringArgumentType.getString(ctx, "joueur");
        UUID u = s.resoudre(nomJ);
        if (u == null) return echec(ctx, "joueur_introuvable", nomJ);
        List<Transaction> l = EconomieApi.journal(u, n);
        String affiche = s.nomDe(u);
        if (l.isEmpty()) {
            repondre(ctx.getSource(), () -> msg("admin.journal_vide", nom(affiche)), false);
            return 1;
        }
        repondre(ctx.getSource(), () -> msg("admin.journal_titre", nom(affiche), l.size()).withStyle(ChatFormatting.BOLD), false);
        ZoneId zone = ZoneId.systemDefault();
        for (Transaction t : l) {
            String date = DATE.format(Instant.ofEpochMilli(t.quand()).atZone(zone));
            String motif = t.motif().isEmpty() ? "—" : t.motif();
            if (t.autre() != null) motif += " (" + s.nomDe(t.autre()) + ")";
            String motifF = motif;
            repondre(ctx.getSource(), () -> msg("admin.journal_ligne", Component.literal(date).withStyle(ATTENUE), montantSigne(t.delta()),
                    montant(t.apres()), Component.literal(motifF).withStyle(ATTENUE)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /eco info | recharger | sauvegarder
    private static int info(CommandContext<CommandSourceStack> ctx) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        long total = 0;
        for (Compte c : s.tous()) total += c.solde();
        long t = total;
        repondre(ctx.getSource(), () -> msg("admin.info", s.tous().size(), montant(t), EconomieApi.nom(false), EconomieApi.nom(true),
                Component.literal(s.fichier().toString()).withStyle(ATTENUE), Component.translatable("economie.msg.admin.vault_" + VaultPont.etat())), false);
        if (s.chargementEchoue()) repondre(ctx.getSource(), () -> msg("admin.info_bloque").withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static int recharger(CommandContext<CommandSourceStack> ctx) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        if (!s.recharger()) return echec(ctx, "admin.recharge_echec");
        for (ServerPlayer p : s.server().getPlayerList().getPlayers()) {
            Compte c = s.compte(p.getUUID(), p.getGameProfile().getName());
            ServerEvents.envoyerTout(p, c.solde());
        }
        repondre(ctx.getSource(), () -> msg("admin.recharge", s.tous().size()), true);
        return 1;
    }

    /**
     * /eco sauvegarder : ecriture synchrone. Si la sauvegarde est bloquee (fichier illisible au demarrage), seul
     * --forcer la leve : le fichier illisible est conserve en comptes.json.corrompu-&lt;date&gt; avant l'ecriture.
     */
    private static int sauvegarder(CommandContext<CommandSourceStack> ctx, boolean forcer) {
        ComptesStore s = store(ctx);
        if (s == null) return 0;
        Path copie = null;
        if (s.chargementEchoue()) {
            if (!forcer) return echec(ctx, "admin.sauvegarde_bloquee");
            try {
                copie = s.leverBlocage();
            } catch (IOException ex) {
                return echec(ctx, "admin.sauvegarde_mise_de_cote_echec", ex.toString());
            }
        }
        if (!s.sauvegarderMaintenant()) return echec(ctx, "admin.sauvegarde_echec");
        if (copie != null) {
            String n = copie.getFileName().toString();
            repondre(ctx.getSource(), () -> msg("admin.sauvegarde_forcee", n), true);
        } else {
            repondre(ctx.getSource(), () -> msg("admin.sauvegarde"), true);
        }
        return 1;
    }
}
