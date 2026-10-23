package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Commandes des Points Boutique : {@code /boutique} (tous), {@code /trade <joueur>} (au spawn), {@code /pb} (son solde),
 * {@code /pb stats} (op niveau 4 connecté sur AuthMe) et, à la console du serveur SEULEMENT (ni joueur, ni op, ni bloc de
 * commande, ni RCON), {@code pb donner|retirer|voir <pseudo> [montant]}.
 */
final class CommandesPb {
    private static final java.util.Map<java.util.UUID, Long> DERNIER_PB = new java.util.concurrent.ConcurrentHashMap<>();
    private CommandesPb() {
    }

    private static Component msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.pb." + cle, fr, args);
    }

    private static String nombre(long v) {
        return String.format(java.util.Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ');
    }

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("boutique").executes(c -> c.getSource().getEntity() instanceof ServerPlayer p ? BoutiqueServeur.ouvrir(p) : 0));

        d.register(Commands.literal("trade")
                .then(Commands.literal("accepter").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> joueur(c) == null ? 0 : EchangeServeur.accepter(joueur(c), EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("refuser").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> joueur(c) == null ? 0 : EchangeServeur.refuser(joueur(c), EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("annuler").executes(c -> joueur(c) == null ? 0 : EchangeServeur.annulerCommande(joueur(c))))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> joueur(c) == null ? 0 : EchangeServeur.demander(joueur(c), EntityArgument.getPlayer(c, "joueur")))));

        d.register(Commands.literal("pb")
                .executes(c -> {
                    ServerPlayer p = joueur(c);
                    if (p == null) return 0;
                    // au plus une demande au site toutes les 5 s par joueur (le site limite tout le serveur, une seule IP)
                    long now = System.currentTimeMillis();
                    Long avant = DERNIER_PB.get(p.getUUID());
                    if (avant != null && now - avant < 5000) {
                        p.sendSystemMessage(msg("attends", "Attends quelques secondes avant de redemander ton solde.").copy().withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    DERNIER_PB.put(p.getUUID(), now);
                    String nom = p.getGameProfile().getName();
                    java.util.UUID u = p.getUUID();
                    Pb.solde(nom, r -> {
                        ServerPlayer j = p.server.getPlayerList().getPlayer(u);
                        if (j == null) return;
                        j.sendSystemMessage(r != null && r.code() == 200
                                ? Component.literal("").append(msg("solde", "Tu as %s PB.", nombre(r.nombre("solde")))).withStyle(ChatFormatting.GOLD)
                                : msg("injoignable", "Le site ne répond pas, réessaie dans un instant.").copy().withStyle(ChatFormatting.RED));
                    });
                    return 1;
                })
                .then(Commands.literal("stats").requires(s -> s.getEntity() instanceof ServerPlayer p && p.hasPermissions(4) && PontBukkit.authentifie(p))
                        .executes(c -> {
                            CommandSourceStack s = c.getSource();
                            Pb.stats(r -> {
                                if (r == null || r.code() != 200) {
                                    s.sendFailure(msg("injoignable", "Le site ne répond pas, réessaie dans un instant."));
                                    return;
                                }
                                s.sendSuccess(() -> Component.literal("— Points Boutique —").withStyle(ChatFormatting.GOLD), false);
                                ligne(s, "circulation", "PB en circulation : %s", r.nombre("circulation"));
                                ligne(s, "depenses", "PB dépensés en boutique : %s", r.nombre("depenses"));
                                ligne(s, "echanges_pb", "PB échangés : %s", r.nombre("echanges_pb"));
                                ligne(s, "brules", "PB détruits par la taxe : %s", r.nombre("brules"));
                                ligne(s, "echanges", "Échanges faits : %s", r.nombre("echanges"));
                                ligne(s, "credits", "PB donnés par la console : %s", r.nombre("credits"));
                            });
                            return 1;
                        }))
                .then(Commands.literal("voir").requires(CommandesGrades::console)
                        .then(Commands.argument("pseudo", StringArgumentType.word()).executes(c -> {
                            CommandSourceStack s = c.getSource();
                            String nom = StringArgumentType.getString(c, "pseudo");
                            Pb.solde(nom, r -> repondre(s, r, "solde " + nom + " : " + (r == null ? "?" : nombre(r.nombre("solde"))) + " PB"));
                            return 1;
                        })))
                .then(mouvement("donner", true))
                .then(mouvement("retirer", false)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mouvement(String mot, boolean donner) {
        return Commands.literal(mot).requires(CommandesGrades::console)
                .then(Commands.argument("pseudo", StringArgumentType.word())
                        .then(Commands.argument("montant", LongArgumentType.longArg(1, 100_000_000L)).executes(c -> {
                            CommandSourceStack s = c.getSource();
                            if (!CommandesGrades.console(s)) return 0;
                            String nom = StringArgumentType.getString(c, "pseudo");
                            long n = LongArgumentType.getLong(c, "montant");
                            String ref = Pb.ref(donner ? "console-don" : "console-ret");
                            Consumer<Pb.Reponse> fin = r -> {
                                repondre(s, r, mot + " " + nombre(n) + " PB à " + nom + " -> solde " + (r == null ? "?" : nombre(r.nombre("solde"))));
                                if (r != null && r.ok()) {
                                    BoutiqueServeur.journal("console " + mot + " " + nom + " " + n + " PB (" + ref + ")");
                                    LienDiscord.evenement("💰 Console : " + mot + " " + n + " PB à **" + nom + "**.");
                                    ServerPlayer p = s.getServer().getPlayerList().getPlayerByName(nom);
                                    if (p != null && donner) p.sendSystemMessage(Component.literal("").append(msg("recu", "Tu as reçu %s PB.", nombre(n))).withStyle(ChatFormatting.GOLD));
                                }
                            };
                            if (donner) Pb.crediter(ref, nom, n, "console", fin);
                            else Pb.debiter(ref, nom, n, "console", fin);
                            return 1;
                        })));
    }

    private static void ligne(CommandSourceStack s, String cle, String fr, long v) {
        s.sendSuccess(() -> msg("stats." + cle, fr, nombre(Math.max(0, v))), false);
    }

    private static void repondre(CommandSourceStack s, Pb.Reponse r, String ok) {
        if (r == null) s.sendFailure(Component.literal("Site injoignable (ou clé des PB absente) : rien n'a été fait."));
        else if (!r.ok() && r.code() != 200) s.sendFailure(Component.literal("Refusé par le site : " + r.raison()));
        else s.sendSuccess(() -> Component.literal(ok), false);
    }

    private static ServerPlayer joueur(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }
}
