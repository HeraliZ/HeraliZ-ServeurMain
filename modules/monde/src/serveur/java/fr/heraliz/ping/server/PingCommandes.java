package fr.heraliz.ping.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.ping.PingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /ping} : rappel de l'utilisation (tout le monde). {@code /ping canal [nom|clan]} : canal des pings du joueur
 * ({@link Canaux}). {@code /ping admin} (permission 2) : regles et compteurs ;
 * {@code /ping admin <reglage> <valeur>} : change une regle du serveur, sauvegardee tout de suite et renvoyee aux
 * clients. Le monde a {@code sendCommandFeedback=false} : les reponses vont directement au joueur
 * ({@link ServerPlayer#sendSystemMessage}), jamais par sendSuccess.
 */
public final class PingCommandes {
    private PingCommandes() {
    }

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin").requires(s -> s.hasPermission(2))
                .executes(PingCommandes::info)
                .then(entier("duree", PingConfig.DUREE_S, 3, 60))
                .then(entier("portee", PingConfig.PORTEE, 16, 1024))
                .then(entier("rafale", PingConfig.RAFALE, 1, 20))
                .then(entier("recharge", PingConfig.RECHARGE_MS, 100, 10000))
                .then(entier("max", PingConfig.MAX_PAR_JOUEUR, 1, 10))
                .then(booleen("vue", PingConfig.VERIFIER_VUE))
                .then(booleen("spectateurs", PingConfig.SPECTATEURS_VOIENT))
                .then(booleen("groupe", PingConfig.GROUPE_HORS_MATCH))
                .then(booleen("carte", PingConfig.CARTE))
                .then(booleen("actif", PingConfig.ACTIF));
        // 25/10 (tri des commandes) : plus d'alias /pings (aucun plugin n'a /ping sur HeraliZ)
        // 25/10 : canaux de pings (Canaux) ; joueurs seulement, sans permission
        LiteralArgumentBuilder<CommandSourceStack> canal = Commands.literal("canal").requires(CommandSourceStack::isPlayer)
                .executes(PingCommandes::voirCanal)
                .then(Commands.literal(Canaux.CLAN).executes(ctx -> changerCanal(ctx, null)))
                .then(Commands.argument("nom", StringArgumentType.word()).executes(ctx -> changerCanal(ctx, StringArgumentType.getString(ctx, "nom"))));
        d.register(Commands.literal("ping").executes(PingCommandes::aide).then(canal).then(admin));
    }

    /** Joueur -> ms du dernier changement de canal (anti-spam des annonces d'arrivee et de depart). */
    private static final Map<UUID, Long> DERNIER_CHANGEMENT = new HashMap<>();
    private static final long CHANGEMENT_ECART_MS = 3000L;
    /** Liste reutilisee pour lire les membres connectes d'un canal (fil serveur). */
    private static final ArrayList<ServerPlayer> MEMBRES = new ArrayList<>();

    /** {@code /ping canal} : le canal du joueur et qui y est connecte. */
    private static int voirCanal(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        String canal = Canaux.de(p.getUUID());
        if (canal != null) {
            MEMBRES.clear();
            Canaux.enLigne(canal, p.server, MEMBRES);
            StringBuilder noms = new StringBuilder();
            for (int i = 0; i < MEMBRES.size() && i < 12; i++) noms.append(i == 0 ? "" : ", ").append(MEMBRES.get(i).getGameProfile().getName());
            if (MEMBRES.size() > 12) noms.append(", …");
            int n = MEMBRES.size();
            MEMBRES.clear();
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.voir.canal",
                    "Ton canal de pings : « %s » (%s en ligne : %s). /ping canal clan pour revenir à ton clan.", canal,
                    String.valueOf(n), noms.toString()).withStyle(ChatFormatting.GRAY));
            return 1;
        }
        String tag = Compat.clans() ? ClansCompat.tag(p) : null;
        if (tag != null) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.voir.clan",
                    "Ton canal de pings : ton clan %s. /ping canal <nom> pour un canal à part.", tag).withStyle(ChatFormatting.GRAY));
        } else {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.voir.seul",
                    "Ton canal de pings : aucun (sans clan, tes pings ne sont vus que par toi). /ping canal <nom> pour rejoindre un canal.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    /** {@code /ping canal <nom>} ; nom null : {@code /ping canal clan}, retour au canal du clan. */
    private static int changerCanal(CommandContext<CommandSourceStack> ctx, @Nullable String saisi) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        String canal = null;
        if (saisi != null) {
            canal = Canaux.nettoyer(saisi);
            if (canal == null) {
                p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.nom",
                        "Nom de canal invalide : 2 à 16 lettres ou chiffres, sans espace (« clan » est réservé).").withStyle(ChatFormatting.RED));
                return 0;
            }
        }
        String avant = Canaux.de(p.getUUID());
        if (canal == null ? avant == null : canal.equals(avant)) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.deja", "Tu es déjà sur ce canal.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        long now = Util.getMillis();
        Long dernier = DERNIER_CHANGEMENT.get(p.getUUID());
        if (dernier != null && now - dernier < CHANGEMENT_ECART_MS) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.attente", "Attends un peu avant de changer de canal.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (canal != null && Canaux.compterEnLigne(canal, p.server) >= Canaux.MAX_EN_LIGNE) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.plein", "Ce canal a déjà %s joueurs connectés.",
                    String.valueOf(Canaux.MAX_EN_LIGNE)).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (DERNIER_CHANGEMENT.size() > 512) DERNIER_CHANGEMENT.values().removeIf(t -> now - t >= CHANGEMENT_ECART_MS);
        DERNIER_CHANGEMENT.put(p.getUUID(), now);
        String nom = p.getGameProfile().getName();
        // ceux qui restent sur l'ancien canal sont prevenus du depart, ceux du nouveau de l'arrivee : personne n'ecoute
        // un canal sans que ses membres le sachent
        if (avant != null) annoncer(avant, p, Component.translatableWithFallback("heraliz_ping.canal.depart",
                "%s a quitté ton canal de pings.", nom));
        Canaux.definir(p.getUUID(), canal);
        PingService.retirerDestinataire(p);
        if (canal != null) {
            annoncer(canal, p, Component.translatableWithFallback("heraliz_ping.canal.arrivee", "%s a rejoint ton canal de pings.", nom));
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_ping.canal.rejoint",
                    "Canal de pings : « %s ». Seuls les joueurs de ce canal voient tes pings, et toi les leurs (%s en ligne).",
                    canal, String.valueOf(Canaux.compterEnLigne(canal, p.server))).withStyle(ChatFormatting.GREEN));
        } else {
            String tag = Compat.clans() ? ClansCompat.tag(p) : null;
            MutableComponent c = tag != null
                    ? Component.translatableWithFallback("heraliz_ping.canal.retour_clan", "Canal de pings : retour à ton clan %s.", tag)
                    : Component.translatableWithFallback("heraliz_ping.canal.retour_seul",
                    "Canal de pings : retour au canal du clan (sans clan, tes pings ne sont vus que par toi).");
            p.sendSystemMessage(c.withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    /** Message gris aux membres connectes d'un canal, sauf {@code sauf}. */
    private static void annoncer(String canal, ServerPlayer sauf, MutableComponent c) {
        MEMBRES.clear();
        Canaux.enLigne(canal, sauf.server, MEMBRES);
        Component gris = c.withStyle(ChatFormatting.GRAY);
        for (ServerPlayer d : MEMBRES) if (d != sauf) d.sendSystemMessage(gris);
        MEMBRES.clear();
    }

    /** Arret du serveur. */
    static void vider() {
        DERNIER_CHANGEMENT.clear();
        MEMBRES.clear();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> entier(String nom, ForgeConfigSpec.IntValue v, int min, int max) {
        return Commands.literal(nom).then(Commands.argument("valeur", IntegerArgumentType.integer(min, max)).executes(ctx -> {
            int val = IntegerArgumentType.getInteger(ctx, "valeur");
            v.set(val);
            v.save();
            return change(ctx, nom, String.valueOf(val));
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> booleen(String nom, ForgeConfigSpec.BooleanValue v) {
        return Commands.literal(nom).then(Commands.argument("valeur", BoolArgumentType.bool()).executes(ctx -> {
            boolean val = BoolArgumentType.getBool(ctx, "valeur");
            v.set(val);
            v.save();
            return change(ctx, nom, oui(val));
        }));
    }

    private static int change(CommandContext<CommandSourceStack> ctx, String nom, Object valeur) {
        PingService.diffuserReglages(ctx.getSource().getServer());
        repondre(ctx.getSource(), Component.translatable("heraliz_ping.cmd.change", nom, valeur).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int aide(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        repondre(s, titre("heraliz_ping.cmd.aide.titre"));
        repondre(s, ligne("heraliz_ping.cmd.aide.bref"));
        repondre(s, ligne("heraliz_ping.cmd.aide.roue"));
        repondre(s, ligne("heraliz_ping.cmd.aide.annuler"));
        repondre(s, ligne("heraliz_ping.cmd.aide.qui"));
        repondre(s, ligne("heraliz_ping.cmd.aide.canal"));
        repondre(s, ligne("heraliz_ping.cmd.aide.touche"));
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        repondre(s, titre("heraliz_ping.cmd.admin.titre"));
        repondre(s, Component.translatable("heraliz_ping.cmd.admin.regles", oui(PingConfig.ACTIF.get()), PingConfig.DUREE_S.get(),
                PingConfig.PORTEE.get(), oui(PingConfig.VERIFIER_VUE.get()), PingConfig.RAFALE.get(), PingConfig.RECHARGE_MS.get(),
                PingConfig.MAX_PAR_JOUEUR.get(), oui(PingConfig.SPECTATEURS_VOIENT.get()), oui(PingConfig.GROUPE_HORS_MATCH.get()))
                .withStyle(ChatFormatting.GRAY));
        double moyenneUs = PingService.relayes == 0 ? 0 : PingService.nanosAcceptes / 1000.0 / PingService.relayes;
        repondre(s, Component.translatable("heraliz_ping.cmd.admin.stats", PingService.recus, PingService.relayes, PingService.paquets,
                PingService.actifs(), String.format(Locale.ROOT, "%.1f", moyenneUs)).withStyle(ChatFormatting.GRAY));
        repondre(s, Component.translatable("heraliz_ping.cmd.admin.refus", PingService.refusCadence, PingService.refusPortee,
                PingService.refusVue, PingService.refusCible, PingService.refusSpectateur).withStyle(ChatFormatting.GRAY));
        repondre(s, Component.translatable("heraliz_ping.cmd.admin.liens", oui(Compat.clans()), oui(Compat.matchs())).withStyle(ChatFormatting.GRAY));
        repondre(s, Component.translatable("heraliz_ping.cmd.admin.usage").withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    /** « oui » / « non » traduits chez le joueur (le serveur ne connait que l'anglais). */
    private static MutableComponent oui(boolean b) {
        return Component.translatable(b ? "heraliz_ping.oui" : "heraliz_ping.non");
    }

    private static MutableComponent titre(String cle) {
        return Component.translatable(cle).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    private static MutableComponent ligne(String cle) {
        return Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY).append(Component.translatable(cle).withStyle(ChatFormatting.GRAY));
    }

    /** Reponse directe : au joueur s'il y en a un, sinon a la console. */
    private static void repondre(CommandSourceStack s, Component c) {
        ServerPlayer p = s.getPlayer();
        if (p != null) p.sendSystemMessage(c);
        else s.getServer().sendSystemMessage(c);
    }
}
