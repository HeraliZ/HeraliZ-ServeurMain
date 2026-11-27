package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Armoires des clans pour les admins (27/11, demande du propriétaire : « une perm pour les admins pour se TP directement
 * à la TC d'un clan, pour retrouver une TC perdue quelque part ») : op niveau 4 seulement, comme {@code /base effacer}.
 * <ul>
 *   <li>{@code /base armoires} : toutes les armoires posées (sauf les cabanes du tutoriel) : [TAG] nom du clan, monde et
 *   coordonnées ; clic sur une ligne = y aller ;</li>
 *   <li>{@code /base armoire <tag ou nom du clan>} : vraie téléportation sur l'armoire de ce clan (sur son dessus).</li>
 * </ul>
 * Lecture seule des claims : rien n'est changé dans la base.
 */
final class ArmoiresStaff {
    private ArmoiresStaff() {
    }

    private record Armoire(Group groupe, ServerLevel monde, BlockPos pos) {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("base")
            .then(Commands.literal("armoires").requires(s -> s.hasPermission(4)).executes(c -> lister(c.getSource())))
            .then(Commands.literal("armoire").requires(s -> s.hasPermission(4))
                .then(Commands.argument("clan", StringArgumentType.greedyString())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(
                        toutes(c.getSource().getServer()).stream().map(a -> nomCourt(a.groupe())).toList(), b))
                    .executes(c -> aller(c.getSource(), StringArgumentType.getString(c, "clan"))))));
    }

    /** Toutes les armoires posées, hors monde du tutoriel. */
    private static List<Armoire> toutes(MinecraftServer server) {
        GroupData groupes = GroupData.get(server);
        List<Armoire> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (MondeBases.estTuto(l)) continue;
            for (Claim c : ClaimData.get(l).all()) {
                Group g = groupes.getById(c.groupId());
                if (g != null) out.add(new Armoire(g, l, c.center()));
            }
        }
        out.sort((a, b) -> nomCourt(a.groupe()).compareToIgnoreCase(nomCourt(b.groupe())));
        return out;
    }

    private static String nomCourt(Group g) {
        return g.tag() == null || g.tag().isEmpty() ? g.name() : g.tag();
    }

    private static int lister(CommandSourceStack src) {
        List<Armoire> l = toutes(src.getServer());
        if (l.isEmpty()) {
            src.sendSuccess(() -> T.t("crac.base.armoires.aucune", "Aucune armoire posée."), false);
            return 0;
        }
        src.sendSuccess(() -> T.t("crac.base.armoires.titre", "Armoires posées : %s (clic pour y aller)", l.size())
            .withStyle(ChatFormatting.GOLD), false);
        for (Armoire a : l) {
            BlockPos p = a.pos();
            String clan = a.groupe().tag() == null || a.groupe().tag().isEmpty() ? a.groupe().name()
                : "[" + a.groupe().tag() + "] " + a.groupe().name();
            MutableComponent ligne = Component.literal(" • " + clan + " — " + a.monde().dimension().location().getPath()
                    + " " + p.getX() + " " + p.getY() + " " + p.getZ())
                .withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/base armoire " + nomCourt(a.groupe())))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        T.t("crac.base.armoires.survol", "Clic : aller à cette armoire"))));
            src.sendSuccess(() -> ligne, false);
        }
        return l.size();
    }

    private static int aller(CommandSourceStack src, String demande) {
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(T.t("crac.base.armoire.joueur", "Seul un joueur en jeu peut s'y téléporter."));
            return 0;
        }
        String d = demande.trim().toLowerCase(Locale.ROOT);
        Armoire trouvee = null;
        for (Armoire a : toutes(src.getServer())) {
            String tag = a.groupe().tag() == null ? "" : a.groupe().tag().toLowerCase(Locale.ROOT);
            String nom = a.groupe().name() == null ? "" : a.groupe().name().toLowerCase(Locale.ROOT);
            if (d.equals(tag) || d.equals(nom)) {
                trouvee = a;
                break;
            }
        }
        if (trouvee == null) {
            src.sendFailure(T.t("crac.base.armoire.inconnue", "Aucune armoire pour le clan « %s » (/base armoires pour la liste).", demande));
            return 0;
        }
        BlockPos pos = trouvee.pos();
        // sur le dessus de l'armoire (2 blocs de haut) : jamais dans un mur de la base
        p.teleportTo(trouvee.monde(), pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        String clan = nomCourt(trouvee.groupe());
        src.sendSuccess(() -> T.t("crac.base.armoire.tp", "Téléporté à l'armoire du clan %s.", clan).withStyle(ChatFormatting.GREEN), true);
        Crac.LOGGER.info("[CRac] {} téléporté à l'armoire du clan {} ({} {})", src.getTextName(), clan,
            trouvee.monde().dimension().location(), pos.toShortString());
        return 1;
    }
}
