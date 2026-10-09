package fr.heralisz.camo.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.camo.Camo;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /camos} (staff, niveau 2) : collection de camos à vie d'un joueur connecté.
 * <ul>
 *    <li>{@code voir <joueur>} : ses camos et les délais en cours ;</li>
 *    <li>{@code ajouter <joueur> <camo|tous>} : livraison directe (boutique), sans objet ;</li>
 *    <li>{@code retirer <joueur> <camo>} : le camo quitte la collection (et son arme) ;</li>
 *    <li>{@code recharge <joueur>} : efface ses délais après une mort.</li>
 * </ul>
 */
final class CommandeCamos {
   private static final SuggestionProvider<CommandSourceStack> CAMOS = (c, b) -> {
      List<String> ids = new ArrayList<>();
      ids.add("tous");
      for (Camo camo : Camo.values()) {
         if (!camo.isPackAPunch()) ids.add(camo.id);
      }
      return SharedSuggestionProvider.suggest(ids, b);
   };

   private CommandeCamos() {
   }

   static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("camos").requires(s -> s.hasPermission(2))
         .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player())
            .executes(c -> voir(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
         .then(Commands.literal("ajouter").then(Commands.argument("joueur", EntityArgument.player())
            .then(Commands.argument("camo", StringArgumentType.word()).suggests(CAMOS).executes(CommandeCamos::ajouter))))
         .then(Commands.literal("retirer").then(Commands.argument("joueur", EntityArgument.player())
            .then(Commands.argument("camo", StringArgumentType.word()).suggests(CAMOS).executes(CommandeCamos::retirer))))
         .then(Commands.literal("recharge").then(Commands.argument("joueur", EntityArgument.player())
            .executes(c -> recharge(c.getSource(), EntityArgument.getPlayer(c, "joueur"))))));
   }

   private static int voir(CommandSourceStack s, ServerPlayer p) {
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      if (j == null || j.camos.isEmpty()) {
         s.sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " n'a aucun camo à vie.").withStyle(ChatFormatting.GRAY), false);
         return 0;
      }
      long now = System.currentTimeMillis();
      MutableComponent liste = Component.literal(p.getGameProfile().getName() + " : " + j.camos.size() + " camo(s) à vie : ").withStyle(ChatFormatting.GRAY);
      boolean premier = true;
      for (Camo c : j.camos) {
         if (!premier) liste.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
         liste.append(CamosAVie.nom(c));
         long reste = j.recharges.getOrDefault(c, 0L) - now;
         if (reste > 0) liste.append(Component.literal(" (" + CamosAVie.minutes(reste) + " min)").withStyle(ChatFormatting.RED));
         premier = false;
      }
      s.sendSuccess(() -> liste, false);
      return j.camos.size();
   }

   private static int ajouter(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
      String id = StringArgumentType.getString(c, "camo");
      CollectionCamos.Joueur j = CollectionCamos.get(p.getUUID());
      int nouveaux = 0;
      if (id.equals("tous")) {
         for (Camo camo : Camo.values()) {
            if (!camo.isPackAPunch() && j.camos.add(camo)) nouveaux++;
         }
      } else {
         Camo camo = Camo.byId(id);
         if (camo == null || camo.isPackAPunch()) {
            c.getSource().sendFailure(Component.literal("Camo inconnu (ou réservé au mode zombies) : " + id));
            return 0;
         }
         if (j.camos.add(camo)) nouveaux++;
      }
      CollectionCamos.changer();
      CamosAVie.envoyer(p);
      int n = nouveaux;
      c.getSource().sendSuccess(() -> Component.literal(n + " camo(s) ajouté(s) à la collection de " + p.getGameProfile().getName() + ".")
         .withStyle(ChatFormatting.GREEN), true);
      return n;
   }

   private static int retirer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
      String id = StringArgumentType.getString(c, "camo");
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      List<Camo> camos = new ArrayList<>();
      if (id.equals("tous")) {
         if (j != null) camos.addAll(j.camos);
      } else {
         Camo camo = Camo.byId(id);
         if (camo != null) camos.add(camo);
      }
      int n = 0;
      if (j != null) {
         for (Camo camo : camos) {
            if (j.camos.remove(camo)) n++;
            j.jetons.remove(camo);
            j.recharges.remove(camo);
         }
      }
      if (n == 0) {
         c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " n'a pas ce camo : " + id));
         return 0;
      }
      CollectionCamos.changer();
      CamosAVie.verifier(p);
      CamosAVie.envoyer(p);
      int retires = n;
      c.getSource().sendSuccess(() -> Component.literal(retires + " camo(s) retiré(s) de la collection de " + p.getGameProfile().getName() + ".")
         .withStyle(ChatFormatting.YELLOW), true);
      return n;
   }

   private static int recharge(CommandSourceStack s, ServerPlayer p) {
      CollectionCamos.Joueur j = CollectionCamos.trouver(p.getUUID());
      int n = j == null ? 0 : j.recharges.size();
      if (j != null) j.recharges.clear();
      CollectionCamos.changer();
      CamosAVie.envoyer(p);
      s.sendSuccess(() -> Component.literal("Délais effacés pour " + p.getGameProfile().getName() + " (" + n + ").").withStyle(ChatFormatting.GREEN), true);
      return n;
   }
}
