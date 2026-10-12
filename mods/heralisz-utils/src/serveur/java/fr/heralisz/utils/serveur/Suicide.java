package fr.heralisz.utils.serveur;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * /suicide : le joueur meurt tout de suite (coincé, perdu…). Mort normale : son équipement reste sur son cadavre comme
 * pour toute autre mort ; pas de passage « à terre » (dégâts « kill », comme /kill). Refusé en créatif et en spectateur.
 * Les arènes des matchs bloquent déjà la commande (liste de heralisz_matchs).
 */
public final class Suicide {
    private Suicide() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Suicide::commandes);
    }

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("suicide").requires(s -> s.isPlayer()).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                ctx.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.suicide.refus",
                        "Impossible en créatif ou en spectateur."));
                return 0;
            }
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
            return 1;
        }));
    }
}
