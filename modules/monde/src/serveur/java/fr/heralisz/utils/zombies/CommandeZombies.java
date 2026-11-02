package fr.heralisz.utils.zombies;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * {@code /zombies} (staff, permission 2) : état du système de zombies, zones de population.
 * <ul>
 *     <li>{@code stats} : zombies HeraliZ suivis et par niveau d'IA, zombies du peuplement, monstres de la carte, chemins
 *     calculés / partagés / refusés, bruits et zombies attirés, coups retenus par les jetons, secteurs entamés, ms par tick ;</li>
 *     <li>{@code zone ajouter <id> <police|militaire|danger|village|calme> <rayon>} à l'endroit où on se tient,
 *     {@code zone retirer <id>}, {@code zone liste} ({@link ZonesZombies}) ;</li>
 *     <li>{@code reserves} : capacité et places disponibles des secteurs autour de soi ({@link ReservesZombies}).</li>
 * </ul>
 * 02/11 (audit point 36) : {@code banc <nombre> [rayon]} et {@code banc retirer} sont retirés (jusqu'à 3 000 zombies
 * jamais retirés d'eux-mêmes, « jamais en production » ; le banc de charge de GitHub a ses bots). Des zombies déjà posés
 * par lui portent l'étiquette {@code heraliz_banc_zombie} : {@code /kill @e[tag=heraliz_banc_zombie]} les retire.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class CommandeZombies {
    private CommandeZombies() {
    }

    @SubscribeEvent
    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("zombies").requires(s -> s.hasPermission(2))
                .then(Commands.literal("stats").executes(CommandeZombies::stats))
                .then(Commands.literal("reserves").executes(CommandeZombies::reserves))
                .then(Commands.literal("zone")
                        .then(Commands.literal("liste").executes(CommandeZombies::zones))
                        .then(Commands.literal("retirer").then(Commands.argument("id", StringArgumentType.word())
                                .executes(CommandeZombies::retirerZone)))
                        .then(Commands.literal("ajouter").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (ZonesZombies.Type t : ZonesZombies.Type.values()) b.suggest(t.nom);
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("rayon", IntegerArgumentType.integer(4, 1024))
                                                .executes(CommandeZombies::ajouterZone)))))));
    }

    private static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("heralisz_utils.zombies.cmd." + cle, fr, args);
    }

    private static void ok(CommandSourceStack s, MutableComponent c) {
        s.sendSuccess(() -> c, false);
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        MinecraftServer srv = s.getServer();
        int[] n = IaZombies.niveaux();
        long[] ch = CheminsZombies.compteurs(), st = StimuliZombies.compteurs();
        ok(s, t("stats.titre", "Zombies HeraliZ : %s suivis (proches %s, moyens %s, loin %s, endormis %s)",
                IaZombies.suivis(), n[0], n[1], n[2], n[3]).withStyle(ChatFormatting.GOLD));
        ok(s, t("stats.peuplement", "Peuplement : %s zombies poses ; monstres sur la carte : %s ; secteurs entames : %s",
                Peuplement.suivis(), PlafondZombies.total(), ReservesZombies.entames()));
        ok(s, t("stats.chemins", "Chemins depuis le demarrage : %s calcules, %s partages, %s refuses (budget)", ch[0], ch[1], ch[2]));
        ok(s, t("stats.directs", "Poursuites en ligne droite (sans calcul de chemin) : %s", ch[3]));
        ok(s, t("stats.chemins_temps", "Chemins gardes (cible loin ou hors d'atteinte) : %s ; refuses faute de temps : %s ; pire tick : %s ms de calcul",
                ch[5], ch[4], String.format(Locale.ROOT, "%.2f", ch[6] / 1_000_000.0)));
        ok(s, t("stats.bruit", "Bruits : %s, zombies attires : %s ; coups retenus (3 attaquants au plus) : %s",
                st[0], st[1], JetonsAttaque.refuses()));
        ok(s, t("stats.tick", "Tick moyen : %s ms", String.format(Locale.ROOT, "%.1f", srv.getAverageTickTime())));
        return 1;
    }

    private static int reserves(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        Vec3 p = s.getPosition();
        long now = System.currentTimeMillis();
        int taille = ReservesZombies.taille();
        StringBuilder sb = new StringBuilder();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long cle = ReservesZombies.cle(p.x + dx * taille, p.z + dz * taille);
                sb.append(ReservesZombies.disponible(cle, now)).append('/').append(ReservesZombies.capacite(cle)).append(dx < 1 ? "  " : "");
            }
            if (dz < 1) sb.append(" | ");
        }
        ZonesZombies.Zone z = ZonesZombies.ici(p.x, p.z);
        ok(s, t("reserves", "Places disponibles / capacite des 9 secteurs autour (ligne par ligne, du nord au sud) : %s", sb.toString()));
        ok(s, t("reserves.zone", "Zone ici : %s", z == null ? "-" : z.id() + " (" + z.type().nom + ")"));
        return 1;
    }

    private static int zones(CommandContext<CommandSourceStack> c) {
        if (ZonesZombies.toutes().isEmpty()) {
            ok(c.getSource(), t("zone.aucune", "Aucune zone de zombies."));
            return 0;
        }
        for (ZonesZombies.Zone z : ZonesZombies.toutes()) {
            ok(c.getSource(), t("zone.ligne", "%s : %s, centre %s %s, rayon %s", z.id(), z.type().nom, (int) z.x(), (int) z.z(), (int) z.rayon()));
        }
        return ZonesZombies.toutes().size();
    }

    private static int ajouterZone(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        ZonesZombies.Type type = ZonesZombies.Type.parNom(StringArgumentType.getString(c, "type"));
        if (type == null) {
            c.getSource().sendFailure(t("zone.type", "Type inconnu : police, militaire, danger, village ou calme."));
            return 0;
        }
        Vec3 p = c.getSource().getPosition();
        int r = IntegerArgumentType.getInteger(c, "rayon");
        ZonesZombies.ajouter(new ZonesZombies.Zone(id, type, p.x, p.z, r));
        ok(c.getSource(), t("zone.ajoutee", "Zone %s (%s) posee ici, rayon %s.", id, type.nom, r));
        return 1;
    }

    private static int retirerZone(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        if (!ZonesZombies.retirer(id)) {
            c.getSource().sendFailure(t("zone.inconnue", "Zone inconnue : %s", id));
            return 0;
        }
        ok(c.getSource(), t("zone.retiree", "Zone %s retiree.", id));
        return 1;
    }
}
