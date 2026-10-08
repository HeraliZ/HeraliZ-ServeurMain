package fr.heralisz.matchs.moteur.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.CombatTag;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.compat.Revive;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Banc d'essai Reanimation x moteur (client de dev seulement, sous {@code /matchs test reanimation}) : journal des vraies
 * morts et des mises a terre (morts annulees) par joueur, compte des corps du mod corpse, execution et fouille forcees
 * (API de Reanimation par reflexion), verifications ecrites {@code [matchs] BANC ... OK|ECHEC} dans le journal.
 * <ul>
 *     <li>{@code raz} : journal et compte des corps remis a zero ;</li>
 *     <li>{@code etat <joueur>} : a terre (Reanimation), tag, en match, refus du Coffre ;</li>
 *     <li>{@code executer <bourreau> <victime>} : le bourreau est place a cote et lance le Coup de grace ;</li>
 *     <li>{@code fouiller <chercheur> <cible>} : ouvre la fouille de la cible a terre ;</li>
 *     <li>{@code mort <nom> "<type>" <auteur|-|_> <credit|-|_> <corps>} : exactement une vraie mort de ce type (auteur de la
 *     source, kill credite = {@code getKillCredit}), et {@code corps} corps poses depuis la remise a zero ;</li>
 *     <li>{@code aterre <nom> <0|1>} : etat a terre attendu ; {@code tague <nom> <0|1>} : tag attendu ;</li>
 *     <li>{@code fuite <joueur> <0|1>} : le joueur tape {@code /tp @s ~ ~3 ~} ; refus attendu ou non ;</li>
 *     <li>{@code journal} : tout le journal dans le fichier de log.</li>
 * </ul>
 */
public final class BancReanimation {
    private BancReanimation() {
    }

    /** Une mort reelle ou annulee vue par le banc. */
    record Mort(String type, String auteur, String credit, boolean annulee) {
    }

    private static final Map<String, List<Mort>> MORTS = new LinkedHashMap<>();
    private static int corps;
    private static boolean ecoute;

