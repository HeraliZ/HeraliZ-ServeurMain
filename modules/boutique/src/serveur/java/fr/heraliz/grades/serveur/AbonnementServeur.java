package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.grades.Grade;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /boutique abonnement [joueur]} (2.7.0) : le grade de la boutique en cours, son temps restant (ou « à vie »), puis les
 * grades achetés en réserve, dans l'ordre où ils démarreront (du plus haut au plus bas, comme {@link RegistreGrades#expirer}).
 * Chacun voit le sien ; le staff (Helper et plus, connecté sur AuthMe), les op de niveau 4 connectés et la console voient
 * celui des autres. Aussi {@code /staff panel} : le panneau admin des op (op 4 connecté sur AuthMe), ouvert chez le joueur.
 */
final class AbonnementServeur {
    private AbonnementServeur() {
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.abonnement." + cle, fr, args);
    }

    private static boolean voitLesAutres(CommandSourceStack s) {
        if (CommandesGrades.console(s) || CommandesGrades.staff(s, Grade.HELPER)) return true;
        return s.getEntity() instanceof ServerPlayer p && p.hasPermissions(4) && PontBukkit.authentifie(p);
    }

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        // 25/10 (tri des commandes) : /abonnement devient /boutique abonnement, /panel devient /staff panel
        d.register(Commands.literal("boutique").then(Commands.literal("abonnement")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return afficher(c.getSource(), p.getUUID(), p.getGameProfile().getName());
                })
                .then(Commands.argument("joueur", StringArgumentType.word()).requires(AbonnementServeur::voitLesAutres)
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b))
                        .executes(AbonnementServeur::autre))));
        // /staff panel : le panel du staff (même chose que la touche P), pour tout le staff ; droits revérifiés par PanelStaff
        d.register(Commands.literal("staff").requires(VanishServeur::racineStaff).then(Commands.literal("panel")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PanelStaff.ouvrir(p);
                    return 1;
                })));
    }

    private static int autre(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        if (!voitLesAutres(s)) return 0;
        String nom = StringArgumentType.getString(c, "joueur");
        ServerPlayer p = s.getServer().getPlayerList().getPlayerByName(nom);
        if (p != null) return afficher(s, p.getUUID(), p.getGameProfile().getName());
        RegistreGrades r = ServeurGrades.registre;
        if (r != null) {
            for (Map.Entry<UUID, RegistreGrades.Fiche> e : r.tous()) {
                if (e.getValue().nom().equalsIgnoreCase(nom)) return afficher(s, e.getKey(), e.getValue().nom());
            }
        }
        s.sendFailure(msg("inconnu", "Joueur introuvable : %s (il doit être connecté, ou avoir déjà eu un grade).", nom));
        return 0;
    }

    /** « 12 j 4 h 30 min », « 3 h 05 min », « 12 min » (moins d'une minute : « moins d'une minute »). */
    static Component duree(long ms) {
        long min = Math.max(0, ms) / 60_000L, h = min / 60, j = h / 24;
        if (j > 0) return msg("duree_jhm", "%1$s j %2$s h %3$s min", String.valueOf(j), String.valueOf(h % 24), String.valueOf(min % 60));
        if (h > 0) return msg("duree_hm", "%1$s h %2$s min", String.valueOf(h), String.valueOf(min % 60));
        if (min > 0) return msg("duree_m", "%s min", String.valueOf(min));
        return msg("duree_moins", "moins d'une minute");
    }

    private static int afficher(CommandSourceStack s, UUID uuid, String pseudo) {
        RegistreGrades r = ServeurGrades.registre;
        if (r == null || r.illisible()) {
            s.sendFailure(msg("illisible", "Le fichier des grades est illisible : rien à afficher (voir la console du serveur)."));
            return 0;
        }
        RegistreGrades.Fiche f = r.fiche(uuid);
        long now = System.currentTimeMillis();
        List<Component> lignes = new ArrayList<>();
        lignes.add(msg("titre", "Abonnement de %s", pseudo).withStyle(ChatFormatting.GOLD));
        if (f == null || f.boutique() == null) {
            lignes.add(msg("aucun", "Aucun grade de la boutique en cours.").withStyle(ChatFormatting.GRAY));
        } else if (f.finBoutique() == 0) {
            lignes.add(msg("actuel_vie", "En cours : %s, à vie.", f.boutique().nom()).withStyle(ChatFormatting.WHITE));
        } else {
            lignes.add(msg("actuel", "En cours : %1$s, encore %2$s.", f.boutique().nom(), duree(f.finBoutique() - now)).withStyle(ChatFormatting.WHITE));
        }
        List<Map.Entry<Grade, Long>> reserve = f == null ? List.of() : RegistreGrades.ordreReserve(f.reserve());
        if (reserve.isEmpty()) {
            lignes.add(msg("reserve_vide", "Rien en réserve.").withStyle(ChatFormatting.GRAY));
        } else {
            lignes.add(msg("reserve", "Ensuite, dans cet ordre :").withStyle(ChatFormatting.GRAY));
            int i = 1;
            for (Map.Entry<Grade, Long> e : reserve) {
                Component d = e.getValue() >= RegistreGrades.RESERVE_VIE ? msg("vie", "à vie") : duree(e.getValue());
                lignes.add(msg("ligne", "%1$s. %2$s (%3$s)", String.valueOf(i++), e.getKey().nom(), d).withStyle(ChatFormatting.WHITE));
            }
        }
        for (Component l : lignes) s.sendSuccess(() -> l, false);
        return 1;
    }
}
