package fr.heraliz.usure;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Profil;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;


/**
 * Commandes d'administration (op 2), sur l'arme tenue en main par le joueur vise :
 * <ul>
 *   <li>{@code /usure voir <joueur>} : categorie, etat, etat maximal, reparations, usure par tir ;</li>
 *   <li>{@code /usure reparer <joueur>} : remise a neuf (100 %, maximum rendu) ;</li>
 *   <li>{@code /usure definir <joueur> <etat %> [max %]} : regle l'etat (et l'etat maximal) ;</li>
 *   <li>{@code /usure config} : ouvre l'ecran des reglages.</li>
 * </ul>
 * Reponses en message systeme : {@code sendCommandFeedback} est coupe sur le serveur.
 */
public final class Commandes {
    private Commandes() {
    }

    static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("usure").requires(s -> s.hasPermission(2))
                .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player()).executes(Commandes::voir)))
                .then(Commands.literal("reparer").then(Commands.argument("joueur", EntityArgument.player()).executes(Commandes::reparer)))
                .then(Commands.literal("definir").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("etat", FloatArgumentType.floatArg(0, 100)).executes(c -> definir(c, false))
                                .then(Commands.argument("max", FloatArgumentType.floatArg(1, 100)).executes(c -> definir(c, true))))))
                .then(Commands.literal("config").executes(Commandes::config)));
    }

    /** Arme tenue par le joueur vise, ou null (message deja envoye). */
    private static ItemStack arme(CommandContext<CommandSourceStack> c, ServerPlayer cible) {
        ItemStack s = cible.getMainHandItem();
        if (Usure.gunId(s) == null) {
            repondre(c.getSource(), Component.translatable("heraliz_usure.cmd.pas_d_arme", cible.getDisplayName()).withStyle(ChatFormatting.RED));
            return null;
        }
        return s;
    }

    private static int voir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer cible = EntityArgument.getPlayer(c, "joueur");
        ItemStack s = arme(c, cible);
        if (s == null) return 0;
        Regles r = ReglesServeur.get();
        String id = Usure.gunId(s);
        Profil p = r.profil(id);
        if (Usure.migrer(s, p)) {
            cible.getInventory().setChanged();
            repondre(c.getSource(), Component.translatable("heraliz_usure.cmd.migree").withStyle(ChatFormatting.AQUA));
        }
        CommandSourceStack src = c.getSource();
        repondre(src, Component.translatable("heraliz_usure.cmd.voir.titre", s.getHoverName(), cible.getDisplayName()).withStyle(ChatFormatting.GOLD));
        repondre(src, gris(Component.translatable("heraliz_usure.cmd.voir.id", id)));
        if (!p.suivie) {
            repondre(src, gris(Component.translatable(p.exclue ? "heraliz_usure.cmd.voir.exclue" : "heraliz_usure.cmd.voir.sans_usure",
                    Component.translatable(p.categorie.cleTraduction()))));
            return 1;
        }
        int etat = Usure.etat(s, p), max = Usure.max(s);
        Etat e = Etat.de(etat, r);
        repondre(src, gris(Component.translatable("heraliz_usure.cmd.voir.categorie", Component.translatable(p.categorie.cleTraduction()),
                Regles.texte(p.usurePct), p.tirsPourRuiner())));
        repondre(src, gris(Component.translatable("heraliz_usure.cmd.voir.etat", Component.translatable(e.cleTraduction())
                .withStyle(st -> st.withColor(e.couleur)), Reparation.pct(etat), Reparation.pct(max), Usure.reparations(s))));
        if (Tirs.exemptee(cible, s, r)) repondre(src, gris(Component.translatable("heraliz_usure.cmd.voir.exemptee")));
        return 1;
    }

    private static int reparer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer cible = EntityArgument.getPlayer(c, "joueur");
        ItemStack s = arme(c, cible);
        if (s == null) return 0;
        Usure.migrer(s, ReglesServeur.get().profil(Usure.gunId(s)));
        CompoundTag t = s.getTag();
        if (t != null) {
            t.remove(Usure.ETAT);
            t.remove(Usure.MAX);
            t.remove(Usure.REPARATIONS);
        }
        cible.getInventory().setChanged();
        repondre(c.getSource(), Component.translatable("heraliz_usure.cmd.reparee", s.getHoverName(), cible.getDisplayName()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int definir(CommandContext<CommandSourceStack> c, boolean avecMax) throws CommandSyntaxException {
        ServerPlayer cible = EntityArgument.getPlayer(c, "joueur");
        ItemStack s = arme(c, cible);
        if (s == null) return 0;
        Usure.migrer(s, ReglesServeur.get().profil(Usure.gunId(s)));
        int etat = Math.round(FloatArgumentType.getFloat(c, "etat") * 10f);
        int max = avecMax ? Math.round(FloatArgumentType.getFloat(c, "max") * 10f) : Math.max(Usure.max(s), etat);
        etat = Math.min(etat, max);
        Usure.ecrireMax(s, max);
        Usure.ecrireEtat(s, etat);
        cible.getInventory().setChanged();
        repondre(c.getSource(), Component.translatable("heraliz_usure.cmd.definie", s.getHoverName(), Reparation.pct(etat), Reparation.pct(max))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int config(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) {
            repondre(c.getSource(), Component.translatable("heraliz_usure.cmd.joueur_seulement").withStyle(ChatFormatting.RED));
            return 0;
        }
        Reseau.envoyer(p, new Reseau.SyncRegles(ReglesServeur.get(), true));
        return 1;
    }

    private static MutableComponent gris(MutableComponent c) {
        return c.withStyle(ChatFormatting.GRAY);
    }

    /** Joueur : message systeme (toujours affiche) ; console / bloc de commande : retour classique. */
    static void repondre(CommandSourceStack src, Component msg) {
        if (src.getEntity() instanceof ServerPlayer p) p.sendSystemMessage(msg);
        else src.sendSuccess(() -> msg, false);
    }
}
