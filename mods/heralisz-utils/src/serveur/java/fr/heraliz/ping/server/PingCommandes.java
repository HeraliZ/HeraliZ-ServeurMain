package fr.heraliz.ping.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.ping.PingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;

/**
 * {@code /ping} : rappel de l'utilisation (tout le monde). {@code /ping admin} (permission 2) : regles et compteurs ;
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
        d.register(Commands.literal("ping").executes(PingCommandes::aide).then(admin));
        // alias : un plugin du serveur (Essentials...) peut deja avoir /ping
        d.register(Commands.literal("pings").executes(PingCommandes::aide).then(admin));
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
