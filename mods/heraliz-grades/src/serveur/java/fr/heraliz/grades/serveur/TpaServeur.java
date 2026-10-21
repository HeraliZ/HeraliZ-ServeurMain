package fr.heraliz.grades.serveur;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Téléportation vers un autre joueur (Grades 1.4.0, demande du propriétaire du 21/10) : {@code /tpa <joueur>} envoie une
 * demande ; le joueur visé l'accepte ({@code /tpaccept}, ou le bouton cliquable du chat) ou la refuse ({@code /tpdeny}) ;
 * sans réponse en 60 s, elle tombe. Une fois acceptée : 3 s sans bouger ni être touché, puis vraie téléportation chez lui
 * (même monde que lui).
 * <p>Recharge entre deux téléportations réussies : 5 min sans grade, 15 s de moins par grade de la boutique (Rescapé
 * 4 min 45, Traqueur 4 min 30, Roi de la Zone 4 min 15) ; le staff (op connecté sur AuthMe) n'en a pas et ne patiente
 * pas. Refusé en match, en combat (celui qui part comme celui qui reçoit) et vers un joueur en match. Le tutoriel des
 * bases de CRac refuse ces commandes. Recharges gardées en mémoire (un redémarrage les remet à zéro). Fil serveur seulement.
 */
final class TpaServeur {
    private static final long RECHARGE_MS = 300_000L, PAR_GRADE_MS = 15_000L, EXPIRATION_MS = 60_000L;
    private static final int DELAI_TICKS = 60;
    private static final String CLE_COMBAT = "heralisz:combat_jusqua", DRAPEAU_MATCH = "heralisz:en_match";

    /** Demande en cours : qui veut venir (clé : le joueur visé, une demande à la fois par demandeur). */
    private record Demande(UUID demandeur, UUID cible, long fin) {
    }

    private record Attente(UUID cible, Vec3 depart, float vie, int fin) {
    }

    /** Demandes reçues par joueur visé : demandeur -> demande. */
    private static final Map<UUID, Map<UUID, Demande>> RECUES = new HashMap<>();
    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    /** Fin de la recharge (heure en ms) par joueur. */
    private static final Map<UUID, Long> RECHARGES = new HashMap<>();
    private static int ticks;

