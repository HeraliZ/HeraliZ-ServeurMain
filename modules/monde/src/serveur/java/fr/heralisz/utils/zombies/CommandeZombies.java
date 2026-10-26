package fr.heralisz.utils.zombies;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * {@code /zombies} (staff, permission 2) : état du système de zombies, banc de charge, zones de population.
 * <ul>
 *     <li>{@code stats} : zombies HeraliZ suivis et par niveau d'IA, zombies du peuplement, monstres de la carte, chemins
 *     calculés / partagés / refusés, bruits et zombies attirés, coups retenus par les jetons, secteurs entamés, ms par tick ;</li>
 *     <li>{@code banc <nombre> [rayon]} : pose ce nombre de zombies HeraliZ autour de soi (50 par tick, seulement dans les
 *     tronçons actifs, étiquette {@value #TAG_BANC}, ni peuplement ni retrait) pour mesurer ; {@code banc retirer} les
 *     retire tous ; à faire sur un serveur de test, jamais en production avec des joueurs ;</li>
 *     <li>{@code zone ajouter <id> <police|militaire|danger|village|calme> <rayon>} à l'endroit où on se tient,
 *     {@code zone retirer <id>}, {@code zone liste} ({@link ZonesZombies}) ;</li>
 *     <li>{@code reserves} : capacité et places disponibles des secteurs autour de soi ({@link ReservesZombies}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class CommandeZombies {
    private CommandeZombies() {
    }

    /** Étiquette des zombies posés par le banc. */
    public static final String TAG_BANC = "heraliz_banc_zombie";
    private static final int PAR_TICK = 50;

    private static int resteBanc;
    private static ServerLevel niveauBanc;
    private static Vec3 centreBanc = Vec3.ZERO;
    private static int rayonBanc, essaisBanc;
    /** Raisons des essais ratés du banc : tronçon inactif, pas de sol, refusé à l'arrivée. */
    private static int ratesTroncon, ratesSol, ratesRefus;

    @SubscribeEvent
    public static void enregistrer(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("zombies").requires(s -> s.hasPermission(2))
                .then(Commands.literal("stats").executes(CommandeZombies::stats))
                .then(Commands.literal("reserves").executes(CommandeZombies::reserves))
                .then(Commands.literal("banc")
                        .then(Commands.literal("retirer").executes(CommandeZombies::retirerBanc))
                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 3000))
                                .executes(c -> banc(c, 96))
                                .then(Commands.argument("rayon", IntegerArgumentType.integer(16, 256))
                                        .executes(c -> banc(c, IntegerArgumentType.getInteger(c, "rayon"))))))
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

    private static int banc(CommandContext<CommandSourceStack> c, int rayon) {
        CommandSourceStack s = c.getSource();
        niveauBanc = s.getLevel();
        centreBanc = s.getPosition();
        rayonBanc = rayon;
        resteBanc = IntegerArgumentType.getInteger(c, "nombre");
        essaisBanc = resteBanc * 20;
        ok(s, t("banc.debut", "Banc : %s zombies vont etre poses autour de vous (rayon %s, %s par tick). /zombies stats et /diag mesure pour mesurer.",
                resteBanc, rayon, PAR_TICK));
        HeraliszUtils.LOG.info("[zombies] banc : {} zombies demandes autour de {} (rayon {})", resteBanc, centreBanc, rayon);
        return 1;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || resteBanc <= 0 || niveauBanc == null) return;
        ServerLevel sl = niveauBanc;
        RandomSource rnd = sl.getRandom();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int poses = 0;
        while (poses < PAR_TICK && resteBanc > 0 && essaisBanc-- > 0) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * rayonBanc;
            int x = Mth.floor(centreBanc.x + Math.cos(a) * d), z = Mth.floor(centreBanc.z + Math.sin(a) * d);
            m.set(x, Mth.floor(centreBanc.y), z);
            if (!sl.isPositionEntityTicking(m)) {
                ratesTroncon++;
                continue;
            }
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 place = Peuplement.debout(sl, m, x, y, z);
            if (place == null) {
                ratesSol++;
                continue;
            }
            ZombieSurvie zb = ZombiesSurvie.ZOMBIE.get().create(sl);
            if (zb == null) break;
            zb.tenue(SpawnZombiesServeur.tenueIci(place.x, place.z, rnd));
            zb.moveTo(place.x, place.y, place.z, rnd.nextFloat() * 360f, 0f);
            zb.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(place)), MobSpawnType.COMMAND, null, null);
            zb.setPersistenceRequired();
            zb.addTag(TAG_BANC);
            if (sl.addFreshEntity(zb)) {
                resteBanc--;
                poses++;
            } else {
                ratesRefus++;
            }
        }
        if (resteBanc <= 0 || essaisBanc <= 0) {
            HeraliszUtils.LOG.info("[zombies] banc : pose terminee ({} non poses ; essais rates : {} troncon inactif, {} sans sol, {} refuses a l'arrivee)",
                    Math.max(0, resteBanc), ratesTroncon, ratesSol, ratesRefus);
            ratesTroncon = ratesSol = ratesRefus = 0;
            resteBanc = 0;
            niveauBanc = null;
        }
    }

    private static int retirerBanc(CommandContext<CommandSourceStack> c) {
        int n = 0;
        resteBanc = 0;
        for (ServerLevel sl : c.getSource().getServer().getAllLevels()) {
            for (Entity en : sl.getAllEntities()) {
                if (en instanceof ZombieSurvie && en.getTags().contains(TAG_BANC)) {
                    en.discard();
                    n++;
                }
            }
        }
        final int total = n;
        ok(c.getSource(), t("banc.retires", "Banc : %s zombies retires.", total));
        return n;
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
