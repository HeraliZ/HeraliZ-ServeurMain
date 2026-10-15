package fr.heralisz.bunker.serveur.salle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.KeycardConfig;
import fr.heralisz.bunker.keycard.KeycardRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Commande du staff (op 2) des salles à carte :
 * <ul><li>/salle baguette — donne la baguette (clic gauche = coin 1, clic droit = coin 2) ;</li>
 * <li>/salle coin1 [x y z], /salle coin2 [x y z] — coins par coordonnées (sans coordonnées : le bloc où l'on se tient) ;</li>
 * <li>/salle selection [effacer] — taille de la sélection, ou l'oublier ;</li>
 * <li>/salle creer &lt;nom&gt; [verte|bleue|rouge] — salle = la sélection ; portes à carte trouvées à 3 blocs autour liées,
 * couleur prise d'elles si elle n'est pas donnée ; loot de la salle rempli ;</li>
 * <li>/salle redefinir &lt;nom&gt; — nouvelle boîte = la sélection ;</li>
 * <li>/salle lier|delier &lt;nom&gt; — lie (ou délie) la porte à carte visée (ou celle de son lecteur) ;</li>
 * <li>/salle liste, /salle voir [nom] — état des salles (voir : contour montré 30 s) ;</li>
 * <li>/salle recharger &lt;nom&gt; — loot remis tout de suite ; /salle retirer &lt;nom&gt;.</li></ul>
 * Réponses en message direct à celui qui tape la commande.
 */
final class CommandesSalle {
   private CommandesSalle() {}

   private static final SuggestionProvider<CommandSourceStack> SALLES = (c, b) -> SharedSuggestionProvider.suggest(
      SallesMonde.get(c.getSource().getLevel()).salles.keySet(), b);

   static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("salle").requires(s -> s.hasPermission(2))
         .then(Commands.literal("baguette").executes(c -> baguette(c.getSource())))
         .then(coin("coin1", 1))
         .then(coin("coin2", 2))
         .then(Commands.literal("selection").executes(c -> selection(c.getSource()))
            .then(Commands.literal("effacer").executes(c -> {
               Selections.effacer(c.getSource().getPlayerOrException());
               ok(c.getSource(), Component.translatableWithFallback("heralisz_bunker.salle.selection.effacee", "Sélection effacée"));
               return 1;
            })))
         .then(Commands.literal("creer").then(Commands.argument("nom", StringArgumentType.word())
            .executes(c -> creer(c, null))
            .then(Commands.literal("verte").executes(c -> creer(c, AccessLevel.GREEN)))
            .then(Commands.literal("bleue").executes(c -> creer(c, AccessLevel.BLUE)))
            .then(Commands.literal("rouge").executes(c -> creer(c, AccessLevel.RED)))))
         .then(Commands.literal("redefinir").then(nom().executes(CommandesSalle::redefinir)))
         .then(Commands.literal("lier").then(nom().executes(c -> lier(c, true))))
         .then(Commands.literal("delier").then(nom().executes(c -> lier(c, false))))
         .then(Commands.literal("liste").executes(c -> liste(c.getSource())))
         .then(Commands.literal("voir").executes(c -> voir(c.getSource(), null))
            .then(nom().executes(c -> voir(c.getSource(), StringArgumentType.getString(c, "nom")))))
         .then(Commands.literal("recharger").then(nom().executes(CommandesSalle::recharger)))
         .then(Commands.literal("retirer").then(nom().executes(CommandesSalle::retirer))));
   }

   private static RequiredArgumentBuilder<CommandSourceStack, String> nom() {
      return Commands.argument("nom", StringArgumentType.word()).suggests(SALLES);
   }

   private static LiteralArgumentBuilder<CommandSourceStack> coin(String mot, int n) {
      return Commands.literal(mot)
         .executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Selections.coin(p, BlockPos.containing(p.position()), n);
            return 1;
         })
         .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
            Selections.coin(c.getSource().getPlayerOrException(), BlockPosArgument.getBlockPos(c, "pos"), n);
            return 1;
         }));
   }

   // ------------------------------------------------------------------------------------------ textes
   private static void ok(CommandSourceStack s, Component c) {
      s.sendSuccess(() -> c, false);
   }

   private static void echec(CommandSourceStack s, Component c) {
      s.sendFailure(c);
   }

   static Component couleur(AccessLevel n) {
      String fr = switch (n) {
         case GREEN -> "verte";
         case BLUE -> "bleue";
         case RED -> "rouge";
      };
      return Component.translatableWithFallback("heralisz_bunker.salle.couleur." + n.id, fr).withStyle(n.color);
   }

   static Component duree(long ticks) {
      long s = Math.max(0, ticks) / 20;
      if (s >= 3600) {
         return Component.translatableWithFallback("heralisz_bunker.salle.duree.h", "%1$s h %2$s min",
            String.valueOf(s / 3600), String.format("%02d", (s % 3600) / 60));
      }
      if (s >= 60) {
         return Component.translatableWithFallback("heralisz_bunker.salle.duree.min", "%1$s min %2$s s",
            String.valueOf(s / 60), String.format("%02d", s % 60));
      }
      return Component.translatableWithFallback("heralisz_bunker.salle.duree.s", "%s s", String.valueOf(s));
   }

   private static Component inconnue(String nom) {
      return Component.translatableWithFallback("heralisz_bunker.salle.inconnue", "Salle inconnue : %s (/salle liste)", nom);
   }

   private static Component lootAbsent() {
      return Component.translatableWithFallback("heralisz_bunker.salle.loot.absent",
         "HeraliZ Loot absent : le loot de la salle n'est pas géré (la porte, si)").withStyle(ChatFormatting.GRAY);
   }

   // ------------------------------------------------------------------------------------------ baguette, sélection
   private static int baguette(CommandSourceStack s) throws CommandSyntaxException {
      ServerPlayer p = s.getPlayerOrException();
      ItemStack it = new ItemStack(KeycardRegistry.BAGUETTE_SALLE.get());
      if (!p.getInventory().add(it)) p.drop(it, false);
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.baguette",
         "Baguette des salles : clic gauche = coin 1, clic droit = coin 2, puis /salle creer <nom>").withStyle(ChatFormatting.YELLOW));
      return 1;
   }

   private static int selection(CommandSourceStack s) throws CommandSyntaxException {
      ServerPlayer p = s.getPlayerOrException();
      Selections.Sel sel = Selections.de(p);
      if (sel == null || !sel.complete()) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.selection.incomplete",
            "Sélection incomplète : pose les deux coins (baguette, ou /salle coin1 et /salle coin2)"));
         return 0;
      }
      ok(s, Selections.taille(sel));
      Selections.envoyer(p);
      return 1;
   }

   /** Sélection complète du joueur, vérifiée (taille) ; null après un message d'échec. */
   @Nullable
   private static Selections.Sel selectionValide(CommandSourceStack s, ServerPlayer p) {
      Selections.Sel sel = Selections.de(p);
      if (sel == null || !sel.complete()) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.selection.incomplete",
            "Sélection incomplète : pose les deux coins (baguette, ou /salle coin1 et /salle coin2)"));
         return null;
      }
      BlockPos a = sel.min(), b = sel.max();
      if (b.getX() - a.getX() + 1 > SallesMonde.MAX_LARGEUR || b.getZ() - a.getZ() + 1 > SallesMonde.MAX_LARGEUR
         || b.getY() - a.getY() + 1 > SallesMonde.MAX_HAUTEUR) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.trop_grande", "Salle trop grande (au plus %1$s × %2$s × %1$s blocs)",
            String.valueOf(SallesMonde.MAX_LARGEUR), String.valueOf(SallesMonde.MAX_HAUTEUR)));
         return null;
      }
      return sel;
   }

   /** Une autre salle touche-t-elle cette boîte ? -> son nom, ou null. */
   @Nullable
   private static String chevauche(SallesMonde m, BlockPos a, BlockPos b, @Nullable String sauf) {
      for (SallesMonde.Salle x : m.salles.values()) {
         if (!x.nom.equals(sauf) && x.touche(a, b)) return x.nom;
      }
      return null;
   }

   // ------------------------------------------------------------------------------------------ créer, redessiner
   private static int creer(CommandContext<CommandSourceStack> c, @Nullable AccessLevel couleur) throws CommandSyntaxException {
      CommandSourceStack s = c.getSource();
      ServerPlayer p = s.getPlayerOrException();
      ServerLevel l = p.serverLevel();
      String nom = StringArgumentType.getString(c, "nom");
      SallesMonde m = SallesMonde.get(l);
      if (m.salle(nom) != null) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.existe", "La salle %s existe déjà (/salle redefinir pour changer sa boîte)", nom));
         return 0;
      }
      Selections.Sel sel = selectionValide(s, p);
      if (sel == null) return 0;
      String autre = chevauche(m, sel.min(), sel.max(), null);
      if (autre != null) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.chevauche", "La sélection touche la salle %s", autre));
         return 0;
      }
      Map<BlockPos, AccessLevel> portes = SallesServeur.portesAutour(l, sel.min(), sel.max(), 3);
      AccessLevel niveau = couleur;
      if (niveau == null) {
         java.util.Set<AccessLevel> vues = java.util.EnumSet.noneOf(AccessLevel.class);
         vues.addAll(portes.values());
         if (vues.size() != 1) {
            echec(s, vues.isEmpty()
               ? Component.translatableWithFallback("heralisz_bunker.salle.couleur.aucune",
                  "Aucune porte à carte autour : précise la couleur (/salle creer %s verte|bleue|rouge)", nom)
               : Component.translatableWithFallback("heralisz_bunker.salle.couleur.plusieurs",
                  "Portes de plusieurs couleurs autour : précise la couleur (/salle creer %s verte|bleue|rouge)", nom));
            return 0;
         }
         niveau = vues.iterator().next();
      }
      SallesMonde.Salle x = new SallesMonde.Salle(nom, sel.min(), sel.max(), niveau);
      x.portes.addAll(SallesServeur.portesDeCouleur(portes, niveau));
      x.lootVu = true;
      m.salles.put(nom, x);
      m.setDirty();
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.creee", "Salle %1$s créée : carte %2$s, %3$s × %4$s × %5$s blocs, %6$s porte(s) liée(s)",
         nom, couleur(niveau), String.valueOf(x.largeurX()), String.valueOf(x.hauteur()), String.valueOf(x.largeurZ()),
         String.valueOf(x.portes.size())).withStyle(ChatFormatting.GREEN));
      if (x.portes.isEmpty()) {
         ok(s, Component.translatableWithFallback("heralisz_bunker.salle.sans_porte",
            "Aucune porte à carte de cette couleur à 3 blocs de la salle : regarde la porte (ou son lecteur) et tape /salle lier %s", nom)
            .withStyle(ChatFormatting.GOLD));
      }
      loot(s, l, x);
      Selections.envoyer(p);
      return 1;
   }

   /** Déclare la salle à HeraliZ Loot et la remplit ; dit combien de conteneurs y sont. */
   private static void loot(CommandSourceStack s, ServerLevel l, SallesMonde.Salle x) {
      int n = LootSalles.declarer(l, x);
      if (n < 0) {
         ok(s, lootAbsent());
         return;
      }
      int r = LootSalles.recharger(l, x.nom);
      x.rechargeA = -1;
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.loot.declare",
         "Loot : %1$s conteneur(s) dans la salle, %2$s rempli(s). Il réapparaît %3$s après la sortie du dernier joueur, jamais pendant qu'on est dedans",
         String.valueOf(n), String.valueOf(Math.max(0, r)), duree(KeycardConfig.rechargeTicks(x.niveau))).withStyle(ChatFormatting.GRAY));
   }

   private static int redefinir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack s = c.getSource();
      ServerPlayer p = s.getPlayerOrException();
      ServerLevel l = p.serverLevel();
      String nom = StringArgumentType.getString(c, "nom");
      SallesMonde m = SallesMonde.get(l);
      SallesMonde.Salle x = m.salle(nom);
      if (x == null) {
         echec(s, inconnue(nom));
         return 0;
      }
      Selections.Sel sel = selectionValide(s, p);
      if (sel == null) return 0;
      String autre = chevauche(m, sel.min(), sel.max(), nom);
      if (autre != null) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.chevauche", "La sélection touche la salle %s", autre));
         return 0;
      }
      x.boite(sel.min(), sel.max());
      x.seuils = null;
      for (BlockPos porte : SallesServeur.portesDeCouleur(SallesServeur.portesAutour(l, x.min, x.max, 3), x.niveau)) {
         if (!x.portes.contains(porte)) x.portes.add(porte);
      }
      m.setDirty();
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.redefinie", "Salle %1$s : nouvelle boîte %2$s × %3$s × %4$s blocs, %5$s porte(s) liée(s)",
         nom, String.valueOf(x.largeurX()), String.valueOf(x.hauteur()), String.valueOf(x.largeurZ()), String.valueOf(x.portes.size()))
         .withStyle(ChatFormatting.GREEN));
      loot(s, l, x);
      Selections.envoyer(p);
      return 1;
   }

   // ------------------------------------------------------------------------------------------ portes
   private static int lier(CommandContext<CommandSourceStack> c, boolean lier) throws CommandSyntaxException {
      CommandSourceStack s = c.getSource();
      ServerPlayer p = s.getPlayerOrException();
      ServerLevel l = p.serverLevel();
      String nom = StringArgumentType.getString(c, "nom");
      SallesMonde m = SallesMonde.get(l);
      SallesMonde.Salle x = m.salle(nom);
      if (x == null) {
         echec(s, inconnue(nom));
         return 0;
      }
      BlockPos porte = SallesServeur.porteVisee(p);
      if (porte == null) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.porte.aucune",
            "Regarde une porte à carte (ou son lecteur), à 8 blocs au plus"));
         return 0;
      }
      if (lier) {
         if (!x.portes.contains(porte)) x.portes.add(porte);
      } else if (!x.portes.remove(porte)) {
         echec(s, Component.translatableWithFallback("heralisz_bunker.salle.porte.pas_liee", "Cette porte n'est pas liée à la salle %s", nom));
         return 0;
      }
      x.seuils = null;
      m.setDirty();
      if (!lier && l.getBlockEntity(porte) instanceof com.hbm_m.block.entity.doors.DoorBlockEntity d) d.checkRedstonePower();
      ok(s, Component.translatableWithFallback(lier ? "heralisz_bunker.salle.porte.liee" : "heralisz_bunker.salle.porte.deliee",
         lier ? "Porte en %1$s liée à la salle %2$s (%3$s porte(s))" : "Porte en %1$s déliée de la salle %2$s (%3$s porte(s))",
         Selections.coords(porte), nom, String.valueOf(x.portes.size())).withStyle(ChatFormatting.GREEN));
      return 1;
   }

   // ------------------------------------------------------------------------------------------ état
   private static Component etat(ServerLevel l, SallesMonde.Salle x) {
      long now = l.getGameTime();
      MutableComponent e;
      if (x.occupants > 0) {
         e = Component.translatableWithFallback("heralisz_bunker.salle.etat.occupee", "occupée (%s joueur(s)), loot bloqué",
            String.valueOf(x.occupants)).withStyle(ChatFormatting.RED);
      } else if (x.rechargeA >= 0) {
         e = Component.translatableWithFallback("heralisz_bunker.salle.etat.recharge", "vide, loot dans %s", duree(x.rechargeA - now))
            .withStyle(ChatFormatting.YELLOW);
      } else {
         e = Component.translatableWithFallback("heralisz_bunker.salle.etat.prete", "vide, loot en place").withStyle(ChatFormatting.GREEN);
      }
      int[] lo = LootSalles.etat(l, x.nom);
      if (lo != null) {
         e.append(Component.translatableWithFallback("heralisz_bunker.salle.etat.loot", " ; %1$s/%2$s conteneur(s) remplis (%3$s déjà ouverts)",
            String.valueOf(lo[1]), String.valueOf(lo[0]), String.valueOf(lo[2])).withStyle(ChatFormatting.GRAY));
      }
      if (x.tenue) e.append(Component.translatableWithFallback("heralisz_bunker.salle.etat.ouverte", " ; porte tenue ouverte")
         .withStyle(ChatFormatting.GRAY));
      return e;
   }

   private static int liste(CommandSourceStack s) {
      ServerLevel l = s.getLevel();
      SallesMonde m = SallesMonde.get(l);
      if (m.salles.isEmpty()) {
         ok(s, Component.translatableWithFallback("heralisz_bunker.salle.liste.vide",
            "Aucune salle à carte dans ce monde (/salle baguette pour en délimiter une)"));
         return 0;
      }
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.liste.titre", "%1$s salle(s) à carte (%2$s)",
         String.valueOf(m.salles.size()), l.dimension().location().toString()).withStyle(ChatFormatting.GOLD));
      for (SallesMonde.Salle x : m.salles.values()) {
         MutableComponent ligne = Component.literal(x.nom).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/salle voir " + x.nom)));
         ligne.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY)).append(couleur(x.niveau))
            .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY)).append(etat(l, x));
         ok(s, ligne);
      }
      return m.salles.size();
   }

   private static int voir(CommandSourceStack s, @Nullable String nom) throws CommandSyntaxException {
      ServerPlayer p = s.getPlayerOrException();
      ServerLevel l = p.serverLevel();
      SallesMonde m = SallesMonde.get(l);
      SallesMonde.Salle x = nom != null ? m.salle(nom) : m.salleA(p.getX(), p.getY(), p.getZ());
      if (x == null) {
         echec(s, nom != null ? inconnue(nom) : Component.translatableWithFallback("heralisz_bunker.salle.voir.hors",
            "Tu n'es dans aucune salle : /salle voir <nom>"));
         return 0;
      }
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.voir.titre", "Salle %1$s — carte %2$s", x.nom, couleur(x.niveau))
         .withStyle(ChatFormatting.GOLD));
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.voir.boite", "Boîte : de %1$s à %2$s (%3$s × %4$s × %5$s blocs)",
         Selections.coords(x.min), Selections.coords(x.max), String.valueOf(x.largeurX()), String.valueOf(x.hauteur()),
         String.valueOf(x.largeurZ())));
      List<BlockPos> portes = x.portes;
      MutableComponent lp = Component.translatableWithFallback("heralisz_bunker.salle.voir.portes", "Portes liées : %s", String.valueOf(portes.size()));
      for (BlockPos b : portes) {
         lp.append(" ").append(Component.literal("[" + Selections.coords(b) + "]").withStyle(st -> st.withColor(ChatFormatting.AQUA)
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + b.getX() + " " + (b.getY() + 1) + " " + b.getZ()))));
      }
      ok(s, lp);
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.voir.etat", "État : %s", etat(l, x)));
      Selections.montrer(p, x);
      return 1;
   }

   // ------------------------------------------------------------------------------------------ loot, retrait
   private static int recharger(CommandContext<CommandSourceStack> c) {
      CommandSourceStack s = c.getSource();
      ServerLevel l = s.getLevel();
      String nom = StringArgumentType.getString(c, "nom");
      SallesMonde m = SallesMonde.get(l);
      SallesMonde.Salle x = m.salle(nom);
      if (x == null) {
         echec(s, inconnue(nom));
         return 0;
      }
      if (LootSalles.etat(l, nom) == null) LootSalles.declarer(l, x);
      int r = LootSalles.recharger(l, nom);
      if (r < 0) {
         ok(s, lootAbsent());
         return 0;
      }
      x.rechargeA = -1;
      m.setDirty();
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.rechargee", "Salle %1$s : %2$s conteneur(s) remplis à neuf",
         nom, String.valueOf(r)).withStyle(ChatFormatting.GREEN));
      return r;
   }

   private static int retirer(CommandContext<CommandSourceStack> c) {
      CommandSourceStack s = c.getSource();
      ServerLevel l = s.getLevel();
      String nom = StringArgumentType.getString(c, "nom");
      SallesMonde m = SallesMonde.get(l);
      SallesMonde.Salle x = m.salles.remove(nom);
      if (x == null) {
         echec(s, inconnue(nom));
         return 0;
      }
      m.setDirty();
      x.tenue = false;
      SallesMonde.notifier(l, x);                                   // ses portes reprennent le simple signal du lecteur
      LootSalles.retirer(l, nom);
      ok(s, Component.translatableWithFallback("heralisz_bunker.salle.retiree",
         "Salle %s retirée : ses conteneurs reprennent la rotation habituelle du loot", nom).withStyle(ChatFormatting.GREEN));
      return 1;
   }
}
