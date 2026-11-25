package fr.heraliz.vehicules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.ywzj.vehicle.custom.CommonAssetsManager;
import org.ywzj.vehicle.custom.vehicle.BaseVehicleData;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Véhicules qui apparaissent tout seuls un peu partout sur la carte (25/11, demande du propriétaire : « que les véhicules
 * contrôlables spawn en plus grande quantité un peu partout, mais avec une limite sur la carte, et que chaque véhicule
 * spawn avec genre 3 % d'essence »). Section {@code [apparition]} de {@code heraliz_vehicules-server.toml}.
 * <ul>
 *   <li>Un essai toutes les {@code intervalle_secondes} tant que la carte n'a pas son maximum ({@code max}) et qu'un joueur
 *   y est : un point au hasard à {@code distance_min} .. {@code distance_max} blocs d'un joueur pris au hasard, dans un
 *   coin de carte DÉJÀ chargé (le serveur ne charge jamais rien pour ça), à plus de {@code distance_min} blocs de tous les
 *   joueurs (personne ne le voit surgir), loin du spawn et des autres véhicules apparus ({@code ecart}), sur un sol de la
 *   liste {@code sols} à l'air libre (pas sous un toit ni sous un arbre, ni dans l'eau), à plat sur 5 x 5 blocs.</li>
 *   <li>Le véhicule naît avec {@code essence_pourcent} de son réservoir : réglé par {@link #entretenir} (appelé par
 *   {@link SuiviVehicules}) dès que le moteur l'a initialisé.</li>
 *   <li>Registre des véhicules apparus ({@link Registre}, avec le monde principal) : un véhicule détruit, ramassé ou
 *   retiré libère sa place ; un véhicule jamais revu chargé depuis {@code oubli_jours} aussi. Les véhicules posés par les
 *   joueurs (objet) ne comptent pas.</li>
 * </ul>
 * Fil serveur seulement ; rien n'est fait à chaque tick (un passage par seconde, un essai par intervalle, au plus quelques
 * points regardés, sans charger de coin de carte).
 */
public final class ApparitionVehicules {
    /** Marque (données persistantes de l'entité) d'un véhicule apparu tout seul. */
    static final String MARQUE = "heraliz_apparu";
    /** Essence (pour cent) à mettre dès que le moteur a initialisé le véhicule. */
    static final String ESSENCE = "heraliz_essence_initiale";
    /** Points regardés au plus par essai. */
    private static final int POINTS_PAR_ESSAI = 12;
    /** Demi-côté du carré qui doit être à plat (5 x 5). */
    private static final int DEMI_COTE = 2;
    /** « Vu » noté au plus une fois toutes les 10 minutes par véhicule (pas d'écriture du registre à chaque seconde). */
    private static final long VU_PAS_MS = 10 * 60_000L;

    private static int ticks;
    private static long prochainEssaiMs;
    private static boolean absentSignale;

    private ApparitionVehicules() {
    }

    static void enregistrer(IEventBus bus) {
        bus.addListener(ApparitionVehicules::onTick);
        bus.addListener(ApparitionVehicules::onDepart);
        bus.addListener(ApparitionVehicules::onArret);
    }

    private static boolean active() {
        try {
            return VehiculesConfig.APPARITION_ACTIVE.get() && VehiculesConfig.APPARITION_MAX.get() > 0;
        } catch (IllegalStateException pasEncoreCharge) {
            return false;
        }
    }

