package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Heraliz;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Les deux seules commandes de déplacement des joueurs (le staff, permission 2, n'a ni délai ni règle de lieu) :
 * <ul>
 *   <li>{@code /home} : seulement depuis le monde du spawn ({@code heralisz:spawn}), vers sa base du monde des bases
 *   (à côté de son Claim Block) ; sans base, vers un point au hasard du monde des bases pour en bâtir une. Une fois
 *   toutes les {@code [home] delai_s} secondes (3 min, gardé au redémarrage).</li>
 *   <li>{@code /spawn} : seulement depuis SA base (dans la zone de son claim, monde des bases), hors combat, vers le point
 *   de réveil du spawn (HeraliZ Utils, {@code fr.heralisz.utils.api.SpawnApi.allerAuSpawn}, par réflexion).</li>
 * </ul>
 * Pour aller sur la carte : la borne de départ du spawn. Départ après {@code [home] immobile_s} secondes sans bouger ni
 * être blessé ; toujours une vraie téléportation ({@code teleportTo}, suivie par Spartan).
 */
@EventBusSubscriber(modid = "crac")
public final class Deplacements {
    private record Attente(String genre, Vec3 depart, int ticks, Runnable action) {
    }

    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    @Nullable
    private static Method allerAuSpawn;
    private static boolean spawnCherche;

    private Deplacements() {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("home").executes(c -> home(c.getSource().getPlayerOrException())));
        d.register(Commands.literal("spawn").executes(c -> spawn(c.getSource().getPlayerOrException())));
    }

    private static boolean staff(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    // ---------------------------------------------------------------------------------------------- /home

    static int home(ServerPlayer p) {
        boolean staff = staff(p);
        if (!staff && !MondeBases.estSpawn(p.level())) {
            T.chat(p, T.t("crac.home.depuis_spawn", "/home ne marche que depuis le spawn. Retourne au spawn avec /spawn depuis ta base."),
                ChatFormatting.RED);
            return 0;
        }
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            T.chat(p, T.t("crac.bases.absent", "Le monde des bases n'existe pas sur ce serveur (crac:bases)."), ChatFormatting.RED);
            return 0;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).joueur(p.getUUID());
        long delai = BasesReglages.i(BasesReglages.HOME_DELAI_S, 180) * 1000L;
        long reste = j.dernierHome + delai - System.currentTimeMillis();
        if (!staff && reste > 0) {
            T.chat(p, T.t("crac.home.attendre", "Prochain /home dans %s.", T.duree(reste + 999)), ChatFormatting.RED);
            return 0;
        }
        attendre(p, "home", () -> partirHome(p));
        return 1;
    }

    private static void partirHome(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return;
        }
        Claim c = baseDe(p);
        Vec3 dest = null;
        boolean aSaBase = false;
        if (c != null) {
            bases.getChunk(c.anchor().getX() >> 4, c.anchor().getZ() >> 4);
            dest = Points.presDe(bases, p, c.anchor());
            if (dest == null) {
                dest = Vec3.atBottomCenterOf(c.anchor().above());
            }
            aSaBase = true;
        } else {
            dest = Points.auHasard(bases, p);
        }
        if (dest == null) {
            T.chat(p, T.t("crac.home.pas_de_place", "Aucune place sûre trouvée dans le monde des bases : réessaie dans un instant."),
                ChatFormatting.RED);
            return;
        }
        p.stopRiding();
        p.teleportTo(bases, dest.x, dest.y, dest.z, p.getYRot(), 0f);
        p.resetFallDistance();
        BasesDonnees d = BasesDonnees.get(p.server);
        d.joueur(p.getUUID()).dernierHome = System.currentTimeMillis();
        d.marquer();
        if (aSaBase) {
            T.barre(p, T.t("crac.home.arrive", "Bienvenue chez toi."), ChatFormatting.GREEN);
        } else {
            T.chat(p, T.t("crac.home.sans_base", "Tu n'as pas de base : te voici dans le monde des bases, trouve un coin pour en bâtir une."),
                ChatFormatting.YELLOW);
        }
    }

    /** Le claim de la base du joueur dans le monde des bases (son groupe), ou null. */
    @Nullable
    static Claim baseDe(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return null;
        }
        Group g = GroupData.get(p.server).getByMember(p.getUUID());
        return g == null ? null : ClaimManager.getByGroup(bases, g.id());
    }

    // ---------------------------------------------------------------------------------------------- /spawn

    static int spawn(ServerPlayer p) {
        if (!staff(p)) {
            if (!MondeBases.estBases(p.level()) || !dansSaBase(p)) {
                T.chat(p, T.t("crac.spawn.depuis_base", "/spawn ne marche que depuis ta base (dans la zone de ton Claim Block)."),
                    ChatFormatting.RED);
                return 0;
            }
            int combat = Heraliz.combatSecondsLeft(p);
            if (combat > 0) {
                T.chat(p, T.t("crac.spawn.combat", "Tu es en combat : /spawn dans %s s.", String.valueOf(combat)), ChatFormatting.RED);
                return 0;
            }
        }
        attendre(p, "spawn", () -> {
            if (!allerAuSpawn(p)) {
                T.chat(p, T.t("crac.spawn.indisponible", "Le spawn n'est pas disponible pour l'instant : préviens le staff."),
                    ChatFormatting.RED);
            }
        });
        return 1;
    }

    /** Le joueur est-il dans la zone d'un claim de son groupe (ou de son clan, droit d'entrer) ? */
    static boolean dansSaBase(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel sl)) {
            return false;
        }
        Claim c = ClaimManager.getAt(sl, p.blockPosition());
        if (c == null) {
            return false;
        }
        Group g = GroupData.get(p.server).getById(c.groupId());
        return g != null && (g.isMember(p.getUUID()) || ClanAccess.allows(g, p.getUUID(), ClanAccess.Action.ENTER));
    }

    /** {@code SpawnApi.allerAuSpawn(ServerPlayer)} de HeraliZ Utils, lu par son nom (pas de dépendance). */
    static boolean allerAuSpawn(ServerPlayer p) {
        if (!spawnCherche) {
            spawnCherche = true;
            try {
                allerAuSpawn = Class.forName("fr.heralisz.utils.api.SpawnApi").getMethod("allerAuSpawn", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                Crac.LOGGER.warn("[CRac] SpawnApi de HeraliZ Utils introuvable : /spawn ne peut pas marcher ({})", e.toString());
            }
        }
        if (allerAuSpawn == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(allerAuSpawn.invoke(null, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Crac.LOGGER.warn("[CRac] SpawnApi.allerAuSpawn en erreur", e);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------- départ immobile

    private static void attendre(ServerPlayer p, String genre, Runnable action) {
        int s = staff(p) ? 0 : BasesReglages.i(BasesReglages.IMMOBILE_S, 3);
        if (s <= 0) {
            ATTENTES.remove(p.getUUID());
            action.run();
            return;
        }
        ATTENTES.put(p.getUUID(), new Attente(genre, p.position(), s * 20, action));
        T.barre(p, T.t("crac.depart.ne_bouge_pas", "Ne bouge pas : départ dans %s s.", String.valueOf(s)), ChatFormatting.YELLOW);
    }

    /** Chaque tick (MondeBasesServeur). */
    static void tick(MinecraftServer server) {
        if (ATTENTES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Attente>> it = ATTENTES.entrySet().iterator();
        Map<UUID, Attente> suite = new HashMap<>();
        java.util.List<Runnable> departs = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Attente> e = it.next();
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Attente a = e.getValue();
            if (p == null || !p.isAlive()) {
                continue;
            }
            if (p.position().distanceToSqr(a.depart()) > 0.36) {
                T.barre(p, T.t("crac.depart.annule", "Départ annulé : tu as bougé."), ChatFormatting.RED);
                continue;
            }
            int t = a.ticks() - 1;
            if (t <= 0) {
                departs.add(a.action());
                continue;
            }
            if (t % 20 == 0) {
                T.barre(p, T.t("crac.depart.ne_bouge_pas", "Ne bouge pas : départ dans %s s.", String.valueOf(t / 20)), ChatFormatting.YELLOW);
            }
            suite.put(e.getKey(), new Attente(a.genre(), a.depart(), t, a.action()));
        }
        ATTENTES.putAll(suite);
        // les téléportations après le parcours (une action peut relancer une attente)
        for (Runnable r : departs) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac] départ (/home, /spawn) en erreur", ex);
            }
        }
    }

    /** Blessé pendant l'attente : départ annulé. */
    @SubscribeEvent
    public static void blesse(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ATTENTES.remove(p.getUUID()) != null) {
            T.barre(p, T.t("crac.depart.blesse", "Départ annulé : tu as été blessé."), ChatFormatting.RED);
        }
    }

    static void oublier(UUID joueur) {
        ATTENTES.remove(joueur);
    }
}