    private TpaServeur() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(TpaServeur::commandes);
        MinecraftForge.EVENT_BUS.addListener(TpaServeur::tick);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            UUID u = e.getEntity().getUUID();
            ATTENTES.remove(u);
            RECUES.remove(u);
            RECUES.values().forEach(m -> m.remove(u));
        });
    }

    // ------------------------------------------------------------------ règles
    private static boolean staff(ServerPlayer p) {
        return p.hasPermissions(2) && PontBukkit.authentifie(p);
    }

    private static boolean enMatch(ServerPlayer p) {
        return p.getPersistentData().getBoolean(DRAPEAU_MATCH) || p.level().dimension().location().getNamespace().equals("heralisz_matchs");
    }

    private static boolean enCombat(ServerPlayer p) {
        return p.getPersistentData().getLong(CLE_COMBAT) > System.currentTimeMillis();
    }

    /** Recharge du joueur selon son grade de la boutique (0 à 3). */
    static long recharge(UUID joueur) {
        return Math.max(0L, RECHARGE_MS - PAR_GRADE_MS * Math.max(0, GradesApi.rangBoutique(joueur)));
    }

    private static String duree(long ms) {
        long s = (ms + 999) / 1000;
        return s >= 60 ? (s / 60) + " min " + String.format("%02d", s % 60) + " s" : s + " s";
    }

    /** Raison qui empêche ce joueur de partir maintenant, ou null. */
    private static Component empechement(ServerPlayer p) {
        if (enMatch(p)) return Component.translatableWithFallback("heraliz_grades.tpa.match", "Impossible pendant un match.");
        if (enCombat(p)) return Component.translatableWithFallback("heraliz_grades.tpa.combat", "Impossible en combat.");
        return null;
    }

    private static int refus(ServerPlayer p, Component c) {
        p.sendSystemMessage(c.copy().withStyle(ChatFormatting.RED));
        return 0;
    }

    // ------------------------------------------------------------------ demande, réponse
    private static int demander(ServerPlayer p, ServerPlayer cible) {
        if (cible == p) return refus(p, Component.translatableWithFallback("heraliz_grades.tpa.soi", "Tu ne peux pas te téléporter vers toi-même."));
        Component non = empechement(p);
        if (non != null) return refus(p, non);
        if (enMatch(cible)) return refus(p, Component.translatableWithFallback("heraliz_grades.tpa.cible_match", "%s est en match.", cible.getDisplayName()));
        long reste = RECHARGES.getOrDefault(p.getUUID(), 0L) - System.currentTimeMillis();
        if (reste > 0 && !staff(p)) {
            return refus(p, Component.translatableWithFallback("heraliz_grades.tpa.recharge", "Prochaine téléportation dans %s.", duree(reste)));
        }
        // une seule demande à la fois par demandeur : l'ancienne tombe
        RECUES.values().forEach(m -> m.remove(p.getUUID()));
        RECUES.computeIfAbsent(cible.getUUID(), k -> new HashMap<>())
                .put(p.getUUID(), new Demande(p.getUUID(), cible.getUUID(), System.currentTimeMillis() + EXPIRATION_MS));
        String nom = p.getGameProfile().getName();
        Component oui = Component.translatableWithFallback("heraliz_grades.tpa.bouton_oui", "[Accepter]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + nom))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tpaccept " + nom))));
        Component nonB = Component.translatableWithFallback("heraliz_grades.tpa.bouton_non", "[Refuser]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + nom))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/tpdeny " + nom))));
        cible.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.tpa.recue", "%s veut se téléporter vers toi (60 s pour répondre).",
                p.getDisplayName()).withStyle(ChatFormatting.GOLD));
        cible.sendSystemMessage(Component.empty().append(oui).append(Component.literal("  ")).append(nonB));
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.tpa.envoyee", "Demande envoyée à %s.", cible.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    /** Demande à laquelle répond le joueur : celle du demandeur nommé, sinon la plus récente encore valable. */
    private static Demande prendre(ServerPlayer cible, ServerPlayer demandeur) {
        Map<UUID, Demande> m = RECUES.get(cible.getUUID());
        if (m == null) return null;
        long now = System.currentTimeMillis();
        m.values().removeIf(d -> d.fin() < now);
        Demande d = null;
        if (demandeur != null) d = m.remove(demandeur.getUUID());
        else {
            for (Demande x : m.values()) if (d == null || x.fin() > d.fin()) d = x;
            if (d != null) m.remove(d.demandeur());
        }
        if (m.isEmpty()) RECUES.remove(cible.getUUID());
        return d;
    }

    private static int accepter(ServerPlayer cible, ServerPlayer nomme) {
        Demande d = prendre(cible, nomme);
        if (d == null) return refus(cible, Component.translatableWithFallback("heraliz_grades.tpa.aucune", "Aucune demande de téléportation en attente."));
        ServerPlayer p = cible.server.getPlayerList().getPlayer(d.demandeur());
        if (p == null) return refus(cible, Component.translatableWithFallback("heraliz_grades.tpa.parti", "Ce joueur n'est plus connecté."));
        if (enMatch(cible)) return refus(cible, Component.translatableWithFallback("heraliz_grades.tpa.match", "Impossible pendant un match."));
        Component non = empechement(p);
        if (non != null) {
            refus(p, non);
            return refus(cible, Component.translatableWithFallback("heraliz_grades.tpa.impossible", "%s ne peut pas venir pour le moment.", p.getDisplayName()));
        }
        cible.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.tpa.acceptee_cible", "Tu as accepté : %s arrive dans 3 s.",
                p.getDisplayName()).withStyle(ChatFormatting.GOLD));
        if (staff(p)) {
            teleporter(p, cible);
            return 1;
        }
        ATTENTES.put(p.getUUID(), new Attente(cible.getUUID(), p.position(), p.getHealth(), ticks + DELAI_TICKS));
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.tpa.acceptee", "%s a accepté : téléportation dans 3 s, ne bouge pas.",
                cible.getDisplayName()).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int refuser(ServerPlayer cible, ServerPlayer nomme) {
        Demande d = prendre(cible, nomme);
        if (d == null) return refus(cible, Component.translatableWithFallback("heraliz_grades.tpa.aucune", "Aucune demande de téléportation en attente."));
        ServerPlayer p = cible.server.getPlayerList().getPlayer(d.demandeur());
        if (p != null) refus(p, Component.translatableWithFallback("heraliz_grades.tpa.refusee", "%s a refusé ta demande.", cible.getDisplayName()));
        cible.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.tpa.refusee_cible", "Demande refusée.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static void teleporter(ServerPlayer p, ServerPlayer cible) {
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(cible.serverLevel(), cible.getX(), cible.getY(), cible.getZ(), cible.getYRot(), cible.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        if (!staff(p)) RECHARGES.put(p.getUUID(), System.currentTimeMillis() + recharge(p.getUUID()));
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.tpa.arrive", "Téléporté vers %s.", cible.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ATTENTES.isEmpty()) return;
        MinecraftServer srv = e.getServer();
        ATTENTES.entrySet().removeIf(en -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            Attente a = en.getValue();
            if (p == null || p.isDeadOrDying()) return true;
            ServerPlayer cible = srv.getPlayerList().getPlayer(a.cible());
            if (cible == null || cible.isDeadOrDying()) {
                refus(p, Component.translatableWithFallback("heraliz_grades.tpa.parti", "Ce joueur n'est plus connecté."));
                return true;
            }
            if (p.position().distanceToSqr(a.depart()) > 0.09 || p.getHealth() < a.vie()) {
                refus(p, Component.translatableWithFallback("heraliz_grades.tpa.annule", "Téléportation annulée : tu as bougé."));
                return true;
            }
            if (ticks < a.fin()) return false;
            Component non = empechement(p);
            if (non == null && enMatch(cible)) non = Component.translatableWithFallback("heraliz_grades.tpa.cible_match", "%s est en match.", cible.getDisplayName());
            if (non != null) refus(p, non);
            else teleporter(p, cible);
            return true;
        });
    }

    // ------------------------------------------------------------------ commandes
    private static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("tpa")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> demander(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur")))));
        d.register(Commands.literal("tpaccept")
                .executes(c -> accepter(c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> accepter(c.getSource().getPlayerOrException(), joueur(c)))));
        d.register(Commands.literal("tpdeny")
                .executes(c -> refuser(c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> refuser(c.getSource().getPlayerOrException(), joueur(c)))));
    }

    private static ServerPlayer joueur(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return EntityArgument.getPlayer(c, "joueur");
    }
}