    static LiteralArgumentBuilder<CommandSourceStack> commandes() {
        if (!ecoute) {
            ecoute = true;
            // LOWEST, annules compris : on voit les mises a terre (mort annulee par Reanimation) et les vraies morts
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BancReanimation::mortVue);
            MinecraftForge.EVENT_BUS.addListener(BancReanimation::entite);
        }
        return Commands.literal("reanimation")
                .then(Commands.literal("raz").executes(x -> {
                    MORTS.clear();
                    corps = 0;
                    resultat(x.getSource().getServer(), "reanimation raz", true, "Reanimation " + (Revive.reanimation() ? "present" : "ABSENT"));
                    return 1;
                }))
                .then(Commands.literal("journal").executes(x -> {
                    HeraliszMatchs.LOG.info("[matchs] BANC reanimation journal : morts {} ; corps {}", MORTS, corps);
                    return 1;
                }))
                .then(Commands.literal("etat").then(Commands.argument("joueur", EntityArgument.player()).executes(x -> {
                    ServerPlayer p = EntityArgument.getPlayer(x, "joueur");
                    resultat(p.server, "reanimation etat", true, nom(p) + " : a terre " + Revive.aTerre(p) + ", tague " + CombatTag.estTague(p)
                            + " (" + CombatTag.restantMs(p) + " ms), en match " + MatchApi.enMatch(p) + ", sante " + p.getHealth()
                            + ", refus du Coffre " + Coffre.refus(p) + ", pose " + p.getPose() + ", pose forcee " + p.getForcedPose());
                    return 1;
                })))
                .then(Commands.literal("executer").then(Commands.argument("bourreau", EntityArgument.player())
                        .then(Commands.argument("victime", EntityArgument.player()).executes(BancReanimation::executer))))
                .then(Commands.literal("fouiller").then(Commands.argument("chercheur", EntityArgument.player())
                        .then(Commands.argument("cible", EntityArgument.player()).executes(BancReanimation::fouiller))))
                .then(Commands.literal("mort").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("type", StringArgumentType.string())
                                .then(Commands.argument("auteur", StringArgumentType.word())
                                        .then(Commands.argument("credit", StringArgumentType.word())
                                                .then(Commands.argument("corps", IntegerArgumentType.integer(0, 10)).executes(BancReanimation::verifierMort)))))))
                .then(Commands.literal("aterre").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("attendu", IntegerArgumentType.integer(0, 1)).executes(x -> {
                            String n = StringArgumentType.getString(x, "nom");
                            ServerPlayer p = x.getSource().getServer().getPlayerList().getPlayerByName(n);
                            boolean a = p != null && Revive.aTerre(p);
                            boolean ok = a == (IntegerArgumentType.getInteger(x, "attendu") == 1);
                            resultat(x.getSource().getServer(), "reanimation aterre", ok, n + " a terre " + a + (p == null ? " (hors ligne)" : ", pose " + p.getPose()));
                            return ok ? 1 : 0;
                        }))))
                .then(Commands.literal("fuite").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("refusee", IntegerArgumentType.integer(0, 1)).executes(x -> {
                            // commande de teleportation tapee par le joueur (meme chemin que le chat : CommandEvent)
                            ServerPlayer p = EntityArgument.getPlayer(x, "joueur");
                            double y0 = p.getY();
                            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "tp @s ~ ~3 ~");
                            boolean refusee = Math.abs(p.getY() - y0) < 0.01;
                            boolean ok = refusee == (IntegerArgumentType.getInteger(x, "refusee") == 1);
                            resultat(p.server, "reanimation fuite", ok, nom(p) + " /tp " + (refusee ? "refuse" : "execute") + " (a terre "
                                    + Revive.aTerre(p) + ", tague " + CombatTag.estTague(p) + ")");
                            return ok ? 1 : 0;
                        }))))
                .then(Commands.literal("tague").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("attendu", IntegerArgumentType.integer(0, 1)).executes(x -> {
                            String n = StringArgumentType.getString(x, "nom");
                            UUID u = uuid(x.getSource().getServer(), n);
                            boolean t = CombatTag.estTague(u);
                            boolean ok = t == (IntegerArgumentType.getInteger(x, "attendu") == 1);
                            resultat(x.getSource().getServer(), "reanimation tague", ok, n + " tague " + t + " (" + CombatTag.restantMs(u) + " ms)");
                            return ok ? 1 : 0;
                        }))));
    }

    // ------------------------------------------------------------------ ecoute
    private static void mortVue(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        String type = e.getSource().typeHolder().unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse("?");
        Entity a = e.getSource().getEntity();
        LivingEntity c = p.getKillCredit();
        Mort m = new Mort(type, a == null ? "-" : a.getName().getString(), c == null ? "-" : c.getName().getString(), e.isCanceled());
        MORTS.computeIfAbsent(nom(p), k -> new ArrayList<>()).add(m);
        HeraliszMatchs.LOG.info("[matchs] BANC reanimation : {} {} ({}, auteur {}, credit {})", nom(p), m.annulee() ? "mort ANNULEE (mise a terre ou match)"
                : "MORT REELLE", type, m.auteur(), m.credit());
    }

    private static void entite(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk()) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType());
        if (id != null && id.getNamespace().equals("corpse")) {
            corps++;
            HeraliszMatchs.LOG.info("[matchs] BANC reanimation : corps pose ({}) en {}", id, e.getEntity().blockPosition());
        }
    }

    // ------------------------------------------------------------------ actions
    private static int executer(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerPlayer b = EntityArgument.getPlayer(x, "bourreau");
        ServerPlayer v = EntityArgument.getPlayer(x, "victime");
        approcher(b, v);
        boolean ok;
        String detail;
        try {
            Class<?> ex = Class.forName("fr.heraliz.reanimation.execution.Executions");
            Class<?> type = Class.forName("fr.heraliz.reanimation.execution.ExecutionType");
            Object cdg = null;
            for (Object o : type.getEnumConstants()) if (((Enum<?>) o).name().equals("COUP_DE_GRACE")) cdg = o;
            Method start = ex.getMethod("start", LivingEntity.class, LivingEntity.class, type);
            ok = Boolean.TRUE.equals(start.invoke(null, b, v, cdg));
            detail = nom(b) + " execute " + nom(v) + " (Coup de grace) : " + (ok ? "lancee" : "REFUSEE") + ", distance "
                    + String.format(Locale.ROOT, "%.2f", b.distanceTo(v));
        } catch (ReflectiveOperationException | RuntimeException e) {
            ok = false;
            detail = "API Reanimation : " + e;
        }
        resultat(b.server, "reanimation executer", ok, detail);
        return ok ? 1 : 0;
    }

    private static int fouiller(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        ServerPlayer c = EntityArgument.getPlayer(x, "chercheur");
        ServerPlayer v = EntityArgument.getPlayer(x, "cible");
        approcher(c, v);
        boolean ok;
        String detail;
        try {
            Class<?> m = Class.forName("fr.heraliz.reanimation.core.DownedManager");
            m.getMethod("startSearch", LivingEntity.class, LivingEntity.class).invoke(null, c, v);
            ok = c.containerMenu != c.inventoryMenu;
            detail = nom(c) + " fouille " + nom(v) + " : menu " + c.containerMenu.getClass().getSimpleName();
        } catch (ReflectiveOperationException | RuntimeException e) {
            ok = false;
            detail = "API Reanimation : " + e;
        }
        resultat(c.server, "reanimation fouiller", ok, detail);
        return ok ? 1 : 0;
    }

    /** Place l'acteur a 1,2 bloc de la cible, tourne vers elle (portee d'execution et de fouille : 3 blocs). */
    private static void approcher(ServerPlayer acteur, ServerPlayer cible) {
        Vec3 d = acteur.position().subtract(cible.position());
        Vec3 h = new Vec3(d.x, 0, d.z);
        h = h.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : h.normalize();
        Vec3 pos = cible.position().add(h.scale(1.2));
        float lacet = (float) Math.toDegrees(Math.atan2(-(cible.getX() - pos.x), cible.getZ() - pos.z));
        acteur.teleportTo(cible.serverLevel(), pos.x, cible.getY(), pos.z, lacet, 30f);
    }

    // ------------------------------------------------------------------ verifications
    private static int verifierMort(CommandContext<CommandSourceStack> x) {
        String n = StringArgumentType.getString(x, "nom");
        String type = StringArgumentType.getString(x, "type");
        String auteur = StringArgumentType.getString(x, "auteur");
        String credit = StringArgumentType.getString(x, "credit");
        int corpsAttendus = IntegerArgumentType.getInteger(x, "corps");
        List<Mort> l = MORTS.getOrDefault(n, List.of());
        List<Mort> reelles = l.stream().filter(m -> !m.annulee()).toList();
        long annulees = l.size() - reelles.size();
        Mort m = reelles.size() == 1 ? reelles.get(0) : null;
        boolean ok = m != null && m.type().equals(type) && (auteur.equals("_") || m.auteur().equals(auteur))
                && (credit.equals("_") || m.credit().equals(credit)) && corps == corpsAttendus;
        resultat(x.getSource().getServer(), "reanimation mort " + n, ok, "vraies morts " + reelles.size() + " (attendu 1) " + reelles
                + ", mises a terre " + annulees + ", corps " + corps + " (attendu " + corpsAttendus + "), attendu " + type + " auteur " + auteur
                + " credit " + credit);
        return ok ? 1 : 0;
    }

    // ------------------------------------------------------------------ outils
    private static String nom(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    private static UUID uuid(MinecraftServer s, String nom) {
        ServerPlayer p = s.getPlayerList().getPlayerByName(nom);
        return p != null ? p.getUUID() : UUIDUtil.createOfflinePlayerUUID(nom);
    }

    private static void resultat(MinecraftServer s, String test, boolean ok, String detail) {
        HeraliszMatchs.LOG.info("[matchs] BANC {} {} {}", test, ok ? "OK" : "ECHEC", detail);
        Annonces.chat(s, Component.literal(test.toUpperCase(Locale.ROOT) + " · ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(ok ? "OK" : "ECHEC").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(detail.isEmpty() ? "" : "  " + detail).withStyle(ChatFormatting.GRAY)));
    }
}
