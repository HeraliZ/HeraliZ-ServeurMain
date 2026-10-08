package fr.heralisz.matchs.moteur.arenes;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implementation des arenes (MATCHS_SPEC §1.5) : reservation, pose par tranches de {@link Arenes#BLOCS_PAR_TICK} blocs
 * par tick (file d'attente commune), registre persistant ({@link RegistreArenes}), rechargement au demarrage,
 * nettoyage a la liberation et ecouteurs de remise en etat des modes. Grille : rang i -> case
 * ((i mod 32) x 512, (i / 32) x 512) de la dimension de la carte, origine en y = 64 (ou {@code "y"} du JSON de la
 * carte). Fil serveur.
 */
public final class ArenesService {
    private ArenesService() {
    }

    /** Cases par rangee de la grille. */
    public static final int LARGEUR_GRILLE = 32;
    /**
     * Temps de pose maximal par tick (10 ms) : la pose d'une grande carte s'etale sur quelques secondes sans figer le
     * serveur (avant : 20 000 blocs d'un coup, 0,5 a 0,75 s de gel par tick, « Can't keep up » au premier lancement).
     */
    private static final long BUDGET_POSE_NS = 10_000_000L;
    /** Occupant d'une instance posee d'avance au demarrage, rendue au premier qui reserve la carte. */
    private static final String PRECHAUFFAGE = "prechauffage";
    private static final int DRAPEAUX = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Pose en cours. */
    private static final class Pose {
        final Instance inst;
        final Structure st;
        final RegistreArenes.Entree entree;
        int i;
        final long debutMs = System.currentTimeMillis();

        Pose(Instance inst, Structure st, RegistreArenes.Entree entree) {
            this.inst = inst;
            this.st = st;
            this.entree = entree;
        }
    }

    private static final ArrayDeque<Pose> FILE = new ArrayDeque<>();
    private static final Map<String, Structure> STRUCTURES = new HashMap<>();
    private static final List<Consumer<Instance>> ECOUTEURS = new ArrayList<>();
    private static final BlockPos.MutableBlockPos CURSEUR = new BlockPos.MutableBlockPos();
    private static final Set<String> NAMESPACES_NETTOYES = Set.of("corpse", "tacz", "heralisz_tactical", "heralisz_grenade");

    // ------------------------------------------------------------------ API
    @Nullable
    public static Instance reserver(MinecraftServer s, String carteId) {
        Carte c = Arenes.carte(carteId);
        if (c == null) {
            HeraliszMatchs.LOG.warn("[matchs] reserver : carte inconnue {}", carteId);
            return null;
        }
        for (Instance i : Arenes.instances()) {
            if (i.carte().id().equals(carteId) && i.libre()) {
                i.occuper("?");
                return i;
            }
        }
        // instance encore en pose d'avance (demarrage) : on la prend, elle sera prete avant une neuve
        for (Instance i : Arenes.instances()) {
            if (i.carte().id().equals(carteId) && PRECHAUFFAGE.equals(i.occupant())) {
                i.occuper("?");
                return i;
            }
        }
        ServerLevel niveau = s.getLevel(c.dimension());
        if (niveau == null) {
            HeraliszMatchs.LOG.warn("[matchs] reserver {} : dimension {} absente", carteId, c.dimension().location());
            return null;
        }
        Structure st = structure(s, carteId);
        if (st == null) return null;
        RegistreArenes reg = RegistreArenes.de(s);
        String dim = c.dimension().location().toString();
        int index = reg.prochainIndex(dim);
        RegistreArenes.Entree e = new RegistreArenes.Entree();
        e.carte = carteId;
        e.dim = dim;
        e.index = index;
        e.origine = origine(c, index);
        e.empreinte = st.empreinte;
        reg.entrees.add(e);
        reg.setDirty();
        Instance inst = new Instance(c, niveau, e.origine, index);
        Arenes.inscrire(inst);
        inst.occuper("?");
        FILE.addLast(new Pose(inst, st, e));
        HeraliszMatchs.LOG.info("[matchs] nouvelle instance {} en {} : pose de {} blocs", inst.id(), e.origine.toShortString(), st.blocs.size());
        return inst;
    }

    /** Origine de la case {@code index} pour cette carte. */
    public static BlockPos origine(Carte c, int index) {
        int y = c.meta().has("y") ? c.meta().get("y").getAsInt() : 64;
        int h = c.taille().getY();
        if (y + h > 250) y = Math.max(1, 250 - h);
        // bornes sous la structure (structures annexes, ex. salle du boss sous The Giant) : jamais sous le bas du monde
        int bas = (int) Math.floor(c.bornes().minY);
        if (bas < 0 && y + bas < 1) y = 1 - bas;
        return new BlockPos((index % LARGEUR_GRILLE) * Arenes.ESPACEMENT, y, (index / LARGEUR_GRILLE) * Arenes.ESPACEMENT);
    }

    public static void liberer(Instance inst) {
        if (inst == null || inst.libre()) return;
        nettoyer(inst);
        for (Consumer<Instance> l : ECOUTEURS) {
            try {
                l.accept(inst);
            } catch (RuntimeException ex) {
                HeraliszMatchs.LOG.error("[matchs] remise en etat de {} en erreur", inst.id(), ex);
            }
        }
        inst.occuper(null);
    }

    /** Ajoute un ecouteur de liberation (remise en etat propre au mode : portes zombies, charge des duels...). */
    public static void quandLiberee(Consumer<Instance> l) {
        ECOUTEURS.add(l);
    }

    /**
     * Retire de l'instance (bornes elargies de 16) les objets au sol, orbes, projectiles et grenades, explosifs, mobs,
     * corps (mod corpse) et entites TaCZ/tactiques ; garde les decors (tableaux, cadres, supports, affichages) et les
     * joueurs. Rend le nombre d'entites retirees.
     */
    public static int nettoyer(Instance inst) {
        AABB zone = inst.bornes().inflate(16);
        List<Entity> l = inst.niveau().getEntities((Entity) null, zone, ArenesService::aNettoyer);
        for (Entity e : l) e.discard();
        if (!l.isEmpty()) HeraliszMatchs.LOG.info("[matchs] instance {} nettoyee : {} entites", inst.id(), l.size());
        return l.size();
    }

    private static boolean aNettoyer(Entity e) {
        if (e instanceof Player) return false;
        if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile || e instanceof PrimedTnt || e instanceof Mob) return true;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && NAMESPACES_NETTOYES.contains(id.getNamespace());
    }

    /** Texte d'avancement de la pose (« 12 000 / 48 210 ») ou « prete ». */
    public static String progression(Instance inst) {
        if (inst.prete()) return "prete";
        for (Pose p : FILE) if (p.inst == inst) return p.i + " / " + p.st.blocs.size();
        return "en attente";
    }

    // ------------------------------------------------------------------ cycle de vie
    /** Rechargement des donnees : les structures seront relues (empreinte) a la prochaine reservation. */
    public static void oublierStructures() {
        STRUCTURES.clear();
    }

    /** Structure lue de la carte (gardee en memoire jusqu'au prochain rechargement des donnees), ou null. Fil serveur. */
    @Nullable
    public static Structure structure(MinecraftServer s, String carte) {
        Structure st = STRUCTURES.get(carte);
        if (st == null) {
            st = Structure.lire(s, carte);
            if (st != null) STRUCTURES.put(carte, st);
        }
        return st;
    }

    /** Demarrage : relit le registre, inscrit les instances encore valides et reprend les poses interrompues. */
    public static void demarrage(MinecraftServer s) {
        RegistreArenes reg = RegistreArenes.de(s);
        int ok = 0, reprises = 0, obsoletes = 0;
        for (RegistreArenes.Entree e : reg.entrees) {
            if (e.obsolete) continue;
            Carte c = Arenes.carte(e.carte);
            if (c == null) continue;
            Structure st = structure(s, e.carte);
            if (st == null) continue;
            if (st.empreinte != e.empreinte) {
                e.obsolete = true;
                reg.setDirty();
                obsoletes++;
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(e.dim);
            ServerLevel niveau = rl == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
            if (niveau == null) continue;
            Instance inst = new Instance(c, niveau, e.origine, e.index);
            Arenes.inscrire(inst);
            if (e.prete) {
                inst.marquerPrete();
                ok++;
            } else {
                FILE.addLast(new Pose(inst, st, e));
                reprises++;
            }
        }
        HeraliszMatchs.LOG.info("[matchs] arenes : {} instances pretes, {} poses reprises, {} obsoletes", ok, reprises, obsoletes);
        prechauffer(s);
    }

    /**
     * Pose d'avance, en tache de fond, une instance de chaque carte qui n'en a encore aucune (premier demarrage apres
     * une installation ou une nouvelle version de la carte) : le premier joueur qui lance la carte la trouve prete.
     */
    private static void prechauffer(MinecraftServer s) {
        int n = 0;
        for (Carte c : Arenes.cartes()) {
            boolean existe = false;
            for (Instance i : Arenes.instances()) {
                if (i.carte().id().equals(c.id())) {
                    existe = true;
                    break;
                }
            }
            if (existe) continue;
            Instance i = reserver(s, c.id());
            if (i == null) continue;
            i.occuper(PRECHAUFFAGE);
            i.quandPrete(() -> {
                if (PRECHAUFFAGE.equals(i.occupant())) i.occuper(null);
            });
            n++;
        }
        if (n > 0) HeraliszMatchs.LOG.info("[matchs] arenes : {} carte(s) posee(s) d'avance en tache de fond", n);
    }

    /**
     * Pose par tranches (appele a chaque tick serveur) : au plus {@link Arenes#BLOCS_PAR_TICK} blocs et
     * {@link #BUDGET_POSE_NS} de temps par tick. Sans allocation hors fin de pose.
     */
    public static void tick(MinecraftServer s) {
        if (FILE.isEmpty()) return;
        int budget = Arenes.BLOCS_PAR_TICK;
        long fin = System.nanoTime() + BUDGET_POSE_NS;
        while (budget > 0 && !FILE.isEmpty()) {
            Pose p = FILE.peekFirst();
            ServerLevel lvl = p.inst.niveau();
            BlockPos o = p.inst.origine();
            List<Structure.Bloc> blocs = p.st.blocs;
            int n = blocs.size();
            while (budget > 0 && p.i < n) {
                Structure.Bloc b = blocs.get(p.i++);
                CURSEUR.set(o.getX() + b.x(), o.getY() + b.y(), o.getZ() + b.z());
                lvl.setBlock(CURSEUR, b.etat(), DRAPEAUX);
                if (b.nbt() != null) {
                    BlockEntity be = lvl.getBlockEntity(CURSEUR);
                    if (be != null) {
                        CompoundTag t = b.nbt().copy();
                        t.putInt("x", CURSEUR.getX());
                        t.putInt("y", CURSEUR.getY());
                        t.putInt("z", CURSEUR.getZ());
                        be.load(t);
                        be.setChanged();
                    }
                }
                budget--;
                if ((budget & 63) == 0 && System.nanoTime() > fin) budget = 0;
            }
            if (p.i >= n) {
                FILE.pollFirst();
                terminer(s, p);
            }
        }
    }

    private static void terminer(MinecraftServer s, Pose p) {
        ServerLevel lvl = p.inst.niveau();
        BlockPos o = p.inst.origine();
        int ents = 0;
        for (Structure.Ent e : p.st.entites) {
            CompoundTag t = e.nbt().copy();
            t.remove("UUID");
            double x = o.getX() + e.x(), y = o.getY() + e.y(), z = o.getZ() + e.z();
            Entity ent = EntityType.loadEntityRecursive(t, lvl, en -> {
                en.moveTo(x, y, z, en.getYRot(), en.getXRot());
                return en;
            });
            if (ent != null && lvl.addFreshEntity(ent)) ents++;
        }
        nettoyer(p.inst);
        p.entree.prete = true;
        RegistreArenes.de(s).setDirty();
        HeraliszMatchs.LOG.info("[matchs] instance {} posee : {} blocs, {} entites en {} ms", p.inst.id(), p.st.blocs.size(), ents,
                System.currentTimeMillis() - p.debutMs);
        p.inst.marquerPrete();
    }

    /** Arret du serveur : oublie la file (les poses inachevees seront reprises au demarrage). */
    public static void arret() {
        FILE.clear();
        STRUCTURES.clear();
    }

    /** Vrai si le niveau est une dimension de matchs. */
    public static boolean estArene(Level l) {
        return Arenes.estArene(l);
    }
}
