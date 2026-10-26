package fr.heraliz.carte.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heraliz.carte.Lieu;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Lieux nommés de la carte du monde, posés EN JEU par le staff (niveau 2) à l'endroit où il se tient :
 * <ul>
 *     <li>{@code /carte lieu ajouter <type> <nom>} (types : ville, village, aerodrome, militaire, centrale, usine, port,
 *     hopital, gare, point) ; ailleurs : {@code /execute positioned X Y Z run carte lieu ajouter ...} ;</li>
 *     <li>{@code /carte lieu retirer <nom>}, {@code /carte lieu liste} ;</li>
 *     <li>{@code /carte nom <nom>} : nom de la carte (sous-titre « Monde · ... » de l'écran).</li>
 * </ul>
 * Réponses en message direct, traduites chez le joueur (français en secours, et à la console).
 */
public final class CarteCommandes {
    private CarteCommandes() {
    }

    private static final SuggestionProvider<CommandSourceStack> TYPES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Lieu.Type.values()).map(t -> t.id), b);
    private static final SuggestionProvider<CommandSourceStack> NOMS = (ctx, b) ->
            SharedSuggestionProvider.suggest(LieuxData.de(ctx.getSource().getServer()).lieux().stream().map(Lieu::nom), b);

    public static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("carte").requires(s -> s.hasPermission(2))
                .executes(CarteCommandes::aide)
                .then(Commands.literal("lieu")
                        .then(Commands.literal("ajouter")
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                        .then(Commands.argument("nom", StringArgumentType.greedyString()).executes(CarteCommandes::ajouter))))
                        .then(Commands.literal("retirer")
                                .then(Commands.argument("nom", StringArgumentType.greedyString()).suggests(NOMS).executes(CarteCommandes::retirer)))
                        .then(Commands.literal("liste").executes(CarteCommandes::liste)))
                .then(Commands.literal("nom")
                        .then(Commands.argument("nom", StringArgumentType.greedyString()).executes(CarteCommandes::nommer))));
    }

    private static int aide(CommandContext<CommandSourceStack> ctx) {
        repondre(ctx.getSource(), tr("heraliz_carte.cmd.aide",
                "/carte lieu ajouter <type> <nom> · /carte lieu retirer <nom> · /carte lieu liste · /carte nom <nom>").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int ajouter(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "type");
        Lieu.Type type = Lieu.Type.parId(typeId);
        if (type == null) {
            String types = Arrays.stream(Lieu.Type.values()).map(t -> t.id).collect(Collectors.joining(", "));
            repondre(s, tr("heraliz_carte.cmd.type_inconnu", "Type inconnu : %s (types : %s).", typeId, types).withStyle(ChatFormatting.RED));
            return 0;
        }
        String nom = Lieu.nettoyer(StringArgumentType.getString(ctx, "nom"));
        if (nom.isEmpty()) {
            repondre(s, tr("heraliz_carte.cmd.nom_vide", "Nom vide ou trop long (%s caractères au plus).", String.valueOf(Lieu.NOM_MAX))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        LieuxData data = LieuxData.de(s.getServer());
        if (data.trouver(nom) != null) {
            repondre(s, tr("heraliz_carte.cmd.existe", "Un lieu porte déjà ce nom : %s.", nom).withStyle(ChatFormatting.RED));
            return 0;
        }
        Vec3 pos = s.getPosition();
        Lieu l = new Lieu(nom, type, s.getLevel().dimension().location(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (!data.ajouter(l)) {
            repondre(s, tr("heraliz_carte.cmd.plein", "Trop de lieux (%s au plus).", String.valueOf(LieuxData.MAX)).withStyle(ChatFormatting.RED));
            return 0;
        }
        data.diffuser(s.getServer());
        repondre(s, tr("heraliz_carte.cmd.ajoute", "Lieu « %s » (%s) ajouté en %s, %s.", nom, Component.translatable(type.cle()),
                String.valueOf(l.x()), String.valueOf(l.z())).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        String nom = StringArgumentType.getString(ctx, "nom");
        LieuxData data = LieuxData.de(s.getServer());
        Lieu l = data.trouver(nom);
        if (l == null || !data.retirer(l)) {
            repondre(s, tr("heraliz_carte.cmd.inconnu", "Aucun lieu ne s'appelle « %s ».", nom).withStyle(ChatFormatting.RED));
            return 0;
        }
        data.diffuser(s.getServer());
        repondre(s, tr("heraliz_carte.cmd.retire", "Lieu « %s » retiré.", l.nom()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        LieuxData data = LieuxData.de(s.getServer());
        if (data.lieux().isEmpty()) {
            repondre(s, tr("heraliz_carte.cmd.liste.vide", "Aucun lieu : /carte lieu ajouter <type> <nom> à l'endroit voulu.")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }
        repondre(s, tr("heraliz_carte.cmd.liste.titre", "Lieux de la carte « %s » (%s) :", data.nom(), String.valueOf(data.lieux().size()))
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        for (Lieu l : data.lieux()) {
            repondre(s, Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY).append(tr("heraliz_carte.cmd.liste.ligne", "%s · %s · %s, %s · %s",
                    l.nom(), Component.translatable(l.type().cle()), String.valueOf(l.x()), String.valueOf(l.z()), l.dimension().toString())
                    .withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int nommer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        String nom = Lieu.nettoyer(StringArgumentType.getString(ctx, "nom"));
        if (nom.isEmpty()) {
            repondre(s, tr("heraliz_carte.cmd.nom_vide", "Nom vide ou trop long (%s caractères au plus).", String.valueOf(Lieu.NOM_MAX))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        LieuxData data = LieuxData.de(s.getServer());
        data.nommer(nom);
        data.diffuser(s.getServer());
        repondre(s, tr("heraliz_carte.cmd.nom", "Nom de la carte : %s.", nom).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static MutableComponent tr(String cle, String francais, Object... args) {
        return Component.translatableWithFallback(cle, francais, args);
    }

    /** Réponse directe : au joueur s'il y en a un, sinon à la console. */
    private static void repondre(CommandSourceStack s, Component c) {
        ServerPlayer p = s.getPlayer();
        if (p != null) p.sendSystemMessage(c);
        else s.getServer().sendSystemMessage(c);
    }
}
