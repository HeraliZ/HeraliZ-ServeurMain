package fr.heraliz.banc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Le spawn du banc et les vrais chemins du serveur pour y aller et en repartir (01/11), par réflexion (le banc ne dépend
 * que du noyau ; sans HeraliZ Utils ou sans HeraliZ Extraction, chaque appel rend faux et le banc fait le trajet
 * lui-même) :
 * <ul>
 *     <li>{@code /bots camp} : le monde du spawn construit n'est pas dans le dépôt (sur le banc, c'est du vide) ; une dalle
 *     de 25 × 25 est posée sous le point de réveil (celui du jar de HeraliZ Utils), gardée chargée comme le vrai spawn :
 *     le point de réveil devient « praticable » et les vrais chemins y mènent ;</li>
 *     <li>{@link #versSpawn} : {@code SpawnApi.allerAuSpawn} (fin de {@code /spawn}, retour d'une extraction, point de
 *     réveil d'un vrai joueur qui réapparaît « Au camp » : les bots en sont exclus par leur étiquette, le banc le fait
 *     pour eux) ;</li>
 *     <li>{@link #extraire} : {@code Destination.teleport} de HeraliZ Extraction (sortie d'une zone d'extraction) ;</li>
 *     <li>{@link #borne} : {@code DepartServeur.demander} (clic sur la borne de départ : compte à rebours, point d'arrivée
 *     cherché en tâche de fond, vraie téléportation) ; les zones de groupe sont ses zones d'arrivée
 *     ({@link #zoneArrivee}, effacées par {@code /bots retirer}).</li>
 * </ul>
 * Fil serveur seulement.
 */
final class Camp {
    private Camp() {
    }

    private static final ResourceKey<net.minecraft.world.level.Level> SPAWN =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("heralisz", "spawn"));
    private static final int DEMI = 12;

    @Nullable
    static ServerLevel niveau;
    @Nullable
    static Vec3 point;
    /** Le point de réveil de HeraliZ Utils est praticable (après la dalle) : les vrais chemins y mènent. */
    static boolean reveil;

    // ------------------------------------------------------------------ préparation (/bots camp)

    /** Pose la dalle sous le point de réveil ; texte du résultat (journal). */
    static String preparer(MinecraftServer srv) {
        ServerLevel l = null;
        Vec3 p = null;
        try {
            Object dec = Class.forName("fr.heralisz.utils.cine.CineDecorServeur").getMethod("get", MinecraftServer.class).invoke(null, srv);
            Object etat = dec.getClass().getMethod("etat").invoke(dec);
            p = (Vec3) etat.getClass().getMethod("reveil").invoke(etat);
            l = (ServerLevel) dec.getClass().getMethod("niveau", MinecraftServer.class).invoke(dec, srv);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            HeralizBanc.LOG.info("[banc] point de réveil de HeraliZ Utils introuvable ({}) : camp au point d'apparition du spawn", ex.toString());
        }
        if (l == null) l = srv.getLevel(SPAWN);
        if (l == null) l = srv.overworld();
        if (p == null) p = Vec3.atBottomCenterOf(l.getSharedSpawnPos());
        int x = Mth.floor(p.x), y = Mth.floor(p.y), z = Mth.floor(p.z);
        if (y - 1 <= l.getMinBuildHeight() || y + 3 >= l.getMaxBuildHeight()) {
            y = Mth.clamp(100, l.getMinBuildHeight() + 2, l.getMaxBuildHeight() - 4);
            p = new Vec3(x + 0.5, y, z + 0.5);
        }
        int poses = 0;
        for (int dx = -DEMI; dx <= DEMI; dx++) {
            for (int dz = -DEMI; dz <= DEMI; dz++) {
                BlockPos sol = new BlockPos(x + dx, y - 1, z + dz);
                if (l.getBlockState(sol).isAir()) {
                    l.setBlock(sol, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    poses++;
                }
            }
        }
        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        l.getChunkSource().addRegionTicket(Troncons.CAMP, cp, 2, cp);
        // le point de réveil n'est « praticable » que si son fichier de régions existe : écrit tout de suite
        l.getChunkSource().save(true);
        niveau = l;
        point = p;
        reveil = reveilPraticable(srv);
        return String.format(java.util.Locale.ROOT, "%s %.1f %.1f %.1f, %d blocs posés, point de réveil praticable : %s",
                l.dimension().location(), p.x, p.y, p.z, poses, reveil);
    }

    static void oublier() {
        if (niveau != null && point != null) {
            ChunkPos cp = new ChunkPos(Mth.floor(point.x) >> 4, Mth.floor(point.z) >> 4);
            niveau.getChunkSource().removeRegionTicket(Troncons.CAMP, cp, 2, cp);
        }
        niveau = null;
        point = null;
        reveil = false;
    }

    static boolean pret() {
        return niveau != null && point != null;
    }

    static boolean auCamp(ServerPlayer p) {
        return niveau != null && p.serverLevel() == niveau;
    }

    // ------------------------------------------------------------------ chemins

    private static boolean cherche;
    @Nullable
    private static Method allerAuSpawn, praticable, demander, carte, extraction, pointsGet;
    @Nullable
    private static Field zonesChamp;
    @Nullable
    private static Constructor<?> zoneNeuve;

    private static void chercher() {
        if (cherche) return;
        cherche = true;
        allerAuSpawn = methode("fr.heralisz.utils.api.SpawnApi", "allerAuSpawn", ServerPlayer.class);
        praticable = methode("fr.heralisz.utils.cine.CineReseauServeur", "reveilPraticable", MinecraftServer.class);
        demander = methode("fr.heralisz.utils.spawn.DepartServeur", "demander", ServerPlayer.class, BlockPos.class);
        carte = methode("fr.heralisz.utils.spawn.DepartServeur", "carte", MinecraftServer.class);
        extraction = methode("com.heraliz.extraction.serveur.Destination", "teleport", ServerPlayer.class);
        pointsGet = methode("fr.heralisz.utils.spawn.PointsDepart", "get", ServerLevel.class);
        try {
            Class<?> pd = Class.forName("fr.heralisz.utils.spawn.PointsDepart");
            zonesChamp = pd.getField("zones");
            zoneNeuve = Class.forName("fr.heralisz.utils.spawn.PointsDepart$Zone").getConstructor(int.class, int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            zonesChamp = null;
            zoneNeuve = null;
        }
    }

    @Nullable
    private static Method methode(String classe, String nom, Class<?>... types) {
        try {
            return Class.forName(classe).getMethod(nom, types);
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeralizBanc.LOG.info("[banc] {}.{} absent : le banc fera ce trajet lui-même", classe, nom);
            return null;
        }
    }

    private static boolean vrai(@Nullable Method m, Object... args) {
        if (m == null) return false;
        try {
            Object r = m.invoke(null, args);
            return r == null || Boolean.TRUE.equals(r);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeralizBanc.LOG.warn("[banc] {} : {}", m.getName(), ex.toString());
            return false;
        }
    }

    private static boolean reveilPraticable(MinecraftServer srv) {
        chercher();
        return praticable != null && vrai(praticable, srv);
    }

    /** Vers le spawn par le vrai chemin (point de réveil), sinon sur la dalle ; faux sans camp. */
    static boolean versSpawn(ServerPlayer p) {
        chercher();
        if (reveil && allerAuSpawn != null && vrai(allerAuSpawn, p) && auCamp(p)) return true;
        if (!pret()) return false;
        p.stopRiding();
        p.teleportTo(niveau, point.x, point.y, point.z, p.getYRot(), 0f);
        p.resetFallDistance();
        return true;
    }

    /** Sortie d'une zone d'extraction par le vrai chemin (HeraliZ Extraction), sinon vers le spawn. */
    static boolean extraire(ServerPlayer p) {
        chercher();
        if (extraction != null) {
            ServerLevel avant = p.serverLevel();
            Vec3 pos = p.position();
            vrai(extraction, p);
            if (p.serverLevel() != avant || p.position().distanceToSqr(pos) > 1) return true;
        }
        return versSpawn(p);
    }

    /** Clic sur la borne de départ (vraie borne) ; faux si HeraliZ Utils n'est pas là ou si le bot n'est pas au camp. */
    static boolean borne(ServerPlayer p) {
        chercher();
        if (demander == null || !auCamp(p)) return false;
        return vrai(demander, p, BlockPos.containing(point == null ? p.position() : point));
    }

    /** Le monde de la carte où mène la borne (DepartServeur.carte), sinon le monde principal. */
    static ServerLevel carte(MinecraftServer srv) {
        chercher();
        if (carte != null) {
            try {
                Object l = carte.invoke(null, srv);
                if (l instanceof ServerLevel s) return s;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeralizBanc.LOG.debug("[banc] carte de la borne", ex);
            }
        }
        return srv.overworld();
    }

    /** Zone d'arrivée de la borne (PointsDepart, données du monde du banc) ; rien si ce n'est pas le monde de la carte. */
    @SuppressWarnings("unchecked")
    static void zoneArrivee(ServerLevel l, String nom, int x, int z, int rayon) {
        chercher();
        if (pointsGet == null || zonesChamp == null || zoneNeuve == null || l != carte(l.getServer())) return;
        try {
            Object pd = pointsGet.invoke(null, l);
            ((Map<String, Object>) zonesChamp.get(pd)).put(nom, zoneNeuve.newInstance(x, z, Math.max(4, rayon)));
            ((SavedData) pd).setDirty();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeralizBanc.LOG.warn("[banc] zone d'arrivée {} pas posée : {}", nom, ex.toString());
        }
    }

    /** Retire les zones d'arrivée posées par le banc (noms « banc_… »). */
    @SuppressWarnings("unchecked")
    static void retirerZonesArrivee(MinecraftServer srv) {
        chercher();
        if (pointsGet == null || zonesChamp == null) return;
        try {
            Object pd = pointsGet.invoke(null, carte(srv));
            if (((Map<String, Object>) zonesChamp.get(pd)).keySet().removeIf(k -> k.startsWith("banc_"))) ((SavedData) pd).setDirty();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            HeralizBanc.LOG.warn("[banc] zones d'arrivée du banc pas retirées : {}", ex.toString());
        }
    }
}