    private static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks < 20) return;
        ticks = 0;
        if (!active()) return;
        long now = System.currentTimeMillis();
        if (now < prochainEssaiMs) return;
        prochainEssaiMs = now + VehiculesConfig.APPARITION_INTERVALLE.get() * 1000L;
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        ResourceLocation dim = ResourceLocation.tryParse(VehiculesConfig.APPARITION_DIMENSION.get());
        ServerLevel l = dim == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (l == null || l.players().isEmpty()) return;
        Registre reg = Registre.de(s);
        reg.oublier(now, VehiculesConfig.APPARITION_OUBLI_JOURS.get() * 86_400_000L);
        if (reg.taille() >= VehiculesConfig.APPARITION_MAX.get()) return;
        try {
            for (int i = 0; i < POINTS_PAR_ESSAI; i++) {
                if (tenter(l, reg, now)) return;
            }
        } catch (RuntimeException | LinkageError ex) {
            HeralizVehicules.LOG.error("[vehicules] apparition d'un vehicule impossible", ex);
        }
    }

    /** Un point au hasard près d'un joueur : vrai si un véhicule y est apparu. */
    private static boolean tenter(ServerLevel l, Registre reg, long now) {
        RandomSource rnd = l.getRandom();
        List<ServerPlayer> joueurs = l.players();
        ServerPlayer j = joueurs.get(rnd.nextInt(joueurs.size()));
        if (j.isSpectator()) return false;
        double min = VehiculesConfig.APPARITION_DISTANCE_MIN.get();
        double max = Math.max(min + 16, VehiculesConfig.APPARITION_DISTANCE_MAX.get());
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double d = Math.sqrt(Mth.lerp(rnd.nextDouble(), min * min, max * max));   // même densité partout dans l'anneau
        int x = Mth.floor(j.getX() + Math.cos(angle) * d), z = Mth.floor(j.getZ() + Math.sin(angle) * d);
        if (!l.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) return false;
        BlockPos spawn = l.getSharedSpawnPos();
        double loin = VehiculesConfig.APPARITION_LOIN_DU_SPAWN.get();
        if (sq(x - spawn.getX()) + sq(z - spawn.getZ()) < loin * loin) return false;
        for (ServerPlayer p : joueurs) {
            if (sq(p.getX() - x) + sq(p.getZ() - z) < min * min) return false;
        }
        if (!reg.loinDesAutres(x, z, VehiculesConfig.APPARITION_ECART.get())) return false;
        int y = sol(l, x, z);
        if (y == Integer.MIN_VALUE) return false;
        String id = tirerVehicule(rnd);
        if (id == null) return false;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Optional<BaseVehicleData> data = rl == null ? Optional.empty() : CommonAssetsManager.vehicleDataManager().getVehicleData(rl);
        if (data.isEmpty()) {
            if (!absentSignale) {
                absentSignale = true;
                HeralizVehicules.LOG.warn("[vehicules] apparition : vehicule inconnu « {} » (liste « vehicules » du reglage)", id);
            }
            return false;
        }
        float lacet = rnd.nextFloat() * 360.0F;
        AbstractVehicle v = data.get().construct(l, new Vec3(x + 0.5, y + 1, z + 0.5), 0.0F, lacet);
        if (v == null) return false;
        Entity ent = v;
        CompoundTag pd = ent.getPersistentData();
        pd.putBoolean(MARQUE, true);
        pd.putFloat(ESSENCE, VehiculesConfig.APPARITION_ESSENCE.get().floatValue());
        if (!l.addFreshEntity(ent)) return false;
        reg.ajouter(ent.getUUID(), x, z, now);
        HeralizVehicules.LOG.info("[vehicules] apparition : {} en {} {} {} ({} / {} sur la carte)", id, x, y + 1, z, reg.taille(),
                VehiculesConfig.APPARITION_MAX.get());
        return true;
    }

    /**
     * Hauteur du sol en (x, z) si un véhicule peut y apparaître (sol de la liste au dessus de la colonne, à l'air libre,
     * à plat à un bloc près sur 5 x 5, tous les coins de carte déjà chargés), sinon {@link Integer#MIN_VALUE}.
     */
    private static int sol(ServerLevel l, int x, int z) {
        int y0 = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Set<Block> sols = sols();
        List<TagKey<Block>> tags = tagsSols();
        for (int dx = -DEMI_COTE; dx <= DEMI_COTE; dx++) {
            for (int dz = -DEMI_COTE; dz <= DEMI_COTE; dz++) {
                int cx = x + dx, cz = z + dz;
                LevelChunk c = l.getChunkSource().getChunkNow(cx >> 4, cz >> 4);
                if (c == null) return Integer.MIN_VALUE;   // coin pas chargé : jamais chargé pour ça
                int y = c.getHeight(Heightmap.Types.MOTION_BLOCKING, cx & 15, cz & 15);
                if (y0 == Integer.MIN_VALUE) y0 = y;
                else if (Math.abs(y - y0) > 1) return Integer.MIN_VALUE;
                BlockState st = c.getBlockState(pos.set(cx, y, cz));
                if (!sols.contains(st.getBlock()) && tags.stream().noneMatch(st::is)) return Integer.MIN_VALUE;
            }
        }
        return y0;
    }

    private static List<String> solsLus;
    private static Set<Block> solsBlocs = Set.of();
    private static List<TagKey<Block>> solsTags = List.of();

    private static Set<Block> sols() {
        lireSols();
        return solsBlocs;
    }

    private static List<TagKey<Block>> tagsSols() {
        lireSols();
        return solsTags;
    }

    /** Liste « sols » relue seulement quand elle change. */
    private static void lireSols() {
        List<? extends String> l = VehiculesConfig.APPARITION_SOLS.get();
        if (l.equals(solsLus)) return;
        solsLus = new ArrayList<>(l);
        Set<Block> blocs = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String s : l) {
            if (s.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(s.substring(1));
                if (rl != null) tags.add(TagKey.create(Registries.BLOCK, rl));
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(s);
            Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
            if (b != null && ForgeRegistries.BLOCKS.getKey(b).equals(rl)) blocs.add(b);
            else HeralizVehicules.LOG.warn("[vehicules] apparition : bloc inconnu « {} » dans « sols »", s);
        }
        solsBlocs = Set.copyOf(blocs);
        solsTags = List.copyOf(tags);
    }

    /** Véhicule tiré selon les poids de la liste « vehicules » (identifiant=poids), null si la liste est vide. */
    private static String tirerVehicule(RandomSource rnd) {
        Map<String, Integer> poids = new LinkedHashMap<>();
        int total = 0;
        for (String s : VehiculesConfig.APPARITION_VEHICULES.get()) {
            int i = s.lastIndexOf('=');
            String id = (i > 0 ? s.substring(0, i) : s).trim();
            int p = 1;
            if (i > 0) {
                try {
                    p = Integer.parseInt(s.substring(i + 1).trim());
                } catch (NumberFormatException ex) {
                    p = 0;
                }
            }
            if (p <= 0 || id.isEmpty()) continue;
            poids.merge(id, p, Integer::sum);
            total += p;
        }
        if (total <= 0) return null;
        int t = rnd.nextInt(total);
        for (Map.Entry<String, Integer> e : poids.entrySet()) {
            t -= e.getValue();
            if (t < 0) return e.getKey();
        }
        return null;
    }

    /**
     * Véhicule chargé (une fois par seconde, {@link SuiviVehicules}) : essence de départ mise dès que le moteur l'a
     * initialisé ; un véhicule apparu est noté « vu » (il compte toujours dans le maximum).
     */
    static void entretenir(AbstractVehicle v) {
        Entity e = v;
        CompoundTag pd = e.getPersistentData();
        if (!pd.getBoolean(MARQUE)) return;
        if (pd.contains(ESSENCE) && v.energyInfo != null && v.energyInfo.energyCapacity > 0) {
            float pct = Mth.clamp(pd.getFloat(ESSENCE), 0.0F, 100.0F);
            v.setEnergy(v.energyInfo.energyCapacity * pct / 100.0F);
            pd.remove(ESSENCE);
        }
        if (e.level() instanceof ServerLevel l) {
            Registre.de(l.getServer()).vu(e.getUUID(), e.getBlockX(), e.getBlockZ(), System.currentTimeMillis());
        }
    }

    /** Un véhicule apparu détruit, ramassé ou retiré (pas simplement déchargé) libère sa place. */
    private static void onDepart(EntityLeaveLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !(e.getEntity() instanceof AbstractVehicle)) return;
        Entity ent = e.getEntity();
        Entity.RemovalReason r = ent.getRemovalReason();
        if (r == null || !r.shouldDestroy() || !ent.getPersistentData().getBoolean(MARQUE)) return;
        Registre.de(l.getServer()).retirer(ent.getUUID());
    }

    private static void onArret(ServerStoppedEvent e) {
        ticks = 0;
        prochainEssaiMs = 0;
        absentSignale = false;
        solsLus = null;
    }

    /** Nombre de véhicules apparus comptés sur la carte (commande /vehicules apparus). */
    static int compte(MinecraftServer s) {
        return Registre.de(s).taille();
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Véhicules apparus tout seuls : identifiant -> {x, z, dernière fois vu (ms)}. Gardé avec le monde principal. */
    static final class Registre extends SavedData {
        private static final String ID = "heraliz_vehicules_apparus";
        private final Map<UUID, long[]> vehicules = new LinkedHashMap<>();

        static Registre de(MinecraftServer s) {
            return s.overworld().getDataStorage().computeIfAbsent(Registre::charger, Registre::new, ID);
        }

        private static Registre charger(CompoundTag tag) {
            Registre r = new Registre();
            ListTag l = tag.getList("vehicules", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                if (c.hasUUID("id")) r.vehicules.put(c.getUUID("id"), new long[]{c.getInt("x"), c.getInt("z"), c.getLong("vu")});
            }
            return r;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, long[]> e : vehicules.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", e.getKey());
                c.putInt("x", (int) e.getValue()[0]);
                c.putInt("z", (int) e.getValue()[1]);
                c.putLong("vu", e.getValue()[2]);
                l.add(c);
            }
            tag.put("vehicules", l);
            return tag;
        }

        int taille() {
            return vehicules.size();
        }

        void ajouter(UUID id, int x, int z, long now) {
            vehicules.put(id, new long[]{x, z, now});
            setDirty();
        }

        void retirer(UUID id) {
            if (vehicules.remove(id) != null) setDirty();
        }

        void vu(UUID id, int x, int z, long now) {
            long[] v = vehicules.get(id);
            if (v == null || now - v[2] < VU_PAS_MS) return;
            v[0] = x;
            v[1] = z;
            v[2] = now;
            setDirty();
        }

        void oublier(long now, long apresMs) {
            if (vehicules.values().removeIf(v -> now - v[2] > apresMs)) setDirty();
        }

        boolean loinDesAutres(int x, int z, int ecart) {
            long e2 = (long) ecart * ecart;
            for (long[] v : vehicules.values()) {
                long dx = v[0] - x, dz = v[1] - z;
                if (dx * dx + dz * dz < e2) return false;
            }
            return true;
        }
    }
}
