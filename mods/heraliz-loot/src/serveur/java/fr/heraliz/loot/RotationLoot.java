package fr.heraliz.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rotation du loot des conteneurs T1-T6 et des familles (un fichier par dimension : {@code heraliz_loot_rotation.dat}).
 * <p>Chaque zone ({@link ZonesLoot}) a N conteneurs actifs tirés au hasard (N = arrondi(part x taille), au moins 1) ;
 * les autres sont vides et habillent les intérieurs. Le contenu d'un actif est tiré dans sa table à la première
 * ouverture ({@link TablesCoffres#pourConteneur}) puis gardé tel quel. Quand le dernier joueur referme un actif vide, il
 * redevient inactif et, après un délai (configuration), un AUTRE conteneur de la zone tiré au hasard devient actif. Les
 * actifs se voient : étoiles blanches qui scintillent autour d'eux, envoyées aux seuls joueurs proches.</p>
 * <p>Tout est logique : un conteneur peut être actif dans un secteur non chargé. Quand on l'approche ou qu'on l'ouvre,
 * on vérifie que le bloc en place est bien le conteneur attendu ; sinon un autre conteneur de la zone prend sa place.</p>
 * <p>Changer le loot de place est automatique : un conteneur posé (à la main, /setblock, ou trouvé à l'ouverture) entre
 * dans la rotation. À sa place d'origine avec son modèle d'origine, il reprend sa place ; sinon (autre modèle, autre
 * niveau, nouvel endroit) il rejoint la zone de son niveau la plus proche, à moins de 64 blocs
 * ({@link ZonesLoot#rattacher}), ou en commence une nouvelle (« ajouts/t3_x_z », avec la part d'actifs de son niveau) ;
 * il se remplit, quand son tour vient, dans sa propre table (un T6 posé à la place d'un T5 donne du loot T6). Un
 * conteneur cassé : sa place d'origine attend un conteneur (« invalide »), un conteneur posé en jeu sort de la rotation
 * (une zone créée en jeu qui se vide disparaît). Ces changements et les parts réglées par commande sont gardés avec le
 * monde ({@link #places}, {@link #zonesAjoutees}, {@link #parts}).</p>
 * <p>Salles à carte d'accès (HeraliZ Bunker, par {@link SallesLoot}) : les conteneurs posés dans la boîte d'une salle
 * forment une zone à part, « salles_carte/&lt;nom&gt; », qui ne se remplit JAMAIS d'elle-même (ni réapparition après un
 * conteneur vidé, ni complément au démarrage) : seulement quand Bunker la recharge (salle vide depuis le délai de sa
 * couleur), et alors tous ses conteneurs à la fois (ou la part réglée par « /heralizloot zone ... part »).</p>
 */
public final class RotationLoot extends SavedData {
    private static final String FICHIER = "heraliz_loot_rotation";
    static final int TAILLE = 27;
    /** Pas de position exclue. */
    static final long AUCUNE = Long.MIN_VALUE;
    /** Préfixe des zones des salles à carte (« salles_carte/labo »). */
    static final String PREFIXE_SALLE = "salles_carte/";

    /** Zone d'une salle à carte : remplie seulement sur demande de Bunker ({@link SallesLoot#recharger}). */
    static boolean estSalle(String zone) {
        return zone.startsWith(PREFIXE_SALLE);
    }

    /** Une salle à carte : son nom affiché et sa boîte (blocs min et max compris). */
    record Salle(String nom, long min, long max) {
        boolean contient(long k) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            return x >= BlockPos.getX(min) && x <= BlockPos.getX(max) && y >= BlockPos.getY(min) && y <= BlockPos.getY(max)
                    && z >= BlockPos.getZ(min) && z <= BlockPos.getZ(max);
        }
    }

    /** Un conteneur actif : sa zone, son nom, son contenu (null tant que personne ne l'a ouvert). */
    static final class Actif {
        String zone;
        final String nom;
        @Nullable
        Inventaire contenu;

        Actif(String zone, String nom) {
            this.zone = zone;
            this.nom = nom;
        }
    }

    /** Une réapparition programmée : un conteneur de la zone (autre que exclu) deviendra actif à l'heure dite (ticks). */
    record Attente(String zone, long heure, long exclu) {}

    /** Un conteneur posé ou changé en jeu : sa zone et son nom. */
    record Place(String zone, String nom) {}

    /** Une zone créée en jeu : son nom et sa part d'actifs (celle de son niveau, null : configuration). */
    record ZoneAjoutee(String nom, @Nullable Double actifs) {}

    final Long2ObjectOpenHashMap<Actif> actifs = new Long2ObjectOpenHashMap<>();
    final List<Attente> attentes = new ArrayList<>();
    /** Positions retirées de leur zone (le bloc en place n'est pas le conteneur attendu) -> nom attendu. */
    final Long2ObjectOpenHashMap<String> invalides = new Long2ObjectOpenHashMap<>();
    /** Conteneurs posés ou changés en jeu (position -> zone, nom) : remis dans les zones à chaque lecture des fichiers. */
    final Long2ObjectOpenHashMap<Place> places = new Long2ObjectOpenHashMap<>();
    /** Zones créées en jeu (identifiant -> nom, part). */
    final Map<String, ZoneAjoutee> zonesAjoutees = new java.util.TreeMap<>();
    /** Parts d'actifs réglées par commande (identifiant de zone -> part, 0 à 1). */
    final Map<String, Double> parts = new java.util.TreeMap<>();
    /** Salles à carte déclarées par HeraliZ Bunker (nom de la salle -> boîte). */
    final Map<String, Salle> salles = new java.util.TreeMap<>();
    /** Actifs dont le contenu est tiré. */
    private final LongOpenHashSet tires = new LongOpenHashSet();
    /** Index spatial des actifs (étoiles) : secteur -> positions. */
    private final Long2ObjectOpenHashMap<LongArrayList> parSecteur = new Long2ObjectOpenHashMap<>();
    private int versionVue = -1;
    @Nullable
    private ServerLevel niveau;

    public static RotationLoot get(ServerLevel l) {
        RotationLoot r = l.getDataStorage().computeIfAbsent(RotationLoot::charger, RotationLoot::new, FICHIER);
        r.niveau = l;
        return r;
    }

    // ------------------------------------------------------------------------------------------ sauvegarde
    static RotationLoot charger(CompoundTag t) {
        RotationLoot r = new RotationLoot();
        for (Tag x : t.getList("Actifs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            long k = c.getLong("Pos");
            Actif a = new Actif(c.getString("Zone"), c.getString("Nom"));
            if (c.getBoolean("Tire")) {
                Inventaire inv = new Inventaire(r, k);
                ContainerHelper.loadAllItems(c, inv.objets);
                a.contenu = inv;
                r.tires.add(k);
            }
            r.actifs.put(k, a);
            r.indexer(k);
        }
        for (Tag x : t.getList("Attentes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            r.attentes.add(new Attente(c.getString("Zone"), c.getLong("Heure"), c.contains("Exclu") ? c.getLong("Exclu") : AUCUNE));
        }
        for (Tag x : t.getList("Invalides", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            r.invalides.put(c.getLong("Pos"), c.getString("Nom"));
        }
        for (Tag x : t.getList("Places", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            r.places.put(c.getLong("Pos"), new Place(c.getString("Zone"), c.getString("Nom")));
        }
        for (Tag x : t.getList("ZonesAjoutees", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            r.zonesAjoutees.put(c.getString("Id"), new ZoneAjoutee(c.getString("Nom"), c.contains("Actifs") ? c.getDouble("Actifs") : null));
        }
        for (Tag x : t.getList("Parts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            r.parts.put(c.getString("Zone"), c.getDouble("Part"));
        }
        for (Tag x : t.getList("Salles", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            r.salles.put(c.getString("Id"), new Salle(c.getString("Nom"), c.getLong("Min"), c.getLong("Max")));
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag la = new ListTag();
        for (Long2ObjectMap.Entry<Actif> e : actifs.long2ObjectEntrySet()) {
            Actif a = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getLongKey());
            c.putString("Zone", a.zone);
            c.putString("Nom", a.nom);
            if (a.contenu != null) {
                c.putBoolean("Tire", true);
                ContainerHelper.saveAllItems(c, a.contenu.objets, true);
            }
            la.add(c);
        }
        t.put("Actifs", la);
        ListTag lt = new ListTag();
        for (Attente a : attentes) {
            CompoundTag c = new CompoundTag();
            c.putString("Zone", a.zone());
            c.putLong("Heure", a.heure());
            if (a.exclu() != AUCUNE) c.putLong("Exclu", a.exclu());
            lt.add(c);
        }
        t.put("Attentes", lt);
        ListTag li = new ListTag();
        for (Long2ObjectMap.Entry<String> e : invalides.long2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getLongKey());
            c.putString("Nom", e.getValue());
            li.add(c);
        }
        t.put("Invalides", li);
        ListTag lp = new ListTag();
        for (Long2ObjectMap.Entry<Place> e : places.long2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getLongKey());
            c.putString("Zone", e.getValue().zone());
            c.putString("Nom", e.getValue().nom());
            lp.add(c);
        }
        t.put("Places", lp);
        ListTag lz = new ListTag();
        zonesAjoutees.forEach((id, z) -> {
            CompoundTag c = new CompoundTag();
            c.putString("Id", id);
            c.putString("Nom", z.nom());
            if (z.actifs() != null) c.putDouble("Actifs", z.actifs());
            lz.add(c);
        });
        t.put("ZonesAjoutees", lz);
        ListTag lq = new ListTag();
        parts.forEach((id, p) -> {
            CompoundTag c = new CompoundTag();
            c.putString("Zone", id);
            c.putDouble("Part", p);
            lq.add(c);
        });
        t.put("Parts", lq);
        ListTag ls = new ListTag();
        salles.forEach((id, s) -> {
            CompoundTag c = new CompoundTag();
            c.putString("Id", id);
            c.putString("Nom", s.nom());
            c.putLong("Min", s.min());
            c.putLong("Max", s.max());
            ls.add(c);
        });
        t.put("Salles", ls);
        return t;
    }

    // ------------------------------------------------------------------------------------------ actifs
    private static long secteur(long k) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(BlockPos.getX(k)), SectionPos.blockToSectionCoord(BlockPos.getZ(k)));
    }

    private void indexer(long k) {
        parSecteur.computeIfAbsent(secteur(k), s -> new LongArrayList()).add(k);
    }

    private void ajouterActif(long k, Actif a) {
        if (actifs.put(k, a) == null) indexer(k);
        setDirty();
    }

    @Nullable
    private Actif retirerActif(long k) {
        Actif a = actifs.remove(k);
        if (a == null) return null;
        tires.remove(k);
        long s = secteur(k);
        LongArrayList l = parSecteur.get(s);
        if (l != null) {
            l.rem(k);
            if (l.isEmpty()) parSecteur.remove(s);
        }
        setDirty();
        return a;
    }

    private boolean libre(long k, long exclu) {
        return k != exclu && !actifs.containsKey(k) && !invalides.containsKey(k);
    }

    /** Rend actif un conteneur de la zone tiré au hasard, autre que exclu (sauf s'il est le seul possible). */
    boolean activerUn(ZonesLoot.Zone z, long exclu, RandomSource r) {
        int n = z.taille();
        if (n == 0) return false;
        for (int essai = 0; essai < 32; essai++) {                   // rapide : la plupart des conteneurs sont libres
            int i = r.nextInt(n);
            if (libre(z.position(i), exclu)) {
                ajouterActif(z.position(i), new Actif(z.id, z.conteneur(i)));
                return true;
            }
        }
        IntArrayList libres = new IntArrayList();
        int seul = -1;
        for (int i = 0; i < n; i++) {
            long k = z.position(i);
            if (libre(k, exclu)) libres.add(i);
            else if (k == exclu && libre(k, AUCUNE)) seul = i;
        }
        int i = libres.isEmpty() ? seul : libres.getInt(r.nextInt(libres.size()));
        if (i < 0) return false;
        ajouterActif(z.position(i), new Actif(z.id, z.conteneur(i)));
        return true;
    }

    /** Un actif vient d'être vidé : il redevient inactif, un autre conteneur de sa zone se remplira après le délai. */
    private void vider(long k) {
        Actif a = retirerActif(k);
        if (a == null || niveau == null) return;
        if (estSalle(a.zone)) return;                                   // salle à carte : rechargée par Bunker seulement
        attentes.add(new Attente(a.zone, niveau.getGameTime() + ConfigLoot.delaiTicks(niveau.random), k));
        setDirty();
    }

    /**
     * Le conteneur en k n'est plus là (cassé, ou autre chose en place) : s'il était actif, un autre conteneur de sa zone
     * prend sa place. Une place d'origine (fichier de la zone) attend son conteneur (« invalide ») ; un conteneur posé en
     * jeu sort de la rotation.
     */
    void invalider(ServerLevel l, long k, String trouve, boolean retire) {
        ResourceKey<Level> dim = l.dimension();
        ZonesLoot.Zone avant = ZonesLoot.zoneDe(dim, k);
        if (avant == null) return;
        Actif a = retirerActif(k);
        places.remove(k);
        ZonesLoot.Zone origine = ZonesLoot.origine(dim, k);
        String n = origine == null ? null : origine.duFichier(k);
        if (n != null) {
            ZonesLoot.placer(origine, k, n);
            invalides.put(k, n);
        } else {
            ZonesLoot.enlever(dim, k);
            invalides.remove(k);
        }
        nettoyer(dim, avant);
        setDirty();
        if (a == null) return;
        if (retire) {
            HeralizLoot.LOG.info("Rotation : {} retiré en {} (zone {}), un autre conteneur prend sa place", a.nom, BlockPos.of(k).toShortString(), a.zone);
        } else {
            HeralizLoot.LOG.warn("Rotation : {} attendu en {} (zone {}) mais {} en place ; un autre conteneur prend sa place",
                    a.nom, BlockPos.of(k).toShortString(), a.zone, trouve);
        }
        ZonesLoot.Zone z = ZonesLoot.zone(dim, a.zone);
        if (z != null && !estSalle(z.id)) activerUn(z, k, l.random);
    }

    /**
     * Le conteneur nom est en k : il entre dans la rotation. Avec le modèle de sa place d'origine, il y revient ; sinon il
     * rejoint la zone la plus proche de son niveau ({@link ZonesLoot#rattacher}). -> sa zone (null : aucune zone).
     */
    @Nullable
    ZonesLoot.Zone adopter(ServerLevel l, long k, String nom) {
        ResourceKey<Level> dim = l.dimension();
        String salle = salleDe(k);
        ZonesLoot.Zone origine = ZonesLoot.origine(dim, k);
        boolean chezLui = salle == null && origine != null && nom.equals(origine.duFichier(k));
        ZonesLoot.Zone z = salle != null ? zoneSalle(l, salle) : chezLui ? origine : ZonesLoot.rattacher(dim, k, nom);
        if (z == null) z = creerZone(l, k, nom);
        ZonesLoot.Zone avant = ZonesLoot.zoneDe(dim, k);
        ZonesLoot.placer(z, k, nom);
        if (avant != null && avant != z) nettoyer(dim, avant);
        invalides.remove(k);
        if (chezLui) places.remove(k);
        else places.put(k, new Place(z.id, nom));
        Actif a = actifs.get(k);
        if (a != null && !(a.nom.equals(nom) && a.zone.equals(z.id))) {
            if (a.zone.equals(z.id)) {                                  // même zone : le nouveau modèle reste actif
                Actif b = new Actif(z.id, nom);
                b.contenu = a.contenu;
                actifs.put(k, b);
            } else {                                                    // autre zone : l'ancienne en active un autre
                retirerActif(k);
                ZonesLoot.Zone za = ZonesLoot.zone(dim, a.zone);
                if (za != null && !estSalle(za.id)) activerUn(za, k, l.random);
            }
        }
        if (avant != z) {
            HeralizLoot.LOG.info("Rotation : {} posé en {}, rejoint la zone {}", nom, BlockPos.of(k).toShortString(), z.id);
        }
        if (!estSalle(z.id)) completer(l, z);                           // une salle attend sa recharge (Bunker)
        setDirty();
        return z;
    }

    // ------------------------------------------------------------------------------------------ salles à carte
    /** Salle à carte dont la boîte contient k (null : aucune). */
    @Nullable
    String salleDe(long k) {
        for (Map.Entry<String, Salle> e : salles.entrySet()) {
            if (e.getValue().contient(k)) return e.getKey();
        }
        return null;
    }

    /** Zone de la salle id (créée si besoin) : part d'actifs 1 (tout se remplit), niveau -1 (aucun conteneur posé
     *  ailleurs ne s'y rattache). */
    private ZonesLoot.Zone zoneSalle(ServerLevel l, String id) {
        Salle s = salles.get(id);
        ZonesLoot.Zone z = ZonesLoot.creer(l.dimension(), PREFIXE_SALLE + id, s == null ? id : s.nom(), 1.0, -1);
        Double p = parts.get(z.id);
        if (p != null) z.partForcee = p;
        return z;
    }

    /** État à jour des zones (démarrage, /reload) avant de toucher aux salles. */
    void aJour(ServerLevel l) {
        int v = ZonesLoot.version();
        if (versionVue != v) {
            versionVue = v;
            reconcilier(l);
        }
    }

    /**
     * Déclare la salle id (boîte min..max, blocs compris) : les conteneurs de loot qui y sont (connus des zones, ou posés
     * dans un secteur chargé) quittent leur zone pour celle de la salle, vides jusqu'à la prochaine recharge. Même boîte
     * qu'avant : rien ne change. -> nombre de conteneurs de la salle.
     */
    int declarerSalle(ServerLevel l, String id, String nom, BlockPos a, BlockPos b) {
        aJour(l);
        long min = BlockPos.asLong(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        long max = BlockPos.asLong(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        Salle avant = salles.get(id);
        if (avant != null && avant.min() == min && avant.max() == max) {
            ZonesLoot.Zone z = ZonesLoot.zone(l.dimension(), PREFIXE_SALLE + id);
            return z == null ? 0 : z.taille();
        }
        if (avant != null) retirerSalle(l, id);
        Salle s = new Salle(nom, min, max);
        salles.put(id, s);
        setDirty();
        ResourceKey<Level> dim = l.dimension();
        ZonesLoot.Zone zs = zoneSalle(l, id);
        // 1. conteneurs déjà dans une zone (même dans un secteur non chargé)
        Map<Long, String> dedans = new HashMap<>();
        for (Long2ObjectMap.Entry<ZonesLoot.Zone> e : ZonesLoot.index(dim).long2ObjectEntrySet()) {
            long k = e.getLongKey();
            if (e.getValue() == zs || invalides.containsKey(k) || !s.contient(k) || !id.equals(salleDe(k))) continue;
            String n = e.getValue().conteneur(k);
            if (n != null) dedans.put(k, n);
        }
        // 2. conteneurs des secteurs chargés (le modèle réellement posé l'emporte)
        int x0 = BlockPos.getX(min), y0 = BlockPos.getY(min), z0 = BlockPos.getZ(min);
        int x1 = BlockPos.getX(max), y1 = BlockPos.getY(max), z1 = BlockPos.getZ(max);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk ch = l.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                    for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                        for (int y = y0; y <= y1; y++) {
                            BlockState st = ch.getBlockState(mp.set(x, y, z));
                            if (st.getBlock() instanceof ConteneurBlock cb && avecLoot(cb)) {
                                long k = mp.asLong();
                                if (id.equals(salleDe(k))) dedans.put(k, cb.nom);
                            }
                        }
                    }
                }
            }
        }
        dedans.forEach((k, n) -> adopter(l, k, n));
        ZonesLoot.invalider();                                          // les zones quittées refont leurs comptes
        HeralizLoot.LOG.info("Rotation : salle à carte {} ({}), {} conteneurs", id, nom, zs.taille());
        return zs.taille();
    }

    /** Retire la salle id : ses conteneurs retournent dans les zones (comme posés en jeu), sans leur loot. */
    boolean retirerSalle(ServerLevel l, String id) {
        aJour(l);
        if (salles.remove(id) == null) return false;
        setDirty();
        ResourceKey<Level> dim = l.dimension();
        String zid = PREFIXE_SALLE + id;
        parts.remove(zid);
        ZonesLoot.Zone zs = ZonesLoot.zone(dim, zid);
        if (zs != null) {
            Map<Long, String> siens = new HashMap<>();
            for (int i = 0; i < zs.taille(); i++) siens.put(zs.position(i), zs.conteneur(i));
            for (long k : siens.keySet()) {
                Actif a = actifs.get(k);
                if (a != null && a.zone.equals(zid)) retirerActif(k);
                places.remove(k);
            }
            ZonesLoot.supprimer(dim, zid);
            siens.forEach((k, n) -> adopter(l, k, n));
        }
        ZonesLoot.invalider();
        HeralizLoot.LOG.info("Rotation : salle à carte {} retirée", id);
        return true;
    }

    /** Recharge la salle id : tous ses conteneurs (ou la part réglée) se remplissent, contenus en cours oubliés.
     *  -> conteneurs remplis, -1 si la salle est inconnue. */
    int rechargerSalle(ServerLevel l, String id) {
        aJour(l);
        if (!salles.containsKey(id)) return -1;
        return retirerZone(l, zoneSalle(l, id));
    }

    /** {conteneurs, remplis, déjà ouverts} de la salle id, null si elle est inconnue. */
    @Nullable
    int[] etatSalle(ServerLevel l, String id) {
        aJour(l);
        if (!salles.containsKey(id)) return null;
        String zid = PREFIXE_SALLE + id;
        ZonesLoot.Zone z = ZonesLoot.zone(l.dimension(), zid);
        int n = 0, o = 0;
        for (Actif a : actifs.values()) {
            if (!a.zone.equals(zid)) continue;
            n++;
            if (a.contenu != null) o++;
        }
        return new int[]{z == null ? 0 : z.taille(), n, o};
    }

    /** Nouvelle zone pour un conteneur posé loin de toute zone de son niveau : « ajouts/t3_120_-450 ». */
    private ZonesLoot.Zone creerZone(ServerLevel l, long k, String nom) {
        int niv = ZonesLoot.niveau(nom), x = BlockPos.getX(k), z = BlockPos.getZ(k);
        String id = "ajouts/t" + niv + "_" + x + "_" + z;
        ZoneAjoutee za = zonesAjoutees.get(id);
        if (za == null) {
            za = new ZoneAjoutee("Posé en jeu, T" + niv + " (" + x + ", " + z + ")", ZonesLoot.partNiveau(l.dimension(), niv));
            zonesAjoutees.put(id, za);
            setDirty();
            HeralizLoot.LOG.info("Rotation : nouvelle zone {} ({})", id, za.nom());
        }
        ZonesLoot.Zone zo = ZonesLoot.creer(l.dimension(), id, za.nom(), za.actifs(), niv);
        Double p = parts.get(id);
        if (p != null) zo.partForcee = p;
        return zo;
    }

    /** Niveau d'une zone créée en jeu, lu dans son identifiant (ajouts/t3_x_z -> 3). */
    private static int niveauAjout(String id) {
        int i = id.indexOf("/t"), j = id.indexOf('_', i + 2);
        try {
            return Integer.parseInt(id.substring(i + 2, j));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Une zone créée en jeu qui n'a plus de conteneur disparaît (avec ses réapparitions et sa part réglée). */
    private void nettoyer(ResourceKey<Level> dim, ZonesLoot.Zone z) {
        if (z.taille() > 0 || !zonesAjoutees.containsKey(z.id)) return;
        zonesAjoutees.remove(z.id);
        parts.remove(z.id);
        attentes.removeIf(a -> a.zone().equals(z.id));
        ZonesLoot.supprimer(dim, z.id);
        setDirty();
        HeralizLoot.LOG.info("Rotation : zone {} vide, retirée", z.id);
    }

    /** Part d'actifs d'une zone réglée par commande (null : celle du fichier ou de son niveau). */
    void regler(ZonesLoot.Zone z, @Nullable Double part) {
        if (part == null) parts.remove(z.id);
        else parts.put(z.id, part);
        z.partForcee = part;
        setDirty();
        ZonesLoot.invalider();                                          // les comptes se refont au prochain tick
    }

    /** Zone qui a grandi (ou retrouvé un conteneur) : si sa part d'actifs le demande, un conteneur de plus devient actif. */
    private void completer(ServerLevel l, ZonesLoot.Zone z) {
        int a = 0, inv = 0;
        for (Actif x : actifs.values()) {
            if (x.zone.equals(z.id)) a++;
        }
        for (Attente x : attentes) {
            if (x.zone().equals(z.id)) a++;
        }
        for (int i = 0; i < z.taille(); i++) {
            if (invalides.containsKey(z.position(i))) inv++;
        }
        int cible = z.cible(z.taille() - inv);
        while (a < cible && activerUn(z, AUCUNE, l.random)) a++;
    }

    /** Remet dans les zones (qui viennent d'être lues) les zones créées en jeu, les parts réglées et les conteneurs
     *  posés ou changés en jeu. */
    private void remettrePlaces(ServerLevel l) {
        ResourceKey<Level> dim = l.dimension();
        zonesAjoutees.forEach((id, za) -> ZonesLoot.creer(dim, id, za.nom(), za.actifs(), niveauAjout(id)));
        salles.forEach((id, s) -> ZonesLoot.creer(dim, PREFIXE_SALLE + id, s.nom(), 1.0, -1));
        if (parts.keySet().removeIf(id -> ZonesLoot.zone(dim, id) == null)) setDirty();
        parts.forEach((id, p) -> ZonesLoot.zone(dim, id).partForcee = p);
        if (places.isEmpty()) return;
        LongArrayList perdus = new LongArrayList();
        Map<Long, Place> deplaces = new HashMap<>();
        for (Long2ObjectMap.Entry<Place> e : places.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            Place p = e.getValue();
            ZonesLoot.Zone z = ZonesLoot.zone(dim, p.zone());
            if (z == null) {                                            // zone disparue : la plus proche, ou une nouvelle
                z = ZonesLoot.rattacher(dim, k, p.nom());
                if (z == null) z = creerZone(l, k, p.nom());
                deplaces.put(k, new Place(z.id, p.nom()));
            }
            ZonesLoot.placer(z, k, p.nom());
        }
        for (long k : perdus) places.remove(k);
        deplaces.forEach((k, p) -> places.put((long) k, p));
        if (!perdus.isEmpty() || !deplaces.isEmpty()) setDirty();
    }

    /** Quelqu'un regarde-t-il dans cet inventaire ? (autre que sauf) */
    private boolean regarde(Container inv, @Nullable Player sauf) {
        if (niveau == null) return false;
        for (ServerPlayer q : niveau.players()) {
            if (q != sauf && q.containerMenu instanceof ChestMenu cm && cm.getContainer() == inv) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ zones
    /**
     * Met l'état en accord avec les zones (démarrage, /reload, configuration) : positions sorties des zones oubliées,
     * zones ramenées à leur nombre d'actifs (actifs + réapparitions en attente = cible).
     */
    void reconcilier(ServerLevel l) {
        remettrePlaces(l);
        if (!salles.isEmpty()) {                                        // conteneur d'un fichier ajouté depuis dans une salle
            Map<Long, String> dedans = new HashMap<>();
            for (Long2ObjectMap.Entry<ZonesLoot.Zone> e : ZonesLoot.index(l.dimension()).long2ObjectEntrySet()) {
                long k = e.getLongKey();
                if (estSalle(e.getValue().id) || invalides.containsKey(k) || salleDe(k) == null) continue;
                String n = e.getValue().conteneur(k);
                if (n != null) dedans.put(k, n);
            }
            dedans.forEach((k, n) -> adopter(l, k, n));
        }
        Map<String, ZonesLoot.Zone> zones = ZonesLoot.zones(l.dimension());
        Object2IntOpenHashMap<String> nbInvalides = new Object2IntOpenHashMap<>();
        ObjectIterator<Long2ObjectMap.Entry<String>> ii = invalides.long2ObjectEntrySet().iterator();
        while (ii.hasNext()) {
            Long2ObjectMap.Entry<String> e = ii.next();
            ZonesLoot.Zone z = ZonesLoot.zoneDe(l.dimension(), e.getLongKey());
            if (z == null || !e.getValue().equals(z.conteneur(e.getLongKey()))) {
                ii.remove();
                setDirty();
            } else {
                nbInvalides.addTo(z.id, 1);
            }
        }
        Map<String, LongArrayList> parZone = new HashMap<>();
        LongArrayList perimes = new LongArrayList();
        for (Long2ObjectMap.Entry<Actif> e : actifs.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            Actif a = e.getValue();
            ZonesLoot.Zone z = ZonesLoot.zoneDe(l.dimension(), k);
            if (z == null || !a.nom.equals(z.conteneur(k)) || invalides.containsKey(k)) {
                perimes.add(k);
                continue;
            }
            if (!z.id.equals(a.zone)) {
                a.zone = z.id;
                setDirty();
            }
            parZone.computeIfAbsent(z.id, x -> new LongArrayList()).add(k);
        }
        for (long k : perimes) retirerActif(k);
        if (attentes.removeIf(a -> !zones.containsKey(a.zone()))) setDirty();
        Object2IntOpenHashMap<String> nbAttentes = new Object2IntOpenHashMap<>();
        for (Attente a : attentes) nbAttentes.addTo(a.zone(), 1);
        int ajoutes = 0, retires = perimes.size();
        for (ZonesLoot.Zone z : zones.values()) {
            if (estSalle(z.id)) continue;                               // salle à carte : rien sans recharge de Bunker
            int cible = z.cible(z.taille() - nbInvalides.getInt(z.id));
            LongArrayList act = parZone.getOrDefault(z.id, new LongArrayList());
            int a = act.size(), p = nbAttentes.getInt(z.id);
            if (a + p > cible && p > 0) {                               // trop : d'abord les réapparitions les plus lointaines
                List<Attente> siennes = new ArrayList<>(attentes.stream().filter(x -> x.zone().equals(z.id)).toList());
                siennes.sort((x, y) -> Long.compare(y.heure(), x.heure()));
                for (int i = 0; i < siennes.size() && a + p > cible; i++, p--) attentes.remove(siennes.get(i));
                setDirty();
            }
            if (a > cible) {                                            // puis les actifs jamais ouverts, puis les autres
                long[] ks = act.toLongArray();
                for (int i = ks.length - 1; i > 0; i--) {
                    int j = l.random.nextInt(i + 1);
                    long t = ks[i];
                    ks[i] = ks[j];
                    ks[j] = t;
                }
                for (int passe = 0; passe < 2 && a > cible; passe++) {
                    for (long k : ks) {
                        if (a <= cible) break;
                        Actif x = actifs.get(k);
                        if (x == null || (passe == 0) != (x.contenu == null) || (x.contenu != null && regarde(x.contenu, null))) continue;
                        retirerActif(k);
                        a--;
                        retires++;
                    }
                }
            }
            while (a + p < cible && activerUn(z, AUCUNE, l.random)) {
                a++;
                ajoutes++;
            }
        }
        if (!zones.isEmpty() || ajoutes > 0 || retires > 0) {
            HeralizLoot.LOG.info("Rotation du loot ({}) : {} zones, {} actifs ({} ajoutés, {} retirés), {} en attente, {} invalides, {} posés en jeu",
                    l.dimension().location(), zones.size(), actifs.size(), ajoutes, retires, attentes.size(), invalides.size(), places.size());
        }
    }

    /** Commande « zone retirer » : nouveaux actifs tirés au hasard (contenus et réapparitions oubliés). */
    int retirerZone(ServerLevel l, ZonesLoot.Zone z) {
        LongArrayList ks = new LongArrayList();
        for (Long2ObjectMap.Entry<Actif> e : actifs.long2ObjectEntrySet()) {
            if (e.getValue().zone.equals(z.id)) ks.add(e.getLongKey());
        }
        for (long k : ks) retirerActif(k);
        attentes.removeIf(a -> a.zone().equals(z.id));
        for (int i = 0; i < z.taille(); i++) invalides.remove(z.position(i));   // revérifiés quand on les approchera
        int cible = z.cible(z.taille()), n = 0;
        while (n < cible && activerUn(z, AUCUNE, l.random)) n++;
        setDirty();
        return n;
    }

    // ------------------------------------------------------------------------------------------ chaque seconde
    static void tick(ServerLevel l) {
        RotationLoot e = get(l);
        e.aJour(l);
        if (!e.attentes.isEmpty()) {
            long now = l.getGameTime();
            List<Attente> dues = new ArrayList<>();
            Iterator<Attente> it = e.attentes.iterator();
            while (it.hasNext()) {
                Attente a = it.next();
                if (a.heure() <= now) {
                    dues.add(a);
                    it.remove();
                }
            }
            for (Attente a : dues) {
                ZonesLoot.Zone z = ZonesLoot.zone(l.dimension(), a.zone());
                if (z != null) e.activerUn(z, a.exclu(), l.random);
            }
            if (!dues.isEmpty()) e.setDirty();
        }
        if (!e.tires.isEmpty()) {                                       // vidés sans fermeture propre (déconnexion...)
            Set<Container> vus = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ServerPlayer p : l.players()) {
                if (p.containerMenu instanceof ChestMenu cm) vus.add(cm.getContainer());
            }
            LongArrayList vides = new LongArrayList();
            for (long k : e.tires) {
                Actif a = e.actifs.get(k);
                if (a != null && a.contenu != null && !vus.contains(a.contenu) && a.contenu.isEmpty()) vides.add(k);
            }
            for (long k : vides) e.vider(k);
        }
        if (ConfigLoot.etoiles() && !e.actifs.isEmpty() && !l.players().isEmpty()) e.etoiles(l);
    }

    // ------------------------------------------------------------------------------------------ étoiles
    private void etoiles(ServerLevel l) {
        int rayon = ConfigLoot.rayonEtoiles(), nb = ConfigLoot.etoilesParSeconde();
        double r2 = (double) rayon * rayon;
        int rs = SectionPos.blockToSectionCoord(rayon) + 1;
        Map<Long, BlockState> fautifs = new HashMap<>();
        for (ServerPlayer p : l.players()) {
            int sx = SectionPos.blockToSectionCoord(p.getBlockX()), sz = SectionPos.blockToSectionCoord(p.getBlockZ());
            for (int dx = -rs; dx <= rs; dx++) {
                for (int dz = -rs; dz <= rs; dz++) {
                    LongArrayList liste = parSecteur.get(ChunkPos.asLong(sx + dx, sz + dz));
                    if (liste == null) continue;
                    for (int i = 0; i < liste.size(); i++) {
                        long k = liste.getLong(i);
                        if (p.distanceToSqr(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5) > r2) continue;
                        BlockPos b = BlockPos.of(k);
                        if (!l.isLoaded(b)) continue;
                        Actif a = actifs.get(k);
                        if (a == null) continue;
                        BlockState st = l.getBlockState(b);
                        if (!(st.getBlock() instanceof ConteneurBlock cb) || !cb.nom.equals(a.nom)) {
                            fautifs.put(k, st);
                            continue;
                        }
                        scintiller(l, p, b, st, cb, nb);
                    }
                }
            }
        }
        fautifs.forEach((k, st) -> {                                    // autre conteneur en place : il prend la relève
            if (st.getBlock() instanceof ConteneurBlock cb && avecLoot(cb)) adopter(l, k, cb.nom);
            else invalider(l, k, BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString(), false);
        });
    }

    /** Quelques étoiles (END_ROD) sur le dessus, l'avant et les côtés de l'objet (pas le dos, contre le mur). */
    private static void scintiller(ServerLevel l, ServerPlayer p, BlockPos b, BlockState st, ConteneurBlock cb, int nb) {
        Direction face = st.getValue(HorizontalDirectionalBlock.FACING);
        VoxelShape s = cb.encombrement().outline(face, 0, 0, 0);
        AABB o = s.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : s.bounds();
        double m = 0.08;
        AABB box = new AABB(o.minX - m, o.minY + 0.05, o.minZ - m, o.maxX + m, o.maxY + m, o.maxZ + m);
        RandomSource r = l.random;
        for (int i = 0; i < nb; i++) {
            double x = box.minX + r.nextDouble() * (box.maxX - box.minX);
            double y = box.minY + r.nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + r.nextDouble() * (box.maxZ - box.minZ);
            int c = r.nextInt(4);
            Direction d = c == 0 ? Direction.UP : c == 1 ? face : c == 2 ? face.getClockWise() : face.getCounterClockWise();
            switch (d) {
                case UP -> y = box.maxY;
                case EAST -> x = box.maxX;
                case WEST -> x = box.minX;
                case SOUTH -> z = box.maxZ;
                case NORTH -> z = box.minZ;
                default -> {
                }
            }
            l.sendParticles(p, ParticleTypes.END_ROD, false, b.getX() + x, b.getY() + y, b.getZ() + z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------------------------------ joueurs, blocs
    /** Le conteneur peut-il avoir du loot ? (une table lui correspond ; les véhicules n'en ont pas) */
    static boolean avecLoot(ConteneurBlock cb) {
        return !cb.vehicule && TablesCoffres.pourConteneur(cb.nom) != null;
    }

    /** Clic droit sur un conteneur (bloc principal en pos). */
    static void ouvrir(ServerLevel l, BlockPos pos, BlockState st, ConteneurBlock cb, ServerPlayer p) {
        RotationLoot e = get(l);
        long k = pos.asLong();
        ZonesLoot.Zone z = ZonesLoot.zoneDe(l.dimension(), k);
        if ((z == null || !cb.nom.equals(z.conteneur(k)) || e.invalides.containsKey(k)) && avecLoot(cb)) {
            e.adopter(l, k, cb.nom);                                    // posé sans qu'on le sache (WorldEdit...) : il entre
        }
        Actif a = e.actifs.get(k);
        if (a != null && !a.nom.equals(cb.nom)) {                       // conteneur sans table à la place d'un actif
            e.invalider(l, k, cb.nom, false);
            a = null;
        }
        if (a == null) {
            p.displayClientMessage(Component.translatableWithFallback("heraliz_loot.conteneur.vide", "Vide.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (a.contenu == null) {
            // contenu tiré à la première ouverture : avec le bonus de loot de celui qui ouvre
            int bonus = BonusLoot.pourcent(p);
            a.contenu = e.remplir(k, cb.nom, l.random, bonus);
            e.tires.add(k);
            e.setDirty();
            BonusLoot.annoncer(p, bonus);
        }
        Inventaire inv = a.contenu;
        boolean premier = !e.regarde(inv, p);
        p.openMenu(new SimpleMenuProvider((id, pi, pl) -> ChestMenu.threeRows(id, pi, inv), titre(cb)));
        if (premier && p.containerMenu instanceof ChestMenu cm && cm.getContainer() == inv) son(l, pos, st, true);
    }

    private static final Set<String> SANS_TABLE = new HashSet<>();

    /** Contenu d'un conteneur, tiré dans sa table (bonus de loot en %) : objets placés au hasard dans les 27 cases. */
    private Inventaire remplir(long k, String nom, RandomSource r, int bonus) {
        Inventaire inv = new Inventaire(this, k);
        TablesCoffres.Choix c = TablesCoffres.pourConteneur(nom);
        if (c == null || c.table().objets().isEmpty()) {
            if (SANS_TABLE.add(nom)) HeralizLoot.LOG.warn("Aucune table de loot pour {} (data/heraliz_loot/coffres/)", nom);
            return inv;
        }
        int n = Math.min(TAILLE, c.min() + r.nextInt(Math.max(1, c.max() - c.min() + 1)));
        int[] cases = new int[TAILLE];
        for (int i = 0; i < TAILLE; i++) cases[i] = i;
        for (int i = TAILLE - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = cases[i];
            cases[i] = cases[j];
            cases[j] = t;
        }
        boolean[] sac = {false};
        for (int i = 0; i < n; i++) inv.objets.set(cases[i], TablesCoffres.tirerUnSac(c.table(), r, bonus, sac));
        return inv;
    }

    /** Un conteneur vient d'être posé : il entre dans la rotation tout seul (voir {@link #adopter}). */
    static void blocPose(ServerLevel l, BlockPos pos, String nom) {
        ConteneurBlock cb = ConteneurBlock.parNom(nom);
        if (cb == null || !avecLoot(cb)) return;
        get(l).adopter(l, pos.asLong(), nom);
    }

    /** Un conteneur de la rotation vient d'être cassé ou remplacé (voir {@link #invalider}). */
    static void blocRetire(ServerLevel l, BlockPos pos, String nom) {
        long k = pos.asLong();
        if (ZonesLoot.zoneDe(l.dimension(), k) == null) return;
        get(l).invalider(l, k, BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).toString(), true);
    }

    private static void son(ServerLevel l, BlockPos pos, BlockState st, boolean ouverture) {
        SoundType t = st.getSoundType(l, pos, null);
        SoundEvent s;
        if (t == SoundType.METAL) s = ouverture ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE;
        else if (t == SoundType.WOOL) s = SoundEvents.ARMOR_EQUIP_LEATHER;
        else if (t == SoundType.STONE) s = ouverture ? SoundEvents.BUNDLE_REMOVE_ONE : SoundEvents.BUNDLE_INSERT;
        else s = ouverture ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        l.playSound(null, pos, s, SoundSource.BLOCKS, 0.5f, 0.9f + l.random.nextFloat() * 0.2f);
    }

    // ------------------------------------------------------------------------------------------ titres
    private static Map<String, String> noms;

    /**
     * Titre de l'inventaire : le nom du bloc dans la langue du joueur, dont son jeu retire l'étiquette de tiers (« [T2] »,
     * ClientLoot) ; sans le jar client, le nom français sans l'étiquette.
     */
    static Component titre(ConteneurBlock cb) {
        if (noms == null) noms = lireNoms();
        String n = noms.get(cb.nom);
        return n != null ? Component.translatableWithFallback(cb.getDescriptionId(), n) : Component.translatable(cb.getDescriptionId());
    }

    private static Map<String, String> lireNoms() {
        Map<String, String> m = new HashMap<>();
        String prefixe = "block." + HeralizLoot.MODID + ".";
        try (InputStream in = HeralizLoot.class.getResourceAsStream("/assets/" + HeralizLoot.MODID + "/lang/fr_fr.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getKey().startsWith(prefixe)) {
                        m.put(e.getKey().substring(prefixe.length()), e.getValue().getAsString().replaceFirst("^\\[[^\\]]*\\]\\s*", ""));
                    }
                }
            }
        } catch (Exception ex) {
            HeralizLoot.LOG.warn("Noms des conteneurs illisibles (fr_fr.json)", ex);
        }
        return m;
    }

    // ------------------------------------------------------------------------------------------ inventaire
    /**
     * Inventaire d'un conteneur actif, partagé par tous les joueurs qui l'ouvrent (comme un coffre). Quand le dernier
     * le referme vide, le conteneur redevient inactif.
     */
    static final class Inventaire implements Container {
        final RotationLoot etat;
        final long pos;
        final NonNullList<ItemStack> objets = NonNullList.withSize(TAILLE, ItemStack.EMPTY);

        Inventaire(RotationLoot etat, long pos) {
            this.etat = etat;
            this.pos = pos;
        }

        @Override
        public int getContainerSize() {
            return TAILLE;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : objets) {
                if (!s.isEmpty()) return false;
            }
            return true;
        }

        public int nombre() {
            int n = 0;
            for (ItemStack s : objets) {
                if (!s.isEmpty()) n++;
            }
            return n;
        }

        @Override
        public ItemStack getItem(int i) {
            return objets.get(i);
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            ItemStack s = ContainerHelper.removeItem(objets, i, n);
            if (!s.isEmpty()) setChanged();
            return s;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack s = ContainerHelper.takeItem(objets, i);
            setChanged();
            return s;
        }

        @Override
        public void setItem(int i, ItemStack s) {
            objets.set(i, s);
            if (s.getCount() > getMaxStackSize()) s.setCount(getMaxStackSize());
            setChanged();
        }

        @Override
        public void setChanged() {
            etat.setDirty();
        }

        @Override
        public boolean stillValid(Player p) {
            Actif a = etat.actifs.get(pos);
            if (a == null || a.contenu != this || etat.niveau == null || p.level() != etat.niveau) return false;
            return p.distanceToSqr(BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.5, BlockPos.getZ(pos) + 0.5) <= 100;
        }

        @Override
        public void clearContent() {
            objets.clear();
            setChanged();
        }

        @Override
        public void stopOpen(Player p) {
            Actif a = etat.actifs.get(pos);
            if (a == null || a.contenu != this || etat.niveau == null || etat.regarde(this, p)) return;
            son(etat.niveau, BlockPos.of(pos), etat.niveau.getBlockState(BlockPos.of(pos)), false);
            if (isEmpty()) etat.vider(pos);
        }
    }
}
