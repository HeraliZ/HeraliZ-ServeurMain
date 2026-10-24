package fr.heraliz.airstrike.helico;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.airstrike.AirstrikeConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Commandes du staff (permission 2, comme /airstrike) :
 * <ul><li>{@code /helico lancer [x z]} : un hélicoptère arrive du bord de la carte (ou apparaît au-dessus de x z) ;</li>
 * <li>{@code /helico retirer} : retire l'hélicoptère en vol et toutes les épaves et caisses chargées ;</li>
 * <li>{@code /helico etat} : vie, phase, position, cibles, prochain lancement automatique ;</li>
 * <li>{@code /helico abattre} : le fait tomber tout de suite (essais).</li></ul>
 * Toujours sur la carte (overworld), d'où que la commande soit tapée.
 */
public final class HelicoCommand {
    private HelicoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("helico").requires(s -> s.hasPermission(2))
                .then(Commands.literal("lancer")
                        .executes(c -> lancer(c, null))
                        .then(Commands.argument("pos", Vec2Argument.vec2()).executes(c -> lancer(c, Vec2Argument.getVec2(c, "pos")))))
                .then(Commands.literal("retirer").executes(HelicoCommand::retirer))
                .then(Commands.literal("etat").executes(HelicoCommand::etat))
                .then(Commands.literal("abattre").executes(HelicoCommand::abattre)));
    }

    private static ServerLevel carte(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer().overworld();
    }

    private static int lancer(CommandContext<CommandSourceStack> c, @Nullable Vec2 pos) {
        CommandSourceStack s = c.getSource();
        if (!AirstrikeConfig.HELICO_ACTIF.get()) {
            s.sendFailure(Component.translatableWithFallback("airstrike.helico.cmd.inactif", "Événement désactivé (airstrike-server.toml, [helico] actif)."));
            return 0;
        }
        if (Helicos.actif() != null) {
            s.sendFailure(Component.translatableWithFallback("airstrike.helico.cmd.deja", "Un hélicoptère est déjà en vol (/helico retirer)."));
            return 0;
        }
        HelicoEntityServeur h = Helicos.lancer(carte(c), pos == null ? null : new Vec3(pos.x, 0, pos.y));
        if (h == null) {
            s.sendFailure(Component.translatableWithFallback("airstrike.helico.cmd.impossible", "Lancement impossible."));
            return 0;
        }
        String ou = String.format("%d %d %d", (int) h.getX(), (int) h.getY(), (int) h.getZ());
        s.sendSuccess(() -> Component.translatableWithFallback("airstrike.helico.cmd.lance", "Hélicoptère lancé en %s.", ou), true);
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> c) {
        ServerLevel ow = carte(c);
        int n = Helicos.actif() != null ? 1 : 0;
        Helicos.retirer(ow);
        List<Entity> l = new ArrayList<>();
        for (Entity e : ow.getAllEntities()) {
            if (e instanceof EpaveHelicoEntity || e instanceof CaisseHelicoEntity) l.add(e);
        }
        l.forEach(Entity::discard);
        int total = n + l.size();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("airstrike.helico.cmd.retire",
                "%s entité(s) de l'hélicoptère retirée(s).", String.valueOf(total)), true);
        return total;
    }

    private static int etat(CommandContext<CommandSourceStack> c) {
        ServerLevel ow = carte(c);
        HelicoEntityServeur h = Helicos.actif();
        CommandSourceStack s = c.getSource();
        if (h == null) {
            s.sendSuccess(() -> Component.translatableWithFallback("airstrike.helico.cmd.aucun", "Aucun hélicoptère en vol."), false);
        } else {
            String vie = String.format("%.0f / %.0f", Math.max(0, h.vieRestante()), h.vieMax());
            String ou = String.format("%d %d %d", (int) h.getX(), (int) h.getY(), (int) h.getZ());
            String phase = h.phase(), cibles = String.valueOf(h.agresseurs());
            s.sendSuccess(() -> Component.translatableWithFallback("airstrike.helico.cmd.etat",
                    "Hélicoptère : vie %s, %s, en %s, %s joueur(s) qui l'ont attaqué.", vie, phase, ou, cibles), false);
        }
        long min = Helicos.minutesAvantAuto(ow.getServer());
        String auto = !AirstrikeConfig.HELICO_AUTO.get() ? "-" : String.valueOf(min);
        String joueurs = String.valueOf(Helicos.joueursSurCarte(ow)), mini = String.valueOf(AirstrikeConfig.HELICO_JOUEURS_MIN.get());
        s.sendSuccess(() -> Component.translatableWithFallback("airstrike.helico.cmd.prochain",
                "Prochain lancement automatique dans %s min (%s joueur(s) sur la carte, %s au moins).", auto, joueurs, mini), false);
        return 1;
    }

    private static int abattre(CommandContext<CommandSourceStack> c) {
        HelicoEntityServeur h = Helicos.actif();
        if (h == null) {
            c.getSource().sendFailure(Component.translatableWithFallback("airstrike.helico.cmd.aucun", "Aucun hélicoptère en vol."));
            return 0;
        }
        h.mourir(carte(c), null);
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("airstrike.helico.cmd.abattu", "Hélicoptère abattu (essai)."), true);
        return 1;
    }
}
