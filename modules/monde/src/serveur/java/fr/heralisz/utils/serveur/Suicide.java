package fr.heralisz.utils.serveur;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * /suicide : le joueur meurt tout de suite (coincé, perdu…). Mort normale : son équipement reste sur son cadavre comme
 * pour toute autre mort ; pas de passage « à terre » (dégâts « kill », comme /kill). Refusé en créatif et en spectateur.
 * Les arènes de ClashWar bloquent déjà la commande. Refusé dans le monde du spawn protégé
 * (ProtectionSpawn), sauf pour le staff. Refusé en combat (tag de combat de Matchs, clé {@code heralisz:combat_jusqua}
 * des données du joueur) et à terre (Réanimation, par réflexion) : pas de mort instantanée pour échapper à la fouille.
 * Délai de 5 min entre deux /suicide (28/10, demande du propriétaire ; staff niveau 2 exempté, comme au spawn) : en
 * temps réel, gardé par UUID en mémoire ET dans les données gardées du joueur ({@code PlayerPersisted}, qui suivent la
 * mort et sont écrites dans sa sauvegarde) : ni une déconnexion, ni une mort, ni un redémarrage ne le remettent à zéro.
 */
public final class Suicide {
    /** Délai entre deux /suicide d'un même joueur, en millisecondes (temps réel). */
    private static final long DELAI_MS = 5L * 60L * 1000L;
    /** Clé de la fin du délai (heure en ms) dans les données gardées du joueur. */
    private static final String CLE = "heraliz_suicide_jusqua";
    /** Fin du délai par joueur, en mémoire (survit à une déconnexion, même si sa sauvegarde n'est pas relue). */
    private static final java.util.Map<java.util.UUID, Long> PROCHAIN = new java.util.concurrent.ConcurrentHashMap<>();

    private Suicide() {
    }

    private static net.minecraft.nbt.CompoundTag garde(ServerPlayer p) {
        net.minecraft.nbt.CompoundTag d = p.getPersistentData();
        if (!d.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            d.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        }
        return d.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    /** Millisecondes restantes avant le prochain /suicide (0 : permis). */
    private static long restant(ServerPlayer p, long maintenant) {
        long fin = Math.max(PROCHAIN.getOrDefault(p.getUUID(), 0L), garde(p).getLong(CLE));
        return Math.max(0L, fin - maintenant);
    }

    /** « 4:05 », « 0:42 » (lisible dans les trois langues). */
    private static String duree(long ms) {
        long s = (ms + 999L) / 1000L;
        long m = s / 60L;
        s %= 60L;
        return m + ":" + (s < 10 ? "0" : "") + s;
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
            long maintenant = System.currentTimeMillis();
            boolean staff = p.hasPermissions(2);
            if (!staff) {
                long reste = restant(p, maintenant);
                if (reste > 0) {
                    ctx.getSource().sendFailure(Component.translatableWithFallback("heralisz_utils.suicide.delai",
                            "Tu dois attendre encore %s avant de refaire /suicide.", duree(reste)));
                    return 0;
                }
                long fin = maintenant + DELAI_MS;
                PROCHAIN.put(p.getUUID(), fin);
                garde(p).putLong(CLE, fin);
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
