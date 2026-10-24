package fr.heralisz.utils.serveur;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * /suicide : le joueur meurt tout de suite (coincé, perdu…). Mort normale : son équipement reste sur son cadavre comme
 * pour toute autre mort ; pas de passage « à terre » (dégâts « kill », comme /kill). Refusé en créatif et en spectateur.
 * Refusé dans le monde du spawn protégé
 * (ProtectionSpawn), sauf pour le staff. Refusé en combat (tag de combat de Utils, clé {@code heralisz:combat_jusqua}
 * des données du joueur) et à terre (Réanimation, par réflexion) : pas de mort instantanée pour échapper à la fouille.
 */
public final class Suicide {
    private Suicide() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Suicide::commandes);
        MinecraftForge.EVENT_BUS.addListener(Suicide::demarrage);
    }

    /** keepInventory à true sur la carte : cadavres et objets à vie ne servent plus à rien ; le staff est prévenu. */
    private static void demarrage(net.minecraftforge.event.server.ServerStartedEvent e) {
        if (e.getServer().overworld().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
            org.slf4j.LoggerFactory.getLogger("HeraliZ Utils").error(
                    "La gamerule keepInventory est a true sur la carte : les morts ne laissent plus de cadavre. "
                            + "Taper /gamerule keepInventory false en jeu.");
        }
    }

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("suicide").requires(s -> s.isPlayer()).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                ctx.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.suicide.refus",
                        "Impossible en créatif ou en spectateur."));
                return 0;
            }
            if (fr.heralisz.utils.spawn.ProtectionSpawn.protege(p) && !p.hasPermissions(2)) {
                ctx.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.suicide.spawn",
                        "Impossible au spawn : tu y es à l'abri."));
                return 0;
            }
            if (p.getPersistentData().getLong("heralisz:combat_jusqua") > System.currentTimeMillis()) {
                ctx.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.suicide.combat",
                        "Impossible en combat."));
                return 0;
            }
            if (aTerre(p)) {
                ctx.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.suicide.terre",
                        "Impossible à terre."));
                return 0;
            }
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
            return 1;
        }));
    }

    private static java.lang.reflect.Method downed;
    private static boolean downedCherche;

    /** Joueur à terre (Réanimation, par réflexion ; sans Réanimation : jamais). */
    private static boolean aTerre(ServerPlayer p) {
        try {
            if (!downedCherche) {
                downedCherche = true;
                downed = Class.forName("fr.heraliz.reanimation.core.Downed")
                        .getMethod("isDowned", net.minecraft.world.entity.Entity.class);
            }
            return downed != null && Boolean.TRUE.equals(downed.invoke(null, p));
        } catch (Throwable t) {
            return false;
        }
    }
}
