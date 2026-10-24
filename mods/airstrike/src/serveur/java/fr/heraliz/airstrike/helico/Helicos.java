package fr.heraliz.airstrike.helico;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.network.FouillePacket;
import fr.heraliz.airstrike.network.HelicoPositionPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * L'événement « hélicoptère de patrouille » côté serveur (24/10) : un seul à la fois, sur la carte (overworld).
 * <ul><li>Lancement automatique : {@code premierDelaiMinutes} après le démarrage, puis {@code intervalleMin/Max} après la
 * fin du précédent, s'il y a au moins {@code joueursMin} joueurs en survie sur la carte (sinon nouvel essai chaque
 * minute) ; et par le staff : /helico lancer [x z].</li>
 * <li>Annonces aux joueurs de la carte (arrivée, abattu, parti).</li>
 * <li>Position envoyée toutes les 10 ticks aux joueurs à moins de {@code porteeSuivi} blocs (vu et entendu au-delà de
 * la distance de vue).</li>
 * <li>Crash : 6 morceaux d'épave (3 fouillables), 2 caisses T6, un coffre T7 de HeraliZ Loot ; feu de 60 à 150 s pendant
 * lequel caisses, coffre et morceaux sont verrouillés (« Encore trop chaud »), puis {@code epaveMinutes}.</li></ul>
 * Rien n'est gardé au redémarrage : l'hélicoptère en vol disparaît (l'épave déjà au sol reste, sauvegardée avec le monde).
 */
public final class Helicos {
    @Nullable
    private static HelicoEntityServeur actif;
    private static long prochainAuto = -1;
    @Nullable
    private static List<Vec3> villes;

    private Helicos() {
    }

    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(Helicos::tickServeur);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Helicos::clicBloc);
        MinecraftForge.EVENT_BUS.addListener(Helicos::blesse);
        MinecraftForge.EVENT_BUS.addListener(Helicos::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(Helicos::arret);
        MinecraftForge.EVENT_BUS.addListener(Helicos::commandes);
    }

    // ================================================================================================ écouteurs
    private static void tickServeur(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = e.getServer();
        Fouilles.tick(srv);
        ServerLevel ow = srv.overworld();
        long t = srv.getTickCount();
        if (actif != null && (actif.isRemoved() || actif.level() != ow)) finir(ow);
        if (actif != null && t % 10 == 0) diffuser(ow, actif, true);
        if (!AirstrikeConfig.HELICO_ACTIF.get() || !AirstrikeConfig.HELICO_AUTO.get()) return;
        if (prochainAuto < 0) prochainAuto = t + AirstrikeConfig.HELICO_PREMIER_DELAI.get() * 1200L;
        if (actif == null && t >= prochainAuto && t % 20 == 0) {
            if (joueursSurCarte(ow) >= AirstrikeConfig.HELICO_JOUEURS_MIN.get()) lancer(ow, null);
            else prochainAuto = t + 1200;
        }
    }

    /** Coffre T7 du crash : verrouillé tant qu'une épave brûle à côté (clic ordinaire et fouille de HeraliZ Loot). */
    private static void clicBloc(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel sl)) return;
        BlockPos pos = e.getPos();
        if (!coffreT7(sl, pos)) return;
        if (sl.getEntitiesOfClass(EpaveHelicoEntity.class, new AABB(pos).inflate(16), EpaveHelicoEntity::brule).isEmpty()) return;
        e.setCanceled(true);
        Fouilles.chaud(p);
    }

    private static boolean coffreT7(ServerLevel sl, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(sl.getBlockState(pos).getBlock());
        if (!"heraliz_loot".equals(id.getNamespace())) return false;
        if (id.getPath().startsWith("t7_")) return true;
        if (!id.getPath().equals("partie")) return false;
        // bloc « partie » d'un coffre sur plusieurs blocs : le coffre est juste à côté
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            ResourceLocation r = BuiltInRegistries.BLOCK.getKey(sl.getBlockState(q).getBlock());
            if ("heraliz_loot".equals(r.getNamespace()) && r.getPath().startsWith("t7_")) return true;
        }
        return false;
    }

    private static void blesse(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Fouilles.enCours(p)) Fouilles.arreter(p, true);
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        Fouilles.deconnexion(e.getEntity().getUUID());
    }

    private static void arret(ServerStoppedEvent e) {
        actif = null;
        prochainAuto = -1;
        villes = null;
        Fouilles.vider();
    }

    private static void commandes(RegisterCommandsEvent e) {
        HelicoCommand.register(e.getDispatcher());
    }

    /** Paquet du jeu du joueur (PartieServeur). */
    public static void recevoir(ServerPlayer p, FouillePacket pk) {
        Entity e = p.level().getEntity(pk.entite);
        if (e != null) Fouilles.clic(p, e, pk.etape);
        else if (pk.etape == FouillePacket.RELACHE) Fouilles.arreter(p, false);
    }

    // ================================================================================================ lancement
    @Nullable
    static HelicoEntityServeur actif() {
        return actif != null && actif.isAlive() ? actif : null;
    }

    static long minutesAvantAuto(MinecraftServer srv) {
        return prochainAuto < 0 ? -1 : Math.max(0, (prochainAuto - srv.getTickCount()) / 1200);
    }

    static int joueursSurCarte(ServerLevel ow) {
        int n = 0;
        for (ServerPlayer p : ow.players()) if (!p.isSpectator() && !p.isCreative() && !p.getTags().contains("heraliz_bot")) n++;
        return n;
    }

    /** Lance un hélicoptère : au-dessus de (x, z) si donné, sinon il arrive du bord de la carte. Null si impossible. */
    @Nullable
    static HelicoEntityServeur lancer(ServerLevel ow, @Nullable Vec3 auDessus) {
        if (actif() != null) return null;
        RandomSource r = ow.getRandom();
        List<Vec3> trajet = trajet(ow, r);
        WorldBorder b = ow.getWorldBorder();
        double x, z;
        if (auDessus != null) {
            x = auDessus.x;
            z = auDessus.z;
        } else {
            Vec3 premier = trajet.get(0);
            double cx = b.getCenterX(), cz = b.getCenterZ();
            double a = Math.atan2(premier.z - cz, premier.x - cx);
            if (Math.abs(premier.x - cx) + Math.abs(premier.z - cz) < 1) a = r.nextDouble() * Math.PI * 2;
            double demi = Math.max(64, b.getSize() / 2 - 40);
            x = Mth.clamp(cx + Math.cos(a) * demi, b.getMinX() + 40, b.getMaxX() - 40);
            z = Mth.clamp(cz + Math.sin(a) * demi, b.getMinZ() + 40, b.getMaxZ() - 40);
        }
        // tronçon de départ chargé tout de suite, puis tenu par les tickets de l'hélicoptère
        ow.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        double sol = HelicoEntityServeur.sol(ow, x, z);
        double y = (Double.isNaN(sol) ? ow.getSeaLevel() : sol) + AirstrikeConfig.HELICO_HAUTEUR.get() + 8;
        Entity e = ModEntities.HELICO.get().create(ow);
        if (!(e instanceof HelicoEntityServeur h)) {
            AirstrikeMod.LOGGER.error("[helico] la partie serveur n'est pas branchée : pas d'hélicoptère");
            return null;
        }
        Vec3 premier = trajet.get(0);
        h.moveTo(x, y, z, HelicoEntityServeur.lacetVers(premier.x - x, premier.z - z), 0f);
        h.preparer(trajet, ow.getGameTime() + AirstrikeConfig.HELICO_DUREE.get() * 1200L);
        if (!ow.addFreshEntity(h)) return null;
        actif = h;
        annoncer(ow, "airstrike.helico.annonce",
                "Un hélicoptère de patrouille survole la zone. Abattez-le pour son butin... s'il ne vous abat pas avant.");
        AirstrikeMod.LOGGER.info("[helico] lancé en {} {} {} ({} points de passage)", (int) x, (int) y, (int) z, trajet.size());
        return h;
    }

    /** Villes de la carte (cercles des territoires des clans), puis points au hasard ; d'abord là où sont les joueurs. */
    private static List<Vec3> trajet(ServerLevel ow, RandomSource r) {
        WorldBorder b = ow.getWorldBorder();
        List<Vec3> candidats = new ArrayList<>();
        for (Vec3 v : villes()) if (b.isWithinBounds(v.x, v.z)) candidats.add(v);
        while (candidats.size() < 4) {
            double marge = Math.min(150, b.getSize() / 4);
            candidats.add(new Vec3(b.getMinX() + marge + r.nextDouble() * (b.getSize() - 2 * marge), 0,
                    b.getMinZ() + marge + r.nextDouble() * (b.getSize() - 2 * marge)));
        }
        Collections.shuffle(candidats, new java.util.Random(r.nextLong()));
        List<ServerPlayer> joueurs = new ArrayList<>(ow.players());
        candidats.sort(Comparator.comparingInt(v -> -proches(joueurs, v)));
        List<Vec3> choix = new ArrayList<>(candidats.subList(0, Math.min(6, candidats.size())));
        // ordre de visite : toujours la plus proche de la précédente (pas d'allers-retours)
        List<Vec3> ordre = new ArrayList<>();
        Vec3 ici = choix.remove(0);
        ordre.add(ici);
        while (!choix.isEmpty()) {
            Vec3 c = ici;
            Vec3 n = Collections.min(choix, Comparator.comparingDouble(v -> v.distanceToSqr(c)));
            choix.remove(n);
            ordre.add(n);
            ici = n;
        }
        return ordre;
    }

    private static int proches(List<ServerPlayer> joueurs, Vec3 v) {
        int n = 0;
        for (ServerPlayer p : joueurs) {
            double dx = p.getX() - v.x, dz = p.getZ() - v.z;
            if (dx * dx + dz * dz < 400 * 400) n++;
        }
        return n;
    }

    private static List<Vec3> villes() {
        if (villes != null) return villes;
        List<Vec3> l = new ArrayList<>();
        Path f = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("zones.json");
        try {
            if (Files.isRegularFile(f)) {
                JsonElement racine = JsonParser.parseString(Files.readString(f));
                JsonArray a = racine.isJsonArray() ? racine.getAsJsonArray() : new JsonArray();
                for (JsonElement el : a) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("x") && o.has("z")) l.add(new Vec3(o.get("x").getAsDouble(), 0, o.get("z").getAsDouble()));
                }
            }
        } catch (Exception ex) {
            AirstrikeMod.LOGGER.warn("[helico] villes illisibles ({}) : trajet au hasard", ex.toString());
        }
        villes = l;
        return l;
    }

    // ================================================================================================ fin, annonces
    private static void finir(ServerLevel ow) {
        if (actif != null) diffuser(ow, actif, false);
        actif = null;
        int a = AirstrikeConfig.HELICO_INTERVALLE_MIN.get(), b = Math.max(a, AirstrikeConfig.HELICO_INTERVALLE_MAX.get());
        prochainAuto = ow.getServer().getTickCount() + (a + ow.getRandom().nextInt(b - a + 1)) * 1200L;
    }

    static void retirer(ServerLevel ow) {
        if (actif != null) {
            actif.discard();
            finir(ow);
        }
    }

    private static void diffuser(ServerLevel ow, HelicoEntityServeur h, boolean present) {
        double r = AirstrikeConfig.HELICO_PORTEE_SUIVI.get();
        HelicoPositionPacket pk = new HelicoPositionPacket(h.getId(), present && h.isAlive(), h.getX(), h.getY(), h.getZ(),
                h.getYRot(), h.vie(), h.etat());
        for (ServerPlayer p : ow.players()) {
            if (!pk.present || p.distanceToSqr(h) < r * r) Network.sendTo(p, pk);
        }
    }

    static void annoncer(ServerLevel ow, String cle, String fr) {
        if (!AirstrikeConfig.HELICO_ANNONCES.get()) return;
        Component c = Component.translatableWithFallback(cle, fr).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : ow.players()) p.sendSystemMessage(c);
    }

    static void parti(HelicoEntityServeur h) {
        if (h.level() instanceof ServerLevel ow) {
            annoncer(ow, "airstrike.helico.parti", "L'hélicoptère de patrouille a quitté la zone.");
            AirstrikeMod.LOGGER.info("[helico] parti sans être abattu");
        }
    }

    static void abattu(HelicoEntityServeur h, @Nullable ServerPlayer auteur) {
        if (!(h.level() instanceof ServerLevel ow)) return;
        annoncer(ow, "airstrike.helico.abattu", "L'hélicoptère de patrouille a été abattu ! Son épave brûle quelque part sur la carte.");
        AirstrikeMod.LOGGER.info("[helico] abattu en {} {} {}{}", (int) h.getX(), (int) h.getY(), (int) h.getZ(),
                auteur != null ? " par " + auteur.getGameProfile().getName() : "");
    }

    // ================================================================================================ chute et crash
    /**
     * Point de chute : dans une direction au hasard, à 35-80 blocs, sur la terre ferme (aucune eau ni lave sous le point ni
     * à 3 blocs autour) et dans la bordure ; 24 directions et 3 distances essayées au plus, seulement dans les tronçons
     * chargés. Sinon : sous lui (même dans l'eau, en dernier recours).
     */
    static Vec3 pointDeChute(ServerLevel sl, Entity h) {
        RandomSource r = h.getRandom();
        double base = r.nextDouble() * Math.PI * 2, d0 = 35 + r.nextDouble() * 45;
        for (int k = 0; k < 24; k++) {
            double a = base + ((k % 2 == 0) ? 1 : -1) * ((k + 1) / 2) * Math.toRadians(15);
            for (double f : new double[]{1.0, 0.6, 1.35}) {
                double x = h.getX() + Math.cos(a) * d0 * f, z = h.getZ() + Math.sin(a) * d0 * f;
                if (!sl.getWorldBorder().isWithinBounds(x, z)) continue;
                double y = terreFerme(sl, x, z);
                if (!Double.isNaN(y)) return new Vec3(x, y, z);
            }
        }
        double y = terreFerme(sl, h.getX(), h.getZ());
        if (!Double.isNaN(y)) return new Vec3(h.getX(), y, h.getZ());
        double s = HelicoEntityServeur.sol(sl, h.getX(), h.getZ());
        return new Vec3(h.getX(), Double.isNaN(s) ? sl.getSeaLevel() : s, h.getZ());
    }

    private static double terreFerme(ServerLevel sl, double x, double z) {
        double haut = Double.NaN;
        int[][] autour = {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] o : autour) {
            int bx = Mth.floor(x) + o[0], bz = Mth.floor(z) + o[1];
            if (!sl.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            BlockPos p = new BlockPos(bx, y - 1, bz);
            if (!sl.getFluidState(p).isEmpty() || !sl.getFluidState(p.above()).isEmpty()) return Double.NaN;
            if (o[0] == 0 && o[1] == 0) haut = y;
        }
        return haut;
    }

    /** L'impact : morceaux projetés, caisses T6, coffre T7, le tout verrouillé tant que ça brûle. */
    static void crash(ServerLevel sl, Vec3 p, float lacet, @Nullable ServerPlayer tueur) {
        RandomSource r = sl.getRandom();
        long now = sl.getGameTime();
        int a = AirstrikeConfig.HELICO_FEU_MIN.get(), b = Math.max(a, AirstrikeConfig.HELICO_FEU_MAX.get());
        long feuFin = now + (a + r.nextInt(b - a + 1)) * 20L;
        long fin = feuFin + AirstrikeConfig.HELICO_EPAVE_MINUTES.get() * 1200L;
        List<Integer> ordre = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        Collections.shuffle(ordre, new java.util.Random(r.nextLong()));
        List<Integer> fouillables = ordre.subList(0, 3);
        double base = Math.toRadians(lacet);
        for (int k = 0; k < EpaveHelicoEntity.PIECES; k++) {
            Entity e = ModEntities.HELICO_EPAVE.get().create(sl);
            if (!(e instanceof EpaveHelicoEntityServeur ep)) return;
            double ang = base + k * Math.PI / 3 + (r.nextDouble() - 0.5) * 0.6;
            Vec3 v = k == 0 ? new Vec3(0, 0.15, 0)
                    : new Vec3(Math.cos(ang) * (0.3 + r.nextDouble() * 0.35), 0.45 + r.nextDouble() * 0.35, Math.sin(ang) * (0.3 + r.nextDouble() * 0.35));
            ep.moveTo(p.x, p.y + 1.0, p.z, k == 0 ? lacet : r.nextFloat() * 360f, 0f);
            ep.preparer(k, fouillables.contains(k), feuFin, fin, v, k == 0 ? 0f : (r.nextFloat() - 0.5f) * 30f);
            sl.addFreshEntity(ep);
        }
        // deux caisses T6, de part et d'autre de la cabine
        double cx = Math.cos(base), cz = Math.sin(base);
        for (int i = 0; i < 2; i++) {
            Entity e = ModEntities.HELICO_CAISSE.get().create(sl);
            if (!(e instanceof CaisseHelicoEntityServeur c)) break;
            double s = i == 0 ? 4.0 : -4.0;
            double x = p.x + cx * s - cz * 1.5, z = p.z + cz * s + cx * 1.5;
            double y = HelicoEntityServeur.sol(sl, x, z);
            c.moveTo(x, (Double.isNaN(y) ? p.y : y) + 0.3, z, lacet + 90f * i + r.nextFloat() * 20f, 0f);
            c.preparer(AirstrikeConfig.HELICO_TABLE_T6.get(), feuFin, fin);
            sl.addFreshEntity(c);
        }
        // coffre T7 de HeraliZ Loot, présent jusqu'à la disparition de l'épave
        int presence = (int) Math.min(Integer.MAX_VALUE, fin - now);
        String type = AirstrikeConfig.HELICO_COFFRE_T7.get();
        boolean pose = false;
        for (int k = 0; k < 16 && !pose; k++) {
            double ang = base + Math.PI / 2 + k * Math.PI / 8;
            double d = 5 + (k % 3);
            int x = Mth.floor(p.x + Math.cos(ang) * d), z = Mth.floor(p.z + Math.sin(ang) * d);
            double y = HelicoEntityServeur.sol(sl, x + 0.5, z + 0.5);
            if (Double.isNaN(y)) continue;
            Direction face = Direction.from2DDataValue(r.nextInt(4));
            pose = Ponts.coffreT7(sl, BlockPos.containing(x, y, z), face, type, presence);
        }
        if (!pose) {
            // secours : une troisième caisse, avec la table T7
            Entity e = ModEntities.HELICO_CAISSE.get().create(sl);
            if (e instanceof CaisseHelicoEntityServeur c) {
                double y = HelicoEntityServeur.sol(sl, p.x - cz * 4, p.z + cx * 4);
                c.moveTo(p.x - cz * 4, (Double.isNaN(y) ? p.y : y) + 0.3, p.z + cx * 4, lacet, 0f);
                c.preparer("t7", feuFin, fin);
                sl.addFreshEntity(c);
            }
            AirstrikeMod.LOGGER.warn("[helico] coffre T7 impossible à poser ({}), caisse T7 à la place", type);
        }
        AirstrikeMod.LOGGER.info("[helico] épave en {} {} {} : feu {} s, morceaux fouillables {}", (int) p.x, (int) p.y, (int) p.z,
                (feuFin - now) / 20, fouillables);
    }
}
