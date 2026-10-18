package fr.heralisz.utils.spawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.api.SpawnApi;
import fr.heralisz.utils.cine.CineReseauServeur;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Borne de départ et monde du spawn, côté serveur.
 * <ul>
 * <li>Départ : clic sur la borne (au spawn), délai réglable (le joueur doit rester là : s'il s'éloigne, est blessé ou
 * change de monde, c'est annulé) pendant lequel un point d'arrivée sûr est cherché, un essai par tick (un tronçon chargé
 * au plus par tick : pas de saccade) ; puis vraie téléportation (teleportTo : suivie par Spartan), fondu au noir et
 * protection de quelques secondes à l'arrivée.</li>
 * <li>Points d'arrivée : points exacts et zones (disques) posés par le staff (/depart point, /depart zone), tirés au
 * hasard ; dans une zone (ou dans toute la bordure de la carte si rien n'est posé), un sol de la liste « sols » avec le
 * ciel au-dessus (jamais un toit, l'eau, l'intérieur d'un bâtiment ni sous un arbre), hors des zones dangereuses de
 * HeraliZ Décors et à « distance_villes » au moins du bord de chaque ville (territoires des clans).
 * Partout : place libre, aucun autre joueur à moins de « distance_joueurs », dans la bordure, et seulement dans une
 * partie de la carte déjà construite (fichier de régions présent : jamais de terrain généré).</li>
 * <li>Monde du spawn : pas de monstres, pas de combat entre joueurs (réglables), sa propre bordure centrée sur le
 * réveil, envoyée aux joueurs qui y entrent (le jeu leur enverrait celle de la carte).</li>
 * <li>{@link SpawnApi#allerAuSpawn} branché sur le point de réveil de la cinématique (CineReseauServeur).</li>
 * </ul>
 * Fil serveur seulement.
 */
public final class DepartServeur {
    private DepartServeur() {}

    /** Un départ en cours. */
    private static final class EnCours {
        final ResourceKey<Level> monde;
        final Vec3 depart;
        final int fin;
        int essais;
        Arrivee trouvee;

        EnCours(ResourceKey<Level> monde, Vec3 depart, int fin, int essais) {
            this.monde = monde;
            this.depart = depart;
            this.fin = fin;
            this.essais = essais;
        }
    }

    /** Point d'arrivée retenu : pieds du joueur et regard. */
    public record Arrivee(double x, double y, double z, float lacet, String origine) {}

    private static final Map<UUID, EnCours> EN_COURS = new HashMap<>();
    private static final Map<UUID, Integer> DERNIER = new HashMap<>();
    private static final Map<UUID, Integer> PROTEGES = new HashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::tick);
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::commandes);
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::blesse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, DepartServeur::attaque);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, DepartServeur::arrivee);
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::changementMonde);
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::connexion);
        MinecraftForge.EVENT_BUS.addListener(DepartServeur::reapparition);
        SpawnApi.brancher(CineReseauServeur::auSpawn);
        ProtectionSpawn.init();                                     // aucun dégât, aucune créature, explosions sans effet
    }

    // ---------------------------------------------------------------------------------------------- outils
    private static Component t(String cle, String francais, Object... args) {
        return Component.translatableWithFallback("heralisz_utils.depart." + cle, francais, args);
    }

    private static String pos(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.0f %.0f %.0f", x, y, z);
    }

    /** Le monde de la carte (réglage « monde »), ou null s'il n'existe pas sur ce serveur. */
    public static ServerLevel carte(MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(DepartConfig.lire(DepartConfig.MONDE));
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /**
     * Le fichier de régions qui contient (x, z) existe-t-il dans ce monde ? Avant de charger un tronçon : jamais de
     * terrain généré (carte construite à la main, monde du spawn déposé tel quel).
     */
    public static boolean regionPresente(ServerLevel l, int x, int z) {
        Path dossier = DimensionType.getStorageFolder(l.dimension(), l.getServer().getWorldPath(LevelResource.ROOT));
        return Files.isRegularFile(dossier.resolve("region").resolve("r." + (x >> 9) + "." + (z >> 9) + ".mca"));
    }

    // ---------------------------------------------------------------------------------------------- départ
    /** Clic sur la borne (BorneDepartServeur). */
    public static void demander(ServerPlayer sp, BlockPos borne) {
        MinecraftServer server = sp.server;
        ServerLevel ici = sp.serverLevel();
        if (DepartConfig.lire(DepartConfig.SEULEMENT_AU_SPAWN) && !SpawnApi.estAuSpawn(ici)) {
            sp.displayClientMessage(t("seulement_spawn", "Cette borne ne fonctionne qu'au spawn."), true);
            return;
        }
        if (EN_COURS.containsKey(sp.getUUID())) {
            sp.displayClientMessage(t("deja", "Départ déjà en cours : ne bouge pas."), true);
            return;
        }
        int maintenant = server.getTickCount();
        Integer dernier = DERNIER.get(sp.getUUID());
        int attente = DepartConfig.lire(DepartConfig.ATTENTE) * 20;
        if (dernier != null && maintenant - dernier < attente && !sp.hasPermissions(2)) {
            sp.displayClientMessage(t("attente", "Attends encore %s s avant un nouveau départ.",
                    String.valueOf((attente - (maintenant - dernier) + 19) / 20)), true);
            return;
        }
        ServerLevel carte = carte(server);
        if (carte == null) {
            sp.displayClientMessage(t("pas_de_carte", "La carte (%s) n'existe pas sur ce serveur : préviens le staff.",
                    DepartConfig.lire(DepartConfig.MONDE)), false);
            return;
        }
        DERNIER.put(sp.getUUID(), maintenant);
        int delai = DepartConfig.lire(DepartConfig.DELAI) * 20;
        int essais = DepartConfig.lire(DepartConfig.ESSAIS);
        ici.playSound(null, borne, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.2f);
        if (delai <= 0) {
            Arrivee a = chercher(carte, sp, essais);
            if (a == null) {
                echec(sp);
            } else {
                partir(sp, carte, a);
            }
            return;
        }
        EN_COURS.put(sp.getUUID(), new EnCours(ici.dimension(), sp.position(), maintenant + delai, essais));
        sp.displayClientMessage(t("compte", "Départ dans %s s : ne bouge pas.", String.valueOf(delai / 20)), true);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) {
            return;
        }
        MinecraftServer server = e.getServer();
        int maintenant = server.getTickCount();
        if (maintenant % 20 == 0) {
            bordure(server, false);
            PROTEGES.values().removeIf(fin -> fin < maintenant);
        }
        if (EN_COURS.isEmpty()) {
            return;
        }
        double bouger = DepartConfig.lire(DepartConfig.BOUGER);
        ServerLevel carte = carte(server);
        List<Runnable> apres = new ArrayList<>();                 // téléportations après le parcours de la liste
        for (Iterator<Map.Entry<UUID, EnCours>> it = EN_COURS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, EnCours> en = it.next();
            EnCours d = en.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || carte == null) {
                it.remove();
                continue;
            }
            double dx = sp.getX() - d.depart.x, dz = sp.getZ() - d.depart.z;
            if (sp.isDeadOrDying() || sp.level().dimension() != d.monde || dx * dx + dz * dz > bouger * bouger
                    || Math.abs(sp.getY() - d.depart.y) > bouger + 1) {
                it.remove();
                sp.displayClientMessage(t("annule", "Départ annulé : tu as bougé."), true);
                continue;
            }
            if (d.trouvee == null && d.essais > 0) {               // un essai par tick
                d.essais--;
                d.trouvee = essai(carte, sp, sp.getRandom());
            }
            int reste = d.fin - maintenant;
            ServerLevel ici = sp.serverLevel();
            if (maintenant % 3 == 0) {
                ici.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 4, 0.35, 0.7, 0.35, 0.02);
            }
            if (reste > 0) {
                if (reste % 20 == 0) {
                    sp.displayClientMessage(t("compte", "Départ dans %s s : ne bouge pas.", String.valueOf(reste / 20)), true);
                    ici.playSound(null, sp.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5f, 1.6f);
                }
                continue;
            }
            if (d.trouvee != null) {
                it.remove();
                Arrivee a = d.trouvee;
                apres.add(() -> partir(sp, carte, a));
            } else if (d.essais <= 0) {
                it.remove();
                echec(sp);
            } else if (maintenant % 20 == 0) {
                sp.displayClientMessage(t("recherche", "Recherche d'un point d'arrivée sûr…"), true);
            }
        }
        apres.forEach(Runnable::run);
    }

    private static void echec(ServerPlayer sp) {
        sp.displayClientMessage(t("echec", "Aucun point d'arrivée sûr pour l'instant : réessaie dans un moment (ou préviens le staff)."), false);
        HeraliszUtils.LOG.warn("[spawn] départ de {} : aucun point d'arrivée sûr trouvé (/depart info)", sp.getGameProfile().getName());
    }

    /** Vraie téléportation vers la carte (suivie par Spartan), effets d'arrivée. */
    public static void partir(ServerPlayer sp, ServerLevel carte, Arrivee a) {
        ServerLevel ici = sp.serverLevel();
        ici.sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 40, 0.4, 0.9, 0.4, 0.4);
        ici.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.8f);
        sp.stopRiding();
        sp.teleportTo(carte, a.x(), a.y(), a.z(), a.lacet(), 0f);
        sp.resetFallDistance();
        if (DepartConfig.lire(DepartConfig.AVEUGLEMENT)) {
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
        }
        int protection = DepartConfig.lire(DepartConfig.PROTECTION) * 20;
        if (protection > 0) {
            PROTEGES.put(sp.getUUID(), sp.server.getTickCount() + protection);
        }
        carte.playSound(null, a.x(), a.y(), a.z(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.5f, 0.9f);
        carte.sendParticles(ParticleTypes.CLOUD, a.x(), a.y() + 0.2, a.z(), 12, 0.4, 0.1, 0.4, 0.02);
        sp.displayClientMessage(t("arrive", "Te voilà sur la carte. Bonne chance."), true);
        HeraliszUtils.LOG.info("[spawn] {} part sur la carte : {} ({})", sp.getGameProfile().getName(), pos(a.x(), a.y(), a.z()), a.origine());
    }

    // ---------------------------------------------------------------------------------------------- points d'arrivée
    /** Jusqu'à n essais d'un coup (commandes du staff, délai nul). */
    public static Arrivee chercher(ServerLevel carte, ServerPlayer pour, int n) {
        RandomSource r = carte.getRandom();
        for (int i = 0; i < n; i++) {
            Arrivee a = essai(carte, pour, r);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    /** Un essai : un point ou une zone tirés au hasard, contrôlés ; null si refusé (on réessaiera). */
    static Arrivee essai(ServerLevel carte, ServerPlayer pour, RandomSource r) {
        PointsDepart pd = PointsDepart.get(carte);
        int np = pd.points.size(), nz = pd.zones.size();
        WorldBorder b = carte.getWorldBorder();
        int marge = DepartConfig.lire(DepartConfig.MARGE_BORDURE);
        if (np + nz > 0) {
            int k = r.nextInt(np + nz);
            if (k < np) {
                String nom = new ArrayList<>(pd.points.keySet()).get(k);
                PointsDepart.Point p = pd.points.get(nom);
                return pointLibre(carte, pour, p.x(), p.y(), p.z()) ? new Arrivee(p.x(), p.y(), p.z(), p.lacet(), "point " + nom) : null;
            }
            String nom = new ArrayList<>(pd.zones.keySet()).get(k - np);
            PointsDepart.Zone z = pd.zones.get(nom);
            for (int i = 0; i < TIRAGES_VILLES; i++) {             // tirages sans rien charger tant qu'on est près d'une ville
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * z.rayon();
                int x = Mth.floor(z.x() + 0.5 + Math.cos(a) * d), zz = Mth.floor(z.z() + 0.5 + Math.sin(a) * d);
                if (loinDesVilles(carte, x + 0.5, zz + 0.5)) {
                    return sol(carte, pour, x, zz, r, "zone " + nom);
                }
            }
            return null;
        }
        if (!DepartConfig.lire(DepartConfig.CARTE_ENTIERE)) {
            return null;
        }
        double x0 = b.getMinX() + marge, x1 = b.getMaxX() - marge, z0 = b.getMinZ() + marge, z1 = b.getMaxZ() - marge;
        if (x1 <= x0 || z1 <= z0) {
            return null;
        }
        for (int i = 0; i < TIRAGES_VILLES; i++) {
            int x = Mth.floor(Mth.lerp(r.nextDouble(), x0, x1)), z = Mth.floor(Mth.lerp(r.nextDouble(), z0, z1));
            if (loinDesVilles(carte, x + 0.5, z + 0.5)) {
                return sol(carte, pour, x, z, r, "carte");
            }
        }
        return null;
    }

    /** Tirages de colonnes par essai pour trouver une colonne loin des villes (aucun tronçon chargé pour ces tirages). */
    private static final int TIRAGES_VILLES = 64;

    // villes : cercles des territoires des clans (config/heralisz_clans/zones.json), relus quand le fichier change
    private static long villesDate = -1;
    private static List<double[]> villes = List.of();

    /** Vrai si (x, z) est à « distance_villes » au moins du bord de chaque ville (carte principale seulement). */
    static boolean loinDesVilles(ServerLevel carte, double x, double z) {
        int marge = DepartConfig.lire(DepartConfig.DISTANCE_VILLES);
        if (marge <= 0 || carte != carte.getServer().overworld()) {
            return true;
        }
        Path f = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("zones.json");
        try {
            long date = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (date != villesDate) {
                villesDate = date;
                List<double[]> l = new ArrayList<>();
                if (date != 0) {
                    try (Reader in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        for (JsonElement e : JsonParser.parseReader(in).getAsJsonArray()) {
                            JsonObject v = e.getAsJsonObject();
                            if (v.has("id") && "test".equals(v.get("id").getAsString())) {
                                continue;                          // zone d'essai du staff, pas une ville
                            }
                            l.add(new double[]{v.get("x").getAsDouble(), v.get("z").getAsDouble(),
                                    v.has("rayon") ? v.get("rayon").getAsDouble() : 0.0});
                        }
                    }
                }
                villes = l;
                HeraliszUtils.LOG.info("[spawn] {} villes évitées par la borne de départ ({})", l.size(), f);
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("[spawn] villes illisibles ({}) : ignorées", f, ex);
            villes = List.of();
        }
        for (double[] v : villes) {
            double dx = x - v[0], dz = z - v[1], loin = v[2] + marge;
            if (dx * dx + dz * dz < loin * loin) {
                return false;
            }
        }
        return true;
    }

    /** Un sol sûr dans la colonne (x, z) : sol de la liste, ciel au-dessus, place libre, pas d'eau, loin des autres. */
    private static Arrivee sol(ServerLevel carte, ServerPlayer pour, int x, int z, RandomSource r, String origine) {
        int marge = DepartConfig.lire(DepartConfig.MARGE_BORDURE);
        WorldBorder b = carte.getWorldBorder();
        if (x < b.getMinX() + marge || x > b.getMaxX() - marge || z < b.getMinZ() + marge || z > b.getMaxZ() - marge
                || !regionPresente(carte, x, z) || dangereux(carte, x + 0.5, z + 0.5)) {
            return null;
        }
        carte.getChunk(x >> 4, z >> 4);                            // chargé depuis le disque (jamais généré : région présente)
        int y = carte.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= carte.getMinBuildHeight() + 1 || y >= carte.getMaxBuildHeight() - 2) {
            return null;
        }
        if (carte.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > y) {
            return null;                                           // feuillage au-dessus : sous un arbre
        }
        BlockPos pieds = new BlockPos(x, y, z);
        BlockState dessous = carte.getBlockState(pieds.below());
        if (!dessous.getFluidState().isEmpty() || !solPermis(dessous)) {
            return null;
        }
        double py = y + Math.max(0.0, dessous.getCollisionShape(carte, pieds.below()).max(Direction.Axis.Y) - 1.0);
        if (!pointLibre(carte, pour, x + 0.5, py, z + 0.5)) {
            return null;
        }
        return new Arrivee(x + 0.5, py, z + 0.5, r.nextFloat() * 360f - 180f, origine);
    }

    /** Place pour un joueur (corps libre, pas de liquide, un sol dessous), loin des autres joueurs, dans la bordure. */
    private static boolean pointLibre(ServerLevel carte, ServerPlayer pour, double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!carte.getWorldBorder().isWithinBounds(x, z) || !regionPresente(carte, bx, bz)) {
            return false;
        }
        carte.getChunk(bx >> 4, bz >> 4);
        BlockPos p = BlockPos.containing(x, y, z);
        if (!carte.getFluidState(p).isEmpty() || !carte.getFluidState(p.above()).isEmpty()) {
            return false;
        }
        AABB corps = EntityType.PLAYER.getDimensions().makeBoundingBox(new Vec3(x, y, z));
        if (!carte.noCollision(corps) || carte.noCollision(new AABB(x - 0.3, y - 0.6, z - 0.3, x + 0.3, y - 0.01, z + 0.3))) {
            return false;
        }
        double d = DepartConfig.lire(DepartConfig.DISTANCE_JOUEURS);
        for (ServerPlayer o : carte.players()) {
            if (o != pour && !o.isSpectator() && o.distanceToSqr(x, y, z) < d * d) {
                return false;
            }
        }
        return true;
    }

    // sols permis (réglage « sols ») : blocs et tags, relus quand le réglage change
    private static List<? extends String> solsSource;
    private static final Set<Block> SOLS_BLOCS = new HashSet<>();
    private static final List<TagKey<Block>> SOLS_TAGS = new ArrayList<>();

    private static boolean solPermis(BlockState s) {
        List<? extends String> l = DepartConfig.lire(DepartConfig.SOLS);
        if (l != solsSource) {
            solsSource = l;
            SOLS_BLOCS.clear();
            SOLS_TAGS.clear();
            for (String e : l) {
                boolean tag = e.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? e.substring(1) : e);
                if (id == null) {
                    continue;
                }
                if (tag) {
                    SOLS_TAGS.add(TagKey.create(Registries.BLOCK, id));
                } else if (ForgeRegistries.BLOCKS.containsKey(id)) {
                    SOLS_BLOCS.add(ForgeRegistries.BLOCKS.getValue(id));
                }
            }
        }
        if (SOLS_BLOCS.contains(s.getBlock())) {
            return true;
        }
        for (TagKey<Block> t : SOLS_TAGS) {
            if (s.is(t)) {
                return true;
            }
        }
        return false;
    }

    // zones dangereuses de HeraliZ Décors (data/heraliz_zones.json du monde principal) : polygones et leur bordure
    private static long zonesDate = -1;
    private static List<double[][]> zones = List.of();

    private static boolean dangereux(ServerLevel carte, double x, double z) {
        if (!DepartConfig.lire(DepartConfig.EVITER_ZONES) || carte != carte.getServer().overworld()) {
            return false;
        }
        Path f = carte.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("heraliz_zones.json");
        try {
            long date = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (date != zonesDate) {
                zonesDate = date;
                List<double[][]> l = new ArrayList<>();
                if (date != 0) {
                    try (Reader in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                        for (JsonElement e : o.getAsJsonArray("zones")) {
                            JsonObject zo = e.getAsJsonObject();
                            var pts = zo.getAsJsonArray("points");
                            double[][] p = new double[pts.size() + 1][];
                            for (int i = 0; i < pts.size(); i++) {
                                var a = pts.get(i).getAsJsonArray();
                                p[i] = new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
                            }
                            p[pts.size()] = new double[]{zo.has("bord") ? zo.get("bord").getAsDouble() : 0.0, 0};
                            l.add(p);
                        }
                    }
                }
                zones = l;
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("[spawn] zones dangereuses illisibles ({}) : ignorées", f, ex);
            zones = List.of();
        }
        for (double[][] p : zones) {
            int n = p.length - 1;
            if (n < 3) {
                continue;
            }
            boolean dedans = false;
            double best = Double.MAX_VALUE;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = p[i][0], zi = p[i][1], xj = p[j][0], zj = p[j][1];
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                    dedans = !dedans;
                }
                double dx = xi - xj, dz = zi - zj, l2 = dx * dx + dz * dz;
                double t = l2 > 0 ? Mth.clamp(((x - xj) * dx + (z - zj) * dz) / l2, 0, 1) : 0;
                double qx = xj + t * dx - x, qz = zj + t * dz - z;
                best = Math.min(best, qx * qx + qz * qz);
            }
            if (dedans || Math.sqrt(best) < p[n][0] + 8) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------- gardes
    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        EN_COURS.remove(e.getEntity().getUUID());
        PROTEGES.remove(e.getEntity().getUUID());
    }

    /** Blessé pendant le délai : départ annulé. */
    private static void blesse(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && EN_COURS.remove(sp.getUUID()) != null) {
            sp.displayClientMessage(t("annule_blesse", "Départ annulé : tu as été blessé."), true);
        }
    }

    /** Protection à l'arrivée ; pas de combat entre joueurs au spawn (réglable). */
    private static void attaque(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide()) {
            return;
        }
        if (e.getSource().getEntity() instanceof ServerPlayer att) {
            PROTEGES.remove(att.getUUID());                         // il attaque : il n'est plus protégé
        }
        if (!(e.getEntity() instanceof ServerPlayer cible)) {
            return;
        }
        Integer fin = PROTEGES.get(cible.getUUID());
        if (fin != null && fin >= cible.server.getTickCount() && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            return;
        }
        if (SpawnApi.estAuSpawn(cible.level()) && e.getSource().getEntity() instanceof Player && !DepartConfig.lire(DepartConfig.PVP)) {
            e.setCanceled(true);
        }
    }

    /** Pas de monstres dans le monde du spawn (réglable). */
    private static void arrivee(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Enemy && SpawnApi.estAuSpawn(e.getLevel())
                && !DepartConfig.lire(DepartConfig.MONSTRES)) {
            e.setCanceled(true);
        }
    }

    private static void changementMonde(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.getTo().location().equals(SpawnApi.SPAWN.location())) {
            envoyerBordure(sp);
        }
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && SpawnApi.estAuSpawn(sp.level())) {
            envoyerBordure(sp);
        }
    }

    private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && SpawnApi.estAuSpawn(sp.level())) {
            envoyerBordure(sp);
        }
    }

    // ---------------------------------------------------------------------------------------------- bordure du spawn
    /**
     * Bordure propre au monde du spawn (réglage « bordure »), centrée sur le point de réveil : imposée chaque seconde (le
     * jeu recopie celle de la carte dans les autres mondes) et renvoyée à ceux qui y sont si elle a changé.
     */
    private static void bordure(MinecraftServer server, boolean forcer) {
        ServerLevel l = server.getLevel(SpawnApi.SPAWN);
        int taille = DepartConfig.lire(DepartConfig.BORDURE);
        if (l == null || taille <= 0) {
            return;
        }
        Vec3 c = fr.heralisz.utils.cine.CineDecorServeur.get(server).etat().reveil();
        WorldBorder b = l.getWorldBorder();
        if (!forcer && b.getCenterX() == c.x && b.getCenterZ() == c.z && Math.abs(b.getSize() - taille) < 0.5) {
            return;
        }
        b.setCenter(c.x, c.z);
        b.setSize(taille);
        ClientboundInitializeBorderPacket p = new ClientboundInitializeBorderPacket(b);
        for (ServerPlayer sp : l.players()) {
            sp.connection.send(p);
        }
    }

    private static void envoyerBordure(ServerPlayer sp) {
        if (DepartConfig.lire(DepartConfig.BORDURE) <= 0) {
            return;
        }
        bordure(sp.server, false);
        sp.connection.send(new ClientboundInitializeBorderPacket(sp.serverLevel().getWorldBorder()));
    }

    // ---------------------------------------------------------------------------------------------- commandes (staff)
    private static ServerLevel carteOuErreur(CommandSourceStack src) {
        ServerLevel c = carte(src.getServer());
        if (c == null) {
            src.sendFailure(t("pas_de_carte", "La carte (%s) n'existe pas sur ce serveur : préviens le staff.", DepartConfig.lire(DepartConfig.MONDE)));
        }
        return c;
    }

    private static int poserBorne(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Direction d = sp.getDirection();
        BlockPos p = sp.blockPosition().relative(d);
        ServerLevel l = sp.serverLevel();
        if (!l.getBlockState(p).canBeReplaced()) {
            src.sendFailure(t("borne_place", "Pas de place devant toi pour la borne (%s).", pos(p.getX(), p.getY(), p.getZ())));
            return 0;
        }
        l.setBlock(p, Depart.BORNE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d.getOpposite()), 3);
        Component quoi = t("borne_posee", "Borne de départ posée en %s, tournée vers toi.", pos(p.getX(), p.getY(), p.getZ()));
        src.sendSuccess(() -> quoi, true);
        if (!SpawnApi.estAuSpawn(l) && DepartConfig.lire(DepartConfig.SEULEMENT_AU_SPAWN)) {
            src.sendSuccess(() -> t("borne_hors_spawn", "Attention : tu n'es pas au spawn, elle ne fonctionnera pas ici (réglage seulement_au_spawn)."), false);
        }
        return 1;
    }

    private static int ajouterPoint(CommandSourceStack src, String nom) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel c = carteOuErreur(src);
        if (c == null) {
            return 0;
        }
        if (sp.serverLevel() != c) {
            src.sendFailure(t("pas_sur_carte", "Place-toi sur la carte (%s) pour poser un point d'arrivée.", c.dimension().location().toString()));
            return 0;
        }
        PointsDepart pd = PointsDepart.get(c);
        pd.points.put(nom, new PointsDepart.Point(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot()));
        pd.setDirty();
        src.sendSuccess(() -> t("point_pose", "Point d'arrivée « %s » posé en %s (%s points, %s zones).", nom, pos(sp.getX(), sp.getY(), sp.getZ()),
                String.valueOf(pd.points.size()), String.valueOf(pd.zones.size())), true);
        return 1;
    }

    private static int ajouterZone(CommandSourceStack src, String nom, int rayon) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel c = carteOuErreur(src);
        if (c == null) {
            return 0;
        }
        if (sp.serverLevel() != c) {
            src.sendFailure(t("pas_sur_carte", "Place-toi sur la carte (%s) pour poser un point d'arrivée.", c.dimension().location().toString()));
            return 0;
        }
        PointsDepart pd = PointsDepart.get(c);
        pd.zones.put(nom, new PointsDepart.Zone(sp.getBlockX(), sp.getBlockZ(), rayon));
        pd.setDirty();
        src.sendSuccess(() -> t("zone_posee", "Zone d'arrivée « %s » posée : %s blocs autour de %s (%s points, %s zones).", nom, String.valueOf(rayon),
                pos(sp.getX(), sp.getY(), sp.getZ()), String.valueOf(pd.points.size()), String.valueOf(pd.zones.size())), true);
        return 1;
    }

    private static int retirer(CommandSourceStack src, String nom, boolean zone) {
        ServerLevel c = carteOuErreur(src);
        if (c == null) {
            return 0;
        }
        PointsDepart pd = PointsDepart.get(c);
        boolean ok = (zone ? pd.zones.remove(nom) : pd.points.remove(nom)) != null;
        if (!ok) {
            src.sendFailure(t("inconnu", "Rien de ce nom : « %s » (/depart liste).", nom));
            return 0;
        }
        pd.setDirty();
        src.sendSuccess(() -> t("retire", "« %s » retiré.", nom), true);
        return 1;
    }

    private static int liste(CommandSourceStack src) {
        ServerLevel c = carteOuErreur(src);
        if (c == null) {
            return 0;
        }
        PointsDepart pd = PointsDepart.get(c);
        var s = t("liste", "Points d'arrivée : %s, zones : %s (monde %s).", String.valueOf(pd.points.size()),
                String.valueOf(pd.zones.size()), c.dimension().location().toString()).copy();
        pd.points.forEach((n, p) -> s.append("\n").append(t("liste_point", " • point %s : %s", n, pos(p.x(), p.y(), p.z()))));
        pd.zones.forEach((n, z) -> s.append("\n").append(t("liste_zone", " • zone %s : %s blocs autour de %s", n, String.valueOf(z.rayon()),
                z.x() + " " + z.z())));
        if (pd.points.isEmpty() && pd.zones.isEmpty()) {
            s.append("\n").append(DepartConfig.lire(DepartConfig.CARTE_ENTIERE)
                    ? t("liste_vide_carte", "Rien de posé : un sol sûr est cherché n'importe où dans la bordure de la carte.")
                    : t("liste_vide", "Rien de posé : la borne ne peut envoyer personne (réglage carte_entiere_si_vide)."));
        }
        src.sendSuccess(() -> s, false);
        return 1;
    }

    private static int allerPoint(CommandSourceStack src, String nom) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel c = carteOuErreur(src);
        if (c == null) {
            return 0;
        }
        PointsDepart pd = PointsDepart.get(c);
        PointsDepart.Point p = pd.points.get(nom);
        PointsDepart.Zone z = pd.zones.get(nom);
        if (p != null) {
            sp.teleportTo(c, p.x(), p.y(), p.z(), p.lacet(), 0f);
        } else if (z != null) {
            sp.teleportTo(c, z.x() + 0.5, c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, z.x(), z.z()), z.z() + 0.5, sp.getYRot(), 0f);
        } else {
            src.sendFailure(t("inconnu", "Rien de ce nom : « %s » (/depart liste).", nom));
            return 0;
        }
        return 1;
    }

    private static int essaiCommande(CommandSourceStack src, boolean aller) throws CommandSyntaxException {
        ServerLevel c = carteOuErreur(src);
        if (c == null) {
            return 0;
        }
        ServerPlayer sp = src.getPlayer();
        Arrivee a = chercher(c, sp, DepartConfig.lire(DepartConfig.ESSAIS));
        if (a == null) {
            src.sendFailure(t("essai_rien", "Aucun point d'arrivée sûr trouvé en %s essais (/depart liste, réglages heraliz_spawn-server.toml).",
                    String.valueOf(DepartConfig.lire(DepartConfig.ESSAIS))));
            return 0;
        }
        src.sendSuccess(() -> t("essai", "Point d'arrivée trouvé : %s (%s).", pos(a.x(), a.y(), a.z()), a.origine()), false);
        if (aller) {
            src.getPlayerOrException().teleportTo(c, a.x(), a.y(), a.z(), a.lacet(), 0f);
        }
        return 1;
    }

    private static int envoyer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel c = carteOuErreur(ctx.getSource());
        if (c == null) {
            return 0;
        }
        int n = 0;
        for (ServerPlayer sp : EntityArgument.getPlayers(ctx, "cibles")) {
            Arrivee a = chercher(c, sp, DepartConfig.lire(DepartConfig.ESSAIS));
            if (a == null) {
                echec(sp);
                continue;
            }
            EN_COURS.remove(sp.getUUID());
            partir(sp, c, a);
            n++;
        }
        int total = n;
        ctx.getSource().sendSuccess(() -> t("envoyes", "%s joueur(s) envoyé(s) sur la carte.", String.valueOf(total)), true);
        return n;
    }

    private static int info(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        ServerLevel spawn = server.getLevel(SpawnApi.SPAWN);
        ServerLevel c = carte(server);
        var s = (spawn == null ? t("info_spawn_absent", "Monde du spawn : absent (jar serveur de HeraliZ Utils sans ses données ?).")
                : CineReseauServeur.reveilPraticable(server) ? t("info_spawn_pret", "Monde du spawn : prêt (point de réveil praticable).")
                : t("info_spawn_vide", "Monde du spawn : déclaré, mais rien au point de réveil (monde pas encore déposé ?).")).copy();
        s.append("\n").append(c == null ? t("info_carte_absente", "Carte : absente (réglage monde).")
                : t("info_carte", "Carte : %s.", c.dimension().location().toString()));
        if (c != null) {
            PointsDepart pd = PointsDepart.get(c);
            s.append("\n").append(t("info_points", "Points d'arrivée : %s, zones : %s.", String.valueOf(pd.points.size()), String.valueOf(pd.zones.size())));
        }
        s.append("\n").append(ProtectionSpawn.active()
                ? t("info_protection", "Protection du spawn : active (aucun dégât, aucune créature, explosions sans effet).")
                : t("info_protection_coupee", "Protection du spawn : coupée (réglage [protection] actif)."));
        s.append("\n").append(t("info_regles", "Délai %s s, aucun joueur à moins de %s blocs, protection %s s à l'arrivée.",
                String.valueOf(DepartConfig.lire(DepartConfig.DELAI)), String.valueOf(DepartConfig.lire(DepartConfig.DISTANCE_JOUEURS)),
                String.valueOf(DepartConfig.lire(DepartConfig.PROTECTION))));
        src.sendSuccess(() -> s, false);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> noms(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b, boolean zone) {
        ServerLevel c = carte(ctx.getSource().getServer());
        if (c == null) {
            return b.buildFuture();
        }
        PointsDepart pd = PointsDepart.get(c);
        List<String> l = new ArrayList<>(zone ? pd.zones.keySet() : pd.points.keySet());
        if (!zone) {
            l.addAll(pd.zones.keySet());
        }
        return SharedSuggestionProvider.suggest(l, b);
    }

    private static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("depart").requires(s -> s.hasPermission(2))
                .executes(c -> info(c.getSource()))
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("borne").executes(c -> poserBorne(c.getSource())))
                .then(Commands.literal("liste").executes(c -> liste(c.getSource())))
                .then(Commands.literal("point")
                        .then(Commands.literal("ajouter").then(Commands.argument("nom", StringArgumentType.word())
                                .executes(c -> ajouterPoint(c.getSource(), StringArgumentType.getString(c, "nom")))))
                        .then(Commands.literal("retirer").then(Commands.argument("nom", StringArgumentType.word())
                                .suggests((c, b) -> noms(c, b, false)).executes(c -> retirer(c.getSource(), StringArgumentType.getString(c, "nom"), false))))
                        .then(Commands.literal("tp").then(Commands.argument("nom", StringArgumentType.word())
                                .suggests((c, b) -> noms(c, b, false)).executes(c -> allerPoint(c.getSource(), StringArgumentType.getString(c, "nom")))))
                        .then(Commands.literal("liste").executes(c -> liste(c.getSource()))))
                .then(Commands.literal("zone")
                        .then(Commands.literal("ajouter").then(Commands.argument("nom", StringArgumentType.word())
                                .then(Commands.argument("rayon", IntegerArgumentType.integer(4, 2048))
                                        .executes(c -> ajouterZone(c.getSource(), StringArgumentType.getString(c, "nom"),
                                                IntegerArgumentType.getInteger(c, "rayon"))))))
                        .then(Commands.literal("retirer").then(Commands.argument("nom", StringArgumentType.word())
                                .suggests((c, b) -> noms(c, b, true)).executes(c -> retirer(c.getSource(), StringArgumentType.getString(c, "nom"), true))))
                        .then(Commands.literal("liste").executes(c -> liste(c.getSource()))))
                .then(Commands.literal("essai").executes(c -> essaiCommande(c.getSource(), false))
                        .then(Commands.literal("tp").executes(c -> essaiCommande(c.getSource(), true))))
                .then(Commands.literal("joueur").then(Commands.argument("cibles", EntityArgument.players()).executes(DepartServeur::envoyer))));
    }
}
