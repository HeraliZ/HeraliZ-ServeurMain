package fr.heraliz.noyau.serveur.combat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.noyau.api.Matchs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /combat etat|marquer|effacer <joueur>} (staff, niveau 2) : voir ou changer le tag de combat d'un joueur. */
public final class CommandeCombat {
   private CommandeCombat() {}

   public static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("combat").requires(s -> s.hasPermission(2))
            .then(Commands.literal("etat").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> combat(x, 0))))
            .then(Commands.literal("marquer").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> combat(x, 1))))
            .then(Commands.literal("effacer").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> combat(x, 2)))));
   }

   private static int combat(CommandContext<CommandSourceStack> x, int action) throws CommandSyntaxException {
      ServerPlayer p = EntityArgument.getPlayer(x, "joueur");
      if (action == 1) CombatTag.marquer(p);
      else if (action == 2) CombatTag.effacer(p.getUUID());
      long cle = p.getPersistentData().getLong(CombatTag.CLE_JUSQUA);
      Component etat = CombatTag.estTague(p)
            ? Component.translatableWithFallback("noyau.commande.combat.en_combat", "en combat, %s s restantes",
            String.valueOf((CombatTag.restantMs(p) + 999) / 1000))
            : Matchs.enMatch(p) ? Component.translatableWithFallback("noyau.commande.combat.en_match", "en match (jamais de tag)")
            : Component.translatableWithFallback("noyau.commande.combat.libre", "libre");
      Component valeur = cle == 0 ? Component.translatableWithFallback("noyau.commande.combat.absente", "absente")
            : Component.literal(String.valueOf(cle));
      Component ligne = Component.translatableWithFallback("noyau.commande.combat.ligne", "%s : %s ; %s = %s",
            p.getGameProfile().getName(), etat, CombatTag.CLE_JUSQUA, valeur);
      x.getSource().sendSystemMessage(ligne);
      return CombatTag.estTague(p) ? 1 : 0;
   }
}
